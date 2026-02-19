package com.ae2powertools.features.maintainer;

import java.util.concurrent.Future;

import javax.annotation.Nullable;

import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.storage.data.IAEItemStack;


/**
 * Represents a scheduled or running crafting task for the maintainer.
 */
public class MaintainerTask {

    /**
     * Index of the entry this task belongs to.
     */
    private final int entryIndex;

    /**
     * The item being crafted.
     */
    private final IAEItemStack targetItem;

    /**
     * Number of items to craft.
     */
    private final long craftAmount;

    /**
     * World time when this task was created.
     */
    private final long createdTime;

    /**
     * The crafting job calculation future, set when calculation begins.
     */
    @Nullable
    private Future<ICraftingJob> craftingFuture;

    /**
     * The crafting link from AE2, set when the job is submitted.
     */
    @Nullable
    private ICraftingLink craftingLink;

    /**
     * Whether this task is waiting for a free CPU.
     */
    private boolean waitingForCpu;

    /**
     * Number of times this task has been retried due to CPU unavailability.
     */
    private int cpuRetryCount;

    /**
     * Whether this task has been cancelled.
     */
    private boolean cancelled;

    public MaintainerTask(int entryIndex, IAEItemStack targetItem, long craftAmount, long createdTime) {
        this.entryIndex = entryIndex;
        this.targetItem = targetItem.copy();
        this.craftAmount = craftAmount;
        this.createdTime = createdTime;
        this.craftingFuture = null;
        this.craftingLink = null;
        this.waitingForCpu = false;
        this.cpuRetryCount = 0;
        this.cancelled = false;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    public IAEItemStack getTargetItem() {
        return targetItem;
    }

    public long getCraftAmount() {
        return craftAmount;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    @Nullable
    public Future<ICraftingJob> getCraftingFuture() {
        return craftingFuture;
    }

    public void setCraftingFuture(@Nullable Future<ICraftingJob> craftingFuture) {
        this.craftingFuture = craftingFuture;
    }

    @Nullable
    public ICraftingLink getCraftingLink() {
        return craftingLink;
    }

    public void setCraftingLink(@Nullable ICraftingLink craftingLink) {
        this.craftingLink = craftingLink;
    }

    public boolean isWaitingForCpu() {
        return waitingForCpu;
    }

    public void setWaitingForCpu(boolean waitingForCpu) {
        this.waitingForCpu = waitingForCpu;
    }

    public int getCpuRetryCount() {
        return cpuRetryCount;
    }

    public void incrementCpuRetryCount() {
        this.cpuRetryCount++;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
        if (craftingFuture != null) craftingFuture.cancel(true);
        if (craftingLink != null) craftingLink.cancel();
    }

    /**
     * Checks if the crafting job is done (completed or cancelled).
     */
    public boolean isDone() {
        if (cancelled) return true;
        if (craftingLink == null) return false;

        return craftingLink.isDone();
    }

    /**
     * Checks if the crafting job was cancelled externally.
     */
    public boolean isCraftingCancelled() {
        return craftingLink != null && craftingLink.isCanceled();
    }

    /**
     * Checks if the crafting job is currently running.
     */
    public boolean isRunning() {
        return craftingLink != null && !craftingLink.isDone() && !cancelled;
    }

    /**
     * Checks if this task is still calculating the crafting job.
     */
    public boolean isCalculating() {
        return craftingFuture != null && !craftingFuture.isDone() && craftingLink == null && !cancelled;
    }
}
