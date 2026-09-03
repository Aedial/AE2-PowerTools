package com.ae2powertools.features.crafter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.ByteBuf;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.container.ContainerNull;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import appeng.tile.AEBaseTile;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.config.PowerToolsServerConfig;
import com.ae2powertools.items.ItemCrafterSpeedUpgrade;
import com.ae2powertools.util.OperationTimingStats;
import com.ae2powertools.util.PowerStateClientFlags;
import com.ae2powertools.util.SaturatingMath;


/**
 * Tile entity for the AE2 AutoCrafter block.
 * Automatically crafts items using patterns, with support for reusable/catalyst items.
 */
public class TileAutoCrafter extends AEBaseTile implements ITickable, IActionHost, IGridProxyable, IPowerChannelState {

    public static final int ENTRY_COUNT = 12;
    public static final int MIN_SPEED_TICKS = 20;
    public static final int DEFAULT_SPEED_TICKS = 20;
    public static final int MIN_BATCH_SIZE = 1;
    public static final int DEFAULT_BATCH_SIZE = 1;
    private static final int PROBE_TIMING_REFRESH_INTERVAL = 20;

    /**
     * Gets the base crafts per operation from server config.
     * This is a flat multiplier on all batch sizes.
     */
    public static int getBaseCraftsPerOperation() {
        return PowerToolsServerConfig.crafter.getBaseCraftsPerOperation();
    }

    // Fake player for crafting operations (UUID generated per-crafter to avoid conflicts)
    private static final GameProfile CRAFTER_PROFILE = new GameProfile(
            UUID.randomUUID(),
            "[AE2AutoCrafter]"
    );

    private final AENetworkProxy gridProxy;
    private final IActionSource actionSource;
    private final List<CrafterEntry> entries;
    private final OperationTimingStats operationTimingStats;

    /**
     * Speed for all recipes (in ticks).
     */
    private int speedTicks;

    /**
     * Batch size multiplier for all recipes.
     */
    private int batchSize;

    /**
     * Tick counter for scheduling.
     */
    private long tickCounter;
    private long probeTimingSnapshotTick;
    private boolean probeTimingSnapshotHasSample;
    private long probeLastDurationNanos;
    private long probeAverageDurationNanos;
    private long probeMaxDurationNanos;

    /**
     * Cached fake player for crafting.
     */
    @Nullable
    private FakePlayer fakePlayer;

    /**
     * Upgrades inventory (4 slots).
     * Position in GUI: 186,7 with 18x18 slots
     */
    public static final int UPGRADE_SLOTS = 4;
    private final ItemStack[] upgradeInventory;

    /**
     * Current GUI page (0-11), persisted until world load.
     * This is NOT saved to NBT as it should reset on world load.
     */
    private int currentPage;

    private int clientFlags;

    public TileAutoCrafter() {
        this.gridProxy = new AENetworkProxy(this, "proxy", this.getItemFromTile(this), true);
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.gridProxy.setIdlePowerUsage(2.0);
        this.actionSource = new MachineSource(this);

        this.entries = new ArrayList<>();
        for (int i = 0; i < ENTRY_COUNT; i++) entries.add(new CrafterEntry());
        this.operationTimingStats = new OperationTimingStats();

        this.speedTicks = DEFAULT_SPEED_TICKS;
        this.batchSize = DEFAULT_BATCH_SIZE;
        this.tickCounter = 0;
        this.probeTimingSnapshotTick = 0;
        this.probeTimingSnapshotHasSample = false;
        this.probeLastDurationNanos = 0L;
        this.probeAverageDurationNanos = 0L;
        this.probeMaxDurationNanos = 0L;
        this.currentPage = 0;

        this.upgradeInventory = new ItemStack[UPGRADE_SLOTS];
        for (int i = 0; i < UPGRADE_SLOTS; i++) this.upgradeInventory[i] = ItemStack.EMPTY;
    }

    // ==================== TICK LOGIC ====================

    @Override
    public void update() {
        if (world.isRemote) return;

        tickCounter++;

        // Check if it's time to run crafts (and insert pending outputs back into the network)
        long craftIntervalTicks = getCraftIntervalTicks();
        if (tickCounter < craftIntervalTicks) return;

        tickCounter = 0;

        boolean shouldSampleTiming = hasTimingWorkload();
        long startedAt = shouldSampleTiming ? System.nanoTime() : 0L;

        processPendingOutputs();
        processAllEntries();

        if (startedAt > 0L) operationTimingStats.recordSample(System.nanoTime() - startedAt);
    }

    private boolean hasTimingWorkload() {
        for (CrafterEntry entry : entries) {
            if (entry.hasPattern() || entry.hasPendingOutputs()) return true;
        }

        return false;
    }

    private long getCraftIntervalTicks() {
        // The user-facing timer supports intervals beyond Integer.MAX_VALUE.
        // Keep the server scheduler in long space as well so large batch values do not wrap.
        return Math.max(1L, SaturatingMath.saturatingMultiply(speedTicks, batchSize));
    }

    /**
     * Returns the remaining ticks before the next craft cycle starts.
     */
    public long getTicksUntilNextOperation() {
        long interval = getCraftIntervalTicks();
        long remaining = interval - tickCounter;

        if (remaining > 0L) return remaining;

        return interval;
    }

    boolean isEntryAtTarget(CrafterEntry entry, @Nullable IItemList<IAEItemStack> storageList) {
        if (entry == null || !entry.hasValidRecipeInfo()) return false;

        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null || info.getOutputs().isEmpty()) return false;

