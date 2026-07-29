package com.ae2powertools.features.locator;

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

import com.ae2powertools.client.HudOverlayManager;
import com.ae2powertools.client.TrackedLocationRenderer;
import com.ae2powertools.features.locator.LocatorClientState.ComponentLocationClient;
import com.ae2powertools.features.locator.LocatorClientState.SelectedLocationWithType;
import com.ae2powertools.items.ItemNetworkComponentLocator;


/**
 * Client-side renderer for Network Component Locator overlays.
 * Renders block outlines in the world and a HUD overlay for selected component locations.
 * Only renders when the locator item is held and has selected locations.
 */
@SideOnly(Side.CLIENT)
public class LocatorRenderer implements HudOverlayManager.HudOverlayProvider {

    // Locator color (green/teal)
    private static final int LOCATOR_COLOR = 0x44AAFF;

    private static int lastOverlayHeight = 0;

    private List<SelectedLocationWithType> selected = new ArrayList<>();

    @Override
    public boolean isActive(Minecraft mc) {
        if (mc.player == null || mc.world == null) return false;

        ItemStack heldLocator = getHeldLocator(mc);
        if (heldLocator.isEmpty()) return false;
        if (!ItemNetworkComponentLocator.isOverlayEnabled(heldLocator)) return false;

        long deviceId = ItemNetworkComponentLocator.getDeviceId(heldLocator);
        LocatorClientState.setActiveDeviceId(deviceId);

        // Show overlay for all selected locations with their type names (even when not in detail view)
        selected = LocatorClientState.getSelectedLocationsWithTypes();
        return !selected.isEmpty();
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
        long deviceId = ItemNetworkComponentLocator.getDeviceId(getHeldLocator(mc));
        LocatorClientState.setActiveDeviceId(deviceId);

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

        return HudOverlayManager.HudOverlayLine.textLines(lines, colors);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public void onOverlayRendered(int renderedHeight) {
        lastOverlayHeight = renderedHeight;
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

        return ItemNetworkComponentLocator.getHeldLocator(mc.player);
    }
}
