package com.ae2powertools.widgets;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Collects ItemStack draw calls and flushes them under one shared lighting setup.
 * <p>
 * This keeps repeated GL state toggles out of high-density GUI loops while making the
 * code explicit about which phase owns item rendering.
 */
@SideOnly(Side.CLIENT)
public final class QueuedItemRenderer {

    @FunctionalInterface
    public interface ItemDraw {

        void draw(WidgetContext context);
    }

    private final List<ItemDraw> queuedDraws = new ArrayList<>();

    public void queue(ItemDraw draw) {
        queuedDraws.add(draw);
    }

    public boolean isEmpty() {
        return queuedDraws.isEmpty();
    }

    public void flush(WidgetContext context) {
        if (queuedDraws.isEmpty()) return;

        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        for (ItemDraw draw : queuedDraws) draw.draw(context);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        queuedDraws.clear();
    }
}