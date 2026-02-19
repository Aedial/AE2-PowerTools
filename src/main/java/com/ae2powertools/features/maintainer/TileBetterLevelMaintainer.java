package com.ae2powertools.features.maintainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.common.util.Constants;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import appeng.tile.AEBaseTile;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.AEItemStack;

import com.ae2powertools.AE2PowerTools;


/**
 * Tile entity for the Better Level Maintainer block.
 * Manages multiple crafting entries to maintain item quantities in the network.
 *
 * FIXME: After restarting, entries start in Not enough items to start crafting,
 *        and sometimes stay stuck there.
 */
public class TileBetterLevelMaintainer extends AEBaseTile
        implements ITickable, IActionHost, IGridProxyable, ICraftingRequester {

    /**
     * Number of entries per row in the GUI.
     */
    public static final int ENTRIES_PER_ROW = 3;

    /**
     * Tick interval for checking crafting needs (every second).
     */
    private static final int CHECK_INTERVAL = 20;

    /**
     * Maximum time (in ticks) to wait for a crafting job calculation before timing out.
     * 10 seconds should be plenty for any reasonable job.
     */
    private static final int JOB_CALCULATION_TIMEOUT = 200;

    /**
     * Debounce time (in ticks) before rescheduling an entry after frequency changes.
     * 10 seconds to allow rapid scrolling without spamming reschedules.
     */
    private static final int SCHEDULE_DEBOUNCE_TICKS = 10 * 20;

    private final AENetworkProxy gridProxy;
    private final IActionSource actionSource;
    private final List<MaintainerEntry> entries;
    private final List<MaintainerTask> activeTasks;

    /**
     * Crafting links restored from NBT after server restart.
     * These links don't have associated tasks but must be tracked for AE2 reconciliation.
     * When items are delivered via injectCraftedItems, the link is used to identify the entry.
     */
    private final List<PersistedCraftingLink> persistedLinks;

    /**
     * Number of open rows visible to the user.
     * Starts at 1 and increases when an entry in the last row is populated.
     */
    private int openRows;

    private int tickCounter;
    private boolean needsSync;

    public TileBetterLevelMaintainer() {
        this.gridProxy = new AENetworkProxy(this, "proxy", this.getItemFromTile(this), true);
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.actionSource = new MachineSource(this);

        // Start with 1 row (3 empty entries)
        this.entries = new ArrayList<>();
        for (int i = 0; i < ENTRIES_PER_ROW; i++) this.entries.add(new MaintainerEntry());

        this.activeTasks = new ArrayList<>();
        this.persistedLinks = new ArrayList<>();

        this.openRows = 1;
        this.tickCounter = 0;
        this.needsSync = false;
    }

    @Override
    public void update() {
        if (world.isRemote) return;

        tickCounter++;

        // Process pending crafting jobs (lightweight: just checks if futures are done)
        processPendingJobs();

        // Process CPU wait queue
        processCpuWaitQueue();

        // Check active tasks status
        updateActiveTaskStates();

        // Periodic check for crafting needs
        if (tickCounter % CHECK_INTERVAL == 0) checkCraftingNeeds();

        // Process dirty entries (debounced frequency changes)
        processDirtyEntries();

        // Sync to clients if needed
        if (needsSync) {
            markForUpdate();
            needsSync = false;
        }
    }

    /**
     * Checks all entries and schedules crafting as needed.
     */
    private void checkCraftingNeeds() {
        if (!gridProxy.isActive()) return;

        long worldTime = world.getTotalWorldTime();

        try {
            IStorageGrid storageGrid = gridProxy.getStorage();
            IMEMonitor<IAEItemStack> storage = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            int totalSlots = openRows * ENTRIES_PER_ROW;
            for (int i = 0; i < totalSlots && i < entries.size(); i++) {
                MaintainerEntry entry = entries.get(i);

                if (!entry.hasRecipe() || !entry.isEnabled()) continue;

                // Skip if task is already running or scheduled
                if (hasActiveTask(i)) continue;

                // Check if it's time for next run
                if (worldTime < entry.getNextRunTime()) continue;

                // Update current quantity
                IAEItemStack stored = storage.getStorageList().findPrecise(entry.getTargetItem());
                long currentQty = stored != null ? stored.getStackSize() : 0;
                entry.setCurrentQuantity(currentQty);

                // Check if crafting is needed
                if (entry.needsCrafting()) {
                    scheduleCrafting(i, entry);
                } else {
                    // Schedule next check
                    entry.setNextRunTime(worldTime + entry.getFrequencyTicks());
                    entry.setState(MaintainerState.IDLE);
                    entry.clearError();
                }

                needsSync = true;
            }
        } catch (GridAccessException e) {
            // Grid not available
        }
    }

    /**
     * Processes entries marked dirty due to frequency changes.
     * After the debounce period passes, recalculates nextRunTime based on the new frequency.
     */
    private void processDirtyEntries() {
        long worldTime = world.getTotalWorldTime();
        int totalSlots = openRows * ENTRIES_PER_ROW;

        for (int i = 0; i < totalSlots && i < entries.size(); i++) {
            MaintainerEntry entry = entries.get(i);

            if (!entry.isScheduleDirty()) continue;
            if (!entry.shouldReschedule(worldTime, SCHEDULE_DEBOUNCE_TICKS)) continue;

            // Debounce period has passed - reschedule this entry
            entry.clearScheduleDirty();

            if (!entry.hasRecipe() || !entry.isEnabled()) continue;

            // Calculate new nextRunTime based on lastRunTime and new frequency
            // If the new schedule time is in the past, run immediately (nextRunTime = 0)
            long newNextRunTime = entry.getLastRunTime() + entry.getFrequencyTicks();
            if (newNextRunTime < worldTime) newNextRunTime = 0;

            entry.setNextRunTime(newNextRunTime);
            needsSync = true;
        }
    }

    /**
     * Refreshes the current quantity for all entries from storage.
     * Called when the GUI is opened to ensure quantities are up-to-date.
     */
    public void refreshAllCurrentQuantities() {
        if (world.isRemote) return;
        if (!gridProxy.isActive()) return;

        try {
            IStorageGrid storageGrid = gridProxy.getStorage();
            IMEMonitor<IAEItemStack> storage = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            for (MaintainerEntry entry : entries) {
                if (!entry.hasRecipe()) continue;

                IAEItemStack stored = storage.getStorageList().findPrecise(entry.getTargetItem());
                long currentQty = stored != null ? stored.getStackSize() : 0;
                entry.setCurrentQuantity(currentQty);
            }

            needsSync = true;
        } catch (GridAccessException e) {
            // Grid not available
        }
    }

    /**
     * Refreshes the current quantity for a single entry from storage.
     */
    private void refreshEntryQuantity(int entryIndex) {
        if (world.isRemote) return;
        if (!gridProxy.isActive()) return;
        if (entryIndex < 0 || entryIndex >= entries.size()) return;

        MaintainerEntry entry = entries.get(entryIndex);
        if (!entry.hasRecipe()) return;

        try {
            IStorageGrid storageGrid = gridProxy.getStorage();
            IMEMonitor<IAEItemStack> storage = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            IAEItemStack stored = storage.getStorageList().findPrecise(entry.getTargetItem());
            long currentQty = stored != null ? stored.getStackSize() : 0;
            entry.setCurrentQuantity(currentQty);
        } catch (GridAccessException e) {
            // Grid not available
        }
    }

    /**
     * Schedules a crafting job for an entry.
     */
    private void scheduleCrafting(int entryIndex, MaintainerEntry entry) {
        try {
            ICraftingGrid craftingGrid = gridProxy.getCrafting();
            IGrid grid = gridProxy.getGrid();

            IAEItemStack targetItem = entry.getTargetItem().copy();
            targetItem.setStackSize(entry.getBatchSize());

            // Check if any CPU is free before calculating the job (avoid unnecessary load)
            boolean hasFreeCpu = craftingGrid.getCpus().stream().anyMatch(cpu -> !cpu.isBusy());
            if (!hasFreeCpu) {
                // No CPU available, create task marked as waiting for CPU
                MaintainerTask task = new MaintainerTask(
                        entryIndex, targetItem, entry.getBatchSize(), world.getTotalWorldTime());
                task.setWaitingForCpu(true);
                activeTasks.add(task);
                entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.no_cpu");
                needsSync = true;
                return;
            }

            // Begin crafting job calculation
            entry.setState(MaintainerState.SCHEDULED);
            Future<ICraftingJob> future = craftingGrid.beginCraftingJob(
                    world, grid, actionSource, targetItem, null);

            // Create task to track this job with its future
            MaintainerTask task = new MaintainerTask(
                    entryIndex, targetItem, entry.getBatchSize(), world.getTotalWorldTime());
            task.setCraftingFuture(future);
            activeTasks.add(task);

            needsSync = true;

        } catch (GridAccessException e) {
            entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.no_network");
        }
    }

    /**
     * Processes completed crafting job calculations.
     */
    private void processPendingJobs() {
        long currentTime = world.getTotalWorldTime();
        Iterator<MaintainerTask> taskIter = activeTasks.iterator();

        while (taskIter.hasNext()) {
            MaintainerTask task = taskIter.next();

            // Skip tasks that are already submitted (running) or waiting for CPU
            if (task.getCraftingLink() != null || task.isWaitingForCpu()) continue;

            Future<ICraftingJob> future = task.getCraftingFuture();
            if (future == null) continue;

            // Check for timeout on job calculation
            long elapsed = currentTime - task.getCreatedTime();
            if (!future.isDone() && elapsed > JOB_CALCULATION_TIMEOUT) {
                MaintainerEntry entry = entries.get(task.getEntryIndex());
                entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.calculation_timeout");
                entry.setNextRunTime(currentTime + entry.getFrequencyTicks());
                task.cancel();
                taskIter.remove();
                needsSync = true;

                continue;
            }

            if (!future.isDone()) continue;

            try {
                ICraftingJob job = future.get();

                if (job == null) {
                    MaintainerEntry entry = entries.get(task.getEntryIndex());
                    entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.job_failed");
                    entry.setNextRunTime(currentTime + entry.getFrequencyTicks());
                    taskIter.remove();
                    needsSync = true;
                    continue;
                }

                if (job.isSimulation()) {
                    MaintainerEntry entry = entries.get(task.getEntryIndex());

                    // Determine if this is a "no pattern" or "missing resources" issue
                    // by checking if any pattern exists for this item
                    String errorKey;
                    try {
                        ICraftingGrid craftingGrid = gridProxy.getCrafting();
                        boolean hasPattern = !craftingGrid.getCraftingFor(
                                task.getTargetItem(), null, 0, world).isEmpty();

                        if (hasPattern) {
                            // Pattern exists but resources are missing
                            errorKey = "gui.ae2powertools.maintainer.error.missing_resources";
                        } else {
                            // No pattern found for this item
                            errorKey = "gui.ae2powertools.maintainer.error.no_recipe";
                        }
                    } catch (GridAccessException e) {
                        errorKey = "gui.ae2powertools.maintainer.error.missing_resources";
                    }

                    entry.setError(MaintainerState.ERROR, errorKey);
                    entry.setNextRunTime(currentTime + entry.getFrequencyTicks());
                    taskIter.remove();
                    needsSync = true;
                    continue;
                }

                // Job is valid - submit it
                if (submitCraftingJob(task, job)) {
                    // Task failed permanently (CPU too small, network error) - remove it
                    taskIter.remove();
                }
                // else: Task remains in activeTasks (either waiting for CPU, or running with link)

            } catch (Exception e) {
                AE2PowerTools.LOGGER.error("Error processing crafting job", e);
                MaintainerEntry entry = entries.get(task.getEntryIndex());
                entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.job_failed");
                entry.setNextRunTime(currentTime + entry.getFrequencyTicks());
                taskIter.remove();
                needsSync = true;
            }
        }
    }

    /**
     * Submits a calculated crafting job to be executed.
     * @return true if task failed permanently and should be removed, false if task should remain in activeTasks
     */
    private boolean submitCraftingJob(MaintainerTask task, ICraftingJob job) {
        try {
            ICraftingGrid craftingGrid = gridProxy.getCrafting();
            MaintainerEntry entry = entries.get(task.getEntryIndex());

            // Determine if the issue is "job too large for any CPU" BEFORE trying to submit
            // This allows us to fail fast without waiting for a CPU that will never work
            long jobBytes = job.getByteTotal();
            boolean cpuTooSmall = craftingGrid.getCpus().stream()
                    .noneMatch(cpu -> cpu.getAvailableStorage() >= jobBytes);

            if (cpuTooSmall) {
                String jobSize = ReadableNumberConverter.INSTANCE.toWideReadableForm(jobBytes);
                int bestCpuStorage = (int) craftingGrid.getCpus().stream()
                        .mapToLong(ICraftingCPU::getAvailableStorage)
                        .max().orElse(0);
                String cpuSize = ReadableNumberConverter.INSTANCE.toWideReadableForm(bestCpuStorage);
                String error = I18n.translateToLocalFormatted("gui.ae2powertools.maintainer.error.cpu_too_small", jobSize, cpuSize);
                entry.setError(MaintainerState.ERROR, error);
                entry.setNextRunTime(world.getTotalWorldTime() + entry.getFrequencyTicks());
                needsSync = true;

                return true;  // Permanent failure - remove task
            }

            // Try to submit the job
            ICraftingLink link = craftingGrid.submitJob(job, this, null, false, actionSource);

            if (link == null) {
                // No CPU available right now, mark as waiting
                task.setWaitingForCpu(true);
                task.incrementCpuRetryCount();
                entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.no_cpu");
                needsSync = true;

                return false;  // Keep in activeTasks, waiting for CPU
            }

            // Successfully submitted - task stays in activeTasks to track completion
            task.setCraftingLink(link);
            task.setWaitingForCpu(false);
            entry.setState(MaintainerState.RUNNING);
            entry.clearError();
            needsSync = true;

            return false;  // Keep in activeTasks to track crafting progress

        } catch (GridAccessException e) {
            MaintainerEntry entry = entries.get(task.getEntryIndex());
            entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.no_network");
            entry.setNextRunTime(world.getTotalWorldTime() + entry.getFrequencyTicks());

            return true;  // Permanent failure - remove task
        }
    }

    /**
     * Retries submitting tasks that are waiting for a free CPU.
     * Tasks waiting for CPU are in activeTasks with waitingForCpu flag set.
     */
    private void processCpuWaitQueue() {
        try {
            ICraftingGrid craftingGrid = gridProxy.getCrafting();

            // Check if any CPU is free
            boolean hasFreeCpu = craftingGrid.getCpus().stream().anyMatch(cpu -> !cpu.isBusy());
            if (!hasFreeCpu) return;

            // Find tasks waiting for CPU and reschedule them
            // We need to remove and reschedule because the old job calculation is stale
            Iterator<MaintainerTask> iter = activeTasks.iterator();
            List<Integer> entriesToReschedule = new ArrayList<>();

            while (iter.hasNext()) {
                MaintainerTask task = iter.next();
                if (!task.isWaitingForCpu()) continue;

                if (task.isCancelled()) {
                    iter.remove();
                    continue;
                }

                // Collect entry indices to reschedule after iteration
                entriesToReschedule.add(task.getEntryIndex());
                iter.remove();
            }

            // Reschedule collected entries
            for (int entryIndex : entriesToReschedule) {
                MaintainerEntry entry = entries.get(entryIndex);
                if (entry != null && entry.hasRecipe() && entry.isEnabled()) {
                    scheduleCrafting(entryIndex, entry);
                }
            }

        } catch (GridAccessException e) {
            // Grid not available
        }
    }

    /**
     * Updates the state of active tasks based on their crafting links.
     */
    private void updateActiveTaskStates() {
        Iterator<MaintainerTask> iter = activeTasks.iterator();

        while (iter.hasNext()) {
            MaintainerTask task = iter.next();
            MaintainerEntry entry = entries.get(task.getEntryIndex());

            if (task.getCraftingLink() == null) continue;

            if (task.isDone()) {
                if (task.isCraftingCancelled()) {
                    entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.cancelled");
                    entry.setNextRunTime(world.getTotalWorldTime() + entry.getFrequencyTicks());
                } else {
                    // Success! Schedule next run and refresh quantity
                    entry.setLastRunTime(world.getTotalWorldTime());
                    entry.setNextRunTime(world.getTotalWorldTime() + entry.getFrequencyTicks());
                    entry.setState(MaintainerState.IDLE);
                    entry.clearError();
                    refreshEntryQuantity(task.getEntryIndex());
                }

                iter.remove();
                needsSync = true;
            }
        }
    }

    /**
     * Checks if there's an active task for the given entry index.
     * This includes both active tasks and persisted links restored from server restart.
     */
    private boolean hasActiveTask(int entryIndex) {
        for (MaintainerTask task : activeTasks) {
            if (task.getEntryIndex() == entryIndex && !task.isCancelled()) return true;
        }

        // Also check persisted links from server restart - these represent ongoing crafts
        // that were saved before restart and are still running in the crafting CPUs
        for (PersistedCraftingLink persisted : persistedLinks) {
            if (persisted.getEntryIndex() == entryIndex) {
                ICraftingLink link = persisted.getLink();
                if (link != null && !link.isDone() && !link.isCanceled()) return true;
            }
        }

        return false;
    }

    /**
     * Updates the number of open rows based on entry population.
     * A new row opens when an entry in the last row is populated.
     */
    public void updateOpenRows() {
        // Find the last row that has any populated entry
        int lastPopulatedRow = 0;

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).hasRecipe()) lastPopulatedRow = i / ENTRIES_PER_ROW;
        }

        // Always have at least one row, and add one more if last row has an entry
        int lastRowStart = lastPopulatedRow * ENTRIES_PER_ROW;
        boolean lastRowHasEntry = false;
        for (int i = lastRowStart; i < lastRowStart + ENTRIES_PER_ROW && i < entries.size(); i++) {
            if (entries.get(i).hasRecipe()) {
                lastRowHasEntry = true;
                break;
            }
        }

        int neededRows = lastRowHasEntry ? lastPopulatedRow + 2 : Math.max(1, lastPopulatedRow + 1);

        // Ensure we have enough entries for the needed rows
        int neededEntries = neededRows * ENTRIES_PER_ROW;
        while (entries.size() < neededEntries) entries.add(new MaintainerEntry());

        openRows = neededRows;
        markDirty();
    }

    // --- Public accessors ---

    public MaintainerEntry getEntry(int index) {
        if (index < 0 || index >= entries.size()) return null;

        return entries.get(index);
    }

    public void setEntry(int index, @Nullable IAEItemStack item, long targetQty, long batchSize, int frequencySeconds) {
        if (index < 0) return;

        // Expand entries list if needed
        while (index >= entries.size()) entries.add(new MaintainerEntry());

        MaintainerEntry entry = entries.get(index);

        // Track if frequency changed to trigger debounced reschedule
        int oldFrequency = entry.getFrequencySeconds();
        boolean frequencyChanged = entry.hasRecipe() && oldFrequency != frequencySeconds;

        entry.setTargetItem(item);
        entry.setTargetQuantity(targetQty);
        entry.setBatchSize(batchSize);
        entry.setFrequencySeconds(frequencySeconds);

        // Mark dirty for rescheduling if frequency changed
        if (frequencyChanged && !world.isRemote) entry.markScheduleDirty(world.getTotalWorldTime());

        if (item != null && entry.getState() == MaintainerState.DISABLED) entry.setEnabled(true);

        // Refresh current quantity immediately when recipe is set
        if (item != null) refreshEntryQuantity(index);

        updateOpenRows();
        markDirty();
        needsSync = true;
    }

    public void clearEntry(int index) {
        if (index < 0 || index >= entries.size()) return;

        // Cancel any active task
        cancelTaskForEntry(index);

        entries.set(index, new MaintainerEntry());
        updateOpenRows();
        markDirty();
        needsSync = true;
    }

    public void toggleEntryEnabled(int index) {
        if (index < 0 || index >= entries.size()) return;

        MaintainerEntry entry = entries.get(index);
        entry.setEnabled(!entry.isEnabled());

        if (!entry.isEnabled()) cancelTaskForEntry(index);

        markDirty();
        needsSync = true;
    }

    private void cancelTaskForEntry(int index) {
        activeTasks.removeIf(task -> {
            if (task.getEntryIndex() == index) {
                task.cancel();
                return true;
            }

            return false;
        });
    }

    public int getOpenRows() {
        return openRows;
    }

    /**
     * Returns the total number of open entry slots (openRows * ENTRIES_PER_ROW).
     */
    public int getOpenSlots() {
        return openRows * ENTRIES_PER_ROW;
    }

    public int getActiveCpuCount() {
        int count = 0;
        for (MaintainerTask task : activeTasks) {
            if (task.isRunning()) count++;
        }

        return count;
    }

    public int getTotalCpuCount() {
        try {
            return gridProxy.getCrafting().getCpus().size();
        } catch (GridAccessException e) {
            return 0;
        }
    }

    public int getRunningRecipeCount() {
        return (int) activeTasks.stream().filter(MaintainerTask::isRunning).count();
    }

    public int getTotalRecipeCount() {
        int count = 0;
        int totalSlots = openRows * ENTRIES_PER_ROW;
        for (int i = 0; i < totalSlots && i < entries.size(); i++) {
            MaintainerEntry entry = entries.get(i);
            if (entry.hasRecipe() && entry.isEnabled()) count++;
        }

        return count;
    }

    public int getFailedRecipeCount() {
        int count = 0;
        int totalSlots = openRows * ENTRIES_PER_ROW;
        for (int i = 0; i < totalSlots && i < entries.size(); i++) {
            if (entries.get(i).getState() == MaintainerState.ERROR) count++;
        }

        return count;
    }

    public int getPostErrorRecipeCount() {
        int count = 0;
        int totalSlots = openRows * ENTRIES_PER_ROW;
        for (int i = 0; i < totalSlots && i < entries.size(); i++) {
            if (entries.get(i).getState() == MaintainerState.POST_ERROR) count++;
        }

        return count;
    }

    // --- AE2 Integration ---

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
        // Re-check all entries when grid changes
        needsSync = true;
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

    // --- ICraftingRequester ---

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        List<ICraftingLink> links = new ArrayList<>();

        // Include links from active tasks
        for (MaintainerTask task : activeTasks) {
            ICraftingLink link = task.getCraftingLink();
            if (link != null && !link.isDone()) links.add(link);
        }

        // Include persisted links restored from NBT (survived server restart)
        for (PersistedCraftingLink persisted : persistedLinks) {
            ICraftingLink link = persisted.getLink();
            if (link != null && !link.isDone()) links.add(link);
        }

        return ImmutableSet.copyOf(links);
    }

    @Override
    public IAEItemStack injectCraftedItems(ICraftingLink link, IAEItemStack items, Actionable mode) {
        // The Level Maintainer receives crafted items and injects them into network storage.
        // We must handle this ourselves - AE2 delivers items TO the requester, not to storage.

        try {
            IStorageGrid storageGrid = gridProxy.getStorage();
            IMEMonitor<IAEItemStack> storage = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            // Inject items into network storage and return any remainder.
            // Don't set error states here - transient capacity issues are normal during crafting.
            // The craft completion is tracked via ICraftingLink state in updateActiveTaskStates.
            // FIXME: still need to track if the network was full and items were rejected, to properly set error state on entry
            return storage.injectItems(items, mode, actionSource);

        } catch (GridAccessException e) {
            // Grid not available - cannot inject items, return them as remainder
            return items;
        }
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        // Check if this is a persisted link (from server restart)
        Iterator<PersistedCraftingLink> iter = persistedLinks.iterator();
        while (iter.hasNext()) {
            PersistedCraftingLink persisted = iter.next();
            if (persisted.getLink() == link) {
                // Persisted link completed - update entry state
                int entryIndex = persisted.getEntryIndex();
                if (entryIndex >= 0 && entryIndex < entries.size()) {
                    MaintainerEntry entry = entries.get(entryIndex);
                    if (link.isCanceled()) {
                        entry.setError(MaintainerState.ERROR, "gui.ae2powertools.maintainer.error.cancelled");
                    } else {
                        entry.setLastRunTime(world.getTotalWorldTime());
                        entry.setState(MaintainerState.IDLE);
                        entry.clearError();
                        refreshEntryQuantity(entryIndex);
                    }
                    entry.setNextRunTime(world.getTotalWorldTime() + entry.getFrequencyTicks());
                }

                iter.remove();
                break;
            }
        }

        // State change will be picked up in updateActiveTaskStates for active tasks
        needsSync = true;
    }

    // --- NBT ---

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        gridProxy.readFromNBT(data);

        openRows = data.getInteger("openRows");
        if (openRows < 1) openRows = 1;

        entries.clear();
        NBTTagList entriesList = data.getTagList("entries", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entriesList.tagCount(); i++) {
            MaintainerEntry entry = new MaintainerEntry();
            entry.readFromNBT(entriesList.getCompoundTagAt(i));
            entries.add(entry);
        }

        // Ensure we have at least one row of entries
        while (entries.size() < openRows * ENTRIES_PER_ROW) entries.add(new MaintainerEntry());

        // Restore crafting links that were active during save.
        // These links allow AE2 to reconnect ongoing crafting jobs after restart.
        persistedLinks.clear();
        NBTTagList linksList = data.getTagList("craftingLinks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < linksList.tagCount(); i++) {
            NBTTagCompound linkTag = linksList.getCompoundTagAt(i);
            int entryIndex = linkTag.getInteger("entryIndex");
            NBTTagCompound linkData = linkTag.getCompoundTag("link");

            if (!linkData.isEmpty()) {
                ICraftingLink link = AEApi.instance().storage().loadCraftingLink(linkData, this);
                if (link != null && !link.isDone() && !link.isCanceled()) {
                    persistedLinks.add(new PersistedCraftingLink(entryIndex, link));
                    // Mark entry as RUNNING since it has an ongoing craft
                    if (entryIndex >= 0 && entryIndex < entries.size()) {
                        entries.get(entryIndex).setState(MaintainerState.RUNNING);
                    }
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        gridProxy.writeToNBT(data);

        data.setInteger("openRows", openRows);

        NBTTagList entriesList = new NBTTagList();
        for (MaintainerEntry entry : entries) entriesList.appendTag(entry.writeToNBT());
        data.setTag("entries", entriesList);

        // Save active crafting links so they can be restored after restart.
        // This prevents item loss when the server restarts mid-craft.
        NBTTagList linksList = new NBTTagList();

        // Save links from active tasks
        for (MaintainerTask task : activeTasks) {
            ICraftingLink link = task.getCraftingLink();
            if (link != null && !link.isDone() && !link.isCanceled()) {
                NBTTagCompound linkTag = new NBTTagCompound();
                linkTag.setInteger("entryIndex", task.getEntryIndex());

                NBTTagCompound linkData = new NBTTagCompound();
                link.writeToNBT(linkData);
                linkTag.setTag("link", linkData);
                linksList.appendTag(linkTag);
            }
        }

        // Also save any persisted links that haven't completed yet
        for (PersistedCraftingLink persisted : persistedLinks) {
            ICraftingLink link = persisted.getLink();
            if (link != null && !link.isDone() && !link.isCanceled()) {
                NBTTagCompound linkTag = new NBTTagCompound();
                linkTag.setInteger("entryIndex", persisted.getEntryIndex());

                NBTTagCompound linkData = new NBTTagCompound();
                link.writeToNBT(linkData);
                linkTag.setTag("link", linkData);
                linksList.appendTag(linkTag);
            }
        }

        data.setTag("craftingLinks", linksList);

        return data;
    }

    // --- Client Sync ---

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);

        // Write open rows and entry count
        data.writeInt(openRows);
        int entryCount = Math.min(entries.size(), openRows * ENTRIES_PER_ROW);
        data.writeInt(entryCount);

        // Write each entry
        for (int i = 0; i < entryCount; i++) {
            MaintainerEntry entry = entries.get(i);
            data.writeBoolean(entry.hasRecipe());

            if (entry.hasRecipe()) {
                entry.getTargetItem().writeToPacket(data);
                data.writeLong(entry.getTargetQuantity());
                data.writeLong(entry.getBatchSize());
                data.writeInt(entry.getFrequencySeconds());
                data.writeBoolean(entry.isEnabled());
                data.writeInt(entry.getState().ordinal());
                data.writeLong(entry.getCurrentQuantity());

                // Sync error message for tooltip display
                String errorMsg = entry.getErrorMessage();
                data.writeBoolean(errorMsg != null);
                if (errorMsg != null) {
                    byte[] msgBytes = errorMsg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    data.writeShort(msgBytes.length);
                    data.writeBytes(msgBytes);
                }
            }
        }
    }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);

        int newOpenRows = data.readInt();
        int entryCount = data.readInt();

        // Ensure entries list has enough capacity
        while (entries.size() < entryCount) entries.add(new MaintainerEntry());

        // Read each entry
        for (int i = 0; i < entryCount; i++) {
            MaintainerEntry entry = entries.get(i);
            boolean hasRecipe = data.readBoolean();

            if (hasRecipe) {
                IAEItemStack targetItem = AEItemStack.fromPacket(data);
                long targetQty = data.readLong();
                long batchSize = data.readLong();
                int freqSecs = data.readInt();
                boolean enabled = data.readBoolean();
                int stateOrdinal = data.readInt();
                long currentQty = data.readLong();

                // Read error message
                boolean hasError = data.readBoolean();
                String errorMsg = null;
                if (hasError) {
                    int msgLen = data.readShort();
                    byte[] msgBytes = new byte[msgLen];
                    data.readBytes(msgBytes);
                    errorMsg = new String(msgBytes, java.nio.charset.StandardCharsets.UTF_8);
                }

                entry.setTargetItem(targetItem);
                entry.setTargetQuantity(targetQty);
                entry.setBatchSize(batchSize);
                entry.setFrequencySeconds(freqSecs);
                entry.setEnabled(enabled);
                if (stateOrdinal >= 0 && stateOrdinal < MaintainerState.values().length) {
                    entry.setState(MaintainerState.values()[stateOrdinal]);
                }
                entry.setCurrentQuantity(currentQty);
                entry.setErrorMessage(errorMsg);
            } else {
                // Clear entry if it had a recipe before
                if (entry.hasRecipe()) entries.set(i, new MaintainerEntry());
            }
        }

        if (newOpenRows != openRows) {
            openRows = newOpenRows;
            changed = true;
        }

        return changed;
    }

    // --- Lifecycle ---

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        gridProxy.onChunkUnload();
    }

    @Override
    public void onReady() {
        super.onReady();
        gridProxy.onReady();
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
