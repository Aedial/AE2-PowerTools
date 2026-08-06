package com.ae2powertools.features.maintainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.util.text.ITextComponent;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Immutable per-entry snapshot sent from server to client via
 * {@link com.ae2powertools.network.PacketMaintainerEntrySync}.
 * <p>
 * The container diffs snapshots in {@code detectAndSendChanges} and
 * only sends packets to relevant listeners when something actually changed.
 * <p>
 * The error message is pre-serialized to JSON so {@link #equals(Object)}
 * is a cheap string comparison and serialization happens at most once per
 * snapshot capture.
 */
public final class MaintainerEntrySnapshot {

    /**
     * Sentinel snapshot for an empty (no recipe) entry. Reused across all
     * empty slots to keep diff comparisons trivial.
     */
    public static final MaintainerEntrySnapshot EMPTY =
            new MaintainerEntrySnapshot(false, null, 0L, 0L, 0, false, 0, 0L, null);

    private final boolean hasRecipe;
    @Nullable
    private final IAEItemStack targetItem;
    private final long targetQuantity;
    private final long batchSize;
    private final int frequencySeconds;
    private final boolean enabled;
    private final int stateOrdinal;
    private final long currentQuantity;
    /** Pre-serialized JSON form of the error component, or null. */
    @Nullable
    private final String errorJson;

    private MaintainerEntrySnapshot(boolean hasRecipe, @Nullable IAEItemStack targetItem,
                                    long targetQuantity, long batchSize, int frequencySeconds,
                                    boolean enabled, int stateOrdinal, long currentQuantity,
                                    @Nullable String errorJson) {
        this.hasRecipe = hasRecipe;
        this.targetItem = targetItem;
        this.targetQuantity = targetQuantity;
        this.batchSize = batchSize;
        this.frequencySeconds = frequencySeconds;
        this.enabled = enabled;
        this.stateOrdinal = stateOrdinal;
        this.currentQuantity = currentQuantity;
        this.errorJson = errorJson;
    }

    /**
     * Captures the current sync-relevant state of an entry on the server.
     * Returns {@link #EMPTY} when the entry has no recipe.
     */
    public static MaintainerEntrySnapshot fromEntry(@Nullable MaintainerEntry entry) {
        if (entry == null || !entry.hasRecipe()) return EMPTY;

        ITextComponent err = entry.getErrorComponent();
        // Serialize the error component once at capture time. This keeps equals() a
        // simple string comparison and avoids repeating serialization every diff tick.
        String json = err != null ? ITextComponent.Serializer.componentToJson(err) : null;

        IAEItemStack target = entry.getTargetItem();
        if (target == null || target.getDefinition().isEmpty()) return EMPTY;

        // Defensive copy so the cached snapshot doesn't hold a reference that mutates.
        IAEItemStack targetCopy = target.copy();

        return new MaintainerEntrySnapshot(
                true,
                targetCopy,
                entry.getTargetQuantity(),
                entry.getBatchSize(),
                entry.getFrequencySeconds(),
                entry.isEnabled(),
                entry.getState().ordinal(),
                entry.getCurrentQuantity(),
                json);
    }

    public void writeToBuf(ByteBuf buf) throws IOException {
        buf.writeBoolean(hasRecipe);
        if (!hasRecipe || targetItem == null) return;

        targetItem.writeToPacket(buf);
        buf.writeLong(targetQuantity);
        buf.writeLong(batchSize);
        buf.writeInt(frequencySeconds);
        buf.writeBoolean(enabled);
        buf.writeInt(stateOrdinal);
        buf.writeLong(currentQuantity);

        // Sync error component for tooltip display. JSON-serialized so the client
        // re-creates the same ITextComponent and renders it in the player's language.
        boolean hasError = errorJson != null;
        buf.writeBoolean(hasError);
        if (hasError) {
            byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    public static MaintainerEntrySnapshot readFromBuf(ByteBuf buf) {
        boolean hasRecipe = buf.readBoolean();
        if (!hasRecipe) return EMPTY;

        IAEItemStack targetItem = AEItemStack.fromPacket(buf);
        long targetQty = buf.readLong();
        long batchSize = buf.readLong();
        int freqSecs = buf.readInt();
        boolean enabled = buf.readBoolean();
        int stateOrdinal = buf.readInt();
        long currentQty = buf.readLong();

        String errorJson = null;
        if (buf.readBoolean()) {
            int len = buf.readShort() & 0xFFFF;
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            errorJson = new String(bytes, StandardCharsets.UTF_8);
        }

        return new MaintainerEntrySnapshot(true, targetItem, targetQty, batchSize, freqSecs,
                enabled, stateOrdinal, currentQty, errorJson);
    }

    /**
     * Applies this snapshot to the given client-side entry. Caller is responsible
     * for handling the {@link #isEmpty()} case (typically by replacing with a
     * fresh {@link MaintainerEntry} to clear all transient state).
     */
    public void applyTo(MaintainerEntry entry) {
        if (!hasRecipe) return;

        entry.setTargetItem(targetItem);
        entry.setTargetQuantity(targetQuantity);
        entry.setBatchSize(batchSize);
        entry.setFrequencySeconds(frequencySeconds);
        entry.setEnabled(enabled);
        if (stateOrdinal >= 0 && stateOrdinal < MaintainerState.values().length) {
            entry.setState(MaintainerState.values()[stateOrdinal]);
        }
        entry.setCurrentQuantity(currentQuantity);
        entry.setErrorComponent(errorJson != null
                ? ITextComponent.Serializer.jsonToComponent(errorJson)
                : null);
    }

    public boolean isEmpty() {
        return !hasRecipe;
    }

    /**
     * Equality drives diff detection: a snapshot is sent only when the new value
     * differs from the cached one. Item identity uses isSameType + stack size to
     * ignore irrelevant differences (NBT / fluid metadata).
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MaintainerEntrySnapshot)) return false;

        MaintainerEntrySnapshot that = (MaintainerEntrySnapshot) o;
        if (hasRecipe != that.hasRecipe) return false;
        if (!hasRecipe) return true;

        if (targetQuantity != that.targetQuantity) return false;
        if (batchSize != that.batchSize) return false;
        if (frequencySeconds != that.frequencySeconds) return false;
        if (enabled != that.enabled) return false;
        if (stateOrdinal != that.stateOrdinal) return false;
        if (currentQuantity != that.currentQuantity) return false;
        if (!Objects.equals(errorJson, that.errorJson)) return false;

        return aeItemEquals(targetItem, that.targetItem);
    }

    @Override
    public int hashCode() {
        if (!hasRecipe) return 0;

        return Objects.hash(
                targetItem == null ? 0 : targetItem.getItem(),
                targetQuantity, batchSize, frequencySeconds,
                enabled, stateOrdinal, currentQuantity, errorJson);
    }

    private static boolean aeItemEquals(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (!a.isSameType(b)) return false;

        return a.getStackSize() == b.getStackSize();
    }
}
