package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
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
import com.ae2powertools.features.scanner.ScannerClientState.ChunkLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.ChokeLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.ConnectionFlowClient;
import com.ae2powertools.features.scanner.ScannerClientState.LoopLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.MissingDeviceClient;
import com.ae2powertools.features.scanner.ScannerClientState.Tab;
import com.ae2powertools.items.ItemNetworkHealthScanner;


/**
 * Client-side renderer for scanner overlays and direction arrows.
 */
@SideOnly(Side.CLIENT)
public class ScannerRenderer {

    // ========== Distance Limits ==========
    private static final double WIREFRAME_MAX_DISTANCE = 50.0;  // Max distance for wireframe rendering
    private static final double FLOATING_TEXT_MAX_DISTANCE = 10.0;  // Max distance for floating text

    // ========== Overlay Constants ==========
    private static final int PADDING_EXTERNAL = 5;
    private static final int PADDING_INTERNAL = 4;
    private static final int LINE_SPACING = 2;

    // ========== World Text Rendering ==========
    private static final float WORLD_TEXT_SCALE = 0.02f;

    // Loop color (red)
    private static final int LOOP_COLOR = 0xFF4444;
    // Chunk color (orange)
    private static final int CHUNK_COLOR = 0xFFAA00;
    // Missing channel color (red/magenta)
    private static final int MISSING_COLOR = 0xFF6666;
    // Chokepoint color (cyan/blue)
    private static final int CHOKE_COLOR = 0x66AAFF;
    // Fatal error color (strong red)
    private static final int FATAL_COLOR = 0xFF4444;
    // Pattern issue color (golden)
    private static final int PATTERN_COLOR = 0xD8B45A;

