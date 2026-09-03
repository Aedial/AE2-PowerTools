package com.ae2powertools.features.crafter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.util.text.ITextComponent;

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
 * - error details (extended overview tooltip diagnostics)
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
    private final List<ITextComponent> errorDetails;
    private final List<ITextComponent> hints;
    private final long metricsTotal;
    private final long metricsError;
    private final long metricsTotalActualCrafted;
    private final long metricsTotalMaxPossible;

    public CrafterOverviewSnapshot(int stateOrdinal, boolean enabled, boolean hasDisplayData,
                                   @Nullable IAEItemStack output,
                                   List<ITextComponent> errorDetails,
                                   List<ITextComponent> hints,
                                   long metricsTotal, long metricsError,
                                   long metricsTotalActualCrafted, long metricsTotalMaxPossible) {
        this.stateOrdinal = stateOrdinal;
        this.enabled = enabled;
        this.hasDisplayData = hasDisplayData;
        this.output = output;
        this.errorDetails = errorDetails;
        this.hints = hints;
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
            copyComponents(entry.getErrorDetails()),
            copyComponents(entry.getHints()),
            entry.getMetricsTotal(),
            entry.getMetricsError(),
            entry.getMetricsTotalActualCrafted(),
            entry.getMetricsTotalMaxPossible()
        );
    }

    private static List<ITextComponent> copyComponents(List<ITextComponent> source) {
        return source.isEmpty() ? Collections.emptyList() : new ArrayList<>(source);
    }

    public void writeToBuf(ByteBuf buf) throws IOException {
        buf.writeByte(stateOrdinal);
        buf.writeBoolean(enabled);
        buf.writeBoolean(hasDisplayData);
        buf.writeBoolean(output != null);
        if (output != null) output.writeToPacket(buf);
        buf.writeShort(errorDetails.size());
        for (ITextComponent comp : errorDetails) {
            writeString(buf, ITextComponent.Serializer.componentToJson(comp));
        }
        buf.writeShort(hints.size());
        for (ITextComponent comp : hints) {
            writeString(buf, ITextComponent.Serializer.componentToJson(comp));
        }
        buf.writeLong(metricsTotal);
        buf.writeLong(metricsError);
        buf.writeLong(metricsTotalActualCrafted);
        buf.writeLong(metricsTotalMaxPossible);
    }

    public static CrafterOverviewSnapshot readFromBuf(ByteBuf buf) {
        int stateOrdinal = buf.readByte() & 0xFF;
        boolean enabled = buf.readBoolean();
        boolean hasDisplayData = buf.readBoolean();
        IAEItemStack output = buf.readBoolean() ? AEItemStack.fromPacket(buf) : null;
        int errorCount = buf.readShort() & 0xFFFF;
        List<ITextComponent> errorDetails = new ArrayList<>(errorCount);
        for (int i = 0; i < errorCount; i++) {
            errorDetails.add(ITextComponent.Serializer.jsonToComponent(readString(buf)));
        }
        int hintCount = buf.readShort() & 0xFFFF;
        List<ITextComponent> hints = new ArrayList<>(hintCount);
        for (int i = 0; i < hintCount; i++) {
            hints.add(ITextComponent.Serializer.jsonToComponent(readString(buf)));
        }
        long metricsTotal = buf.readLong();
        long metricsError = buf.readLong();
        long metricsTotalActualCrafted = buf.readLong();
        long metricsTotalMaxPossible = buf.readLong();
        return new CrafterOverviewSnapshot(stateOrdinal, enabled, hasDisplayData, output,
            errorDetails, hints,
            metricsTotal, metricsError, metricsTotalActualCrafted, metricsTotalMaxPossible);
    }

    private static void writeString(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteBuf buf) {
        int len = buf.readShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public int getStateOrdinal() { return stateOrdinal; }
    public boolean isEnabled() { return enabled; }
    public boolean hasDisplayData() { return hasDisplayData; }
    @Nullable public IAEItemStack getOutput() { return output; }
    public List<ITextComponent> getErrorDetails() { return errorDetails; }
    public List<ITextComponent> getHints() { return hints; }
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
        if (!errorDetails.equals(that.errorDetails)) return false;
        if (!hints.equals(that.hints)) return false;
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
            errorDetails, hints,
            metricsTotal, metricsError, metricsTotalActualCrafted, metricsTotalMaxPossible);
    }

    private static boolean aeItemEquals(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (!a.isSameType(b)) return false;

        return a.getStackSize() == b.getStackSize();
    }
}
