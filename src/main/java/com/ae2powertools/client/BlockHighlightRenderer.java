package com.ae2powertools.client;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;


/**
 * Renders block highlight outlines that are visible through walls.
 * Supports configurable duration, animation patterns, and colors.
 */
public class BlockHighlightRenderer {

    private static final Map<BlockPos, HighlightEntry> highlights = new HashMap<>();

    /**
     * Animation pattern for highlight alpha.
     */
    public enum HighlightPattern {
        /** Constant alpha (no animation) */
        CONSTANT,
        /** Linear fade from full to zero */
        LINEAR_FADE,
        /** Pulsing wave effect */
        WAVE
    }

    /**
     * Color for highlights, stored as RGB floats.
     */
    public static class HighlightColor {
        public static final HighlightColor GREEN = new HighlightColor(0.0f, 1.0f, 0.0f);
        public static final HighlightColor TEAL = new HighlightColor(0.0f, 1.0f, 0.5f);
        public static final HighlightColor RED = new HighlightColor(1.0f, 0.27f, 0.27f);
        public static final HighlightColor ORANGE = new HighlightColor(1.0f, 0.67f, 0.0f);
        public static final HighlightColor BLUE = new HighlightColor(0.3f, 0.5f, 1.0f);
        public static final HighlightColor YELLOW = new HighlightColor(1.0f, 1.0f, 0.0f);
        public static final HighlightColor WHITE = new HighlightColor(1.0f, 1.0f, 1.0f);

        public final float red;
        public final float green;
        public final float blue;

        public HighlightColor(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        /**
         * Create a color from an RGB hex integer (e.g., 0xFF4444).
         */
        public static HighlightColor fromRGB(int rgb) {
            float r = ((rgb >> 16) & 0xFF) / 255.0f;
            float g = ((rgb >> 8) & 0xFF) / 255.0f;
            float b = (rgb & 0xFF) / 255.0f;

            return new HighlightColor(r, g, b);
        }
    }

    /**
     * Internal data for a single highlight.
     */
    private static class HighlightEntry {
        final long startTime;
        final long duration;
        final HighlightPattern pattern;
        final HighlightColor color;
        final float baseAlpha;

        HighlightEntry(long duration, HighlightPattern pattern, HighlightColor color, float baseAlpha) {
            this.startTime = System.currentTimeMillis();
            this.duration = duration;
            this.pattern = pattern;
            this.color = color;
            this.baseAlpha = baseAlpha;
        }

        float getAlpha(long now) {
            float progress = duration <= 0 ? 0f : (float) (now - startTime) / duration;

            switch (pattern) {
                case LINEAR_FADE:
                    return baseAlpha * Math.max(0f, 1f - progress);

                case WAVE:
                    float wave = 0.7f + 0.3f * (float) Math.sin(now / 500.0);

                    return baseAlpha * wave;

                case CONSTANT:
                default:
                    return baseAlpha;
            }
        }

        boolean isExpired(long now) {
            if (duration < 0) return false;

            return now >= startTime + duration;
        }
    }

    /**
     * Add a highlight with full customization.
     * @param pos Block position to highlight
     * @param durationMs Duration in milliseconds, or -1 for infinite
     * @param pattern Animation pattern
     * @param color Highlight color
     * @param alpha Base alpha (0.0 to 1.0)
     */
    public static void addHighlight(BlockPos pos, long durationMs, HighlightPattern pattern,
            HighlightColor color, float alpha) {
        highlights.put(pos, new HighlightEntry(durationMs, pattern, color, alpha));
    }

    /**
     * Add a highlight with default alpha (0.8).
     */
    public static void addHighlight(BlockPos pos, long durationMs, HighlightPattern pattern, HighlightColor color) {
        addHighlight(pos, durationMs, pattern, color, 0.8f);
    }

    /**
     * Add a simple timed highlight with wave pattern (legacy compatibility).
     */
    public static void addHighlight(BlockPos pos, long durationMs) {
        addHighlight(pos, durationMs, HighlightPattern.WAVE, HighlightColor.TEAL, 1.0f);
    }

    /**
     * Add a priority highlight (green, linear fade) for the specified duration.
     * This is kept for backward compatibility.
     */
    public static void addPriorityHighlight(BlockPos pos, long durationMs) {
        addHighlight(pos, durationMs, HighlightPattern.LINEAR_FADE, HighlightColor.GREEN, 1.0f);
    }

    /**
     * Remove a highlight for a specific block.
     */
    public static void removeHighlight(BlockPos pos) {
        highlights.remove(pos);
    }

    /**
     * Clear all highlights.
     */
    public static void clearHighlights() {
        highlights.clear();
    }

    /**
     * Check if a block is currently highlighted.
     */
    public static boolean hasHighlight(BlockPos pos) {
        return highlights.containsKey(pos);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (highlights.isEmpty()) return;

        long now = System.currentTimeMillis();

        // Remove expired highlights
        highlights.entrySet().removeIf(entry -> entry.getValue().isExpired(now));

        if (highlights.isEmpty()) return;

        EntityPlayer player = Minecraft.getMinecraft().player;
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(3.0F);

        for (Map.Entry<BlockPos, HighlightEntry> entry : highlights.entrySet()) {
            BlockPos pos = entry.getKey();
            HighlightEntry highlight = entry.getValue();
            float alpha = highlight.getAlpha(now);

            if (alpha > 0.001f) {
                renderBlockOutline(pos, highlight.color.red, highlight.color.green, highlight.color.blue, alpha);
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /**
     * Render a block outline at the given position.
     * This method can be called externally for one-off renders within an existing GL context.
     */
    public static void renderBlockOutline(BlockPos pos, float red, float green, float blue, float alpha) {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.002);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Bottom face
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Top face
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // Vertical edges
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();
    }

    /**
     * Render a block outline using a HighlightColor.
     */
    public static void renderBlockOutline(BlockPos pos, HighlightColor color, float alpha) {
        renderBlockOutline(pos, color.red, color.green, color.blue, alpha);
    }
}
