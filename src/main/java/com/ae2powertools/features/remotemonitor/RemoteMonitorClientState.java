package com.ae2powertools.features.remotemonitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.network.PacketRemoteMonitorRequestSync;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Client-side RAM mirror of Remote Storage Monitor sessions.
 * Stores the latest synced slot selections, polling rate, overlay deltas, and the
 * current selector contents per device ID.
 */
@SideOnly(Side.CLIENT)
public final class RemoteMonitorClientState {

    public static final class DeviceState {

        private int refreshRate = RemoteMonitorSessionManager.DEFAULT_REFRESH_RATE;
        private MonitoredResource[] resources = new MonitoredResource[RemoteMonitorSessionManager.SLOT_COUNT];
        private long[] deltas = new long[RemoteMonitorSessionManager.SLOT_COUNT];
        private List<MonitoredResource> selectorResources = new ArrayList<>();
        private long lastSyncRequestTick = Long.MIN_VALUE;

        public int getRefreshRate() {
            return this.refreshRate;
        }

        public MonitoredResource[] getResources() {
            return this.resources;
        }

        public long[] getDeltas() {
            return this.deltas;
        }

        public List<MonitoredResource> getSelectorResources() {
            return this.selectorResources;
        }

        private void syncState(int refreshRate, MonitoredResource[] resources, long[] deltas) {
            this.refreshRate = refreshRate;
            this.resources = Arrays.copyOf(resources, resources.length);
            this.deltas = Arrays.copyOf(deltas, deltas.length);
        }

        private void setSelectorResources(List<MonitoredResource> selectorResources) {
            this.selectorResources = new ArrayList<>(selectorResources);
        }

        private boolean shouldRequestSync(long worldTick) {
            int requestInterval = Math.max(1, this.refreshRate / 2);
            if (this.lastSyncRequestTick != Long.MIN_VALUE
                    && worldTick >= this.lastSyncRequestTick
                    && worldTick - this.lastSyncRequestTick < requestInterval) {
                return false;
            }

            this.lastSyncRequestTick = worldTick;
            return true;
        }
    }

    private static final Map<Long, DeviceState> DEVICE_STATES = new HashMap<>();
    private static long activeDeviceId;

    private RemoteMonitorClientState() {}

    public static void setActiveDeviceId(long deviceId) {
        activeDeviceId = deviceId;
    }

    public static long getActiveDeviceId() {
        return activeDeviceId;
    }

    public static DeviceState getOrCreateState(long deviceId) {
        return DEVICE_STATES.computeIfAbsent(deviceId, ignored -> new DeviceState());
    }

    public static DeviceState getActiveState() {
        return getOrCreateState(activeDeviceId);
    }

    public static boolean hasState(long deviceId) {
        return DEVICE_STATES.containsKey(deviceId);
    }

    public static void syncState(long deviceId, int refreshRate, MonitoredResource[] resources, long[] deltas) {
        getOrCreateState(deviceId).syncState(refreshRate, resources, deltas);
    }

    public static void requestSyncIfNeeded(long deviceId, boolean forceSync) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        DeviceState state = getOrCreateState(deviceId);
        long worldTick = mc.world.getTotalWorldTime();
        if (!state.shouldRequestSync(worldTick)) return;

        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorRequestSync(deviceId, forceSync));
    }

    public static void setSelectorResources(long deviceId, List<MonitoredResource> selectorResources) {
        getOrCreateState(deviceId).setSelectorResources(selectorResources);
    }

    public static void clearState(long deviceId) {
        DEVICE_STATES.remove(deviceId);
        if (activeDeviceId == deviceId) activeDeviceId = 0L;
    }
}