    /**
     * Render the scanner overlay on the HUD for info about the positions (top-left corner).
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.gameSettings.showDebugInfo) return;

        // Get held scanner and check its overlay enabled state
        ItemStack heldScanner = getHeldScanner(mc);
        if (heldScanner.isEmpty()) { lastOverlayHeight = 0; return; }
        if (!ItemNetworkHealthScanner.isOverlayEnabled(heldScanner)) { lastOverlayHeight = 0; return; }

        long deviceId = ItemNetworkHealthScanner.getDeviceId(heldScanner);
        ScannerClientState.setActiveDeviceId(deviceId);

        if (!ScannerClientState.hasActiveSession()) { lastOverlayHeight = 0; return; }

        Tab currentTab = ScannerClientState.getCurrentTab();
        BlockPos playerPos = mc.player.getPosition();
        int playerDim = mc.player.dimension;

        // Build display lines based on current tab
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (currentTab == Tab.LOOPS) {
            List<LoopLocationClient> selected = ScannerClientState.getSelectedLoops();

            for (LoopLocationClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                double distance = loc.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String posStr = String.format("[%d, %d, %d]", loc.pos.getX(), loc.pos.getY(), loc.pos.getZ());
                lines.add(loc.description + " " + posStr + ": " + distanceStr);
                colors.add(LOOP_COLOR);
            }
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            List<ChunkLocationClient> selected = ScannerClientState.getSelectedChunks();

            for (ChunkLocationClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                double distance = loc.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String text = I18n.format("gui.ae2powertools.scanner.chunk_entry", loc.chunkX, loc.chunkZ)
                    + ": " + distanceStr;
                lines.add(text);
                colors.add(CHUNK_COLOR);
            }
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            List<MissingDeviceClient> selected = ScannerClientState.getSelectedMissing();

            for (MissingDeviceClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                double distance = loc.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String posStr = String.format("[%d, %d, %d]", loc.pos.getX(), loc.pos.getY(), loc.pos.getZ());
                lines.add(loc.getDisplayName() + " " + posStr + ": " + distanceStr);
                colors.add(MISSING_COLOR);
            }
        } else if (currentTab == Tab.CHOKEPOINTS) {
            List<ChokeLocationClient> selected = ScannerClientState.getSelectedChokes();

            for (ChokeLocationClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                double distance = loc.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String posStr = String.format("[%d, %d, %d]", loc.pos.getX(), loc.pos.getY(), loc.pos.getZ());
                String channelStr = loc.getChannelString();
                int excess = loc.getExcessChannels();
                String excessStr = excess > 0 ? " (-" + excess + ")" : "";
                lines.add(loc.description + " " + posStr + " " + channelStr + excessStr + ": " + distanceStr);
                colors.add(CHOKE_COLOR);
            }
        } else if (currentTab == Tab.PATTERNS) {
            List<PatternIssue> selected = ScannerClientState.getSelectedPatternIssues();

            for (PatternIssue issue : selected) {
                if (issue.getDimension() != playerDim) continue;

                double distance = issue.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String posStr = String.format("[%d, %d, %d]", issue.getPos().getX(), issue.getPos().getY(),
                    issue.getPos().getZ());
                lines.add(ScannerClientState.getPatternIssueDisplayText(issue) + " " + posStr + ": " + distanceStr);
                colors.add(PATTERN_COLOR);
            }
        } else {
            List<FatalNetworkError> selected = ScannerClientState.getSelectedFatalErrors();

            for (FatalNetworkError error : selected) {
                if (error.getDimension() != playerDim) continue;

                double distance = error.getDistanceFrom(playerPos);
                String distanceStr = formatDistance(distance);
                String posStr = String.format("[%d, %d, %d]", error.getPos().getX(), error.getPos().getY(),
                    error.getPos().getZ());
                lines.add(ScannerClientState.getFatalErrorDisplayText(error) + " " + posStr + ": " + distanceStr);
                colors.add(FATAL_COLOR);
            }
        }

        if (lines.isEmpty()) { lastOverlayHeight = 0; return; }

        // Calculate dimensions
        int lineHeight = mc.fontRenderer.FONT_HEIGHT;
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));

        int boxW = maxWidth + PADDING_INTERNAL * 2 + 8;  // Extra 8 for color indicator
        int boxH = lines.size() * lineHeight + (lines.size() - 1) * LINE_SPACING + PADDING_INTERNAL * 2;

        // Position (top left)
        int boxX = PADDING_EXTERNAL;
        int boxY = PADDING_EXTERNAL;

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

        // Record the total overlay height (box + border + external padding) for other renderers to offset
        lastOverlayHeight = boxH + 2 + PADDING_EXTERNAL;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.world == null) return;

        // Get the held scanner and set its device ID as active
        ItemStack heldScanner = getHeldScanner(mc);
        if (heldScanner.isEmpty()) return;

        long deviceId = ItemNetworkHealthScanner.getDeviceId(heldScanner);
        ScannerClientState.setActiveDeviceId(deviceId);

        if (!ScannerClientState.hasActiveSession()) return;

        int playerDim = player.dimension;
        float partialTicks = event.getPartialTicks();
        Tab currentTab = ScannerClientState.getCurrentTab();
        BlockPos playerPos = player.getPosition();

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        if (currentTab == Tab.LOOPS) {
            renderLoopLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks);
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            renderChunkLocations(mc, player, playerDim, playerPos, partialTicks);
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            renderMissingLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks);
        } else if (currentTab == Tab.CHOKEPOINTS) {
            renderChokeLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks);
        } else if (currentTab == Tab.PATTERNS) {
            renderPatternLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks);
        } else {
            renderFatalLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks);
        }
    }

    /**
     * Render loop location markers.
     */
    private void renderLoopLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        renderBlockIssueLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks,
            ScannerClientState.getSelectedLoops(), 1.0f, 0.27f, 0.27f, LOOP_COLOR);
    }

    /**
     * Render chunk location markers.
     */

    private void renderChunkLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            float partialTicks) {
        List<ChunkLocationClient> selected = ScannerClientState.getSelectedChunks();

        // Render direction arrows pointing to chunk centers (only in current dimension)
        ItemStack heldScanner = getHeldScanner(mc);
        if (ItemNetworkHealthScanner.isOverlayEnabled(heldScanner)) {
            for (ChunkLocationClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                BlockPos centerPos = loc.getCenterPos();
                double distance = loc.getDistanceFrom(playerPos);
                DirectionArrowRenderer.drawDirectionArrowYAgnostic(player, centerPos, CHUNK_COLOR, distance, partialTicks);
            }
        }
    }

    /**
     * Render missing channel device location markers.
     */

    private void renderMissingLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        renderBlockIssueLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks,
            ScannerClientState.getSelectedMissing(), 1.0f, 0.4f, 0.4f, MISSING_COLOR);
    }

    /**
     * Render fatal error location markers.
     */
    private void renderFatalLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        renderBlockIssueLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks,
            ScannerClientState.getSelectedFatalErrors(), 1.0f, 0.27f, 0.27f, FATAL_COLOR);
    }

    /**
     * Render pattern issue location markers.
     */
    private void renderPatternLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        renderBlockIssueLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks,
            ScannerClientState.getSelectedPatternIssues(), 0.85f, 0.71f, 0.35f, PATTERN_COLOR);
    }

    /**
     * Render chokepoint location markers with channel info.
     */

    private void renderChokeLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        List<ChokeLocationClient> selected = ScannerClientState.getSelectedChokes();
        if (selected.isEmpty()) return;

        renderBlockIssueLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks,
            selected, 0.4f, 0.67f, 1.0f, CHOKE_COLOR);

        // Render floating text for nearby chokepoints
        for (ChokeLocationClient loc : selected) {
            if (loc.dimension != playerDim) continue;

            double distance = loc.getDistanceFrom(playerPos);
            if (distance <= FLOATING_TEXT_MAX_DISTANCE) {
                renderChokeFloatingText(mc, player, loc, playerX, playerY, playerZ, partialTicks);
            }
        }

        // Render direction arrows (for locations beyond wireframe distance)
        ItemStack heldScanner = getHeldScanner(mc);
        if (ItemNetworkHealthScanner.isOverlayEnabled(heldScanner)) {
            for (ChokeLocationClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                double distance = loc.getDistanceFrom(playerPos);
                if (distance > WIREFRAME_MAX_DISTANCE) {
                    DirectionArrowRenderer.drawDirectionArrow(player, loc.pos, CHOKE_COLOR, distance, partialTicks);
                }
            }
        }
    }

    /**
     * Wireframe render for block-related issues with directional arrows for distant ones.
     */
    private <T extends AbstractLocation> void renderBlockIssueLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks, List<T> selected,
            float red, float green, float blue, int arrowColor) {
        if (selected.isEmpty()) return;

        // wireframe render for nearby locations
        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(3.0F);

        for (T entry : selected) {
            if (entry.dimension != playerDim) continue;

            double distance = entry.getDistanceFrom(playerPos);
            if (distance <= WIREFRAME_MAX_DISTANCE) {
                BlockHighlightRenderer.renderBlockOutline(entry.pos, red, green, blue, 0.8f);
            }
        }

        // arrow render for distant locations
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();

        ItemStack heldScanner = getHeldScanner(mc);
        if (!ItemNetworkHealthScanner.isOverlayEnabled(heldScanner)) return;

        for (T entry : selected) {
            if (entry.dimension != playerDim) continue;

            double distance = entry.getDistanceFrom(playerPos);
            if (distance > WIREFRAME_MAX_DISTANCE) {
                DirectionArrowRenderer.drawDirectionArrow(player, entry.pos, arrowColor, distance, partialTicks);
            }
        }
    }

    /**
     * Render floating text above a chokepoint showing channel info.
     */

    private void renderChokeFloatingText(Minecraft mc, EntityPlayer player, ChokeLocationClient loc,
            double playerX, double playerY, double playerZ, float partialTicks) {
        BlockPos pos = loc.pos;

        // Position text above the block
        double textX = pos.getX() + 0.5;
        double textY = pos.getY() + 1.5;
        double textZ = pos.getZ() + 0.5;

        // Main text: demanded/capacity
        String mainText = loc.demandedChannels + "/" + loc.capacity;

        float playerYaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float playerPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(textX - playerX, textY - playerY, textZ - playerZ);

        // Billboard rotation - face the player
        GlStateManager.rotate(-playerYaw, 0, 1, 0);
        GlStateManager.rotate(playerPitch, 1, 0, 0);
        GlStateManager.scale(-WORLD_TEXT_SCALE, -WORLD_TEXT_SCALE, WORLD_TEXT_SCALE);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        // Draw main text centered
        int mainWidth = mc.fontRenderer.getStringWidth(mainText);
        mc.fontRenderer.drawStringWithShadow(mainText, -mainWidth / 2.0f, 0, CHOKE_COLOR | 0xFF000000);

        // Draw connection flow numbers around the main text
        int flowY = mc.fontRenderer.FONT_HEIGHT + 2;
        for (ConnectionFlowClient flow : loc.connectionFlows) {
            String flowText = String.valueOf(flow.demandedChannels);
            int flowWidth = mc.fontRenderer.getStringWidth(flowText);

            // Simple layout: stack flows below main text
            mc.fontRenderer.drawStringWithShadow(flowText, -flowWidth / 2.0f, flowY, 0xFFAAAAAA);
            flowY += mc.fontRenderer.FONT_HEIGHT + 1;
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }

    /**
     * Format distance for display.
     */
    private String formatDistance(double distance) {
        if (distance < 1000) {
            return String.format("%.0fm", distance);
        } else {
            return String.format("%.1fkm", distance / 1000);
        }
    }

    /**
     * Check if the player is holding a Network Health Scanner in either hand.
     * Returns the scanner ItemStack if held, or ItemStack.EMPTY if not.
     */
    private ItemStack getHeldScanner(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;

        ItemStack mainHand = mc.player.getHeldItem(EnumHand.MAIN_HAND);
        if (mainHand.getItem() == ItemRegistry.NETWORK_HEALTH_SCANNER) return mainHand;

        ItemStack offHand = mc.player.getHeldItem(EnumHand.OFF_HAND);
        if (offHand.getItem() == ItemRegistry.NETWORK_HEALTH_SCANNER) return offHand;

        return ItemStack.EMPTY;
    }

    /**
     * Returns the height of the scanner overlay currently being drawn (including border + padding).
     * Other overlay renderers can use this to offset themselves below the scanner overlay.
     * Returns 0 if the scanner overlay is not being shown.
     */
    public static int getOverlayHeight() {
        return lastOverlayHeight;
    }

    // Tracks the last rendered overlay height so the NCL renderer can offset below it
    private static int lastOverlayHeight = 0;
}
