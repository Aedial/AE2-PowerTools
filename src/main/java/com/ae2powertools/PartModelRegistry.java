package com.ae2powertools;

import java.util.Collection;
import java.util.stream.Collectors;

import net.minecraft.util.ResourceLocation;

import appeng.api.AEApi;
import appeng.api.parts.IPartModels;

import com.ae2powertools.features.monitor.emitter.PartStorageLevelEmitter;
import com.ae2powertools.features.monitor.display.PartStorageDisplay;


/**
 * Registers AE2 cable part models with the IPartModels registry.
 * Must be called during preInit, before AE2 locks the part model registry.
 */
public class PartModelRegistry {

    public static void init() {
        IPartModels partModels = AEApi.instance().registries().partModels();

        // Extract all ResourceLocations from the part model definitions and register them.
        // IPartModels.registerModels() takes Collection<ResourceLocation>, so we need to
        // flatten IPartModel → List<ResourceLocation> for each model variant.
        Collection<ResourceLocation> emitterModels = PartStorageLevelEmitter.getModels().stream()
            .flatMap(m -> m.getModels().stream())
            .collect(Collectors.toList());
        partModels.registerModels(emitterModels);

        Collection<ResourceLocation> displayModels = PartStorageDisplay.getModels().stream()
            .flatMap(m -> m.getModels().stream())
            .collect(Collectors.toList());
        partModels.registerModels(displayModels);
    }
}
