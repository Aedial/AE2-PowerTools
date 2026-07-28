package com.ae2powertools.features.remotemonitor;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
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
 * The item NBT owns the persisted filters, refresh interval, and sliding window,
 * while the session keeps only transient quantity history in RAM.
 */
public final class RemoteMonitorSessionManager {

    public static final int SLOT_COUNT = 81;
    public static final int DEFAULT_REFRESH_RATE = PollingRateUtils.TICKS_PER_SECOND;
    public static final int MIN_REFRESH_RATE = PollingRateUtils.TICKS_PER_SECOND;
    public static final int DEFAULT_SLIDING_WINDOW = DEFAULT_REFRESH_RATE;

    /**
     * Extra time to wait after access resumes before locking in a new baseline.
     * AE2 can expose the grid before all storage providers have reported their startup totals.
     */
    private static final int BASELINE_SETTLE_TICKS = 5 * PollingRateUtils.TICKS_PER_SECOND;

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

        private static final class HistorySample {

            private final long worldTick;
            private final long[] quantities;

            private HistorySample(long worldTick, long[] quantities) {
                this.worldTick = worldTick;
                this.quantities = quantities;
            }
        }

        private final long deviceId;
        private final MonitoredResource[] resources = new MonitoredResource[SLOT_COUNT];
        private final long[] currentQuantities = new long[SLOT_COUNT];
        private final long[] deltas = new long[SLOT_COUNT];
        private final ArrayDeque<HistorySample> quantityHistory = new ArrayDeque<>();

        private int refreshRate = DEFAULT_REFRESH_RATE;
        private int slidingWindow = DEFAULT_SLIDING_WINDOW;
        private int ticksUntilPoll = DEFAULT_REFRESH_RATE;
        private boolean awaitingBaselineSample;
        private long baselineSettleDeadline;
        private boolean networkAccessible = true;

        private RemoteMonitorSession(long deviceId, MonitoredResource[] storedResources, int refreshRate,
                int slidingWindow) {
            this.deviceId = deviceId;
            System.arraycopy(storedResources, 0, this.resources, 0, Math.min(this.resources.length, storedResources.length));
            applyTimingSettings(refreshRate, slidingWindow);
        }

        public long getDeviceId() {
            return this.deviceId;
        }

        public int getRefreshRate() {
            return this.refreshRate;
        }

        public int getSlidingWindow() {
            return this.slidingWindow;
        }

        public MonitoredResource[] copyResources() {
            return Arrays.copyOf(this.resources, this.resources.length);
        }

        public long[] copyDeltas() {
            return Arrays.copyOf(this.deltas, this.deltas.length);
        }

        public long[] copyCurrentQuantities() {
            return Arrays.copyOf(this.currentQuantities, this.currentQuantities.length);
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

            long currentQuantity = 0;
            if (resource != null) {
                currentQuantity = RemoteMonitorNetworkHelper.lookupQuantity(handler, player, monitorStack, resource);
            }

            this.currentQuantities[slotIndex] = currentQuantity;
            this.deltas[slotIndex] = 0;
            updateHistorySlot(player.world.getTotalWorldTime(), slotIndex, currentQuantity);
        }

        public void setRefreshRate(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack,
                int refreshRate) {
            applyTimingSettings(refreshRate, this.slidingWindow);
            persistTimingSettings(monitorStack);
            resetBaselines(handler, player, monitorStack);
        }

        public void setSlidingWindow(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack,
                int slidingWindow) {
            applyTimingSettings(this.refreshRate, slidingWindow);
            persistTimingSettings(monitorStack);
            resetBaselines(handler, player, monitorStack);
        }

        public void tick(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack) {
            if (--this.ticksUntilPoll > 0) return;

            this.ticksUntilPoll = this.refreshRate;

            boolean accessible = RemoteMonitorNetworkHelper.hasAccess(handler, player, monitorStack);
            if (!accessible) {
                if (this.networkAccessible) {
                    this.networkAccessible = false;
                    clearDeltas();
                    this.quantityHistory.clear();
                }

                return;
            }

            if (!this.networkAccessible) {
                this.networkAccessible = true;
                resetBaselines(handler, player, monitorStack);
                return;
            }

            pollNow(handler, player, monitorStack, player.world.getTotalWorldTime());
        }

