package com.ae2powertools.features.locator;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.client.BlockHighlightRenderer;
import com.ae2powertools.client.DirectionArrowRenderer;
import com.ae2powertools.features.locator.LocatorClientState.ComponentLocationClient;
import com.ae2powertools.features.locator.LocatorClientState.SelectedLocationWithType;
import com.ae2powertools.features.scanner.ScannerRenderer;
import com.ae2powertools.items.ItemNetworkComponentLocator;


/**
 * Client-side renderer for Network Component Locator overlays.
 * Renders block outlines in the world and a HUD overlay for selected component locations.
 * Only renders when the locator item is held and has selected locations.
 */
@SideOnly(Side.CLIENT)
public class LocatorRenderer {

    // ========== Distance Limits ==========
    private static final double WIREFRAME_MAX_DISTANCE = 50.0;

    // ========== Overlay Constants ==========
    private static final int PADDING_EXTERNAL = 5;
    private static final int PADDING_INTERNAL = 4;
    private static final int LINE_SPACING = 2;

    // Locator color (green/teal)
    private static final int LOCATOR_COLOR = 0x44AAFF;

    private static int lastOverlayHeight = 0;

    /**
     * Render the HUD overlay showing selected locations (top-left corner).
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        lastOverlayHeight = 0;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;

        ItemStack heldLocator = getHeldLocator(mc);
        if (heldLocator.isEmpty()) return;
        if (!ItemNetworkComponentLocator.isOverlayEnabled(heldLocator)) return;

        long deviceId = ItemNetworkComponentLocator.getDeviceId(heldLocator);
        LocatorClientState.setActiveDeviceId(deviceId);

        // Show overlay for all selected locations with their type names (even when not in detail view)
        List<SelectedLocationWithType> selected = LocatorClientState.getSelectedLocationsWithTypes();
        if (selected.isEmpty()) return;

        BlockPos playerPos = mc.player.getPosition();
        int playerDim = mc.player.dimension;

        // Build display lines for current-dimension entries
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        for (SelectedLocationWithType entry : selected) {
            ComponentLocationClient loc = entry.location;
            if (loc.dimension != playerDim) continue;

            double distance = loc.getDistanceFrom(playerPos);
            String distStr = formatDistance(distance);
            // Format: "TypeName [x, y, z]: distance"
            String posStr = loc.getCoordStringNoDim();
            lines.add(entry.typeName + " " + posStr + ": " + distStr);
            colors.add(LOCATOR_COLOR);
        }

        if (lines.isEmpty()) return;

        // Calculate dimensions
        int lineHeight = mc.fontRenderer.FONT_HEIGHT;
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));

        int boxW = maxWidth + PADDING_INTERNAL * 2 + 8;
        int boxH = lines.size() * lineHeight + (lines.size() - 1) * LINE_SPACING + PADDING_INTERNAL * 2;

        // Position (top left, offset below scanner overlay if active)
        int boxX = PADDING_EXTERNAL;
        int scannerOverlayOffset = ScannerRenderer.getOverlayHeight();
        int boxY = PADDING_EXTERNAL + scannerOverlayOffset;

        // Draw box with border
        int bgColor = 0xC0101010;
        int borderColor = 0xFF404040;

        Gui.drawRect(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, borderColor);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, bgColor);

        // Draw text with color indicators
        int textX = boxX + PADDING_INTERNAL;
        int textY = boxY + PADDING_INTERNAL;

        for (int i = 0; i < lines.size(); i++) {
            int color = colors.get(i) | 0xFF000000;
            Gui.drawRect(textX, textY + 1, textX + 4, textY + lineHeight - 1, color);
            mc.fontRenderer.drawStringWithShadow(lines.get(i), textX + 8, textY, 0xFFFFFF);
            textY += lineHeight + LINE_SPACING;
        }

        lastOverlayHeight = boxH + 2 + PADDING_EXTERNAL;
    }

    public static int getOverlayHeight() {
        return lastOverlayHeight;
    }

    /**
     * Render block outlines in the world for selected locations.
     * For locations within WIREFRAME_MAX_DISTANCE, renders block outlines.
     * For locations beyond that distance, renders direction arrows.
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) return;

        ItemStack heldLocator = getHeldLocator(mc);
        if (heldLocator.isEmpty()) return;
        if (!ItemNetworkComponentLocator.isOverlayEnabled(heldLocator)) return;

        long deviceId = ItemNetworkComponentLocator.getDeviceId(heldLocator);
        LocatorClientState.setActiveDeviceId(deviceId);

        // Show overlay for all selected locations
        List<ComponentLocationClient> selected = LocatorClientState.getSelectedLocations();
        if (selected.isEmpty()) return;

        int playerDim = player.dimension;
        BlockPos playerPos = player.getPosition();
        float partialTicks = event.getPartialTicks();

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        float r = ((LOCATOR_COLOR >> 16) & 0xFF) / 255.0f;
        float g = ((LOCATOR_COLOR >> 8) & 0xFF) / 255.0f;
        float b = (LOCATOR_COLOR & 0xFF) / 255.0f;

        // Separate locations into near (wireframe) and far (arrow)
        List<ComponentLocationClient> nearLocations = new ArrayList<>();
        List<ComponentLocationClient> farLocations = new ArrayList<>();

        for (ComponentLocationClient loc : selected) {
            if (loc.dimension != playerDim) continue;

            double distance = loc.getDistanceFrom(playerPos);
            if (distance <= WIREFRAME_MAX_DISTANCE) {
                nearLocations.add(loc);
            } else {
                farLocations.add(loc);
            }
        }

        // Render block outlines for near locations
        if (!nearLocations.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-playerX, -playerY, -playerZ);

            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.glLineWidth(3.0F);

            for (ComponentLocationClient loc : nearLocations) {
                BlockHighlightRenderer.renderBlockOutline(loc.pos, r, g, b, 0.8f);
            }

            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }

        // Render direction arrows for far locations
        for (ComponentLocationClient loc : farLocations) {
            double distance = loc.getDistanceFrom(playerPos);
            DirectionArrowRenderer.drawDirectionArrow(player, loc.pos, LOCATOR_COLOR, distance, partialTicks, 1.0f);
        }
    }

    /**
     * Find the held Network Component Locator item, checking both hands.
     */
    private ItemStack getHeldLocator(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;

        ItemStack mainHand = mc.player.getHeldItem(EnumHand.MAIN_HAND);
        if (!mainHand.isEmpty() && mainHand.getItem() == ItemRegistry.NETWORK_COMPONENT_LOCATOR) {
            return mainHand;
        }

        ItemStack offHand = mc.player.getHeldItem(EnumHand.OFF_HAND);
        if (!offHand.isEmpty() && offHand.getItem() == ItemRegistry.NETWORK_COMPONENT_LOCATOR) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }

    private static String formatDistance(double distance) {
        if (distance < 10) return String.format("%.1fm", distance);
        if (distance < 1000) return String.format("%.0fm", distance);

        return String.format("%.1fkm", distance / 1000.0);
    }
}
