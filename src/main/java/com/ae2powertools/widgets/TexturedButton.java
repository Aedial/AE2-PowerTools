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

    public TexturedButton(int x, int y, int width, int height, ResourceLocation texture,
            int textureU, int textureV, int textureWidth, int textureHeight) {
        super(x, y, width, height);
        this.texture = texture;
        this.textureU = textureU;
        this.textureV = textureV;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
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