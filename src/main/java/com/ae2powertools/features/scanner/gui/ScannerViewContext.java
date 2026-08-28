package com.ae2powertools.features.scanner.gui;

import net.minecraft.util.math.BlockPos;


/**
 * Player data snapshot, used to format rows and calculate distances.
 */
public final class ScannerViewContext {

    private static final ScannerViewContext EMPTY = new ScannerViewContext(0, BlockPos.ORIGIN, false);

    private final int playerDimension;
    private final BlockPos playerPosition;
    private final boolean hasPlayer;

    private ScannerViewContext(int playerDimension, BlockPos playerPosition, boolean hasPlayer) {
        this.playerDimension = playerDimension;
        this.playerPosition = playerPosition;
        this.hasPlayer = hasPlayer;
    }

    public static ScannerViewContext of(int playerDimension, BlockPos playerPosition) {
        return new ScannerViewContext(playerDimension, playerPosition, true);
    }

    public static ScannerViewContext empty() {
        return EMPTY;
    }

    public int getPlayerDimension() {
        return playerDimension;
    }

    public BlockPos getPlayerPosition() {
        return playerPosition;
    }

    public boolean hasPlayer() {
        return hasPlayer;
    }

    public boolean isCurrentDimension(int dimension) {
        return hasPlayer && playerDimension == dimension;
    }

    public ScannerSortAnchor createSortAnchor() {
        return new ScannerSortAnchor(playerDimension, playerPosition);
    }
}
