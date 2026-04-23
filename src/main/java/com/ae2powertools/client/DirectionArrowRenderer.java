package com.ae2powertools.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Shared utility for drawing directional arrows pointing to distant targets.
 * Used by both ScannerRenderer and LocatorRenderer to avoid code duplication.
 *
 * The arrow appears in front of the player's view, oriented towards the target.
 * Supports config-based scaling and adaptive text sizing.
 */
@SideOnly(Side.CLIENT)
public class DirectionArrowRenderer {

    // ========== Arrow Rendering Constants ==========
    private static final float ARROW_BASE_DISTANCE = 0.6f;
    private static final float ARROW_SPREAD_RADIUS = 0.12f;
    private static final float ARROW_LENGTH = 0.05f;
    private static final float ARROW_WIDTH = 0.02f;
    private static final float ARROW_THICKNESS = 0.01f;
    private static final float MIN_PITCH_ANGLE = 10.0f;
    private static final float ARROW_ALPHA = 1.0f;
    private static final float BASE_TEXT_SCALE = 0.0012f;
    private static final float BASE_TEXT_HEIGHT_OFFSET = 0.02f;

    // ========== Arrow Gradient Constants ==========
    private static final float GRADIENT_START_FACTOR = 0.8f;
    private static final float GRADIENT_END_FACTOR = 0.4f;
    private static final float GRADIENT_CURVE = 0.5f;
    private static final int GRADIENT_RINGS = 16;

