package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.client.PowerToolsClientConfig;


/**
 * Client-side state for scanner overlay and rendering.
 * Stores detected loops, unloaded chunks, and channels received from server.
 * State is indexed by device ID to support multiple scanner devices simultaneously.
 * TODO: Split this into separate classes for each tab, to aggressively share the
 *       selection, sorting, and filtering logic.
 */
@SideOnly(Side.CLIENT)
public class ScannerClientState {

    /**
     * The currently active tab in the GUI.
     */
    public enum Tab {
        LOOPS,
        UNLOADED_CHUNKS,
        CHOKEPOINTS,
        MISSING_CHANNELS,
        FATAL_ERRORS,
        PATTERNS
    }

    /**
     * Sort order within dimension categories.
     */
    public enum SortMode {
        /** Sort by distance first, then name. */
        DISTANCE,
        /** Sort by name/description first, then distance. */
        NAME
    }

    /**
     * Per-device scan state.
     */
    public static class DeviceScanState {
        private ITextComponent statusMessage = new TextComponentString("");
        private boolean isScanComplete = false;
        private boolean subnetScanEnabled = false;
        private Tab currentTab = Tab.LOOPS;

        // Loop locations synced from server
        private final List<LoopLocationClient> loopLocations = new ArrayList<>();
        private final Set<Integer> selectedLoopIndices = new HashSet<>();
        private List<LoopLocationClient> sortedLoopLocations = null;

        // Unloaded chunk locations synced from server
        private final List<ChunkLocationClient> chunkLocations = new ArrayList<>();
        private final Set<Integer> selectedChunkIndices = new HashSet<>();
        private List<ChunkLocationClient> sortedChunkLocations = null;

        // Missing channel device locations synced from server
        private final List<MissingDeviceClient> missingDevices = new ArrayList<>();
        private final Set<Integer> selectedMissingIndices = new HashSet<>();
        private List<MissingDeviceClient> sortedMissingDevices = null;

        // Channel chokepoint locations synced from server
        private final List<ChokeLocationClient> chokeLocations = new ArrayList<>();
        private final Set<Integer> selectedChokeIndices = new HashSet<>();
        private List<ChokeLocationClient> sortedChokeLocations = null;

        // Fatal network errors synced from server
        private final List<FatalNetworkError> fatalErrors = new ArrayList<>();
        private final Set<Integer> selectedFatalIndices = new HashSet<>();
        private List<FatalNetworkError> sortedFatalErrors = null;

        // Pattern issues synced from server
        private final List<PatternIssue> patternIssues = new ArrayList<>();
        private final Set<Integer> selectedPatternIndices = new HashSet<>();
        private List<PatternIssue> sortedPatternIssues = null;

        public void clearData() {
            loopLocations.clear();
            selectedLoopIndices.clear();
            sortedLoopLocations = null;
            chunkLocations.clear();
            selectedChunkIndices.clear();
            sortedChunkLocations = null;
            missingDevices.clear();
            selectedMissingIndices.clear();
            sortedMissingDevices = null;
            chokeLocations.clear();
            selectedChokeIndices.clear();
            sortedChokeLocations = null;
            fatalErrors.clear();
            selectedFatalIndices.clear();
            sortedFatalErrors = null;
            patternIssues.clear();
            selectedPatternIndices.clear();
            sortedPatternIssues = null;
            isScanComplete = false;
            currentTab = Tab.LOOPS;
        }

        public void invalidateSortCache() {
            sortedLoopLocations = null;
            sortedChunkLocations = null;
            sortedMissingDevices = null;
            sortedChokeLocations = null;
            sortedFatalErrors = null;
            sortedPatternIssues = null;
        }
    }

    // Global state
    private static long activeDeviceId = 0L;

    // Per-device state map
    private static final Map<Long, DeviceScanState> deviceStates = new HashMap<>();

    /**
     * Client-side loop location data.
     */
    public static class LoopLocationClient extends AbstractLocation {
        public final String dimensionName;
        public final String blockName;
        public final String description;
        public final boolean isLoaded;

        public LoopLocationClient(BlockPos pos, int dimension, String dimensionName,
                String blockName, String description, boolean isLoaded) {
            super(pos, dimension);

            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.isLoaded = isLoaded;
        }
    }

