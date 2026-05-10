package com.ae2powertools.features.monitor.dependent;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;


/**
 * Centralized resolver for {@link IStorageMonitorHost} instances from a (world, pos, side)
 * triple, so every monitor packet handler and the GUI handler share the same lookup logic.
 * <p>
 * Block tiles are addressed with {@link AEPartLocation#INTERNAL} (no side); cable parts are
 * addressed with the actual side they're attached to. This mirrors how AE2 itself
 * distinguishes between a tile and a part on a cable bus.
 * <p>
 * Returns {@code null} if no host of the expected kind exists at the location: callers are
 * expected to ignore the packet rather than crash, since stale packets can still arrive
 * after the host is removed (chunk unload, broken cable, etc).
 */
public final class StorageMonitorHostResolver {

    private StorageMonitorHostResolver() {}

    @Nullable
    public static IStorageMonitorHost resolve(World world, BlockPos pos, AEPartLocation side) {
        if (world == null || pos == null) return null;

        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) return null;

        // INTERNAL means "block tile host". Anything else means "look the part up on this side".
        if (side == null || side == AEPartLocation.INTERNAL) {
            return tile instanceof IStorageMonitorHost ? (IStorageMonitorHost) tile : null;
        }

        if (!(tile instanceof IPartHost)) return null;

        IPart part = ((IPartHost) tile).getPart(side);
        return part instanceof IStorageMonitorHost ? (IStorageMonitorHost) part : null;
    }

    /**
     * Convenience overload taking a side ordinal. -1 maps to {@link AEPartLocation#INTERNAL}.
     * Used by network packets that serialize the side as a byte to keep wire format compact.
     */
    @Nullable
    public static IStorageMonitorHost resolve(World world, BlockPos pos, int sideOrdinal) {
        AEPartLocation side = sideOrdinal < 0 ? AEPartLocation.INTERNAL : AEPartLocation.fromOrdinal(sideOrdinal);
        return resolve(world, pos, side);
    }

    /**
     * Encodes the side as a byte for wire transport: INTERNAL -> -1, others -> ordinal (0..5).
     * Pairs with {@link #resolve(World, BlockPos, int)}.
     */
    public static byte encodeSide(AEPartLocation side) {
        if (side == null || side == AEPartLocation.INTERNAL) return -1;
        return (byte) side.ordinal();
    }
}
