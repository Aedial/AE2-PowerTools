package com.ae2powertools.widgets;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Vanilla-style button whose lower edge remains visible when its height is less
 * than the 20-pixel height of the vanilla button texture.
 */
@SideOnly(Side.CLIENT)
public class SmallVanillaButton extends PressableWidget {

    private static final ResourceLocation BUTTON_TEXTURES = new ResourceLocation(
        "minecraft", "textures/gui/widgets.png");

    private final int id;
    private String label;
    private int textColor;

    /**
     * Create a small vanilla button with a given size, position, and text.
     */
    public SmallVanillaButton(int buttonId, int x, int y, int width, int height, String text) {
        super(x, y, width, height);
        this.id = buttonId;
        this.label = text == null ? "" : text;
    }

    /**
     * Create a small square vanilla button with a given size, position, and text.
     */
    public SmallVanillaButton(int buttonId, int x, int y, int size, String text) {
        this(buttonId, x, y, size, size, text);
    }

    /**
     * Create a small square vanilla button with a given size and text, positioned at (0, 0).
     */
    public SmallVanillaButton(int buttonId, int size, String text) {
        this(buttonId, 0, 0, size, size, text);
    }

    @Override
    protected void drawWidget(WidgetContext context, int mouseX, int mouseY) {
        FontRenderer fontRenderer = context.getWidgetFontRenderer();
        context.getWidgetMinecraft().getTextureManager().bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        int textureY = 46 + getHoverState() * 20;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        int leftWidth = getWidth() / 2;
        int rightWidth = getWidth() - leftWidth;
        int bottomHeight = Math.min(2, getHeight());
        int middleHeight = getHeight() - bottomHeight;

        drawTexturedModalRect(getX(), getY(), 0, textureY, leftWidth, middleHeight);
        drawTexturedModalRect(getX() + leftWidth, getY(), 200 - rightWidth, textureY, rightWidth, middleHeight);
        drawTexturedModalRect(getX(), getY() + middleHeight, 0, textureY + 20 - bottomHeight, leftWidth, bottomHeight);
        drawTexturedModalRect(
            getX() + leftWidth,
            getY() + middleHeight,
            200 - rightWidth,
            textureY + 20 - bottomHeight,
            rightWidth,
            bottomHeight);

        drawCenteredString(
            fontRenderer,
            label,
            getX() + getWidth() / 2,
            getY() + (getHeight() - 8) / 2,
            getTextColor());
    }

    public int getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public void setColor(int textColor) {
        this.textColor = textColor;
    }

    public void clearColor() {
        this.textColor = 0;
    }

    private int getHoverState() {
        if (!isEnabled()) return 0;
        if (isHovered()) return 2;

        return 1;
    }

    private int getTextColor() {
        if (textColor != 0) return textColor;
        if (!isEnabled()) return 0xA0A0A0;
        if (isHovered()) return 0xFFFFA0;

        return 0xE0E0E0;
    }
}