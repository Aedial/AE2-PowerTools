package com.ae2powertools.features.monitor.alarm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.client.TrackedLocationRenderer;
import com.ae2powertools.features.locator.LocatorRenderer;
import com.ae2powertools.features.scanner.ScannerRenderer;


/**
 * Renders directional arrows to all currently active alarms while the locator item is held.
 */
@SideOnly(Side.CLIENT)
public class LevelMonitorAlarmArrowRenderer {

    private static final int ARROW_COLOR = 0xFFAA33;

    private static int lastOverlayHeight = 0;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        lastOverlayHeight = 0;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;
        if (!isHoldingLocator(mc.player.getHeldItemMainhand()) && !isHoldingLocator(mc.player.getHeldItemOffhand())) return;

        List<AlarmLocation> alarms = LevelMonitorAlarmClientState.getActiveAlarms();
        if (alarms.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<BlockPos> sameDimensionTargets = new ArrayList<>();
        BlockPos playerPos = mc.player.getPosition();

        for (AlarmLocation alarm : alarms) {
            if (alarm.getDimensionId() != mc.player.dimension) continue;

            sameDimensionTargets.add(alarm.getPos());

            double distance = alarm.getPos().distanceSq(playerPos);
            lines.add(String.format("[%d, %d, %d] - %s",
                alarm.getPos().getX(),
                alarm.getPos().getY(),
                alarm.getPos().getZ(),
                TrackedLocationRenderer.formatDistance(Math.sqrt(distance))));
            colors.add(ARROW_COLOR);
        }

        if (lines.isEmpty()) return;

        int boxY = TrackedLocationRenderer.getExternalPadding()
            + ScannerRenderer.getOverlayHeight()
            + LocatorRenderer.getOverlayHeight();
        lastOverlayHeight = TrackedLocationRenderer.drawOverlayBox(mc, boxY, lines, colors);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) return;

        if (!isHoldingLocator(player.getHeldItemMainhand()) && !isHoldingLocator(player.getHeldItemOffhand())) {
            return;
        }

        List<BlockPos> positions = new ArrayList<>();

        for (AlarmLocation alarm : LevelMonitorAlarmClientState.getActiveAlarms()) {
            if (alarm.getDimensionId() == player.dimension) positions.add(alarm.getPos());
        }

        TrackedLocationRenderer.renderWorldTargets(player, positions, ARROW_COLOR, event.getPartialTicks());
    }

    public static int getOverlayHeight() {
        return lastOverlayHeight;
    }

    private boolean isHoldingLocator(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ItemRegistry.LEVEL_MONITOR_ALARM_LOCATOR;
    }
}