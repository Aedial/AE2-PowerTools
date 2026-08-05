package com.ae2powertools.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Minimal rendering context shared by GUI widgets.
 */
@SideOnly(Side.CLIENT)
public interface WidgetContext {

    Minecraft getWidgetMinecraft();

    FontRenderer getWidgetFontRenderer();

    RenderItem getWidgetItemRenderer();

    int getWidgetWidth();

    int getWidgetHeight();
}