package com.ae2powertools.features.scanner;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.google.common.collect.ImmutableSetMultimap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.Constants;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.implementations.parts.IPartCable;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
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

    private static final String TEMP_NODE_TAG = "node";
    private static final String AE2STUFF_WIRELESS_CLASS = "net.bdew.ae2stuff.machines.wireless.TileWireless";
    private static final String AE2STUFF_WIRELESS_HUB_CLASS = "net.bdew.ae2stuff.machines.wireless.TileWirelessHub";
    private static final String NODE_TAG_PROXY = "proxy";
    private static final String NODE_TAG_OUTER = "outer";
    private static final String NODE_TAG_PART = "part";
    private static final String NODE_TAG_AE2STUFF = "ae_node";

    private static final int MAX_NODES_PER_TICK = 100;
    private static final int MAX_TOTAL_NODES = 1000000;

    private final IGrid grid;
    private final World world;

    // Cache of forced chunks per dimension (lazy-loaded)
    private final Map<Integer, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>> forcedChunksCache = new HashMap<>();
    private final Map<ChunkLocation, Map<BlockPos, SavedTileData>> savedChunkDataCache = new HashMap<>();

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

    // Status. Stored as ITextComponent (typically TextComponentTranslation) so the message
    // can be serialized as JSON and re-translated client-side in the player's locale.
    private boolean isComplete = false;
    private boolean hasController = false;
    private int nodesProcessed = 0;
    private ITextComponent statusMessage = new TextComponentString("");

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

    private static class SavedTileData {
        final Set<Long> nodeIds;
        final SavedCableData centerCable;

        SavedTileData(Set<Long> nodeIds, SavedCableData centerCable) {
            this.nodeIds = nodeIds;
            this.centerCable = centerCable;
        }
    }

    private static class SavedCableData {
        final AEColor color;
        final EnumSet<EnumFacing> blockedSides;

        SavedCableData(AEColor color, EnumSet<EnumFacing> blockedSides) {
            this.color = color;
            this.blockedSides = blockedSides;
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
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.no_pathing_grid");
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

                statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.scanning_controllerless");
            } else {
                // Start from all controller blocks
                for (IGridNode node : grid.getMachines(TileController.class)) {
                    PathNode pathNode = new PathNode(node, null, null, 0);
                    openList.add(pathNode);
                    visitedNodes.put(node, pathNode);
                }

                statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.scanning_controller");
            }

            if (openList.isEmpty()) {
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.no_start");
                isComplete = true;
            }
        } catch (Exception e) {
            AE2PowerTools.LOGGER.error("Error initializing network scanner", e);
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.error", e.getMessage());
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
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.too_large",
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
                statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.scanning_channels");

                return false; // Continue to channel scan phase
            }

            // No controller or channel scan already done
            return finishScan();
        }

        statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.scanning",
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
        int fatalCount = channelScanner != null ? channelScanner.getFatalErrors().size() : 0;

        // Loops are not counted, because they are not necessarily "issues".
        // Just a catch to be aware of.
        if (unloadedChunks.isEmpty() && chokeCount == 0 && missingCount == 0 && fatalCount == 0) {
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.no_issues", nodesProcessed);
        } else {
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.found_issues", nodesProcessed);
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

        checkChunkLoaded(node);
        checkAdjacentUnloadedChunks(node);
        checkAe2StuffWirelessChunks(node);

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

        String description = ScannerTextHelper.getNodeDescription(node);
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

        String description = ScannerTextHelper.getNodeDescription(node);
        IssueLocation loopLoc = new IssueLocation(pos, dimension, dimName, blockState, isLoaded, description);
        detectedLoops.add(loopLoc);
    }

    /**
     * Check if a node's chunk is force-loaded (chunkloaded) and track non-chunkloaded chunks.
     * Note: This checks for FORCED chunk loading (chunkloaders), not just loaded chunks.
     * Chunks can be loaded temporarily when players are nearby but not force-loaded.
     * <p>
     * LIMITATION: Quantum Network Bridges are invisible to the grid until they are loaded,
     * so we cannot detect their target chunks if they are not loaded.
     * A solution may be to persist the last known target chunk of each bridge in the chunk data,
     * but I'd rather not do that, as it is quite invasive on AE2's code.
     */
    private void checkChunkLoaded(IGridNode node) {
        BlockPos pos = getNodePosition(node);
        if (pos == null) return;

        World nodeWorld = getNodeWorld(node);
        if (nodeWorld == null) return;

        addUnloadedChunkIfNotForced(nodeWorld, new ChunkPos(pos));
    }

    /**
     * Probe the first unloaded chunk directly adjacent to a loaded node by reading saved chunk NBT.
     * This keeps the scan local and avoids activating chunks or rebuilding the live grid.
     * <p>
     * TODO: Quantum bridge endpoints still need an explicit persisted endpoint index.
     * Their unloaded target is not adjacent, so the local chunk-boundary probe cannot discover it.
     */
    private void checkAdjacentUnloadedChunks(IGridNode node) {
        if (!node.getGridBlock().isWorldAccessible()) return;

        BlockPos pos = getNodePosition(node);
        if (pos == null) return;

        World nodeWorld = getNodeWorld(node);
        if (!(nodeWorld instanceof WorldServer)) return;

        long gridStorageId = getGridStorageId(node);
        if (gridStorageId < 0) return;

        ChunkPos currentChunk = new ChunkPos(pos);
        EnumSet<EnumFacing> connectableSides = node.getGridBlock().getConnectableSides();

        for (EnumFacing side : connectableSides) {
            BlockPos adjacentPos = pos.offset(side);
            if (nodeWorld.isBlockLoaded(adjacentPos)) continue;

            ChunkPos adjacentChunk = new ChunkPos(adjacentPos);
            if (adjacentChunk.equals(currentChunk)) continue;

            if (!hasSavedConnectionAt((WorldServer) nodeWorld, node, side, adjacentPos, adjacentChunk,
                gridStorageId)) continue;

            addUnloadedChunkIfNotForced(nodeWorld, adjacentChunk);
        }
    }

    private void checkAe2StuffWirelessChunks(IGridNode node) {
        World nodeWorld = getNodeWorld(node);
        if (!(nodeWorld instanceof WorldServer)) return;

        IGridHost host = node.getMachine();

        if (hasClassName(host, AE2STUFF_WIRELESS_HUB_CLASS)) {
            Object[] linkSlots = (Object[]) invokeNoArg(host, "links");
            if (linkSlots == null) return;

            for (Object linkSlot : linkSlots) {
                checkAe2StuffWirelessLink((WorldServer) nodeWorld, linkSlot);
            }

            return;
        }

        if (hasClassName(host, AE2STUFF_WIRELESS_CLASS)) {
            checkAe2StuffWirelessLink((WorldServer) nodeWorld, invokeNoArg(host, "link"));
        }
    }

    private void checkAe2StuffWirelessLink(WorldServer nodeWorld, Object linkSlot) {
        BlockPos targetPos = extractAe2StuffLinkTarget(linkSlot);
        if (targetPos == null || nodeWorld.isBlockLoaded(targetPos)) return;

        addUnloadedChunkIfNotForced(nodeWorld, new ChunkPos(targetPos));
    }

    private BlockPos extractAe2StuffLinkTarget(Object linkSlot) {
        if (!invokeBooleanNoArg(linkSlot, "isDefined")) return null;

        Object option = invokeNoArg(linkSlot, "value");
        if (!invokeBooleanNoArg(option, "isDefined")) return null;

        Object value = invokeNoArg(option, "get");
        if (value instanceof BlockPos) return (BlockPos) value;

        return null;
    }

    private void addUnloadedChunkIfNotForced(World nodeWorld, ChunkPos chunkPos) {
        int dimension = nodeWorld.provider.getDimension();
        String dimName = nodeWorld.provider.getDimensionType().getName();

        // Get forced chunks for this dimension (cached)
        ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> forcedChunks = forcedChunksCache.computeIfAbsent(
            dimension, dim -> ForgeChunkManager.getPersistentChunksFor(nodeWorld)
        );

        // Check if the chunk is force-loaded (persistent)
        if (!forcedChunks.containsKey(chunkPos)) {
            unloadedChunks.add(new ChunkLocation(chunkPos, dimension, dimName));
        }
    }

    private long getGridStorageId(IGridNode node) {
        NBTTagCompound nodeData = new NBTTagCompound();

        // Serialize the node into a temporary in-memory tag so we can read the
        // existing grid storage id
        node.saveToNBT(TEMP_NODE_TAG, nodeData);
        if (!nodeData.hasKey(TEMP_NODE_TAG, Constants.NBT.TAG_COMPOUND)) return -1;

        return nodeData.getCompoundTag(TEMP_NODE_TAG).getLong("g");
    }

    private boolean hasSavedConnectionAt(WorldServer nodeWorld, IGridNode node, EnumFacing side, BlockPos targetPos,
        ChunkPos targetChunk, long gridStorageId) {
        Map<BlockPos, SavedTileData> savedTileDataByPos = getSavedChunkTileData(nodeWorld, targetChunk);
        SavedTileData savedTileData = savedTileDataByPos.get(targetPos);
        if (savedTileData == null) return false;

        if (savedTileData.nodeIds.contains(gridStorageId)) return true;

        return isSavedCableContinuation(node, side, savedTileData.centerCable);
    }

    private boolean isSavedCableContinuation(IGridNode node, EnumFacing side, SavedCableData savedCableData) {
        if (savedCableData == null) return false;
        if (savedCableData.blockedSides.contains(side.getOpposite())) return false;

        IGridHost host = node.getMachine();
        if (!(host instanceof IPartCable)) return false;

        // Fresh-start AE2 networks can keep stale grid storage ids in unloaded cable chunks
        // until those chunks load once and re-merge. Fall back to the saved cable-bus shape
        // so direct cable continuations across the chunk border are still detected.
        return ((IPartCable) host).getCableColor().matches(savedCableData.color);
    }

    private Map<BlockPos, SavedTileData> getSavedChunkTileData(WorldServer nodeWorld, ChunkPos chunkPos) {
        ChunkLocation cacheKey = new ChunkLocation(chunkPos, nodeWorld.provider.getDimension(),
            nodeWorld.provider.getDimensionType().getName());

        Map<BlockPos, SavedTileData> cached = savedChunkDataCache.get(cacheKey);
        if (cached != null) return cached;

        Map<BlockPos, SavedTileData> parsed = loadSavedChunkTileData(nodeWorld, chunkPos);
        savedChunkDataCache.put(cacheKey, parsed);

        return parsed;
    }

    private Map<BlockPos, SavedTileData> loadSavedChunkTileData(WorldServer nodeWorld, ChunkPos chunkPos) {
        try (DataInputStream inputStream = RegionFileCache.getChunkInputStream(nodeWorld.getChunkSaveLocation(),
            chunkPos.x, chunkPos.z)) {
            if (inputStream == null) return Collections.emptyMap();

            NBTTagCompound chunkData = CompressedStreamTools.read(inputStream);
            if (chunkData == null || !chunkData.hasKey("Level", Constants.NBT.TAG_COMPOUND)) {
                return Collections.emptyMap();
            }

            NBTTagCompound levelData = chunkData.getCompoundTag("Level");
            NBTTagList tileEntities = levelData.getTagList("TileEntities", Constants.NBT.TAG_COMPOUND);
            Map<BlockPos, SavedTileData> savedTileDataByPos = new HashMap<>();

            for (int i = 0; i < tileEntities.tagCount(); i++) {
                NBTTagCompound tileData = tileEntities.getCompoundTagAt(i);
                SavedTileData savedTileData = collectSavedTileData(tileData);
                if (savedTileData == null) continue;

                BlockPos pos = new BlockPos(tileData.getInteger("x"), tileData.getInteger("y"),
                    tileData.getInteger("z"));
                savedTileDataByPos.put(pos, savedTileData);
            }

            return savedTileDataByPos;
        } catch (IOException e) {
            AE2PowerTools.LOGGER.warn("Failed reading saved chunk data for scanner at {}:{} in dim {}",
                chunkPos.x, chunkPos.z, nodeWorld.provider.getDimension(), e);

            return Collections.emptyMap();
        }
    }

    private SavedTileData collectSavedTileData(NBTTagCompound tileData) {
        Set<Long> nodeIds = collectSavedNodeIds(tileData);
        SavedCableData savedCableData = collectSavedCenterCable(tileData);

        if (nodeIds.isEmpty() && savedCableData == null) return null;

        return new SavedTileData(nodeIds, savedCableData);
    }

    private SavedCableData collectSavedCenterCable(NBTTagCompound tileData) {
        String centerKey = "def:" + AEPartLocation.INTERNAL.ordinal();
        if (!tileData.hasKey(centerKey, Constants.NBT.TAG_COMPOUND)) return null;

        ItemStack centerPartStack = new ItemStack(tileData.getCompoundTag(centerKey));
        if (centerPartStack.isEmpty()) return null;
        if (!(centerPartStack.getItem() instanceof IPartItem)) return null;

        IPart centerPart = ((IPartItem) centerPartStack.getItem()).createPartFromItemStack(centerPartStack.copy());
        if (!(centerPart instanceof IPartCable)) return null;

        EnumSet<EnumFacing> blockedSides = EnumSet.noneOf(EnumFacing.class);
        for (EnumFacing side : EnumFacing.values()) {
            String sideKey = "def:" + side.ordinal();
            if (tileData.hasKey(sideKey, Constants.NBT.TAG_COMPOUND)) {
                blockedSides.add(side);
            }
        }

        return new SavedCableData(((IPartCable) centerPart).getCableColor(), blockedSides);
    }

    private Set<Long> collectSavedNodeIds(NBTTagCompound tileData) {
        Set<Long> nodeIds = new HashSet<>();

        collectSavedNodeId(tileData, NODE_TAG_PROXY, nodeIds);
        collectSavedNodeId(tileData, NODE_TAG_OUTER, nodeIds);
        collectSavedNodeId(tileData, NODE_TAG_PART, nodeIds);
        collectSavedNodeId(tileData, NODE_TAG_AE2STUFF, nodeIds);

        // 6 sides + 1 internal = 7 possible node tags for connections
        for (int i = 0; i < 7; i++) {
            String extraKey = "extra:" + i;
            if (!tileData.hasKey(extraKey, Constants.NBT.TAG_COMPOUND)) continue;

            NBTTagCompound extraData = tileData.getCompoundTag(extraKey);
            collectSavedNodeId(extraData, NODE_TAG_PROXY, nodeIds);
            collectSavedNodeId(extraData, NODE_TAG_OUTER, nodeIds);
            collectSavedNodeId(extraData, NODE_TAG_PART, nodeIds);
            collectSavedNodeId(extraData, NODE_TAG_AE2STUFF, nodeIds);
        }

        return nodeIds;
    }

    private void collectSavedNodeId(NBTTagCompound data, String key, Set<Long> nodeIds) {
        if (!data.hasKey(key, Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound nodeData = data.getCompoundTag(key);
        if (!nodeData.hasKey("g")) return;

        nodeIds.add(nodeData.getLong("g"));
    }

    private boolean hasClassName(Object target, String className) {
        return target != null && target.getClass().getName().equals(className);
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;

        try {
            Method method = target.getClass().getMethod(methodName);

            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private boolean invokeBooleanNoArg(Object target, String methodName) {
        Object value = invokeNoArg(target, methodName);

        return value instanceof Boolean && (Boolean) value;
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

    public Set<FatalNetworkError> getFatalErrors() {
        if (channelScanner != null) return channelScanner.getFatalErrors();

        return new HashSet<>();
    }

    public int getNodesProcessed() {
        return nodesProcessed;
    }

    public boolean hasController() {
        return hasController;
    }
}
