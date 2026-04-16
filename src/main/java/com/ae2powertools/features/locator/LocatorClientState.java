package com.ae2powertools.features.locator;

import java.util.ArrayList;
import java.util.Collections;
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
 * Client-side state for the Network Component Locator.
 * Stores scanned component types and their locations, plus selection state for overlay rendering.
 * State is indexed by device ID to support multiple locator devices.
 */
@SideOnly(Side.CLIENT)
public class LocatorClientState {

    /**
     * Client-side component location data.
     */
    public static class ComponentLocationClient {
        public final BlockPos pos;
        public final int dimension;

        public ComponentLocationClient(BlockPos pos, int dimension) {
            this.pos = pos;
            this.dimension = dimension;
        }

        public double getDistanceFrom(BlockPos from) {
            double dx = pos.getX() - from.getX();
            double dy = pos.getY() - from.getY();
            double dz = pos.getZ() - from.getZ();

            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /**
         * Format position as "[dim / x, y, z]".
         */
        public String getCoordString() {
            return String.format("[%d / %d, %d, %d]", dimension, pos.getX(), pos.getY(), pos.getZ());
        }

        /**
         * Format position as "[x, y, z]" (without dimension).
         */
        public String getCoordStringNoDim() {
            return String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * Client-side component type data with its item representation and all locations.
     */
    public static class ComponentTypeClient {
        public final ItemStack itemStack;
        public final List<ComponentLocationClient> locations;

        public ComponentTypeClient(ItemStack itemStack, List<ComponentLocationClient> locations) {
            this.itemStack = itemStack != null ? itemStack.copy() : ItemStack.EMPTY;
            this.locations = locations;
        }

        public String getDisplayName() {
            return itemStack.isEmpty() ? "Unknown" : itemStack.getDisplayName();
        }

        public int getCount() {
            return locations.size();
        }
    }

    /**
     * Unique key for a selected location across all component types.
     * Uses typeIndex + locationIndex to uniquely identify a location.
     */
    public static class SelectionKey {
        public final int typeIndex;
        public final int locationIndex;

        public SelectionKey(int typeIndex, int locationIndex) {
            this.typeIndex = typeIndex;
            this.locationIndex = locationIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SelectionKey)) return false;

            SelectionKey that = (SelectionKey) o;
            return typeIndex == that.typeIndex && locationIndex == that.locationIndex;
        }

        @Override
        public int hashCode() {
            return 31 * typeIndex + locationIndex;
        }
    }

    /**
     * Per-device locator state.
     */
    public static class DeviceState {
        private List<ComponentTypeClient> componentTypes = new ArrayList<>();
        private int totalNodes = 0;
        private boolean subnetScanEnabled = false;

        // Index of the currently viewed component type (-1 = grid view, showing all types)
        private int selectedTypeIndex = -1;

        // Scroll offset for the detail list
        private int detailScrollOffset = 0;

        // Selected locations across ALL component types (typeIndex, locationIndex pairs)
        private final Set<SelectionKey> selectedLocations = new HashSet<>();

        public void clearData() {
            componentTypes.clear();
            totalNodes = 0;
            selectedTypeIndex = -1;
            detailScrollOffset = 0;
            selectedLocations.clear();
        }
    }

    // Active device ID
    private static long activeDeviceId = 0L;

    // Per-device state map
    private static final Map<Long, DeviceState> deviceStates = new HashMap<>();

    // ========== Device ID Management ==========

    public static long getActiveDeviceId() {
        return activeDeviceId;
    }

    public static void setActiveDeviceId(long deviceId) {
        activeDeviceId = deviceId;
    }

    private static DeviceState getOrCreateState(long deviceId) {
        return deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());
    }

    private static DeviceState getActiveState() {
        return deviceStates.get(activeDeviceId);
    }

    public static boolean hasData() {
        DeviceState state = getActiveState();

        return state != null && !state.componentTypes.isEmpty();
    }

    // ========== Data Management ==========

