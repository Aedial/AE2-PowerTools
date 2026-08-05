package com.ae2powertools.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Small shared drawing helpers for repeated widget chrome.
 */
@SideOnly(Side.CLIENT)
public final class WidgetDrawHelper extends Gui {

    public static final int AE2_UPGRADE_ICON_U = 15 * 16;
    public static final int AE2_UPGRADE_ICON_V = 13 * 16;
    public static final int AE2_PATTERN_ICON_U = 15 * 16;
    public static final int AE2_PATTERN_ICON_V = 8 * 16;
    public static final float ICON_OPACITY = 0.4f;  // AE2's default opacity for slot icons

    private static final WidgetDrawHelper INSTANCE = new WidgetDrawHelper();

    private WidgetDrawHelper() {}

    /**
     * Draws the vanilla creative-tab style scrollbar thumb.
     * <p>
     * Callers pass the logical scroll offset and max scroll in row units; this helper
     * only handles the repeated texture binding and thumb interpolation math.
     */
    public static void drawCreativeScrollbar(Minecraft minecraft, int x, int y, int trackHeight,
            int thumbWidth, int thumbHeight, int scrollOffset, int maxScroll) {
        minecraft.getTextureManager().bindTexture(WidgetTextures.CREATIVE_SCROLLBAR);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        if (maxScroll <= 0) {
            INSTANCE.drawTexturedModalRect(x, y, 244, 0, thumbWidth, thumbHeight);
            return;
        }

        int thumbY = y + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        INSTANCE.drawTexturedModalRect(x, thumbY, 232, 0, thumbWidth, thumbHeight);
    }

    /**
     * Draws a semi-transparent icon from AE2's states sheet.
     * <p>
     * This is shared by empty-slot decorations, following AE2's style.
     */
    public static void drawAe2StateIcon(Minecraft minecraft, int x, int y, int u, int v,
            int width, int height, float alpha) {
        minecraft.getTextureManager().bindTexture(WidgetTextures.AE2_STATES);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        INSTANCE.drawTexturedModalRect(x, y, u, v, width, height);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    public static void drawUpgradePlaceholder(Minecraft minecraft, int x, int y) {
        drawAe2StateIcon(minecraft, x, y, AE2_UPGRADE_ICON_U, AE2_UPGRADE_ICON_V, 16, 16, ICON_OPACITY);
    }

    public static void drawPatternPlaceholder(Minecraft minecraft, int x, int y) {
        drawAe2StateIcon(minecraft, x, y, AE2_PATTERN_ICON_U, AE2_PATTERN_ICON_V, 16, 16, ICON_OPACITY);
    }
}