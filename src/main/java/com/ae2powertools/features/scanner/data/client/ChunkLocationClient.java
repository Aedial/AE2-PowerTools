package com.ae2powertools.features.scanner.data.client;

import net.minecraft.util.math.BlockPos;

import com.ae2powertools.features.scanner.data.ScannerIssue;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;
import com.ae2powertools.features.scanner.data.ScannerTabId;


/**
 * Client data for an unloaded chunk found during a scan.
 */
public class ChunkLocationClient implements ScannerIssue {

    public final int chunkX;
    public final int chunkZ;
    public final int dimension;
    public final String dimensionName;

    public ChunkLocationClient(int chunkX, int chunkZ, int dimension, String dimensionName) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
        this.dimensionName = dimensionName;
    }

    public BlockPos getCenterPos() {
        return new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
    }

    @Override
    public ScannerIssueKey getIssueKey() {
        return new ScannerIssueKey(ScannerTabId.UNLOADED_CHUNKS, dimension + ":" + chunkX + ':' + chunkZ);
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public BlockPos getAnchorPos() {
        return getCenterPos();
    }

    @Override
    public double getDistanceFrom(BlockPos from) {
        BlockPos center = getCenterPos();
        double dx = center.getX() - from.getX();
        double dz = center.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dz * dz);
    }

    public String getCoordString() {
        return String.format("[%d, %d]", chunkX, chunkZ);
    }
}