        public void resetBaselines(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack) {
            this.ticksUntilPoll = 1;
            this.awaitingBaselineSample = true;
            this.baselineSettleDeadline = player.world.getTotalWorldTime() + BASELINE_SETTLE_TICKS;
            clearDeltas();
            this.quantityHistory.clear();

            if (!RemoteMonitorNetworkHelper.hasAccess(handler, player, monitorStack)) {
                this.networkAccessible = false;
                return;
            }

            this.networkAccessible = true;
            for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
                MonitoredResource resource = this.resources[slotIndex];
                if (resource == null) {
                    this.currentQuantities[slotIndex] = 0;
                    continue;
                }

                long quantity = RemoteMonitorNetworkHelper.lookupQuantity(handler, player, monitorStack, resource);
                this.currentQuantities[slotIndex] = quantity;
            }
        }

        private void pollNow(IWirelessTermHandler handler, EntityPlayer player, ItemStack monitorStack, long worldTick) {
            for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
                MonitoredResource resource = this.resources[slotIndex];
                if (resource == null) {
                    this.currentQuantities[slotIndex] = 0;
                    continue;
                }

                long currentQuantity = RemoteMonitorNetworkHelper.lookupQuantity(handler, player, monitorStack, resource);
                this.currentQuantities[slotIndex] = currentQuantity;
            }

            // Hold the baseline open briefly so AE2 startup storage churn is absorbed into it.
            if (this.awaitingBaselineSample) {
                if (worldTick < this.baselineSettleDeadline) {
                    clearDeltas();
                    return;
                }

                this.awaitingBaselineSample = false;
                replaceHistory(worldTick);
                clearDeltas();
                return;
            }

            addHistorySample(worldTick);
            recomputeDeltas();
        }

        private boolean isValidSlot(int slotIndex) {
            return slotIndex >= 0 && slotIndex < SLOT_COUNT;
        }

        private void applyTimingSettings(int refreshRate, int slidingWindow) {
            int clampedSlidingWindow = Math.max(MIN_REFRESH_RATE, slidingWindow);

            this.slidingWindow = clampedSlidingWindow;
            this.refreshRate = Math.max(MIN_REFRESH_RATE, Math.min(refreshRate, clampedSlidingWindow));
            this.ticksUntilPoll = this.refreshRate;
        }

        private void persistTimingSettings(ItemStack monitorStack) {
            ItemRemoteStorageMonitor.setStoredRefreshRate(monitorStack, this.refreshRate);
            ItemRemoteStorageMonitor.setStoredSlidingWindow(monitorStack, this.slidingWindow);
        }

        private void updateHistorySlot(long worldTick, int slotIndex, long quantity) {
            if (this.awaitingBaselineSample) return;

            if (this.quantityHistory.isEmpty()) {
                replaceHistory(worldTick);
                return;
            }

            for (HistorySample sample : this.quantityHistory) sample.quantities[slotIndex] = quantity;
        }

        private void addHistorySample(long worldTick) {
            this.quantityHistory.addLast(new HistorySample(
                worldTick,
                Arrays.copyOf(this.currentQuantities, this.currentQuantities.length)));
            pruneHistory(worldTick);
        }

        private void replaceHistory(long worldTick) {
            this.quantityHistory.clear();
            this.quantityHistory.addLast(new HistorySample(
                worldTick,
                Arrays.copyOf(this.currentQuantities, this.currentQuantities.length)));
        }

        private void pruneHistory(long worldTick) {
            long cutoffTick = worldTick - this.slidingWindow;

            while (this.quantityHistory.size() > 1) {
                HistorySample secondSample = getSecondHistorySample();
                if (secondSample == null || secondSample.worldTick > cutoffTick) return;

                this.quantityHistory.removeFirst();
            }
        }

        @Nullable
        private HistorySample getSecondHistorySample() {
            Iterator<HistorySample> iterator = this.quantityHistory.iterator();
            if (!iterator.hasNext()) return null;

            iterator.next();
            return iterator.hasNext() ? iterator.next() : null;
        }

        private void recomputeDeltas() {
            HistorySample baselineSample = this.quantityHistory.peekFirst();
            if (baselineSample == null) {
                clearDeltas();
                return;
            }

            for (int slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
                MonitoredResource resource = this.resources[slotIndex];
                if (resource == null) {
                    this.deltas[slotIndex] = 0;
                    continue;
                }

                this.deltas[slotIndex] = this.currentQuantities[slotIndex] - baselineSample.quantities[slotIndex];
            }
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
            ItemRemoteStorageMonitor.getStoredRefreshRate(monitorStack),
            ItemRemoteStorageMonitor.getStoredSlidingWindow(monitorStack));
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