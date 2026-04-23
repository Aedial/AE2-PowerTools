package com.ae2powertools.features.locator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.capabilities.Capabilities;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileInterface;

import com.ae2powertools.util.ItemStackKey;


/**
 * Scans an AE2 network and groups all components by their item representation.
 * This is the server-side scanning logic for the Network Component Locator.
 *
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

        // Optionally scan subnets connected via Storage Bus -> Interface (ME PassThrough)
        if (includeSubnets) {
            Set<IGrid> visitedGrids = new HashSet<>();
            visitedGrids.add(grid);
            totalNodes += scanSubnets(grid, typeMap, visitedGrids);
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

    /**
     * Detect subnets connected via Storage Bus -> Interface (ME PassThrough) and scan them.
     * Uses the same pattern as AE2SubnetScanner: iterate PartStorageBus machines on the grid,
     * check if the target tile has STORAGE_MONITORABLE_ACCESSOR capability (indicating a subnet
     * connection), then extract the remote grid from the target (TileInterface or PartInterface).
     *
     * @param mainGrid     The main grid to find subnet connections from
     * @param typeMap      Shared type map to merge results into
     * @param visitedGrids Set of already-scanned grids to prevent infinite recursion
     * @return Number of nodes scanned across all subnets
     */
    private static int scanSubnets(IGrid mainGrid, Map<ItemStackKey, ComponentType> typeMap, Set<IGrid> visitedGrids) {
        int totalNodes = 0;

        for (IGridNode node : mainGrid.getMachines(PartStorageBus.class)) {
            if (!node.isActive()) continue;

            PartStorageBus bus = (PartStorageBus) node.getMachine();
            TileEntity hostTile = bus.getHost().getTile();
            if (hostTile == null) continue;

            EnumFacing facing = bus.getSide().getFacing();
            if (facing == null) continue;

            BlockPos targetPos = hostTile.getPos().offset(facing);
            World world = hostTile.getWorld();
            TileEntity targetTile = world.getTileEntity(targetPos);
            if (targetTile == null) continue;

            // Check if target has STORAGE_MONITORABLE_ACCESSOR capability (indicates subnet connection)
            EnumFacing targetSide = facing.getOpposite();
            if (!targetTile.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide)) continue;

            // Extract the remote grid from the target tile (Interface)
            IGrid remoteGrid = getGridFromTile(targetTile);
            if (remoteGrid == null || visitedGrids.contains(remoteGrid)) continue;

            // Mark as visited and scan the subnet
            visitedGrids.add(remoteGrid);
            totalNodes += scanGrid(remoteGrid, typeMap);

            // Recursively scan sub-subnets
            totalNodes += scanSubnets(remoteGrid, typeMap, visitedGrids);
        }

        return totalNodes;
    }

    /**
     * Extract the ME grid from a tile entity.
     * Handles both TileInterface (full block) and PartInterface (cable part).
     */
    private static IGrid getGridFromTile(TileEntity tile) {
        if (tile == null) return null;

        // Handle full-block TileInterface
        if (tile instanceof TileInterface) {
            IGridNode node = ((TileInterface) tile).getGridNode(AEPartLocation.INTERNAL);
            if (node != null && node.getGrid() != null) return node.getGrid();
        }

        // Handle PartInterface on cable buses
        if (tile instanceof IPartHost) {
            IPartHost host = (IPartHost) tile;

            for (AEPartLocation loc : AEPartLocation.values()) {
                IPart part = host.getPart(loc);

                if (part instanceof PartInterface) {
                    IGridNode node = part.getGridNode();
                    if (node != null && node.getGrid() != null) return node.getGrid();
                }
            }
        }

        return null;
    }
}
