package com.ae2powertools.features.crafter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
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
import appeng.util.item.AEItemStack;

import com.ae2powertools.config.PowerToolsServerConfig;
import com.ae2powertools.items.ItemCrafterSpeedUpgrade;


/**
 * Tile entity for the AE2 AutoCrafter block.
 * Automatically crafts items using patterns, with support for reusable/catalyst items.
 */
public class TileAutoCrafter extends AEBaseTile implements ITickable, IActionHost, IGridProxyable {

    public static final int ENTRY_COUNT = 12;
    public static final int MIN_SPEED_TICKS = 20;
    public static final int DEFAULT_SPEED_TICKS = 20;
    public static final int MIN_BATCH_SIZE = 1;
    public static final int DEFAULT_BATCH_SIZE = 1;

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
    private int tickCounter;

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

    public TileAutoCrafter() {
        this.gridProxy = new AENetworkProxy(this, "proxy", this.getItemFromTile(this), true);
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.gridProxy.setIdlePowerUsage(2.0);
        this.actionSource = new MachineSource(this);

        this.entries = new ArrayList<>();
        for (int i = 0; i < ENTRY_COUNT; i++) entries.add(new CrafterEntry());

        this.speedTicks = DEFAULT_SPEED_TICKS;
        this.batchSize = DEFAULT_BATCH_SIZE;
        this.tickCounter = 0;
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
        if (tickCounter % (speedTicks * batchSize) == 0) {
            processPendingOutputs();
            processAllEntries();
        }
    }

    /**
     * Try to insert any pending outputs into the network.
     */
    private void processPendingOutputs() {
        for (CrafterEntry entry : entries) {
            if (!entry.hasPendingOutputs()) continue;

            List<IAEItemStack> pendingList = entry.getPendingOutputs();
            Iterator<IAEItemStack> it = pendingList.iterator();

            while (it.hasNext()) {
                IAEItemStack pending = it.next();
                IAEItemStack remaining = tryInsertIntoNetwork(pending, Actionable.MODULATE);

                if (remaining == null || remaining.getStackSize() == 0) {
                    it.remove();
                } else {
                    pending.setStackSize(remaining.getStackSize());
                }
            }

            if (pendingList.isEmpty()) {
                entry.setState(CrafterState.IDLE);
            }
        }
    }

    /**
     * Process all entries and run crafts where possible.
     * <p>
     * This method processes entries atomically with a shared resource pool:
     * 1. Collect: Gather all entries that want to craft
     * 2. Allocate: Distribute resources fairly among competing entries
     * 3. Extract: Extract all inputs for all entries
     * 4. Craft: Run all crafts using cached recipe info
     * 5. Insert: Insert all outputs into network
     * <p>
     * Fair allocation ensures that if multiple entries need the same resource,
     * each gets a proportional share rather than first-come-first-served.
     */
    private void processAllEntries() {
        // Phase 0: Filter to craftable entries and collect candidates
        List<CraftCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            CrafterEntry entry = entries.get(i);

            // Skip empty entries (no pattern) - distinct from disabled
            if (entry.isEmpty()) {
                updateEntryState(entry, CrafterState.NO_PATTERN);
                continue;
            }

            // Skip disabled entries
            if (!entry.isEnabled()) {
                updateEntryState(entry, CrafterState.DISABLED);
                continue;
            }

            // Skip entries with pending outputs
            if (entry.hasPendingOutputs()) {
                updateEntryState(entry, CrafterState.HOLDING_OUTPUT);
                continue;
            }

            // Check recipe validity
            if (!entry.hasValidRecipeInfo()) {
                updateEntryState(entry, CrafterState.SIMULATION_FAILED);
                continue;
            }

            // Check target quantity
            CrafterRecipeInfo info = entry.getRecipeInfo();
            if (info != null && !info.getOutputs().isEmpty()) {
                IAEItemStack output = info.getOutputs().get(0);
                long currentQty = getNetworkQuantity(output);
                if (currentQty >= entry.getTargetQuantity()) {
                    updateEntryState(entry, CrafterState.IDLE);
                    continue;
                }
            }

            // Clear previous error details
            entry.clearErrorDetails();

            // Check catalysts
            if (info.requiresCatalysts()) {
                List<ITextComponent> catalystErrors = new ArrayList<>();
                if (!hasSufficientCatalysts(entry, info, catalystErrors)) {
                    for (ITextComponent error : catalystErrors) entry.addErrorDetail(error);
                    updateEntryState(entry, CrafterState.MISSING_CATALYST);
                    continue;
                }
            }

            candidates.add(new CraftCandidate(entry, info));
        }

        // No candidates to process
        if (candidates.isEmpty()) return;

        // Phase 0.5: Pre-initialize shared pool with all items needed by all candidates
        // This avoids making 100+ round-trips to the network during allocation
        Map<ItemStackKey, Long> sharedPool = initializeSharedPool(candidates);

