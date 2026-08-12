package com.ae2powertools.features.monitor.dependent;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.AENetworkProxy;

import com.ae2powertools.features.monitor.emitter.IEmitterCardHost;
import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.util.FormatUtil;


/**
 * Shared composition class for all storage monitoring dependents (emitters, displays).
 * Contains the refresh logic, configured entries (each with resource + comparison + threshold),
 * and the overall condition result (AND/OR across all entry evaluations).
 * <p>
 * This class is NOT a tile entity -- it is composed into tile entities and parts
 * to share behavior without inheritance.
 * <p>
 * Queries the AE2 network grid directly via findPrecise() for O(1) lookups,
 * rather than going through an intermediary cache.
 */
public class MonitorLogic {

    /** Default refresh rate: every 1 second (20 ticks) */
    public static final int DEFAULT_REFRESH_RATE = FormatUtil.TICKS_PER_SECOND;

    /** Minimum refresh rate: 1 second (20 ticks) */
    public static final int MIN_REFRESH_RATE = FormatUtil.TICKS_PER_SECOND;

    /**
     * Number of monitored entries (resource/comparison/threshold) supported. This is a fixed limit.
     * Must match the GUI's GRID_COLS * GRID_ROWS.
     */
    public static final int GRID_CAPACITY = 24;

    private final IMonitorLogicHost host;

    private int refreshRate = DEFAULT_REFRESH_RATE;

    /** The list of monitored entries, each with its own resource, comparison, and threshold.
     *  Always sized to {@link #GRID_CAPACITY}; slots without a resource are placeholders
     *  (see {@link MonitoredEntry#empty()}). */
    private List<MonitoredEntry> entries = createEmptyGrid();

    /**
     * Monotonically-increasing counter bumped every time the entries list mutates
     * (add / remove / replace / bulk-set). The container uses this to detect changes
     * worth broadcasting to the client GUI - the actual entries are too heavy to
     * snapshot/diff every tick.
     */
    private int entriesVersion;

    /** How multiple entries are combined: AND = all must be met, OR = any must be met */
    private MatchMode matchMode = MatchMode.AND;

    /** Whether entries use separate increasing/decreasing thresholds instead of a single bound. */
    private boolean hysteresisEnabled;

    /** Whether the overall condition (AND/OR across all entries) is currently met */
    private boolean conditionMet;

    /**
     * Forces one host-side condition callback after the next successful refresh.
     * Needed when config mutations keep the same boolean condition but still change the
     * host's derived state, such as alarm latching when entries are disabled or retuned.
     */
    private boolean conditionDirty;

    public MonitorLogic(IMonitorLogicHost host) {
        this.host = host;
    }

    // --- Core refresh logic ---

    /**
     * Performs a single refresh cycle: query the AE2 grid for each entry's resource,
     * evaluate each entry's condition (quantity COMP threshold), then AND/OR across all.
     * <p>
     * Called directly from IGridTickable hosts, the grid tick manager controls the rate.
     */
    public boolean refresh() {
        AENetworkProxy proxy = host.getProxy();
        if (proxy == null || !proxy.isReady() || !proxy.isActive()) return false;

        boolean newCondition;

        try {
            IGrid grid = proxy.getGrid();
            IStorageGrid storage = grid.getCache(IStorageGrid.class);

            newCondition = evaluateCondition(grid, storage);
        } catch (GridAccessException e) {
            // Network not available, keep last known state
            return false;
        }

        boolean old = conditionMet;
        conditionMet = newCondition;

        if (old == conditionMet && !conditionDirty) return true;

        conditionDirty = false;
        host.onConditionChanged(old, conditionMet);

        return true;
    }

