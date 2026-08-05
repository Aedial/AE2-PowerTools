package com.ae2powertools.features.monitor.dependent;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.util.Platform;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.emitter.IEmitterCardHost;
import com.ae2powertools.features.monitor.emitter.EmitterRedstonePower;
import com.ae2powertools.features.monitor.emitter.IEmitterRedstoneHost;
import com.ae2powertools.network.PacketStorageEntryStateSync;
import com.ae2powertools.network.PacketSyncMonitorEntries;
import com.ae2powertools.network.PowerToolsNetwork;
import com.ae2powertools.util.ContainerListenerSync;
import com.ae2powertools.util.upgrade.UpgradeInventoryUtil;


/**
 * Container for both ME Storage Level Emitter and ME Storage Display.
 * Syncs host fields to the client via @GuiSync, and pushes per-entry
 * quantity / condition state to listeners via {@link PacketStorageEntryStateSync}
 * whenever any of those values change (so the GUI can render live feedback).
 *
 * Adapts automatically based on the host's {@link IStorageMonitorHost#getHostType()} to decide
 * which fields are relevant.
 */
public class ContainerStorageMonitor extends AEBaseContainer {

    private static final int EMITTER_CARD_PANEL_X = 223;
    private static final int FIRST_EMITTER_CARD_SLOT_X = EMITTER_CARD_PANEL_X + 19;
    private static final int EMITTER_CARD_SLOT_Y = 77;
    private static final int EMITTER_CARD_SLOT_COUNT = 2;
    private static final int HIDDEN_PLAYER_INVENTORY_X = -9999;
    private static final int HIDDEN_PLAYER_INVENTORY_Y = -9999;

    private final IStorageMonitorHost host;
    private final EntityPlayer viewer;
    private final List<AppEngSlot> emitterCardSlots;

    @GuiSync(0)
    public long refreshRate;

    @GuiSync(1)
    public int matchMode;

    @GuiSync(2)
    public int conditionMet;

    @GuiSync(3)
    public long firstEntryQuantity;

    @GuiSync(4)
    public int emitterRedstoneSignalStrength;

    @GuiSync(5)
    public int hysteresisEnabled;

    @GuiSync(6)
    public int playerRegistered;

    @GuiSync(7)
    public int emitterStrength;

    // --- Cached per-entry state for change detection (server-side only) ---
    /** Cached quantities for each entry, used to detect changes worth syncing to the client. */
    private long[] cachedQuantities = new long[0];
    /** Cached per-entry condition results for change detection. */
    private boolean[] cachedConditions = new boolean[0];

    /**
     * Last entries-version we broadcast to clients. When the host's version differs we
     * push the full entry list out via {@link PacketSyncMonitorEntries}. -1 forces an
     * initial sync on the first detectAndSendChanges call.
     */
    private int lastEntriesVersion = -1;

    public ContainerStorageMonitor(InventoryPlayer playerInv, IStorageMonitorHost host) {
        super(playerInv, host);
        this.host = host;
        this.viewer = playerInv.player;

        // Keep the player inventory synced while the GUI-owned upgrade picker mutates it.
        // The slots stay far off-screen because the picker renders the 4 inventory rows manually.
        bindPlayerInventory(playerInv, HIDDEN_PLAYER_INVENTORY_X, HIDDEN_PLAYER_INVENTORY_Y);

        emitterCardSlots = createEmitterCardSlots();
        for (AppEngSlot slot : emitterCardSlots) addSlotToContainer(slot);

        if (Platform.isServer()) syncFromHost();
    }

    private List<AppEngSlot> createEmitterCardSlots() {
        if (!(host instanceof IEmitterCardHost)) return Collections.emptyList();

        return UpgradeInventoryUtil.createPassiveUpgradeSlots(
            ((IEmitterCardHost) host).getUpgradeInventory(),
            FIRST_EMITTER_CARD_SLOT_X,
            EMITTER_CARD_SLOT_Y,
            EMITTER_CARD_SLOT_COUNT,
            UpgradeInventoryUtil.SlotLayout.HORIZONTAL);
    }

    public List<AppEngSlot> getEmitterCardSlots() {
        return emitterCardSlots;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (!Platform.isServer()) return;

        if (host.shouldRefreshWhileGuiOpen()) host.getMonitorLogic().refresh();

        // Poll disabled entries, since they were ignored for AND/OR evaluation
        host.getMonitorLogic().pollDisabledEntriesForDisplay();

        syncFromHost();
        detectAndSendEntriesChanges();
        detectAndSendEntryStateChanges();
    }

    /**
     * If the host's entry list has mutated since the last sync (add/remove/replace),
     * pushes the full list to all listeners so the client GUI mirrors the server state.
     * Cheap when nothing changed (single int compare).
     */
    private void detectAndSendEntriesChanges() {
        int currentVersion = host.getMonitorLogic().getEntriesVersion();
        if (currentVersion == lastEntriesVersion) return;

        lastEntriesVersion = currentVersion;

        PacketSyncMonitorEntries packet = new PacketSyncMonitorEntries(host.getEntries());
        ContainerListenerSync.sendToPlayerListeners(this.listeners, packet);
    }

