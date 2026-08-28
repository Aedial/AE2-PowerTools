package com.ae2powertools.features.scanner.gui;

import net.minecraft.util.math.BlockPos;


/**
 * Player location snapshot when a tab is sorted by distance.
 * The location keeps the sort order stable while the player moves.
 */
public final class ScannerSortAnchor {

    private final int dimension;
    private final BlockPos position;

    public ScannerSortAnchor(int dimension, BlockPos position) {
        this.dimension = dimension;
        this.position = position;
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getPosition() {
        return position;
    }
}
