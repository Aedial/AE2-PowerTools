package com.ae2powertools.features.remotemonitor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import appeng.api.features.IWirelessTermHandler;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.items.ItemRemoteStorageMonitor;
import com.ae2powertools.util.PollingRateUtils;


/**
 * Server-side session store for Remote Storage Monitors.
 * The item NBT owns the persisted filters and refresh rate, while the session keeps
 * only transient baseline/delta state in RAM.
 */
public final class RemoteMonitorSessionManager {

    public static final int SLOT_COUNT = 81;
    public static final int DEFAULT_REFRESH_RATE = PollingRateUtils.TICKS_PER_SECOND;
    public static final int MIN_REFRESH_RATE = PollingRateUtils.TICKS_PER_SECOND;

    private static final Map<SessionKey, RemoteMonitorSession> SESSIONS = new HashMap<>();

    private RemoteMonitorSessionManager() {}

    public static final class SessionKey {

        private final UUID playerId;
        private final long deviceId;

        public SessionKey(UUID playerId, long deviceId) {
            this.playerId = playerId;
            this.deviceId = deviceId;
        }

        public UUID getPlayerId() {
            return this.playerId;
        }

        public long getDeviceId() {
            return this.deviceId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof SessionKey)) return false;

            SessionKey other = (SessionKey) obj;
            return this.deviceId == other.deviceId && this.playerId.equals(other.playerId);
        }

        @Override
        public int hashCode() {
            return 31 * this.playerId.hashCode() + Long.hashCode(this.deviceId);
        }
    }

    /**
     * Represents a server-side session for a Remote Storage Monitor device.
     * Each device is attached to 1 session, which is created on first access.
     * Stores transient baseline/delta state in RAM.
     */
    public static final class RemoteMonitorSession {

        private final long deviceId;
        private final MonitoredResource[] resources = new MonitoredResource[SLOT_COUNT];
        private final long[] baselineQuantities = new long[SLOT_COUNT];
        private final long[] deltas = new long[SLOT_COUNT];

        private int refreshRate = DEFAULT_REFRESH_RATE;
        private int ticksUntilPoll = DEFAULT_REFRESH_RATE;
        private int demandLeaseTicks;
        private boolean networkAccessible = true;

        private RemoteMonitorSession(long deviceId, MonitoredResource[] storedResources, int refreshRate) {
            this.deviceId = deviceId;
            System.arraycopy(storedResources, 0, this.resources, 0, Math.min(this.resources.length, storedResources.length));
            this.refreshRate = Math.max(MIN_REFRESH_RATE, refreshRate);
            this.ticksUntilPoll = this.refreshRate;
        }

        public long getDeviceId() {
            return this.deviceId;
        }

        public int getRefreshRate() {
            return this.refreshRate;
        }

        public MonitoredResource[] copyResources() {
            return Arrays.copyOf(this.resources, this.resources.length);
        }

        public long[] copyDeltas() {
            return Arrays.copyOf(this.deltas, this.deltas.length);
        }

        public long[] copyCurrentQuantities() {
            return Arrays.copyOf(this.baselineQuantities, this.baselineQuantities.length);
        }

        @Nullable
        public MonitoredResource getResource(int slotIndex) {
            if (!isValidSlot(slotIndex)) return null;
            return this.resources[slotIndex];
        }

        public void setResource(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack,
                int slotIndex, @Nullable MonitoredResource resource) {
            if (!isValidSlot(slotIndex)) return;

            this.resources[slotIndex] = resource;
            ItemRemoteStorageMonitor.setStoredResource(monitorStack, slotIndex, resource);
            if (resource == null) {
                this.baselineQuantities[slotIndex] = 0;
                this.deltas[slotIndex] = 0;
                return;
            }

            long currentQuantity = RemoteMonitorNetworkHelper.lookupQuantity(handler, player, monitorStack, resource);
            this.baselineQuantities[slotIndex] = currentQuantity;
            this.deltas[slotIndex] = 0;
        }

        public void setRefreshRate(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack,
                int refreshRate) {
            this.refreshRate = Math.max(MIN_REFRESH_RATE, refreshRate);
            this.ticksUntilPoll = this.refreshRate;
            ItemRemoteStorageMonitor.setStoredRefreshRate(monitorStack, this.refreshRate);
            resetBaselines(handler, player, monitorStack);
        }

        public boolean noteSyncRequest(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack) {
            boolean wasDormant = this.demandLeaseTicks <= 0;
            this.demandLeaseTicks = this.refreshRate;

            if (!wasDormant) return false;

            resetBaselines(handler, player, monitorStack);
            return true;
        }

        public boolean tick(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack) {
            // Only actually tick if there is a device asking for updates, otherwise just sleep
            if (this.demandLeaseTicks <= 0) return false;

            if (--this.demandLeaseTicks <= 0) {
                clearDeltas();  // don't keep stale deltas when going to sleep
                return false;
            }

            if (--this.ticksUntilPoll > 0) return false;

            this.ticksUntilPoll = this.refreshRate;

            boolean accessible = RemoteMonitorNetworkHelper.hasAccess(handler, player, monitorStack);
            if (!accessible) {
                this.networkAccessible = false;
                return false;
            }

            if (!this.networkAccessible) {
                this.networkAccessible = true;
                resetBaselines(handler, player, monitorStack);
                return true;
            }

            pollNow(handler, player, monitorStack);
            return true;
        }

        public void resetBaselines(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack) {
            this.ticksUntilPoll = this.refreshRate;
            clearDeltas();

            if (!RemoteMonitorNetworkHelper.hasAccess(handler, player, monitorStack)) {
                this.networkAccessible = false;
                return;
            }

            this.networkAccessible = true;
            for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
                MonitoredResource resource = this.resources[slotIndex];
                if (resource == null) {
                    this.baselineQuantities[slotIndex] = 0;
                    this.deltas[slotIndex] = 0;
                    continue;
                }

                long quantity = RemoteMonitorNetworkHelper.lookupQuantity(handler, player, monitorStack, resource);
                this.baselineQuantities[slotIndex] = quantity;
            }
        }

        private void pollNow(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack) {
            for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
                MonitoredResource resource = this.resources[slotIndex];
                if (resource == null) {
                    this.baselineQuantities[slotIndex] = 0;
                    this.deltas[slotIndex] = 0;
                    continue;
                }

                long currentQuantity = RemoteMonitorNetworkHelper.lookupQuantity(handler, player, monitorStack, resource);
                long previousQuantity = this.baselineQuantities[slotIndex];

                this.deltas[slotIndex] = currentQuantity - previousQuantity;
                this.baselineQuantities[slotIndex] = currentQuantity;
            }
        }

        private boolean isValidSlot(int slotIndex) {
            return slotIndex >= 0 && slotIndex < SLOT_COUNT;
        }

        private void clearDeltas() {
            Arrays.fill(this.deltas, 0L);
        }
    }

    public static RemoteMonitorSession getOrCreateSession(IWirelessTermHandler handler, EntityPlayerMP player,
            ItemStack monitorStack, long deviceId) {
        SessionKey key = new SessionKey(player.getUniqueID(), deviceId);
        RemoteMonitorSession session = SESSIONS.get(key);
        if (session != null) return session;

        session = new RemoteMonitorSession(
            deviceId,
            ItemRemoteStorageMonitor.getStoredResources(monitorStack),
            ItemRemoteStorageMonitor.getStoredRefreshRate(monitorStack));
        session.resetBaselines(handler, player, monitorStack);
        SESSIONS.put(key, session);
        return session;
    }

    @Nullable
    public static RemoteMonitorSession getSession(EntityPlayer player, long deviceId) {
        return SESSIONS.get(new SessionKey(player.getUniqueID(), deviceId));
    }

    @Nullable
    public static RemoteMonitorSession getSession(UUID playerId, long deviceId) {
        return SESSIONS.get(new SessionKey(playerId, deviceId));
    }

    public static void endSession(EntityPlayer player, long deviceId) {
        SESSIONS.remove(new SessionKey(player.getUniqueID(), deviceId));
    }

    public static void endSession(UUID playerId, long deviceId) {
        SESSIONS.remove(new SessionKey(playerId, deviceId));
    }

    public static Iterable<Map.Entry<SessionKey, RemoteMonitorSession>> getSessionEntries() {
        return SESSIONS.entrySet();
    }
}