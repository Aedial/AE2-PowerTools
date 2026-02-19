package com.ae2powertools.features.scanner;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;


/**
 * Represents a chunk location with dimension information.
 * Used to track unloaded/non-force-loaded chunks in the network.
 */
public class ChunkLocation {

    private final int chunkX;
    private final int chunkZ;
    private final int dimension;
    private final String dimensionName;

    public ChunkLocation(int chunkX, int chunkZ, int dimension, String dimensionName) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
        this.dimensionName = dimensionName;
    }

    public ChunkLocation(ChunkPos chunkPos, int dimension, String dimensionName) {
        this(chunkPos.x, chunkPos.z, dimension, dimensionName);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public int getDimension() {
        return dimension;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    /**
     * Get the chunk position.
     */
    public ChunkPos getChunkPos() {
        return new ChunkPos(chunkX, chunkZ);
    }

    /**
     * Get the center block position of this chunk.
     */
    public BlockPos getCenterPos() {
        return new BlockPos((chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
    }

    /**
     * Calculate distance from a given position (only meaningful if same dimension).
     */
    public double getDistanceFrom(BlockPos from) {
        BlockPos center = getCenterPos();
        double dx = center.getX() - from.getX();
        double dz = center.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChunkLocation)) return false;

        ChunkLocation other = (ChunkLocation) obj;

        return chunkX == other.chunkX && chunkZ == other.chunkZ && dimension == other.dimension;
    }

    @Override
    public int hashCode() {
        int result = chunkX;
        result = 31 * result + chunkZ;
        result = 31 * result + dimension;

        return result;
    }

    @Override
    public String toString() {
        return String.format("ChunkLocation[%d, %d] in %s (dim %d)", chunkX, chunkZ, dimensionName, dimension);
    }
}
