package com.ae2powertools.features.scanner.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.client.BlockHighlightRenderer;
import com.ae2powertools.client.DirectionArrowRenderer;
import com.ae2powertools.client.HudOverlayManager;
import com.ae2powertools.features.scanner.gui.IssueOverlayPosition;
import com.ae2powertools.features.scanner.gui.ScannerHudLine;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;
import com.ae2powertools.items.ItemNetworkHealthScanner;


/**
 * Client-side renderer for scanner overlays and directional arrows.
 */
@SideOnly(Side.CLIENT)
public class ScannerRenderer implements HudOverlayManager.HudOverlayProvider {

    // ========== Distance Limits ==========
    private static final double WIREFRAME_MAX_DISTANCE = 50.0;  // Max distance for wireframe rendering
    private static final double FLOATING_TEXT_MAX_DISTANCE = 10.0;  // Max distance for floating text

    // ========== World Text Rendering ==========
    private static final float WORLD_TEXT_SCALE = 0.02f;
    private static int lastOverlayHeight;

    @Override
    public boolean isActive(Minecraft mc) {
        if (mc.player == null || mc.world == null) return false;

        // Read overlay state from the held scanner
        ItemStack scanner = getHeldScanner(mc);
        if (scanner.isEmpty() || !ItemNetworkHealthScanner.isOverlayEnabled(scanner)) return false;

        return ScannerClientState.hasSession(ItemNetworkHealthScanner.getDeviceId(scanner));
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
        ScannerSession session = getHeldSession(mc);
        if (session == null || mc.player == null) return new ArrayList<>();

        // Build lines for selected issues in the active tab
        ScannerViewContext viewContext = ScannerViewContext.of(mc.player.dimension, mc.player.getPosition());
        List<ScannerHudLine> hudLines = session.buildHudLines(viewContext);

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (ScannerHudLine line : hudLines) {
            lines.add(line.getText());
            colors.add(line.getColor());
        }

        return HudOverlayManager.HudOverlayLine.textLines(lines, colors);
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


        // Read the scanner session stored for the held scanner
        ItemStack scanner = getHeldScanner(mc);
        if (scanner.isEmpty()) return;


        ScannerSession session = ScannerClientState.getSession(ItemNetworkHealthScanner.getDeviceId(scanner));
        if (session == null) return;

        List<IssueOverlayPosition> issuePositions = session.buildIssueOverlay();
        if (issuePositions.isEmpty()) return;

        int playerDimension = player.dimension;
        BlockPos playerPosition = player.getPosition();
        float partialTicks = event.getPartialTicks();
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        // Draw nearby block outlines and floating text
        renderBlockOutlines(issuePositions, playerDimension, playerPosition, playerX, playerY, playerZ);
        renderFloatingText(mc, player, issuePositions, playerDimension, playerPosition,
            playerX, playerY, playerZ, partialTicks);

        // Draw direction arrows while the scanner overlay is enabled
        if (ItemNetworkHealthScanner.isOverlayEnabled(scanner)) {
            renderDirectionArrows(player, issuePositions, playerDimension, playerPosition, partialTicks);
        }
    }

