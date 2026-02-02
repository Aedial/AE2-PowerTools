package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Client-side state for scanner overlay and rendering.
 * Stores detected loops, unloaded chunks, and channels received from server.
 * TODO: refactor into array-based functions, with Tab enum supplying indexes.
 * TODO: index cache to use multiple devices simultaneously.
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

    private static boolean overlayEnabled = true;
    private static boolean hasActiveSession = false;
    private static String statusMessage = "";
    private static boolean isScanComplete = false;
    private static Tab currentTab = Tab.LOOPS;

    // Loop locations synced from server
    private static final List<LoopLocationClient> loopLocations = new ArrayList<>();
    private static final Set<Integer> selectedLoopIndices = new HashSet<>();
    private static List<LoopLocationClient> sortedLoopLocations = null;

    // Unloaded chunk locations synced from server
    private static final List<ChunkLocationClient> chunkLocations = new ArrayList<>();
    private static final Set<Integer> selectedChunkIndices = new HashSet<>();
    private static List<ChunkLocationClient> sortedChunkLocations = null;

    // Missing channel device locations synced from server
    private static final List<MissingDeviceClient> missingDevices = new ArrayList<>();
    private static final Set<Integer> selectedMissingIndices = new HashSet<>();
    private static List<MissingDeviceClient> sortedMissingDevices = null;

    // Channel chokepoint locations synced from server
    private static final List<ChokeLocationClient> chokeLocations = new ArrayList<>();
    private static final Set<Integer> selectedChokeIndices = new HashSet<>();
    private static List<ChokeLocationClient> sortedChokeLocations = null;

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

    // ========== Tab Management ==========

    public static Tab getCurrentTab() {
        return currentTab;
    }

    public static void setCurrentTab(Tab tab) {
        currentTab = tab;
    }

    // ========== State Management ==========

    public static void setOverlayEnabled(boolean enabled) {
        overlayEnabled = enabled;
    }

    public static boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public static void toggleOverlay() {
        overlayEnabled = !overlayEnabled;
    }

    public static boolean hasActiveSession() {
        return hasActiveSession;
    }

    public static void setActiveSession(boolean active) {
        hasActiveSession = active;

        if (!active) clearData();
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static void setStatusMessage(String message) {
        statusMessage = message;
    }

    public static boolean isScanComplete() {
        return isScanComplete;
    }

    public static void setScanComplete(boolean complete) {
        isScanComplete = complete;
    }

    // ========== Data Management ==========

    public static void clearData() {
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

    // ========== Loop Location Management ==========

    public static void setLoopLocations(List<LoopLocationClient> locations) {
        loopLocations.clear();
        loopLocations.addAll(locations);
        sortedLoopLocations = null;
    }

    public static List<LoopLocationClient> getLoopLocations() {
        return loopLocations;
    }

    public static int getLoopCount() {
        return loopLocations.size();
    }

    // ========== Chunk Location Management ==========

    public static void setChunkLocations(List<ChunkLocationClient> locations) {
        chunkLocations.clear();
        chunkLocations.addAll(locations);
        sortedChunkLocations = null;
    }

    public static List<ChunkLocationClient> getChunkLocations() {
        return chunkLocations;
    }

    public static int getChunkCount() {
        return chunkLocations.size();
    }

    // ========== Missing Device Management ==========

    public static void setMissingDevices(List<MissingDeviceClient> devices) {
        missingDevices.clear();
        missingDevices.addAll(devices);
        sortedMissingDevices = null;
    }

    public static List<MissingDeviceClient> getMissingDevices() {
        return missingDevices;
    }

    public static int getMissingCount() {
        return missingDevices.size();
    }

    // ========== Chokepoint Location Management ==========

    public static void setChokeLocations(List<ChokeLocationClient> locations) {
        chokeLocations.clear();
        chokeLocations.addAll(locations);
        sortedChokeLocations = null;
    }

    public static List<ChokeLocationClient> getChokeLocations() {
        return chokeLocations;
    }

    public static int getChokeCount() {
        return chokeLocations.size();
    }

    // ========== Loop Selection Management ==========

    public static void toggleLoopSelection(int index) {
        if (selectedLoopIndices.contains(index)) {
            selectedLoopIndices.remove(index);
        } else {
            selectedLoopIndices.add(index);
        }
    }

    public static void selectOnlyLoop(int index) {
        selectedLoopIndices.clear();
        selectedLoopIndices.add(index);
    }

    public static void selectAllLoops() {
        selectedLoopIndices.clear();
        for (int i = 0; i < loopLocations.size(); i++) selectedLoopIndices.add(i);
    }

    public static void deselectAllLoops() {
        selectedLoopIndices.clear();
    }

    public static boolean isLoopSelected(int index) {
        return selectedLoopIndices.contains(index);
    }

    public static Set<Integer> getSelectedLoopIndices() {
        return selectedLoopIndices;
    }

    public static List<LoopLocationClient> getSelectedLoops() {
        List<LoopLocationClient> result = new ArrayList<>();
        for (int index : selectedLoopIndices) {
            if (index >= 0 && index < loopLocations.size()) {
                result.add(loopLocations.get(index));
            }
        }

        return result;
    }

    // ========== Chunk Selection Management ==========

    public static void toggleChunkSelection(int index) {
        if (selectedChunkIndices.contains(index)) {
            selectedChunkIndices.remove(index);
        } else {
            selectedChunkIndices.add(index);
        }
    }

    public static void selectOnlyChunk(int index) {
        selectedChunkIndices.clear();
        selectedChunkIndices.add(index);
    }

    public static void selectAllChunks() {
        selectedChunkIndices.clear();
        for (int i = 0; i < chunkLocations.size(); i++) selectedChunkIndices.add(i);
    }

    public static void deselectAllChunks() {
        selectedChunkIndices.clear();
    }

    public static boolean isChunkSelected(int index) {
        return selectedChunkIndices.contains(index);
    }

    public static Set<Integer> getSelectedChunkIndices() {
        return selectedChunkIndices;
    }

    public static List<ChunkLocationClient> getSelectedChunks() {
        List<ChunkLocationClient> result = new ArrayList<>();
        for (int index : selectedChunkIndices) {
            if (index >= 0 && index < chunkLocations.size()) {
                result.add(chunkLocations.get(index));
            }
        }

        return result;
    }

    // ========== Missing Device Selection Management ==========

    public static void toggleMissingSelection(int index) {
        if (selectedMissingIndices.contains(index)) {
            selectedMissingIndices.remove(index);
        } else {
            selectedMissingIndices.add(index);
        }
    }

    public static void selectOnlyMissing(int index) {
        selectedMissingIndices.clear();
        selectedMissingIndices.add(index);
    }

    public static void selectAllMissing() {
        selectedMissingIndices.clear();
        for (int i = 0; i < missingDevices.size(); i++) selectedMissingIndices.add(i);
    }

    public static void deselectAllMissing() {
        selectedMissingIndices.clear();
    }

    public static boolean isMissingSelected(int index) {
        return selectedMissingIndices.contains(index);
    }

    public static Set<Integer> getSelectedMissingIndices() {
        return selectedMissingIndices;
    }

    public static List<MissingDeviceClient> getSelectedMissing() {
        List<MissingDeviceClient> result = new ArrayList<>();
        for (int index : selectedMissingIndices) {
            if (index >= 0 && index < missingDevices.size()) {
                result.add(missingDevices.get(index));
            }
        }

        return result;
    }

    // ========== Chokepoint Selection Management ==========

    public static void toggleChokeSelection(int index) {
        if (selectedChokeIndices.contains(index)) {
            selectedChokeIndices.remove(index);
        } else {
            selectedChokeIndices.add(index);
        }
    }

    public static void selectOnlyChoke(int index) {
        selectedChokeIndices.clear();
        selectedChokeIndices.add(index);
    }

    public static void selectAllChokes() {
        selectedChokeIndices.clear();
        for (int i = 0; i < chokeLocations.size(); i++) selectedChokeIndices.add(i);
    }

    public static void deselectAllChokes() {
        selectedChokeIndices.clear();
    }

    public static boolean isChokeSelected(int index) {
        return selectedChokeIndices.contains(index);
    }

    public static Set<Integer> getSelectedChokeIndices() {
        return selectedChokeIndices;
    }

    public static List<ChokeLocationClient> getSelectedChokes() {
        List<ChokeLocationClient> result = new ArrayList<>();
        for (int index : selectedChokeIndices) {
            if (index >= 0 && index < chokeLocations.size()) {
                result.add(chokeLocations.get(index));
            }
        }

        return result;
    }

    // ========== Generic Selection for Current Tab ==========

    public static void selectAll() {
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
        if (currentTab == Tab.LOOPS) {
            return selectedLoopIndices;
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            return selectedChunkIndices;
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            return selectedMissingIndices;
        } else {
            return selectedChokeIndices;
        }
    }

    public static int getCurrentTabItemCount() {
        if (currentTab == Tab.LOOPS) {
            return loopLocations.size();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            return chunkLocations.size();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            return missingDevices.size();
        } else {
            return chokeLocations.size();
        }
    }

    public static int getCurrentTabSelectedCount() {
        if (currentTab == Tab.LOOPS) {
            return selectedLoopIndices.size();
        } else if (currentTab == Tab.UNLOADED_CHUNKS) {
            return selectedChunkIndices.size();
        } else if (currentTab == Tab.MISSING_CHANNELS) {
            return selectedMissingIndices.size();
        } else {
            return selectedChokeIndices.size();
        }
    }

    // ========== Sorted/Grouped Access ==========

    /**
     * Get loop locations sorted by dimension (current first), then distance.
     */
    public static List<LoopLocationClient> getSortedLoopLocations() {
        if (sortedLoopLocations == null) {
            sortedLoopLocations = new ArrayList<>(loopLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                sortedLoopLocations.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return sortedLoopLocations;
    }

    /**
     * Get chunk locations sorted by dimension (current first), then distance.
     */
    public static List<ChunkLocationClient> getSortedChunkLocations() {
        if (sortedChunkLocations == null) {
            sortedChunkLocations = new ArrayList<>(chunkLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                sortedChunkLocations.sort((a, b) -> {
                    boolean aCurrentDim = a.dimension == playerDim;
                    boolean bCurrentDim = b.dimension == playerDim;
                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;
                    if (a.dimension != b.dimension) return Integer.compare(a.dimension, b.dimension);

                    return Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos));
                });
            }
        }

        return sortedChunkLocations;
    }

    /**
     * Get missing device locations sorted by dimension (current first), then by display name.
     */
    public static List<MissingDeviceClient> getSortedMissingDevices() {
        if (sortedMissingDevices == null) {
            sortedMissingDevices = new ArrayList<>(missingDevices);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                sortedMissingDevices.sort((a, b) -> {
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

        return sortedMissingDevices;
    }

    /**
     * Get chokepoint locations sorted by dimension (current first), then by excess channels (worst first).
     */
    public static List<ChokeLocationClient> getSortedChokeLocations() {
        if (sortedChokeLocations == null) {
            sortedChokeLocations = new ArrayList<>(chokeLocations);

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player != null) {
                int playerDim = mc.player.dimension;
                BlockPos playerPos = mc.player.getPosition();

                sortedChokeLocations.sort((a, b) -> {
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

        return sortedChokeLocations;
    }

    /**
     * Invalidate sorted cache (call when player moves significantly).
     */
    public static void invalidateSortCache() {
        sortedLoopLocations = null;
        sortedChunkLocations = null;
        sortedMissingDevices = null;
        sortedChokeLocations = null;
    }
}
