package com.ae2powertools.util;

import appeng.api.networking.IGridNode;
import appeng.me.helpers.AENetworkProxy;


public final class PowerStateClientFlags {

    public static final int POWERED_FLAG = 1;
    public static final int CHANNEL_FLAG = 2;

    private PowerStateClientFlags() {}

    public static int collect(AENetworkProxy gridProxy) {
        int clientFlags = 0;

        if (gridProxy.isPowered()) clientFlags |= POWERED_FLAG;

        IGridNode node = gridProxy.getNode();
        if (node != null && node.meetsChannelRequirements()) clientFlags |= CHANNEL_FLAG;

        return clientFlags;
    }

    public static boolean isPowered(int clientFlags) {
        return (clientFlags & POWERED_FLAG) == POWERED_FLAG;
    }

    public static boolean isActive(int clientFlags) {
        return (clientFlags & CHANNEL_FLAG) == CHANNEL_FLAG;
    }
}