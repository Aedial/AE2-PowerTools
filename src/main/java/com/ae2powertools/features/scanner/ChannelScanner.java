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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
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
import appeng.block.networking.BlockCableBus;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.helpers.IInterfaceHost;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.IAEMultiBlock;
import appeng.parts.misc.PartStorageBus;
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
    // Stored as ITextComponent so the message can be serialized as JSON and re-translated
    // client-side in the player's locale (server-side I18n only has English fallback).
    private ITextComponent statusMessage = new net.minecraft.util.text.TextComponentString("");

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
    private final Set<FatalNetworkError> fatalErrors = new HashSet<>();

    /**
     * Target key used by both storage buses and interfaces.
     * The side is only relevant when the target block can host multiple parts.
     */
    private static class TargetLocation {
        final int dimension;
        final BlockPos pos;
        final EnumFacing side;

        TargetLocation(int dimension, BlockPos pos, EnumFacing side) {
            this.dimension = dimension;
            this.pos = pos;
            this.side = side;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TargetLocation)) return false;

            TargetLocation other = (TargetLocation) obj;
            return dimension == other.dimension && pos.equals(other.pos) && side == other.side;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(dimension);
            result = 31 * result + pos.hashCode();
            result = 31 * result + (side != null ? side.hashCode() : 0);
            return result;
        }
    }

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

        BfsNode(IGridNode gridNode, BfsNode parent, IGridConnection connection, int depth) {
            this.gridNode = gridNode;
            this.parent = parent;
            this.connectionFromParent = connection;
            this.depth = depth;
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
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.no_pathing_grid");
                isComplete = true;

                return;
            }

            ControllerState state = pathingGrid.getControllerState();
            hasController = (state == ControllerState.CONTROLLER_ONLINE);

            if (!hasController) {
                // Ad-hoc network - channel chokepoints don't make sense here
                // (all devices share 8 channels, all-or-nothing)
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.no_controller");
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
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.no_controller");
                isComplete = true;

                return;
            }

            statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.building_tree");
        } catch (Exception e) {
            AE2PowerTools.LOGGER.error("Error initializing channel scanner", e);
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.error", e.getMessage());
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
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.too_large",
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
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.calculating_demand");

            // Collect leaf nodes for bottom-up traversal
            for (BfsNode node : nodeMap.values()) {
                if (node.children.isEmpty()) leafNodes.add(node);
            }
        } else {
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.building_tree_progress",
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

                // Only the representative checks for channel requirement
                if (node.hasFlag(GridFlags.REQUIRE_CHANNEL)) current.requiresChannel = true;
            } else {
                // Part of existing cluster - point to representative, don't require a channel
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
            identifyFatalErrors();
            isComplete = true;

            if (chokepoints.isEmpty()) {
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.no_chokepoints");
            } else {
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.found",
                    chokepoints.size());
            }
        } else {
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.channel.calculating_progress",
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
            String description = ScannerTextHelper.getNodeDescription(bfsNode.gridNode);

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

            String description = ScannerTextHelper.getNodeDescription(node);
            MissingChannelDevice device = new MissingChannelDevice(
                pos, dimension, dimName, itemStack, description
            );
            missingDevices.add(device);
        }
    }

    /**
     * Detect fatal storage configuration problems after the tree is fully built.
     * This pass only looks at active nodes we already discovered on the main grid.
     */
    private void identifyFatalErrors() {
        Map<TargetLocation, List<IGridNode>> storageBusesByExactTarget = new HashMap<>();
        Map<TargetLocation, List<IGridNode>> storageBusesByDuplicateTarget = new HashMap<>();
        Set<TargetLocation> interfaceTargets = new HashSet<>();

        for (BfsNode bfsNode : nodeMap.values()) {
            IGridNode node = bfsNode.gridNode;

            TargetLocation storageBusTarget = getStorageBusTarget(node);
            if (storageBusTarget != null) {
                storageBusesByExactTarget.computeIfAbsent(storageBusTarget, key -> new ArrayList<>()).add(node);

                TargetLocation duplicateTarget = getDuplicateStorageTargetKey(node, storageBusTarget);
                storageBusesByDuplicateTarget.computeIfAbsent(duplicateTarget, key -> new ArrayList<>()).add(node);
            }

            interfaceTargets.addAll(getInterfaceTargets(node));
        }

        for (Map.Entry<TargetLocation, List<IGridNode>> entry : storageBusesByDuplicateTarget.entrySet()) {
            List<IGridNode> storageBuses = entry.getValue();

            if (storageBuses.size() > 1) {
                for (IGridNode storageBus : storageBuses) {
                    addFatalError(FatalNetworkError.Category.DUPLICATE_STORAGE_TARGET, storageBus, entry.getKey().pos);
                }
            }
        }

        for (Map.Entry<TargetLocation, List<IGridNode>> entry : storageBusesByExactTarget.entrySet()) {
            TargetLocation target = entry.getKey();
            List<IGridNode> storageBuses = entry.getValue();

            if (interfaceTargets.contains(target)) {
                for (IGridNode storageBus : storageBuses) {
                    addFatalError(FatalNetworkError.Category.SAME_NETWORK_INTERFACE_LINK, storageBus, target.pos);
                }
            }
        }
    }

    private TargetLocation getDuplicateStorageTargetKey(IGridNode node, TargetLocation exactTarget) {
        if (isCableMultipartTarget(node, exactTarget.pos)) return exactTarget;

        return new TargetLocation(exactTarget.dimension, exactTarget.pos, null);
    }

    private boolean isCableMultipartTarget(IGridNode node, BlockPos targetPos) {
        World nodeWorld = getNodeWorld(node);
        if (nodeWorld == null || !nodeWorld.isBlockLoaded(targetPos)) return false;

        return nodeWorld.getBlockState(targetPos).getBlock() instanceof BlockCableBus;
    }

    private TargetLocation getStorageBusTarget(IGridNode node) {
        IGridHost host = node.getMachine();
        TileEntity hostTile;
        EnumFacing facing;

        if (host instanceof PartStorageBus) {
            PartStorageBus storageBus = (PartStorageBus) host;
            if (storageBus.getHost() == null) return null;

            hostTile = storageBus.getHost().getTile();
            facing = storageBus.getSide().getFacing();
        } else if (host instanceof PartFluidStorageBus) {
            PartFluidStorageBus storageBus = (PartFluidStorageBus) host;
            if (storageBus.getHost() == null) return null;

            hostTile = storageBus.getHost().getTile();
            facing = storageBus.getSide().getFacing();
        } else {
            return null;
        }

        if (hostTile == null || hostTile.getWorld() == null || facing == null) return null;

        int dimension = hostTile.getWorld().provider.getDimension();
        BlockPos targetPos = hostTile.getPos().offset(facing);
        EnumFacing targetSide = facing.getOpposite();
        return new TargetLocation(dimension, targetPos, targetSide);
    }

    private Set<TargetLocation> getInterfaceTargets(IGridNode node) {
        Set<TargetLocation> targets = new HashSet<>();
        IGridHost host = node.getMachine();
        if (!(host instanceof IInterfaceHost)) return targets;

        IInterfaceHost interfaceHost = (IInterfaceHost) host;
        TileEntity tile = interfaceHost.getTileEntity();
        if (tile == null || tile.getWorld() == null) return targets;

        int dimension = tile.getWorld().provider.getDimension();
        for (EnumFacing side : interfaceHost.getTargets()) {
            if (side == null) continue;

            targets.add(new TargetLocation(dimension, tile.getPos(), side));
        }

        return targets;
    }

    private void addFatalError(FatalNetworkError.Category category, IGridNode node, BlockPos pos) {
        if (pos == null) return;

        int dimension = getNodeDimension(node);
        String dimName = getNodeDimensionName(node);
        String description = ScannerTextHelper.getNodeDescription(node);
        BlockPos sourcePos = getNodePosition(node);

        fatalErrors.add(new FatalNetworkError(category, pos, dimension, dimName, description, sourcePos));
    }

    /**
     * Add connection flow information to a chokepoint.
     */
    private void addConnectionFlows(ChannelChokepoint chokepoint, BfsNode bfsNode) {
        // Add parent direction (toward controller)
        if (bfsNode.parent != null) {
            BlockPos parentPos = getNodePosition(bfsNode.parent.gridNode);
            EnumFacing direction = getConnectionDirection(bfsNode.gridNode, bfsNode.connectionFromParent);
            String parentDesc = ScannerTextHelper.getNodeDescription(bfsNode.parent.gridNode);

            // Parent carries all the demand (toward controller)
            int parentChannels = bfsNode.connectionFromParent != null
                ? bfsNode.connectionFromParent.getUsedChannels() : 0;

            String parentDescWithDirection = ScannerTextHelper.appendTranslatedSuffix(
                parentDesc,
                "ae2powertools.scanner.channel.to_controller"
            );
            DirectionFlow parentFlow = new DirectionFlow(
                direction, parentChannels, bfsNode.channelDemand,
                parentPos, parentDescWithDirection
            );
            chokepoint.addConnectionFlow(parentFlow);
        }

        // Add children directions (away from controller)
        for (BfsNode child : bfsNode.children) {
            BlockPos childPos = getNodePosition(child.gridNode);
            EnumFacing direction = getConnectionDirection(bfsNode.gridNode, child.connectionFromParent);
            String childDesc = ScannerTextHelper.getNodeDescription(child.gridNode);

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

    public ITextComponent getStatusMessage() {
        return statusMessage;
    }

    public Set<ChannelChokepoint> getChokepoints() {
        return chokepoints;
    }

    public Set<MissingChannelDevice> getMissingDevices() {
        return missingDevices;
    }

    public Set<FatalNetworkError> getFatalErrors() {
        return fatalErrors;
    }

    public int getNodesProcessed() {
        return nodesProcessed;
    }

    public boolean hasController() {
        return hasController;
    }
}
