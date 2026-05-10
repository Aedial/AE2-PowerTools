package com.ae2powertools.features.monitor;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.features.monitor.dependent.ComparisonMode;


/**
 * A single monitoring entry: a resource to watch, a comparison operator, and a threshold.
 * Each entry independently evaluates to a boolean (quantity COMP threshold),
 * then AND/OR across all entries determines the overall condition.
 *
 * Disabled entries are still polled (so the GUI can show their current quantity),
 * but they do NOT contribute to the AND/OR evaluation.
 *
 * Also stores the last looked-up quantity (transient, not persisted) for display purposes.
 */
public class MonitoredEntry {

    /**
     * The resource being monitored. May be null for empty cells: the user can still configure
     * a comparator and threshold on a resource-less slot, and only set the resource later via
     * the content selector. Slots with a null resource are NOT counted in the AND/OR evaluation
     * (see {@code MonitorLogic#evaluateCondition}), but the rest of their state
     * persists across mutations.
     */
    @Nullable
    private final MonitoredResource resource;
    private ComparisonMode comparison;
    private long threshold;

    /** Whether this entry counts toward the overall AND/OR condition. */
    private boolean enabled;

    /** Last looked-up quantity from the network. Transient, not persisted. */
    private transient long lastQuantity;

    /** Last evaluation result (transient, used for GUI feedback). Even disabled entries get evaluated for display. */
    private transient boolean lastConditionMet;

    public MonitoredEntry(MonitoredResource resource, ComparisonMode comparison, long threshold) {
        this(resource, comparison, threshold, true);
    }

    public MonitoredEntry(@Nullable MonitoredResource resource, ComparisonMode comparison, long threshold, boolean enabled) {
        this.resource = resource;
        this.comparison = comparison;
        this.threshold = threshold;
        this.enabled = enabled;
    }

    /**
     * Creates an empty placeholder entry: no resource yet, default comparator (>=), threshold 0,
     * enabled. Empty entries are still rendered with their comparator and threshold so the user
     * can pre-configure a slot before selecting a resource for it.
     */
    public static MonitoredEntry empty() {
        return new MonitoredEntry(null, ComparisonMode.GREATER_EQUAL, 0, true);
    }

    /**
     * Creates an entry with default comparison (>=) and threshold (0).
     * Used when the user picks a resource from the selector before configuring thresholds.
     */
    public static MonitoredEntry withDefaults(MonitoredResource resource) {
        return new MonitoredEntry(resource, ComparisonMode.GREATER_EQUAL, 0, true);
    }

    // --- Evaluation ---

    /**
     * Evaluates whether this entry's condition is met, given the current network quantity.
     * Always stores the quantity and the evaluation result, regardless of enabled state,
     * so the GUI can show live feedback for every entry.
     */
    public boolean evaluate(long networkQuantity) {
        this.lastQuantity = networkQuantity;
        this.lastConditionMet = comparison.test(networkQuantity, threshold);

        return this.lastConditionMet;
    }

    // --- Accessors ---

    /**
     * Returns the underlying resource, or null for an empty/placeholder entry.
     */
    @Nullable
    public MonitoredResource getResource() {
        return resource;
    }

    /** Convenience: true when this entry has a resource assigned. */
    public boolean hasResource() {
        return resource != null;
    }

    public ComparisonMode getComparison() {
        return comparison;
    }

    public void setComparison(ComparisonMode comparison) {
        this.comparison = comparison;
    }

    public long getThreshold() {
        return threshold;
    }

    public void setThreshold(long threshold) {
        // Negative thresholds clamp to 0 since AE2 quantities can't go negative.
        this.threshold = Math.max(0, threshold);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLastQuantity() {
        return lastQuantity;
    }

    /**
     * Sets the last-looked-up quantity. Used on the client side when receiving sync
     * packets from the server, so the GUI can render fresh feedback.
     */
    public void setLastQuantity(long lastQuantity) {
        this.lastQuantity = lastQuantity;
    }

    public boolean isLastConditionMet() {
        return lastConditionMet;
    }

    public void setLastConditionMet(boolean lastConditionMet) {
        this.lastConditionMet = lastConditionMet;
    }

    // --- NBT serialization ---

    private static final String NBT_RESOURCE = "Resource";
    private static final String NBT_COMPARISON = "Comparison";
    private static final String NBT_THRESHOLD = "Threshold";
    private static final String NBT_ENABLED = "Enabled";

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        // Empty entries omit the resource subtag entirely; readers detect this and reconstruct
        // a placeholder via {@link #empty()}-style parameters.
        if (resource != null) tag.setTag(NBT_RESOURCE, resource.writeToNBT());
        tag.setInteger(NBT_COMPARISON, comparison.getId());
        tag.setLong(NBT_THRESHOLD, threshold);
        tag.setBoolean(NBT_ENABLED, enabled);

        return tag;
    }

    @Nullable
    public static MonitoredEntry readFromNBT(NBTTagCompound tag) {
        // A missing resource tag means this is a placeholder entry: still has comparator/threshold/enabled.
        MonitoredResource resource = null;
        if (tag.hasKey(NBT_RESOURCE)) {
            resource = MonitoredResource.readFromNBT(tag.getCompoundTag(NBT_RESOURCE));
        }

        ComparisonMode comparison = ComparisonMode.fromId(tag.getInteger(NBT_COMPARISON));
        long threshold = tag.getLong(NBT_THRESHOLD);
        boolean enabled = tag.getBoolean(NBT_ENABLED);

        return new MonitoredEntry(resource, comparison, threshold, enabled);
    }

    // --- ByteBuf serialization (for network packets) ---

    public void writeToBuf(ByteBuf buf) {
        buf.writeBoolean(resource != null);
        if (resource != null) resource.writeToBuf(buf);
        buf.writeInt(comparison.getId());
        buf.writeLong(threshold);
        buf.writeBoolean(enabled);
    }

    public static MonitoredEntry readFromBuf(ByteBuf buf) {
        MonitoredResource resource = buf.readBoolean() ? MonitoredResource.readFromBuf(buf) : null;
        ComparisonMode comparison = ComparisonMode.fromId(buf.readInt());
        long threshold = buf.readLong();
        boolean enabled = buf.readBoolean();

        return new MonitoredEntry(resource, comparison, threshold, enabled);
    }
}
