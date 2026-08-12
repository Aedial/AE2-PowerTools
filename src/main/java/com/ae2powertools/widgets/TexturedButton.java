package com.ae2powertools.widgets;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Shared full-texture button for small icon buttons.
 */
@SideOnly(Side.CLIENT)
public class TexturedButton extends PressableWidget {

    private final ResourceLocation texture;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;

    /**
     * Creates a new textured button with the given position, size, and texture parameters.
     * @param x The x position of the button.
     * @param y The y position of the button.
     * @param width The width of the button.
     * @param height The height of the button.
     * @param texture The texture resource location.
     * @param textureU The u coordinate of the texture.
     * @param textureV The v coordinate of the texture.
     * @param textureWidth The width of the texture.
     * @param textureHeight The height of the texture.
     */
    public TexturedButton(int x, int y, int width, int height, ResourceLocation texture,
            int textureU, int textureV, int textureWidth, int textureHeight) {
        super(x, y, width, height);
        this.texture = texture;
        this.textureU = textureU;
        this.textureV = textureV;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    /**
     * Creates a new textured button with the given position, size, and texture parameters.
     * The button will be square with the given size, and the texture will be assumed to be the same size as the button.
     * @param x The x position of the button.
     * @param y The y position of the button.
     * @param size The size of the button (width and height).
     * @param texture The texture resource location.
     * @param textureU The u coordinate of the texture.
     * @param textureV The v coordinate of the texture.
     */
    public TexturedButton(int x, int y, int size, ResourceLocation texture, int textureU, int textureV) {
        this(x, y, size, size, texture, textureU, textureV, size, size);
    }

    /**
     * Creates a new textured button with the given position, size, and texture parameters.
     * The button will be square with the given size, and the texture will be assumed to be the same size as the button.
     * The texture will be drawn starting at the top-left corner of the button.
     * @param x The x position of the button.
     * @param y The y position of the button.
     * @param size The size of the button (width and height).
     * @param texture The texture resource location.
     */
    public TexturedButton(int x, int y, int size, ResourceLocation texture) {
        this(x, y, size, size, texture, 0, 0, size, size);
    }

    @Override
    protected void drawWidget(WidgetContext context, int mouseX, int mouseY) {
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        context.getWidgetMinecraft().getTextureManager().bindTexture(texture);
        drawModalRectWithCustomSizedTexture(
            getX(),
            getY(),
            textureU,
            textureV,
            getWidth(),
            getHeight(),
            textureWidth,
            textureHeight);

        if (isHovered()) {
            drawRect(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x40FFFFFF);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}