package com.ae2powertools.features.scanner.data;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;


/**
 * Represents a channel chokepoint in the network - a location where channel demand exceeds capacity.
 * This is specifically for cables/connections at intersections where multiple paths converge.
 */
public class ChannelChokepoint {

    private final BlockPos pos;
    private final int dimension;
    private final String dimensionName;
    private final IBlockState blockState;
    private final String description;

    // Channel information
    private final int usedChannels;     // Channels actually being used (capped at capacity)
    private final int demandedChannels; // Channels that would be used if capacity was unlimited
    private final int capacity;         // Maximum channels this cable can carry

    // Per-direction breakdown: how many channels are flowing in each direction
    // This shows where channels are going TO (outgoing from controller perspective)
    private final List<DirectionFlow> connectionFlows;

    /**
     * Represents the channel flow through one connection of the chokepoint.
     */
    public static class DirectionFlow {
        public final EnumFacing direction;     // Direction of the connection (or null for internal)
        public final int channels;             // Number of channels going this way
        public final int demandedChannels;     // Channels that would flow if no limit
        public final BlockPos connectedPos;    // Position of the connected block
        public final String connectedDescription; // Description of connected device/cable

        public DirectionFlow(EnumFacing direction, int channels, int demandedChannels,
                BlockPos connectedPos, String connectedDescription) {
            this.direction = direction;
            this.channels = channels;
            this.demandedChannels = demandedChannels;
            this.connectedPos = connectedPos;
            this.connectedDescription = connectedDescription;
        }

        /**
         * Check if this direction has unsatisfied demand.
         */
        public boolean hasUnmetDemand() {
            return demandedChannels > channels;
        }
    }

    public ChannelChokepoint(BlockPos pos, int dimension, String dimensionName, IBlockState blockState,
            String description, int usedChannels, int demandedChannels, int capacity) {
        this.pos = pos;
        this.dimension = dimension;
        this.dimensionName = dimensionName;
        this.blockState = blockState;
        this.description = description;
        this.usedChannels = usedChannels;
        this.demandedChannels = demandedChannels;
        this.capacity = capacity;
        this.connectionFlows = new ArrayList<>();
    }

    public void addConnectionFlow(DirectionFlow flow) {
        connectionFlows.add(flow);
    }

    // ========== Getters ==========

    public BlockPos getPos() {
        return pos;
    }

    public int getDimension() {
        return dimension;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public IBlockState getBlockState() {
        return blockState;
    }

    public String getDescription() {
        return description;
    }

    public int getUsedChannels() {
        return usedChannels;
    }

    public int getDemandedChannels() {
        return demandedChannels;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<DirectionFlow> getConnectionFlows() {
        return connectionFlows;
    }

    /**
     * Check if this is actually a chokepoint (demand > capacity).
     */
    public boolean isChokepoint() {
        return demandedChannels > capacity;
    }

    /**
     * Get the number of channels that need to be shed to not have devices offline.
     */
    public int getExcessChannels() {
        return Math.max(0, demandedChannels - capacity);
    }

    /**
     * Check if this is an intersection (3+ connections).
     */
    public boolean isIntersection() {
        return connectionFlows.size() >= 3;
    }

    /**
     * Calculate distance from a given position.
     */
    public double getDistanceFrom(BlockPos from) {
        double dx = pos.getX() - from.getX();
        double dy = pos.getY() - from.getY();
        double dz = pos.getZ() - from.getZ();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChannelChokepoint)) return false;

        ChannelChokepoint other = (ChannelChokepoint) obj;

        return dimension == other.dimension && pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return 31 * pos.hashCode() + dimension;
    }
}
