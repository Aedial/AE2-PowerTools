package com.ae2powertools.features.locator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.DimensionalCoord;

import com.ae2powertools.util.ItemStackKey;
import com.ae2powertools.util.SubnetGridHelper;


/**
 * Scans an AE2 network and groups all components by their item representation.
 * This is the server-side scanning logic for the Network Component Locator.
 * <p>
 * Iterates all machine classes on the grid, groups nodes by their getMachineRepresentation() ItemStack,
 * and collects all coordinates for each component type.
 */
public class ComponentScanner {

    /**
     * A single component location on the network.
     */
    public static class ComponentLocation {
        public final BlockPos pos;
        public final int dimension;

        public ComponentLocation(BlockPos pos, int dimension) {
            this.pos = pos;
            this.dimension = dimension;
        }
    }

    /**
     * A component type with its item representation and all locations.
     */
    public static class ComponentType {
        public final ItemStack itemStack;
        public final List<ComponentLocation> locations;

        public ComponentType(ItemStack itemStack) {
            this.itemStack = itemStack.copy();
            this.itemStack.setCount(1);
            this.locations = new ArrayList<>();
        }

        public void addLocation(BlockPos pos, int dimension) {
            locations.add(new ComponentLocation(pos, dimension));
        }
    }

    /**
     * Result of a network component scan.
     */
    public static class ScanResult {
        public final List<ComponentType> componentTypes;
        public final int totalNodes;

        public ScanResult(List<ComponentType> componentTypes, int totalNodes) {
            this.componentTypes = componentTypes;
            this.totalNodes = totalNodes;
        }
    }

    /**
     * Scan the entire network and group components by type.
     *
     * @param grid          The AE2 grid to scan
     * @param player        The player performing the scan (for reference position)
     * @param includeSubnets Whether to also scan subnets connected via ME PassThrough
     * @return ScanResult containing all component types and their locations
     */
    public static ScanResult scan(IGrid grid, EntityPlayer player, boolean includeSubnets) {
        Map<ItemStackKey, ComponentType> typeMap = new HashMap<>();
        int totalNodes = 0;

        // Scan the main grid
        totalNodes += scanGrid(grid, typeMap);

        // Optionally scan each connected subnet grid independently and merge the component totals.
        if (includeSubnets) {
            Set<IGrid> connectedGrids = new HashSet<>(SubnetGridHelper.collectConnectedGrids(grid));

            for (IGrid connectedGrid : connectedGrids) totalNodes += scanGrid(connectedGrid, typeMap);
        }

        // Sort component types by count (descending), then by name
        List<ComponentType> sorted = new ArrayList<>(typeMap.values());
        sorted.sort((a, b) -> {
            int countCmp = Integer.compare(b.locations.size(), a.locations.size());
            if (countCmp != 0) return countCmp;

            return a.itemStack.getDisplayName().compareToIgnoreCase(b.itemStack.getDisplayName());
        });

        return new ScanResult(sorted, totalNodes);
    }

    /**
     * Scan a single grid and populate the type map.
     *
     * @return Number of nodes scanned
     */
    private static int scanGrid(IGrid grid, Map<ItemStackKey, ComponentType> typeMap) {
        int totalNodes = 0;

        for (Class<? extends IGridHost> machineClass : grid.getMachinesClasses()) {
            for (IGridNode node : grid.getMachines(machineClass)) {
                totalNodes++;
                IGridBlock blk = node.getGridBlock();
                ItemStack representation = blk.getMachineRepresentation();
                if (representation.isEmpty()) continue;

                DimensionalCoord coord = blk.getLocation();
                ItemStackKey key = ItemStackKey.of(representation);
                if (key == null) continue;

                ComponentType type = typeMap.computeIfAbsent(key, k -> new ComponentType(representation));
                type.addLocation(coord.getPos(), coord.getWorld().provider.getDimension());
            }
        }

        return totalNodes;
    }

}
