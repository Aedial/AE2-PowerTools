package com.ae2powertools.features.crafter;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Immutable per-entry overview data sent from server to client.
 * Used for overview mode rendering and for the per-entry state indicator.
 * <p>
 * Hosts the smallest data set required to render the overview row for an entry:
 * - state (drives row color and text)
 * - enabled (whether the entry is processing)
 * - hasDisplayData (whether output/recipe info is available)
 * - output item (icon and name)
 * - metrics (occupancy/error rate calculations)
 * <p>
 * Serialization is plain ByteBuf reads/writes (no NBT compression). Packets are
 * diff-based and only sent when an actual change is detected on the server, so
 * the per-tick payload remains tiny.
 */
public final class CrafterOverviewSnapshot {

    private final int stateOrdinal;
    private final boolean enabled;
    private final boolean hasDisplayData;
    @Nullable
    private final IAEItemStack output;
    private final long metricsTotal;
    private final long metricsError;
    private final long metricsTotalActualCrafted;
    private final long metricsTotalMaxPossible;

    public CrafterOverviewSnapshot(int stateOrdinal, boolean enabled, boolean hasDisplayData,
                                   @Nullable IAEItemStack output,
                                   long metricsTotal, long metricsError,
                                   long metricsTotalActualCrafted, long metricsTotalMaxPossible) {
        this.stateOrdinal = stateOrdinal;
        this.enabled = enabled;
        this.hasDisplayData = hasDisplayData;
        this.output = output;
        this.metricsTotal = metricsTotal;
        this.metricsError = metricsError;
        this.metricsTotalActualCrafted = metricsTotalActualCrafted;
        this.metricsTotalMaxPossible = metricsTotalMaxPossible;
    }

    /**
     * Captures the current overview state of an entry on the server.
     */
    public static CrafterOverviewSnapshot fromEntry(CrafterEntry entry) {
        boolean hasDisplay = entry.hasPattern() && entry.hasValidRecipeInfo();
        IAEItemStack out = hasDisplay ? entry.getOutputItem() : null;
        // Defensive copy so caching doesn't end up holding references that mutate later
        IAEItemStack outCopy = out != null ? out.copy() : null;
        return new CrafterOverviewSnapshot(
            entry.getState().ordinal(),
            entry.isEnabled(),
            hasDisplay,
            outCopy,
            entry.getMetricsTotal(),
            entry.getMetricsError(),
            entry.getMetricsTotalActualCrafted(),
            entry.getMetricsTotalMaxPossible()
        );
    }

    public void writeToBuf(ByteBuf buf) throws IOException {
        buf.writeByte(stateOrdinal);
        buf.writeBoolean(enabled);
        buf.writeBoolean(hasDisplayData);
        buf.writeBoolean(output != null);
        if (output != null) output.writeToPacket(buf);
        buf.writeLong(metricsTotal);
        buf.writeLong(metricsError);
        buf.writeLong(metricsTotalActualCrafted);
        buf.writeLong(metricsTotalMaxPossible);
    }

    public static CrafterOverviewSnapshot readFromBuf(ByteBuf buf) throws IOException {
        int stateOrdinal = buf.readByte() & 0xFF;
        boolean enabled = buf.readBoolean();
        boolean hasDisplayData = buf.readBoolean();
        IAEItemStack output = buf.readBoolean() ? AEItemStack.fromPacket(buf) : null;
        long metricsTotal = buf.readLong();
        long metricsError = buf.readLong();
        long metricsTotalActualCrafted = buf.readLong();
        long metricsTotalMaxPossible = buf.readLong();
        return new CrafterOverviewSnapshot(stateOrdinal, enabled, hasDisplayData, output,
            metricsTotal, metricsError, metricsTotalActualCrafted, metricsTotalMaxPossible);
    }

    public int getStateOrdinal() { return stateOrdinal; }
    public boolean isEnabled() { return enabled; }
    public boolean hasDisplayData() { return hasDisplayData; }
    @Nullable public IAEItemStack getOutput() { return output; }
    public long getMetricsTotal() { return metricsTotal; }
    public long getMetricsError() { return metricsError; }
    public long getMetricsTotalActualCrafted() { return metricsTotalActualCrafted; }
    public long getMetricsTotalMaxPossible() { return metricsTotalMaxPossible; }

    /**
     * Equality is used for diff detection: a snapshot is sent only when the new value
     * differs from the cached one. Item identity comparison uses isSameType + stack size
     * to ignore irrelevant differences.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrafterOverviewSnapshot)) return false;

        CrafterOverviewSnapshot that = (CrafterOverviewSnapshot) o;
        if (stateOrdinal != that.stateOrdinal) return false;
        if (enabled != that.enabled) return false;
        if (hasDisplayData != that.hasDisplayData) return false;
        if (metricsTotal != that.metricsTotal) return false;
        if (metricsError != that.metricsError) return false;
        if (metricsTotalActualCrafted != that.metricsTotalActualCrafted) return false;
        if (metricsTotalMaxPossible != that.metricsTotalMaxPossible) return false;

        return aeItemEquals(this.output, that.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stateOrdinal, enabled, hasDisplayData,
            output == null ? 0 : output.getItem(),
            output == null ? 0L : output.getStackSize(),
            metricsTotal, metricsError, metricsTotalActualCrafted, metricsTotalMaxPossible);
    }

    private static boolean aeItemEquals(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (!a.isSameType(b)) return false;

        return a.getStackSize() == b.getStackSize();
    }
}
