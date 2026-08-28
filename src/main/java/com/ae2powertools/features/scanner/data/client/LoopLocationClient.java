package com.ae2powertools.features.scanner.data.client;

import net.minecraft.util.math.BlockPos;

import com.ae2powertools.features.scanner.data.AbstractLocation;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;
import com.ae2powertools.features.scanner.data.ScannerTabId;


/**
 * Client data for a detected network loop location.
 */
public class LoopLocationClient extends AbstractLocation {

    public final String dimensionName;
    public final String blockName;
    public final String description;
    public final boolean isLoaded;

    public LoopLocationClient(BlockPos pos, int dimension, String dimensionName,
            String blockName, String description, boolean isLoaded) {
        super(pos, dimension);
        this.dimensionName = dimensionName;
        this.blockName = blockName;
        this.description = description;
        this.isLoaded = isLoaded;
    }

    @Override
    public ScannerIssueKey getIssueKey() {
        return new ScannerIssueKey(ScannerTabId.LOOPS,
            dimension + ":" + pos.getX() + ':' + pos.getY() + ':' + pos.getZ());
    }
}
