package com.ae2powertools.features.monitor.dependent;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.me.helpers.AENetworkProxy;


/**
 * Callback interface for anything hosting a {@link MonitorLogic}.
 * Implemented by tiles and parts that monitor AE2 network resources.
 */
public interface IMonitorLogicHost {

    World getHostWorld();

    BlockPos getHostPos();

    /**
     * Returns the AE2 network proxy for direct grid queries.
     * Used by MonitorLogic to look up resource quantities via findPrecise().
     */
    AENetworkProxy getProxy();

    /**
     * Mark the host as dirty and persist NBT.
     */
    void markDirtyAndSave();

    /**
     * Called when the overall condition (AND/OR across all entries) changes.
     * The host can use this to update redstone state, display color, etc.
     */
    void onConditionChanged(boolean oldMet, boolean newMet);

    /**
     * Called when the refresh rate changes.
     * All hosts use IGridTickable and should re-register with the tick manager
     * to apply the new TickingRequest bounds.
     */
    default void onRefreshRateChanged() {}
}
