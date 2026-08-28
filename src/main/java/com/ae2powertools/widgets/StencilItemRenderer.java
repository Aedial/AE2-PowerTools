package com.ae2powertools.widgets;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Separate rendering pass used to dim ItemStack icons through the framebuffer stencil.
 */
@SideOnly(Side.CLIENT)
public final class StencilItemRenderer {

    private static final int GHOST_COLOR = 0xAA555555;
    private static final int STENCIL_MASK = 0xFF;
    private static final int STENCIL_VALUE = 1;

    private static final class QueuedItem {

        private final ItemStack stack;
        private final int x;
        private final int y;

        private QueuedItem(ItemStack stack, int x, int y) {
            this.stack = stack;
            this.x = x;
            this.y = y;
        }
    }

    private final List<QueuedItem> queuedItems = new ArrayList<>();

    public void queue(ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) queuedItems.add(new QueuedItem(stack, x, y));
    }

    public void flush(WidgetContext context) {
        if (queuedItems.isEmpty()) return;

        if (!context.getWidgetMinecraft().getFramebuffer().isStencilEnabled()) {
            drawFallback(context);
            queuedItems.clear();
            return;
        }

        try {
            writeItemMasks(context);
            drawItems(context);
            drawDimmingQuads();
        } finally {
            resetRenderState();
            queuedItems.clear();
        }
    }

    /**
     * Writes every queued item's visible pixels into a shared stencil value.
     */
    private void writeItemMasks(WidgetContext context) {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(STENCIL_MASK);
        GL11.glStencilFunc(GL11.GL_ALWAYS, STENCIL_VALUE, STENCIL_MASK);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GlStateManager.clear(GL11.GL_STENCIL_BUFFER_BIT);

        GlStateManager.colorMask(false, false, false, false);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        RenderHelper.enableGUIStandardItemLighting();

        for (QueuedItem queuedItem : queuedItems) {
            context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(
                queuedItem.stack, queuedItem.x, queuedItem.y);
        }
    }

    /**
     * Draws the queued stacks while preserving the stencil values written by the mask pass.
     */
    private void drawItems(WidgetContext context) {
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glStencilMask(0);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, STENCIL_MASK);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        RenderHelper.enableGUIStandardItemLighting();

        for (QueuedItem queuedItem : queuedItems) {
            context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(
                queuedItem.stack, queuedItem.x, queuedItem.y);
            context.getWidgetItemRenderer().renderItemOverlayIntoGUI(
                context.getWidgetFontRenderer(), queuedItem.stack, queuedItem.x, queuedItem.y, null);
        }
    }

    /**
     * Dims only the pixels that were written by the item mask pass.
     */
    private void drawDimmingQuads() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GL11.glStencilFunc(GL11.GL_EQUAL, STENCIL_VALUE, STENCIL_MASK);

        for (QueuedItem queuedItem : queuedItems) {
            Gui.drawRect(queuedItem.x, queuedItem.y, queuedItem.x + 16, queuedItem.y + 16, GHOST_COLOR);
        }
    }

    /**
     * Uses the slot-wide dimming effect when the client has no stencil framebuffer.
     */
    private void drawFallback(WidgetContext context) {
        RenderHelper.enableGUIStandardItemLighting();

        for (QueuedItem queuedItem : queuedItems) {
            context.getWidgetItemRenderer().renderItemAndEffectIntoGUI(
                queuedItem.stack, queuedItem.x, queuedItem.y);
            context.getWidgetItemRenderer().renderItemOverlayIntoGUI(
                context.getWidgetFontRenderer(), queuedItem.stack, queuedItem.x, queuedItem.y, null);
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        for (QueuedItem queuedItem : queuedItems) {
            Gui.drawRect(queuedItem.x, queuedItem.y, queuedItem.x + 16, queuedItem.y + 16, GHOST_COLOR);
        }

        GlStateManager.disableBlend();
    }

    /**
     * Restores the state expected after a queued item-rendering pass.
     */
    private void resetRenderState() {
        GL11.glStencilMask(STENCIL_MASK);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, STENCIL_MASK);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
