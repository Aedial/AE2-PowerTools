package com.ae2powertools.features.crafter;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;


/**
 * Container for the Batch Size configuration GUI.
 */
public class ContainerCrafterBatch extends AEBaseContainer {

    private final TileAutoCrafter tile;

    @GuiSync(0)
    public long batchSize = TileAutoCrafter.DEFAULT_BATCH_SIZE;

    public ContainerCrafterBatch(InventoryPlayer playerInv, TileAutoCrafter tile) {
        super(playerInv, tile, null);
        this.tile = tile;

        // Initialize synced value from tile immediately (server-side)
        // This ensures first sync sends correct value to client
        if (Platform.isServer()) this.batchSize = tile.getBatchSize();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) this.batchSize = tile.getBatchSize();
    }

    public TileAutoCrafter getTile() {
        return tile;
    }

    public void setBatchSize(int value) {
        int clamped = Math.max(TileAutoCrafter.MIN_BATCH_SIZE, value);
        this.tile.setBatchSize(clamped);
        this.batchSize = clamped;
    }
}
