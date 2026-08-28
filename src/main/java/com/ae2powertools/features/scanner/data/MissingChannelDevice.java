package com.ae2powertools.features.scanner.data;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;


/**
 * Represents a device that requires a channel but couldn't get one.
 * Stores the device's item representation for icon display in the GUI.
 */
public class MissingChannelDevice {

    private final BlockPos pos;
    private final int dimension;
    private final String dimensionName;
    private final ItemStack itemStack;
    private final String description;

    public MissingChannelDevice(BlockPos pos, int dimension, String dimensionName,
            ItemStack itemStack, String description) {
        this.pos = pos;
        this.dimension = dimension;
        this.dimensionName = dimensionName;
        this.itemStack = itemStack != null ? itemStack.copy() : ItemStack.EMPTY;
        this.description = description;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getDimension() {
        return dimension;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get display name from ItemStack, falling back to description.
     */
    public String getDisplayName() {
        if (!itemStack.isEmpty()) return itemStack.getDisplayName();

        return description;
    }

    @Override
    public int hashCode() {
        return pos.hashCode() ^ dimension;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MissingChannelDevice)) return false;

        MissingChannelDevice other = (MissingChannelDevice) obj;

        return pos.equals(other.pos) && dimension == other.dimension;
    }
}
