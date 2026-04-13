package com.ae2powertools.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.PacketCrafterStateSync;
import com.ae2powertools.features.crafter.PacketOpenCrafterSubGui;
import com.ae2powertools.features.crafter.PacketRequestCrafterSync;
import com.ae2powertools.features.crafter.PacketReturnToCrafterGui;
import com.ae2powertools.features.crafter.PacketSetCrafterBatch;
import com.ae2powertools.features.crafter.PacketSetCrafterPage;
import com.ae2powertools.features.crafter.PacketSetCrafterSpeed;
import com.ae2powertools.features.crafter.PacketToggleCrafterEntry;
import com.ae2powertools.features.locator.PacketLocatorSync;
import com.ae2powertools.features.locator.PacketLocatorToggleSubnet;


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

        // Locator packets
        INSTANCE.registerMessage(PacketLocatorSync.Handler.class, PacketLocatorSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketLocatorToggleSubnet.Handler.class, PacketLocatorToggleSubnet.class, packetId++, Side.SERVER);

        // Priority Tuner packets
        INSTANCE.registerMessage(PacketPriorityApplied.Handler.class, PacketPriorityApplied.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketSetTunerPriority.Handler.class, PacketSetTunerPriority.class, packetId++, Side.SERVER);

        // Better Level Maintainer packets
        INSTANCE.registerMessage(PacketOpenMaintainerGui.Handler.class, PacketOpenMaintainerGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketUpdateMaintainerEntry.Handler.class, PacketUpdateMaintainerEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSelectRecipe.Handler.class, PacketSelectRecipe.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketCraftableItemsSync.Handler.class, PacketCraftableItemsSync.class, packetId++, Side.CLIENT);

        // AutoCrafter packets
        INSTANCE.registerMessage(PacketToggleCrafterEntry.Handler.class, PacketToggleCrafterEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketOpenCrafterSubGui.Handler.class, PacketOpenCrafterSubGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetCrafterBatch.Handler.class, PacketSetCrafterBatch.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetCrafterSpeed.Handler.class, PacketSetCrafterSpeed.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetCrafterPage.Handler.class, PacketSetCrafterPage.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketReturnToCrafterGui.Handler.class, PacketReturnToCrafterGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRequestCrafterSync.Handler.class, PacketRequestCrafterSync.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketCrafterStateSync.Handler.class, PacketCrafterStateSync.class, packetId++, Side.CLIENT);
    }
}
