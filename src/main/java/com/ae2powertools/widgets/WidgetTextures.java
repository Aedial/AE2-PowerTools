package com.ae2powertools.widgets;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Shared texture locations used by GUI widgets.
 * <p>
 * Keeping the common atlas references here avoids each widget re-declaring the
 * same ResourceLocation and makes the remaining feature-local textures easier to spot.
 */
@SideOnly(Side.CLIENT)
public final class WidgetTextures {

    public static final ResourceLocation AE2_STATES = new ResourceLocation(
        "appliedenergistics2", "textures/guis/states.png");
    public static final ResourceLocation CREATIVE_SCROLLBAR = new ResourceLocation(
        "minecraft", "textures/gui/container/creative_inventory/tabs.png");
    public static final ResourceLocation SELECTOR_BACKGROUND = new ResourceLocation(
        "ae2powertools", "textures/guis/recipe_selector.png");

    private WidgetTextures() {}
}