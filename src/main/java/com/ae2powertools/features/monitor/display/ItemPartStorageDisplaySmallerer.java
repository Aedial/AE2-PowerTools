package com.ae2powertools.features.monitor.display;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.ae2powertools.features.monitor.dependent.ItemPartStorageMonitorBase;


/**
 * Item for the ME Storage Display cable part.
 * All Manager binding, placement, and tooltip logic
 * is handled by {@link ItemPartStorageMonitorBase}.
 */
public class ItemPartStorageDisplaySmallerer extends ItemPartStorageMonitorBase<PartStorageDisplaySmallerer> {

    public ItemPartStorageDisplaySmallerer() {
        super("storage_display_part_smallerer");
    }

    @Override
    protected String getTooltipKey() {
        return "tile.ae2powertools.storage_display.tooltip";
    }

    @Nullable
    @Override
    public PartStorageDisplaySmallerer createPartFromItemStack(ItemStack is) {
        return new PartStorageDisplaySmallerer(is);
    }
}
