package com.ae2powertools.features.maintainer;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Represents a single entry in the Better Level Maintainer.
 * Each entry tracks a recipe output to maintain at a target quantity.
 */
public class MaintainerEntry {

    /**
     * The item stack representing the recipe output to maintain.
     * Null if no recipe is set for this entry.
     */
    @Nullable
    private IAEItemStack targetItem;

    /**
     * Target quantity to maintain in the network.
     */
    private long targetQuantity;

    /**
     * Number of items to craft per batch.
     */
    private long batchSize;

    /**
     * Frequency in seconds between maintenance checks.
     */
    private int frequencySeconds;

    /**
     * Whether this entry is enabled.
     */
    private boolean enabled;

    /**
     * Current state of this entry.
     */
    private MaintainerState state;

    /**
     * Error message if state is ERROR or POST_ERROR.
     */
    @Nullable
    private String errorMessage;

    /**
     * Time of the last successful run (world time in ticks).
     */
    private long lastRunTime;

    /**
     * Time of the next scheduled run (world time in ticks).
     */
    private long nextRunTime;

    /**
     * Time when the entry was marked dirty for rescheduling (world time in ticks).
     * Zero means not dirty. Used for debouncing rapid frequency changes.
     */
    private long scheduleDirtyTime;

    /**
     * Current quantity in the network (cached for display).
     */
    private long currentQuantity;

    public MaintainerEntry() {
        this.targetItem = null;
        this.targetQuantity = 0;
        this.batchSize = 1;
        this.frequencySeconds = 60;  // Default: 1 minute
        this.enabled = true;
        this.state = MaintainerState.IDLE;
        this.errorMessage = null;
        this.lastRunTime = 0;
        this.nextRunTime = 0;
        this.scheduleDirtyTime = 0;
        this.currentQuantity = 0;
    }

    /**
     * Checks if this entry has a valid recipe set.
     */
    public boolean hasRecipe() {
        return targetItem != null;
    }

    /**
     * Checks if this entry is empty (no recipe set).
     */
    public boolean isEmpty() {
        return targetItem == null;
    }

    // --- Getters and Setters ---

    @Nullable
    public IAEItemStack getTargetItem() {
        return targetItem;
    }

    public void setTargetItem(@Nullable IAEItemStack targetItem) {
        this.targetItem = targetItem;
    }

    @Nullable
    public ItemStack getTargetItemStack() {
        return targetItem != null ? targetItem.createItemStack() : ItemStack.EMPTY;
    }

    public long getTargetQuantity() {
        return targetQuantity;
    }

    public void setTargetQuantity(long targetQuantity) {
        this.targetQuantity = Math.max(0, targetQuantity);
    }

