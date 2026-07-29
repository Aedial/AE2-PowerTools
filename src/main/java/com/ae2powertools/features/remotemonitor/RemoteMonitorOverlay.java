package com.ae2powertools.features.remotemonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.util.ReadableNumberConverter;

import com.ae2powertools.client.HudOverlayManager;
import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.items.ItemRemoteStorageMonitor;


/**
 * Client-side HUD overlay for the Remote Storage Monitor.
 * Renders non-zero quantity deltas for the first held or worn monitor device.
 */
@SideOnly(Side.CLIENT)
public class RemoteMonitorOverlay implements HudOverlayManager.HudOverlayProvider {

    private static int lastOverlayHeight;

    @Override
    public boolean isActive(Minecraft mc) {
        if (mc.player == null || mc.world == null) return false;

        long deviceId = resolveDisplayedDeviceId(mc);
        if (deviceId == 0L) return false;

        boolean hasState = RemoteMonitorClientState.hasState(deviceId);
        RemoteMonitorClientState.requestSyncIfNeeded(deviceId, !hasState);
        return hasState;
    }

    @Override
    public HudOverlayManager.OverlayAnchor getAnchor() {
        return HudOverlayManager.OverlayAnchor.TOP_LEFT_STACK;
    }

    @Override
    public HudOverlayManager.OverlayStyle getStyle() {
        return HudOverlayManager.OverlayStyle.UNBOXED;
    }

    @Override
    public List<HudOverlayManager.HudOverlayLine> getLines(Minecraft mc) {
        long deviceId = RemoteMonitorClientState.getActiveDeviceId();
        if (deviceId == 0L || !RemoteMonitorClientState.hasState(deviceId)) return Collections.emptyList();

        PowerToolsClientConfig.RemoteMonitor config = PowerToolsClientConfig.remoteMonitor;
        RemoteMonitorClientState.DeviceState state = RemoteMonitorClientState.getOrCreateState(deviceId);
        List<HudOverlayManager.HudOverlayLine> lines = new ArrayList<>();

        MonitoredResource[] configured = state.getResources();
        long[] deltas = state.getDeltas();
        long[] currentQuantities = state.getCurrentQuantities();
        int slotCount = Math.min(configured.length, Math.min(deltas.length, currentQuantities.length));
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            MonitoredResource resource = configured[slotIndex];
            long delta = deltas[slotIndex];
            if (resource == null || delta == 0) continue;

            lines.add(HudOverlayManager.HudOverlayLine.iconText(
                resource,
                formatEntry(delta, currentQuantities[slotIndex], config),
                delta > 0 ? config.getGainColor() : config.getLossColor()));
        }

        return lines;
    }

    public static int getOverlayHeight() {
        return lastOverlayHeight;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void onOverlayRendered(int renderedHeight) {
        lastOverlayHeight = renderedHeight;
    }

    private long resolveDisplayedDeviceId(Minecraft mc) {
        // deviceId of 0L is invalid (no client state yet or stale client state)
        long deviceId = RemoteMonitorClientState.getActiveDeviceId();
        if (deviceId != 0L) {
            ItemStack cachedMonitor = ItemRemoteStorageMonitor.getHeldMonitor(mc.player, deviceId);
            if (!cachedMonitor.isEmpty()) return deviceId;

            RemoteMonitorClientState.invalidateActiveDeviceId();
        }

        if (!RemoteMonitorClientState.shouldRescanDisplayedMonitor(mc.world.getTotalWorldTime())) return 0L;

        ItemStack monitor = ItemRemoteStorageMonitor.getHeldMonitor(mc.player);
        if (monitor.isEmpty()) return 0L;

        long resolvedDeviceId = ItemRemoteStorageMonitor.getDeviceId(monitor);
        RemoteMonitorClientState.setActiveDeviceId(resolvedDeviceId);
        return resolvedDeviceId;
    }

    private String formatEntry(long delta, long currentQuantity, PowerToolsClientConfig.RemoteMonitor config) {
        String deltaText = formatSignedQuantity(delta, config);
        String totalText = config.showRemoteMonitorTotalQuantity()
            ? " / " + formatQuantity(currentQuantity, config)
            : "";

        double percent = RemoteMonitorMath.calculateChangePercent(delta, currentQuantity);
        if (!Double.isFinite(percent) || percent <= 0.1D) return deltaText + totalText;

        return deltaText + totalText + " (" + formatPercent(percent) + "%)";
    }

    private String formatSignedQuantity(long delta, PowerToolsClientConfig.RemoteMonitor config) {
        String prefix = delta > 0 ? "+" : "-";
        return prefix + formatQuantity(Math.abs(delta), config);
    }

    private String formatQuantity(long quantity, PowerToolsClientConfig.RemoteMonitor config) {
        if (config.showRemoteMonitorShortenedNumbers()) {
            return ReadableNumberConverter.INSTANCE.toWideReadableForm(quantity);
        }

        return String.format(Locale.US, "%,d", quantity);
    }

    private String formatPercent(double percent) {
        String formattedPercent = String.format(Locale.US, "%.1f", percent);
        if (formattedPercent.endsWith(".0")) return formattedPercent.substring(0, formattedPercent.length() - 2);

        return formattedPercent;
    }
}