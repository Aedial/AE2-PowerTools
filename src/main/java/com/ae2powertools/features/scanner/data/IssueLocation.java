package com.ae2powertools.features.scanner.data;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;


/**
 * Represents a location in the network where an issue was detected.
 * Stores position, dimension, and the block at that location for display purposes.
 */
public class IssueLocation {

    private final BlockPos pos;
    private final int dimension;
    private final String dimensionName;
    private final IBlockState blockState;
    private final boolean isLoaded;
    private final String description;

    public IssueLocation(BlockPos pos, int dimension, String dimensionName, IBlockState blockState,
            boolean isLoaded, String description) {
        this.pos = pos;
        this.dimension = dimension;
        this.dimensionName = dimensionName;
        this.blockState = blockState;
        this.isLoaded = isLoaded;
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

    public IBlockState getBlockState() {
        return blockState;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Calculate distance from a given position (only if same dimension).
     */
    public double getDistanceFrom(BlockPos from) {
        double dx = pos.getX() - from.getX();
        double dy = pos.getY() - from.getY();
        double dz = pos.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IssueLocation)) return false;

        IssueLocation other = (IssueLocation) obj;
        return dimension == other.dimension && pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return 31 * pos.hashCode() + dimension;
    }
}
