package com.ae2powertools.features.remotemonitor;

import com.ae2powertools.network.PacketRemoteMonitorSetSlidingWindow;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Sliding-window sub-screen for the Remote Storage Monitor.
 */
public class GuiRemoteMonitorSlidingWindow extends GuiTimingScreen {

    public GuiRemoteMonitorSlidingWindow(long deviceId) {
        super(deviceId, RemoteMonitorClientState.getOrCreateState(deviceId).getSlidingWindow());
    }

    @Override
    protected int getSyncedValue() {
        return RemoteMonitorClientState.getOrCreateState(getDeviceId()).getSlidingWindow();
    }

    @Override
    protected int getMinimumValue() {
        return RemoteMonitorSessionManager.MIN_REFRESH_RATE;
    }

    @Override
    protected String getTitleKey() {
        return "gui.ae2powertools.remote_monitor.sliding_window.title";
    }

    @Override
    protected void sendUpdatedValue(int value) {
        PowerToolsNetwork.INSTANCE.sendToServer(new PacketRemoteMonitorSetSlidingWindow(getDeviceId(), value));
    }
}