package com.ae2powertools.features.monitor.display;

import java.util.List;

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
public class PartStorageDisplaySmaller extends PartStorageDisplayBase {

    // Part model resources
    private static final ResourceLocation MODEL_BASE =
        new ResourceLocation(Tags.MODID, "part/storage_display_base_smaller");

    public static final PartModel MODEL = new PartModel(MODEL_BASE);

    @PartModels
    public static List<IPartModel> getModels() {
        return ImmutableList.of(MODEL);
    }

    public PartStorageDisplaySmaller(ItemStack is) {
        super(is, 1);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODEL;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(1, 1, 14, 15, 15, 16);
    }
}