    /**
     * Update the scan data for a given device.
     */
    public static void updateData(long deviceId, List<ComponentTypeClient> types, int totalNodes, boolean subnetScanEnabled) {
        DeviceState state = getOrCreateState(deviceId);
        state.componentTypes = types;
        state.totalNodes = totalNodes;
        state.subnetScanEnabled = subnetScanEnabled;

        // Keep selectedTypeIndex if user had one selected and it's still valid
        if (state.selectedTypeIndex >= types.size()) state.selectedTypeIndex = -1;

        state.detailScrollOffset = 0;
        state.selectedLocations.clear();
    }

    /**
     * Get all component types for the active device.
     */
    public static List<ComponentTypeClient> getComponentTypes() {
        DeviceState state = getActiveState();
        if (state == null) return Collections.emptyList();

        return state.componentTypes;
    }

    /**
     * Get total nodes scanned.
     */
    public static int getTotalNodes() {
        DeviceState state = getActiveState();

        return state != null ? state.totalNodes : 0;
    }

    /**
     * Check if subnet scanning is currently enabled for the active device.
     */
    public static boolean isSubnetScanEnabled() {
        DeviceState state = getActiveState();

        return state != null && state.subnetScanEnabled;
    }

    /**
     * Toggle the local subnet scan state (for GUI display).
     * This does NOT modify server-side NBT, use PacketLocatorToggleSubnet for that.
     */
    public static void toggleSubnetScanLocal() {
        DeviceState state = getActiveState();
        if (state != null) state.subnetScanEnabled = !state.subnetScanEnabled;
    }

    /**
     * Initialize the subnet scan state for a device before any scan data is received.
     * This ensures the GUI shows the correct state immediately when opened.
     */
    public static void initSubnetState(long deviceId, boolean subnetScanEnabled) {
        DeviceState state = getOrCreateState(deviceId);
        state.subnetScanEnabled = subnetScanEnabled;
    }

    // ========== Grid View (Component Selection) ==========

    /**
     * Get the currently selected component type index.
     * -1 means we're in the grid overview, not viewing a specific type.
     */
    public static int getSelectedTypeIndex() {
        DeviceState state = getActiveState();

        return state != null ? state.selectedTypeIndex : -1;
    }

    /**
     * Set the selected component type. Pass -1 to go back to grid view.
     * Note: This does NOT clear selections, allowing users to select from multiple types.
     */
    public static void setSelectedTypeIndex(int index) {
        DeviceState state = getActiveState();
        if (state == null) return;

        state.selectedTypeIndex = index;
        state.detailScrollOffset = 0;
    }

    /**
     * Get the currently selected component type, or null if in grid view.
     */
    public static ComponentTypeClient getSelectedType() {
        DeviceState state = getActiveState();
        if (state == null || state.selectedTypeIndex < 0
                || state.selectedTypeIndex >= state.componentTypes.size()) {

            return null;
        }

        return state.componentTypes.get(state.selectedTypeIndex);
    }

    /**
     * Check if we are in the detail view (a component type is selected).
     */
    public static boolean isInDetailView() {
        return getSelectedTypeIndex() >= 0;
    }

    /**
     * Navigate back to the grid view from the detail view, but keep selections.
     * This allows users to select components from multiple types and see them on the overlay.
     */
    public static void backToGridKeepSelections() {
        DeviceState state = getActiveState();
        if (state == null) return;

        state.selectedTypeIndex = -1;
    }

    // ========== Detail View (Location List) ==========

    public static int getDetailScrollOffset() {
        DeviceState state = getActiveState();

        return state != null ? state.detailScrollOffset : 0;
    }

    public static void setDetailScrollOffset(int offset) {
        DeviceState state = getActiveState();
        if (state != null) state.detailScrollOffset = Math.max(0, offset);
    }

    /**
     * Toggle selection of a location by its index in the current type's locations list.
     */
    public static void toggleLocationSelection(int locationIndex) {
        DeviceState state = getActiveState();
        if (state == null || state.selectedTypeIndex < 0) return;

        SelectionKey key = new SelectionKey(state.selectedTypeIndex, locationIndex);
        if (state.selectedLocations.contains(key)) {
            state.selectedLocations.remove(key);
        } else {
            state.selectedLocations.add(key);
        }
    }