    /**
     * Evaluates the overall condition across all entries.
     * Each entry independently checks (quantity COMP threshold) -> boolean,
     * then AND requires all true (of the enabled entries), OR requires any true.
     * <p>
     * Disabled entries are still polled and have their lastQuantity / lastConditionMet
     * updated for client-side GUI feedback, but they are excluded from the AND/OR.
     */
    private boolean evaluateCondition(IGrid grid, IStorageGrid storage) {
        // Poll only ENABLED entries with a resource here. Resource-less placeholders are
        // skipped: they have nothing to look up and don't contribute to the AND/OR.
        for (MonitoredEntry entry : entries) {
            if (!entry.isEnabled() || !entry.hasResource()) continue;

            long qty = lookupQuantity(grid, storage, entry.getResource());
            entry.evaluate(qty, hysteresisEnabled);
        }

        // Second pass: combine only the ENABLED entries' results (resource-less ones don't count).
        boolean hasEnabled = false;
        if (matchMode == MatchMode.AND) {
            for (MonitoredEntry entry : entries) {
                if (!entry.isEnabled() || !entry.hasResource()) continue;

                hasEnabled = true;
                if (!entry.isLastConditionMet()) return false;
            }

            // AND of zero enabled entries: nothing to satisfy, treat as false (matches old behavior).
            return hasEnabled;
        }

        // OR mode: any enabled entry met = condition met.
        for (MonitoredEntry entry : entries) {
            if (!entry.isEnabled() || !entry.hasResource()) continue;

            if (entry.isLastConditionMet()) return true;
        }

        return false;
    }

    /**
     * Looks up the quantity of a single resource in the network.
     * Uses findPrecise() for O(1) lookup on the NetworkMonitor's cached list.
     */
    private long lookupQuantity(IGrid grid, IStorageGrid storage, MonitoredResource resource) {
        IAEStack<?> stack = resource.getStack();
        if (stack == null) return 0;

        switch (resource.getType()) {
            case ITEM:
                return lookupItemQuantity(grid, storage, (IAEItemStack) stack);
            case FLUID:
                return lookupFluidQuantity(storage, (IAEFluidStack) stack);
            case GAS:
                if (Loader.isModLoaded("mekeng")) return lookupGasQuantity(storage, stack);
                return 0;
            case ESSENTIA:
                if (Loader.isModLoaded("thaumicenergistics")) return lookupEssentiaQuantity(storage, stack);
                return 0;
            default:
                return 0;
        }
    }

    private long lookupItemQuantity(IGrid grid, IStorageGrid storage, IAEItemStack stack) {
        if (usesCraftingCard()) return lookupCraftingItemQuantity(grid, stack);

        IMEMonitor<IAEItemStack> monitor = storage.getInventory(
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        // findFuzzy() returns every matching variant, so we sum them all up
        if (usesFuzzyCard()) {
            return sumItemStacks(monitor.getStorageList().findFuzzy(stack, FuzzyMode.IGNORE_ALL));
        }

        IAEItemStack found = monitor.getStorageList().findPrecise(stack);

        return found != null ? found.getStackSize() : 0;
    }

    private long lookupCraftingItemQuantity(IGrid grid, IAEItemStack stack) {
        ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);

        if (!usesFuzzyCard()) return crafting.requesting(stack);

        // CPUs do not expose a findFuzzy() API, so we have to iterate each CPU and do the work ourselves.
        long requested = 0;
        for (ICraftingCPU cpu : crafting.getCpus()) {
            if (!(cpu instanceof CraftingCPUCluster)) continue;

            IItemList<IAEItemStack> active = AEApi.instance()
                .storage()
                .getStorageChannel(IItemStorageChannel.class)
                .createList();
            ((CraftingCPUCluster) cpu).getListOfItem(active, CraftingItemList.ACTIVE);
            requested += sumItemStacks(active.findFuzzy(stack, FuzzyMode.IGNORE_ALL));
        }

        return requested;
    }