    /**
     * Compares each entry's current quantity / condition against the cached snapshot.
     * If any value differs, refreshes the cache and pushes a fresh
     * {@link PacketStorageEntryStateSync} to all listening players.
     */
    private void detectAndSendEntryStateChanges() {
        List<MonitoredEntry> entries = host.getEntries();
        int n = entries.size();

        // Resize cache arrays when the entry list size changes.
        // A size change always counts as a change (force a sync).
        boolean changed = (cachedQuantities.length != n);
        if (changed) {
            cachedQuantities = new long[n];
            cachedConditions = new boolean[n];
        }

        long[] quantities = new long[n];
        boolean[] conditions = new boolean[n];

        for (int i = 0; i < n; i++) {
            MonitoredEntry e = entries.get(i);
            quantities[i] = e.getLastQuantity();
            conditions[i] = e.isLastConditionMet();

            if (!changed && (quantities[i] != cachedQuantities[i] || conditions[i] != cachedConditions[i])) {
                changed = true;
            }
        }

        if (!changed) return;

        // Refresh cache & broadcast to listeners.
        System.arraycopy(quantities, 0, cachedQuantities, 0, n);
        System.arraycopy(conditions, 0, cachedConditions, 0, n);

        PacketStorageEntryStateSync packet = new PacketStorageEntryStateSync(quantities, conditions);
        ContainerListenerSync.sendToPlayerListeners(this.listeners, packet);
    }

    /**
     * Adds a listener and immediately pushes the latest entry state to it,
     * so a freshly-opened GUI doesn't have to wait for the next change to render.
     */
    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);

        EntityPlayerMP mp = ContainerListenerSync.getPlayerListener(listener);
        if (!Platform.isServer() || mp == null) return;

        // Push the full entry list immediately so the freshly-opened GUI doesn't
        // start blank waiting for the next mutation.
        PowerToolsNetwork.INSTANCE.sendTo(
            new PacketSyncMonitorEntries(host.getEntries()),
            mp);
        lastEntriesVersion = host.getMonitorLogic().getEntriesVersion();

        List<MonitoredEntry> entries = host.getEntries();
        int n = entries.size();

        long[] quantities = new long[n];
        boolean[] conditions = new boolean[n];
        for (int i = 0; i < n; i++) {
            MonitoredEntry e = entries.get(i);
            quantities[i] = e.getLastQuantity();
            conditions[i] = e.isLastConditionMet();
        }

        PowerToolsNetwork.INSTANCE.sendTo(
            new PacketStorageEntryStateSync(quantities, conditions),
            mp);
    }

    private void syncFromHost() {
        this.refreshRate = host.getRefreshRate();
        this.matchMode = host.getMatchMode().ordinal();
        this.conditionMet = host.isConditionMet() ? 1 : 0;
        this.firstEntryQuantity = host.getFirstEntryQuantity();
        this.emitterRedstoneSignalStrength = supportsEmitterRedstone()
            ? ((IEmitterRedstoneHost) host).getRedstonePower().getId()
            : EmitterRedstonePower.WEAK.getId();
        this.emitterStrength = supportsEmitterRedstone()
            ? ((IEmitterRedstoneHost) host).getRedstoneStrength()
            : IEmitterRedstoneHost.DEFAULT_REDSTONE_STRENGTH;
        this.hysteresisEnabled = host.isHysteresisEnabled() ? 1 : 0;
        this.playerRegistered = supportsPlayerRegistration() && host.isPlayerRegistered(viewer) ? 1 : 0;
    }

    // --- Client-side getters ---

    public long getSyncRefreshRate() {
        return refreshRate;
    }

    public MatchMode getSyncMatchMode() {
        int ord = matchMode;
        return (ord >= 0 && ord < MatchMode.values().length)
            ? MatchMode.values()[ord]
            : MatchMode.AND;
    }

    public boolean isSyncConditionMet() {
        return conditionMet != 0;
    }

    public long getSyncFirstEntryQuantity() {
        return firstEntryQuantity;
    }

    public EmitterRedstonePower getSyncEmitterRedstonePower() {
        return EmitterRedstonePower.fromId(emitterRedstoneSignalStrength);
    }

    public int getSyncEmitterStrength() {
        return Math.max(
            IEmitterRedstoneHost.MIN_REDSTONE_STRENGTH,
            Math.min(IEmitterRedstoneHost.MAX_REDSTONE_STRENGTH, emitterStrength));
    }

    public boolean isSyncHysteresisEnabled() {
        return hysteresisEnabled != 0;
    }

    public boolean isSyncPlayerRegistered() {
        return playerRegistered != 0;
    }

    public IStorageMonitorHost getHost() {
        return host;
    }

    public MonitorHostType getHostType() {
        return host.getHostType();
    }

    public boolean supportsEmitterRedstone() {
        return host.getHostType() == MonitorHostType.EMITTER && host instanceof IEmitterRedstoneHost;
    }

    public boolean supportsMatchMode() {
        return host.supportsMatchMode();
    }

    public boolean supportsPlayerRegistration() {
        return host.supportsPlayerRegistration();
    }
}