    /**
     * Check if a location index is selected in the current type.
     */
    public static boolean isLocationSelected(int locationIndex) {
        DeviceState state = getActiveState();
        if (state == null || state.selectedTypeIndex < 0) return false;

        SelectionKey key = new SelectionKey(state.selectedTypeIndex, locationIndex);
        return state.selectedLocations.contains(key);
    }

    /**
     * Get count of selected locations for a specific component type index.
     * Used by the grid view to show how many items of each type are selected.
     */
    public static int getSelectedCountForTypeIndex(int typeIndex) {
        DeviceState state = getActiveState();
        if (state == null) return 0;

        int count = 0;
        for (SelectionKey key : state.selectedLocations) {
            if (key.typeIndex == typeIndex) count++;
        }

        return count;
    }

    public static int getSelectedCountForType(ComponentTypeClient type) {
        DeviceState state = getActiveState();
        if (state == null || type == null) return 0;

        int typeIndex = state.componentTypes.indexOf(type);
        if (typeIndex < 0) return 0;

        return getSelectedCountForTypeIndex(typeIndex);
    }

    /**
     * Get all selected locations across ALL component types.
     * Used by the overlay renderer to draw highlights.
     */
    public static List<ComponentLocationClient> getSelectedLocations() {
        DeviceState state = getActiveState();
        if (state == null) return Collections.emptyList();

        List<ComponentLocationClient> selected = new ArrayList<>();

        for (SelectionKey key : state.selectedLocations) {
            if (key.typeIndex >= 0 && key.typeIndex < state.componentTypes.size()) {
                ComponentTypeClient type = state.componentTypes.get(key.typeIndex);
                if (key.locationIndex >= 0 && key.locationIndex < type.locations.size()) {
                    selected.add(type.locations.get(key.locationIndex));
                }
            }
        }

        return selected;
    }

    /**
     * A selected location paired with its component type name for overlay display.
     */
    public static class SelectedLocationWithType {
        public final ComponentLocationClient location;
        public final String typeName;

        public SelectedLocationWithType(ComponentLocationClient location, String typeName) {
            this.location = location;
            this.typeName = typeName;
        }
    }

    /**
     * Get all selected locations with their type names.
     * Used by the overlay renderer to show which component type each location belongs to.
     */
    public static List<SelectedLocationWithType> getSelectedLocationsWithTypes() {
        DeviceState state = getActiveState();
        if (state == null) return Collections.emptyList();

        List<SelectedLocationWithType> result = new ArrayList<>();

        for (SelectionKey key : state.selectedLocations) {
            if (key.typeIndex >= 0 && key.typeIndex < state.componentTypes.size()) {
                ComponentTypeClient type = state.componentTypes.get(key.typeIndex);
                if (key.locationIndex >= 0 && key.locationIndex < type.locations.size()) {
                    result.add(new SelectedLocationWithType(
                        type.locations.get(key.locationIndex),
                        type.getDisplayName()
                    ));
                }
            }
        }

        return result;
    }

    /**
     * Get locations sorted by distance from the player.
     */
    public static List<ComponentLocationClient> getSortedLocations(ComponentTypeClient type) {
        if (type == null) return Collections.emptyList();

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return type.locations;

        BlockPos playerPos = mc.player.getPosition();

        List<ComponentLocationClient> sorted = new ArrayList<>(type.locations);
        sorted.sort((a, b) -> Double.compare(a.getDistanceFrom(playerPos), b.getDistanceFrom(playerPos)));

        return sorted;
    }

    /**
     * Select all locations in the current detail view (current type only).
     */
    public static void selectAll() {
        DeviceState state = getActiveState();
        if (state == null || state.selectedTypeIndex < 0) return;

        ComponentTypeClient type = state.componentTypes.get(state.selectedTypeIndex);
        for (int i = 0; i < type.locations.size(); i++) {
            state.selectedLocations.add(new SelectionKey(state.selectedTypeIndex, i));
        }
    }

    /**
     * Deselect all locations in the current detail view (current type only).
     */
    public static void deselectAll() {
        DeviceState state = getActiveState();
        if (state == null || state.selectedTypeIndex < 0) return;

        // Only remove selections for the current type
        state.selectedLocations.removeIf(key -> key.typeIndex == state.selectedTypeIndex);
    }
}
