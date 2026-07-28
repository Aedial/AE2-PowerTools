package com.ae2powertools.features.remotemonitor;

import com.ae2powertools.network.PacketRemoteMonitorSetRefreshRate;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Refresh-interval sub-screen for the Remote Storage Monitor.
 */
public class GuiRemoteMonitorPollingRate extends GuiTimingScreen {

    public GuiRemoteMonitorPollingRate(long deviceId) {
        super(deviceId, RemoteMonitorClientState.getOrCreateState(deviceId).getRefreshRate());
    }

    @Override
    protected int getSyncedValue() {
        return RemoteMonitorClientState.getOrCreateState(getDeviceId()).getRefreshRate();
    }

    @Override
    protected int getMinimumValue() {
        return RemoteMonitorSessionManager.MIN_REFRESH_RATE;
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2powertools.remote_monitor.refresh_interval.title";
    }

    @Override
    protected void sendUpdatedValue(int value) {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorSetRefreshRate(getDeviceId(), value));
    }
}