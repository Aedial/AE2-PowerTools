package com.ae2powertools.util;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.capabilities.Capabilities;
import appeng.fluids.parts.PartFluidInterface;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.fluids.tile.TileFluidInterface;
import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.misc.TileInterface;

import com.cells.api.ISubnetProxy;


/**
 * Discovers cross-grid subnet links reachable from an AE2 grid.
 * The links themselves are traversed separately from any per-grid BFS so callers can
 * run a full scan independently on each discovered network.
 */
public final class SubnetGridHelper {

    private SubnetGridHelper() {}

    /**
     * Collect every distinct remote grid reachable from the root grid through known subnet links.
     * The root grid itself is excluded from the returned set.
     */
    public static Set<IGrid> collectConnectedGrids(IGrid rootGrid) {
        LinkedHashSet<IGrid> visited = new LinkedHashSet<>();
        LinkedHashSet<IGrid> discovered = new LinkedHashSet<>();
        ArrayDeque<IGrid> open = new ArrayDeque<>();

        if (rootGrid == null) return discovered;

        visited.add(rootGrid);
        open.add(rootGrid);

        while (!open.isEmpty()) {
            IGrid currentGrid = open.removeFirst();

            for (IGrid adjacentGrid : findAdjacentGrids(currentGrid)) {
                if (adjacentGrid == null || !visited.add(adjacentGrid)) continue;

                discovered.add(adjacentGrid);
                open.addLast(adjacentGrid);
            }
        }

        return discovered;
    }

    /**
     * Resolve a representative world for a grid so child scans keep sensible fallbacks.
     */
    public static World resolveWorld(IGrid grid, World fallbackWorld) {
        if (grid == null) return fallbackWorld;

        for (IGridNode node : grid.getNodes()) {
            World nodeWorld = getNodeWorld(node);
            if (nodeWorld != null) return nodeWorld;
        }

        return fallbackWorld;
    }

    private static Set<IGrid> findAdjacentGrids(IGrid grid) {
        LinkedHashSet<IGrid> adjacent = new LinkedHashSet<>();

        if (grid == null) return adjacent;

        collectOutboundStorageBusTargets(grid, adjacent);
        collectInboundInterfaceTargets(grid, adjacent);
        collectSubnetProxyTargets(grid, adjacent);

        adjacent.remove(grid);

        return adjacent;
    }

    private static void collectOutboundStorageBusTargets(IGrid grid, Set<IGrid> adjacent) {
        for (IGridNode node : grid.getMachines(PartStorageBus.class)) {
            if (!node.isActive()) continue;

            IGrid remoteGrid = getStorageBusTargetGrid((PartStorageBus) node.getMachine());
            if (remoteGrid != null && remoteGrid != grid) adjacent.add(remoteGrid);
        }

        for (IGridNode node : grid.getMachines(PartFluidStorageBus.class)) {
            if (!node.isActive()) continue;

            IGrid remoteGrid = getStorageBusTargetGrid((PartFluidStorageBus) node.getMachine());
            if (remoteGrid != null && remoteGrid != grid) adjacent.add(remoteGrid);
        }
    }

    private static void collectInboundInterfaceTargets(IGrid grid, Set<IGrid> adjacent) {
        for (IGridNode node : grid.getMachines(TileInterface.class)) {
            if (!node.isActive()) continue;

            IGridHost host = node.getMachine();
            if (!(host instanceof TileEntity)) continue;

            TileEntity ifaceTile = (TileEntity) host;
            for (EnumFacing facing : EnumFacing.values()) {
                addAdjacentStorageBusTarget(ifaceTile, facing, grid, adjacent);
            }
        }

        for (IGridNode node : grid.getMachines(TileFluidInterface.class)) {
            if (!node.isActive()) continue;

            IGridHost host = node.getMachine();
            if (!(host instanceof TileEntity)) continue;

            TileEntity ifaceTile = (TileEntity) host;
            for (EnumFacing facing : EnumFacing.values()) {
                addAdjacentStorageBusTarget(ifaceTile, facing, grid, adjacent);
            }
        }

        for (IGridNode node : grid.getMachines(PartInterface.class)) {
            if (!node.isActive()) continue;

            PartInterface iface = (PartInterface) node.getMachine();
            TileEntity ifaceTile = iface.getTileEntity();
            if (ifaceTile == null) continue;

            EnumFacing facing = iface.getSide().getFacing();
            if (facing == null) continue;

            addAdjacentStorageBusTarget(ifaceTile, facing, grid, adjacent);
        }

        for (IGridNode node : grid.getMachines(PartFluidInterface.class)) {
            if (!node.isActive()) continue;

            PartFluidInterface iface = (PartFluidInterface) node.getMachine();
            TileEntity ifaceTile = iface.getTileEntity();
            if (ifaceTile == null) continue;

            EnumFacing facing = iface.getSide().getFacing();
            if (facing == null) continue;

            addAdjacentStorageBusTarget(ifaceTile, facing, grid, adjacent);
        }
    }

