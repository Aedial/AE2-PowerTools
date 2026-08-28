package com.ae2powertools.features.scanner.data.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.math.BlockPos;

import com.ae2powertools.features.scanner.data.AbstractLocation;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;
import com.ae2powertools.features.scanner.data.ScannerTabId;


/**
 * Client data for a channel chokepoint.
 */
public class ChokeLocationClient extends AbstractLocation {

    public final String dimensionName;
    public final String blockName;
    public final String description;
    public final int usedChannels;
    public final int demandedChannels;
    public final int capacity;
    public final List<ConnectionFlowClient> connectionFlows;

    public ChokeLocationClient(BlockPos pos, int dimension, String dimensionName,
            String blockName, String description, int usedChannels, int demandedChannels,
            int capacity, List<ConnectionFlowClient> connectionFlows) {
        super(pos, dimension);
        this.dimensionName = dimensionName;
        this.blockName = blockName;
        this.description = description;
        this.usedChannels = usedChannels;
        this.demandedChannels = demandedChannels;
        this.capacity = capacity;
        this.connectionFlows = Collections.unmodifiableList(new ArrayList<>(connectionFlows));
    }

    /**
     * Return the channels demanded beyond chokepoint capacity.
     */
    public int getExcessChannels() {
        return Math.max(0, demandedChannels - capacity);
    }

    /**
     * Return demanded channels and capacity as "demanded/capacity".
     */
    public String getChannelString() {
        return demandedChannels + "/" + capacity;
    }

    @Override
    public ScannerIssueKey getIssueKey() {
        return new ScannerIssueKey(ScannerTabId.CHOKEPOINTS,
            dimension + ":" + pos.getX() + ':' + pos.getY() + ':' + pos.getZ());
    }
}
