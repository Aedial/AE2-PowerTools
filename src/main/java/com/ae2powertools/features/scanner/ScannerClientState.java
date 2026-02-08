package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Client-side state for scanner overlay and rendering.
 * Stores detected loops, unloaded chunks, and channels received from server.
 * State is indexed by device ID to support multiple scanner devices simultaneously.
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
        MISSING_CHANNELS
    }

    /**
     * Per-device scan state.
     */
    public static class DeviceScanState {
        private String statusMessage = "";
        private boolean isScanComplete = false;
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
            isScanComplete = false;
            currentTab = Tab.LOOPS;
        }

        public void invalidateSortCache() {
            sortedLoopLocations = null;
            sortedChunkLocations = null;
            sortedMissingDevices = null;
            sortedChokeLocations = null;
        }
    }

    // Global state
    private static long activeDeviceId = 0L;

    // Per-device state map
    private static final Map<Long, DeviceScanState> deviceStates = new HashMap<>();

    /**
     * Client-side loop location data.
     */
    public static class LoopLocationClient {
        public final BlockPos pos;
        public final int dimension;
        public final String dimensionName;
        public final String blockName;
        public final String description;
        public final boolean isLoaded;

        public LoopLocationClient(BlockPos pos, int dimension, String dimensionName,
                String blockName, String description, boolean isLoaded) {
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.isLoaded = isLoaded;
        }

        public double getDistanceFrom(BlockPos from) {
            double dx = pos.getX() - from.getX();
            double dy = pos.getY() - from.getY();
            double dz = pos.getZ() - from.getZ();

            return Math.sqrt(dx * dx + dy * dy + dz * dz);
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
    public static class MissingDeviceClient {
        public final BlockPos pos;
        public final int dimension;
        public final String dimensionName;
        public final ItemStack itemStack;
        public final String description;

        public MissingDeviceClient(BlockPos pos, int dimension, String dimensionName,
                ItemStack itemStack, String description) {
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.itemStack = itemStack != null ? itemStack.copy() : ItemStack.EMPTY;
            this.description = description;
        }

        public double getDistanceFrom(BlockPos from) {
            double dx = pos.getX() - from.getX();
            double dy = pos.getY() - from.getY();
            double dz = pos.getZ() - from.getZ();

            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /**
         * Get display name from ItemStack, falling back to description.
         */
        public String getDisplayName() {
            if (!itemStack.isEmpty()) {
                return itemStack.getDisplayName();
            }

            return description;
        }
    }

    /**
     * Client-side channel chokepoint location data.
     */
    public static class ChokeLocationClient {
        public final BlockPos pos;
        public final int dimension;
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
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.usedChannels = usedChannels;
            this.demandedChannels = demandedChannels;
            this.capacity = capacity;
            this.connectionFlows = connectionFlows;
        }

        public double getDistanceFrom(BlockPos from) {
            double dx = pos.getX() - from.getX();
            double dy = pos.getY() - from.getY();
            double dz = pos.getZ() - from.getZ();

            return Math.sqrt(dx * dx + dy * dy + dz * dz);
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

    public static String getStatusMessage() {
        DeviceScanState state = getActiveState();

        return state != null ? state.statusMessage : "";
    }

    public static void setStatusMessage(long deviceId, String message) {
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

    // ========== Data Management ==========

    public static void clearData(long deviceId) {
        DeviceScanState state = getState(deviceId);
        if (state != null) state.clearData();
    }

    // ========== Loop Location Management ==========

    public static void setLoopLocations(long deviceId, List<LoopLocationClient> locations) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.loopLocations.clear();
        state.loopLocations.addAll(locations);
        state.sortedLoopLocations = null;
    }

    public static List<LoopLocationClient> getLoopLocations() {
        DeviceScanState state = getActiveState();

        return state != null ? state.loopLocations : new ArrayList<>();
    }

    public static int getLoopCount() {
        DeviceScanState state = getActiveState();

        return state != null ? state.loopLocations.size() : 0;
    }

    // ========== Chunk Location Management ==========

    public static void setChunkLocations(long deviceId, List<ChunkLocationClient> locations) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.chunkLocations.clear();
        state.chunkLocations.addAll(locations);
        state.sortedChunkLocations = null;
    }

    public static List<ChunkLocationClient> getChunkLocations() {
        DeviceScanState state = getActiveState();

        return state != null ? state.chunkLocations : new ArrayList<>();
    }

    public static int getChunkCount() {
        DeviceScanState state = getActiveState();

        return state != null ? state.chunkLocations.size() : 0;
    }

    // ========== Missing Device Management ==========

    public static void setMissingDevices(long deviceId, List<MissingDeviceClient> devices) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.missingDevices.clear();
        state.missingDevices.addAll(devices);
        state.sortedMissingDevices = null;
    }

    public static List<MissingDeviceClient> getMissingDevices() {
        DeviceScanState state = getActiveState();

        return state != null ? state.missingDevices : new ArrayList<>();
    }

    public static int getMissingCount() {
        DeviceScanState state = getActiveState();

        return state != null ? state.missingDevices.size() : 0;
    }

    // ========== Chokepoint Location Management ==========

    public static void setChokeLocations(long deviceId, List<ChokeLocationClient> locations) {
        DeviceScanState state = getOrCreateState(deviceId);
        state.chokeLocations.clear();
        state.chokeLocations.addAll(locations);
        state.sortedChokeLocations = null;
    }

    public static List<ChokeLocationClient> getChokeLocations() {
        DeviceScanState state = getActiveState();

        return state != null ? state.chokeLocations : new ArrayList<>();
    }

    public static int getChokeCount() {
        DeviceScanState state = getActiveState();

        return state != null ? state.chokeLocations.size() : 0;
    }

    // ========== Loop Selection Management ==========

    public static void toggleLoopSelection(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        if (state.selectedLoopIndices.contains(index)) {
            state.selectedLoopIndices.remove(index);
        } else {
            state.selectedLoopIndices.add(index);
        }
    }

    public static void selectOnlyLoop(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedLoopIndices.clear();
        state.selectedLoopIndices.add(index);
    }

    public static void selectAllLoops() {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedLoopIndices.clear();
        for (int i = 0; i < state.loopLocations.size(); i++) state.selectedLoopIndices.add(i);
    }

    public static void deselectAllLoops() {
        DeviceScanState state = getActiveState();
        if (state != null) state.selectedLoopIndices.clear();
    }

    public static boolean isLoopSelected(int index) {
        DeviceScanState state = getActiveState();

        return state != null && state.selectedLoopIndices.contains(index);
    }

    public static Set<Integer> getSelectedLoopIndices() {
        DeviceScanState state = getActiveState();

        return state != null ? state.selectedLoopIndices : new HashSet<>();
    }

    public static List<LoopLocationClient> getSelectedLoops() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        List<LoopLocationClient> result = new ArrayList<>();
        for (int index : state.selectedLoopIndices) {
            if (index >= 0 && index < state.loopLocations.size()) {
                result.add(state.loopLocations.get(index));
            }
        }

        return result;
    }

    // ========== Chunk Selection Management ==========

    public static void toggleChunkSelection(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        if (state.selectedChunkIndices.contains(index)) {
            state.selectedChunkIndices.remove(index);
        } else {
            state.selectedChunkIndices.add(index);
        }
    }

    public static void selectOnlyChunk(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedChunkIndices.clear();
        state.selectedChunkIndices.add(index);
    }

    public static void selectAllChunks() {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedChunkIndices.clear();
        for (int i = 0; i < state.chunkLocations.size(); i++) state.selectedChunkIndices.add(i);
    }

    public static void deselectAllChunks() {
        DeviceScanState state = getActiveState();
        if (state != null) state.selectedChunkIndices.clear();
    }

    public static boolean isChunkSelected(int index) {
        DeviceScanState state = getActiveState();

        return state != null && state.selectedChunkIndices.contains(index);
    }

    public static Set<Integer> getSelectedChunkIndices() {
        DeviceScanState state = getActiveState();

        return state != null ? state.selectedChunkIndices : new HashSet<>();
    }

    public static List<ChunkLocationClient> getSelectedChunks() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        List<ChunkLocationClient> result = new ArrayList<>();
        for (int index : state.selectedChunkIndices) {
            if (index >= 0 && index < state.chunkLocations.size()) {
                result.add(state.chunkLocations.get(index));
            }
        }

        return result;
    }

    // ========== Missing Device Selection Management ==========

    public static void toggleMissingSelection(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        if (state.selectedMissingIndices.contains(index)) {
            state.selectedMissingIndices.remove(index);
        } else {
            state.selectedMissingIndices.add(index);
        }
    }

    public static void selectOnlyMissing(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedMissingIndices.clear();
        state.selectedMissingIndices.add(index);
    }

    public static void selectAllMissing() {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedMissingIndices.clear();
        for (int i = 0; i < state.missingDevices.size(); i++) state.selectedMissingIndices.add(i);
    }

    public static void deselectAllMissing() {
        DeviceScanState state = getActiveState();
        if (state != null) state.selectedMissingIndices.clear();
    }

    public static boolean isMissingSelected(int index) {
        DeviceScanState state = getActiveState();

        return state != null && state.selectedMissingIndices.contains(index);
    }

    public static Set<Integer> getSelectedMissingIndices() {
        DeviceScanState state = getActiveState();

        return state != null ? state.selectedMissingIndices : new HashSet<>();
    }

    public static List<MissingDeviceClient> getSelectedMissing() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        List<MissingDeviceClient> result = new ArrayList<>();
        for (int index : state.selectedMissingIndices) {
            if (index >= 0 && index < state.missingDevices.size()) {
                result.add(state.missingDevices.get(index));
            }
        }

        return result;
    }

    // ========== Chokepoint Selection Management ==========

    public static void toggleChokeSelection(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        if (state.selectedChokeIndices.contains(index)) {
            state.selectedChokeIndices.remove(index);
        } else {
            state.selectedChokeIndices.add(index);
        }
    }

    public static void selectOnlyChoke(int index) {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedChokeIndices.clear();
        state.selectedChokeIndices.add(index);
    }

    public static void selectAllChokes() {
        DeviceScanState state = getActiveState();
        if (state == null) return;

        state.selectedChokeIndices.clear();
        for (int i = 0; i < state.chokeLocations.size(); i++) state.selectedChokeIndices.add(i);
    }

    public static void deselectAllChokes() {
        DeviceScanState state = getActiveState();
        if (state != null) state.selectedChokeIndices.clear();
    }

    public static boolean isChokeSelected(int index) {
        DeviceScanState state = getActiveState();

        return state != null && state.selectedChokeIndices.contains(index);
    }

    public static Set<Integer> getSelectedChokeIndices() {
        DeviceScanState state = getActiveState();

        return state != null ? state.selectedChokeIndices : new HashSet<>();
    }

    public static List<ChokeLocationClient> getSelectedChokes() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        List<ChokeLocationClient> result = new ArrayList<>();
        for (int index : state.selectedChokeIndices) {
            if (index >= 0 && index < state.chokeLocations.size()) {
                result.add(state.chokeLocations.get(index));
            }
        }

        return result;
    }

    // ========== Generic Selection for Current Tab ==========

    public static void selectAll() {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            selectAllLoops();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            selectAllChunks();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            selectAllMissing();
        } else {
            selectAllChokes();
        }
    }

    public static void deselectAll() {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            deselectAllLoops();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            deselectAllChunks();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            deselectAllMissing();
        } else {
            deselectAllChokes();
        }
    }

    public static void toggleSelection(int index) {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            toggleLoopSelection(index);
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            toggleChunkSelection(index);
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            toggleMissingSelection(index);
        } else {
            toggleChokeSelection(index);
        }
    }

    public static void selectOnly(int index) {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            selectOnlyLoop(index);
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            selectOnlyChunk(index);
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            selectOnlyMissing(index);
        } else {
            selectOnlyChoke(index);
        }
    }

    public static boolean isSelected(int index) {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            return isLoopSelected(index);
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            return isChunkSelected(index);
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            return isMissingSelected(index);
        } else {
            return isChokeSelected(index);
        }
    }

    public static Set<Integer> getSelectedIndices() {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            return getSelectedLoopIndices();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            return getSelectedChunkIndices();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            return getSelectedMissingIndices();
        } else {
            return getSelectedChokeIndices();
        }
    }

    public static int getCurrentTabItemCount() {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            return getLoopCount();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            return getChunkCount();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            return getMissingCount();
        } else {
            return getChokeCount();
        }
    }

    public static int getCurrentTabSelectedCount() {
        Tab currentTab = getCurrentTab();

        if (currentTab == Tab.LOOPS) {
            return getSelectedLoopIndices().size();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            return getSelectedChunkIndices().size();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            return getSelectedMissingIndices().size();
        } else {
            return getSelectedChokeIndices().size();
        }
    }

    // ========== Sorted/Grouped Access ==========

    /**
     * Get loop locations sorted by dimension (current first), then distance.
     */
    public static List<LoopLocationClient> getSortedLoopLocations() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        if (state.sortedLoopLocations == null) {
            state.sortedLoopLocations = new ArrayList<>(state.loopLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                state.sortedLoopLocations.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return state.sortedLoopLocations;
    }

    /**
     * Get chunk locations sorted by dimension (current first), then distance.
     */
    public static List<ChunkLocationClient> getSortedChunkLocations() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        if (state.sortedChunkLocations == null) {
            state.sortedChunkLocations = new ArrayList<>(state.chunkLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                state.sortedChunkLocations.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return state.sortedChunkLocations;
    }

    /**
     * Get missing device locations sorted by dimension (current first), then by display name.
     */
    public static List<MissingDeviceClient> getSortedMissingDevices() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        if (state.sortedMissingDevices == null) {
            state.sortedMissingDevices = new ArrayList<>(state.missingDevices);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                state.sortedMissingDevices.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    // Sort by display name, then by distance
                    int nameCompare = a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
                    if (nameCompare != 0) return nameCompare;

                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return state.sortedMissingDevices;
    }

    /**
     * Get chokepoint locations sorted by dimension (current first), then by excess channels (worst first).
     */
    public static List<ChokeLocationClient> getSortedChokeLocations() {
        DeviceScanState state = getActiveState();
        if (state == null) return new ArrayList<>();

        if (state.sortedChokeLocations == null) {
            state.sortedChokeLocations = new ArrayList<>(state.chokeLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                state.sortedChokeLocations.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    // Sort by excess channels (most severe first)
                    int excessCompare = Integer.compare(b.getExcessChannels(), a.getExcessChannels());
                    if (excessCompare != 0) return excessCompare;

                    // Then by distance
                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return state.sortedChokeLocations;
    }

    /**
     * Invalidate sorted cache for the active device (call when player moves significantly).
     */
    public static void invalidateSortCache() {
        DeviceScanState state = getActiveState();
        if (state != null) state.invalidateSortCache();
    }
}
