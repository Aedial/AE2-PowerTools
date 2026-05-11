package com.ae2powertools.features.monitor.display;

import net.minecraft.block.Block;

import com.ae2powertools.features.monitor.dependent.ItemBlockStorageMonitorBase;


/**
 * ItemBlock for the ME Storage Display.
 * All Manager binding, placement guard, and NBT transfer logic
 * is handled by {@link ItemBlockStorageMonitorBase}.
 */
public class ItemBlockStorageDisplay extends ItemBlockStorageMonitorBase {

    public ItemBlockStorageDisplay(Block block) {
        super(block);
    }
}
