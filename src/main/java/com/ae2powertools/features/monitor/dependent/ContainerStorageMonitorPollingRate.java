package com.ae2powertools.features.monitor.dependent;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;


/**
 * Container for the Storage Emitter / Display polling rate sub-GUI.
 * Trivially syncs the host's refresh rate, just like CELLS' ContainerPollingRate.
 *
 * The main {@link ContainerStorageMonitor} also syncs the refresh rate, but a
 * dedicated container is needed because Minecraft's GuiHandler architecture
 * requires a unique container per GUI screen (and the polling rate sub-GUI is
 * a separate priority-style screen, opened via {@link PacketOpenStorageMonitorPollingRate}).
 */
public class ContainerStorageMonitorPollingRate extends AEBaseContainer {

    private final IStorageMonitorHost host;

    @GuiSync(0)
    public long refreshRate;

    public ContainerStorageMonitorPollingRate(InventoryPlayer playerInv, IStorageMonitorHost host) {
        super(playerInv, host);
        this.host = host;

        if (Platform.isServer()) this.refreshRate = host.getRefreshRate();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.refreshRate = host.getRefreshRate();
    }

    public IStorageMonitorHost getHost() {
        return host;
    }
}
