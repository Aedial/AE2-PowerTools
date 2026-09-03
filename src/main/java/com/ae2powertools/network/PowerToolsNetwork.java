package com.ae2powertools.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.PacketCrafterOverviewSync;
import com.ae2powertools.features.crafter.PacketCrafterRecipeSync;
import com.ae2powertools.features.crafter.PacketOpenCrafterSubGui;
import com.ae2powertools.features.crafter.PacketReturnToCrafterGui;
import com.ae2powertools.features.crafter.PacketSetCrafterBatch;
import com.ae2powertools.features.crafter.PacketCrafterPageInit;
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
        INSTANCE.registerMessage(PacketScannerToggleSubnet.Handler.class, PacketScannerToggleSubnet.class, packetId++, Side.SERVER);

        // Locator packets
        INSTANCE.registerMessage(PacketLocatorSync.Handler.class, PacketLocatorSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketLocatorToggleSubnet.Handler.class, PacketLocatorToggleSubnet.class, packetId++, Side.SERVER);

        // Priority Tuner packets
        INSTANCE.registerMessage(PacketPriorityApplied.Handler.class, PacketPriorityApplied.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketSetTunerPriority.Handler.class, PacketSetTunerPriority.class, packetId++, Side.SERVER);

        // Better Level Maintainer packets
        INSTANCE.registerMessage(PacketOpenMaintainerGui.Handler.class, PacketOpenMaintainerGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketUpdateMaintainerEntry.Handler.class, PacketUpdateMaintainerEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRunMaintainerEntry.Handler.class, PacketRunMaintainerEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSelectRecipe.Handler.class, PacketSelectRecipe.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketCraftableItemsSync.Handler.class, PacketCraftableItemsSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketMaintainerEntrySync.Handler.class, PacketMaintainerEntrySync.class, packetId++, Side.CLIENT);

        // AutoCrafter packets
        INSTANCE.registerMessage(PacketToggleCrafterEntry.Handler.class, PacketToggleCrafterEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketOpenCrafterSubGui.Handler.class, PacketOpenCrafterSubGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetCrafterBatch.Handler.class, PacketSetCrafterBatch.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetCrafterSpeed.Handler.class, PacketSetCrafterSpeed.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetCrafterPage.Handler.class, PacketSetCrafterPage.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketCrafterPageInit.Handler.class, PacketCrafterPageInit.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketReturnToCrafterGui.Handler.class, PacketReturnToCrafterGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketCrafterOverviewSync.Handler.class, PacketCrafterOverviewSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketCrafterRecipeSync.Handler.class, PacketCrafterRecipeSync.class, packetId++, Side.CLIENT);

        // Storage Monitor packets
        INSTANCE.registerMessage(PacketSetRefreshRate.Handler.class, PacketSetRefreshRate.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSelectMonitorContent.Handler.class, PacketSelectMonitorContent.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRequestMonitorContents.Handler.class, PacketRequestMonitorContents.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketMonitorContentsSync.Handler.class, PacketMonitorContentsSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketUpdateMonitorEntry.Handler.class, PacketUpdateMonitorEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoveMonitorEntry.Handler.class, PacketRemoveMonitorEntry.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetMatchMode.Handler.class, PacketSetMatchMode.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetHysteresisMode.Handler.class, PacketSetHysteresisMode.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetEmitterRedstonePower.Handler.class, PacketSetEmitterRedstonePower.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetEmitterRedstoneStrength.Handler.class, PacketSetEmitterRedstoneStrength.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketModifyStorageMonitorUpgradeSlot.Handler.class, PacketModifyStorageMonitorUpgradeSlot.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketToggleAlarmRegistration.Handler.class, PacketToggleAlarmRegistration.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSyncLevelMonitorAlarms.Handler.class, PacketSyncLevelMonitorAlarms.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketStorageEntryStateSync.Handler.class, PacketStorageEntryStateSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketSyncMonitorEntries.Handler.class, PacketSyncMonitorEntries.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketOpenStorageMonitorPollingRate.Handler.class, PacketOpenStorageMonitorPollingRate.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketStorageMonitorPollNow.Handler.class, PacketStorageMonitorPollNow.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketReturnToStorageMonitorGui.Handler.class, PacketReturnToStorageMonitorGui.class, packetId++, Side.SERVER);

        // Remote Storage Monitor packets
        INSTANCE.registerMessage(PacketRemoteMonitorOpenGui.Handler.class, PacketRemoteMonitorOpenGui.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketRemoteMonitorSync.Handler.class, PacketRemoteMonitorSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketRemoteMonitorContentsSync.Handler.class, PacketRemoteMonitorContentsSync.class, packetId++, Side.CLIENT);
        INSTANCE.registerMessage(PacketRemoteMonitorRequestContents.Handler.class, PacketRemoteMonitorRequestContents.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoteMonitorPollNow.Handler.class, PacketRemoteMonitorPollNow.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoteMonitorRequestSync.Handler.class, PacketRemoteMonitorRequestSync.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoteMonitorSelectSlot.Handler.class, PacketRemoteMonitorSelectSlot.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoteMonitorSetRefreshRate.Handler.class, PacketRemoteMonitorSetRefreshRate.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketRemoteMonitorSetSlidingWindow.Handler.class, PacketRemoteMonitorSetSlidingWindow.class, packetId++, Side.SERVER);
    }
}
