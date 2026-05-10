package com.ae2powertools.features.monitor.emitter;

import net.minecraft.block.Block;

import com.ae2powertools.features.monitor.dependent.ItemBlockStorageMonitorBase;


/**
 * ItemBlock for the ME Storage Level Emitter.
 * All Manager binding, placement guard, and NBT transfer logic
 * is handled by {@link ItemBlockStorageMonitorBase}.
 */
public class ItemBlockStorageLevelEmitter extends ItemBlockStorageMonitorBase {

    public ItemBlockStorageLevelEmitter(Block block) {
        super(block);
    }
}
