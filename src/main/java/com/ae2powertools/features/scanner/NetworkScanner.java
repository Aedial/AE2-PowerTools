package com.ae2powertools.features.scanner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;

import com.google.common.collect.ImmutableSetMultimap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.util.DimensionalCoord;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.IAEMultiBlock;
import appeng.tile.networking.TileController;

import com.ae2powertools.AE2PowerTools;


/**
 * Detects loops and unloaded chunks in AE2 networks by performing BFS from the controller and tracking paths.
 * A loop is detected when a node is reached through two different paths.
 *
 * Note: getConnections() only returns same-grid connections. Quartz fibers create separate grids
 * and won't appear here. P2P tunnels create INTERNAL connections between proxies.
 */
public class NetworkScanner {

    private static final int MAX_NODES_PER_TICK = 100;
    private static final int MAX_TOTAL_NODES = 1000000;

    private final IGrid grid;
    private final World world;

    // Cache of forced chunks per dimension (lazy-loaded)
    private final Map<Integer, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>> forcedChunksCache = new HashMap<>();

    // BFS state
    private final Queue<PathNode> openList = new LinkedList<>();
    private final Map<IGridNode, PathNode> visitedNodes = new HashMap<>();
    private final Set<IssueLocation> detectedLoops = new HashSet<>();
    private final Set<ChunkLocation> unloadedChunks = new HashSet<>();

    // Track multiblock clusters and where they were first entered from outside.
    // If we enter the same cluster from outside again, that indicates a loop.
    private final Map<IAECluster, BlockPos> clusterEntryPoints = new HashMap<>();

    // Channel scanner (runs after main scan completes)
    private ChannelScanner channelScanner = null;
    private boolean channelScanStarted = false;

    // Status
    private boolean isComplete = false;
    private boolean hasController = false;
    private int nodesProcessed = 0;
    private String statusMessage = "";

    /**
     * Wrapper to track the path to each node during BFS.
     */
    private static class PathNode {
        final IGridNode node;
        final PathNode parent;
        final IGridConnection connectionFromParent;
        final int depth;

        PathNode(IGridNode node, PathNode parent, IGridConnection connection, int depth) {
            this.node = node;
            this.parent = parent;
            this.connectionFromParent = connection;
            this.depth = depth;
        }
    }

    public NetworkScanner(IGrid grid, World world) {
        this.grid = grid;
        this.world = world;

        initialize();
    }

    /**
     * Initialize the BFS by finding controller(s) and starting from them.
     */
    private void initialize() {
        try {
            IPathingGrid pathingGrid = grid.getCache(IPathingGrid.class);

            if (pathingGrid == null) {
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.no_pathing_grid");
                isComplete = true;

                return;
            }

            ControllerState state = pathingGrid.getControllerState();
            hasController = (state == ControllerState.CONTROLLER_ONLINE);

            if (!hasController) {
                // Controllerless network - start from any node
                for (IGridNode node : grid.getNodes()) {
                    PathNode pathNode = new PathNode(node, null, null, 0);
                    openList.add(pathNode);
                    visitedNodes.put(node, pathNode);
                    break; // Just start from one node
                }

                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.scanning_controllerless");
            } else {
                // Start from all controller blocks
                for (IGridNode node : grid.getMachines(TileController.class)) {
                    PathNode pathNode = new PathNode(node, null, null, 0);
                    openList.add(pathNode);
                    visitedNodes.put(node, pathNode);
                }

                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.scanning_controller");
            }

            if (openList.isEmpty()) {
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.no_start");
                isComplete = true;
            }
        } catch (Exception e) {
            AE2PowerTools.LOGGER.error("Error initializing network scanner", e);
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.error", e.getMessage());
            isComplete = true;
        }
    }

    /**
     * Process a batch of nodes. Call this each tick to spread work over time.
     * @return true if scan is complete
     */
    public boolean processBatch() {
        if (isComplete) return true;

        // Phase 1: Main network scan (loops and chunks)
        if (!openList.isEmpty()) return processMainScan();

        // Phase 2: Channel scan (runs after main scan completes)
        return processChannelScan();
    }

    /**
     * Process main network scan for loops and unloaded chunks.
     */
    private boolean processMainScan() {
        int processed = 0;

        while (!openList.isEmpty() && processed < MAX_NODES_PER_TICK) {
            if (nodesProcessed >= MAX_TOTAL_NODES) {
                statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.too_large",
                    MAX_TOTAL_NODES);
                isComplete = true;

                return true;
            }

            PathNode current = openList.poll();
            processNode(current);
            processed++;
            nodesProcessed++;
        }

