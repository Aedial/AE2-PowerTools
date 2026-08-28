package com.ae2powertools.features.scanner.data;

import net.minecraft.util.math.BlockPos;


public abstract class AbstractLocation implements ScannerIssue {
    public final BlockPos pos;
    public final int dimension;

    public AbstractLocation(BlockPos pos, int dimension) {
        this.pos = pos;
        this.dimension = dimension;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public BlockPos getAnchorPos() {
        return pos;
    }
}
