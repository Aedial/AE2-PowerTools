package com.ae2powertools.features.scanner.data.client;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import com.ae2powertools.features.scanner.data.AbstractLocation;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;
import com.ae2powertools.features.scanner.data.ScannerTabId;


/**
 * Client data for a device that could not obtain a channel.
 */
public class MissingDeviceClient extends AbstractLocation {

    public final String dimensionName;
    public final ItemStack itemStack;
    public final String description;

    public MissingDeviceClient(BlockPos pos, int dimension, String dimensionName,
            ItemStack itemStack, String description) {
        super(pos, dimension);
        this.dimensionName = dimensionName;
        this.itemStack = itemStack != null ? itemStack.copy() : ItemStack.EMPTY;
        this.description = description;
    }

    public String getDisplayName() {
        return itemStack.isEmpty() ? description : itemStack.getDisplayName();
    }

    @Override
    public ScannerIssueKey getIssueKey() {
        return new ScannerIssueKey(ScannerTabId.MISSING_CHANNELS,
            dimension + ":" + pos.getX() + ':' + pos.getY() + ':' + pos.getZ());
    }
}