    private static void collectSubnetProxyTargets(IGrid grid, Set<IGrid> adjacent) {
        for (IGridNode node : grid.getNodes()) {
            if (!node.isActive()) continue;

            IGridHost host = node.getMachine();
            if (!(host instanceof ISubnetProxy)) continue;

            Object targetGrid = ((ISubnetProxy) host).getTargetGrid();
            if (!(targetGrid instanceof IGrid)) continue;

            IGrid remoteGrid = (IGrid) targetGrid;
            if (remoteGrid != grid) adjacent.add(remoteGrid);
        }
    }

    private static void addAdjacentStorageBusTarget(TileEntity ifaceTile, EnumFacing facing, IGrid localGrid,
            Set<IGrid> adjacent) {
        if (ifaceTile == null || facing == null) return;

        TileEntity adjacentTile = ifaceTile.getWorld().getTileEntity(ifaceTile.getPos().offset(facing));
        if (adjacentTile == null) return;

        IGrid remoteGrid = getRemoteStorageBusGrid(adjacentTile, facing.getOpposite(), localGrid);
        if (remoteGrid != null) adjacent.add(remoteGrid);
    }

    private static IGrid getRemoteStorageBusGrid(TileEntity tile, EnumFacing side, IGrid localGrid) {
        if (!(tile instanceof IPartHost)) return null;

        IPartHost host = (IPartHost) tile;
        IPart part = host.getPart(AEPartLocation.fromFacing(side));

        if (part instanceof PartStorageBus) {
            IGridNode node = part.getGridNode();
            if (node != null) {
                node.getGrid();
                if (node.getGrid() != localGrid) {
                    return node.getGrid();
                }
            }
        }

        if (part instanceof PartFluidStorageBus) {
            IGridNode node = part.getGridNode();
            if (node != null) {
                node.getGrid();
                if (node.getGrid() != localGrid) {
                    return node.getGrid();
                }
            }
        }

        return null;
    }

    private static IGrid getStorageBusTargetGrid(PartStorageBus storageBus) {
        if (storageBus == null || storageBus.getHost() == null) return null;

        TileEntity hostTile = storageBus.getHost().getTile();
        EnumFacing facing = storageBus.getSide().getFacing();
        return getGridFromFacingTarget(hostTile, facing);
    }

    private static IGrid getStorageBusTargetGrid(PartFluidStorageBus storageBus) {
        if (storageBus == null || storageBus.getHost() == null) return null;

        TileEntity hostTile = storageBus.getHost().getTile();
        EnumFacing facing = storageBus.getSide().getFacing();
        return getGridFromFacingTarget(hostTile, facing);
    }

    private static IGrid getGridFromFacingTarget(TileEntity hostTile, EnumFacing facing) {
        if (hostTile == null || facing == null) return null;

        TileEntity targetTile = hostTile.getWorld().getTileEntity(hostTile.getPos().offset(facing));
        if (targetTile == null) return null;

        EnumFacing targetSide = facing.getOpposite();
        if (!targetTile.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide)) return null;

        return getGridFromTile(targetTile);
    }

    private static IGrid getGridFromTile(TileEntity tile) {
        if (tile == null) return null;

        if (tile instanceof IPartHost) {
            IPartHost host = (IPartHost) tile;

            for (AEPartLocation location : AEPartLocation.values()) {
                IPart part = host.getPart(location);
                if (part == null) continue;

                IGridNode node = part.getGridNode();
                if (node != null) {
                    node.getGrid();
                    return node.getGrid();
                }
            }
        }

        if (tile instanceof IGridHost) {
            IGridHost host = (IGridHost) tile;

            for (AEPartLocation location : AEPartLocation.values()) {
                IGridNode node = host.getGridNode(location);
                if (node != null) {
                    node.getGrid();
                    return node.getGrid();
                }
            }
        }

        return null;
    }

    private static World getNodeWorld(IGridNode node) {
        if (node == null) return null;

        IGridHost host = node.getMachine();
        if (host instanceof TileEntity) return ((TileEntity) host).getWorld();

        try {
            DimensionalCoord coord = node.getGridBlock().getLocation();
            return coord.getWorld();
        } catch (Exception e) {
            return null;
        }
    }
}