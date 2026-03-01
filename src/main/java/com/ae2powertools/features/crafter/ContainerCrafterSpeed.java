package com.ae2powertools.features.crafter;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;


/**
 * Container for the Speed configuration GUI.
 */
public class ContainerCrafterSpeed extends AEBaseContainer {

    private final TileAutoCrafter tile;

    @GuiSync(0)
    public int speedTicks = TileAutoCrafter.DEFAULT_SPEED_TICKS;

    public ContainerCrafterSpeed(InventoryPlayer playerInv, TileAutoCrafter tile) {
        super(playerInv, tile, null);
        this.tile = tile;

        // Initialize synced value from tile immediately (server-side)
        // This ensures first sync sends correct value to client
        if (Platform.isServer()) this.speedTicks = tile.getSpeedTicks();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.speedTicks = tile.getSpeedTicks();
    }

    public TileAutoCrafter getTile() {
        return tile;
    }

    public void setSpeedTicks(int value) {
        int clamped = Math.max(TileAutoCrafter.MIN_SPEED_TICKS, value);
        this.tile.setSpeedTicks(clamped);
        this.speedTicks = clamped;
    }
}