    /**
     * Draws a 3D directional arrow pointing towards the target position.
     * Uses config-based arrow and text scaling from PowerToolsClientConfig.scanner.
     *
     * @param player       The player entity (for position and view calculations)
     * @param target       The target block position to point towards
     * @param color        The arrow color (RGB, e.g., 0xFF4444 for red)
     * @param distance     The distance to the target (for display text)
     * @param partialTicks Partial tick time for smooth interpolation
     */
    public static void drawDirectionArrow(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        float arrowScale = PowerToolsClientConfig.scanner.getArrowScale();
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, arrowScale, false, true);
    }

    /**
     * Draws a 3D directional arrow with explicit scale factor.
     *
     * @param player       The player entity (for position and view calculations)
     * @param target       The target block position to point towards
     * @param color        The arrow color (RGB, e.g., 0xFF4444 for red)
     * @param distance     The distance to the target (for display text)
     * @param partialTicks Partial tick time for smooth interpolation
     * @param arrowScale   Scale factor for the arrow size (1.0 = normal)
     */
    public static void drawDirectionArrow(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks, float arrowScale) {
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, arrowScale, false, true);
    }

    /**
     * Draws a 3D directional arrow without distance text.
     * Uses config-based arrow scaling.
     */
    public static void drawDirectionArrowNoText(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        float arrowScale = PowerToolsClientConfig.scanner.getArrowScale();
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, arrowScale, false, false);
    }

    /**
     * Draws a 3D directional arrow without distance text, with explicit scale.
     */
    public static void drawDirectionArrowNoText(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks, float arrowScale) {
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, arrowScale, false, false);
    }

    /**
     * Draws a 3D directional arrow that always points horizontally (y-agnostic).
     * Used when the target's Y coordinate is unknown or irrelevant (e.g., chunks).
     * Uses config-based arrow scaling.
     */
    public static void drawDirectionArrowYAgnostic(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        float arrowScale = PowerToolsClientConfig.scanner.getArrowScale();
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, arrowScale, true, true);
    }

    /**
     * Draws a 3D directional arrow that always points horizontally, with explicit scale.
     */
    public static void drawDirectionArrowYAgnostic(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks, float arrowScale) {
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, arrowScale, true, true);
    }

    private static void drawDirectionArrowInternal(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks, float arrowScale, boolean yAgnostic, boolean showDistanceText) {
        Minecraft mc = Minecraft.getMinecraft();

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        double eyeY = playerY + player.getEyeHeight();

        float cameraYaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float cameraPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;

        double dx = target.getX() + 0.5 - playerX;
        double dy = target.getY() + 0.5 - eyeY;
        double dz = target.getZ() + 0.5 - playerZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist < 1) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float targetPitch;

        if (yAgnostic) {
            // For chunks, always point horizontally (slightly downward)
            targetPitch = -MIN_PITCH_ANGLE;
        } else {
            targetPitch = (float) Math.toDegrees(Math.atan2(dy, horizontalDist));
            if (Math.abs(targetPitch) < MIN_PITCH_ANGLE) {
                targetPitch = targetPitch >= 0 ? MIN_PITCH_ANGLE : -MIN_PITCH_ANGLE;
            }
        }

        double camYawRad = Math.toRadians(cameraYaw);
        double camPitchRad = Math.toRadians(cameraPitch);
        double camForwardX = -Math.sin(camYawRad) * Math.cos(camPitchRad);
        double camForwardY = -Math.sin(camPitchRad);
        double camForwardZ = Math.cos(camYawRad) * Math.cos(camPitchRad);

        double baseX = playerX + camForwardX * ARROW_BASE_DISTANCE;
        double baseY = eyeY + camForwardY * ARROW_BASE_DISTANCE;
        double baseZ = playerZ + camForwardZ * ARROW_BASE_DISTANCE;

        double relativeYaw = Math.toRadians(targetYaw - cameraYaw);
        double targetPitchRad = Math.toRadians(targetPitch);

        double targetDirX = dx / horizontalDist;
        double targetDirZ = dz / horizontalDist;

        double offsetX = targetDirX * ARROW_SPREAD_RADIUS;
        double offsetZ = targetDirZ * ARROW_SPREAD_RADIUS;
        double offsetY = Math.sin(targetPitchRad) * ARROW_SPREAD_RADIUS;

        double forwardOffset = Math.cos(relativeYaw);
        if (forwardOffset < 0) {
            double behindFactor = 1.0 + Math.abs(forwardOffset) * 0.5;
            offsetX *= behindFactor;
            offsetZ *= behindFactor;
        }

        double arrowX = baseX + offsetX;
        double arrowY = baseY + offsetY;
        double arrowZ = baseZ + offsetZ;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float alpha = ARROW_ALPHA;

        double renderX = arrowX - playerX;
        double renderY = arrowY - playerY;
        double renderZ = arrowZ - playerZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY, renderZ);

        GlStateManager.rotate(180 + targetYaw, 0, 1, 0);
        GlStateManager.rotate(targetPitch, 1, 0, 0);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthRange(0.0, 0.001);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        float halfThick = (ARROW_THICKNESS / 2) * arrowScale;
        float len = ARROW_LENGTH * arrowScale;
        float w = ARROW_WIDTH * arrowScale;

        for (int i = 0; i < GRADIENT_RINGS; i++) {
            float t0 = (float) i / GRADIENT_RINGS;
            float t1 = (float) (i + 1) / GRADIENT_RINGS;

            float z0 = -t0 * len;
            float z1 = -t1 * len;

            float w0 = w * (1.0f - t0);
            float w1 = w * (1.0f - t1);

            float curve0 = (float) (1.0 - Math.pow(1.0 - t0, GRADIENT_CURVE));
            float curve1 = (float) (1.0 - Math.pow(1.0 - t1, GRADIENT_CURVE));

            float factor0 = GRADIENT_START_FACTOR + curve0 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
            float factor1 = GRADIENT_START_FACTOR + curve1 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);

            float r0 = r * factor0, g0 = g * factor0, b0 = b * factor0;
            float r1 = r * factor1, g1 = g * factor1, b1 = b * factor1;

            boolean isTip = (i == GRADIENT_RINGS - 1);

            if (isTip) {
                // Tip triangles converging to a point
                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(0, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(0, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(0, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
            } else {
                // Ring segment quads (converted to triangles)
                // Top face
                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                // Bottom face
                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                // Left side
                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                // Right side
                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
            }

            // Draw back cap on first ring (accentuated brighter)
            if (i == 0) {
                float rBack = Math.min(r0 * 1.2f, 1.0f);
                float gBack = Math.min(g0 * 1.2f, 1.0f);
                float bBack = Math.min(b0 * 1.2f, 1.0f);

                buffer.pos(-w0, halfThick, z0).color(rBack, gBack, bBack, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(rBack, gBack, bBack, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(rBack, gBack, bBack, alpha).endVertex();

                buffer.pos(-w0, halfThick, z0).color(rBack, gBack, bBack, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(rBack, gBack, bBack, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(rBack, gBack, bBack, alpha).endVertex();
            }
        }

        tessellator.draw();

        GL11.glDepthRange(0.0, 1.0);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();

        // Draw distance text above arrow if requested
        if (showDistanceText) {
            String distanceStr = formatDistance(distance);
            float textOffset = BASE_TEXT_HEIGHT_OFFSET * arrowScale;

            // Calculate text scale based on arrow's distance from camera
            double arrowDistFromCamera = Math.sqrt(renderX * renderX + renderY * renderY + renderZ * renderZ);
            float textScale = calculateTextScale(arrowDistFromCamera, arrowScale);

            // Additional offset for text height
            textOffset += 4.0f * textScale;

            GlStateManager.pushMatrix();
            GlStateManager.translate(renderX, renderY + textOffset, renderZ);

            GlStateManager.rotate(-cameraYaw, 0, 1, 0);
            GlStateManager.rotate(cameraPitch, 1, 0, 0);

            GlStateManager.scale(-textScale, -textScale, textScale);

            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();

            int textWidth = mc.fontRenderer.getStringWidth(distanceStr);
            mc.fontRenderer.drawStringWithShadow(distanceStr, -textWidth / 2.0f, 0, color | 0xFF000000);

            GlStateManager.enableDepth();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();

            GlStateManager.popMatrix();
        }
    }

    /**
     * Calculate adaptive text scale based on arrow's distance from camera.
     * Arrows further around the viewing sphere appear smaller due to perspective,
     * so we scale them up to maintain consistent readability.
     *
     * @param arrowDistFromCamera Distance from camera to arrow position (typically 0.5-2 blocks)
     * @param arrowScale          The arrow scale factor being used
     */
    private static float calculateTextScale(double arrowDistFromCamera, float arrowScale) {
        float baseScale = BASE_TEXT_SCALE * PowerToolsClientConfig.scanner.getTextScale() * arrowScale;

        if (!PowerToolsClientConfig.scanner.adaptiveTextScale) return baseScale;

        // Derive distance bounds from arrow positioning constants:
        // - Closest: looking directly at arrow (base distance, minimal perpendicular offset)
        // - Furthest: looking away from arrow (base + spread * behindFactor where behindFactor max is 1.5)
        double minDist = ARROW_BASE_DISTANCE - ARROW_SPREAD_RADIUS;
        double maxDist = ARROW_BASE_DISTANCE + ARROW_SPREAD_RADIUS * 1.5;

        float minMult = PowerToolsClientConfig.scanner.getAdaptiveMin();
        float maxMult = PowerToolsClientConfig.scanner.getAdaptiveMax();

        if (arrowDistFromCamera <= minDist) return baseScale * minMult;
        if (arrowDistFromCamera >= maxDist) return baseScale * maxMult;

        // Linear interpolation: further arrows get larger text
        float t = (float) ((arrowDistFromCamera - minDist) / (maxDist - minDist));
        float multiplier = minMult + t * (maxMult - minMult);

        return baseScale * multiplier;
    }

    /**
     * Format distance for display.
     */
    private static String formatDistance(double distance) {
        if (distance < 10) return String.format("%.1fm", distance);
        if (distance < 1000) return String.format("%.0fm", distance);

        return String.format("%.1fkm", distance / 1000.0);
    }
}