        IAEItemStack output = info.getOutputs().get(0);
        return getNetworkQuantity(storageList, output) >= entry.getTargetQuantity();
    }

    @Override
    public boolean isPowered() {
        if (Platform.isServer()) return gridProxy.isPowered();

        return PowerStateClientFlags.isPowered(clientFlags);
    }

    @Override
    public boolean isActive() {
        if (Platform.isServer()) return gridProxy.isActive();

        return PowerStateClientFlags.isActive(clientFlags);
    }

    @MENetworkEventSubscribe
    public void onChannelStateChanged(MENetworkChannelsChanged event) {
        markForUpdate();
    }

    @MENetworkEventSubscribe
    public void onPowerStateChanged(MENetworkPowerStatusChange event) {
        markForUpdate();
    }

    /**
     * Try to insert any pending outputs into the network.
     */
    private void processPendingOutputs() {
        IMEMonitor<IAEItemStack> itemStorage = getItemStorageMonitor();

        for (CrafterEntry entry : entries) {
            if (!entry.hasPendingOutputs()) continue;

            List<IAEItemStack> pendingList = entry.getPendingOutputs();
            Iterator<IAEItemStack> it = pendingList.iterator();

            while (it.hasNext()) {
                IAEItemStack pending = it.next();
                IAEItemStack remaining = tryInsertIntoNetwork(itemStorage, pending, Actionable.MODULATE);

                if (remaining == null || remaining.getStackSize() == 0) {
                    it.remove();
                } else {
                    pending.setStackSize(remaining.getStackSize());
                }
            }

            if (pendingList.isEmpty()) {
                entry.resetState(CrafterState.IDLE);
            } else {
                refreshPendingOutputDetails(entry);
            }
        }
    }

    /**
     * Process all entries and run crafts where possible.
     * <p>
     * This method processes entries atomically with a shared resource pool:
     * 1. Collect: Gather all entries that want to craft
     * 2. Simulate: Query the live amount for each shared input
     * 3. Allocate: Distribute resources fairly among competing entries
     * 4. Extract: Extract all inputs for all entries
     * 5. Craft: Run all crafts using cached recipe info
     * 6. Insert: Insert all outputs into network
     * <p>
     * Fair allocation ensures that if multiple entries need the same resource,
     * each gets a proportional share rather than first-come-first-served.
     */
    private void processAllEntries() {
        IMEMonitor<IAEItemStack> itemStorage = getItemStorageMonitor();
        IItemList<IAEItemStack> storageList = getNetworkStorageList(itemStorage);

        List<CraftCandidate> candidates = collectCandidates(storageList);
        if (candidates.isEmpty()) return;

        // Phase 0: Simulate the shared pool with all items needed by all candidates
        final long effectiveMaxBatchSize = getEffectiveMaxBatchSize();
        Map<ItemStackKey, Long> sharedPool = initializeSharedPool(candidates, itemStorage, effectiveMaxBatchSize);

        // Phase 1: Fair allocation of shared resources
        // allocateResourcesFairly marks entries with insufficient resources as MISSING_INPUT
        List<CraftSimulation> simulations = allocateResourcesFairly(candidates, sharedPool, effectiveMaxBatchSize);

        // If no entries can craft after allocation, we're done
        if (simulations.isEmpty()) return;

        // Phase 2: Extract all inputs from network
        List<CraftSimulation> successfulExtractions = new ArrayList<>();
        for (CraftSimulation sim : simulations) {
            DurabilityExtraction durabilityExtraction = extractInputs(itemStorage, sim.entry, sim.info, sim.crafts);
            if (durabilityExtraction != null) {
                sim.durabilityExtraction = durabilityExtraction;
                successfulExtractions.add(sim);
            } else {
                // Should never happen, as it means MODULATE does not agree with SIMULATE.
                // This means some provider out there is VERY broken and is not reporting its storage correctly.
                sim.entry.setError(CrafterEntry.CrafterErrorState.INPUTS_CHANGED);
            }
        }

        // Phase 3: Run all crafts
        List<CraftResult> results = new ArrayList<>();
        for (CraftSimulation sim : successfulExtractions) {
            List<IAEItemStack> outputs = performCraftInternal(sim.entry, sim.info, sim.crafts,
                sim.durabilityExtraction);
            if (outputs != null && !outputs.isEmpty()) {
                results.add(new CraftResult(sim.entry, outputs));
            } else {
                sim.entry.setState(CrafterState.SIMULATION_FAILED);
            }
        }

        // Phase 4: Insert all outputs
        for (CraftResult result : results) {
            boolean anyPending = false;
            for (IAEItemStack output : result.outputs) {
                IAEItemStack remaining = tryInsertIntoNetwork(itemStorage, output, Actionable.MODULATE);
                if (remaining != null && remaining.getStackSize() > 0) {
                    result.entry.addPendingOutput(remaining);
                    anyPending = true;
                }
            }

            result.entry.setLastCraftTick(world.getTotalWorldTime());

            if (anyPending) {
                refreshPendingOutputDetails(result.entry);
            } else {
                result.entry.setState(CrafterState.IDLE);
            }

            // Record successful craft metrics
            result.entry.recordMetrics(false, result.entry.getLastRequestedBatchSize(), result.entry.getLastActualBatchSize());
        }
    }

    /**
     * Validate all entries without crafting.
     * <p>
     * This is a "dry run" that updates entry states based on current network conditions:
     * - DISABLED: Entry is disabled or has no pattern
     * - HOLDING_OUTPUT: Entry has pending outputs waiting to be inserted
     * - SIMULATION_FAILED: Recipe info is invalid
     * - IDLE: Target quantity reached
     * - MISSING_CATALYST: Missing required catalyst items
     * - MISSING_INPUT: Not enough items in network (checked via simulation)
     * - IDLE: Entry can craft (has all inputs and catalysts)
     * <p>
     * Called on world load to show correct states immediately.
     * <p>
     * <b>NOTE</b>: Due to the network not being fully initialized on world load,
     *              this may show MISSING_INPUT for entries that are actually craftable.
     *              This is fairly minor, as it will correct itself on the next crafting
     *              cycle (network <i>should</i> be fully initialized by then).
     *
     * TODO: Should we delay this until the network is fully initialized?
     *       It's really not a big deal, so it's quite low priority.
     */
    private void validateAllEntries() {
        IMEMonitor<IAEItemStack> itemStorage = getItemStorageMonitor();
        IItemList<IAEItemStack> storageList = getNetworkStorageList(itemStorage);

        List<CraftCandidate> candidates = collectCandidates(storageList);

        // No candidates to validate - we're done
        if (candidates.isEmpty()) return;

        // Simulate the shared pool (we only need to check presence of inputs for 1 craft)
        Map<ItemStackKey, Long> sharedPool = initializeSharedPool(candidates, itemStorage, 1L);

        // Simulate fair allocation (same as processAllEntries, but don't extract/craft)
        // Check each candidate for input availability
        for (CraftCandidate candidate : candidates) {
            boolean hasAllInputs = true;
            Map<ItemStackKey, long[]> missingInputs = new LinkedHashMap<>();
            Map<ItemStackKey, ItemStack> missingStacks = new HashMap<>();

            Map<ItemStackKey, Long> demand = calculateResourceDemand(candidate.entry, candidate.info, 1L);
            for (Map.Entry<ItemStackKey, Long> resource : demand.entrySet()) {
                ItemStackKey key = resource.getKey();
                long available = sharedPool.getOrDefault(key, 0L);
                long needed = resource.getValue();

                if (available < needed) {
                    hasAllInputs = false;

                    long[] counts = missingInputs.computeIfAbsent(key, ignored -> new long[]{needed, available});
                    counts[0] = Math.max(counts[0], needed);
                    counts[1] = Math.min(counts[1], available);
                    ItemStack stack = getResourceStack(candidate.entry, candidate.info, key);
                    if (stack != null) missingStacks.putIfAbsent(key, stack);
                }
            }

            if (hasAllInputs) {
                candidate.entry.setState(CrafterState.IDLE);
            } else {
                candidate.entry.setState(CrafterState.MISSING_INPUT);
                for (Map.Entry<ItemStackKey, long[]> error : missingInputs.entrySet()) {
                    ItemStack stack = missingStacks.get(error.getKey());
                    long[] counts = error.getValue();
                    if (stack == null) continue;

                    // Build a TextComponentTranslation so the receiving client renders the
                    // message in its own locale; server-side I18n would lock everyone to
                    // English (and fail entirely on dedicated servers without lang files).
                    candidate.entry.addErrorDetail(CrafterEntry.CrafterErrorState.MISSING_INPUT,
                        stack.getDisplayName(), counts[0], counts[1]);
                }

                candidate.entry.addHint(CrafterEntry.CrafterHint.NBT_MISMATCH_HINT);
            }
        }
    }

    /**
     * Collects entries that are ready for resource allocation.
     * Updates entries rejected before allocation.
     */
    private List<CraftCandidate> collectCandidates(@Nullable IItemList<IAEItemStack> storageList) {
        List<CraftCandidate> candidates = new ArrayList<>();

        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            CrafterEntry entry = entries.get(entryIndex);

            if (entry.isEmpty()) {                              // Skip empty entries (no pattern)
                entry.resetState(CrafterState.NO_PATTERN);
            } else if (!entry.isEnabled()) {                    // Skip disabled entries
                entry.resetState(CrafterState.DISABLED);
            } else if (entry.hasPendingOutputs()) {             // Skip entries with pending outputs
                refreshPendingOutputDetails(entry);
            } else if (!entry.hasValidRecipeInfo()) {           // Check recipe validity
                entry.resetState(CrafterState.SIMULATION_FAILED);
            } else if (isEntryAtTarget(entry, storageList)) {   // Check target quantity
                // TODO: If we ever set a target different than 2.1B, we might want to check
                //       the *real* quantity, instead of the cache (because of ghost items).
                //       This means doing a SIMULATE extraction of the output.
                entry.resetState(CrafterState.IDLE);
            } else {                                            // Check catalysts and inputs for allocation
                CrafterRecipeInfo info = entry.getRecipeInfo();
                if (info == null || ensureCatalystsValid(entryIndex, entry, info)) {
                    entry.resetState(CrafterState.IDLE);
                    candidates.add(new CraftCandidate(entry, info));
                }
            }
        }

        return candidates;
    }

    /**
     * Helper class for craft simulation data.
     */
    private static class CraftSimulation {
        final CrafterEntry entry;
        final CrafterRecipeInfo info;
        final long crafts;
        DurabilityExtraction durabilityExtraction;

        CraftSimulation(CrafterEntry entry, CrafterRecipeInfo info, long crafts) {
            this.entry = entry;
            this.info = info;
            this.crafts = crafts;
        }
    }

    /**
     * Damaged tool variants extracted for each recipe slot.
     */
    private static class DurabilityExtraction {
        private final Map<Integer, List<IAEItemStack>> stacksBySlot = new HashMap<>();

        void add(int slotIndex, IAEItemStack stack) {
            stacksBySlot.computeIfAbsent(slotIndex, ignored -> new ArrayList<>()).add(stack);
        }

        List<IAEItemStack> get(int slotIndex) {
            return stacksBySlot.getOrDefault(slotIndex, Collections.emptyList());
        }
    }

    /**
     * Shared resource associated with its requesting recipe entry and ingredient.
     */
    private static class ResourceRequest {
        final CrafterEntry entry;
        final CrafterRecipeInfo.IngredientInfo ingredient;

        ResourceRequest(CrafterEntry entry, CrafterRecipeInfo.IngredientInfo ingredient) {
            this.entry = entry;
            this.ingredient = ingredient;
        }
    }

    /**
     * Helper class for craft results.
     */
    private static class CraftResult {
        final CrafterEntry entry;
        final List<IAEItemStack> outputs;

        CraftResult(CrafterEntry entry, List<IAEItemStack> outputs) {
            this.entry = entry;
            this.outputs = outputs;
        }
    }

    /**
     * Helper class for craft candidates before fair allocation.
     */
    private static class CraftCandidate {
        final CrafterEntry entry;
        final CrafterRecipeInfo info;

        CraftCandidate(CrafterEntry entry, CrafterRecipeInfo info) {
            this.entry = entry;
            this.info = info;
        }
    }

    /**
     * Pre-initialize the shared resource pool with all items needed by all candidates.
     * Simulates the needed resources extraction for each input type so the pool
     * reflects the current network state.
     * 
     * @param candidates List of all candidates wanting to craft
     * @param itemStorage Item storage monitor used for live availability simulations
     * @param effectiveMaxBatchSize The largest craft batch the allocator can select
     * @return Map of ItemStackKey to available quantity in network
     */
    private Map<ItemStackKey, Long> initializeSharedPool(List<CraftCandidate> candidates,
                                                         @Nullable IMEMonitor<IAEItemStack> itemStorage,
                                                         long effectiveMaxBatchSize) {
        Map<ItemStackKey, Long> pool = new HashMap<>();
        Map<ItemStackKey, ResourceRequest> requests = new LinkedHashMap<>();
        Map<ItemStackKey, Long> totalDemand = new LinkedHashMap<>();

        // Collect all unique items needed by all candidates
        for (CraftCandidate candidate : candidates) {
            for (CrafterRecipeInfo.IngredientInfo ingredient : candidate.info.getConsumedItems()) {
                ItemStackKey key = getResourceKey(candidate.entry, ingredient);
                if (key == null) continue;

                requests.putIfAbsent(key, new ResourceRequest(candidate.entry, ingredient));
            }

            Map<ItemStackKey, Long> demand = calculateResourceDemand(
                candidate.entry, candidate.info, effectiveMaxBatchSize);
            for (Map.Entry<ItemStackKey, Long> entry : demand.entrySet()) {
                totalDemand.merge(entry.getKey(), entry.getValue(), SaturatingMath::saturatingAdd);
            }
        }

        // Align the demand with the live network state for each unique item
        for (Map.Entry<ItemStackKey, ResourceRequest> entry : requests.entrySet()) {
            long requested = totalDemand.getOrDefault(entry.getKey(), 0L);
            if (requested <= 0L) {
                pool.put(entry.getKey(), 0L);
                continue;
            }

            ResourceRequest request = entry.getValue();
            long available;
            if (request.ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY) {
                available = getAvailableDurability(itemStorage, request.entry, request.ingredient);
            } else {
                IAEItemStack item = request.ingredient.getItem();
                if (item == null) {
                    pool.put(entry.getKey(), 0L);
                    continue;
                }

                IAEItemStack simulatedRequest = item.copy();
                simulatedRequest.setStackSize(requested);
                IAEItemStack result = tryExtractFromNetwork(itemStorage, simulatedRequest, Actionable.SIMULATE);
                available = result != null ? Math.min(requested, result.getStackSize()) : 0L;
            }

            pool.put(entry.getKey(), Math.max(0L, available));
        }

        return pool;
    }

    /**
     * Allocate resources fairly among competing entries.
     * <p>
     * Fair allocation algorithm:
     * 1. For each contested resource, divide available items equally among candidates that need it
     * 2. Each candidate converts their item share to crafts (share ÷ items_per_craft)
     * 3. A candidate's final crafts is the minimum across all its required resources
     * <p>
     * This ensures that if two entries fight for the same resource, each gets an equal
     * share of items.
     * 
     * @param candidates List of entries wanting to craft (with their requested batch sizes)
     * @param pool Shared resource pool (pre-initialized via initializeSharedPool)
     * @param effectiveMaxBatchSize The pre-calculated effective max batch size
     * @return List of simulations with fairly allocated craft counts
     */
    private List<CraftSimulation> allocateResourcesFairly(List<CraftCandidate> candidates,
                                                          Map<ItemStackKey, Long> pool,
                                                          long effectiveMaxBatchSize) {
        // Step 1: For each resource, count how many candidates need it and check if contested
        // A resource is contested if total demand exceeds supply
        Map<ItemStackKey, List<CraftCandidate>> resourceUsers = new HashMap<>();
        Map<ItemStackKey, Long> totalDemand = new HashMap<>();
        Map<CraftCandidate, Map<ItemStackKey, Long>> candidateDemand = new HashMap<>();

        for (CraftCandidate candidate : candidates) {
            Map<ItemStackKey, Long> demand = calculateResourceDemand(candidate.entry, candidate.info, effectiveMaxBatchSize);
            candidateDemand.put(candidate, demand);

            for (Map.Entry<ItemStackKey, Long> entry : demand.entrySet()) {
                ItemStackKey key = entry.getKey();
                long needed = entry.getValue();
                if (needed <= 0L) continue;

                resourceUsers.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
                totalDemand.merge(key, needed, SaturatingMath::saturatingAdd);
            }
        }

        // Step 2: For each contested resource, calculate equal item share per candidate
        // Key: resource -> candidate index -> allocated items
        Map<ItemStackKey, Map<CraftCandidate, Long>> itemAllocations = new HashMap<>();

        for (Map.Entry<ItemStackKey, List<CraftCandidate>> entry : resourceUsers.entrySet()) {
            ItemStackKey key = entry.getKey();
            List<CraftCandidate> users = entry.getValue();
            long available = pool.getOrDefault(key, 0L);
            long demand = totalDemand.getOrDefault(key, 0L);

            Map<CraftCandidate, Long> allocations = new HashMap<>();

            if (demand <= available) {
                // Not contested - each candidate gets what they need
                for (CraftCandidate user : users) {
                    Map<ItemStackKey, Long> demandByResource = candidateDemand.get(user);
                    long needed = demandByResource != null ? demandByResource.getOrDefault(key, 0L) : 0L;
                    allocations.put(user, needed);
                }
            } else {
                // Contested - divide equally among users
                long sharePerUser = available / users.size();
                for (CraftCandidate user : users) allocations.put(user, sharePerUser);
            }

            itemAllocations.put(key, allocations);
        }

        // Step 3: Each candidate calculates crafts based on their allocated items
        List<CraftSimulation> simulations = new ArrayList<>();

        for (CraftCandidate candidate : candidates) {
            long finalCrafts = effectiveMaxBatchSize;
            Map<ItemStackKey, long[]> limitingFactors = new LinkedHashMap<>();
            Map<ItemStackKey, ItemStack> limitingStacks = new HashMap<>();
            Map<ItemStackKey, Long> demandByResource = candidateDemand.get(candidate);
            if (demandByResource == null) demandByResource = new HashMap<>();

            for (Map.Entry<ItemStackKey, Long> demand : demandByResource.entrySet()) {
                ItemStackKey key = demand.getKey();
                long needed = demand.getValue();
                Map<CraftCandidate, Long> allocations = itemAllocations.get(key);
                if (allocations == null) continue;

                Long allocated = allocations.get(candidate);
                if (allocated == null) allocated = 0L;

                // Convert the complete per-craft demand for this item into crafts
                long craftsFromAllocation = calculateCraftsFromResource(candidate.entry, candidate.info, key, allocated,
                    effectiveMaxBatchSize);

                if (craftsFromAllocation < finalCrafts) {
                    if (allocated < needed) {
                        long allocatedCount = allocated;
                        long[] counts = limitingFactors.computeIfAbsent(key, ignored -> new long[]{allocatedCount, 0L});
                        counts[0] = Math.min(counts[0], allocatedCount);
                        counts[1] = SaturatingMath.saturatingAdd(counts[1], needed);
                        ItemStack stack = getResourceStack(candidate.entry, candidate.info, key);
                        if (stack != null) limitingStacks.putIfAbsent(key, stack);
                    }
                }

                finalCrafts = Math.min(finalCrafts, craftsFromAllocation);
            }

            // Update batch size tracking for occupancy calculation
            candidate.entry.setBatchSizeTracking(effectiveMaxBatchSize, finalCrafts);

            if (finalCrafts > 0) {
                // Deduct from shared pool
                deductFromPool(candidate.entry, candidate.info, pool, finalCrafts);
                simulations.add(new CraftSimulation(candidate.entry, candidate.info, finalCrafts));

                // If crafts were reduced, add info about why
                if (finalCrafts < effectiveMaxBatchSize && !limitingFactors.isEmpty()) {
                    for (Map.Entry<ItemStackKey, long[]> factor : limitingFactors.entrySet()) {
                        ItemStack stack = limitingStacks.get(factor.getKey());
                        long[] counts = factor.getValue();
                        if (stack == null) continue;

                        candidate.entry.addErrorDetail(CrafterEntry.CrafterErrorState.LIMITED_BY,
                            stack.getDisplayName(), counts[0], counts[1]);
                    }
                }
            } else {
                // No crafts possible - add all limiting factors as errors
                candidate.entry.setState(CrafterState.MISSING_INPUT);
                for (Map.Entry<ItemStackKey, long[]> factor : limitingFactors.entrySet()) {
                    ItemStack stack = limitingStacks.get(factor.getKey());
                    long[] counts = factor.getValue();
                    if (stack == null) continue;

                    candidate.entry.addErrorDetail(CrafterEntry.CrafterErrorState.LIMITED_BY,
                        stack.getDisplayName(), counts[0], counts[1]);
                }

                if (limitingFactors.isEmpty()) {
                    candidate.entry.addErrorDetail(CrafterEntry.CrafterErrorState.NO_ITEMS_IN_NETWORK);
                }

                candidate.entry.addHint(CrafterEntry.CrafterHint.NBT_MISMATCH_HINT);

                // Record as error (no crafts possible)
                candidate.entry.recordMetrics(true, 0, 0);
            }
        }

        return simulations;
    }

    /**
     * Calculate the network supply required for each resource in a recipe.
     * For non-durability items, this is simply the number of crafts multiplied by the per-craft consumption.
     * For durability items, this depends on the durability of the tools, and the consumption per craft.
     */
    private Map<ItemStackKey, Long> calculateResourceDemand(CrafterEntry entry, CrafterRecipeInfo info,
                                                            long crafts) {
        Map<ItemStackKey, Long> demand = new LinkedHashMap<>();

        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            ItemStackKey ingredientKey = getResourceKey(entry, ingredient);
            if (ingredientKey == null) continue;

            long needed = crafts;
            if (ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY) {
                long required = SaturatingMath.saturatingMultiply(crafts, ingredient.getDurabilityPerCraft());
                long leftover = getLeftoverDurability(entry, ingredient.getSlotIndex(), ingredient.getItem());
                needed = Math.max(0L, required - leftover);
            }

            demand.merge(ingredientKey, needed, SaturatingMath::saturatingAdd);
        }

        return demand;
    }

    /**
     * Calculate the largest batch that fits the allocated quantity of a resource.
     */
    private long calculateCraftsFromResource(CrafterEntry entry, CrafterRecipeInfo info, ItemStackKey key,
                                             long items, long effectiveMaxBatchSize) {
        if (effectiveMaxBatchSize <= 0L) return 0L;

        if (key.isDurabilityResource()) {
            return calculateCraftsFromDurability(entry, info, key, items, effectiveMaxBatchSize);
        }

        if (items <= 0L) return 0L;

        long consumedPerCraft = 0L;

        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            ItemStackKey ingredientKey = getResourceKey(entry, ingredient);
            if (!key.equals(ingredientKey)) continue;

            consumedPerCraft++;
        }

        return consumedPerCraft > 0L ? Math.min(effectiveMaxBatchSize, items / consumedPerCraft) : 0L;
    }

    /**
     * Calculate the largest batch that fits the allocated durability.
     */
    private long calculateCraftsFromDurability(CrafterEntry entry, CrafterRecipeInfo info, ItemStackKey key,
                                               long durability, long effectiveMaxBatchSize) {
        long previousThreshold = 0L;

        while (previousThreshold < effectiveMaxBatchSize) {
            long nextThreshold = effectiveMaxBatchSize;
            long durabilityPerCraft = 0L;
            long leftoverDurability = 0L;

            for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
                if (ingredient.getType() != CrafterRecipeInfo.IngredientType.DURABILITY) continue;
                if (!key.equals(getResourceKey(entry, ingredient))) continue;

                long perCraft = ingredient.getDurabilityPerCraft();
                long leftover = getLeftoverDurability(entry, ingredient.getSlotIndex(), ingredient.getItem());
                long threshold = Math.min(effectiveMaxBatchSize, leftover / perCraft);
                if (threshold <= previousThreshold) {
                    durabilityPerCraft = SaturatingMath.saturatingAdd(durabilityPerCraft, perCraft);
                    leftoverDurability = SaturatingMath.saturatingAdd(leftoverDurability, leftover);
                } else {
                    nextThreshold = Math.min(nextThreshold, threshold);
                }
            }

            if (durabilityPerCraft == 0L) {
                previousThreshold = nextThreshold;
                continue;
            }

            long crafts = SaturatingMath.saturatingAdd(durability, leftoverDurability) / durabilityPerCraft;
            if (crafts <= previousThreshold) return previousThreshold;
            if (crafts <= nextThreshold) return crafts;

            previousThreshold = nextThreshold;
        }

        return effectiveMaxBatchSize;
    }

    private void refreshPendingOutputDetails(CrafterEntry entry) {
        entry.resetState(CrafterState.HOLDING_OUTPUT);

        for (IAEItemStack pending : entry.getPendingOutputs()) {
            if (pending == null || pending.getStackSize() <= 0) continue;

            ItemStack stack = pending.createItemStack();
            entry.addErrorDetail(CrafterEntry.CrafterErrorState.PENDING_OUTPUT,
                pending.getStackSize(), stack.getDisplayName());
        }
    }

    /**
     * Deduct a candidate's network demand from the shared pool.
     */
    private void deductFromPool(CrafterEntry entry, CrafterRecipeInfo info, Map<ItemStackKey, Long> pool,
                                long crafts) {
        for (Map.Entry<ItemStackKey, Long> demand : calculateResourceDemand(entry, info, crafts).entrySet()) {
            long current = pool.getOrDefault(demand.getKey(), 0L);
            pool.put(demand.getKey(), Math.max(0L, current - demand.getValue()));
        }
    }

    private boolean ensureCatalystsValid(int entryIndex, CrafterEntry entry, CrafterRecipeInfo info) {
        if (!info.requiresCatalysts()) return true;

        if (entry.needsCatalystValidation()) validateCatalysts(entryIndex);

        return entry.getCatalystValidationFailureState() == null;
    }

    /**
     * Check if the entry has sufficient catalyst items in its internal inventory.
     * <p>
     * Uses 1:1 slot mapping: each recipe slot that requires a catalyst (REUSABLE or DUPLICATION)
     * must have the corresponding item in the same slot of the internal inventory.
     * <p>
     * Uses inclusive NBT matching: the actual item can have additional NBT tags beyond
     * what the recipe requires.
     * 
     * @param entry The crafter entry to check
     * @param info The recipe info with catalyst requirements
     * @return List of error details for missing or wrong catalysts, or empty if all catalysts are valid
     */
    private List<ITextComponent> getCatalystErrorDetails(CrafterEntry entry, CrafterRecipeInfo info) {
        List<ITextComponent> errorDetails = new ArrayList<>();

        // Check each catalyst slot using 1:1 mapping (recipe slot X = internal inventory slot X)
        for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
            if (catalyst.getItem() == null) continue;
            
            int recipeSlot = catalyst.getSlotIndex();
            ItemStack required = catalyst.getItem().createItemStack();
            ItemStack available = entry.getCatalystStack(recipeSlot);
            
            if (available.isEmpty()) {
                errorDetails.add(CrafterEntry.CrafterErrorState.MISSING_CATALYST.asComponent(
                    recipeSlot + 1, required.getDisplayName()));
                continue;
            }
            
            // Use inclusive NBT matching
            if (!areItemStacksMatchingIncludingNbt(required, available)) {
                errorDetails.add(CrafterEntry.CrafterErrorState.WRONG_CATALYST.asComponent(
                    recipeSlot + 1, required.getDisplayName(), available.getDisplayName()));
            }
        }
        
        return errorDetails;
    }

    @Nullable
    private ItemStackKey getResourceKey(CrafterEntry entry, CrafterRecipeInfo.IngredientInfo ingredient) {
        IAEItemStack item = ingredient.getItem();
        if (item == null) return null;

        boolean durability = ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY;
        return new ItemStackKey(item, durability, durability && entry.isFuzzyDurabilityEnabled(),
            durability ? ingredient.getDurabilityPerCraft() : 0);
    }

    @Nullable
    private ItemStack getResourceStack(CrafterEntry entry, CrafterRecipeInfo info, ItemStackKey key) {
        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            if (!key.equals(getResourceKey(entry, ingredient)) || ingredient.getItem() == null) continue;

            return ingredient.getItem().createItemStack();
        }

        return null;
    }

    /**
     * Get the remaining durability of a leftover item in the internal inventory.
     * Internal inventory mirrors the crafting grid 1:1 (slot 0-8 = crafting slot 0-8).
     * 
     * @param entry The crafter entry
     * @param recipeSlotIndex The recipe slot index (0-8)
     * @param expectedItem The expected item type (durability items can vary in damage)
     * @return The remaining durability, or 0 if no matching leftover found
     */
    private int getLeftoverDurability(CrafterEntry entry, int recipeSlotIndex,
                                      IAEItemStack expectedItem) {
        if (recipeSlotIndex < 0 || recipeSlotIndex >= CrafterEntry.CATALYST_SLOTS) return 0;

        ItemStack leftover = entry.getCatalystStack(recipeSlotIndex);
        if (leftover.isEmpty()) return 0;

        ItemStack expected = expectedItem.createItemStack();
        // Match the item and NBT while allowing the durability damage to differ
        if (!areDamageableItemStacksMatching(expected, leftover)) return 0;

        // Calculate the remaining durability
        return getRemainingDurability(leftover);
    }

    private int getRemainingDurability(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxDamage() <= 0) return 0;

        return Math.max(0, stack.getMaxDamage() - stack.getItemDamage());
    }

    private long getAvailableDurability(@Nullable IMEMonitor<IAEItemStack> itemStorage,
                                        CrafterEntry entry,
                                        CrafterRecipeInfo.IngredientInfo ingredient) {
        long availableDurability = 0L;
        for (IAEItemStack variant : getDurabilityVariants(itemStorage, entry, ingredient)) {
            int durabilityPerItem = getRemainingDurability(variant.createItemStack());
            long durabilityPerStack = SaturatingMath.saturatingMultiply(durabilityPerItem, variant.getStackSize());
            availableDurability = SaturatingMath.saturatingAdd(availableDurability, durabilityPerStack);
        }

        return availableDurability;
    }

    private List<IAEItemStack> getDurabilityVariants(@Nullable IMEMonitor<IAEItemStack> itemStorage,
                                                      CrafterEntry entry,
                                                      CrafterRecipeInfo.IngredientInfo ingredient) {
        IAEItemStack item = ingredient.getItem();
        IItemList<IAEItemStack> storageList = getNetworkStorageList(itemStorage);
        if (item == null || storageList == null) return Collections.emptyList();

        Collection<IAEItemStack> matches;
        if (entry.isFuzzyDurabilityEnabled()) {
            matches = storageList.findFuzzy(item, FuzzyMode.IGNORE_ALL);
        } else {
            IAEItemStack precise = storageList.findPrecise(item);
            matches = precise == null ? Collections.emptyList() : Collections.singletonList(precise);
        }

        ItemStack required = item.createItemStack();
        List<IAEItemStack> variants = new ArrayList<>();
        for (IAEItemStack match : matches) {
            if (match == null || match.getStackSize() <= 0L) continue;
            if (!areDamageableItemStacksMatching(required, match.createItemStack())) continue;

            variants.add(match.copy());
        }

        variants.sort(Comparator.comparingInt(
            (IAEItemStack variant) -> variant.createItemStack().getItemDamage()
        ).reversed());
        return variants;
    }

    /**
     * Extract required inputs from the network.
     * <p>
     * For most items: 1 item per slot per craft.
     * For DURABILITY items, damaged variants are selected before undamaged variants.
     * 
     * @param crafts Number of individual crafts to perform
     * @return The extracted durability variants, or null when an extraction failed.
     */
    @Nullable
    private DurabilityExtraction extractInputs(@Nullable IMEMonitor<IAEItemStack> itemStorage,
                                               CrafterEntry entry,
                                               CrafterRecipeInfo info,
                                               long crafts) {
        List<IAEItemStack> extracted = new ArrayList<>();
        DurabilityExtraction durabilityExtraction = new DurabilityExtraction();

        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            IAEItemStack item = ingredient.getItem();
            if (item == null) continue;
            if (ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY) continue;

            IAEItemStack request = item.copy();
            request.setStackSize(crafts);
            IAEItemStack result = tryExtractFromNetwork(itemStorage, request, Actionable.MODULATE);
            long extractedCount = result != null ? result.getStackSize() : 0L;

            if (result == null || extractedCount != request.getStackSize()) {
                if (result != null && extractedCount > 0L) extracted.add(result);

                restoreExtractedInputs(itemStorage, extracted);
                AE2PowerTools.LOGGER.warn("AutoCrafter at {} failed to extract {} {} after simulation" +
                    " (got {}). This should NEVER happen, something is VERY broken!",
                    pos, extractedCount, request.createItemStack().getDisplayName(), request.getStackSize());
                return null;
            }

            extracted.add(result);
        }

        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            if (ingredient.getType() != CrafterRecipeInfo.IngredientType.DURABILITY) continue;

            long requiredDurability = SaturatingMath.saturatingMultiply(crafts, ingredient.getDurabilityPerCraft());
            int leftover = getLeftoverDurability(entry, ingredient.getSlotIndex(), ingredient.getItem());
            long remainingDurability = Math.max(0L, requiredDurability - leftover);

            for (IAEItemStack variant : getDurabilityVariants(itemStorage, entry, ingredient)) {
                if (remainingDurability <= 0L) break;

                int durabilityPerItem = getRemainingDurability(variant.createItemStack());
                if (durabilityPerItem <= 0) continue;

                long neededCount = CrafterMath.ceilDivPositive(remainingDurability, durabilityPerItem);
                long requestedCount = Math.min(variant.getStackSize(), neededCount);
                IAEItemStack request = variant.copy();
                request.setStackSize(requestedCount);

                IAEItemStack result = tryExtractFromNetwork(itemStorage, request, Actionable.MODULATE);
                long extractedCount = result != null ? result.getStackSize() : 0L;
                if (result == null || extractedCount != requestedCount) {
                    if (result != null && extractedCount > 0L) extracted.add(result);

                    AE2PowerTools.LOGGER.warn("AutoCrafter at {} failed to extract {} {} after simulation" +
                        " (got {}). This should NEVER happen, something is VERY broken!",
                        pos, extractedCount, request.createItemStack().getDisplayName(), requestedCount);
                    restoreExtractedInputs(itemStorage, extracted);

                    return null;
                }

                extracted.add(result);
                durabilityExtraction.add(ingredient.getSlotIndex(), result);
                remainingDurability = Math.max(0L, remainingDurability -
                    SaturatingMath.saturatingMultiply(durabilityPerItem, extractedCount));
            }

            if (remainingDurability > 0L) {
                restoreExtractedInputs(itemStorage, extracted);
                AE2PowerTools.LOGGER.warn("AutoCrafter at {} could not select enough durability from the network", pos);
                return null;
            }
        }

        return durabilityExtraction;
    }

    /**
     * Returns inputs after an unexpected partial extraction.
     */
    private void restoreExtractedInputs(@Nullable IMEMonitor<IAEItemStack> itemStorage,
                                        List<IAEItemStack> extracted) {
        for (IAEItemStack stack : extracted) {
            IAEItemStack remaining = tryInsertIntoNetwork(itemStorage, stack, Actionable.MODULATE);
            if (remaining.getStackSize() > 0L) {
                AE2PowerTools.LOGGER.warn("AutoCrafter at {} could not restore {} {} after partial extraction" +
                    " ({} remaining). Wtf is going on?",
                    pos, stack.getStackSize(), stack.createItemStack().getDisplayName(), remaining.getStackSize());
            }
        }
    }

    /**
     * Perform the craft operation using cached recipe info.
     * <p>
     * This method does NOT re-simulate the recipe. All ingredient types and outputs
     * are determined from the cached CrafterRecipeInfo which was computed once
     * when the pattern was inserted.
     * 
     * @param crafts Number of individual crafts to perform
     * @return List of all outputs (main output + transformed items)
     */
    @Nonnull
    private List<IAEItemStack> performCraftInternal(CrafterEntry entry, CrafterRecipeInfo info, long crafts,
                                                    DurabilityExtraction durabilityExtraction) {
        List<IAEItemStack> outputs = new ArrayList<>();

        // Add main recipe outputs (scaled by craft count)
        for (IAEItemStack output : info.getOutputs()) {
            // Skip transformed item outputs (they're part of ingredient info)
            // We only want the primary crafting result(s) here
            boolean isTransformedOutput = false;
            for (CrafterRecipeInfo.IngredientInfo ing : info.getIngredients()) {
                if (ing.getType() == CrafterRecipeInfo.IngredientType.TRANSFORMED) {
                    IAEItemStack remaining = ing.getRemainingItem();
                    if (remaining != null && remaining.isSameType(output)) {
                        isTransformedOutput = true;
                        break;
                    }
                }
            }
            
            if (!isTransformedOutput) {
                IAEItemStack scaledOutput = output.copy();
                scaledOutput.setStackSize(SaturatingMath.saturatingMultiply(output.getStackSize(), crafts));
                outputs.add(scaledOutput);
            }
        }

        // Process each ingredient based on its type
        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getIngredients()) {
            switch (ingredient.getType()) {
                case CONSUMED:
                    // Already extracted from network, nothing more to do
                    break;

                case REUSABLE:
                    // Item stays in internal inventory, nothing to do
                    break;

                case DURABILITY:
                    // Process durability items: use leftover first, then extracted items
                    // Store final survivor back in catalyst slot
                    List<IAEItemStack> extractedVariants = durabilityExtraction.get(ingredient.getSlotIndex());
                    processDurabilityItems(entry, ingredient, crafts, extractedVariants);
                    break;

                case TRANSFORMED:
                    // Add transformed item to outputs
                    IAEItemStack remaining = ingredient.getRemainingItem();
                    if (remaining != null) {
                        IAEItemStack scaledRemaining = remaining.copy();
                        scaledRemaining.setStackSize(crafts);
                        outputs.add(scaledRemaining);
                    }
                    break;

                case DUPLICATION:
                    // Item is in internal inventory (catalyst), appears in outputs
                    // The outputs already include the duplicated items, nothing extra to do
                    break;
            }
        }

        return outputs;
    }

    /**
     * Process durability items: use leftover from internal inventory first, then extracted items.
     * Stores the single surviving item (if any) back in the internal inventory slot.
     * <p>
     * Internal inventory mirrors the crafting grid 1:1 (slot 0-8 = crafting slot 0-8).
     * A durability item's leftover goes in the same slot as its recipe position.
     * 
     * @param entry The crafter entry (to access/update internal inventory)
     * @param ingredient The durability ingredient being processed
     * @param crafts Number of individual crafts being performed
     * @param extractedVariants The tool variants selected from the network
     */
    private void processDurabilityItems(CrafterEntry entry, CrafterRecipeInfo.IngredientInfo ingredient,
                                        long crafts, List<IAEItemStack> extractedVariants) {
        if (ingredient.getItem() == null) return;

        ItemStack template = ingredient.getItem().createItemStack();
        int recipeSlot = ingredient.getSlotIndex();
        ItemStack leftover = entry.getCatalystStack(recipeSlot);
        long totalDurability = getLeftoverDurability(entry, recipeSlot, ingredient.getItem());
        ItemStack survivorTemplate = areDamageableItemStacksMatching(template, leftover)
            ? leftover : ItemStack.EMPTY;

        for (IAEItemStack variant : extractedVariants) {
            ItemStack variantStack = variant.createItemStack();
            int durabilityPerItem = getRemainingDurability(variantStack);
            if (durabilityPerItem <= 0) continue;

            long durabilityPerStack = SaturatingMath.saturatingMultiply(durabilityPerItem, variant.getStackSize());
            totalDurability = SaturatingMath.saturatingAdd(totalDurability, durabilityPerStack);
            survivorTemplate = variantStack;
        }

        long requiredDurability = SaturatingMath.saturatingMultiply(crafts, ingredient.getDurabilityPerCraft());
        long remainingDurability = Math.max(0L, totalDurability - requiredDurability);
        if (remainingDurability > 0L && !survivorTemplate.isEmpty()) {
            ItemStack survivor = survivorTemplate.copy();
            survivor.setCount(1);
            survivor.setItemDamage(Math.max(0, survivor.getMaxDamage() -
                Math.min((int) remainingDurability, survivor.getMaxDamage())));
            entry.setCatalystStack(recipeSlot, survivor);
        } else {
            // All items broke, clear the slot
            entry.setCatalystStack(recipeSlot, ItemStack.EMPTY);
        }

        markDirty();
    }

    // ==================== NETWORK OPERATIONS ====================

    @Nullable
    private IAEItemStack tryInsertIntoNetwork(@Nullable IMEMonitor<IAEItemStack> itemStorage,
                                              IAEItemStack stack,
                                              Actionable mode) {
        if (itemStorage == null) return stack;

        return itemStorage.injectItems(stack, mode, actionSource);
    }

    @Nullable
    private IAEItemStack tryExtractFromNetwork(@Nullable IMEMonitor<IAEItemStack> itemStorage,
                                               IAEItemStack stack,
                                               Actionable mode) {
        if (itemStorage == null) return null;

        return itemStorage.extractItems(stack, mode, actionSource);
    }

    @Nullable
    private IMEMonitor<IAEItemStack> getItemStorageMonitor() {
        try {
            IGrid grid = gridProxy.getGrid();
            if (grid == null) return null;

            IStorageGrid storage = grid.getCache(IStorageGrid.class);

            return storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    /**
     * Get the network storage list for batch lookups.
     * Call this ONCE and reuse the list for multiple findPrecise() calls.
     * 
     * @return The storage list, or null if network is unavailable
     */
    @Nullable
    private IItemList<IAEItemStack> getNetworkStorageList() {
        return getNetworkStorageList(getItemStorageMonitor());
    }

    @Nullable
    private IItemList<IAEItemStack> getNetworkStorageList(@Nullable IMEMonitor<IAEItemStack> itemStorage) {
        if (itemStorage == null) return null;

        return itemStorage.getStorageList();
    }

    @Nullable
    private IAEItemStack findPreciseInStorage(@Nullable IItemList<IAEItemStack> storageList, IAEItemStack item) {
        if (storageList == null) return null;

        return storageList.findPrecise(item);
    }

    /**
     * Get the quantity of an item available in the network.
     * <p>
     * Note: For performance when processing multiple items, use getNetworkStorageList()
     * once and call findPrecise() on it directly. This method is for one-off queries.
     */
    private long getNetworkQuantity(@Nullable IItemList<IAEItemStack> storageList, IAEItemStack item) {
        if (storageList == null) return 0;

        IAEItemStack found = findPreciseInStorage(storageList, item);
        return found != null ? found.getStackSize() : 0;
    }

    // ==================== PATTERN SIMULATION ====================

    @Nullable
    private FakePlayer getOrCreateFakePlayer() {
        if (world == null || !(world instanceof WorldServer)) return null;

        if (fakePlayer == null) fakePlayer = FakePlayerFactory.get((WorldServer) world, CRAFTER_PROFILE);

        return fakePlayer;
    }

    /**
     * Simulate a pattern and cache the recipe info.
     * Resets state to IDLE on success (for new patterns).
     */
    public void simulatePattern(int entryIndex, ItemStack patternStack) {
        simulatePattern(entryIndex, patternStack, true);
    }

    /**
     * Simulate a pattern and cache the recipe info.
     * 
     * @param entryIndex The entry index (0-11)
     * @param patternStack The pattern to simulate, or null to clear
     * @param resetState If true, set state to IDLE on success. If false, preserve existing state
     *                   (used on world load to keep persisted MISSING_INPUT etc.)
     */
    public void simulatePattern(int entryIndex, @Nullable ItemStack patternStack, boolean resetState) {
        if (entryIndex < 0 || entryIndex >= entries.size()) return;

        CrafterEntry entry = entries.get(entryIndex);
        entry.resetState(entry.getState());
        entry.setPatternStack(patternStack);
        if (resetState) entry.setFuzzyDurabilityEnabled(true);

        if (patternStack == null || patternStack.isEmpty()) {
            return;
        }

        // Get pattern details
        if (!(patternStack.getItem() instanceof ICraftingPatternItem)) {
            entry.setRecipeInfo(new CrafterRecipeInfo(CrafterEntry.CrafterErrorState.NOT_PATTERN));
            return;
        }

        ICraftingPatternItem patternItem = (ICraftingPatternItem) patternStack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(patternStack, world);

        if (details == null) {
            entry.setRecipeInfo(new CrafterRecipeInfo(CrafterEntry.CrafterErrorState.INVALID_PATTERN));
            return;
        }

        // Only crafting patterns are allowed
        if (!details.isCraftable()) {
            entry.setRecipeInfo(new CrafterRecipeInfo(CrafterEntry.CrafterErrorState.PROCESSING_PATTERN));
            return;
        }

        // Analyze the recipe
        CrafterRecipeInfo info = analyzeRecipe(details);
        entry.setRecipeInfo(info);

        if (info.isValid() && resetState) {
            // Only reset to IDLE when inserting a new pattern, not on world load
            entry.resetState(CrafterState.IDLE);
        }

        if (info.isValid() && info.requiresCatalysts()) {
            if (resetState) validateCatalysts(entryIndex);
        } else if (info.isValid()) {
            entry.clearCatalystValidationFailure();
        }

        // If !resetState and info.isValid(), keep the existing state (e.g., MISSING_INPUT from NBT)

        markDirty();
    }

    /**
     * Analyze a crafting pattern to determine ingredient types.
     * <p>
     * This is the ONLY place where recipe simulation happens. The results are cached
     * in CrafterRecipeInfo to avoid expensive simulation on every craft operation.
     * <p>
     * Ingredient types are determined by actually simulating the recipe and checking
     * what items remain in each slot after crafting:
     * - CONSUMED: Slot is empty after crafting
     * - REUSABLE: Same item (same Item, metadata, and NBT) remains unchanged
     * - DURABILITY: Same item type but with different damage value remains
     * - TRANSFORMED: Different item remains (e.g., filled bucket -> empty bucket)
     * - DUPLICATION: Input appears in the recipe outputs
     */
    private CrafterRecipeInfo analyzeRecipe(ICraftingPatternDetails details) {
        List<CrafterRecipeInfo.IngredientInfo> ingredients = new ArrayList<>();
        List<IAEItemStack> outputs = new ArrayList<>();

        IAEItemStack[] inputs = details.getInputs();

        // Set up crafting grid with inputs (no catalyst substitution for initial analysis)
        InventoryCrafting craftMatrix = buildCraftingMatrix(details, null, null);
        if (craftMatrix == null) return new CrafterRecipeInfo(CrafterEntry.CrafterErrorState.INVALID_PATTERN);

        // Find matching recipe
        IRecipe recipe = CraftingManager.findMatchingRecipe(craftMatrix, world);
        if (recipe == null) return new CrafterRecipeInfo(CrafterEntry.CrafterErrorState.NO_RECIPE);

        // Get recipe output
        ItemStack recipeOutput = recipe.getCraftingResult(craftMatrix);
        if (recipeOutput.isEmpty()) return new CrafterRecipeInfo(CrafterEntry.CrafterErrorState.NO_OUTPUT);

        // Add main output
        IAEItemStack mainOutput = AEItemStack.fromItemStack(recipeOutput);
        if (mainOutput != null) outputs.add(mainOutput);

        // Track how many copies of each output type can stay in catalyst inventory.
        // Any matching input beyond this allowance remains a normal consumed ingredient.
        Map<ItemStackKey, Long> duplicationAllowanceByKey = new HashMap<>();
        if (mainOutput != null) {
            ItemStackKey key = new ItemStackKey(mainOutput);
            duplicationAllowanceByKey.put(key, mainOutput.getStackSize());
        }

        // Track how many output items are retained internally so the synced recipe output and
        // actual network insertion only expose the net gain beyond the preserved catalyst copy.
        Map<ItemStackKey, Long> duplicatedInputsByKey = new HashMap<>();

        // Get remaining items after crafting (getRemainingItems returns what each input slot becomes)
        NonNullList<ItemStack> remainingItems = recipe.getRemainingItems(craftMatrix);

        // Analyze each input slot
        for (int i = 0; i < 9 && i < inputs.length; i++) {
            IAEItemStack input = inputs[i];
            if (input == null) continue;

            ItemStack inputStack = input.createItemStack();
            inputStack.setCount(1); // Crafting recipes only use 1 item per slot

            ItemStack remaining = (i < remainingItems.size()) ? remainingItems.get(i) : ItemStack.EMPTY;
            CrafterRecipeInfo.IngredientType type;
            IAEItemStack remainingAE = null;
            int durabilityPerCraft = 1; // Default for non-durability items

            if (remaining.isEmpty()) {
                // Nothing remains - treat matching outputs as duplication only while there is
                // still enough output count left to replenish that catalyst slot.
                ItemStackKey inputKey = new ItemStackKey(input);
                Long outputCount = duplicationAllowanceByKey.get(inputKey);
                if (outputCount != null && outputCount >= 1) {
                    type = CrafterRecipeInfo.IngredientType.DUPLICATION;
                    duplicationAllowanceByKey.put(inputKey, outputCount - 1);
                    duplicatedInputsByKey.merge(inputKey, 1L, SaturatingMath::saturatingAdd);
                } else {
                    type = CrafterRecipeInfo.IngredientType.CONSUMED;
                }

            } else if (areItemStacksIdentical(inputStack, remaining)) {
                // Exact same item remains (including NBT) - REUSABLE
                type = CrafterRecipeInfo.IngredientType.REUSABLE;
            } else if (inputStack.getItem() == remaining.getItem() && remaining.isItemStackDamageable()) {
                // Same item type but damageable - check if it's durability damage
                if (remaining.getItemDamage() > inputStack.getItemDamage()) {
                    type = CrafterRecipeInfo.IngredientType.DURABILITY;
                    // Calculate how much durability was consumed per craft
                    durabilityPerCraft = remaining.getItemDamage() - inputStack.getItemDamage();
                } else {
                    // Item was somehow repaired? Treat as REUSABLE
                    type = CrafterRecipeInfo.IngredientType.REUSABLE;
                }

            } else {
                // Different item remains - TRANSFORMED (e.g., filled bucket -> empty bucket)
                type = CrafterRecipeInfo.IngredientType.TRANSFORMED;
                remainingAE = AEItemStack.fromItemStack(remaining);
                
                // The transformed item is an additional output
                if (remainingAE != null) outputs.add(remainingAE.copy());
            }

            ingredients.add(new CrafterRecipeInfo.IngredientInfo(
                    input.copy(), i, type, 1, remainingAE, durabilityPerCraft));
        }

        subtractDuplicatedInputsFromOutputs(outputs, duplicatedInputsByKey);
        if (outputs.isEmpty()) return new CrafterRecipeInfo(CrafterEntry.CrafterErrorState.NO_OUTPUT);

        return new CrafterRecipeInfo(ingredients, outputs);
    }

    private void subtractDuplicatedInputsFromOutputs(List<IAEItemStack> outputs,
                                                     Map<ItemStackKey, Long> duplicatedInputsByKey) {
        if (duplicatedInputsByKey.isEmpty()) return;

        for (int i = 0; i < outputs.size(); i++) {
            IAEItemStack output = outputs.get(i);
            if (output == null) continue;

            ItemStackKey outputKey = new ItemStackKey(output);
            long toSubtract = duplicatedInputsByKey.getOrDefault(outputKey, 0L);
            if (toSubtract <= 0L) continue;

            long remainingCount = output.getStackSize() - toSubtract;
            if (remainingCount > 0L) {
                output.setStackSize(remainingCount);
                duplicatedInputsByKey.remove(outputKey);
                continue;
            }

            outputs.remove(i--);

            if (remainingCount < 0L) {
                duplicatedInputsByKey.put(outputKey, -remainingCount);
            } else {
                duplicatedInputsByKey.remove(outputKey);
            }
        }
    }

    /**
     * Checks if two ItemStacks are completely identical (Item, metadata, count, and NBT).
     * More strict than ItemStack.areItemStacksEqual which doesn't check NBT properly.
     */
    private boolean areItemStacksIdentical(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        if (a.getMetadata() != b.getMetadata()) return false;
        
        // Check NBT
        NBTTagCompound tagA = a.getTagCompound();
        NBTTagCompound tagB = b.getTagCompound();
        if (tagA == null && tagB == null) return true;
        if (tagA == null || tagB == null) return false;
        return tagA.equals(tagB);
    }

    /**
     * Checks if an item matches a recipe requirement using "including" NBT matching.
     * This means the recipe's NBT must be a subset of the item's NBT - the item can have
     * additional NBT tags beyond what the recipe requires.
     * 
     * @param required The recipe requirement (what we need)
     * @param actual The actual item (what we have)
     * @return true if actual matches required, including NBT subset check
     */
    private boolean areItemStacksMatchingIncludingNbt(ItemStack required, ItemStack actual) {
        if (required.isEmpty() && actual.isEmpty()) return true;
        if (required.isEmpty() || actual.isEmpty()) return false;
        if (required.getItem() != actual.getItem()) return false;
        if (required.getMetadata() != actual.getMetadata()) return false;

        // "Including" NBT check: recipe NBT must be subset of item NBT
        NBTTagCompound requiredTag = required.getTagCompound();
        NBTTagCompound actualTag = actual.getTagCompound();

        // If recipe requires no NBT, any item NBT is acceptable
        if (requiredTag == null || requiredTag.isEmpty()) return true;

        // If recipe requires NBT but item has none, fail
        if (actualTag == null) return false;

        // Check that all keys in requiredTag exist in actualTag with same values
        return isNbtSubset(requiredTag, actualTag);
    }

    private boolean areDamageableItemStacksMatching(ItemStack required, ItemStack actual) {
        if (required.isEmpty() || actual.isEmpty()) return false;
        if (required.getItem() != actual.getItem()) return false;

        return ItemStack.areItemStackTagsEqual(required, actual);
    }

    /**
     * Checks if 'subset' NBT is contained within 'superset' NBT.
     * All keys in subset must exist in superset with equal values.
     */
    private boolean isNbtSubset(NBTTagCompound subset, NBTTagCompound superset) {
        for (String key : subset.getKeySet()) {
            if (!superset.hasKey(key)) return false;

            // Must have same tag type and value
            if (subset.getTagId(key) != superset.getTagId(key)) return false;
            if (!subset.getTag(key).equals(superset.getTag(key))) return false;
        }

        return true;
    }

    // ==================== ENTRY ACCESS ====================

    public CrafterEntry getEntry(int index) {
        if (index < 0 || index >= entries.size()) return null;

        return entries.get(index);
    }

    public List<CrafterEntry> getEntries() {
        return entries;
    }

    public boolean hasOperationTimingSamples() {
        refreshProbeTimingSnapshot();
        return probeTimingSnapshotHasSample;
    }

    public long getLastOperationDurationNanos() {
        refreshProbeTimingSnapshot();
        return probeLastDurationNanos;
    }

    public long getAverageOperationDurationNanos() {
        refreshProbeTimingSnapshot();
        return probeAverageDurationNanos;
    }

    public long getMaxOperationDurationNanos() {
        refreshProbeTimingSnapshot();
        return probeMaxDurationNanos;
    }

    private void refreshProbeTimingSnapshot() {
        boolean hasSamples = operationTimingStats.hasSamples();

        if (world != null) {
            long worldTime = world.getTotalWorldTime();
            if (probeTimingSnapshotHasSample == hasSamples
                    && worldTime - probeTimingSnapshotTick < PROBE_TIMING_REFRESH_INTERVAL) {
                return;
            }

            probeTimingSnapshotTick = worldTime;
        }

        probeTimingSnapshotHasSample = hasSamples;
        probeLastDurationNanos = operationTimingStats.getLastDurationNanos();
        probeAverageDurationNanos = operationTimingStats.getAverageDurationNanos();
        probeMaxDurationNanos = operationTimingStats.getMaxDurationNanos();
    }

    public int getSpeedTicks() {
        return speedTicks;
    }

    public void setSpeedTicks(int speedTicks) {
        this.speedTicks = Math.max(MIN_SPEED_TICKS, speedTicks);
        markDirty();
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(MIN_BATCH_SIZE, batchSize);
        markDirty();
    }

    /**
     * Gets the current GUI page (0-11).
     * Persisted per-block until world load.
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Sets the current GUI page (0-11).
     * Persisted per-block until world load.
     */
    public void setCurrentPage(int page) {
        if (page < 0 || page >= ENTRY_COUNT) return;

        this.currentPage = page;
        // Don't markDirty() - this is intentionally NOT saved to NBT.
        // ContainerAutoCrafter.detectAndSendChanges picks up the page change next tick
        // and propagates it to all listeners via @GuiSync.
    }

    // ==================== UPGRADES ====================

    public ItemStack getUpgradeStack(int slot) {
        if (slot < 0 || slot >= UPGRADE_SLOTS) return ItemStack.EMPTY;

        return upgradeInventory[slot];
    }

    public void setUpgradeStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= UPGRADE_SLOTS) return;

        // Track current tier before change for block state update
        int oldTier = getUpgradeTier();

        upgradeInventory[slot] = stack == null ? ItemStack.EMPTY : stack;

        // If tier changed, immediately update block state on client.
        // markForUpdate is required here because tier affects the block model (not just GUI),
        // which must be re-rendered for non-GUI viewers via the block update packet.
        int newTier = getUpgradeTier();
        if (oldTier != newTier) markForUpdate();

        markDirty();
    }

    public ItemStack[] getUpgradeInventory() {
        return upgradeInventory;
    }

    /**
     * Gets the effective batch multiplier from installed speed upgrades.
     * Speed upgrades are NOT compatible with each other - only the highest tier takes effect.
     * 
     * @return The batch multiplier (1 if no speed upgrades installed)
     */
    public int getUpgradeBatchMultiplier() {
        int maxMultiplier = 1;

        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = upgradeInventory[i];
            if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(stack)) {
                int multiplier = ItemCrafterSpeedUpgrade.getMultiplier(stack);
                maxMultiplier = Math.max(maxMultiplier, multiplier);
            }
        }

        return maxMultiplier;
    }

    /**
     * Gets the upgrade tier (0 if no speed upgrade, 1-4 for tier I-IV).
     * Used for block model dispatch.
     * 
     * @return The tier of the highest installed speed upgrade (0-4)
     */
    public int getUpgradeTier() {
        int maxTier = 0;

        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = upgradeInventory[i];
            if (ItemCrafterSpeedUpgrade.isSpeedUpgrade(stack)) {
                int tier = ItemCrafterSpeedUpgrade.getTier(stack) + 1; // getTier returns 0-3, we want 1-4
                maxTier = Math.max(maxTier, tier);
            }
        }

        return maxTier;
    }

    /**
     * Gets the effective max batch size (base config * user batch size * upgrade multiplier).
     * This is the max batch size that can be requested in allocateResourcesFairly.
     * <p>
     * Formula: baseCraftsPerOperation (config) * batchSize (user) * upgradeMultiplier
     */
    public long getEffectiveMaxBatchSize() {
        long baseBatchSize = SaturatingMath.saturatingMultiply(getBaseCraftsPerOperation(), batchSize);

        return SaturatingMath.saturatingMultiply(baseBatchSize, getUpgradeBatchMultiplier());
    }

    /**
     * Toggle the enabled state of an entry.
     */
    public void toggleEntry(int index) {
        if (index < 0 || index >= entries.size()) return;

        CrafterEntry entry = entries.get(index);
        entry.setEnabled(!entry.isEnabled());
        markDirty();
    }

    /**
     * Toggle damaged-tool use for a durability recipe.
     */
    public void toggleFuzzyDurability(int index) {
        if (index < 0 || index >= entries.size()) return;

        CrafterEntry entry = entries.get(index);
        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null || !info.hasDurabilityItems()) return;

        entry.setFuzzyDurabilityEnabled(!entry.isFuzzyDurabilityEnabled());
        markDirty();
    }

    /**
     * Clear an entry (remove pattern and reset).
     */
    public void clearEntry(int index) {
        if (index < 0 || index >= entries.size()) return;

        CrafterEntry entry = entries.get(index);
        entry.setPatternStack(null);
        entry.setEnabled(true);
        entry.clearPendingOutputs();

        for (int i = 0; i < CrafterEntry.CATALYST_SLOTS; i++) entry.setCatalystStack(i, ItemStack.EMPTY);

        markDirty();
    }

    /**
     * Inserts one held item into the first empty catalyst slot that expects it.
     * Uses the same inclusive NBT matching as catalyst validation so in-world
     * quick insert follows the GUI's acceptance rules.
     */
    public boolean tryQuickInsertCatalyst(ItemStack heldItem) {
        if (heldItem.isEmpty()) return false;

        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            CrafterEntry entry = entries.get(entryIndex);
            if (!entry.hasValidRecipeInfo()) continue;

            CrafterRecipeInfo info = entry.getRecipeInfo();
            if (info == null || !info.requiresCatalysts()) continue;

            for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
                int catalystIndex = catalyst.getSlotIndex();
                if (catalystIndex < 0 || catalystIndex >= CrafterEntry.CATALYST_SLOTS) continue;
                if (!entry.getCatalystStack(catalystIndex).isEmpty()) continue;

                ItemStack expectedItem = catalyst.getItem() != null
                        ? catalyst.getItem().createItemStack()
                        : ItemStack.EMPTY;
                if (expectedItem.isEmpty()) continue;
                if (!areItemStacksMatchingIncludingNbt(expectedItem, heldItem)) continue;

                ItemStack toInsert = heldItem.copy();
                toInsert.setCount(1);

                entry.setCatalystStack(catalystIndex, toInsert);
                heldItem.shrink(1);
                validateCatalysts(entryIndex);
                return true;
            }
        }

        return false;
    }

    /**
     * Validate that the current catalysts are still valid for the recipe.
     * <p>
     * This is called when a catalyst is changed (inserted/removed) to ensure the player
     * cannot swap in a cheaper item. If the recipe still works with the current catalysts,
     * the state is updated. If the recipe would fail, the state is set to SIMULATION_FAILED.
     * <p>
     * Reuses the recipe simulation logic from analyzeRecipe, substituting actual catalyst items.
     */
    public void validateCatalysts(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entries.size()) return;

        CrafterEntry entry = entries.get(entryIndex);
        if (!entry.hasPattern() || !entry.hasValidRecipeInfo()) {
            entry.clearCatalystValidationFailure();
            return;
        }

        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null || !info.requiresCatalysts()) {
            entry.clearCatalystValidationFailure();
            return;
        }

        ItemStack patternStack = entry.getPatternStack();
        if (patternStack == null || patternStack.isEmpty()) {
            entry.clearCatalystValidationFailure();
            return;
        }
        if (!(patternStack.getItem() instanceof ICraftingPatternItem)) {
            entry.clearCatalystValidationFailure();
            return;
        }

        ICraftingPatternItem patternItem = (ICraftingPatternItem) patternStack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(patternStack, world);
        if (details == null || !details.isCraftable()) {
            entry.clearCatalystValidationFailure();
            return;
        }

        // Build crafting matrix with actual catalyst items substituted
        InventoryCrafting craftMatrix = buildCraftingMatrix(details, entry, info);
        if (craftMatrix == null) {
            // Missing catalyst - recipe cannot work
            entry.resetState(CrafterState.MISSING_CATALYST);
            for (ITextComponent errorDetail : getCatalystErrorDetails(entry, info)) {
                entry.addErrorDetail(errorDetail);
            }

            entry.setCatalystValidationFailure(CrafterState.MISSING_CATALYST);
            markDirty();
            return;
        }

        // Check if recipe still matches with the actual catalysts
        IRecipe recipe = CraftingManager.findMatchingRecipe(craftMatrix, world);
        if (recipe == null) {
            entry.setError(CrafterEntry.CrafterErrorState.CATALYST_RECIPE_MISMATCH);
            entry.setCatalystValidationFailure(CrafterState.SIMULATION_FAILED);
            markDirty();
            return;
        }

        // Verify output is the same
        ItemStack newOutput = recipe.getCraftingResult(craftMatrix);
        IAEItemStack expectedOutput = info.getOutputs().isEmpty() ? null : info.getOutputs().get(0);
        
        if (expectedOutput == null || newOutput.isEmpty()) {
            entry.setError(CrafterEntry.CrafterErrorState.NO_OUTPUT);
            entry.setCatalystValidationFailure(CrafterState.SIMULATION_FAILED);
            markDirty();
            return;
        }

        ItemStack expectedStack = expectedOutput.createItemStack();
        if (expectedStack.getItem() != newOutput.getItem() 
            || expectedStack.getMetadata() != newOutput.getMetadata()) {
            entry.setError(CrafterEntry.CrafterErrorState.OUTPUT_MISMATCH);
            entry.setCatalystValidationFailure(CrafterState.SIMULATION_FAILED);
            markDirty();
            return;
        }

        // Recipe is still valid
        entry.clearCatalystValidationFailure();
        if (entry.isEnabled()) {
            entry.resetState(CrafterState.IDLE);
        } else {
            entry.resetState(entry.getState());
        }

        markDirty();
    }

    /**
     * Build a crafting matrix from pattern details, optionally substituting catalyst items.
     *
     * @param details The pattern details
     * @param entry Optional entry to get actual catalyst items from (null = use pattern inputs)
     * @param info Optional recipe info to identify catalyst slots (null = no substitution)
     * @return The crafting matrix, or null if a required catalyst is missing
     */
    @Nullable
    private InventoryCrafting buildCraftingMatrix(ICraftingPatternDetails details, 
                                                  @Nullable CrafterEntry entry,
                                                  @Nullable CrafterRecipeInfo info) {
        IAEItemStack[] inputs = details.getInputs();
        InventoryCrafting craftMatrix = new InventoryCrafting(new ContainerNull(), 3, 3);

        for (int i = 0; i < 9 && i < inputs.length; i++) {
            if (inputs[i] == null) continue;

            ItemStack itemToUse = null;

            // Check if this slot should use a substituted catalyst
            if (entry != null && info != null) {
                for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
                    if (catalyst.getSlotIndex() == i) {
                        ItemStack catalystStack = entry.getCatalystStack(i);
                        // Missing required catalyst
                        if (catalystStack.isEmpty()) return null;

                        itemToUse = catalystStack.copy();
                        itemToUse.setCount(1);
                        break;
                    }
                }
            }

            // Use pattern input if no catalyst substitution
            if (itemToUse == null) {
                itemToUse = inputs[i].createItemStack();
                itemToUse.setCount(1);
            }

            craftMatrix.setInventorySlotContents(i, itemToUse);
        }

        return craftMatrix;
    }

    // ==================== NBT ====================

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        NBTTagList entryList = new NBTTagList();
        for (CrafterEntry entry : entries) entryList.appendTag(entry.writeToNBT());

        data.setTag("entries", entryList);

        data.setInteger("speed", speedTicks);
        data.setInteger("batch", batchSize);
        data.setLong("tickCounter", tickCounter);

        // Save upgrades inventory
        NBTTagList upgradeList = new NBTTagList();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            NBTTagCompound slotTag = new NBTTagCompound();
            if (!upgradeInventory[i].isEmpty()) upgradeInventory[i].writeToNBT(slotTag);

            upgradeList.appendTag(slotTag);
        }
        data.setTag("upgrades", upgradeList);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        if (data.hasKey("entries")) {
            NBTTagList entryList = data.getTagList("entries", 10);
            for (int i = 0; i < entryList.tagCount() && i < entries.size(); i++) {
                entries.get(i).readFromNBT(entryList.getCompoundTagAt(i));
            }
        }

        speedTicks = data.getInteger("speed");
        if (speedTicks < MIN_SPEED_TICKS) speedTicks = DEFAULT_SPEED_TICKS;

        batchSize = data.getInteger("batch");
        if (batchSize < MIN_BATCH_SIZE) batchSize = DEFAULT_BATCH_SIZE;

        tickCounter = Math.max(0L, data.getLong("tickCounter"));

        // Load upgrades inventory
        if (data.hasKey("upgrades")) {
            NBTTagList upgradeList = data.getTagList("upgrades", 10);
            for (int i = 0; i < UPGRADE_SLOTS && i < upgradeList.tagCount(); i++) {
                NBTTagCompound slotTag = upgradeList.getCompoundTagAt(i);
                if (slotTag.isEmpty()) {
                    upgradeInventory[i] = ItemStack.EMPTY;
                } else {
                    upgradeInventory[i] = new ItemStack(slotTag);
                }
            }
        }
    }

    // ==================== GRID PROXY ====================

    @Override
    public AENetworkProxy getProxy() {
        return gridProxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
        // Nothing to do here - we don't cache any dynamic grid info that needs to be invalidated on change.
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        return gridProxy.getNode();
    }

    @Override
    @Nonnull
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {
        world.destroyBlock(pos, true);
    }

    @Override
    @Nonnull
    public IGridNode getActionableNode() {
        return gridProxy.getNode();
    }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        int oldClientFlags = clientFlags;
        clientFlags = data.readByte() & 0xFF;
        return changed || oldClientFlags != clientFlags;
    }

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeByte(PowerStateClientFlags.collect(gridProxy));
    }

    @Override
    public void onReady() {
        super.onReady();
        gridProxy.onReady();

        // Re-simulate patterns on load (to rebuild recipeInfo from NBT pattern data)
        // Pass resetState=false to preserve the persisted state (e.g., MISSING_INPUT)
        for (int i = 0; i < entries.size(); i++) {
            CrafterEntry entry = entries.get(i);
            if (entry.hasPattern()) simulatePattern(i, entry.getPatternStack(), false);
        }

        // Dry validation pass: update entry states based on current network conditions
        // This ensures MISSING_INPUT etc. are shown immediately without waiting for a craft tick
        validateAllEntries();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        gridProxy.onChunkUnload();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        gridProxy.invalidate();
    }

    @Override
    public void validate() {
        super.validate();
        gridProxy.validate();
    }
}
