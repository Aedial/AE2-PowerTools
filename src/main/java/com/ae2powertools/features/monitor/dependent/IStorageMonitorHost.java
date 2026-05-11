package com.ae2powertools.features.monitor.dependent;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.util.AEPartLocation;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;


/**
 * Interface for hosts of the shared storage monitor GUI.
 * Both block tiles and cable parts implement this so the same container/GUI can serve both.
 */
public interface IStorageMonitorHost {

    // --- Refresh rate ---

    int getRefreshRate();

    void setRefreshRate(int rate);

    // --- Monitored entries (resource + comparison + threshold per entry) ---

    List<MonitoredEntry> getEntries();

    void setEntries(List<MonitoredEntry> entries);

    void setEntry(int index, MonitoredEntry entry);

    /**
     * Returns the first entry's resource for backward-compatible single-resource display.
     */
    @Nullable
    MonitoredResource getFirstResource();

    // --- Match mode ---

    MatchMode getMatchMode();

    void setMatchMode(MatchMode mode);

    // --- Condition ---

    /**
     * Whether the overall condition (AND/OR across all entries) is currently met.
     */
    boolean isConditionMet();

    /**
     * Returns the first entry's last looked-up quantity, for display rendering.
     */
    long getFirstEntryQuantity();

    // --- Host type ---

    MonitorHostType getHostType();

    /**
     * Returns an ItemStack representing this host for sub-GUI back-button icons.
     */
    ItemStack getBackButtonStack();

    World getHostWorld();

    BlockPos getHostPos();

    /**
     * Returns the part side this host occupies on its cable bus, or
     * {@link AEPartLocation#INTERNAL} for block-tile hosts. Used by network packets
     * and the GUI handler to disambiguate between block tiles and cable parts when
     * resolving a host from a {@link BlockPos}.
     */
    AEPartLocation getHostSide();

    /**
     * Returns the shared monitor logic for direct grid queries.
     */
    MonitorLogic getMonitorLogic();
}
