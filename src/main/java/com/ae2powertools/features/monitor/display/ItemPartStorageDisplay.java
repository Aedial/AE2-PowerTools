package com.ae2powertools.features.monitor.display;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.ae2powertools.features.monitor.dependent.ItemPartStorageMonitorBase;


/**
 * Item for the ME Storage Display cable part.
 * All Manager binding, placement, and tooltip logic
 * is handled by {@link ItemPartStorageMonitorBase}.
 */
public class ItemPartStorageDisplay extends ItemPartStorageMonitorBase<PartStorageDisplay> {

    public ItemPartStorageDisplay() {
        super("storage_display_part");
    }

    @Override
    protected String getTooltipKey() {
        return "tile.ae2powertools.storage_display.tooltip";
    }

    @Nullable
    @Override
    public PartStorageDisplay createPartFromItemStack(ItemStack is) {
        return new PartStorageDisplay(is);
    }
}
