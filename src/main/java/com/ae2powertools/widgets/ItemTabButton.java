package com.ae2powertools.widgets;

import java.util.Collections;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * AE2-style tab button that renders an item stack icon.
 */
@SideOnly(Side.CLIENT)
public class ItemTabButton extends PressableWidget {

    private final ItemStack iconStack;
    private int hideEdge;

    public ItemTabButton(int x, int y, ItemStack iconStack, String tooltip) {
        super(x, y, 22, 22);
        this.iconStack = iconStack == null ? ItemStack.EMPTY : iconStack;
        setTooltipProvider(() -> tooltip == null ? Collections.emptyList() : Collections.singletonList(tooltip));
    }

    @Override
    protected void drawWidget(WidgetContext context, int mouseX, int mouseY) {
        int frameU = (hideEdge > 0 ? 11 : 13) * 16;
        int offsetX = hideEdge > 0 ? 1 : 0;
        float color = isEnabled() ? 1.0F : 0.5F;

        GlStateManager.color(color, color, color, 1.0F);
        context.getWidgetMinecraft().getTextureManager().bindTexture(WidgetTextures.AE2_STATES);
        drawTexturedModalRect(getX(), getY(), frameU, 0, 25, 22);

        if (!iconStack.isEmpty()) {
            zLevel = 100.0F;
            context.getWidgetItemRenderer().zLevel = 100.0F;

            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(iconStack, offsetX + getX() + 3, getY() + 3);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();

            context.getWidgetItemRenderer().zLevel = 0.0F;
            zLevel = 0.0F;
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public int getHideEdge() {
        return hideEdge;
    }

    public void setHideEdge(int hideEdge) {
        this.hideEdge = hideEdge;
    }
}