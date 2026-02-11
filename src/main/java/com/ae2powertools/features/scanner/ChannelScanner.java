package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.IAEMultiBlock;
import appeng.tile.networking.TileController;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.features.scanner.ChannelChokepoint.DirectionFlow;


/**
 * Scans an AE2 network for channel chokepoints - locations where channel demand exceeds cable capacity.
 *
 * The algorithm:
 * 1. BFS from controller to find all channel-requiring devices and their paths to controller
 * 2. For each cable/connection, calculate:
 *    - Actual channels being used (from IGridConnection.getUsedChannels())
 *    - Demanded channels (count of all REQUIRE_CHANNEL devices behind it)
 * 3. Report chokepoints where demand > capacity at intersections (3+ connections)
 *
 * Note: AE2 doesn't expose "would-be" channel usage, so we must calculate demand ourselves.
 *
 * Multiblock handling: AE2 multiblocks (crafting CPUs, spatial pylons, etc.) are treated as single
 * entities consuming 1 channel total. When we encounter a multiblock cluster during BFS, we only
 * process its first-encountered node and treat subsequent nodes as part of the same entity.
 */
public class ChannelScanner {

    private static final int MAX_NODES_PER_TICK = 100;
    private static final int MAX_TOTAL_NODES = 1000000;

    // Channel capacities (AE2 defaults, but we read from config if possible)
    private static final int NORMAL_CAPACITY = 8;
    private static final int DENSE_CAPACITY = 32;

    private final IGrid grid;
    private final World world;

    // Scan state
    private boolean isComplete = false;
    private boolean hasController = false;
    private int nodesProcessed = 0;
    private String statusMessage = "";

    // BFS tracking - Phase 1: Build tree structure from controller
    private final Queue<BfsNode> openList = new LinkedList<>();
    private final Map<IGridNode, BfsNode> nodeMap = new HashMap<>();
    private final Set<IGridNode> controllerNodes = new HashSet<>();

    // Multiblock handling: map clusters to their representative BfsNode (first-encountered node)
    // All nodes in the same cluster share the same representative and count as 1 channel consumer
    private final Map<IAECluster, BfsNode> clusterRepresentatives = new HashMap<>();

    // Phase 2: Calculate channel demand by counting devices behind each node
    private boolean phase1Complete = false;
    private final List<BfsNode> leafNodes = new ArrayList<>();
    private int demandPhaseIndex = 0;

    // Results
    private final Set<ChannelChokepoint> chokepoints = new HashSet<>();
    private final Set<MissingChannelDevice> missingDevices = new HashSet<>();

    /**
     * BFS node that tracks the tree structure and channel counts.
     */
    private static class BfsNode {
        final IGridNode gridNode;
        final BfsNode parent;
        final IGridConnection connectionFromParent;
        final int depth;

        // Children in the BFS tree (nodes further from controller)
        final List<BfsNode> children = new ArrayList<>();

        // Channel demand: total devices requiring channels in this subtree (including self)
        int channelDemand = 0;

        // Is this node a channel consumer? For multiblocks, only the representative consumes a channel.
        boolean requiresChannel = false;

        // For multiblock handling: if this node is part of a cluster, this points to the representative
        // If null, this node is not part of a multiblock cluster
        // If this == clusterRepresentative, this node IS the representative
        BfsNode clusterRepresentative = null;

        BfsNode(IGridNode gridNode, BfsNode parent, IGridConnection connection, int depth) {
            this.gridNode = gridNode;
            this.parent = parent;
            this.connectionFromParent = connection;
            this.depth = depth;
        }

        boolean isClusterRepresentative() {
            return clusterRepresentative == this;
        }

        boolean isPartOfCluster() {
            return clusterRepresentative != null;
        }
    }

    public ChannelScanner(IGrid grid, World world) {
        this.grid = grid;
        this.world = world;

        initialize();
    }

    /**
     * Initialize the scanner by finding controllers and starting BFS.
     */
    private void initialize() {
        try {
            IPathingGrid pathingGrid = grid.getCache(IPathingGrid.class);

            if (pathingGrid == null) {
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.channel.no_pathing_grid");
                isComplete = true;

                return;
            }

            ControllerState state = pathingGrid.getControllerState();
            hasController = (state == ControllerState.CONTROLLER_ONLINE);

            if (!hasController) {
                // Ad-hoc network - channel chokepoints don't make sense here
                // (all devices share 8 channels, all-or-nothing)
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.channel.no_controller");
                isComplete = true;

                return;
            }

            // Start BFS from all controller blocks
            for (IGridNode node : grid.getMachines(TileController.class)) {
                BfsNode bfsNode = new BfsNode(node, null, null, 0);
                openList.add(bfsNode);
                nodeMap.put(node, bfsNode);
                controllerNodes.add(node);
            }

            if (openList.isEmpty()) {
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.channel.no_controller");
                isComplete = true;

                return;
            }

            statusMessage = I18n.translateToLocal("ae2powertools.scanner.channel.building_tree");
        } catch (Exception e) {
            AE2PowerTools.LOGGER.error("Error initializing channel scanner", e);
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.error", e.getMessage());
            isComplete = true;
        }
    }

