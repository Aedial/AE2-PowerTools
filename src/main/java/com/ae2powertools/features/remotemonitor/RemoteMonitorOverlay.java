package com.ae2powertools.features.remotemonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.util.ReadableNumberConverter;

import baubles.api.BaublesApi;

import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.items.ItemRemoteStorageMonitor;


/**
 * Client-side HUD overlay for the Remote Storage Monitor.
 * Renders non-zero quantity deltas for the first held or worn monitor device.
 */
@SideOnly(Side.CLIENT)
public class RemoteMonitorOverlay {

    private static int lastOverlayHeight;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        lastOverlayHeight = 0;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;
        // TODO: Allow rendering on top of more screens.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat)) return;

        ItemStack monitor = findDisplayedMonitor(mc);
        if (monitor.isEmpty()) return;

        // on first render with a new device, register device (deltas will render next sync)
        long deviceId = ItemRemoteStorageMonitor.getDeviceId(monitor);
        boolean hasState = RemoteMonitorClientState.hasState(deviceId);
        RemoteMonitorClientState.requestSyncIfNeeded(deviceId, !hasState);
        if (!hasState) return;

        PowerToolsClientConfig.RemoteMonitor config = PowerToolsClientConfig.remoteMonitor;
        RemoteMonitorClientState.DeviceState state = RemoteMonitorClientState.getOrCreateState(deviceId);
        List<MonitoredResource> resources = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        MonitoredResource[] configured = state.getResources();
        long[] deltas = state.getDeltas();
        long[] currentQuantities = state.getCurrentQuantities();
        int slotCount = Math.min(configured.length, Math.min(deltas.length, currentQuantities.length));
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            MonitoredResource resource = configured[slotIndex];
            long delta = deltas[slotIndex];
            if (resource == null || delta == 0) continue;

            resources.add(resource);
            lines.add(formatDelta(delta, currentQuantities[slotIndex]));
            colors.add(delta > 0 ? config.getGainColor() : config.getLossColor());
        }

        if (lines.isEmpty()) return;

        float textScale = config.getTextScale();
        float scaledTextHeight = mc.fontRenderer.FONT_HEIGHT * textScale;
        int lineHeight = Math.max((int) Math.ceil(scaledTextHeight), config.getIconSize());
        int boxX = config.getX();
        int boxY = config.getY();

        int iconX = boxX + config.getPaddingInternal();
        int textX = iconX + config.getIconSize() + config.getIconTextGap();
        int lineY = boxY + config.getPaddingInternal();

        for (int i = 0; i < lines.size(); i++) {
            MonitoredResourceRenderer.renderIcon(resources.get(i), iconX, lineY, config.getIconSize());
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            float textY = lineY + (lineHeight - scaledTextHeight) / 2.0F;
            drawScaledText(mc, lines.get(i), textX, textY, colors.get(i), textScale);
            lineY += lineHeight + config.getLineSpacing();
        }

        int boxHeight = config.getPaddingInternal() * 2
            + lines.size() * lineHeight
            + (lines.size() - 1) * config.getLineSpacing();
        lastOverlayHeight = boxHeight + 2 + boxY;
    }

    public static int getOverlayHeight() {
        return lastOverlayHeight;
    }

    private String formatDelta(long delta, long currentQuantity) {
        String deltaText = formatDelta(delta);

        // The sync carries the post-poll quantity, so subtract the signed delta to recover the prior total.
        double previousQuantity = currentQuantity - (double) delta;
        if (previousQuantity <= 0.0D) previousQuantity = delta;  // 100% change if previous = 0

        double percent = Math.abs((double) delta) * 100.0D / previousQuantity;
        if (!Double.isFinite(percent) || percent <= 0.1D) return deltaText;

        return deltaText + " (" + formatPercent(percent) + "%)";
    }

    private String formatDelta(long delta) {
        String prefix = delta > 0 ? "+" : "-";
        return prefix + ReadableNumberConverter.INSTANCE.toWideReadableForm(Math.abs(delta));
    }

    private String formatPercent(double percent) {
        String formattedPercent = String.format(Locale.US, "%.1f", percent);
        if (formattedPercent.endsWith(".0")) return formattedPercent.substring(0, formattedPercent.length() - 2);

        return formattedPercent;
    }

    private void drawScaledText(Minecraft mc, String text, int x, float y, int color, float scale) {
        if (scale == 1.0F) {
            mc.fontRenderer.drawStringWithShadow(text, x, y, color);
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        mc.fontRenderer.drawStringWithShadow(text, 0.0F, 0.0F, color);
        GlStateManager.popMatrix();
    }

    private ItemStack findDisplayedMonitor(Minecraft mc) {
        ItemStack mainHand = mc.player.getHeldItemMainhand();
        if (isRemoteMonitor(mainHand)) return mainHand;

        ItemStack offHand = mc.player.getHeldItemOffhand();
        if (isRemoteMonitor(offHand)) return offHand;

        if (Loader.isModLoaded("baubles")) {
            ItemStack bauble = findDisplayedMonitorInBaubles(mc);
            if (!bauble.isEmpty()) return bauble;
        }

        return ItemStack.EMPTY;
    }

    private boolean isRemoteMonitor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemRemoteStorageMonitor;
    }

    @Optional.Method(modid = "baubles")
    private ItemStack findDisplayedMonitorInBaubles(Minecraft mc) {
        baubles.api.cap.IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(mc.player);
        for (int slot = 0; slot < baubles.getSlots(); slot++) {
            ItemStack stack = baubles.getStackInSlot(slot);
            if (isRemoteMonitor(stack)) return stack;
        }

        return ItemStack.EMPTY;
    }
}