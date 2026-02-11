package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.ItemRegistry;
import com.ae2powertools.client.BlockHighlightRenderer;
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

    // ========== Arrow Rendering Constants ==========
    // TODO: Adapt these values to the screen resolution for better visibility
    private static final float ARROW_BASE_DISTANCE = 0.6f;
    private static final float ARROW_SPREAD_RADIUS = 0.12f;
    private static final float ARROW_LENGTH = 0.05f;
    private static final float ARROW_WIDTH = 0.02f;
    private static final float ARROW_THICKNESS = 0.01f;
    private static final float MIN_PITCH_ANGLE = 10.0f;
    private static final float TEXT_SCALE = 0.0012f;
    private static final float TEXT_HEIGHT_OFFSET = 0.04f;
    private static final float ARROW_ALPHA = 1.0f;

    // ========== Arrow Gradient Constants ==========
    private static final float GRADIENT_START_FACTOR = 0.8f;
    private static final float GRADIENT_END_FACTOR = 0.4f;
    private static final float GRADIENT_CURVE = 0.5f;
    private static final int GRADIENT_RINGS = 16;
    private static final boolean GRADIENT_FRONT_TO_BACK = true;
    private static final boolean ACCENTUATE_BACK = true;

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
        if (heldScanner.isEmpty()) return;
        if (!ItemNetworkHealthScanner.isOverlayEnabled(heldScanner)) return;

        long deviceId = ItemNetworkHealthScanner.getDeviceId(heldScanner);
        ScannerClientState.setActiveDeviceId(deviceId);

        if (!ScannerClientState.hasActiveSession()) return;

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
        } else {
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
        }

        if (lines.isEmpty()) return;

        // Calculate dimensions
        int lineHeight = mc.fontRenderer.FONT_HEIGHT;
        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, mc.fontRenderer.getStringWidth(line));

        int boxW = maxWidth + PADDING_INTERNAL * 2 + 8;  // Extra 8 for color indicator
        int boxH = lines.size() * lineHeight + (lines.size() - 1) * LINE_SPACING + PADDING_INTERNAL * 2;

        // Position (top left)
        ScaledResolution res = new ScaledResolution(mc);
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
        } else {
            renderChokeLocations(mc, player, playerDim, playerPos, playerX, playerY, playerZ, partialTicks);
        }
    }

    /**
     * Render loop location markers.
     */
    private void renderLoopLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        List<LoopLocationClient> selected = ScannerClientState.getSelectedLoops();

        // Render block outlines (only within wireframe distance and in current dimension)
        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(3.0F);

        for (LoopLocationClient loc : selected) {
            if (loc.dimension != playerDim) continue;

            double distance = loc.getDistanceFrom(playerPos);
            if (distance <= WIREFRAME_MAX_DISTANCE) {
                BlockHighlightRenderer.renderBlockOutline(loc.pos, 1.0f, 0.27f, 0.27f, 0.8f);
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();

        // Render direction arrows (for locations beyond wireframe distance)
        ItemStack heldScanner = getHeldScanner(mc);
        if (ItemNetworkHealthScanner.isOverlayEnabled(heldScanner)) {
            for (LoopLocationClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                double distance = loc.getDistanceFrom(playerPos);
                if (distance > WIREFRAME_MAX_DISTANCE) {
                    drawDirectionArrow(player, loc.pos, LOOP_COLOR, distance, partialTicks);
                }
            }
        }
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
                drawDirectionArrowYAgnostic(player, centerPos, CHUNK_COLOR, distance, partialTicks);
            }
        }
    }

    /**
     * Render missing channel device location markers.
     */
    private void renderMissingLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        List<MissingDeviceClient> selected = ScannerClientState.getSelectedMissing();

        // Render block outlines (only within wireframe distance and in current dimension)
        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(3.0F);

        for (MissingDeviceClient loc : selected) {
            if (loc.dimension != playerDim) continue;

            double distance = loc.getDistanceFrom(playerPos);
            if (distance <= WIREFRAME_MAX_DISTANCE) {
                // Red-ish color for missing channels
                BlockHighlightRenderer.renderBlockOutline(loc.pos, 1.0f, 0.4f, 0.4f, 0.8f);
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();

        // Render direction arrows (for locations beyond wireframe distance)
        ItemStack heldScanner = getHeldScanner(mc);
        if (ItemNetworkHealthScanner.isOverlayEnabled(heldScanner)) {
            for (MissingDeviceClient loc : selected) {
                if (loc.dimension != playerDim) continue;

                double distance = loc.getDistanceFrom(playerPos);
                if (distance > WIREFRAME_MAX_DISTANCE) {
                    drawDirectionArrow(player, loc.pos, MISSING_COLOR, distance, partialTicks);
                }
            }
        }
    }

    /**
     * Render chokepoint location markers with channel info.
     */
    private void renderChokeLocations(Minecraft mc, EntityPlayer player, int playerDim, BlockPos playerPos,
            double playerX, double playerY, double playerZ, float partialTicks) {
        List<ChokeLocationClient> selected = ScannerClientState.getSelectedChokes();
        if (selected.isEmpty()) return;

        // Render block outlines and floating text (only within wireframe distance)
        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerX, -playerY, -playerZ);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(3.0F);

        for (ChokeLocationClient loc : selected) {
            if (loc.dimension != playerDim) continue;

            double distance = loc.getDistanceFrom(playerPos);
            if (distance <= WIREFRAME_MAX_DISTANCE) {
                // Cyan-ish color for chokepoints
                BlockHighlightRenderer.renderBlockOutline(loc.pos, 0.4f, 0.67f, 1.0f, 0.8f);
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();

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
                    drawDirectionArrow(player, loc.pos, CHOKE_COLOR, distance, partialTicks);
                }
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
     * Draws a 3D directional arrow pointing towards the target position (y-agnostic).
     * Always points horizontally at -MIN_PITCH_ANGLE regardless of target Y.
     * Used for chunks where the Y coordinate is unknown.
     */
    private void drawDirectionArrowYAgnostic(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, true);
    }

    /**
     * Draws a 3D directional arrow pointing towards the target position.
     * Adapted from ClientRenderEvents in Simple-Structure-Scanner.
     */
    private void drawDirectionArrow(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        drawDirectionArrowInternal(player, target, color, distance, partialTicks, false);
    }

    /**
     * Internal method for drawing direction arrows.
     * @param yAgnostic If true, always use -MIN_PITCH_ANGLE for pitch (horizontal arrow)
     */
    private void drawDirectionArrowInternal(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks, boolean yAgnostic) {
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
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

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

        float halfThick = ARROW_THICKNESS / 2;
        float len = ARROW_LENGTH;
        float w = ARROW_WIDTH;

        for (int i = 0; i < GRADIENT_RINGS; i++) {
            float t0 = (float) i / GRADIENT_RINGS;
            float t1 = (float) (i + 1) / GRADIENT_RINGS;

            float z0 = -t0 * len;
            float z1 = -t1 * len;

            float w0 = w * (1.0f - t0);
            float w1 = w * (1.0f - t1);

            float curve0 = (float) (1.0 - Math.pow(1.0 - t0, GRADIENT_CURVE));
            float curve1 = (float) (1.0 - Math.pow(1.0 - t1, GRADIENT_CURVE));

            float factor0, factor1;
            if (GRADIENT_FRONT_TO_BACK) {
                factor0 = GRADIENT_START_FACTOR + curve0 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
                factor1 = GRADIENT_START_FACTOR + curve1 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
            } else {
                factor0 = GRADIENT_START_FACTOR + curve0 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
                factor1 = GRADIENT_START_FACTOR + curve1 * (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR);
            }

            float r0 = r * factor0, g0 = g * factor0, b0 = b * factor0;
            float r1 = r * factor1, g1 = g * factor1, b1 = b * factor1;

            boolean isTip = (i == GRADIENT_RINGS - 1);

            if (isTip) {
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
                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();

                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w1, -halfThick, z1).color(r1, g1, b1, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
            }

            if (i == 0) {
                if (ACCENTUATE_BACK) {
                    if (!GRADIENT_FRONT_TO_BACK) {
                        r0 = Math.min(r0 / 1.2f, 1.0f);
                        g0 = Math.min(g0 / 1.2f, 1.0f);
                        b0 = Math.min(b0 / 1.2f, 1.0f);
                    } else {
                        r0 = Math.min(r0 * 1.2f, 1.0f);
                        g0 = Math.min(g0 * 1.2f, 1.0f);
                        b0 = Math.min(b0 * 1.2f, 1.0f);
                    }
                }

                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();

                buffer.pos(-w0, halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
                buffer.pos(-w0, -halfThick, z0).color(r0, g0, b0, alpha).endVertex();
            }
        }

        tessellator.draw();

        GL11.glDepthRange(0.0, 1.0);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();

        GlStateManager.popMatrix();

        // Draw distance text above arrow
        String distanceStr = formatDistance(distance);

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY + TEXT_HEIGHT_OFFSET, renderZ);

        GlStateManager.rotate(-cameraYaw, 0, 1, 0);
        GlStateManager.rotate(cameraPitch, 1, 0, 0);

        GlStateManager.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

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

    /**
     * Check if the player is holding a Network Health Scanner in either hand.
     * Returns the scanner ItemStack if held, or ItemStack.EMPTY if not.
     */
    private ItemStack getHeldScanner(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;

        ItemStack mainHand = mc.player.getHeldItemMainhand();
        if (mainHand.getItem() == ItemRegistry.NETWORK_HEALTH_SCANNER) return mainHand;

        ItemStack offHand = mc.player.getHeldItemOffhand();
        if (offHand.getItem() == ItemRegistry.NETWORK_HEALTH_SCANNER) return offHand;

        return ItemStack.EMPTY;
    }

    private boolean isHoldingScanner(Minecraft mc) {
        return !getHeldScanner(mc).isEmpty();
    }
}
