package com.ae2powertools.features.crafter;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.ITextComponent;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Represents a single recipe entry in the AutoCrafter.
 * Each entry holds a pattern, cached recipe info, and internal catalyst inventory.
 */
public class CrafterEntry {

    public static final int CATALYST_SLOTS = 9;

    /**
     * The pattern item stack (encoded pattern).
     */
    @Nullable
    private ItemStack patternStack;

    /**
     * The cached recipe analysis result.
     */
    @Nullable
    private CrafterRecipeInfo recipeInfo;

    /**
     * Internal inventory for items stored between crafts (9 slots).
     * Mirrors the crafting grid 1:1: slot 0-8 corresponds to crafting slot 0-8.
     * 
     * Each slot stores:
     * - REUSABLE ingredients: the catalyst item that never gets consumed
     * - DUPLICATION ingredients: the catalyst item that appears in output
     * - DURABILITY ingredients: the leftover damaged tool from previous craft
     * - CONSUMED/TRANSFORMED: unused (empty)
     */
    private final ItemStack[] catalystInventory;

    /**
     * Whether this entry is enabled.
     */
    private boolean enabled;

    /**
     * Current state of this entry.
     */
    private CrafterState state;

    /**
     * Pending outputs that couldn't be inserted into the network.
     * A crafting operation can produce up to 10 outputs:
     * - 1 main crafted item
     * - Up to 9 transformed/remaining items from inputs (e.g., empty buckets)
     */
    private final List<IAEItemStack> pendingOutputs;

    /**
     * Target quantity on network at which crafting stops (Integer.MAX_VALUE = unlimited).
     */
    private long targetQuantity;

    // ==================== ERROR DETAILS ====================

    /**
     * Detailed error information for verbose tooltips.
     * Lists which inputs were missing and how many more were needed.
     * <p>
     * Stored as {@link ITextComponent} (typically {@link net.minecraft.util.text.TextComponentTranslation})
     * so the message is translated using the client's locale at render time. The server
     * never has a meaningful localization context, so storing pre-translated strings here
     * would always show the English fallback to non-English clients.
     */
    private final List<ITextComponent> errorDetails;

    /**
     * Current batch size achieved vs. requested (for occupancy calculation).
     * actualBatchSize / requestedBatchSize = occupancy%
     */
    private int lastRequestedBatchSize;
    private int lastActualBatchSize;

    /**
     * Last tick when this entry ran a craft.
     */
    private long lastCraftTick;

    // ==================== METRICS (reset on world load) ====================

    /**
     * Total operations this entry has been active (enabled and has pattern).
     * Used to calculate error rate%.
     */
    private long metricsTotal;

    /**
     * Operations where this entry had an error state (MISSING_INPUT, MISSING_CATALYST, SIMULATION_FAILED).
     * Used to calculate error rate%.
     */
    private long metricsError;

    /**
     * Cumulative actual crafts performed.
     * Used to calculate occupancy% = metricsTotalActualCrafted / metricsTotalMaxPossible.
     */
    private long metricsTotalActualCrafted;

    /**
     * Cumulative max possible crafts (batch size requested each successful operation).
     * Used to calculate occupancy% = metricsTotalActualCrafted / metricsTotalMaxPossible.
     */
    private long metricsTotalMaxPossible;

    // ==================== CLIENT-SIDE SYNCED DATA ====================
    // These fields are synced from server via detectAndSendChanges + listener,
    // and used for client-side GUI display when recipeInfo is not available.

    /**
     * Synced output item for client-side display (overview and recipe view).
     * Set by readFromStream on client, computed from recipeInfo on server.
     */
    @Nullable
    private IAEItemStack syncedOutputItem;

    /**
     * Synced input grid for client-side display (recipe preview).
     * Set by readFromStream on client, computed from recipeInfo on server.
     */
    @Nullable
    private IAEItemStack[] syncedInputGrid;

    public CrafterEntry() {
        this.patternStack = null;
        this.recipeInfo = null;
        this.catalystInventory = new ItemStack[CATALYST_SLOTS];
        for (int i = 0; i < CATALYST_SLOTS; i++) this.catalystInventory[i] = ItemStack.EMPTY;

        this.enabled = true;
        this.state = CrafterState.NO_PATTERN;
        this.pendingOutputs = new ArrayList<>();
        this.targetQuantity = Long.MAX_VALUE;
        this.errorDetails = new ArrayList<>();
        this.lastRequestedBatchSize = 0;
        this.lastActualBatchSize = 0;
        this.lastCraftTick = 0;
        this.metricsTotal = 0;
        this.metricsError = 0;
    }

