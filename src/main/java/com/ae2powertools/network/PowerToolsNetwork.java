package com.ae2powertools.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.ae2powertools.Tags;


/**
 * Network handler for mod packets.
 */
public class PowerToolsNetwork {

    public static SimpleNetworkWrapper INSTANCE;

    private static int packetId = 0;

    public static void init() {
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

        // Scanner packets
        INSTANCE.registerMessage(PacketScannerSync.Handler.class, PacketScannerSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketScannerCancel.Handler.class, PacketScannerCancel.class, packetId++, Side.SERVER);

        // Priority Tuner packets
        INSTANCE.registerMessage(PacketPriorityApplied.Handler.class, PacketPriorityApplied.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketSetTunerPriority.Handler.class, PacketSetTunerPriority.class, packetId++, Side.SERVER);

        // Better Level Maintainer packets
        INSTANCE.registerMessage(PacketOpenMaintainerGui.Handler.class, PacketOpenMaintainerGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketUpdateMaintainerEntry.Handler.class, PacketUpdateMaintainerEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSelectRecipe.Handler.class, PacketSelectRecipe.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketCraftableItemsSync.Handler.class, PacketCraftableItemsSync.class, packetId++, Side.CLIENT);
    }
}
