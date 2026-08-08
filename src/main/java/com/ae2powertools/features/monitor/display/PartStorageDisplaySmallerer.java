package com.ae2powertools.features.monitor.display;

import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import com.ae2powertools.Tags;


/**
 * Cable part variant of the ME Storage Display.
 * Renders content icon + quantity + corner color on its face.
 */
public class PartStorageDisplaySmallerer extends PartStorageDisplayBase {

    // Part model resources
    private static final ResourceLocation MODEL_BASE =
        new ResourceLocation(Tags.MODID, "part/storage_display_base_smallerer");

    public static final PartModel MODEL = new PartModel(MODEL_BASE);

    @PartModels
    public static List<IPartModel> getModels() {
        return ImmutableList.of(MODEL);
    }

    public PartStorageDisplaySmallerer(ItemStack is) {
        super(is, 2);
    }

    @Override
    @Nonnull
    public IPartModel getStaticModels() {
        return MODEL;
    }

    // TODO: The fluid being scaled down to 8px, the display has very little room
    //       to show borders and quantity (only 1px on each side + 1px for the frame)
    //       A solution may be scale down the fluid further to 4px,
    //       but that may be too small.
    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
    }
}
