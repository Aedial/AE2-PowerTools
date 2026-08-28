package com.ae2powertools.features.scanner.data.client;

import net.minecraft.util.math.BlockPos;


/**
 * Client data for the connection attached to a channel chokepoint.
 */
public class ConnectionFlowClient {

    public final int directionOrdinal;
    public final int channels;
    public final int demandedChannels;
    public final BlockPos connectedPos;
    public final String connectedDescription;

    public ConnectionFlowClient(int directionOrdinal, int channels, int demandedChannels,
            BlockPos connectedPos, String connectedDescription) {
        this.directionOrdinal = directionOrdinal;
        this.channels = channels;
        this.demandedChannels = demandedChannels;
        this.connectedPos = connectedPos;
        this.connectedDescription = connectedDescription;
    }
}
