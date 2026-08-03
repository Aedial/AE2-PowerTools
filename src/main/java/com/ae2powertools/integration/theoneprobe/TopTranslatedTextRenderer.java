package com.ae2powertools.integration.theoneprobe;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mcjty.theoneprobe.apiimpl.client.ElementTextRender;


@SideOnly(Side.CLIENT)
public final class TopTranslatedTextRenderer {

    private TopTranslatedTextRenderer() {}

    public static void render(String translationKey, String[] arguments, int x, int y) {
        ElementTextRender.render(resolve(translationKey, arguments), x, y);
    }

    public static int getWidth(String translationKey, String[] arguments) {
        return ElementTextRender.getWidth(resolve(translationKey, arguments));
    }

    @Nonnull
    private static String resolve(String translationKey, String[] arguments) {
        return I18n.format(translationKey, (Object[]) arguments);
    }
}