package com.ae2powertools.features.locator;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
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
import com.ae2powertools.client.TrackedLocationRenderer;
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
            String distStr = TrackedLocationRenderer.formatDistance(distance);
            // Format: "TypeName [x, y, z]: distance"
            String posStr = loc.getCoordStringNoDim();
            lines.add(entry.typeName + " " + posStr + ": " + distStr);
            colors.add(LOCATOR_COLOR);
        }

        if (lines.isEmpty()) return;

        int boxY = TrackedLocationRenderer.getExternalPadding() + ScannerRenderer.getOverlayHeight();
        lastOverlayHeight = TrackedLocationRenderer.drawOverlayBox(mc, boxY, lines, colors);
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
        float partialTicks = event.getPartialTicks();

        List<BlockPos> positions = new ArrayList<>();

        for (ComponentLocationClient loc : selected) {
            if (loc.dimension == playerDim) positions.add(loc.pos);
        }

        TrackedLocationRenderer.renderWorldTargets(player, positions, LOCATOR_COLOR, partialTicks);
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
}
