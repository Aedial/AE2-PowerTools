package com.ae2powertools.features.scanner.data;

import net.minecraft.util.math.BlockPos;


/**
 * Interface shared by client-side scanner results.
 */
public interface ScannerIssue {

    ScannerIssueKey getIssueKey();

    default String getSortingKey() {
        return getIssueKey().getKey();
    }

    int getDimension();

    BlockPos getAnchorPos();

    default double getDistanceFrom(BlockPos from) {
        BlockPos target = getAnchorPos();
        double dx = target.getX() - from.getX();
        double dy = target.getY() - from.getY();
        double dz = target.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
