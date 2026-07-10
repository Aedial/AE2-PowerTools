package com.ae2powertools.features.monitor.alarm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Pulsing warning text shown above the XP bar whenever one or more subscribed alarms are active.
 */
@SideOnly(Side.CLIENT)
public class LevelMonitorAlarmOverlay {

    private static final int COLOR_ORANGE = 0xFFAA33;
    private static final int COLOR_RED = 0xFF3333;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            LevelMonitorAlarmClientState.clear();
            return;
        }
        if (mc.gameSettings.showDebugInfo) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

        List<AlarmLocation> alarms = LevelMonitorAlarmClientState.getActiveAlarms();
        if (alarms.isEmpty()) return;

        String locations = joinLocations(alarms, mc.player.dimension);
        String key = alarms.size() == 1
            ? "gui.ae2powertools.level_monitor_alarm.overlay.single"
            : "gui.ae2powertools.level_monitor_alarm.overlay.multiple";
        String message = I18n.format(key, locations);

        ScaledResolution resolution = new ScaledResolution(mc);
        List<String> lines = mc.fontRenderer.listFormattedStringToWidth(message, resolution.getScaledWidth() - 20);
        if (lines.isEmpty()) return;

        // 2 pi = 1 full period
        float pulse = 0.5f + 0.5f * (float) Math.sin((2.0 * Math.PI * System.currentTimeMillis()) / 1000.0);
        int color = interpolateColor(COLOR_ORANGE, COLOR_RED, pulse);

        int totalHeight = lines.size() * mc.fontRenderer.FONT_HEIGHT;
        int y = resolution.getScaledHeight() - 45 - totalHeight;

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        for (String line : lines) {
            int x = (resolution.getScaledWidth() - mc.fontRenderer.getStringWidth(line)) / 2;
            mc.fontRenderer.drawStringWithShadow(line, x, y, color);
            y += mc.fontRenderer.FONT_HEIGHT;
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
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