    /**
     * Process a batch of work. Call each tick.
     * @return true if scan is complete
     */
    public boolean processBatch() {
        if (isComplete) return true;

        // Phase 1: BFS to build tree structure
        if (!phase1Complete) return processTreeBuildingPhase();

        // Phase 2: Calculate demands bottom-up
        return processDemandCalculationPhase();
    }

    /**
     * Phase 1: Build tree structure from controller via BFS.
     */
    private boolean processTreeBuildingPhase() {
        int processed = 0;

        while (!openList.isEmpty() && processed < MAX_NODES_PER_TICK) {
            if (nodesProcessed >= MAX_TOTAL_NODES) {
                statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.too_large",
                    MAX_TOTAL_NODES);
                isComplete = true;

                return true;
            }

            BfsNode current = openList.poll();
            processNodeConnections(current);
            processed++;
            nodesProcessed++;
        }

        if (openList.isEmpty()) {
            phase1Complete = true;
            statusMessage = I18n.translateToLocal("ae2powertools.scanner.channel.calculating_demand");

            // Collect leaf nodes for bottom-up traversal
            for (BfsNode node : nodeMap.values()) {
                if (node.children.isEmpty()) leafNodes.add(node);
            }
        } else {
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.channel.building_tree_progress",
                nodesProcessed);
        }

        return false;
    }

    /**
     * Process connections from a node during Phase 1.
     */
    private void processNodeConnections(BfsNode current) {
        IGridNode node = current.gridNode;
        IGridHost currentHost = node.getMachine();
        IAECluster currentCluster = getClusterOf(currentHost);

        // Handle cluster membership for this node
        if (currentCluster != null) {
            BfsNode representative = clusterRepresentatives.get(currentCluster);

            if (representative == null) {
                // First node of this cluster - it becomes the representative
                clusterRepresentatives.put(currentCluster, current);
                current.clusterRepresentative = current;

                // Only the representative checks for channel requirement
                if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) current.requiresChannel = true;
            } else {
                // Part of existing cluster - point to representative, don't require a channel
                current.clusterRepresentative = representative;
                current.requiresChannel = false;
            }
        } else {
            // Not a multiblock - check channel requirement normally
            if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) current.requiresChannel = true;
        }

        for (IGridConnection connection : node.getConnections()) {
            IGridNode neighbor = connection.getOtherSide(node);
            if (neighbor == null) continue;

            // Skip if already visited (we came from there or it's already in tree)
            if (nodeMap.containsKey(neighbor)) continue;

            // Add to tree
            BfsNode childNode = new BfsNode(neighbor, current, connection, current.depth + 1);
            current.children.add(childNode);
            openList.add(childNode);
            nodeMap.put(neighbor, childNode);
        }
    }

    /**
     * Get the cluster that a grid host belongs to, or null if not a multiblock.
     */
    private IAECluster getClusterOf(IGridHost host) {
        if (host instanceof IAEMultiBlock) return ((IAEMultiBlock) host).getCluster();

        return null;
    }

    /**
     * Phase 2: Calculate channel demand bottom-up and identify chokepoints.
     */
    private boolean processDemandCalculationPhase() {
        // Process in batches by propagating demand from leaves to root
        int processed = 0;

        while (demandPhaseIndex < leafNodes.size() && processed < MAX_NODES_PER_TICK) {
            BfsNode leaf = leafNodes.get(demandPhaseIndex);
            propagateDemand(leaf);
            demandPhaseIndex++;
            processed++;
        }

        if (demandPhaseIndex >= leafNodes.size()) {
            // All demands propagated, now identify chokepoints and missing channels
            identifyChokepoints();
            identifyMissingChannelDevices();
            isComplete = true;

            if (chokepoints.isEmpty()) {
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.channel.no_chokepoints");
            } else {
                statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.channel.found",
                    chokepoints.size());
            }
        } else {
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.channel.calculating_progress",
                demandPhaseIndex, leafNodes.size());
        }

        return isComplete;
    }

    /**
     * Propagate channel demand from a leaf node up to root.
     */
    private void propagateDemand(BfsNode leaf) {
        BfsNode current = leaf;

        // Calculate this node's demand (self + all children)
        while (current != null) {
            int demand = current.requiresChannel ? 1 : 0;

            for (BfsNode child : current.children) demand += child.channelDemand;

            // Only update if we calculated a new value
            // (avoid counting children multiple times when processing multiple leaves)
            if (current.channelDemand == 0 || demand > current.channelDemand) current.channelDemand = demand;

            current = current.parent;
        }
    }

    /**
     * Identify chokepoints by checking each node's connections.
     */
    private void identifyChokepoints() {
        for (BfsNode bfsNode : nodeMap.values()) {
            // Skip controller nodes
            if (controllerNodes.contains(bfsNode.gridNode)) continue;

            // Only check intersections (3+ connections including parent)
            int connectionCount = bfsNode.children.size() + (bfsNode.parent != null ? 1 : 0);
            if (connectionCount < 3) continue;

            // Get this node's capacity
            int capacity = getNodeCapacity(bfsNode.gridNode);
            if (capacity == 0) continue; // Controller or similar - can't be chokepoint

            // Calculate demand through this node (all children combined)
            int totalDemand = bfsNode.channelDemand;

            // Get actual used channels (max of all outgoing connections)
            int actualUsed = 0;
            if (bfsNode.connectionFromParent != null) {
                actualUsed = bfsNode.connectionFromParent.getUsedChannels();
            }

            // Only report if it's actually a chokepoint
            if (totalDemand <= capacity) continue;

            // TODO: should we report nodes that have a branch at 0?
            //       They do not seem particularly useful to the user.

            // Create chokepoint entry
            BlockPos pos = getNodePosition(bfsNode.gridNode);
            if (pos == null) continue;

            int dimension = getNodeDimension(bfsNode.gridNode);
            String dimName = getNodeDimensionName(bfsNode.gridNode);
            World nodeWorld = getNodeWorld(bfsNode.gridNode);
            IBlockState blockState = (nodeWorld != null && nodeWorld.isBlockLoaded(pos))
                ? nodeWorld.getBlockState(pos) : Blocks.AIR.getDefaultState();
            String description = getNodeDescription(bfsNode.gridNode);

            ChannelChokepoint chokepoint = new ChannelChokepoint(
                pos, dimension, dimName, blockState, description,
                actualUsed, totalDemand, capacity
            );

            // Add per-direction breakdown
            addConnectionFlows(chokepoint, bfsNode);

            chokepoints.add(chokepoint);
        }
    }

    /**
     * Identify devices that require a channel but didn't get one.
     */
    private void identifyMissingChannelDevices() {
        for (BfsNode bfsNode : nodeMap.values()) {
            IGridNode node = bfsNode.gridNode;

            // Skip if doesn't require a channel
            if (!node.hasFlag(GridFlags.REQUIRE_CHANNEL)) continue;

            // Check if channel requirements are met
            if (node.meetsChannelRequirements()) continue;

            // This device is missing a channel
            BlockPos pos = getNodePosition(node);
            if (pos == null) continue;

            int dimension = getNodeDimension(node);
            String dimName = getNodeDimensionName(node);

            // Get item representation for icon display
            ItemStack itemStack = ItemStack.EMPTY;

            try {
                itemStack = node.getGridBlock().getMachineRepresentation();
            } catch (Exception e) {
                // Fall back to empty stack
            }

            String description = getNodeDescription(node);
            MissingChannelDevice device = new MissingChannelDevice(
                pos, dimension, dimName, itemStack, description
            );
            missingDevices.add(device);
        }
    }

    /**
     * Add connection flow information to a chokepoint.
     */
    private void addConnectionFlows(ChannelChokepoint chokepoint, BfsNode bfsNode) {
        // Add parent direction (toward controller)
        if (bfsNode.parent != null) {
            BlockPos parentPos = getNodePosition(bfsNode.parent.gridNode);
            EnumFacing direction = getConnectionDirection(bfsNode.gridNode, bfsNode.connectionFromParent);
            String parentDesc = getNodeDescription(bfsNode.parent.gridNode);

            // Parent carries all the demand (toward controller)
            int parentChannels = bfsNode.connectionFromParent != null
                ? bfsNode.connectionFromParent.getUsedChannels() : 0;

            String toControllerSuffix = " " + I18n.translateToLocal("ae2powertools.scanner.channel.to_controller");
            DirectionFlow parentFlow = new DirectionFlow(
                direction, parentChannels, bfsNode.channelDemand,
                parentPos, parentDesc + toControllerSuffix
            );
            chokepoint.addConnectionFlow(parentFlow);
        }

        // Add children directions (away from controller)
        for (BfsNode child : bfsNode.children) {
            BlockPos childPos = getNodePosition(child.gridNode);
            EnumFacing direction = getConnectionDirection(bfsNode.gridNode, child.connectionFromParent);
            String childDesc = getNodeDescription(child.gridNode);

            int childChannels = child.connectionFromParent != null
                ? child.connectionFromParent.getUsedChannels() : 0;

            DirectionFlow childFlow = new DirectionFlow(
                direction, childChannels, child.channelDemand,
                childPos, childDesc
            );
            chokepoint.addConnectionFlow(childFlow);
        }
    }

    /**
     * Get the direction of a connection from a node's perspective.
     */
    private EnumFacing getConnectionDirection(IGridNode node, IGridConnection connection) {
        if (connection == null || !connection.hasDirection()) return null;

        AEPartLocation partLoc = connection.getDirection(node);
        if (partLoc == AEPartLocation.INTERNAL) return null;

        return partLoc.getFacing();
    }

    /**
     * Get the channel capacity for a node based on its flags.
     */
    private int getNodeCapacity(IGridNode node) {
        if (node.hasFlag(GridFlags.CANNOT_CARRY)) return 0; // Controllers don't carry channels
        if (node.hasFlag(GridFlags.DENSE_CAPACITY)) return DENSE_CAPACITY;

        return NORMAL_CAPACITY;
    }

    /**
     * Get the block position of a grid node.
     */
    private BlockPos getNodePosition(IGridNode node) {
        if (node == null) return null;

        IGridHost host = node.getMachine();
        if (host instanceof TileEntity) return ((TileEntity) host).getPos();

        try {
            DimensionalCoord coord = node.getGridBlock().getLocation();
            if (coord != null) return coord.getPos();
        } catch (Exception e) {
            // Fall through
        }

        return null;
    }

    /**
     * Get a human-readable description of a grid node.
     * Uses the machine representation's display name for localized output.
     */
    private String getNodeDescription(IGridNode node) {
        if (node == null) return I18n.translateToLocal("ae2powertools.common.unknown");

        // Try to get the localized name from the machine representation
        try {
            ItemStack representation = node.getGridBlock().getMachineRepresentation();

            if (!representation.isEmpty()) return representation.getDisplayName();
        } catch (Exception e) {
            // Fall through to class name fallback
        }

        // Fallback: clean up class name
        IGridHost host = node.getMachine();
        if (host == null) return I18n.translateToLocal("ae2powertools.common.unknown");

        String className = host.getClass().getSimpleName();

        if (className.startsWith("Tile")) {
            className = className.substring(4);
        } else if (className.startsWith("Part")) {
            className = className.substring(4);
        }

        return className;
    }

    /**
     * Get the world of a grid node.
     */
    private World getNodeWorld(IGridNode node) {
        if (node == null) return null;

        IGridHost host = node.getMachine();
        if (host instanceof TileEntity) return ((TileEntity) host).getWorld();

        try {
            DimensionalCoord coord = node.getGridBlock().getLocation();
            if (coord != null) return coord.getWorld();
        } catch (Exception e) {
            // Fall through
        }

        return world; // Fallback to scanner's world
    }

    /**
     * Get the dimension ID of a grid node.
     */
    private int getNodeDimension(IGridNode node) {
        World nodeWorld = getNodeWorld(node);
        if (nodeWorld != null) return nodeWorld.provider.getDimension();

        return world.provider.getDimension(); // Fallback
    }

    /**
     * Get the dimension name of a grid node.
     */
    private String getNodeDimensionName(IGridNode node) {
        World nodeWorld = getNodeWorld(node);
        if (nodeWorld != null) return nodeWorld.provider.getDimensionType().getName();

        return world.provider.getDimensionType().getName(); // Fallback
    }

    // ========== Getters ==========

    public boolean isComplete() {
        return isComplete;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Set<ChannelChokepoint> getChokepoints() {
        return chokepoints;
    }

    public Set<MissingChannelDevice> getMissingDevices() {
        return missingDevices;
    }

    public int getNodesProcessed() {
        return nodesProcessed;
    }

    public boolean hasController() {
        return hasController;
    }
}