        if (openList.isEmpty()) {
            // Main scan done - start channel scan if we have a controller
            if (hasController && !channelScanStarted) {
                channelScanStarted = true;
                channelScanner = new ChannelScanner(grid, world);
                statusMessage = I18n.translateToLocal("ae2powertools.scanner.status.scanning_channels");

                return false; // Continue to channel scan phase
            }

            // No controller or channel scan already done
            return finishScan();
        }

        statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.scanning",
            nodesProcessed);

        return false;
    }

    /**
     * Process channel scan phase.
     */
    private boolean processChannelScan() {
        if (channelScanner == null) return finishScan();

        boolean channelDone = channelScanner.processBatch();
        if (channelDone) return finishScan();

        statusMessage = channelScanner.getStatusMessage();

        return false;
    }

    /**
     * Finalize scan and set final status message.
     */
    private boolean finishScan() {
        isComplete = true;

        int chokeCount = channelScanner != null ? channelScanner.getChokepoints().size() : 0;
        int missingCount = channelScanner != null ? channelScanner.getMissingDevices().size() : 0;

        if (detectedLoops.isEmpty() && unloadedChunks.isEmpty() && chokeCount == 0 && missingCount == 0) {
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.no_issues", nodesProcessed);
        } else {
            statusMessage = I18n.translateToLocalFormatted("ae2powertools.scanner.status.found_issues", nodesProcessed);
        }

        return true;
    }

    /**
     * Process a single node: check connections and detect loops.
     * A loop is detected when we find a connection that would create a cycle in the graph.
     * We track visited connections to avoid counting the same edge twice.
     */
    private void processNode(PathNode current) {
        IGridNode node = current.node;
        IGridHost currentHost = node.getMachine();
        IAECluster currentCluster = getClusterOf(currentHost);

        for (IGridConnection connection : node.getConnections()) {
            IGridNode neighbor = connection.getOtherSide(node);
            if (neighbor == null) continue;

            // Skip the connection we came from (the edge to our parent)
            if (connection == current.connectionFromParent) continue;

            PathNode existingPath = visitedNodes.get(neighbor);
            if (existingPath != null) {
                // We found a node that was already visited through a different path.
                // This is only a loop if we haven't already processed this edge.
                // Since connections are bidirectional, we need to make sure we only count once.
                // We only report the loop from the node with higher depth (later discovery).
                if (current.depth > existingPath.depth) addLoopLocation(connection, current, existingPath);
            } else {
                // New node - add to open list
                PathNode newPath = new PathNode(neighbor, current, connection, current.depth + 1);
                openList.add(newPath);
                visitedNodes.put(neighbor, newPath);

                // Check for cluster entry from outside - this detects loops through multiblocks
                IGridHost neighborHost = neighbor.getMachine();
                IAECluster neighborCluster = getClusterOf(neighborHost);

                if (neighborCluster != null && neighborCluster != currentCluster) {
                    // Entering a cluster from outside
                    BlockPos neighborPos = getNodePosition(neighbor);

                    if (clusterEntryPoints.containsKey(neighborCluster)) {
                        // We've already entered this cluster from a different path - that's a loop!
                        addClusterLoopLocation(neighborPos, neighborHost, neighbor);
                    } else if (neighborPos != null) {
                        // First entry to this cluster, record it
                        clusterEntryPoints.put(neighborCluster, neighborPos);
                    }
                }

                // Check if the node's chunk is loaded
                checkChunkLoaded(neighbor);
            }
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
     * Add a loop location to the detected loops set.
     * We report the location of the current node that discovered the loop-closing edge.
     */
    private void addLoopLocation(IGridConnection connection, PathNode current, PathNode existing) {
        IGridNode node = current.node;
        IGridHost host = node.getMachine();
        BlockPos pos = getNodePosition(node);
        if (pos == null) return;

        // For multiblock structures, internal connections are handled separately via cluster entry tracking.
        // Skip if both nodes are in the same cluster - that's just internal multiblock wiring.
        IGridHost existingHost = existing.node.getMachine();
        IAECluster currentCluster = getClusterOf(host);
        IAECluster existingCluster = getClusterOf(existingHost);

        if (currentCluster != null && currentCluster == existingCluster) return;

        // Get dimension from the node itself
        int dimension = getNodeDimension(node);
        String dimName = getNodeDimensionName(node);
        World nodeWorld = getNodeWorld(node);

        boolean isLoaded = nodeWorld != null && nodeWorld.isBlockLoaded(pos);
        IBlockState blockState = isLoaded ? nodeWorld.getBlockState(pos) : Blocks.AIR.getDefaultState();

        String description = getNodeDescription(host);
        IssueLocation loopLoc = new IssueLocation(pos, dimension, dimName, blockState, isLoaded, description);
        detectedLoops.add(loopLoc);
    }

    /**
     * Add a loop location when we detect a second entry into a multiblock cluster.
     * Reports the cluster block where the second cable enters.
     */
    private void addClusterLoopLocation(BlockPos pos, IGridHost host, IGridNode node) {
        if (pos == null) return;

        int dimension = getNodeDimension(node);
        String dimName = getNodeDimensionName(node);
        World nodeWorld = getNodeWorld(node);

        IBlockState blockState = Blocks.AIR.getDefaultState();
        boolean isLoaded = nodeWorld != null && nodeWorld.isBlockLoaded(pos);

        if (isLoaded) blockState = nodeWorld.getBlockState(pos);

        String description = getNodeDescription(host);
        IssueLocation loopLoc = new IssueLocation(pos, dimension, dimName, blockState, isLoaded, description);
        detectedLoops.add(loopLoc);
    }

    /**
     * Check if a node's chunk is force-loaded (chunkloaded) and track non-chunkloaded chunks.
     * Note: This checks for FORCED chunk loading (chunkloaders), not just loaded chunks.
     * Chunks can be loaded temporarily when players are nearby but not force-loaded.
     *
     * LIMITATION: This can only detect non-force-loaded chunks for nodes we can actually visit,
     * meaning the chunk must be currently loaded (player nearby, spawn chunks, etc.). We cannot
     * detect network components in chunks that are fully unloaded without loading them first.
     *
     * Potential workarounds (all with significant drawbacks):
     * - Load chunks along cable paths temporarily (causes network forceUpdates, server lag)
     * - Track quantum bridge endpoints and check their target chunks (complex, bridges may be unloaded)
     *   Same issues as above.
     * - Persist network topology to disk and compare against live scan (stale data issues)
     * - Use IGridStorage to serialize/deserialize network state (may not include all location data)
     *
     * For now, we accept this limitation and only report what we can see in currently loaded chunks.
     */
    private void checkChunkLoaded(IGridNode node) {
        BlockPos pos = getNodePosition(node);
        if (pos == null) return;

        int dimension = getNodeDimension(node);
        String dimName = getNodeDimensionName(node);
        World nodeWorld = getNodeWorld(node);
        if (nodeWorld == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);

        // Get forced chunks for this dimension (cached)
        ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> forcedChunks = forcedChunksCache.computeIfAbsent(
            dimension, dim -> ForgeChunkManager.getPersistentChunksFor(nodeWorld)
        );

        // Check if the chunk is force-loaded (persistent)
        if (!forcedChunks.containsKey(chunkPos)) {
            unloadedChunks.add(new ChunkLocation(chunkPos, dimension, dimName));
        }
    }

    /**
     * Get the block position of a grid node.
     */
    private BlockPos getNodePosition(IGridNode node) {
        IGridHost host = node.getMachine();
        if (host instanceof TileEntity) return ((TileEntity) host).getPos();

        // For parts and other non-tile hosts, use the grid block location
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
     */
    private String getNodeDescription(IGridHost host) {
        if (host == null) return I18n.translateToLocal("ae2powertools.common.unknown");

        String className = host.getClass().getSimpleName();

        // TODO: should we try to localize some common AE2 part names here?
        //       Or maybe we can get the part name from the host if it's a part?
        // Clean up common names
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

    public Set<IssueLocation> getDetectedLoops() {
        return detectedLoops;
    }

    public Set<ChunkLocation> getUnloadedChunks() {
        return unloadedChunks;
    }

    public Set<ChannelChokepoint> getChokepoints() {
        if (channelScanner != null) return channelScanner.getChokepoints();

        return new HashSet<>();
    }

    public Set<MissingChannelDevice> getMissingDevices() {
        if (channelScanner != null) return channelScanner.getMissingDevices();

        return new HashSet<>();
    }

    public int getNodesProcessed() {
        return nodesProcessed;
    }

    public boolean hasController() {
        return hasController;
    }
}
