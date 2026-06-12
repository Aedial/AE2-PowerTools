package com.ae2powertools.features.monitor.emitter;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.ae2powertools.features.monitor.dependent.ItemPartStorageMonitorBase;


/**
 * Item for the ME Storage Level Emitter cable part.
 * All Manager binding, placement, and tooltip logic
 * is handled by {@link ItemPartStorageMonitorBase}.
 */
public class ItemPartStorageLevelEmitter extends ItemPartStorageMonitorBase<PartStorageLevelEmitter> {

    public ItemPartStorageLevelEmitter() {
        super("storage_level_emitter_part");
    }

    @Override
    protected String getTooltipKey() {
        return "tile.ae2powertools.storage_level_emitter.tooltip";
    }

    @Nullable
    @Override
    public PartStorageLevelEmitter createPartFromItemStack(ItemStack is) {
        return new PartStorageLevelEmitter(is);
    }
}