    /**
     * Client-side unloaded chunk location data.
     */
    public static class ChunkLocationClient {
        public final int chunkX;
        public final int chunkZ;
        public final int dimension;
        public final String dimensionName;

        public ChunkLocationClient(int chunkX, int chunkZ, int dimension, String dimensionName) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
        }

        /**
         * Get the center block position of this chunk.
         */
        public BlockPos getCenterPos() {
            return new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
        }

        public double getDistanceFrom(BlockPos from) {
            BlockPos center = getCenterPos();
            double dx = center.getX() - from.getX();
            double dz = center.getZ() - from.getZ();

            return Math.sqrt(dx * dx + dz * dz);
        }

        public String getCoordString() {
            return String.format("[%d, %d]", chunkX, chunkZ);
        }
    }

    /**
     * Client-side missing channel device data.
     */
    public static class MissingDeviceClient extends AbstractLocation {
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

        /**
         * Get display name from ItemStack, falling back to description.
         */
        public String getDisplayName() {
            if (!itemStack.isEmpty()) return itemStack.getDisplayName();

            return description;
        }
    }

    /**
     * Client-side channel chokepoint location data.
     */
    public static class ChokeLocationClient extends AbstractLocation {
        public final String dimensionName;
        public final String blockName;
        public final String description;
        public final int usedChannels;
        public final int demandedChannels;
        public final int capacity;
        public final List<ConnectionFlowClient> connectionFlows;

        public ChokeLocationClient(BlockPos pos, int dimension, String dimensionName,
                String blockName, String description, int usedChannels, int demandedChannels,
                int capacity, List<ConnectionFlowClient> connectionFlows) {
            super(pos, dimension);

            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.usedChannels = usedChannels;
            this.demandedChannels = demandedChannels;
            this.capacity = capacity;
            this.connectionFlows = connectionFlows;
        }

        /**
         * Get excess channels that need to be shed.
         */
        public int getExcessChannels() {
            return Math.max(0, demandedChannels - capacity);
        }

        /**
         * Format: demanded/capacity
         */
        public String getChannelString() {
            return demandedChannels + "/" + capacity;
        }
    }

    /**
     * Client-side connection flow data for chokepoints.
     */
    public static class ConnectionFlowClient {
        public final int directionOrdinal; // EnumFacing ordinal, or -1 for internal
        public final int channels;
        public final int demandedChannels;
        public final BlockPos connectedPos;
        public final String connectedDescription;

        public ConnectionFlowClient(int directionOrdinal, int channels, int demandedChannels,
                BlockPos connectedPos, String connectedDescription) {
            this.directionOrdinal = directionOrdinal;
            this.channels = channels;
            this.demandedChannels = demandedChannels;
            this.connectedPos = connectedPos;
            this.connectedDescription = connectedDescription;
        }
    }

    /**
     * Shared per-tab access to state collections, selections, and sorted caches.
     */
    private static class TabData<T> {
        private final Tab tab;
        private final Function<DeviceScanState, List<T>> entriesAccessor;
        private final Function<DeviceScanState, Set<Integer>> selectedIndicesAccessor;
        private final Function<DeviceScanState, List<T>> sortedEntriesAccessor;
        private final BiConsumer<DeviceScanState, List<T>> sortedEntriesMutator;

        private TabData(Tab tab, Function<DeviceScanState, List<T>> entriesAccessor,
                Function<DeviceScanState, Set<Integer>> selectedIndicesAccessor,
                Function<DeviceScanState, List<T>> sortedEntriesAccessor,
                BiConsumer<DeviceScanState, List<T>> sortedEntriesMutator) {
            this.tab = tab;
            this.entriesAccessor = entriesAccessor;
            this.selectedIndicesAccessor = selectedIndicesAccessor;
            this.sortedEntriesAccessor = sortedEntriesAccessor;
            this.sortedEntriesMutator = sortedEntriesMutator;
        }

        private Tab getTab() {
            return tab;
        }

        private List<T> getEntries(DeviceScanState state) {
            return entriesAccessor.apply(state);
        }

        private Set<Integer> getSelectedIndices(DeviceScanState state) {
            return selectedIndicesAccessor.apply(state);
        }

        private List<T> getSortedEntries(DeviceScanState state) {
            return sortedEntriesAccessor.apply(state);
        }

        private void setSortedEntries(DeviceScanState state, List<T> sortedEntries) {
            sortedEntriesMutator.accept(state, sortedEntries);
        }
    }

    private static class PlayerSortContext {
        private final int playerDimension;
        private final BlockPos playerPos;
        private final SortMode sortMode;

        private PlayerSortContext(int playerDimension, BlockPos playerPos, SortMode sortMode) {
            this.playerDimension = playerDimension;
            this.playerPos = playerPos;
            this.sortMode = sortMode;
        }
    }

    private static final TabData<LoopLocationClient> LOOP_TAB_DATA = new TabData<>(
        Tab.LOOPS,
        state -> state.loopLocations,
        state -> state.selectedLoopIndices,
        state -> state.sortedLoopLocations,
        (state, sortedEntries) -> state.sortedLoopLocations = sortedEntries);

    private static final TabData<ChunkLocationClient> CHUNK_TAB_DATA = new TabData<>(
        Tab.UNLOADED_CHUNKS,
        state -> state.chunkLocations,
        state -> state.selectedChunkIndices,
        state -> state.sortedChunkLocations, (state, sortedEntries) -> state.sortedChunkLocations = sortedEntries);

    private static final TabData<MissingDeviceClient> MISSING_TAB_DATA = new TabData<>(
        Tab.MISSING_CHANNELS,
        state -> state.missingDevices,
        state -> state.selectedMissingIndices,
        state -> state.sortedMissingDevices,
        (state, sortedEntries) -> state.sortedMissingDevices = sortedEntries);

    private static final TabData<ChokeLocationClient> CHOKE_TAB_DATA = new TabData<>(
        Tab.CHOKEPOINTS,
        state -> state.chokeLocations,
        state -> state.selectedChokeIndices,
        state -> state.sortedChokeLocations,
        (state, sortedEntries) -> state.sortedChokeLocations = sortedEntries);

    private static final TabData<FatalNetworkError> FATAL_TAB_DATA = new TabData<>(
        Tab.FATAL_ERRORS,
        state -> state.fatalErrors,
        state -> state.selectedFatalIndices,
        state -> state.sortedFatalErrors,
        (state, sortedEntries) -> state.sortedFatalErrors = sortedEntries);

    private static final TabData<PatternIssue> PATTERN_TAB_DATA = new TabData<>(
        Tab.PATTERNS,
        state -> state.patternIssues,
        state -> state.selectedPatternIndices,
        state -> state.sortedPatternIssues,
        (state, sortedEntries) -> state.sortedPatternIssues = sortedEntries);

    private static TabData<?> getTabData(Tab tab) {
        switch (tab) {
            case UNLOADED_CHUNKS:
                return CHUNK_TAB_DATA;
            case CHOKEPOINTS:
                return CHOKE_TAB_DATA;
            case MISSING_CHANNELS:
                return MISSING_TAB_DATA;
            case FATAL_ERRORS:
                return FATAL_TAB_DATA;
            case PATTERNS:
                return PATTERN_TAB_DATA;
            case LOOPS:
            default:
                return LOOP_TAB_DATA;
        }
    }

    private static TabData<?> getCurrentTabData() {
        return getTabData(getCurrentTab());
    }

    private static <T> void replaceEntries(long deviceId, List<T> entries, TabData<T> tabData) {
        DeviceScanState state = getOrCreateState(deviceId);
        List<T> stateEntries = tabData.getEntries(state);

        stateEntries.clear();
        stateEntries.addAll(entries);
        tabData.setSortedEntries(state, null);
    }

    private static <T> List<T> getEntries(TabData<T> tabData) {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        return tabData.getEntries(state);
    }

    private static int getEntryCount(TabData<?> tabData) {
        DeviceScanState state = getActiveState();
        if (state == null) return 0;

        return tabData.getEntries(state).size();
    }

    private static void toggleSelectedIndex(Set<Integer> selectedIndices, int index) {
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(index);
            return;
        }

        selectedIndices.add(index);
    }

    private static void selectOnlyIndex(Set<Integer> selectedIndices, int index) {
        selectedIndices.clear();
        selectedIndices.add(index);
    }

    private static void selectAllIndices(List<?> entries, Set<Integer> selectedIndices) {
        selectedIndices.clear();
        for (int i = 0; i < entries.size(); i++) selectedIndices.add(i);
    }

    private static void toggleTabSelection(TabData<?> tabData, int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        toggleSelectedIndex(tabData.getSelectedIndices(state), index);
    }

    private static void selectOnlyTabEntry(TabData<?> tabData, int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        selectOnlyIndex(tabData.getSelectedIndices(state), index);
    }

    private static void selectAllTabEntries(TabData<?> tabData) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        selectAllIndices(tabData.getEntries(state), tabData.getSelectedIndices(state));
    }

    private static void clearTabSelection(TabData<?> tabData) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        tabData.getSelectedIndices(state).clear();
    }

    private static boolean isTabEntrySelected(TabData<?> tabData, int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return false;

        return tabData.getSelectedIndices(state).contains(index);
    }

    private static Set<Integer> getTabSelectedIndices(TabData<?> tabData) {
        DeviceScanState state = getActiveState();
        if (state == null) return new HashSet<>();

        return tabData.getSelectedIndices(state);
    }

    private static <T> List<T> getSelectedEntries(TabData<T> tabData) {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        List<T> entries = tabData.getEntries(state);
        List<T> result = new ArrayList<>();
        for (int index : tabData.getSelectedIndices(state)) {
            if (index >= 0 && index < entries.size()) result.add(entries.get(index));
        }

        return result;
    }

    private static int compareDimensionPriority(int aDimension, int bDimension, int playerDimension) {
        boolean aCurrentDimension = aDimension == playerDimension;
        boolean bCurrentDimension = bDimension == playerDimension;
        if (aCurrentDimension != bCurrentDimension) return aCurrentDimension ? -1 : 1;

        return Integer.compare(aDimension, bDimension);
    }

    private static String stripFormatting(String text) {
        if (text == null) return "";

        String strippedText = TextFormatting.getTextWithoutFormattingCodes(text);
        if (strippedText != null) return strippedText;

        return text;
    }

    private static int compareDisplayText(String left, String right) {
        return stripFormatting(left).compareToIgnoreCase(stripFormatting(right));
    }

    private static <T> List<T> getSortedEntries(TabData<T> tabData,
            Function<PlayerSortContext, Comparator<T>> comparatorFactory) {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        List<T> sortedEntries = tabData.getSortedEntries(state);
        if (sortedEntries != null) return sortedEntries;

        sortedEntries = new ArrayList<>(tabData.getEntries(state));

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            PlayerSortContext sortContext = new PlayerSortContext(
                    mc.player.dimension,
                    mc.player.getPosition(),
                    getSortMode(tabData.getTab()));
            sortedEntries.sort(comparatorFactory.apply(sortContext));
        }

        tabData.setSortedEntries(state, sortedEntries);
        return sortedEntries;
    }

    // ========== Sort Mode Management ==========

    /**
     * Get sort mode for a specific tab.
     */
    public static SortMode getSortMode(Tab tab) {
        return PowerToolsClientConfig.scanner.getSortMode(tab.ordinal()) == 1
            ? SortMode.NAME : SortMode.DISTANCE;
    }

    /**
     * Get sort mode for the currently active tab.
     */
    public static SortMode getCurrentSortMode() {
        return getSortMode(getCurrentTab());
    }

    public static void setSortMode(Tab tab, SortMode mode) {
        PowerToolsClientConfig.scanner.setSortMode(tab.ordinal(), mode.ordinal());
        invalidateSortCache();
    }

    public static void toggleCurrentSortMode() {
        Tab tab = getCurrentTab();
        SortMode current = getSortMode(tab);
        setSortMode(tab, current == SortMode.DISTANCE ? SortMode.NAME : SortMode.DISTANCE);
    }

    // ========== Device ID Management ==========

    /**
     * Get the currently active device ID for the GUI/overlays.
     */
    public static long getActiveDeviceId() {
        return activeDeviceId;
    }

    /**
     * Set the active device ID (when opening GUI or selecting a scanner).
     */
    public static void setActiveDeviceId(long deviceId) {
        activeDeviceId = deviceId;
    }

    /**
     * Get or create state for a specific device.
     */
    private static DeviceScanState getOrCreateState(long deviceId) {
        return deviceStates.computeIfAbsent(deviceId, k -> new DeviceScanState());
    }

    /**
     * Get state for a specific device, or null if not exists.
     */
    private static DeviceScanState getState(long deviceId) {
        return deviceStates.get(deviceId);
    }

    /**
     * Get state for the active device, or null if not exists.
     */
    private static DeviceScanState getActiveState() {
        return deviceStates.get(activeDeviceId);
    }

    /**
     * Check if a session exists for a specific device.
     */
    public static boolean hasSession(long deviceId) {
        return deviceStates.containsKey(deviceId);
    }

    /**
     * Remove session data for a specific device.
     */
    public static void removeSession(long deviceId) {
        deviceStates.remove(deviceId);
    }

    // ========== Tab Management ==========

    public static Tab getCurrentTab() {
        DeviceScanState state = getActiveState();

        return state != null ? state.currentTab : Tab.LOOPS;
    }

    public static void setCurrentTab(Tab tab) {
        DeviceScanState state = getActiveState();
        if (state != null) state.currentTab = tab;
    }

    // ========== Global State Management ==========

    /**
     * Check if there's an active session for the currently active device.
     */
    public static boolean hasActiveSession() {
        return hasSession(activeDeviceId);
    }

    /**
     * Set session active state for a specific device.
     */
    public static void setActiveSession(long deviceId, boolean active) {
        if (active) {
            getOrCreateState(deviceId);
        } else {
            removeSession(deviceId);
        }
    }

    public static ITextComponent getStatusMessage() {
        DeviceScanState state = getActiveState();

        return state != null ? state.statusMessage : new TextComponentString("");
    }

    public static void setStatusMessage(long deviceId, ITextComponent message) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.statusMessage = message;
    }

    public static boolean isScanComplete() {
        DeviceScanState state = getActiveState();

        return state != null && state.isScanComplete;
    }

    public static void setScanComplete(long deviceId, boolean complete) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.isScanComplete = complete;
    }

    public static boolean isSubnetScanEnabled() {
        DeviceScanState state = getActiveState();

        return state != null && state.subnetScanEnabled;
    }

    public static void setSubnetScanEnabled(long deviceId, boolean enabled) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.subnetScanEnabled = enabled;
    }

    public static void initSubnetState(long deviceId, boolean subnetScanEnabled) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.subnetScanEnabled = subnetScanEnabled;
    }

    // ========== Data Management ==========

    public static void clearData(long deviceId) {
        DeviceScanState state = getState(deviceId);
        if (state != null) state.clearData();
    }

    // ========== Loop Location Management ==========

    public static void setLoopLocations(long deviceId, List<LoopLocationClient> locations) {
        replaceEntries(deviceId, locations, LOOP_TAB_DATA);
    }

    public static List<LoopLocationClient> getLoopLocations() {
        return getEntries(LOOP_TAB_DATA);
    }

    public static int getLoopCount() {
        return getEntryCount(LOOP_TAB_DATA);
    }

    // ========== Chunk Location Management ==========

    public static void setChunkLocations(long deviceId, List<ChunkLocationClient> locations) {
        replaceEntries(deviceId, locations, CHUNK_TAB_DATA);
    }

    public static List<ChunkLocationClient> getChunkLocations() {
        return getEntries(CHUNK_TAB_DATA);
    }

    public static int getChunkCount() {
        return getEntryCount(CHUNK_TAB_DATA);
    }

    // ========== Missing Device Management ==========

    public static void setMissingDevices(long deviceId, List<MissingDeviceClient> devices) {
        replaceEntries(deviceId, devices, MISSING_TAB_DATA);
    }

    public static List<MissingDeviceClient> getMissingDevices() {
        return getEntries(MISSING_TAB_DATA);
    }

    public static int getMissingCount() {
        return getEntryCount(MISSING_TAB_DATA);
    }

    // ========== Chokepoint Location Management ==========

    public static void setChokeLocations(long deviceId, List<ChokeLocationClient> locations) {
        replaceEntries(deviceId, locations, CHOKE_TAB_DATA);
    }

    public static List<ChokeLocationClient> getChokeLocations() {
        return getEntries(CHOKE_TAB_DATA);
    }

    public static int getChokeCount() {
        return getEntryCount(CHOKE_TAB_DATA);
    }

    // ========== Fatal Error Management ==========

    public static void setFatalErrors(long deviceId, List<FatalNetworkError> errors) {
        replaceEntries(deviceId, errors, FATAL_TAB_DATA);
    }

    public static List<FatalNetworkError> getFatalErrors() {
        return getEntries(FATAL_TAB_DATA);
    }

    public static int getFatalCount() {
        return getEntryCount(FATAL_TAB_DATA);
    }

    // ========== Pattern Issue Management ==========

    public static void setPatternIssues(long deviceId, List<PatternIssue> issues) {
        replaceEntries(deviceId, issues, PATTERN_TAB_DATA);
    }

    public static List<PatternIssue> getPatternIssues() {
        return getEntries(PATTERN_TAB_DATA);
    }

    public static int getPatternCount() {
        return getEntryCount(PATTERN_TAB_DATA);
    }

    // ========== Loop Selection Management ==========

    public static void toggleLoopSelection(int index) {
        toggleTabSelection(LOOP_TAB_DATA, index);
    }

    public static void selectOnlyLoop(int index) {
        selectOnlyTabEntry(LOOP_TAB_DATA, index);
    }

    public static void selectAllLoops() {
        selectAllTabEntries(LOOP_TAB_DATA);
    }

    public static void deselectAllLoops() {
        clearTabSelection(LOOP_TAB_DATA);
    }

    public static boolean isLoopSelected(int index) {
        return isTabEntrySelected(LOOP_TAB_DATA, index);
    }

    public static Set<Integer> getSelectedLoopIndices() {
        return getTabSelectedIndices(LOOP_TAB_DATA);
    }

    public static List<LoopLocationClient> getSelectedLoops() {
        return getSelectedEntries(LOOP_TAB_DATA);
    }

    // ========== Chunk Selection Management ==========

    public static void toggleChunkSelection(int index) {
        toggleTabSelection(CHUNK_TAB_DATA, index);
    }

    public static void selectOnlyChunk(int index) {
        selectOnlyTabEntry(CHUNK_TAB_DATA, index);
    }

    public static void selectAllChunks() {
        selectAllTabEntries(CHUNK_TAB_DATA);
    }

    public static void deselectAllChunks() {
        clearTabSelection(CHUNK_TAB_DATA);
    }

    public static boolean isChunkSelected(int index) {
        return isTabEntrySelected(CHUNK_TAB_DATA, index);
    }

    public static Set<Integer> getSelectedChunkIndices() {
        return getTabSelectedIndices(CHUNK_TAB_DATA);
    }

    public static List<ChunkLocationClient> getSelectedChunks() {
        return getSelectedEntries(CHUNK_TAB_DATA);
    }

    // ========== Missing Device Selection Management ==========

    public static void toggleMissingSelection(int index) {
        toggleTabSelection(MISSING_TAB_DATA, index);
    }

    public static void selectOnlyMissing(int index) {
        selectOnlyTabEntry(MISSING_TAB_DATA, index);
    }

    public static void selectAllMissing() {
        selectAllTabEntries(MISSING_TAB_DATA);
    }

    public static void deselectAllMissing() {
        clearTabSelection(MISSING_TAB_DATA);
    }

    public static boolean isMissingSelected(int index) {
        return isTabEntrySelected(MISSING_TAB_DATA, index);
    }

    public static Set<Integer> getSelectedMissingIndices() {
        return getTabSelectedIndices(MISSING_TAB_DATA);
    }

    public static List<MissingDeviceClient> getSelectedMissing() {
        return getSelectedEntries(MISSING_TAB_DATA);
    }

    // ========== Chokepoint Selection Management ==========

    public static void toggleChokeSelection(int index) {
        toggleTabSelection(CHOKE_TAB_DATA, index);
    }

    public static void selectOnlyChoke(int index) {
        selectOnlyTabEntry(CHOKE_TAB_DATA, index);
    }

    public static void selectAllChokes() {
        selectAllTabEntries(CHOKE_TAB_DATA);
    }

    public static void deselectAllChokes() {
        clearTabSelection(CHOKE_TAB_DATA);
    }

    public static boolean isChokeSelected(int index) {
        return isTabEntrySelected(CHOKE_TAB_DATA, index);
    }

    public static Set<Integer> getSelectedChokeIndices() {
        return getTabSelectedIndices(CHOKE_TAB_DATA);
    }

    public static List<ChokeLocationClient> getSelectedChokes() {
        return getSelectedEntries(CHOKE_TAB_DATA);
    }

    // ========== Fatal Error Selection Management ==========

    public static void toggleFatalSelection(int index) {
        toggleTabSelection(FATAL_TAB_DATA, index);
    }

    public static void selectOnlyFatal(int index) {
        selectOnlyTabEntry(FATAL_TAB_DATA, index);
    }

    public static void selectAllFatal() {
        selectAllTabEntries(FATAL_TAB_DATA);
    }

    public static void deselectAllFatal() {
        clearTabSelection(FATAL_TAB_DATA);
    }

    public static boolean isFatalSelected(int index) {
        return isTabEntrySelected(FATAL_TAB_DATA, index);
    }

    public static Set<Integer> getSelectedFatalIndices() {
        return getTabSelectedIndices(FATAL_TAB_DATA);
    }

    public static List<FatalNetworkError> getSelectedFatalErrors() {
        return getSelectedEntries(FATAL_TAB_DATA);
    }

    public static String getFatalErrorDisplayText(FatalNetworkError error) {
        return I18n.format(error.getCategory().getEntryKey(), error.getDescription());
    }

    // ========== Pattern Issue Selection Management ==========

    public static void togglePatternSelection(int index) {
        toggleTabSelection(PATTERN_TAB_DATA, index);
    }

    public static void selectOnlyPattern(int index) {
        selectOnlyTabEntry(PATTERN_TAB_DATA, index);
    }

    public static void selectAllPatterns() {
        selectAllTabEntries(PATTERN_TAB_DATA);
    }

    public static void deselectAllPatterns() {
        clearTabSelection(PATTERN_TAB_DATA);
    }

    public static boolean isPatternSelected(int index) {
        return isTabEntrySelected(PATTERN_TAB_DATA, index);
    }

    public static Set<Integer> getSelectedPatternIndices() {
        return getTabSelectedIndices(PATTERN_TAB_DATA);
    }

    public static List<PatternIssue> getSelectedPatternIssues() {
        return getSelectedEntries(PATTERN_TAB_DATA);
    }

    public static String getPatternIssueDisplayText(PatternIssue issue) {
        return issue.getDescription();
    }

    public static String getPatternIssueTooltipText(PatternIssue issue) {
        return issue.getSummary();
    }

    // ========== Generic Selection for Current Tab ==========

    public static void selectAll() {
        selectAllTabEntries(getCurrentTabData());
    }

    public static void deselectAll() {
        clearTabSelection(getCurrentTabData());
    }

    public static void toggleSelection(int index) {
        toggleTabSelection(getCurrentTabData(), index);
    }

    public static void selectOnly(int index) {
        selectOnlyTabEntry(getCurrentTabData(), index);
    }

    public static boolean isSelected(int index) {
        return isTabEntrySelected(getCurrentTabData(), index);
    }

    public static Set<Integer> getSelectedIndices() {
        return getTabSelectedIndices(getCurrentTabData());
    }

    public static int getCurrentTabItemCount() {
        return getEntryCount(getCurrentTabData());
    }

    public static int getCurrentTabSelectedCount() {
        return getTabSelectedIndices(getCurrentTabData()).size();
    }

    // ========== Sorted/Grouped Access ==========

    /**
     * Get loop locations sorted by dimension (current first), then by sort mode.
     */
    public static List<LoopLocationClient> getSortedLoopLocations() {
        return getSortedEntries(LOOP_TAB_DATA, sortContext -> (a, b) -> {
            int dimensionCompare = compareDimensionPriority(
                    a.dimension,
                    b.dimension,
                    sortContext.playerDimension);
            if (dimensionCompare != 0) return dimensionCompare;

            if (sortContext.sortMode == SortMode.NAME) {
                int nameCompare = compareDisplayText(a.description, b.description);
                if (nameCompare != 0) return nameCompare;
            }

            return Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
        });
    }

    /**
     * Get chunk locations sorted by dimension (current first), then by sort mode.
     */
    public static List<ChunkLocationClient> getSortedChunkLocations() {
        return getSortedEntries(CHUNK_TAB_DATA, sortContext -> (a, b) -> {
            int dimensionCompare = compareDimensionPriority(
                    a.dimension,
                    b.dimension,
                    sortContext.playerDimension);
            if (dimensionCompare != 0) return dimensionCompare;

            if (sortContext.sortMode == SortMode.NAME) {
                int chunkXCompare = Integer.compare(a.chunkX, b.chunkX);
                if (chunkXCompare != 0) return chunkXCompare;

                int chunkZCompare = Integer.compare(a.chunkZ, b.chunkZ);
                if (chunkZCompare != 0) return chunkZCompare;
            }

            return Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
        });
    }

    /**
     * Get missing device locations sorted by dimension (current first), then by sort mode.
     */
    public static List<MissingDeviceClient> getSortedMissingDevices() {
        return getSortedEntries(MISSING_TAB_DATA, sortContext -> (a, b) -> {
            int dimensionCompare = compareDimensionPriority(
                    a.dimension,
                    b.dimension,
                    sortContext.playerDimension);
            if (dimensionCompare != 0) return dimensionCompare;

            String aName = stripFormatting(a.getDisplayName());
            String bName = stripFormatting(b.getDisplayName());

            if (sortContext.sortMode == SortMode.NAME) {
                int nameCompare = aName.compareToIgnoreCase(bName);
                if (nameCompare != 0) return nameCompare;

                return Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
            }

            int distanceCompare = Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
            if (distanceCompare != 0) return distanceCompare;

            return aName.compareToIgnoreCase(bName);
        });
    }

    /**
     * Get chokepoint locations sorted by dimension (current first), then severity,
     * with sort mode controlling secondary ordering (name vs distance).
     */
    public static List<ChokeLocationClient> getSortedChokeLocations() {
        return getSortedEntries(CHOKE_TAB_DATA, sortContext -> (a, b) -> {
            int dimensionCompare = compareDimensionPriority(
                    a.dimension,
                    b.dimension,
                    sortContext.playerDimension);
            if (dimensionCompare != 0) return dimensionCompare;

            int excessCompare = Integer.compare(b.getExcessChannels(), a.getExcessChannels());
            if (excessCompare != 0) return excessCompare;

            if (sortContext.sortMode == SortMode.NAME) {
                int nameCompare = compareDisplayText(a.description, b.description);
                if (nameCompare != 0) return nameCompare;
            }

            return Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
        });
    }

    /**
     * Get fatal errors sorted by category first, then by dimension and current sort mode.
     */
    public static List<FatalNetworkError> getSortedFatalErrors() {
        return getSortedEntries(FATAL_TAB_DATA, sortContext -> (a, b) -> {
            int categoryCompare = Integer.compare(a.getCategory().ordinal(), b.getCategory().ordinal());
            if (categoryCompare != 0) return categoryCompare;

            int dimensionCompare = compareDimensionPriority(
                    a.getDimension(),
                    b.getDimension(),
                    sortContext.playerDimension);
            if (dimensionCompare != 0) return dimensionCompare;

            String aName = stripFormatting(getFatalErrorDisplayText(a));
            String bName = stripFormatting(getFatalErrorDisplayText(b));

            if (sortContext.sortMode == SortMode.NAME) {
                int nameCompare = aName.compareToIgnoreCase(bName);
                if (nameCompare != 0) return nameCompare;

                return Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
            }

            int distanceCompare = Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
            if (distanceCompare != 0) return distanceCompare;

            return aName.compareToIgnoreCase(bName);
        });
    }

    /**
     * Get pattern issues sorted by category first, then by dimension and current sort mode.
     */
    public static List<PatternIssue> getSortedPatternIssues() {
        return getSortedEntries(PATTERN_TAB_DATA, sortContext -> (a, b) -> {
            int categoryCompare = Integer.compare(a.getCategory().ordinal(), b.getCategory().ordinal());
            if (categoryCompare != 0) return categoryCompare;

            int dimensionCompare = compareDimensionPriority(
                    a.getDimension(),
                    b.getDimension(),
                    sortContext.playerDimension);
            if (dimensionCompare != 0) return dimensionCompare;

            String aName = stripFormatting(getPatternIssueDisplayText(a));
            String bName = stripFormatting(getPatternIssueDisplayText(b));

            if (sortContext.sortMode == SortMode.NAME) {
                int nameCompare = aName.compareToIgnoreCase(bName);
                if (nameCompare != 0) return nameCompare;

                return Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
            }

            int distanceCompare = Double.compare(a.getDistanceFrom(sortContext.playerPos), b.getDistanceFrom(sortContext.playerPos));
            if (distanceCompare != 0) return distanceCompare;

            return aName.compareToIgnoreCase(bName);
        });
    }

    /**
     * Invalidate sorted cache for the active device (call when player moves significantly).
     */
    public static void invalidateSortCache() {
        DeviceScanState state = getActiveState();
        if (state != null) state.invalidateSortCache();
    }
}