        // Phase 1: Fair allocation of shared resources
        // allocateResourcesFairly marks entries with insufficient resources as MISSING_INPUT
        final int effectiveMaxBatchSize = getEffectiveMaxBatchSize();
        List<CraftSimulation> simulations = allocateResourcesFairly(candidates, sharedPool, effectiveMaxBatchSize);

        // If no entries can craft after allocation, we're done
        if (simulations.isEmpty()) return;

        // Phase 2: Extract all inputs from network
        List<CraftSimulation> successfulExtractions = new ArrayList<>();
        for (CraftSimulation sim : simulations) {
            if (extractInputs(sim.entry, sim.info, sim.crafts)) {
                successfulExtractions.add(sim);
            } else {
                // Extraction failed (race condition - should never happen since we allocated based on a shared pool, but just in case)
                updateEntryState(sim.entry, CrafterState.MISSING_INPUT);
            }
        }

        // Phase 3: Run all crafts
        List<CraftResult> results = new ArrayList<>();
        for (CraftSimulation sim : successfulExtractions) {
            List<IAEItemStack> outputs = performCraftInternal(sim.entry, sim.info, sim.crafts);
            if (outputs != null && !outputs.isEmpty()) {
                results.add(new CraftResult(sim.entry, outputs));
            } else {
                updateEntryState(sim.entry, CrafterState.SIMULATION_FAILED);
            }
        }

