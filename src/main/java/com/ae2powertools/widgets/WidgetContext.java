package com.ae2powertools.widgets;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Minimal rendering context shared by GUI widgets.
 */
@SideOnly(Side.CLIENT)
public interface WidgetContext {

    static WidgetContext of(Supplier<Minecraft> minecraftSupplier,
            Supplier<FontRenderer> fontRendererSupplier,
            Supplier<RenderItem> itemRendererSupplier,
            IntSupplier widthSupplier,
            IntSupplier heightSupplier) {
        return new WidgetContext() {
            @Override
            public Minecraft getWidgetMinecraft() {
                return minecraftSupplier.get();
            }

            @Override
            public FontRenderer getWidgetFontRenderer() {
                return fontRendererSupplier.get();
            }

            @Override
            public RenderItem getWidgetItemRenderer() {
                return itemRendererSupplier.get();
            }

            @Override
            public int getWidgetWidth() {
                return widthSupplier.getAsInt();
            }

            @Override
            public int getWidgetHeight() {
                return heightSupplier.getAsInt();
            }
        };
    }

    static FontRenderer getFontRenderer(GuiScreen gui) {
        Minecraft minecraft = gui.mc;
        if (minecraft == null) return null;

        return minecraft.fontRenderer;
    }

    static WidgetContext of(GuiScreen gui) {
        return of(
            () -> gui.mc,
            () -> getFontRenderer(gui),
            () -> gui.mc.getRenderItem(),
            () -> gui.width,
            () -> gui.height);
    }

    Minecraft getWidgetMinecraft();

    FontRenderer getWidgetFontRenderer();

    RenderItem getWidgetItemRenderer();

    int getWidgetWidth();

    int getWidgetHeight();
}