    public long getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(long batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public int getFrequencySeconds() {
        return frequencySeconds;
    }

    public void setFrequencySeconds(int frequencySeconds) {
        this.frequencySeconds = Math.max(1, frequencySeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            this.state = MaintainerState.DISABLED;
        } else if (this.state == MaintainerState.DISABLED) {
            this.state = MaintainerState.IDLE;
        }
    }

    public MaintainerState getState() {
        return state;
    }

    public void setState(MaintainerState state) {
        this.state = state;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(@Nullable String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setError(MaintainerState errorState, String message) {
        this.state = errorState;
        this.errorMessage = message;
    }

    public void clearError() {
        if (this.state.isError()) {
            this.state = this.enabled ? MaintainerState.IDLE : MaintainerState.DISABLED;
        }

        this.errorMessage = null;
    }

    public long getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(long lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

    public long getNextRunTime() {
        return nextRunTime;
    }

    public void setNextRunTime(long nextRunTime) {
        this.nextRunTime = nextRunTime;
    }

    /**
     * Marks this entry as dirty for rescheduling.
     * Call this when frequency changes to trigger a debounced reschedule.
     */
    public void markScheduleDirty(long currentWorldTime) {
        this.scheduleDirtyTime = currentWorldTime;
    }

    /**
     * Checks if this entry is dirty and the debounce period has passed.
     * @param currentWorldTime Current world time in ticks
     * @param debounceTicks Number of ticks to wait before considering debounce complete
     * @return true if entry should be rescheduled
     */
    public boolean shouldReschedule(long currentWorldTime, int debounceTicks) {
        if (scheduleDirtyTime <= 0) return false;

        return currentWorldTime - scheduleDirtyTime >= debounceTicks;
    }

    /**
     * Clears the dirty flag after rescheduling.
     */
    public void clearScheduleDirty() {
        this.scheduleDirtyTime = 0;
    }

    /**
     * Returns true if this entry is marked dirty for rescheduling.
     */
    public boolean isScheduleDirty() {
        return scheduleDirtyTime > 0;
    }

    public long getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(long currentQuantity) {
        this.currentQuantity = currentQuantity;
    }

    /**
     * Checks if crafting is needed based on current vs target quantity.
     */
    public boolean needsCrafting() {
        if (!hasRecipe() || !enabled) return false;

        // Craft if current quantity is below target
        return currentQuantity < targetQuantity;
    }

    /**
     * Gets the frequency in ticks (20 ticks per second).
     */
    public int getFrequencyTicks() {
        return frequencySeconds * 20;
    }

    // --- NBT Serialization ---

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        if (targetItem != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            targetItem.writeToNBT(itemTag);
            tag.setTag("targetItem", itemTag);
        }

        tag.setLong("targetQuantity", targetQuantity);
        tag.setLong("batchSize", batchSize);
        tag.setInteger("frequencySeconds", frequencySeconds);
        tag.setBoolean("enabled", enabled);
        tag.setInteger("state", state.ordinal());
        tag.setLong("lastRunTime", lastRunTime);
        tag.setLong("nextRunTime", nextRunTime);

        if (errorMessage != null) tag.setString("errorMessage", errorMessage);

        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("targetItem")) {
            targetItem = AEItemStack.fromNBT(tag.getCompoundTag("targetItem"));
        } else {
            targetItem = null;
        }

        targetQuantity = tag.getLong("targetQuantity");
        batchSize = tag.getLong("batchSize");
        if (batchSize < 1) batchSize = 1;

        frequencySeconds = tag.getInteger("frequencySeconds");
        if (frequencySeconds < 1) frequencySeconds = 60;

        enabled = tag.getBoolean("enabled");
        int stateOrdinal = tag.getInteger("state");
        if (stateOrdinal >= 0 && stateOrdinal < MaintainerState.values().length) {
            state = MaintainerState.values()[stateOrdinal];
        } else {
            state = enabled ? MaintainerState.IDLE : MaintainerState.DISABLED;
        }

        // Reset transient states to IDLE since associated tasks don't survive restart.
        // RUNNING, SCHEDULED, and STALLED all require an in-memory task to be meaningful.
        if (state.isActive()) state = enabled ? MaintainerState.IDLE : MaintainerState.DISABLED;

        lastRunTime = tag.getLong("lastRunTime");
        // Reset nextRunTime so entries are checked immediately after restart.
        // The checkCraftingNeeds() will reschedule them properly based on current world time.
        nextRunTime = 0;

        errorMessage = null;
        if (tag.hasKey("errorMessage")) errorMessage = tag.getString("errorMessage");
    }

    /**
     * Formats the frequency as a human-readable string (e.g., "1h 30m 15s").
     */
    public String formatFrequency() {
        return formatTime(frequencySeconds);
    }

    /**
     * Formats a time in seconds as a human-readable string.
     */
    public static String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";

        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Parses a time string (e.g., "1h 30m 15s") into seconds.
     * 
     * @return The time in seconds, or -1 if parsing failed
     */
    public static int parseTime(String input) {
        if (input == null || input.trim().isEmpty()) return -1;

        input = input.trim().toLowerCase();
        int totalSeconds = 0;
        StringBuilder currentNumber = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (Character.isDigit(c)) {
                currentNumber.append(c);
            } else if (c == 'd' || c == 'h' || c == 'm' || c == 's') {
                if (currentNumber.length() == 0) return -1;

                int value;
                try {
                    value = Integer.parseInt(currentNumber.toString());
                } catch (NumberFormatException e) {
                    return -1;
                }

                switch (c) {
                    case 'd':
                        totalSeconds += value * 86400;
                        break;
                    case 'h':
                        totalSeconds += value * 3600;
                        break;
                    case 'm':
                        totalSeconds += value * 60;
                        break;
                    case 's':
                        totalSeconds += value;
                        break;
                }

                currentNumber = new StringBuilder();
            } else if (c == ' ') {
                // Allow spaces between components
            } else {
                return -1;  // Invalid character
            }
        }

        // Handle trailing number without unit (assume seconds)
        if (currentNumber.length() > 0) {
            try {
                totalSeconds += Integer.parseInt(currentNumber.toString());
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return totalSeconds > 0 ? totalSeconds : -1;
    }

    /**
     * Formats a quantity with comma separators (e.g., 1,234,567).
     * Uses explicit US locale for consistent formatting regardless of system locale.
     */
    public static String formatQuantity(long quantity) {
        return String.format(java.util.Locale.US, "%,d", quantity);
    }

    /**
     * Parses a quantity string with optional characters.
     * 
     * @return The quantity, or -1 if parsing failed
     */
    public static long parseQuantity(String input) {
        if (input == null || input.trim().isEmpty()) return -1;

        // Remove any non-digit characters
        input = input.replaceAll("[^\\d]", "");

        try {
            long value = Long.parseLong(input);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Creates a copy of this entry.
     */
    public MaintainerEntry copy() {
        MaintainerEntry copy = new MaintainerEntry();
        copy.targetItem = this.targetItem != null ? this.targetItem.copy() : null;
        copy.targetQuantity = this.targetQuantity;
        copy.batchSize = this.batchSize;
        copy.frequencySeconds = this.frequencySeconds;
        copy.enabled = this.enabled;
        copy.state = this.state;
        copy.errorMessage = this.errorMessage;
        copy.lastRunTime = this.lastRunTime;
        copy.nextRunTime = this.nextRunTime;
        copy.scheduleDirtyTime = this.scheduleDirtyTime;
        copy.currentQuantity = this.currentQuantity;

        return copy;
    }
}
