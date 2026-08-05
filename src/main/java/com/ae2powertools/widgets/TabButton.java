package com.ae2powertools.widgets;

import java.util.Collections;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * AE2-style tab button with added utilities. The icon is drawn from the AE2 texture atlas.
 */
@SideOnly(Side.CLIENT)
public class TabButton extends PressableWidget {

    private final int iconIndex;
    private int hideEdge;

    public TabButton(int x, int y, int iconIndex, String tooltip) {
        super(x, y, 22, 22);
        this.iconIndex = iconIndex;
        setTooltipProvider(() -> tooltip == null ? Collections.emptyList() : Collections.singletonList(tooltip));
    }

    @Override
    protected void drawWidget(WidgetContext context, int mouseX, int mouseY) {
        int frameU = (hideEdge > 0 ? 11 : 13) * 16;
        int offsetX = hideEdge > 0 ? 1 : 0;
        int uvY = iconIndex / 16;
        int uvX = iconIndex % 16;

        GlStateManager.color(isEnabled() ? 1.0F : 0.5F, isEnabled() ? 1.0F : 0.5F, isEnabled() ? 1.0F : 0.5F, 1.0F);
        context.getWidgetMinecraft().getTextureManager().bindTexture(WidgetTextures.AE2_STATES);
        drawTexturedModalRect(getX(), getY(), frameU, 0, 25, 22);
        drawTexturedModalRect(getX() + offsetX + 3, getY() + 3, uvX * 16, uvY * 16, 16, 16);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public int getHideEdge() {
        return hideEdge;
    }

    public void setHideEdge(int hideEdge) {
        this.hideEdge = hideEdge;
    }
}