    private long lookupFluidQuantity(IStorageGrid storage, IAEFluidStack stack) {
        IMEMonitor<IAEFluidStack> monitor = storage.getInventory(
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        IAEFluidStack found = monitor.getStorageList().findPrecise(stack);

        return found != null ? found.getStackSize() : 0;
    }

    @Optional.Method(modid = "mekeng")
    private long lookupGasQuantity(IStorageGrid storage, IAEStack<?> stack) {
        IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> channel =
            AEApi.instance().storage().getStorageChannel(
                com.mekeng.github.common.me.storage.IGasStorageChannel.class);
        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> monitor = storage.getInventory(channel);

        com.mekeng.github.common.me.data.IAEGasStack gasStack =
            (com.mekeng.github.common.me.data.IAEGasStack) stack;
        com.mekeng.github.common.me.data.IAEGasStack found = monitor.getStorageList().findPrecise(gasStack);

        return found != null ? found.getStackSize() : 0;
    }

    @Optional.Method(modid = "thaumicenergistics")
    private long lookupEssentiaQuantity(IStorageGrid storage, IAEStack<?> stack) {
        IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> channel =
            AEApi.instance().storage().getStorageChannel(
                thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
        IMEMonitor<thaumicenergistics.api.storage.IAEEssentiaStack> monitor = storage.getInventory(channel);

        thaumicenergistics.api.storage.IAEEssentiaStack essentiaStack =
            (thaumicenergistics.api.storage.IAEEssentiaStack) stack;
        thaumicenergistics.api.storage.IAEEssentiaStack found = monitor.getStorageList().findPrecise(essentiaStack);

        return found != null ? found.getStackSize() : 0;
    }

    /**
     * Polls every DISABLED entry for its current network quantity, purely so the
     * client GUI can show live numbers. Does not affect AND/OR evaluation.
     * <p>
     * Intended to be called from {@code Container.detectAndSendChanges()}, which
     * only ticks for the currently open container.
     */
    public void pollDisabledEntriesForDisplay() {
        AENetworkProxy proxy = host.getProxy();
        if (proxy == null || !proxy.isReady() || !proxy.isActive()) return;

        try {
            IGrid grid = proxy.getGrid();
            IStorageGrid storage = grid.getCache(IStorageGrid.class);

            for (MonitoredEntry entry : entries) {
                if (entry.isEnabled() || !entry.hasResource()) continue;

                long qty = lookupQuantity(grid, storage, entry.getResource());
                entry.evaluate(qty, hysteresisEnabled);
            }
        } catch (GridAccessException e) {
            // Network unavailable, leave last known quantities in place.
        }
    }

    private boolean usesFuzzyCard() {
        return host instanceof IEmitterCardHost && ((IEmitterCardHost) host).hasFuzzyCard();
    }

    private boolean usesCraftingCard() {
        return host instanceof IEmitterCardHost && ((IEmitterCardHost) host).hasCraftingCard();
    }

    private long sumItemStacks(Iterable<IAEItemStack> stacks) {
        long total = 0;

        for (IAEItemStack match : stacks) {
            if (match != null) total += match.getStackSize();
        }

        return total;
    }

    // --- Public API: query all resources in the grid for the content selector ---

    /**
     * Queries all storage channels and returns the full list of available resources.
     * Used by the content selector GUI packet to show what's in the network.
     */
    public List<MonitoredResource> queryAllNetworkResources() {
        List<MonitoredResource> resources = new ArrayList<>();
        AENetworkProxy proxy = host.getProxy();

        if (proxy == null || !proxy.isReady() || !proxy.isActive()) return resources;

        try {
            IGrid grid = proxy.getGrid();
            IStorageGrid storage = grid.getCache(IStorageGrid.class);

            // Items
            collectChannel(storage, AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class), resources);

            // Fluids
            collectChannel(storage, AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class), resources);

            // Gas (optional)
            if (Loader.isModLoaded("mekeng")) {
                collectGasChannel(storage, resources);
            }

            // Essentia (optional)
            if (Loader.isModLoaded("thaumicenergistics")) {
                collectEssentiaChannel(storage, resources);
            }
        } catch (GridAccessException e) {
            // Network not available
        }

        return resources;
    }

    /**
     * Collects all resources from a standard item/fluid channel.
     */
    private <T extends IAEStack<T>> void collectChannel(
            IStorageGrid storage, IStorageChannel<T> channel,
            List<MonitoredResource> resources) {
        IMEMonitor<T> monitor = storage.getInventory(channel);
        IItemList<T> list = monitor.getStorageList();

        for (T stack : list) {
            if (stack == null || stack.getStackSize() <= 0) continue;

            MonitoredResource resource = createMonitoredResource(stack);
            if (resource != null) resources.add(resource);
        }
    }