        // Phase 4: Insert all outputs
        for (CraftResult result : results) {
            boolean anyPending = false;
            for (IAEItemStack output : result.outputs) {
                IAEItemStack remaining = tryInsertIntoNetwork(output, Actionable.MODULATE);
                if (remaining != null && remaining.getStackSize() > 0) {
                    result.entry.addPendingOutput(remaining);
                    anyPending = true;
                }
            }

            result.entry.setLastCraftTick(world.getTotalWorldTime());

            if (anyPending) {
                updateEntryState(result.entry, CrafterState.HOLDING_OUTPUT);
            } else {
                updateEntryState(result.entry, CrafterState.IDLE);
            }

            // Record successful craft metrics
            result.entry.recordMetrics(false, result.entry.getLastRequestedBatchSize(), result.entry.getLastActualBatchSize());
        }
    }

    /**
     * Validate all entries without crafting.
     * 
     * This is a "dry run" that updates entry states based on current network conditions:
     * - DISABLED: Entry is disabled or has no pattern
     * - HOLDING_OUTPUT: Entry has pending outputs waiting to be inserted
     * - SIMULATION_FAILED: Recipe info is invalid
     * - IDLE: Target quantity reached
     * - MISSING_CATALYST: Missing required catalyst items
     * - MISSING_INPUT: Not enough items in network (checked via simulation)
     * - IDLE: Entry can craft (has all inputs and catalysts)
     * 
     * Called on world load to show correct states immediately.
     */
    private void validateAllEntries() {
        // Collect candidates (same filtering as processAllEntries Phase 0)
        List<CraftCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            CrafterEntry entry = entries.get(i);

            // Skip disabled or empty entries
            if (entry.isEmpty()) {
                updateEntryState(entry, CrafterState.NO_PATTERN);
                continue;
            }
            if (!entry.isEnabled()) {
                updateEntryState(entry, CrafterState.DISABLED);
                continue;
            }

            // Skip entries with pending outputs
            if (entry.hasPendingOutputs()) {
                updateEntryState(entry, CrafterState.HOLDING_OUTPUT);
                continue;
            }

            // Check recipe validity
            if (!entry.hasValidRecipeInfo()) {
                updateEntryState(entry, CrafterState.SIMULATION_FAILED);
                continue;
            }

            // Check target quantity
            CrafterRecipeInfo info = entry.getRecipeInfo();
            if (info != null && !info.getOutputs().isEmpty()) {
                IAEItemStack output = info.getOutputs().get(0);
                long currentQty = getNetworkQuantity(output);
                if (currentQty >= entry.getTargetQuantity()) {
                    updateEntryState(entry, CrafterState.IDLE);
                    continue;
                }
            }

            // Clear previous error details
            entry.clearErrorDetails();

            // Check catalysts
            if (info.requiresCatalysts()) {
                List<ITextComponent> catalystErrors = new ArrayList<>();
                if (!hasSufficientCatalysts(entry, info, catalystErrors)) {
                    for (ITextComponent error : catalystErrors) entry.addErrorDetail(error);
                    updateEntryState(entry, CrafterState.MISSING_CATALYST);
                    continue;
                }
            }

            candidates.add(new CraftCandidate(entry, info));
        }

        // No candidates to validate - we're done
        if (candidates.isEmpty()) return;

        // Pre-initialize shared pool
        Map<ItemStackKey, Long> sharedPool = initializeSharedPool(candidates);

        // Simulate fair allocation (same as processAllEntries, but don't extract/craft)
        // Check each candidate for input availability
        for (CraftCandidate candidate : candidates) {
            boolean hasAllInputs = true;
            List<ITextComponent> missingInputs = new ArrayList<>();

            for (CrafterRecipeInfo.IngredientInfo ingredient : candidate.info.getConsumedItems()) {
                IAEItemStack item = ingredient.getItem();
                if (item == null) continue;

                ItemStackKey key = new ItemStackKey(item);
                long available = sharedPool.getOrDefault(key, 0L);
                long needed = calculateItemsNeededForCrafts(ingredient, 1); // At least 1 craft

                if (available < needed) {
                    hasAllInputs = false;
                    ItemStack stack = item.createItemStack();
                    // Build a TextComponentTranslation so the receiving client renders the
                    // message in its own locale; server-side I18n would lock everyone to
                    // English (and fail entirely on dedicated servers without lang files).
                    missingInputs.add(new TextComponentTranslation("gui.ae2powertools.crafter.error.need_have",
                            stack.getDisplayName(), needed, available));
                }
            }

            if (hasAllInputs) {
                updateEntryState(candidate.entry, CrafterState.IDLE);
            } else {
                for (ITextComponent error : missingInputs) candidate.entry.addErrorDetail(error);
                updateEntryState(candidate.entry, CrafterState.MISSING_INPUT);
            }
        }
    }

    /**
     * Helper class for craft simulation data.
     */
    private static class CraftSimulation {
        final CrafterEntry entry;
        final CrafterRecipeInfo info;
        final int crafts;

        CraftSimulation(CrafterEntry entry, CrafterRecipeInfo info, int crafts) {
            this.entry = entry;
            this.info = info;
            this.crafts = crafts;
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
     * Fetches the storage list ONCE and lookups are done against that cached list.
     * 
     * @param candidates List of all candidates wanting to craft
     * @return Map of ItemStackKey to available quantity in network
     */
    private Map<ItemStackKey, Long> initializeSharedPool(List<CraftCandidate> candidates) {
        Map<ItemStackKey, Long> pool = new HashMap<>();

        // Get storage list ONCE for all lookups
        IItemList<IAEItemStack> storageList = getNetworkStorageList();
        if (storageList == null) return pool;

        // Collect all unique items needed by all candidates
        for (CraftCandidate candidate : candidates) {
            for (CrafterRecipeInfo.IngredientInfo ingredient : candidate.info.getConsumedItems()) {
                IAEItemStack item = ingredient.getItem();
                if (item == null) continue;

                ItemStackKey key = new ItemStackKey(item);
                if (!pool.containsKey(key)) {
                    IAEItemStack found = storageList.findPrecise(item);
                    pool.put(key, found != null ? found.getStackSize() : 0L);
                }
            }
        }

        return pool;
    }

    /**
     * Allocate resources fairly among competing entries.
     * 
     * Fair allocation algorithm:
     * 1. For each contested resource, divide available items equally among candidates that need it
     * 2. Each candidate converts their item share to crafts (share ÷ items_per_craft)
     * 3. A candidate's final crafts is the minimum across all its required resources
     * 
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
                                                          int effectiveMaxBatchSize) {
        // Step 1: For each resource, count how many candidates need it and check if contested
        // A resource is contested if total demand exceeds supply
        Map<ItemStackKey, List<CraftCandidate>> resourceUsers = new HashMap<>();
        Map<ItemStackKey, Long> totalDemand = new HashMap<>();

        for (CraftCandidate candidate : candidates) {
            for (CrafterRecipeInfo.IngredientInfo ingredient : candidate.info.getConsumedItems()) {
                IAEItemStack item = ingredient.getItem();
                if (item == null) continue;

                ItemStackKey key = new ItemStackKey(item);
                resourceUsers.computeIfAbsent(key, k -> new ArrayList<>()).add(candidate);
                long needed = calculateItemsNeededForCrafts(ingredient, effectiveMaxBatchSize);
                totalDemand.merge(key, needed, Long::sum);
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
                    CrafterRecipeInfo.IngredientInfo ing = findIngredient(user.info, key);
                    if (ing != null) {
                        allocations.put(user, calculateItemsNeededForCrafts(ing, effectiveMaxBatchSize));
                    }
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
            int finalCrafts = effectiveMaxBatchSize;
            List<ITextComponent> limitingFactors = new ArrayList<>();

            for (CrafterRecipeInfo.IngredientInfo ingredient : candidate.info.getConsumedItems()) {
                IAEItemStack item = ingredient.getItem();
                if (item == null) continue;

                ItemStackKey key = new ItemStackKey(item);
                Map<CraftCandidate, Long> allocations = itemAllocations.get(key);
                if (allocations == null) continue;

                Long allocated = allocations.get(candidate);
                if (allocated == null) allocated = 0L;

                // Convert item allocation to crafts
                int craftsFromAllocation = calculateCraftsFromItems(ingredient, allocated);

                if (craftsFromAllocation < finalCrafts) {
                    long needed = calculateItemsNeededForCrafts(ingredient, effectiveMaxBatchSize);
                    if (allocated < needed) {
                        limitingFactors.add(new TextComponentTranslation("gui.ae2powertools.crafter.error.limited_by",
                                item.createItemStack().getDisplayName(), allocated, needed));
                    }
                }
                finalCrafts = Math.min(finalCrafts, craftsFromAllocation);
            }

            // Update batch size tracking for occupancy calculation
            candidate.entry.setBatchSizeTracking(effectiveMaxBatchSize, finalCrafts);

            if (finalCrafts > 0) {
                // Deduct from shared pool
                deductFromPool(candidate.info, pool, finalCrafts);
                simulations.add(new CraftSimulation(candidate.entry, candidate.info, finalCrafts));

                // If crafts were reduced, add info about why
                if (finalCrafts < effectiveMaxBatchSize && !limitingFactors.isEmpty()) {
                    for (ITextComponent factor : limitingFactors) candidate.entry.addErrorDetail(factor);
                }
            } else {
                // No crafts possible - add all limiting factors as errors
                for (ITextComponent factor : limitingFactors) candidate.entry.addErrorDetail(factor);
                if (limitingFactors.isEmpty()) candidate.entry.addErrorDetail(new TextComponentTranslation("gui.ae2powertools.crafter.error.no_items_in_network"));
                updateEntryState(candidate.entry, CrafterState.MISSING_INPUT);
                // Record as error (no crafts possible)
                candidate.entry.recordMetrics(true, 0, 0);
            }
        }

        return simulations;
    }

    /**
     * Find the ingredient info for a specific item in a recipe.
     */
    @Nullable
    private CrafterRecipeInfo.IngredientInfo findIngredient(CrafterRecipeInfo info, ItemStackKey key) {
        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            IAEItemStack item = ingredient.getItem();
            if (item == null) continue;

            if (new ItemStackKey(item).equals(key)) return ingredient;
        }

        return null;
    }

    /**
     * Calculate how many crafts can be performed given a number of allocated items.
     * Inverse of calculateItemsNeededForCrafts.
     * 
     * For most items: 1 craft per item.
     * For DURABILITY items: multiple crafts per item based on durability.
     */
    private int calculateCraftsFromItems(CrafterRecipeInfo.IngredientInfo ingredient, long items) {
        if (items <= 0) return 0;

        if (ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY) {
            IAEItemStack aeItem = ingredient.getItem();
            if (aeItem != null) {
                ItemStack template = aeItem.createItemStack();
                int maxDurability = template.getMaxDamage();
                int durabilityPerCraft = ingredient.getDurabilityPerCraft();

                if (maxDurability > 0 && durabilityPerCraft > 0) {
                    int craftsPerItem = maxDurability / durabilityPerCraft;
                    if (craftsPerItem <= 0) craftsPerItem = 1;
                    return (int) Math.min(items * craftsPerItem, Integer.MAX_VALUE);
                }
            }
        }

        // Regular items: 1 craft per item
        return (int) Math.min(items, Integer.MAX_VALUE);
    }

    /**
     * Calculate how many items are needed for a given number of crafts.
     * 
     * For most items: 1 item per craft.
     * For DURABILITY items: depends on durability per craft and max durability.
     */
    private long calculateItemsNeededForCrafts(CrafterRecipeInfo.IngredientInfo ingredient, int crafts) {
        if (ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY) {
            IAEItemStack item = ingredient.getItem();
            if (item != null) {
                ItemStack template = item.createItemStack();
                int maxDurability = template.getMaxDamage();
                int durabilityPerCraft = ingredient.getDurabilityPerCraft();

                if (maxDurability > 0 && durabilityPerCraft > 0) {
                    int craftsPerItem = maxDurability / durabilityPerCraft;
                    if (craftsPerItem <= 0) craftsPerItem = 1;
                    return (crafts + craftsPerItem - 1) / craftsPerItem; // Ceiling division
                }
            }
        }

        // Regular items: 1 item per craft
        return crafts;
    }

    /**
     * Update entry state if changed. Diff sync in the container will propagate the change.
     */
    private void updateEntryState(CrafterEntry entry, CrafterState newState) {
        if (entry.getState() != newState) {
            entry.setState(newState);
        }
    }

    /**
     * Deduct items from the shared pool after allocating crafts to an entry.
     * 
     * For most items: deduct 1 item per craft.
     * For DURABILITY items: deduct based on how many items are actually needed.
     */
    private void deductFromPool(CrafterRecipeInfo info, Map<ItemStackKey, Long> pool, int crafts) {
        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            IAEItemStack item = ingredient.getItem();
            if (item == null) continue;

            ItemStackKey key = new ItemStackKey(item);
            long current = pool.getOrDefault(key, 0L);

            long itemsToDeduct;
            if (ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY) {
                // Durability items: calculate actual items needed
                ItemStack template = item.createItemStack();
                int maxDurability = template.getMaxDamage();
                int durabilityPerCraft = ingredient.getDurabilityPerCraft();

                if (maxDurability > 0 && durabilityPerCraft > 0) {
                    int craftsPerItem = maxDurability / durabilityPerCraft;
                    if (craftsPerItem <= 0) craftsPerItem = 1;
                    itemsToDeduct = (crafts + craftsPerItem - 1) / craftsPerItem; // Ceiling division
                } else {
                    itemsToDeduct = crafts;
                }
            } else {
                // Regular items: 1 item per craft
                itemsToDeduct = crafts;
            }

            pool.put(key, Math.max(0, current - itemsToDeduct));
        }
    }

    /**
     * Key for ItemStack identity in maps, using including NBT matching.
     */
    private static class ItemStackKey {
        private final IAEItemStack item;

        ItemStackKey(IAEItemStack item) {
            this.item = item;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ItemStackKey)) return false;
            ItemStackKey other = (ItemStackKey) obj;
            return item.isSameType(other.item);
        }

        @Override
        public int hashCode() {
            // Use item + meta for hash, NBT comparison in equals
            ItemStack stack = item.createItemStack();
            return stack.getItem().hashCode() ^ (stack.getMetadata() * 31);
        }
    }

    /**
     * Check if the entry has sufficient catalyst items in its internal inventory.
     * 
     * Uses 1:1 slot mapping: each recipe slot that requires a catalyst (REUSABLE or DUPLICATION)
     * must have the corresponding item in the same slot of the internal inventory.
     * 
     * Uses inclusive NBT matching: the actual item can have additional NBT tags beyond
     * what the recipe requires.
     * 
     * @param entry The crafter entry to check
     * @param info The recipe info with catalyst requirements
     * @param errorDetails If provided, missing catalyst info will be added
     * @return true if all catalysts are present in their correct slots
     */
    private boolean hasSufficientCatalysts(CrafterEntry entry, CrafterRecipeInfo info, 
                                           @Nullable List<ITextComponent> errorDetails) {
        // Check each catalyst slot using 1:1 mapping (recipe slot X = internal inventory slot X)
        for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
            if (catalyst.getItem() == null) continue;
            
            int recipeSlot = catalyst.getSlotIndex();
            ItemStack required = catalyst.getItem().createItemStack();
            ItemStack available = entry.getCatalystStack(recipeSlot);
            
            if (available.isEmpty()) {
                if (errorDetails != null) {
                    errorDetails.add(new TextComponentTranslation("gui.ae2powertools.crafter.error.missing_catalyst_slot",
                            recipeSlot + 1, required.getDisplayName()));
                }
                return false;
            }
            
            // Use inclusive NBT matching
            if (!areItemStacksMatchingIncludingNbt(required, available)) {
                if (errorDetails != null) {
                    errorDetails.add(new TextComponentTranslation("gui.ae2powertools.crafter.error.wrong_catalyst_slot",
                            recipeSlot + 1, required.getDisplayName(), available.getDisplayName()));
                }
                return false;
            }
        }
        
        return true;
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
    private int getLeftoverDurability(CrafterEntry entry, int recipeSlotIndex, IAEItemStack expectedItem) {
        if (recipeSlotIndex < 0 || recipeSlotIndex >= CrafterEntry.CATALYST_SLOTS) return 0;
        
        ItemStack leftover = entry.getCatalystStack(recipeSlotIndex);
        if (leftover.isEmpty()) return 0;
        
        // Check if it's the same item type
        ItemStack expected = expectedItem.createItemStack();
        if (leftover.getItem() != expected.getItem()) return 0;
        
        // Calculate remaining durability
        int maxDurability = leftover.getMaxDamage();
        int currentDamage = leftover.getItemDamage();
        return Math.max(0, maxDurability - currentDamage);
    }

    /**
     * Extract required inputs from the network.
     * 
     * For most items: 1 item per slot per craft.
     * For DURABILITY items: 
     * - First check for leftover durability in catalyst slots
     * - Calculate how many additional items needed from network
     * - May need multiple items if total durability exceeds available
     * 
     * @param crafts Number of individual crafts to perform
     */
    private boolean extractInputs(CrafterEntry entry, CrafterRecipeInfo info, int crafts) {
        // First simulate all extractions
        List<IAEItemStack> toExtract = new ArrayList<>();

        for (CrafterRecipeInfo.IngredientInfo ingredient : info.getConsumedItems()) {
            IAEItemStack item = ingredient.getItem();
            if (item == null) continue;

            IAEItemStack request = item.copy();

            if (ingredient.getType() == CrafterRecipeInfo.IngredientType.DURABILITY) {
                // Durability items: check for leftover first
                // Internal inventory mirrors crafting grid 1:1
                int recipeSlot = ingredient.getSlotIndex();
                int leftoverDurability = getLeftoverDurability(entry, recipeSlot, item);
                
                ItemStack template = item.createItemStack();
                int maxDurability = template.getMaxDamage();
                int durabilityPerCraft = ingredient.getDurabilityPerCraft();
                int totalDurabilityNeeded = crafts * durabilityPerCraft;
                
                // Subtract leftover durability from what we need
                int durabilityFromNetwork = Math.max(0, totalDurabilityNeeded - leftoverDurability);

                if (durabilityFromNetwork > 0 && maxDurability > 0) {
                    // Calculate items needed from network
                    // Each new item from network has full durability
                    int itemsFromNetwork = (durabilityFromNetwork + maxDurability - 1) / maxDurability; // Ceiling division
                    request.setStackSize(itemsFromNetwork);
                } else {
                    // Leftover has enough durability, no network extraction needed
                    request.setStackSize(0);
                }
            } else {
                // Regular items: 1 item per slot per craft
                request.setStackSize(crafts);
            }

            if (request.getStackSize() > 0) toExtract.add(request);
        }

        // Simulate to verify we can extract everything
        // Note: This can fail if another crafter extracted between calculateAvailableBatches and now
        for (IAEItemStack request : toExtract) {
            IAEItemStack result = tryExtractFromNetwork(request, Actionable.SIMULATE);
            if (result == null || result.getStackSize() < request.getStackSize()) return false;
        }

        // Actually extract
        for (IAEItemStack request : toExtract) tryExtractFromNetwork(request, Actionable.MODULATE);

        return true;
    }

    /**
     * Perform the craft operation using cached recipe info.
     * 
     * This method does NOT re-simulate the recipe. All ingredient types and outputs
     * are determined from the cached CrafterRecipeInfo which was computed once
     * when the pattern was inserted.
     * 
     * @param crafts Number of individual crafts to perform
     * @return List of all outputs (main output + transformed items), or null on failure
     */
    @Nullable
    private List<IAEItemStack> performCraftInternal(CrafterEntry entry, CrafterRecipeInfo info, int crafts) {
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
                scaledOutput.setStackSize(output.getStackSize() * crafts);
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
                    processDurabilityItems(entry, info, ingredient, crafts);
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
     * 
     * Internal inventory mirrors the crafting grid 1:1 (slot 0-8 = crafting slot 0-8).
     * A durability item's leftover goes in the same slot as its recipe position.
     * 
     * @param entry The crafter entry (to access/update internal inventory)
     * @param info The recipe info
     * @param ingredient The durability ingredient being processed
     * @param crafts Number of individual crafts being performed
     * @return Empty list (survivor is stored in internal inventory, not returned to network)
     */
    private void processDurabilityItems(CrafterEntry entry, CrafterRecipeInfo info,
                                        CrafterRecipeInfo.IngredientInfo ingredient,
                                        int crafts) {
        // Durability items store their leftover in internal inventory, not returned to network
        if (ingredient.getItem() == null) return;

        ItemStack template = ingredient.getItem().createItemStack();
        int maxDurability = template.getMaxDamage();
        int recipeSlot = ingredient.getSlotIndex();
        
        if (maxDurability <= 0) {
            // Item has no durability - treat as REUSABLE, store in internal inventory
            entry.setCatalystStack(recipeSlot, template.copy());
            return;
        }

        int durabilityPerCraft = ingredient.getDurabilityPerCraft();
        int totalDurabilityNeeded = crafts * durabilityPerCraft;
        
        // Get current leftover from internal inventory
        ItemStack leftover = entry.getCatalystStack(recipeSlot);
        int leftoverDurability = 0;
        if (!leftover.isEmpty() && leftover.getItem() == template.getItem()) {
            leftoverDurability = maxDurability - leftover.getItemDamage();
        }
        
        // Calculate total durability available:
        // - Leftover durability from internal inventory
        // - Fresh items extracted from network (each has maxDurability)
        // extractInputs already calculated how many network items we needed
        int durabilityFromNetwork = Math.max(0, totalDurabilityNeeded - leftoverDurability);
        int itemsFromNetwork = (durabilityFromNetwork > 0 && maxDurability > 0) 
                             ? (durabilityFromNetwork + maxDurability - 1) / maxDurability 
                             : 0;
        int totalDurabilityAvailable = leftoverDurability + (itemsFromNetwork * maxDurability);
        
        // After crafting, remaining durability
        int remainingDurability = totalDurabilityAvailable - totalDurabilityNeeded;
        
        if (remainingDurability > 0) {
            // Store survivor in internal inventory
            int finalDamage = maxDurability - remainingDurability;
            ItemStack survivor = template.copy();
            survivor.setItemDamage(Math.max(0, finalDamage));
            entry.setCatalystStack(recipeSlot, survivor);
        } else {
            // All items broke, clear the slot
            entry.setCatalystStack(recipeSlot, ItemStack.EMPTY);
        }
    }

    // ==================== NETWORK OPERATIONS ====================

    @Nullable
    private IAEItemStack tryInsertIntoNetwork(IAEItemStack stack, Actionable mode) {
        try {
            IGrid grid = gridProxy.getGrid();
            if (grid == null) return stack;

            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return stack;

            IMEMonitor<IAEItemStack> inv = storage.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            return inv.injectItems(stack, mode, actionSource);
        } catch (GridAccessException e) {
            return stack;
        }
    }

    @Nullable
    private IAEItemStack tryExtractFromNetwork(IAEItemStack stack, Actionable mode) {
        try {
            IGrid grid = gridProxy.getGrid();
            if (grid == null) return null;

            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return null;

            IMEMonitor<IAEItemStack> inv = storage.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            return inv.extractItems(stack, mode, actionSource);
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
        try {
            IGrid grid = gridProxy.getGrid();
            if (grid == null) return null;

            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return null;

            IMEMonitor<IAEItemStack> inv = storage.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            return inv.getStorageList();
        } catch (GridAccessException e) {
            return null;
        }
    }

    /**
     * Get the quantity of an item available in the network.
     * 
     * Note: For performance when processing multiple items, use getNetworkStorageList()
     * once and call findPrecise() on it directly. This method is for one-off queries.
     */
    private long getNetworkQuantity(IAEItemStack item) {
        try {
            IGrid grid = gridProxy.getGrid();
            if (grid == null) return 0;

            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) return 0;

            IMEMonitor<IAEItemStack> inv = storage.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            IAEItemStack found = inv.getStorageList().findPrecise(item);
            return found != null ? found.getStackSize() : 0;
        } catch (GridAccessException e) {
            return 0;
        }
    }

    // ==================== FAKE PLAYER ====================

    @Nullable
    private FakePlayer getOrCreateFakePlayer() {
        if (world == null || !(world instanceof WorldServer)) return null;

        if (fakePlayer == null) fakePlayer = FakePlayerFactory.get((WorldServer) world, CRAFTER_PROFILE);

        return fakePlayer;
    }

    // ==================== PATTERN SIMULATION ====================

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
    public void simulatePattern(int entryIndex, ItemStack patternStack, boolean resetState) {
        if (entryIndex < 0 || entryIndex >= entries.size()) return;

        CrafterEntry entry = entries.get(entryIndex);
        entry.setPatternStack(patternStack);

        if (patternStack == null || patternStack.isEmpty()) {
            entry.setRecipeInfo(null);
            entry.setState(CrafterState.NO_PATTERN);
            return;
        }

        // Get pattern details
        if (!(patternStack.getItem() instanceof ICraftingPatternItem)) {
            entry.setRecipeInfo(new CrafterRecipeInfo("gui.ae2powertools.crafter.error.not_pattern"));
            entry.setState(CrafterState.SIMULATION_FAILED);
            return;
        }

        ICraftingPatternItem patternItem = (ICraftingPatternItem) patternStack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(patternStack, world);

        if (details == null) {
            entry.setRecipeInfo(new CrafterRecipeInfo("gui.ae2powertools.crafter.error.invalid_pattern"));
            entry.setState(CrafterState.SIMULATION_FAILED);
            return;
        }

        // Only crafting patterns are allowed
        if (!details.isCraftable()) {
            entry.setRecipeInfo(new CrafterRecipeInfo("gui.ae2powertools.crafter.error.processing_pattern"));
            entry.setState(CrafterState.SIMULATION_FAILED);
            return;
        }

        // Analyze the recipe
        CrafterRecipeInfo info = analyzeRecipe(details);
        entry.setRecipeInfo(info);

        if (!info.isValid()) {
            entry.setState(CrafterState.SIMULATION_FAILED);
        } else if (resetState) {
            // Only reset to IDLE when inserting a new pattern, not on world load
            entry.setState(CrafterState.IDLE);
        }
        // If !resetState and info.isValid(), keep the existing state (e.g., MISSING_INPUT from NBT)

        markDirty();
    }

    /**
     * Analyze a crafting pattern to determine ingredient types.
     * 
     * This is the ONLY place where recipe simulation happens. The results are cached
     * in CrafterRecipeInfo to avoid expensive simulation on every craft operation.
     * 
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
        IAEItemStack[] patternOutputs = details.getOutputs();

        // Set up crafting grid with inputs (no catalyst substitution for initial analysis)
        InventoryCrafting craftMatrix = buildCraftingMatrix(details, null, null);
        if (craftMatrix == null) return new CrafterRecipeInfo("gui.ae2powertools.crafter.error.invalid_pattern");

        // Find matching recipe
        IRecipe recipe = CraftingManager.findMatchingRecipe(craftMatrix, world);
        if (recipe == null) {
            return new CrafterRecipeInfo("gui.ae2powertools.crafter.error.no_matching_recipe");
        }

        // Get recipe output
        ItemStack recipeOutput = recipe.getCraftingResult(craftMatrix);
        if (recipeOutput.isEmpty()) return new CrafterRecipeInfo("gui.ae2powertools.crafter.error.no_output");

        // Add main output
        IAEItemStack mainOutput = AEItemStack.fromItemStack(recipeOutput);
        if (mainOutput != null) outputs.add(mainOutput);

        // Collect pattern outputs for duplication detection
        // Pattern outputs may differ from recipe output if the pattern was created differently
        Map<String, Long> outputItemMap = new HashMap<>();
        for (IAEItemStack output : patternOutputs) {
            if (output != null) {
                String key = getItemKey(output.createItemStack());
                outputItemMap.merge(key, output.getStackSize(), Long::sum);
            }
        }

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
                // Nothing remains - check if this item appears in outputs (duplication)
                String inputKey = getItemKey(inputStack);
                Long outputCount = outputItemMap.get(inputKey);
                if (outputCount != null && outputCount >= 1) {
                    type = CrafterRecipeInfo.IngredientType.DUPLICATION;
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
                if (remainingAE != null) {
                    outputs.add(remainingAE.copy());
                }
            }

            ingredients.add(new CrafterRecipeInfo.IngredientInfo(
                    input.copy(), i, type, 1, remainingAE, durabilityPerCraft));
        }

        return new CrafterRecipeInfo(ingredients, outputs);
    }

    /**
     * Creates a unique key for an ItemStack based on Item, metadata, and NBT.
     * Used for comparing items strictly.
     */
    private String getItemKey(ItemStack stack) {
        if (stack.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder();
        sb.append(stack.getItem().getRegistryName());
        sb.append("@").append(stack.getMetadata());
        if (stack.hasTagCompound()) {
            sb.append("#").append(stack.getTagCompound().toString());
        }
        return sb.toString();
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
        if (oldTier != newTier) {
            markForUpdate();
        }

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
     * 
     * Formula: baseCraftsPerOperation (config) * batchSize (user) * upgradeMultiplier
     */
    public int getEffectiveMaxBatchSize() {
        return getBaseCraftsPerOperation() * batchSize * getUpgradeBatchMultiplier();
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
     * Clear an entry (remove pattern and reset).
     */
    public void clearEntry(int index) {
        if (index < 0 || index >= entries.size()) return;

        CrafterEntry entry = entries.get(index);
        entry.setPatternStack(null);
        entry.setRecipeInfo(null);
        entry.setEnabled(true);
        entry.setState(CrafterState.NO_PATTERN);
        entry.clearPendingOutputs();

        for (int i = 0; i < CrafterEntry.CATALYST_SLOTS; i++) entry.setCatalystStack(i, ItemStack.EMPTY);

        markDirty();
    }

    /**
     * Validate that the current catalysts are still valid for the recipe.
     * 
     * This is called when a catalyst is changed (inserted/removed) to ensure the player
     * cannot swap in a cheaper item. If the recipe still works with the current catalysts,
     * the state is updated. If the recipe would fail, the state is set to SIMULATION_FAILED.
     * 
     * Reuses the recipe simulation logic from analyzeRecipe, substituting actual catalyst items.
     */
    public void validateCatalysts(int entryIndex) {
        if (entryIndex < 0 || entryIndex >= entries.size()) return;

        CrafterEntry entry = entries.get(entryIndex);
        if (!entry.hasPattern() || !entry.hasValidRecipeInfo()) return;

        CrafterRecipeInfo info = entry.getRecipeInfo();
        if (info == null || !info.requiresCatalysts()) return;

        ItemStack patternStack = entry.getPatternStack();
        if (patternStack == null || patternStack.isEmpty()) return;
        if (!(patternStack.getItem() instanceof ICraftingPatternItem)) return;

        ICraftingPatternItem patternItem = (ICraftingPatternItem) patternStack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(patternStack, world);
        if (details == null || !details.isCraftable()) return;

        // Build crafting matrix with actual catalyst items substituted
        InventoryCrafting craftMatrix = buildCraftingMatrix(details, entry, info);
        if (craftMatrix == null) {
            // Missing catalyst - recipe cannot work
            entry.setState(CrafterState.MISSING_CATALYST);
            return;
        }

        // Check if recipe still matches with the actual catalysts
        IRecipe recipe = CraftingManager.findMatchingRecipe(craftMatrix, world);
        if (recipe == null) {
            entry.setState(CrafterState.SIMULATION_FAILED);
            markDirty();
            return;
        }

        // Verify output is the same
        ItemStack newOutput = recipe.getCraftingResult(craftMatrix);
        IAEItemStack expectedOutput = info.getOutputs().isEmpty() ? null : info.getOutputs().get(0);
        
        if (expectedOutput == null || newOutput.isEmpty()) {
            entry.setState(CrafterState.SIMULATION_FAILED);
            markDirty();
            return;
        }

        ItemStack expectedStack = expectedOutput.createItemStack();
        if (expectedStack.getItem() != newOutput.getItem() 
            || expectedStack.getMetadata() != newOutput.getMetadata()) {
            entry.setState(CrafterState.SIMULATION_FAILED);
            markDirty();
            return;
        }

        // Recipe is still valid
        if (entry.isEnabled()) entry.setState(CrafterState.IDLE);

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
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        NBTTagList entryList = new NBTTagList();
        for (CrafterEntry entry : entries) entryList.appendTag(entry.writeToNBT());

        data.setTag("entries", entryList);

        data.setInteger("speed", speedTicks);
        data.setInteger("batch", batchSize);
        data.setInteger("tickCounter", tickCounter);

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

        tickCounter = data.getInteger("tickCounter");
        // tickCounter doesn't need validation - 0 is a valid starting point

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
    public IGridNode getGridNode(AEPartLocation dir) {
        return gridProxy.getNode();
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {
        world.destroyBlock(pos, true);
    }

    @Override
    public IGridNode getActionableNode() {
        return gridProxy.getNode();
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