    /**
     * Checks if this entry has a valid pattern set.
     * On server: checks patternStack directly.
     * On client: this may return false even if entry has a pattern (use hasDisplayData() for GUI).
     */
    public boolean hasPattern() {
        return patternStack != null && !patternStack.isEmpty();
    }

    /**
     * Checks if this entry is empty (no pattern set).
     * On client: this may return true even if entry has a pattern (use hasDisplayData() for GUI).
     */
    public boolean isEmpty() {
        return !hasPattern();
    }

    /**
     * Checks if this entry has valid cached recipe info.
     * On server: checks recipeInfo directly.
     * On client: this returns false (use hasDisplayData() for GUI).
     */
    public boolean hasValidRecipeInfo() {
        return recipeInfo != null && recipeInfo.isValid();
    }

    /**
     * Checks if this entry has display data available for GUI rendering.
     * Works on both client and server:
     * - Server: returns true if recipeInfo is valid
     * - Client: returns true if synced data is available
     */
    public boolean hasDisplayData() {
        // Server-side: check recipeInfo
        if (recipeInfo != null && recipeInfo.isValid()) return true;

        // Client-side: check synced data
        return syncedOutputItem != null;
    }

    // --- Pattern ---

    @Nullable
    public ItemStack getPatternStack() {
        return patternStack;
    }

    public void setPatternStack(@Nullable ItemStack patternStack) {
        this.patternStack = patternStack;
        if (patternStack == null || patternStack.isEmpty()) {
            this.recipeInfo = null;
            this.state = CrafterState.NO_PATTERN;
        }
    }

    // --- Recipe Info ---

    @Nullable
    public CrafterRecipeInfo getRecipeInfo() {
        return recipeInfo;
    }

    public void setRecipeInfo(@Nullable CrafterRecipeInfo recipeInfo) {
        this.recipeInfo = recipeInfo;

        if (recipeInfo == null || !recipeInfo.isValid()) this.state = CrafterState.SIMULATION_FAILED;
    }

    // --- Catalyst Inventory ---

    public ItemStack getCatalystStack(int slot) {
        if (slot < 0 || slot >= CATALYST_SLOTS) return ItemStack.EMPTY;

        return catalystInventory[slot];
    }

