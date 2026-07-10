package com.ae2powertools.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;


/**
 * Shared renderer for top-left location lists and near/far target indicators.
 */
public final class TrackedLocationRenderer {

    public static final double WIREFRAME_MAX_DISTANCE = 50.0;

    private static final int PADDING_EXTERNAL = 5;
    private static final int PADDING_INTERNAL = 4;
    private static final int LINE_SPACING = 2;

    private TrackedLocationRenderer() {}

    public static int drawOverlayBox(Minecraft mc, int boxY, List<String> lines, List<Integer> colors) {
        if (mc == null || mc.fontRenderer == null || lines.isEmpty()) return 0;

        int lineHeight = mc.fontRenderer.FONT_HEIGHT;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));
        }

        int boxW = maxWidth + PADDING_INTERNAL * 2 + 8;
        int boxH = lines.size() * lineHeight + (lines.size() - 1) * LINE_SPACING + PADDING_INTERNAL * 2;
        int boxX = PADDING_EXTERNAL;

        Gui.drawRect(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF404040);
        Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, 0xC0101010);

        int textX = boxX + PADDING_INTERNAL;
        int textY = boxY + PADDING_INTERNAL;
        for (int i = 0; i < lines.size(); i++) {
            int color = colors.get(i) | 0xFF000000;
            Gui.drawRect(textX, textY + 1, textX + 4, textY + lineHeight - 1, color);
            mc.fontRenderer.drawStringWithShadow(lines.get(i), textX + 8, textY, 0xFFFFFF);
            textY += lineHeight + LINE_SPACING;
        }

        return boxH + 2 + PADDING_EXTERNAL;
    }

    public static void renderWorldTargets(EntityPlayer player, List<BlockPos> positions, int color, float partialTicks) {
        if (player == null || positions.isEmpty()) return;

        BlockPos playerPos = player.getPosition();
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        List<BlockPos> nearPositions = new ArrayList<>();
        List<BlockPos> farPositions = new ArrayList<>();

        for (BlockPos pos : positions) {
            double distance = pos.distanceSq(playerPos);
            if (distance <= WIREFRAME_MAX_DISTANCE * WIREFRAME_MAX_DISTANCE) {
                nearPositions.add(pos);
            } else {
                farPositions.add(pos);
            }
        }

        if (!nearPositions.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-playerX, -playerY, -playerZ);

            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.glLineWidth(3.0F);

            for (BlockPos pos : nearPositions) {
                BlockHighlightRenderer.renderBlockOutline(pos, r, g, b, 0.8f);
            }

            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }

        for (BlockPos pos : farPositions) {
            double distance = player.getDistance(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            DirectionArrowRenderer.drawDirectionArrow(player, pos, color, distance, partialTicks, 1.0f);
        }
    }

    public static String formatDistance(double distance) {
        if (distance < 10) return String.format("%.1fm", distance);
        if (distance < 1000) return String.format("%.0fm", distance);

        return String.format("%.1fkm", distance / 1000.0);
    }

    public static int getExternalPadding() {
        return PADDING_EXTERNAL;
    }
}