    /**
     * Draw block outlines for selected issues in the player's current dimension.
     */
    private void renderBlockOutlines(List<IssueOverlayPosition> issuePositions, int playerDimension,
            BlockPos playerPosition, double playerX, double playerY, double playerZ) {

        boolean hasBlockIssue = false;
        for (IssueOverlayPosition issue : issuePositions) {
            if (issue.hasBlockOutline() && issue.getDimension() == playerDimension) {
                hasBlockIssue = true;
                break;
            }
        }
        if (!hasBlockIssue) return;

        // Configure line rendering in camera-relative coordinates
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(-playerX, -playerY, -playerZ);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.glLineWidth(3.0F);

            for (IssueOverlayPosition issue : issuePositions) {
                if (!issue.hasBlockOutline() || issue.getDimension() != playerDimension) continue;
                if (issue.getDistanceFrom(playerPosition) > WIREFRAME_MAX_DISTANCE) continue;

                BlockHighlightRenderer.renderBlockOutline(issue.getPosition(),
                    issue.getRed(), issue.getGreen(), issue.getBlue(), 0.8f);
            }
        } finally {
            // Restore the render state used by the scanner outlines
            GlStateManager.glLineWidth(1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    /**
     * Draw direction arrows for selected issues outside the nearby outline range.
     */
    private void renderDirectionArrows(EntityPlayer player, List<IssueOverlayPosition> issuePositions,
            int playerDimension, BlockPos playerPosition, float partialTicks) {
        for (IssueOverlayPosition issue : issuePositions) {
            if (issue.getDimension() != playerDimension) continue;

            double distance = issue.getDistanceFrom(playerPosition);
            if (!issue.isAlwaysShowArrow() && distance <= WIREFRAME_MAX_DISTANCE) continue;

            // Chunk positions use a vertical-agnostic arrow
            if (issue.isYAgnosticArrow()) {
                DirectionArrowRenderer.drawDirectionArrowYAgnostic(player, issue.getPosition(), issue.getArrowColor(),
                    distance, partialTicks);
            } else {
                DirectionArrowRenderer.drawDirectionArrow(player, issue.getPosition(), issue.getArrowColor(),
                    distance, partialTicks);
            }
        }
    }

    /**
     * Draw floating detail text above nearby chokepoints.
     */
    private void renderFloatingText(Minecraft mc, EntityPlayer player, List<IssueOverlayPosition> issuePositions,
            int playerDimension, BlockPos playerPosition, double playerX, double playerY, double playerZ,
            float partialTicks) {
        for (IssueOverlayPosition issue : issuePositions) {
            if (issue.getDimension() != playerDimension || issue.getFloatingLines().isEmpty()) continue;
            if (issue.getDistanceFrom(playerPosition) > FLOATING_TEXT_MAX_DISTANCE) continue;

            renderFloatingText(mc, player, issue, playerX, playerY, playerZ, partialTicks);
        }
    }

    /**
     * Draw channel demand and flow details above a chokepoint.
     */
    private void renderFloatingText(Minecraft mc, EntityPlayer player, IssueOverlayPosition issue,
            double playerX, double playerY, double playerZ, float partialTicks) {
        BlockPos pos = issue.getPosition();
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;

        GlStateManager.pushMatrix();
        try {
            // Position text above the block and rotate it toward the player
            GlStateManager.translate(pos.getX() + 0.5 - playerX, pos.getY() + 1.5 - playerY,
                pos.getZ() + 0.5 - playerZ);
            GlStateManager.rotate(-yaw, 0, 1, 0);
            GlStateManager.rotate(pitch, 1, 0, 0);
            GlStateManager.scale(-WORLD_TEXT_SCALE, -WORLD_TEXT_SCALE, WORLD_TEXT_SCALE);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();

            // The first line shows demand and capacity, followed by connection demand
            int lineY = 0;
            for (int index = 0; index < issue.getFloatingLines().size(); index++) {
                String line = issue.getFloatingLines().get(index);
                int color = index == 0 ? issue.getArrowColor() | 0xFF000000 : 0xFFAAAAAA;
                mc.fontRenderer.drawStringWithShadow(line, -mc.fontRenderer.getStringWidth(line) / 2.0f, lineY, color);
                lineY += mc.fontRenderer.FONT_HEIGHT + (index == 0 ? 2 : 1);
            }
        } finally {
            // Restore the render state used by floating text
            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private ScannerSession getHeldSession(Minecraft mc) {
        ItemStack scanner = getHeldScanner(mc);
        return scanner.isEmpty() ? null : ScannerClientState.getSession(ItemNetworkHealthScanner.getDeviceId(scanner));
    }

    /**
     * Return the Network Health Scanner held by the player.
     */
    private ItemStack getHeldScanner(Minecraft mc) {
        return mc.player == null ? ItemStack.EMPTY : ItemNetworkHealthScanner.getHeldScanner(mc.player);
    }

    /**
     * Return the height drawn by the scanner overlay, including its border and padding.
     * Other HUD renderers use this value to draw below the scanner overlay.
     * Return 0 when the scanner overlay is not drawn.
     */
    public static int getOverlayHeight() {
        return lastOverlayHeight;
    }
}