    @Nullable
    private MonitoredResource createMonitoredResource(IAEStack<?> stack) {
        if (stack instanceof IAEItemStack) return MonitoredResource.ofItem((IAEItemStack) stack);
        if (stack instanceof IAEFluidStack) return MonitoredResource.ofFluid((IAEFluidStack) stack);

        return null;
    }

    @Optional.Method(modid = "mekeng")
    private void collectGasChannel(IStorageGrid storage, List<MonitoredResource> resources) {
        IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> channel =
            AEApi.instance().storage().getStorageChannel(
                com.mekeng.github.common.me.storage.IGasStorageChannel.class);
        IMEMonitor<com.mekeng.github.common.me.data.IAEGasStack> monitor = storage.getInventory(channel);

        for (com.mekeng.github.common.me.data.IAEGasStack stack : monitor.getStorageList()) {
            if (stack == null || stack.getStackSize() <= 0) continue;

            String name = stack.getGasStack().getGas().getLocalizedName();
            resources.add(MonitoredResource.ofGas(stack, name));
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    private void collectEssentiaChannel(IStorageGrid storage, List<MonitoredResource> resources) {
        IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> channel =
            AEApi.instance().storage().getStorageChannel(
                thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
        IMEMonitor<thaumicenergistics.api.storage.IAEEssentiaStack> monitor = storage.getInventory(channel);

        for (thaumicenergistics.api.storage.IAEEssentiaStack stack : monitor.getStorageList()) {
            if (stack == null || stack.getStackSize() <= 0) continue;

            String name = stack.getAspect().getName();
            resources.add(MonitoredResource.ofEssentia(stack, name));
        }
    }

    // --- Getters and setters ---

    public int getRefreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(int refreshRate) {
        this.refreshRate = Math.max(MIN_REFRESH_RATE, refreshRate);
        host.markDirtyAndSave();
        host.onRefreshRateChanged();
    }

    public List<MonitoredEntry> getEntries() {
        return entries;
    }

    /**
     * Returns the first entry's resource, for backward-compatible single-resource display code.
     * Skips placeholder entries (no resource).
     */
    @Nullable
    public MonitoredResource getFirstResource() {
        for (MonitoredEntry e : entries) {
            if (e.hasResource()) return e.getResource();
        }
        return null;
    }

    /**
     * Returns the first non-empty entry's last looked-up quantity, for display rendering.
     */
    public long getFirstEntryQuantity() {
        for (MonitoredEntry e : entries) {
            if (e.hasResource()) return e.getLastQuantity();
        }
        return 0;
    }

    /**
     * Replaces the entire entry list. Input is normalised to exactly {@link #GRID_CAPACITY} slots:
     * truncated if longer, padded with empty placeholders if shorter. This preserves the GUI's
     * fixed-grid invariant.
     */
    public void setEntries(List<MonitoredEntry> entries) {
        this.entries = normaliseToGrid(entries);
        this.conditionDirty = true;
        this.entriesVersion++;
        host.markDirtyAndSave();
    }

    /**
     * Sets (replaces) an entry at the given index.
     * <p>
     * Carries the previous entry's transient lastQuantity / lastConditionMet onto the new
     * entry when the resource is unchanged, so the GUI doesn't briefly flash 0/red on every
     * server-side mutation (the next poll would refresh those values one tick later, but
     * the broadcast cache compares against the freshly-defaulted 0 first and pushes that to
     * all clients before the next refresh runs).
     */
    public void setEntry(int index, MonitoredEntry entry) {
        if (index < 0 || index >= entries.size()) return;

        MonitoredEntry old = entries.get(index);
        MonitoredResource oldRes = old.getResource();
        MonitoredResource newRes = entry.getResource();
        boolean sameResource = oldRes != null && newRes != null
            && oldRes.toKey().equals(newRes.toKey());
        if (sameResource) {
            entry.setLastQuantity(old.getLastQuantity());
            entry.setLastConditionMet(old.isLastConditionMet());
        }

        entries.set(index, entry);
        conditionDirty = true;
        entriesVersion++;
        host.markDirtyAndSave();
    }

    /**
     * Builds a fresh entries list of {@link #GRID_CAPACITY} placeholder entries.
     */
    private static List<MonitoredEntry> createEmptyGrid() {
        List<MonitoredEntry> list = new ArrayList<>(GRID_CAPACITY);
        for (int i = 0; i < GRID_CAPACITY; i++) list.add(MonitoredEntry.empty());
        return list;
    }

    /**
     * Truncates an oversize input or pads a short one with placeholders so the result is
     * exactly {@link #GRID_CAPACITY} long.
     */
    private static List<MonitoredEntry> normaliseToGrid(List<MonitoredEntry> input) {
        List<MonitoredEntry> out = new ArrayList<>(GRID_CAPACITY);
        int n = Math.min(input.size(), GRID_CAPACITY);
        for (int i = 0; i < n; i++) out.add(input.get(i));
        while (out.size() < GRID_CAPACITY) out.add(MonitoredEntry.empty());
        return out;
    }

    /**
     * Returns the version counter used by the container to detect entry-list mutations
     * worth syncing to the client GUI.
     */
    public int getEntriesVersion() {
        return entriesVersion;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(MatchMode mode) {
        this.matchMode = mode;
        this.conditionDirty = true;
        host.markDirtyAndSave();
    }

    public boolean isHysteresisEnabled() {
        return hysteresisEnabled;
    }

    public void setHysteresisEnabled(boolean hysteresisEnabled) {
        this.hysteresisEnabled = hysteresisEnabled;
        this.conditionDirty = true;
        host.markDirtyAndSave();
    }

    public boolean isConditionMet() {
        return conditionMet;
    }

    /**
     * Returns true when at least one entry is both enabled and bound to a resource.
     * Used by display-style hosts to decide whether they should show an active
     * condition color or a neutral idle state.
     */
    public boolean hasEnabledEntries() {
        for (MonitoredEntry entry : entries) {
            if (entry.isEnabled() && entry.hasResource()) return true;
        }

        return false;
    }

    // --- NBT persistence ---

    private static final String NBT_REFRESH_RATE = "RefreshRate";
    private static final String NBT_ENTRIES = "Entries";
    private static final String NBT_MATCH_MODE = "MatchMode";
    private static final String NBT_HYSTERESIS_ENABLED = "HysteresisEnabled";

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger(NBT_REFRESH_RATE, refreshRate);
        tag.setInteger(NBT_MATCH_MODE, matchMode.getId());
        tag.setBoolean(NBT_HYSTERESIS_ENABLED, hysteresisEnabled);

        if (!entries.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (MonitoredEntry entry : entries) {
                list.appendTag(entry.writeToNBT());
            }
            tag.setTag(NBT_ENTRIES, list);
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        refreshRate = tag.hasKey(NBT_REFRESH_RATE) ? tag.getInteger(NBT_REFRESH_RATE) : DEFAULT_REFRESH_RATE;
        matchMode = tag.hasKey(NBT_MATCH_MODE) ? MatchMode.fromId(tag.getInteger(NBT_MATCH_MODE)) : MatchMode.AND;
        hysteresisEnabled = tag.getBoolean(NBT_HYSTERESIS_ENABLED);

        // Always start fresh with a full-size grid of placeholders, then overwrite slots from NBT.
        // This handles older saves that had < 24 entries and prevents drift from the GUI's
        // fixed-grid invariant in case anything tampers with the tag externally.
        entries = createEmptyGrid();
        if (tag.hasKey(NBT_ENTRIES)) {
            NBTTagList list = tag.getTagList(NBT_ENTRIES, Constants.NBT.TAG_COMPOUND);
            int n = Math.min(list.tagCount(), GRID_CAPACITY);
            for (int i = 0; i < n; i++) {
                MonitoredEntry entry = MonitoredEntry.readFromNBT(list.getCompoundTagAt(i));
                if (entry != null) entries.set(i, entry);
            }
        }

        // Reset volatile state
        conditionMet = false;
        conditionDirty = true;
    }
}
