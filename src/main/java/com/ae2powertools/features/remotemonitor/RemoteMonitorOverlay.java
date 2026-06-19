package com.ae2powertools.features.remotemonitor;

import java.util.ArrayList;
import java.util.List;

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

import com.ae2powertools.features.locator.LocatorRenderer;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceRenderer;
import com.ae2powertools.features.scanner.ScannerRenderer;
import com.ae2powertools.items.ItemRemoteStorageMonitor;


/**
 * Client-side HUD overlay for the Remote Storage Monitor.
 * Renders non-zero quantity deltas for the first held or worn monitor device.
 */
@SideOnly(Side.CLIENT)
public class RemoteMonitorOverlay {

    // TODO: make some of these configurable, to give players some control over the overlay
    private static final int PADDING_EXTERNAL = 5;
    private static final int PADDING_INTERNAL = 4;
    private static final int LINE_SPACING = 2;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 4;
    private static final int COLOR_GAIN = 0x66FF66;
    private static final int COLOR_LOSS = 0xFF6666;

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

        RemoteMonitorClientState.DeviceState state = RemoteMonitorClientState.getOrCreateState(deviceId);
        List<MonitoredResource> resources = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        MonitoredResource[] configured = state.getResources();
        long[] deltas = state.getDeltas();
        for (int slotIndex = 0; slotIndex < configured.length && slotIndex < deltas.length; slotIndex++) {
            MonitoredResource resource = configured[slotIndex];
            long delta = deltas[slotIndex];
            if (resource == null || delta == 0) continue;

            resources.add(resource);
            lines.add(formatDelta(delta));
            colors.add(delta > 0 ? COLOR_GAIN : COLOR_LOSS);

            // TODO: add the %total of the delta, as "+5k (10%)" for a resource that was at 50k before delta
        }

        if (lines.isEmpty()) return;

        int lineHeight = Math.max(mc.fontRenderer.FONT_HEIGHT, ICON_SIZE);
        int maxTextWidth = 0;
        for (String line : lines) {
            maxTextWidth = Math.max(maxTextWidth, mc.fontRenderer.getStringWidth(line));
        }

        int boxX = PADDING_EXTERNAL;
        int boxY = PADDING_EXTERNAL + ScannerRenderer.getOverlayHeight() + LocatorRenderer.getOverlayHeight();

        int iconX = boxX + PADDING_INTERNAL;
        int textX = iconX + ICON_SIZE + ICON_TEXT_GAP;
        int lineY = boxY + PADDING_INTERNAL;

        for (int i = 0; i < lines.size(); i++) {
            MonitoredResourceRenderer.renderIcon(resources.get(i), iconX, lineY, ICON_SIZE);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.fontRenderer.drawStringWithShadow(lines.get(i), textX, lineY + (lineHeight - mc.fontRenderer.FONT_HEIGHT) / 2,
                colors.get(i));
            lineY += lineHeight + LINE_SPACING;
        }

        int boxHeight = PADDING_INTERNAL * 2 + lines.size() * lineHeight + (lines.size() - 1) * LINE_SPACING;
        lastOverlayHeight = boxHeight + 2 + PADDING_EXTERNAL;
    }

    public static int getOverlayHeight() {
        return lastOverlayHeight;
    }

    private String formatDelta(long delta) {
        String prefix = delta > 0 ? "+" : "-";
        return prefix + ReadableNumberConverter.INSTANCE.toWideReadableForm(Math.abs(delta));
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