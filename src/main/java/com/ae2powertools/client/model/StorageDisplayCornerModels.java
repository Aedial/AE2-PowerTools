package com.ae2powertools.client.model;

import net.minecraft.util.ResourceLocation;

import com.ae2powertools.Tags;


/**
 * Resource locations for the dynamic-baked corner overlays on cable storage displays.
 * <p>
 * This class deliberately contains no client-only Minecraft classes: part definitions are
 * constructed on a dedicated server too, and their model locations must therefore be safe to
 * reference on both physical sides.
 */
public final class StorageDisplayCornerModels {

    public static final ResourceLocation FULL_SIZE =
        new ResourceLocation(Tags.MODID, "part/builtin/storage_display_corners");
    public static final ResourceLocation SMALLER =
        new ResourceLocation(Tags.MODID, "part/builtin/storage_display_corners_smaller");
    public static final ResourceLocation SMALLERER =
        new ResourceLocation(Tags.MODID, "part/builtin/storage_display_corners_smallerer");

    private StorageDisplayCornerModels() {
    }
}
