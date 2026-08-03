package com.ae2powertools.features.monitor.alarm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.client.HudOverlayManager;


/**
 * Pulsing warning text shown above the XP bar whenever one or more subscribed alarms are active.
 */
@SideOnly(Side.CLIENT)
public class LevelMonitorAlarmOverlay implements HudOverlayManager.HudOverlayProvider {

    private static final int COLOR_ORANGE = 0xFFAA33;
    private static final int COLOR_RED = 0xFF3333;

    @Override
    public boolean isActive(Minecraft mc) {
        if (mc.player == null || mc.world == null) {
            LevelMonitorAlarmClientState.clear();
            return false;
        }

        return !LevelMonitorAlarmClientState.getActiveAlarms().isEmpty();
    }

    @Override
    public HudOverlayManager.OverlayAnchor getAnchor() {
        return HudOverlayManager.OverlayAnchor.ABOVE_XP_BAR_STACK;
    }

    @Override
    public HudOverlayManager.OverlayStyle getStyle() {
        return HudOverlayManager.OverlayStyle.UNBOXED;
    }

    @Override
    public List<HudOverlayManager.HudOverlayLine> getLines(Minecraft mc) {
        List<AlarmLocation> alarms = LevelMonitorAlarmClientState.getActiveAlarms();
        if (alarms.isEmpty()) return new ArrayList<>();

        String locations = joinLocations(alarms, mc.player.dimension);
        String key = alarms.size() == 1
            ? "gui.ae2powertools.level_monitor_alarm.overlay.single"
            : "gui.ae2powertools.level_monitor_alarm.overlay.multiple";
        String message = I18n.format(key, locations);

        ScaledResolution resolution = new ScaledResolution(mc);
        List<String> lines = mc.fontRenderer.listFormattedStringToWidth(message, resolution.getScaledWidth() - 20);
        if (lines.isEmpty()) return new ArrayList<>();

        // 2 pi = 1 full period
        float pulse = 0.5f + 0.5f * (float) Math.sin((2.0 * Math.PI * System.currentTimeMillis()) / 1000.0);
        int color = interpolateColor(COLOR_ORANGE, COLOR_RED, pulse);

        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) colors.add(color);

        return HudOverlayManager.HudOverlayLine.textLines(lines, colors);
    }

    @Override
    public HudOverlayManager.ScreenPolicy getScreenPolicy() {
        return HudOverlayManager.ScreenPolicy.ALWAYS;
    }

    @Override
    public boolean hideWithDebugInfo() {
        return false;
    }

    private String joinLocations(List<AlarmLocation> alarms, int currentDimension) {
        List<String> formatted = new ArrayList<>();
        for (AlarmLocation alarm : alarms) {
            formatted.add(formatLocation(alarm, currentDimension));
        }

        return String.join(", ", formatted);
    }

    private String formatLocation(AlarmLocation alarm, int currentDimension) {
        if (alarm.getDimensionId() == currentDimension) {
            return String.format("[%d, %d, %d]",
                alarm.getPos().getX(),
                alarm.getPos().getY(),
                alarm.getPos().getZ());
        }

        return String.format("[%d, %d, %d / %d]",
            alarm.getPos().getX(),
            alarm.getPos().getY(),
            alarm.getPos().getZ(),
            alarm.getDimensionId());
    }

    private int interpolateColor(int startColor, int endColor, float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        int startRed = (startColor >> 16) & 0xFF;
        int startGreen = (startColor >> 8) & 0xFF;
        int startBlue = startColor & 0xFF;
        int endRed = (endColor >> 16) & 0xFF;
        int endGreen = (endColor >> 8) & 0xFF;
        int endBlue = endColor & 0xFF;

        int red = startRed + Math.round((endRed - startRed) * clamped);
        int green = startGreen + Math.round((endGreen - startGreen) * clamped);
        int blue = startBlue + Math.round((endBlue - startBlue) * clamped);

        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}