package com.ae2powertools.features.monitor.dependent;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.monitor.MonitoredResource;


/**
 * Logic for the ME Storage Display host.
 * Composes {@link MonitorLogic} and tracks the corner color indicator.
 * <p>
 * The corner color reflects the monitor's current display state:
 * - No enabled resource selected -> neutral idle color
 * - Condition met -> "above" color (green by default)
 * - Condition not met -> "below" color (yellow by default)
 * <p>
 * The condition colors are user-configurable via {@link PowerToolsClientConfig.Monitor}.
 * The neutral idle color is fixed.
 * <p>
 * This class also carries the small render-state cache used by client renderers (TESR /
 * part dynamic). The monitor's full {@code entries} list is only synced to clients while
 * the GUI is open (via {@code PacketSyncMonitorEntries}); without the cache below, a freshly
 * loaded chunk would have nothing to render until the user opened the GUI once. The cache
 * is fed by {@code writeToStream}/{@code readFromStream} on the host (block tile / cable
 * part) and tracked via {@link #pollSyncDirty()} so the host only triggers a network
 * update when something actually changed.
 */
public class DisplayLogic {

    /** Neutral light gray used when the display has nothing active to evaluate. */
    private static final int IDLE_CORNER_COLOR = 0xFFD0D0D0;

    private final MonitorLogic monitorLogic;

    /**
     * The active corner color, determined by whether the condition is met.
     * Updated by {@link #evaluate()} on the server, replaced by {@link #setCornerColor(int)}
     * on the client when a sync packet arrives.
     */
    private int cornerColor;

    // --- Client-side render cache ---
    // These mirror what the server most recently sent through writeToStream. They are only
    // meaningful on the client; on the server they are written through but never read back.
    @Nullable
    private MonitoredResource clientResource;

    private long clientQuantity;

    // --- Server-side sync-state tracking ---
    // The last (resource, quantity, color) tuple that was pushed to clients via writeToStream.
    // Used by pollSyncDirty() so the host only triggers a markForUpdate when something has
    // actually changed since the last push, instead of every refresh tick.
    @Nullable
    private MonitoredResource lastSyncedResource;
    private long lastSyncedQuantity;
    private int lastSyncedColor;
    /** False until the first sync push, so the very first call to pollSyncDirty() returns true. */
    private boolean syncedAtLeastOnce;

    public DisplayLogic(MonitorLogic monitorLogic) {
        this.monitorLogic = monitorLogic;
        this.cornerColor = IDLE_CORNER_COLOR;
    }

    public static int getIdleCornerColor() {
        return IDLE_CORNER_COLOR;
    }

    /**
     * Evaluate the display color based on condition state.
     * Colors are taken from client config, allowing user customization.
     *
     * @return true if the corner color changed
     */
    public boolean evaluate() {
        int newColor;

        if (!monitorLogic.hasEnabledEntries()) {
            newColor = IDLE_CORNER_COLOR;
        } else if (monitorLogic.isConditionMet()) {
            newColor = PowerToolsClientConfig.monitor.getColorAbove();
        } else {
            newColor = PowerToolsClientConfig.monitor.getColorBelow();
        }

        if (newColor == cornerColor) return false;

        cornerColor = newColor;
        return true;
    }

    // --- Getters / setters ---

    public int getCornerColor() {
        return cornerColor;
    }

    /**
     * Client-only: replace the corner color with a value received from the server.
     * Server-side this should never be called; use {@link #evaluate()} instead.
     */
    public void setCornerColor(int color) {
        this.cornerColor = color;
    }

    /**
     * Returns the resource the client should render in the display, or null when no entry
     * has been configured yet. Reads from the network-synced cache, NOT from the entries
     * list, so it works even when the GUI has never been opened.
     */
    @Nullable
    public MonitoredResource getClientResource() {
        return clientResource;
    }

    /**
     * Returns the quantity the client should render under the icon. Mirrors
     * {@link #getClientResource()} - synced through {@code writeToStream}, not entries.
     */
    public long getClientQuantity() {
        return clientQuantity;
    }

    // --- Server-side sync orchestration ---

    /**
     * Called server-side each tick after {@code refresh()}/{@code evaluate()}. Returns true
     * if any of (first resource, first quantity, corner color) has changed since the last
     * call - in which case the host should call {@code markForUpdate()} to re-trigger
     * {@code writeToStream}. Returning false avoids spamming chunk updates when nothing
     * has changed (e.g. the network quantity is stable).
     */
    public boolean pollSyncDirty() {
        MonitoredResource current = monitorLogic.getFirstResource();
        long currentQty = monitorLogic.getFirstEntryQuantity();
        int currentColor = cornerColor;

        boolean changed = !syncedAtLeastOnce
            || !sameResource(current, lastSyncedResource)
            || currentQty != lastSyncedQuantity
            || currentColor != lastSyncedColor;

        if (!changed) return false;

        lastSyncedResource = current;
        lastSyncedQuantity = currentQty;
        lastSyncedColor = currentColor;
        syncedAtLeastOnce = true;
        return true;
    }

    private static boolean sameResource(@Nullable MonitoredResource a, @Nullable MonitoredResource b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toKey().equals(b.toKey());
    }

    // --- writeToStream / readFromStream helpers ---
    // Centralised so the block tile and the cable part stay in lockstep on the wire format.

    /**
     * Writes (firstResource, firstQuantity, cornerColor) to the host's update stream.
     * Server-side only.
     */
    public void writeToStream(ByteBuf data) {
        MonitoredResource res = monitorLogic.getFirstResource();
        data.writeBoolean(res != null);
        if (res != null) res.writeToBuf(data);
        data.writeLong(monitorLogic.getFirstEntryQuantity());
        data.writeInt(cornerColor);
    }

    /**
     * Reads what {@link #writeToStream} wrote, populating the client-side render cache.
     * Client-side only - on the server the values are silently overwritten on the next
     * sync push.
     */
    public void readFromStream(ByteBuf data) {
        clientResource = data.readBoolean() ? MonitoredResource.readFromBuf(data) : null;
        clientQuantity = data.readLong();
        cornerColor = data.readInt();
    }

    // --- NBT (delegates to the shared monitor logic; cornerColor is transient) ---

    public void writeToNBT(NBTTagCompound tag) {
        monitorLogic.writeToNBT(tag);
    }

    public void readFromNBT(NBTTagCompound tag) {
        monitorLogic.readFromNBT(tag);
    }
}
