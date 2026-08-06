package com.ae2powertools.features.monitor.alarm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.client.HudOverlayManager;
import com.ae2powertools.client.TrackedLocationRenderer;


/**
 * Renders directional arrows to all currently active alarms while the locator item is held.
 */
@SideOnly(Side.CLIENT)
public class LevelMonitorAlarmArrowRenderer implements HudOverlayManager.HudOverlayProvider {

    private static final int ARROW_COLOR = 0xFFAA33;

    private static int lastOverlayHeight = 0;

    @Override
    public boolean isActive(Minecraft mc) {
        if (mc.player == null || mc.world == null) return false;
        if (!isHoldingLocator(mc.player.getHeldItemMainhand()) && !isHoldingLocator(mc.player.getHeldItemOffhand())) {
            return false;
        }

        List<AlarmLocation> alarms = LevelMonitorAlarmClientState.getActiveAlarms();
        return !alarms.isEmpty();
    }

    @Override
    public HudOverlayManager.OverlayAnchor getAnchor() {
        return HudOverlayManager.OverlayAnchor.TOP_LEFT_STACK;
    }

    @Override
    public HudOverlayManager.OverlayStyle getStyle() {
        return HudOverlayManager.OverlayStyle.BOXED;
    }

    @Override
    public List<HudOverlayManager.HudOverlayLine> getLines(Minecraft mc) {
        List<AlarmLocation> alarms = LevelMonitorAlarmClientState.getActiveAlarms();
        if (alarms.isEmpty()) return new ArrayList<>();

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        BlockPos playerPos = mc.player.getPosition();

        for (AlarmLocation alarm : alarms) {
            if (alarm.getDimensionId() != mc.player.dimension) continue;

            double distance = alarm.getPos().distanceSq(playerPos);
            lines.add(String.format("[%d, %d, %d] - %s",
                alarm.getPos().getX(),
                alarm.getPos().getY(),
                alarm.getPos().getZ(),
                TrackedLocationRenderer.formatDistance(Math.sqrt(distance))));
            colors.add(ARROW_COLOR);
        }

        return HudOverlayManager.HudOverlayLine.textLines(lines, colors);
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public void onOverlayRendered(int renderedHeight) {
        lastOverlayHeight = renderedHeight;
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