package com.ae2powertools.features.scanner;

import net.minecraft.util.math.BlockPos;


public class AbstractLocation {
    public final BlockPos pos;
    public final int dimension;

    public AbstractLocation(BlockPos pos, int dimension) {
        this.pos = pos;
        this.dimension = dimension;
    }

    public double getDistanceFrom(BlockPos from) {
        double dx = pos.getX() - from.getX();
        double dy = pos.getY() - from.getY();
        double dz = pos.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
