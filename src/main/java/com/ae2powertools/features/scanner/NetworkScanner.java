package com.ae2powertools.features.scanner;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
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
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.implementations.parts.IPartCable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InvalidPatternHelper;
import appeng.items.misc.ItemEncodedPattern;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.IAEMultiBlock;
import appeng.tile.networking.TileController;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.util.SubnetGridHelper;


/**
 * Detects loops and unloaded chunks in AE2 networks by performing BFS from the controller and tracking paths.
 * A loop is detected when a node is reached through two different paths.
 * <p>
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
    private static final String PATTERN_TYPE_TAG = "PatternType";
    private static final String PATTERN_TYPE_PACKAGED_AUTO_PACKAGE = "package";
    private static final String PATTERN_TYPE_PACKAGED_AUTO_RECIPE = "recipe";

    private static final int MAX_NODES_PER_TICK = 100;
    private static final int MAX_TOTAL_NODES = 1000000;

    private final IGrid grid;
    private final World world;
    private final boolean includeSubnets;

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

    // Additional per-grid scans when subnet scanning is enabled.
    private final ArrayDeque<IGrid> pendingSubnetGrids = new ArrayDeque<>();
    private final Set<IssueLocation> subnetDetectedLoops = new HashSet<>();
    private final Set<ChunkLocation> subnetUnloadedChunks = new HashSet<>();
    private final Set<ChannelChokepoint> subnetChokepoints = new HashSet<>();
    private final Set<MissingChannelDevice> subnetMissingDevices = new HashSet<>();
    private final Set<FatalNetworkError> subnetFatalErrors = new HashSet<>();
    private final List<PatternIssue> patternIssues = new ArrayList<>();
    private final List<PatternIssue> subnetPatternIssues = new ArrayList<>();
    private NetworkScanner activeSubnetScanner = null;
    private int completedSubnetNodeCount = 0;

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

    private static class ProviderContext {
        final ICraftingProvider provider;
        final IGridNode node;
        final BlockPos pos;
        final int dimension;
        final String dimensionName;
        final String description;

        ProviderContext(ICraftingProvider provider, IGridNode node, BlockPos pos, int dimension,
                String dimensionName, String description) {
            this.provider = provider;
            this.node = node;
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.description = description;
        }
    }

    private static class ProviderPatternSnapshot {
        final ProviderContext provider;
        final ICraftingPatternDetails details;
        final String identityKey;
        final String outputSignature;
        final List<String> inputTypeKeys;
        final Set<String> inputTypeSet;
        final Set<String> outputTypeSet;
        final String summary;
        final String patternType;
        final String packagerOutputTypeKey;

        ProviderPatternSnapshot(ProviderContext provider, ICraftingPatternDetails details,
                String identityKey, String outputSignature, List<String> inputTypeKeys,
                Set<String> inputTypeSet, Set<String> outputTypeSet, String summary,
                String patternType, String packagerOutputTypeKey) {
            this.provider = provider;
            this.details = details;
            this.identityKey = identityKey;
            this.outputSignature = outputSignature;
            this.inputTypeKeys = inputTypeKeys;
            this.inputTypeSet = inputTypeSet;
            this.outputTypeSet = outputTypeSet;
            this.summary = summary;
            this.patternType = patternType;
            this.packagerOutputTypeKey = packagerOutputTypeKey;
        }

        boolean isPackagedAutoPackage() {
            return PATTERN_TYPE_PACKAGED_AUTO_PACKAGE.equals(patternType);
        }

        boolean isPackagedAutoRecipe() {
            return PATTERN_TYPE_PACKAGED_AUTO_RECIPE.equals(patternType);
        }
    }

    private static class CraftingOptionCollector implements ICraftingProviderHelper {
        final List<ICraftingPatternDetails> patternDetails = new ArrayList<>();

        @Override
        public void addCraftingOption(ICraftingMedium medium, ICraftingPatternDetails api) {
            if (api == null) return;

            patternDetails.add(api);
        }

        @Override
        public void setEmitable(IAEItemStack what) {
        }
    }

    public NetworkScanner(IGrid grid, World world) {
        this(grid, world, false);
    }

    public NetworkScanner(IGrid grid, World world, boolean includeSubnets) {
        this.grid = grid;
        this.world = world;
        this.includeSubnets = includeSubnets;

        if (includeSubnets) {
            pendingSubnetGrids.addAll(SubnetGridHelper.collectConnectedGrids(grid));
        }

        initialize();
    }

    /**
     * Initialize the BFS by finding controller(s) and starting from them.
     */
    private void initialize() {
        try {
            IPathingGrid pathingGrid = grid.getCache(IPathingGrid.class);

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

        if (activeSubnetScanner != null) return processSubnetScan();

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
            return advanceAfterCurrentGridScan();
        }

        statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.scanning",
            nodesProcessed);

        return false;
    }

    /**
     * Process channel scan phase.
     */
    private boolean processChannelScan() {
        if (channelScanner == null) return processPatternScan();

        boolean channelDone = channelScanner.processBatch();
        if (channelDone) return processPatternScan();

        statusMessage = channelScanner.getStatusMessage();

        return false;
    }

    private boolean processPatternScan() {
        scanPatternIssues();

        return advanceAfterCurrentGridScan();
    }

    private boolean advanceAfterCurrentGridScan() {
        if (startNextSubnetScan()) return false;

        return finishScan();
    }

    private boolean startNextSubnetScan() {
        if (!includeSubnets || activeSubnetScanner != null || pendingSubnetGrids.isEmpty()) return false;

        IGrid subnetGrid = pendingSubnetGrids.removeFirst();
        World subnetWorld = SubnetGridHelper.resolveWorld(subnetGrid, world);
        activeSubnetScanner = new NetworkScanner(subnetGrid, subnetWorld, false);
        statusMessage = activeSubnetScanner.getStatusMessage();

        return true;
    }

    private boolean processSubnetScan() {
        if (activeSubnetScanner == null) return finishScan();

        boolean subnetDone = activeSubnetScanner.processBatch();
        if (!subnetDone) {
            statusMessage = activeSubnetScanner.getStatusMessage();

            return false;
        }

        mergeSubnetResults(activeSubnetScanner);
        completedSubnetNodeCount += activeSubnetScanner.getNodesProcessed();
        activeSubnetScanner = null;

        if (startNextSubnetScan()) return false;

        return finishScan();
    }

    private void mergeSubnetResults(NetworkScanner subnetScanner) {
        subnetDetectedLoops.addAll(subnetScanner.getDetectedLoops());
        subnetUnloadedChunks.addAll(subnetScanner.getUnloadedChunks());
        subnetChokepoints.addAll(subnetScanner.getChokepoints());
        subnetMissingDevices.addAll(subnetScanner.getMissingDevices());
        subnetFatalErrors.addAll(subnetScanner.getFatalErrors());
        subnetPatternIssues.addAll(subnetScanner.getPatternIssues());
    }

    /**
     * Finalize scan and set final status message.
     */
    private boolean finishScan() {
        isComplete = true;

        int totalNodes = getNodesProcessed();
        int chokeCount = getChokepoints().size();
        int missingCount = getMissingDevices().size();
        int fatalCount = getFatalErrors().size();
        int patternCount = getPatternIssues().size();

        // Loops are not counted, because they are not necessarily "issues".
        // Just a catch to be aware of.
        if (getUnloadedChunks().isEmpty() && chokeCount == 0 && missingCount == 0 && fatalCount == 0
            && patternCount == 0) {
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.no_issues", totalNodes);
        } else {
            statusMessage = new TextComponentTranslation("ae2powertools.scanner.status.found_issues", totalNodes);
        }

        return true;
    }

    private void scanPatternIssues() {
        patternIssues.clear();

        List<ProviderContext> providers = collectCraftingProviders();
        List<ProviderPatternSnapshot> snapshots = new ArrayList<>();

        for (ProviderContext provider : providers) {
            try {
                collectProviderPatterns(provider, snapshots);
                collectInvalidCraftingPatterns(provider);
            } catch (Exception e) {
                AE2PowerTools.LOGGER.warn("Failed scanning patterns for provider {} at {} in dim {}",
                    provider.description, provider.pos, provider.dimension, e);
            }
        }

        detectConflictingOutputs(snapshots);
        detectNestedInputOutputPatterns(snapshots);
    }

    private List<ProviderContext> collectCraftingProviders() {
        Map<Object, ProviderContext> uniqueProviders = new IdentityHashMap<>();

        for (IGridNode node : visitedNodes.keySet()) {
            IGridHost host = node.getMachine();
            if (!(host instanceof ICraftingProvider)) continue;

            BlockPos pos = getNodePosition(node);
            if (pos == null) continue;

            Object uniqueKey = getCraftingProviderKey(host);
            if (uniqueProviders.containsKey(uniqueKey)) continue;

            uniqueProviders.put(uniqueKey, new ProviderContext(
                (ICraftingProvider) host,
                node,
                pos,
                getNodeDimension(node),
                getNodeDimensionName(node),
                ScannerTextHelper.getNodeDescription(node)
            ));
        }

        return new ArrayList<>(uniqueProviders.values());
    }

    private Object getCraftingProviderKey(IGridHost host) {
        IAECluster cluster = getClusterOf(host);
        if (cluster != null) return cluster;

        return host;
    }

    private void collectProviderPatterns(ProviderContext provider, List<ProviderPatternSnapshot> snapshots) {
        CraftingOptionCollector collector = new CraftingOptionCollector();
        provider.provider.provideCrafting(collector);

        Set<String> seenPatternIdentities = new HashSet<>();

        for (ICraftingPatternDetails details : collector.patternDetails) {
            ProviderPatternSnapshot snapshot = createPatternSnapshot(provider, details);
            if (snapshot == null) continue;
            if (!seenPatternIdentities.add(snapshot.identityKey)) continue;

            snapshots.add(snapshot);
        }
    }

    private ProviderPatternSnapshot createPatternSnapshot(ProviderContext provider,
            ICraftingPatternDetails details) {
        String identityKey = buildPatternIdentityKey(details);
        if (identityKey.isEmpty()) return null;

        List<String> inputTypeKeys = buildTypeKeys(details.getInputs());
        Set<String> inputTypeSet = new HashSet<>(inputTypeKeys);
        Set<String> outputTypeSet = new HashSet<>(buildTypeKeys(details.getOutputs()));
        String outputSignature = buildOutputSignature(details.getCondensedOutputs());
        String summary = summarizeOutputs(details.getCondensedOutputs());
        String patternType = getPatternType(details.getPattern());
        String packagerOutputTypeKey = null;

        if (PATTERN_TYPE_PACKAGED_AUTO_PACKAGE.equals(patternType)) {
            List<String> outputTypeKeys = buildTypeKeys(details.getOutputs());
            if (!outputTypeKeys.isEmpty()) packagerOutputTypeKey = outputTypeKeys.get(0);
        }

        return new ProviderPatternSnapshot(provider, details, identityKey, outputSignature,
            inputTypeKeys, inputTypeSet, outputTypeSet, summary, patternType, packagerOutputTypeKey);
    }

    /**
     * Collect patterns that are craftable but no longer resolve to a valid recipe.
     * This is usually caused by recipe or mod changes that break the pattern's recipe,
     * and indicates that the user should re-encode the pattern with a valid recipe.
     */
    private void collectInvalidCraftingPatterns(ProviderContext provider) {
        if (!(provider.provider instanceof IInterfaceHost)) return;

        IItemHandler patternInventory = ((IInterfaceHost) provider.provider).getInterfaceDuality().getPatterns();
        if (patternInventory == null) return;

        World nodeWorld = getNodeWorld(provider.node);
        if (nodeWorld == null) return;

        Set<String> seenSummaries = new HashSet<>();

        for (int slot = 0; slot < patternInventory.getSlots(); slot++) {
            try {
                ItemStack patternStack = patternInventory.getStackInSlot(slot);
                if (patternStack.isEmpty()) continue;
                if (!(patternStack.getItem() instanceof ItemEncodedPattern)) continue;
                if (!(patternStack.getItem() instanceof ICraftingPatternItem)) continue;
                if (!patternStack.hasTagCompound()) continue;

                ICraftingPatternDetails details = ((ICraftingPatternItem) patternStack.getItem())
                    .getPatternForItem(patternStack, nodeWorld);
                if (details != null) continue;

                InvalidPatternHelper invalidPattern = new InvalidPatternHelper(patternStack);
                if (!invalidPattern.isCraftable()) continue;

                String summary = summarizeInvalidOutputs(invalidPattern);
                if (!seenSummaries.add(summary)) continue;

                addPatternIssue(new PatternIssue(PatternIssue.Category.INVALID_CRAFTING_RECIPE,
                    provider.pos, provider.dimension, provider.dimensionName, provider.description, summary));
            } catch (Exception e) {
                AE2PowerTools.LOGGER.warn("Failed reading raw pattern stack in slot {} for provider {} at {} in dim {}",
                    slot, provider.description, provider.pos, provider.dimension, e);
            }
        }
    }

    /**
     * Detect patterns that expose the same output content. If 2 patterns share an output item
     * and the user attempts to craft that item, it is undefined which pattern AE2 will choose
     * during tree building. This can lead to unstable or unintended recipe paths.
     */
    private void detectConflictingOutputs(List<ProviderPatternSnapshot> snapshots) {
        Map<String, List<ProviderPatternSnapshot>> byOutputType = new HashMap<>();
        Map<ProviderContext, Map<String, Integer>> conflictsByProvider = new IdentityHashMap<>();
        Map<String, String> outputLabels = new HashMap<>();

        for (ProviderPatternSnapshot snapshot : snapshots) {
            for (String outputTypeKey : snapshot.outputTypeSet) {
                if (outputTypeKey.isEmpty()) continue;

                byOutputType.computeIfAbsent(outputTypeKey, key -> new ArrayList<>()).add(snapshot);
            }
        }

        // AE2 indexes craftables per individual output item with the stack size reset.
        // Distinct patterns that expose the same output item type can therefore collide
        // during tree building even if the counts or secondary outputs differ.
        for (Map.Entry<String, List<ProviderPatternSnapshot>> entry : byOutputType.entrySet()) {
            String outputTypeKey = entry.getKey();
            List<ProviderPatternSnapshot> group = entry.getValue();
            Set<String> uniqueIdentities = new HashSet<>();
            for (ProviderPatternSnapshot snapshot : group) uniqueIdentities.add(snapshot.identityKey);

            if (uniqueIdentities.size() < 2) continue;

            outputLabels.put(outputTypeKey, resolveConflictingOutputLabel(group, outputTypeKey));

            for (ProviderPatternSnapshot snapshot : group) {
                Map<String, Integer> providerConflicts = conflictsByProvider.computeIfAbsent(snapshot.provider,
                    key -> new HashMap<>());
                providerConflicts.put(outputTypeKey, uniqueIdentities.size());
            }
        }

        for (Map.Entry<ProviderContext, Map<String, Integer>> entry : conflictsByProvider.entrySet()) {
            ProviderContext provider = entry.getKey();
            String summary = summarizeConflictingOutputs(entry.getValue(), outputLabels);

            addPatternIssue(new PatternIssue(PatternIssue.Category.CONFLICTING_OUTPUTS,
                provider.pos,
                provider.dimension,
                provider.dimensionName,
                provider.description,
                summary));
        }
    }

    private String summarizeConflictingOutputs(Map<String, Integer> conflictingOutputTypes,
            Map<String, String> outputLabels) {
        List<String> outputTypeKeys = new ArrayList<>(conflictingOutputTypes.keySet());
        outputTypeKeys.sort((first, second) -> outputLabels.getOrDefault(first, first)
            .compareToIgnoreCase(outputLabels.getOrDefault(second, second)));

        int conflictCount = outputTypeKeys.size();
        int displayedConflictCount = 0;
        TextComponentString summary = new TextComponentString("");

        for (String outputTypeKey : outputTypeKeys) {
            if (displayedConflictCount >= 2) continue;

            String outputLabel = outputLabels.get(outputTypeKey);
            if (outputLabel == null || outputLabel.isEmpty()) continue;

            if (displayedConflictCount > 0) summary.appendText(", ");

            summary.appendSibling(new TextComponentTranslation(
                "gui.ae2powertools.scanner.pattern.conflicting_output_entry",
                outputLabel,
                conflictingOutputTypes.get(outputTypeKey)
            ));
            displayedConflictCount++;
        }

        if (displayedConflictCount == 0) {
            return ScannerTextHelper.serializeComponent(
                new TextComponentTranslation("gui.ae2powertools.scanner.pattern.unknown_output"));
        }

        if (conflictCount > displayedConflictCount) {
            summary.appendText(", ");
            summary.appendSibling(new TextComponentTranslation(
                "gui.ae2powertools.scanner.pattern.more_outputs",
                conflictCount - displayedConflictCount
            ));
        }

        return ScannerTextHelper.serializeComponent(summary);
    }

    private String resolveConflictingOutputLabel(List<ProviderPatternSnapshot> group, String outputTypeKey) {
        for (ProviderPatternSnapshot snapshot : group) {
            String outputLabel = resolveOutputDisplayName(snapshot.details.getOutputs(), outputTypeKey);
            if (!outputLabel.isEmpty()) return outputLabel;
        }

        return "";
    }

    private String resolveOutputDisplayName(IAEItemStack[] outputs, String outputTypeKey) {
        for (IAEItemStack output : outputs) {
            if (output == null) continue;

            ItemStack itemStack = output.createItemStack();
            if (itemStack.isEmpty()) continue;

            itemStack.setCount(1);
            if (!outputTypeKey.equals(buildItemTypeKey(itemStack))) continue;

            return itemStack.getDisplayName();
        }

        return "";
    }

    private void detectNestedInputOutputPatterns(List<ProviderPatternSnapshot> snapshots) {
        List<ProviderPatternSnapshot> nestedCandidates = buildNestedDetectionSnapshots(snapshots);

        for (ProviderPatternSnapshot snapshot : nestedCandidates) {
            Set<String> overlap = new HashSet<>(snapshot.inputTypeSet);
            overlap.retainAll(snapshot.outputTypeSet);
            if (overlap.isEmpty()) continue;

            addPatternIssue(new PatternIssue(PatternIssue.Category.NESTED_INPUT_OUTPUT,
                snapshot.provider.pos,
                snapshot.provider.dimension,
                snapshot.provider.dimensionName,
                snapshot.provider.description,
                snapshot.summary));
        }
    }

    private void addPatternIssue(PatternIssue issue) {
        if (patternIssues.contains(issue)) return;

        patternIssues.add(issue);
    }

    /**
     * PackagedAuto exposes package creation and recipe execution as separate AE2 providers.
     * When every recipe input can be matched back to a packager output, use the original
     * package inputs for nested input/output detection so package items do not create false negatives.
     */
    private List<ProviderPatternSnapshot> buildNestedDetectionSnapshots(List<ProviderPatternSnapshot> snapshots) {
        List<ProviderPatternSnapshot> nestedCandidates = new ArrayList<>();
        Map<String, List<ProviderPatternSnapshot>> packageSnapshotsByOutput = new HashMap<>();
        Set<ProviderPatternSnapshot> mergedPackages = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ProviderPatternSnapshot snapshot : snapshots) {
            if (!snapshot.isPackagedAutoPackage()) continue;
            if (snapshot.packagerOutputTypeKey == null || snapshot.packagerOutputTypeKey.isEmpty()) continue;

            packageSnapshotsByOutput.computeIfAbsent(snapshot.packagerOutputTypeKey, key -> new ArrayList<>())
                .add(snapshot);
        }

        for (ProviderPatternSnapshot snapshot : snapshots) {
            if (!snapshot.isPackagedAutoRecipe()) {
                nestedCandidates.add(snapshot);
                continue;
            }

            Set<String> mergedInputTypes = new HashSet<>();
            boolean matchedAnyPackage = false;
            boolean mergedAllPackageInputs = true;

            for (String inputTypeKey : snapshot.inputTypeKeys) {
                List<ProviderPatternSnapshot> packageCandidates = packageSnapshotsByOutput.get(inputTypeKey);
                ProviderPatternSnapshot mergedSnapshot = resolveUniquePackageSnapshot(packageCandidates);

                if (mergedSnapshot == null) {
                    mergedAllPackageInputs = false;
                    mergedInputTypes.add(inputTypeKey);
                    continue;
                }

                matchedAnyPackage = true;
                mergedPackages.add(mergedSnapshot);
                mergedInputTypes.addAll(mergedSnapshot.inputTypeSet);
            }

            if (!matchedAnyPackage || !mergedAllPackageInputs) {
                nestedCandidates.add(snapshot);
                continue;
            }

            nestedCandidates.add(new ProviderPatternSnapshot(
                snapshot.provider,
                snapshot.details,
                snapshot.identityKey,
                snapshot.outputSignature,
                new ArrayList<>(mergedInputTypes),
                mergedInputTypes,
                snapshot.outputTypeSet,
                snapshot.summary,
                snapshot.patternType,
                snapshot.packagerOutputTypeKey
            ));
        }

        for (ProviderPatternSnapshot snapshot : snapshots) {
            if (!snapshot.isPackagedAutoPackage()) continue;
            if (mergedPackages.contains(snapshot)) continue;

            nestedCandidates.add(snapshot);
        }

        return nestedCandidates;
    }

    private ProviderPatternSnapshot resolveUniquePackageSnapshot(List<ProviderPatternSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        ProviderPatternSnapshot first = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            ProviderPatternSnapshot other = candidates.get(i);
            if (!first.identityKey.equals(other.identityKey)) return null;
        }

        return first;
    }

    private String buildPatternIdentityKey(ICraftingPatternDetails details) {
        ItemStack patternStack = details.getPattern();
        if (!patternStack.isEmpty()) return buildSizedItemKey(patternStack);

        return (details.isCraftable() ? "C" : "P") + ':' + buildOutputSignature(details.getOutputs())
            + ':' + buildOutputSignature(details.getInputs());
    }

    private String getPatternType(ItemStack patternStack) {
        if (patternStack.isEmpty() || !patternStack.hasTagCompound()) return "";

        return patternStack.getTagCompound().getString(PATTERN_TYPE_TAG);
    }

    private String buildOutputSignature(appeng.api.storage.data.IAEItemStack[] stacks) {
        List<String> keys = new ArrayList<>();

        for (appeng.api.storage.data.IAEItemStack stack : stacks) {
            if (stack == null) continue;

            keys.add(buildSizedItemKey(stack.createItemStack(), stack.getStackSize()));
        }

        Collections.sort(keys);
        return String.join("|", keys);
    }

    private List<String> buildTypeKeys(appeng.api.storage.data.IAEItemStack[] stacks) {
        List<String> keys = new ArrayList<>();

        for (appeng.api.storage.data.IAEItemStack stack : stacks) {
            if (stack == null) continue;

            ItemStack itemStack = stack.createItemStack();
            if (itemStack.isEmpty()) continue;

            itemStack.setCount(1);
            keys.add(buildItemTypeKey(itemStack));
        }

        return keys;
    }

    private String buildSizedItemKey(ItemStack stack) {
        return buildSizedItemKey(stack, stack.getCount());
    }

    private String buildSizedItemKey(ItemStack stack, long count) {
        if (stack.isEmpty()) return "";

        return buildItemTypeKey(stack) + 'x' + count;
    }

    private String buildItemTypeKey(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) return "";

        String nbtKey = stack.hasTagCompound() ? stack.getTagCompound().toString() : "";
        return stack.getItem().getRegistryName() + "@" + stack.getItemDamage() + '#' + nbtKey;
    }

    private String summarizeOutputs(IAEItemStack[] outputs) {
        List<String> parts = new ArrayList<>();
        int outputCount = 0;

        for (IAEItemStack output : outputs) {
            if (output == null) continue;

            outputCount++;
            if (parts.size() >= 2) continue;

            ItemStack itemStack = output.createItemStack();
            if (itemStack.isEmpty()) continue;

            parts.add(output.getStackSize() + "x " + itemStack.getDisplayName());
        }

        if (parts.isEmpty()) {
            return ScannerTextHelper.serializeComponent(
                new TextComponentTranslation("gui.ae2powertools.scanner.pattern.unknown_output"));
        }

        if (outputCount > parts.size()) {
            parts.add(ScannerTextHelper.deserializeComponent(ScannerTextHelper.serializeComponent(
                new TextComponentTranslation("gui.ae2powertools.scanner.pattern.more_outputs",
                    outputCount - parts.size()))).getFormattedText());
        }

        return ScannerTextHelper.serializeComponent(new TextComponentString(String.join(", ", parts)));
    }

    private String summarizeInvalidOutputs(InvalidPatternHelper invalidPattern) {
        List<String> parts = new ArrayList<>();
        int outputCount = invalidPattern.getOutputs().size();

        for (int i = 0; i < invalidPattern.getOutputs().size() && i < 2; i++) {
            InvalidPatternHelper.PatternIngredient ingredient = invalidPattern.getOutputs().get(i);
            parts.add(ingredient.getCount() + "x " + ingredient.getName());
        }

        if (parts.isEmpty()) {
            return ScannerTextHelper.serializeComponent(
                new TextComponentTranslation("gui.ae2powertools.scanner.pattern.unknown_output"));
        }

        if (outputCount > parts.size()) {
            parts.add(ScannerTextHelper.deserializeComponent(ScannerTextHelper.serializeComponent(
                new TextComponentTranslation("gui.ae2powertools.scanner.pattern.more_outputs",
                    outputCount - parts.size()))).getFormattedText());
        }

        return ScannerTextHelper.serializeComponent(new TextComponentString(String.join(", ", parts)));
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
     * TODO: Best I can think is mixin'ing into the bridge to write a persistent list of all known bridges.
     *       But I would really really like to avoid invasive changes to AE2's code.
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

            NBTTagCompound chunkData = CompressedStreamTools.read(inputStream);
            if (!chunkData.hasKey("Level", Constants.NBT.TAG_COMPOUND)) {
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

    @SuppressWarnings("rawtypes")
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
            if (tileData.hasKey(sideKey, Constants.NBT.TAG_COMPOUND)) blockedSides.add(side);
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
            return coord.getPos();
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
            return coord.getWorld();
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
        if (!includeSubnets && activeSubnetScanner == null && subnetDetectedLoops.isEmpty()) return detectedLoops;

        Set<IssueLocation> combined = new HashSet<>(detectedLoops);
        combined.addAll(subnetDetectedLoops);
        if (activeSubnetScanner != null) combined.addAll(activeSubnetScanner.getDetectedLoops());

        return combined;
    }

    public Set<ChunkLocation> getUnloadedChunks() {
        if (!includeSubnets && activeSubnetScanner == null && subnetUnloadedChunks.isEmpty()) return unloadedChunks;

        Set<ChunkLocation> combined = new HashSet<>(unloadedChunks);
        combined.addAll(subnetUnloadedChunks);
        if (activeSubnetScanner != null) combined.addAll(activeSubnetScanner.getUnloadedChunks());

        return combined;
    }

    public Set<ChannelChokepoint> getChokepoints() {
        Set<ChannelChokepoint> combined = new HashSet<>(subnetChokepoints);

        if (channelScanner != null) combined.addAll(channelScanner.getChokepoints());
        if (activeSubnetScanner != null) combined.addAll(activeSubnetScanner.getChokepoints());

        return combined;
    }

    public Set<MissingChannelDevice> getMissingDevices() {
        Set<MissingChannelDevice> combined = new HashSet<>(subnetMissingDevices);

        if (channelScanner != null) combined.addAll(channelScanner.getMissingDevices());
        if (activeSubnetScanner != null) combined.addAll(activeSubnetScanner.getMissingDevices());

        return combined;
    }

    public Set<FatalNetworkError> getFatalErrors() {
        Set<FatalNetworkError> combined = new HashSet<>(subnetFatalErrors);

        if (channelScanner != null) combined.addAll(channelScanner.getFatalErrors());
        if (activeSubnetScanner != null) combined.addAll(activeSubnetScanner.getFatalErrors());

        return combined;
    }

    public List<PatternIssue> getPatternIssues() {
        if (!includeSubnets && activeSubnetScanner == null && subnetPatternIssues.isEmpty()) {
            return new ArrayList<>(patternIssues);
        }

        List<PatternIssue> combined = new ArrayList<>(patternIssues);
        combined.addAll(subnetPatternIssues);
        if (activeSubnetScanner != null) combined.addAll(activeSubnetScanner.getPatternIssues());

        return combined;
    }

    public int getNodesProcessed() {
        int totalNodes = nodesProcessed + completedSubnetNodeCount;
        if (activeSubnetScanner != null) totalNodes += activeSubnetScanner.getNodesProcessed();

        return totalNodes;
    }

    public boolean hasController() {
        return hasController;
    }

    public boolean isSubnetScanEnabled() {
        return includeSubnets;
    }
}
