package com.ae2powertools.widgets;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Shared AE2-framed side button used by the GUIs.
 */
@SideOnly(Side.CLIENT)
public class Ae2Button extends PressableWidget {

    private String label;
    private int labelColor = 0xFFFFFFFF;
    private ResourceLocation iconTexture;
    private int iconU;
    private int iconV;
    private int iconTextureWidth = 16;
    private int iconTextureHeight = 16;
    private boolean scaledIcon;

    public Ae2Button(int x, int y, int size) {
        super(x, y, size, size);
    }

    /**
     * Draws the widget with an AE2-style frame and either a centered label or a centered icon.
     */
    @Override
    protected void drawWidget(WidgetContext context, int mouseX, int mouseY) {
        if (label == null && iconTexture == null) {
            throw new IllegalStateException("Ae2Button must have either a label or an icon set.");
        }

        context.getWidgetMinecraft().getTextureManager().bindTexture(WidgetTextures.AE2_STATES);
        drawTexturedModalRect(getX(), getY(), 240, 240, getWidth(), getHeight());

        if (label != null) {
            int labelWidth = context.getWidgetFontRenderer().getStringWidth(label);
            int labelX = getX() + (getWidth() - labelWidth) / 2;
            int labelY = getY() + (getHeight() - 8) / 2;
            context.getWidgetFontRenderer().drawString(label, labelX, labelY, labelColor);
        } else if (iconTexture != null) {
            context.getWidgetMinecraft().getTextureManager().bindTexture(iconTexture);
            if (scaledIcon) {
                drawScaledCustomSizeModalRect(
                    getX(),
                    getY(),
                    iconU,
                    iconV,
                    getWidth(),
                    getHeight(),
                    getWidth(),
                    getHeight(),
                    iconTextureWidth,
                    iconTextureHeight);
            } else {
                drawTexturedModalRect(getX(), getY(), iconU, iconV, getWidth(), getHeight());
            }
        }

        if (isHovered()) {
            drawRect(
                getX() + 1,
                getY() + 1,
                getX() + getWidth() - 1,
                getY() + getHeight() - 1,
                0x40FFFFFF);
        }
    }

    public void clearIcon() {
        label = null;
        iconTexture = null;
        scaledIcon = false;
    }

    /**
     * Sets the centered label with a custom color.
     * @param label The label text.
     * @param labelColor The color of the label in ARGB format.
     */
    public void setCenteredLabel(String label, int labelColor) {
        this.label = label;
        this.labelColor = labelColor;
        this.iconTexture = null;
        this.scaledIcon = false;
    }

    /**
     * Sets the centered label with the default color (black).
     */
    public void setCenteredLabel(String label) {
        setCenteredLabel(label, 0x000000);
    }

    /**
     * Sets the icon to be drawn from the AE2 texture atlas.
     * @param iconIndex The index of the icon in the AE2 texture atlas. Column-major order,
     *                  starting from the top-left corner (0,0).
     */
    public void setAe2TextureIcon(int iconIndex) {
        setTextureIcon(WidgetTextures.AE2_STATES, iconIndex % 16 * 16, iconIndex / 16 * 16);
    }

    /**
     * Sets the icon to be drawn from a custom texture.
     * @param iconTexture The texture resource location.
     * @param iconU The U coordinate of the icon in the texture.
     * @param iconV The V coordinate of the icon in the texture.
     */
    public void setTextureIcon(ResourceLocation iconTexture, int iconU, int iconV) {
        this.iconTexture = iconTexture;
        this.iconU = iconU;
        this.iconV = iconV;
        this.iconTextureWidth = 16;
        this.iconTextureHeight = 16;
        this.scaledIcon = false;
        this.label = null;
    }

    /**
     * Sets the icon to be drawn from a custom texture, with scaling.
     * @param iconTexture The texture resource location.
     * @param iconU The U coordinate of the icon in the texture.
     * @param iconV The V coordinate of the icon in the texture.
     * @param textureWidth The width of the texture.
     * @param textureHeight The height of the texture.
     */
    public void setScaledTextureIcon(ResourceLocation iconTexture, int iconU, int iconV,
            int textureWidth, int textureHeight) {
        this.iconTexture = iconTexture;
        this.iconU = iconU;
        this.iconV = iconV;
        this.iconTextureWidth = textureWidth;
        this.iconTextureHeight = textureHeight;
        this.scaledIcon = true;
        this.label = null;
    }

}