    public void setCatalystStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CATALYST_SLOTS) return;

        catalystInventory[slot] = stack == null ? ItemStack.EMPTY : stack;
    }

    public ItemStack[] getCatalystInventory() {
        return catalystInventory;
    }

    // --- State ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            this.state = CrafterState.DISABLED;
        } else if (this.state == CrafterState.DISABLED) {
            // Re-enabling an entry should reset to IDLE so it gets processed
            // (unless it has no pattern, in which case validateAllEntries will set NO_PATTERN)
            if (hasPattern()) {
                this.state = CrafterState.IDLE;
            } else {
                this.state = CrafterState.NO_PATTERN;
            }
        }
    }

    public CrafterState getState() {
        return state;
    }

    public void setState(CrafterState state) {
        this.state = state;
    }

    // --- Pending Outputs ---

    /**
     * Gets the list of pending outputs that couldn't be inserted into the network.
     * Returns a mutable list that can be modified directly.
     */
    public List<IAEItemStack> getPendingOutputs() {
        return pendingOutputs;
    }

    /**
     * Adds an output to the pending list.
     */
    public void addPendingOutput(IAEItemStack output) {
        if (output == null || output.getStackSize() <= 0) return;

        // Try to merge with existing pending output of the same type
        for (IAEItemStack pending : pendingOutputs) {
            if (pending.isSameType(output)) {
                pending.setStackSize(pending.getStackSize() + output.getStackSize());
                return;
            }
        }

        pendingOutputs.add(output.copy());
    }

    /**
     * Clears all pending outputs.
     */
    public void clearPendingOutputs() {
        pendingOutputs.clear();
    }

    public boolean hasPendingOutputs() {
        return !pendingOutputs.isEmpty();
    }

    // --- Target Quantity ---

    public long getTargetQuantity() {
        return targetQuantity;
    }

    public void setTargetQuantity(long targetQuantity) {
        this.targetQuantity = Math.max(0, targetQuantity);
    }

    // --- Error Details ---

    /**
     * Gets the list of error detail components.
     * These provide verbose information about why crafting failed or was limited.
     * Components are resolved client-side via {@link ITextComponent#getFormattedText()}.
     */
    public List<ITextComponent> getErrorDetails() {
        return errorDetails;
    }

    /**
     * Clears error details.
     */
    public void clearErrorDetails() {
        errorDetails.clear();
    }

    /**
     * Adds an error detail component.
     * @param detail The error detail (typically a {@link net.minecraft.util.text.TextComponentTranslation})
     */
    public void addErrorDetail(ITextComponent detail) {
        if (detail != null) errorDetails.add(detail);
    }

    /**
     * Sets the batch size tracking for occupancy calculation.
     * @param requested The batch size requested (from config)
     * @param actual The actual batch size achieved (may be lower due to resource constraints)
     */
    public void setBatchSizeTracking(int requested, int actual) {
        this.lastRequestedBatchSize = requested;
        this.lastActualBatchSize = actual;
    }

    public int getLastRequestedBatchSize() {
        return lastRequestedBatchSize;
    }

    public int getLastActualBatchSize() {
        return lastActualBatchSize;
    }

    // --- Last Craft Tick ---

    public long getLastCraftTick() {
        return lastCraftTick;
    }

    public void setLastCraftTick(long lastCraftTick) {
        this.lastCraftTick = lastCraftTick;
    }

    // --- Metrics (reset on world load, not persisted) ---

    /**
     * Record an entry for metrics tracking.
     * Should be called after each operation when the entry is active (enabled and has pattern).
     * 
     * @param wasError Whether the entry had an error state this operation
     * @param requestedCrafts Max batch size that was requested (0 if error/not crafting)
     * @param actualCrafts Actual batch size achieved (0 if error/not crafting)
     */
    public void recordMetrics(boolean wasError, int requestedCrafts, int actualCrafts) {
        metricsTotal++;
        if (wasError) metricsError++;

        // Only count successful operations for occupancy
        if (!wasError && requestedCrafts > 0) {
            metricsTotalMaxPossible += requestedCrafts;
            metricsTotalActualCrafted += actualCrafts;
        }
    }

    /**
     * Gets the error rate as a percentage (0.0 - 100.0).
     * Error rate is the percentage of operations where the entry had an error state.
     */
    public double getErrorRate() {
        if (metricsTotal == 0) return 0.0;
        return (metricsError * 100.0) / metricsTotal;
    }

    /**
     * Gets the occupancy rate as a percentage (0.0 - 100.0).
     * Occupancy is the ratio of actual crafts performed vs. max possible crafts over time.
     * This measures efficiency - 100% means we always craft at full batch size,
     * lower values indicate resource constraints limiting batch size.
     */
    public double getOccupancy() {
        if (metricsTotalMaxPossible == 0) return 0.0;
        return (metricsTotalActualCrafted * 100.0) / metricsTotalMaxPossible;
    }

    /**
     * Resets all metrics counters. Called on world load.
     */
    public void resetMetrics() {
        metricsTotal = 0;
        metricsError = 0;
        metricsTotalActualCrafted = 0;
        metricsTotalMaxPossible = 0;
    }

    public long getMetricsTotal() {
        return metricsTotal;
    }

    public long getMetricsError() {
        return metricsError;
    }

    public long getMetricsTotalActualCrafted() {
        return metricsTotalActualCrafted;
    }

    public long getMetricsTotalMaxPossible() {
        return metricsTotalMaxPossible;
    }

    /**
     * Sets synced metrics data from server (called by TileAutoCrafter.readFromStream on client).
     * This allows the client to display metrics in the GUI without full server-side data.
     */
    public void setSyncedMetrics(long total, long error, long actualCrafted, long maxPossible) {
        this.metricsTotal = total;
        this.metricsError = error;
        this.metricsTotalActualCrafted = actualCrafted;
        this.metricsTotalMaxPossible = maxPossible;
    }

    // --- NBT Serialization ---

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        if (patternStack != null && !patternStack.isEmpty()) {
            tag.setTag("pattern", patternStack.writeToNBT(new NBTTagCompound()));
        }

        if (recipeInfo != null) {
            tag.setTag("recipeInfo", recipeInfo.writeToNBT());
        }

        NBTTagList catalystList = new NBTTagList();
        for (int i = 0; i < CATALYST_SLOTS; i++) {
            NBTTagCompound slotTag = new NBTTagCompound();
            if (!catalystInventory[i].isEmpty()) catalystInventory[i].writeToNBT(slotTag);
            catalystList.appendTag(slotTag);
        }
        tag.setTag("catalysts", catalystList);

        tag.setBoolean("enabled", enabled);
        tag.setInteger("state", state.ordinal());
        tag.setLong("targetQty", targetQuantity);
        tag.setLong("lastCraft", lastCraftTick);

        // Save pending outputs as a list
        if (!pendingOutputs.isEmpty()) {
            NBTTagList pendingList = new NBTTagList();
            for (IAEItemStack pending : pendingOutputs) {
                NBTTagCompound pendingTag = new NBTTagCompound();
                pendingTag.setTag("item", pending.createItemStack().writeToNBT(new NBTTagCompound()));
                pendingTag.setLong("count", pending.getStackSize());
                pendingList.appendTag(pendingTag);
            }
            tag.setTag("pendingOutputs", pendingList);
        }

        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("pattern")) {
            patternStack = new ItemStack(tag.getCompoundTag("pattern"));
        } else {
            patternStack = null;
        }

        if (tag.hasKey("recipeInfo")) {
            recipeInfo = CrafterRecipeInfo.readFromNBT(tag.getCompoundTag("recipeInfo"));
        } else {
            recipeInfo = null;
        }

        if (tag.hasKey("catalysts")) {
            NBTTagList catalystList = tag.getTagList("catalysts", 10);
            for (int i = 0; i < CATALYST_SLOTS && i < catalystList.tagCount(); i++) {
                NBTTagCompound slotTag = catalystList.getCompoundTagAt(i);
                if (slotTag.isEmpty()) {
                    catalystInventory[i] = ItemStack.EMPTY;
                } else {
                    catalystInventory[i] = new ItemStack(slotTag);
                }
            }
        }

        enabled = tag.getBoolean("enabled");

        int stateOrdinal = tag.getInteger("state");
        if (stateOrdinal >= 0 && stateOrdinal < CrafterState.values().length) {
            state = CrafterState.values()[stateOrdinal];
        } else {
            state = CrafterState.DISABLED;
        }

        targetQuantity = tag.getLong("targetQty");
        if (targetQuantity == 0) targetQuantity = Long.MAX_VALUE;

        lastCraftTick = tag.getLong("lastCraft");

        // Load pending outputs
        pendingOutputs.clear();
        if (tag.hasKey("pendingOutputs")) {
            NBTTagList pendingList = tag.getTagList("pendingOutputs", 10);
            for (int i = 0; i < pendingList.tagCount(); i++) {
                NBTTagCompound pendingTag = pendingList.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(pendingTag.getCompoundTag("item"));
                if (!stack.isEmpty()) {
                    IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                    if (aeStack != null) {
                        aeStack.setStackSize(pendingTag.getLong("count"));
                        pendingOutputs.add(aeStack);
                    }
                }
            }
        }
    }

    // ==================== CLIENT-SIDE SYNCED DATA ACCESSORS ====================

    /**
     * Sets the synced output item (called by TileAutoCrafter.readFromStream on client).
     */
    public void setSyncedOutputItem(@Nullable IAEItemStack output) {
        this.syncedOutputItem = output;
    }

    /**
     * Gets the synced output item (for client display).
     */
    @Nullable
    public IAEItemStack getSyncedOutputItem() {
        return syncedOutputItem;
    }

    /**
     * Sets the synced input grid (called by TileAutoCrafter.readFromStream on client).
     */
    public void setSyncedInputGrid(@Nullable IAEItemStack[] grid) {
        this.syncedInputGrid = grid;
    }

    /**
     * Gets the synced input grid (for client display).
     */
    @Nullable
    public IAEItemStack[] getSyncedInputGrid() {
        return syncedInputGrid;
    }

    // ==================== DISPLAY HELPERS ====================

    /**
     * Gets the primary output item for display purposes.
     * On server: returns from recipeInfo.
     * On client: returns synced data since recipeInfo is not available.
     */
    @Nullable
    public IAEItemStack getOutputItem() {
        // Try server-side recipeInfo first
        if (recipeInfo != null && recipeInfo.isValid() && !recipeInfo.getOutputs().isEmpty()) {
            return recipeInfo.getOutputs().get(0);
        }

        // Fall back to synced data (client-side)
        return syncedOutputItem;
    }

    /**
     * Gets the 3x3 input grid for display purposes.
     * On server: computes from recipeInfo.
     * On client: returns synced data since recipeInfo is not available.
     */
    @Nullable
    public IAEItemStack[] getInputGrid() {
        // Try server-side recipeInfo first
        if (recipeInfo != null && recipeInfo.isValid()) {
            IAEItemStack[] grid = new IAEItemStack[9];
            for (CrafterRecipeInfo.IngredientInfo info : recipeInfo.getIngredients()) {
                if (info.getSlotIndex() >= 0 && info.getSlotIndex() < 9) {
                    grid[info.getSlotIndex()] = info.getItem();
                }
            }

            return grid;
        }

        // Fall back to synced data (client-side)
        return syncedInputGrid;
    }
}
