package com.ae2powertools.features.monitor.dependent;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
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

    boolean isHysteresisEnabled();

    void setHysteresisEnabled(boolean hysteresisEnabled);

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
     * Shared memory-card name used by both block and part variants of the same monitor host.
     */
    default String getMemoryCardName() {
        return getHostType().getMemoryCardName();
    }

    /**
     * Memory-card tooltip key shown by AE2's memory card item.
     */
    default String getMemoryCardTooltipKey() {
        return getMemoryCardName() + ".memory_card.tooltip";
    }

    /**
     * Whether this host exposes the shared AND/OR toggle in the monitor GUI.
     */
    default boolean supportsMatchMode() {
        return true;
    }

    /**
     * Whether this host exposes the shared hysteresis toggle and split-threshold UI.
     */
    default boolean supportsHysteresis() {
        return true;
    }

    /**
     * Whether this host allows cycling the per-entry comparison operator.
     */
    default boolean supportsEntryComparison() {
        return true;
    }

    /**
     * Whether this host supports per-player registration through the monitor GUI.
     */
    default boolean supportsPlayerRegistration() {
        return false;
    }

    /**
     * Whether the given player is currently subscribed to receive this host's state.
     */
    default boolean isPlayerRegistered(@Nullable EntityPlayer player) {
        return false;
    }

    /**
     * Toggles registration for the given player.
     *
     * @return true when the player ended up subscribing, false when they ended up unsubscribing
     *         or were ineligible for registration in the first place.
     */
    default boolean togglePlayerRegistration(@Nullable EntityPlayer player) {
        return false;
    }

    /**
     * Whether the container should drive refreshes while its GUI is open.
     * Used by hosts that intentionally sleep when nobody is subscribed to them.
     */
    default boolean shouldRefreshWhileGuiOpen() {
        return false;
    }

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
