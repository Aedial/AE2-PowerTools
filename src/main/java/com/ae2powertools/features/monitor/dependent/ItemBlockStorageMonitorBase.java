package com.ae2powertools.features.monitor.dependent;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;


/**
 * Simple ItemBlock base for storage monitor blocks.
 * <p>
 * Concrete subclasses only need to exist for type identity (registration); no code required.
 */
public abstract class ItemBlockStorageMonitorBase extends ItemBlock {

    public ItemBlockStorageMonitorBase(Block block) {
        super(block);
    }
}
