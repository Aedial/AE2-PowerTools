package com.ae2powertools.features.monitor.emitter;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.ae2powertools.features.monitor.dependent.BlockStorageMonitorBase;


/**
 * ME Storage Level Emitter block.
 * Emits redstone on all 6 faces based on monitored resource quantity vs threshold.
 * Connects to the AE2 network for grid-managed ticking (no channels required).
 */
public class BlockStorageLevelEmitter extends BlockStorageMonitorBase {

    public static final String NAME = "storage_level_emitter";

    public BlockStorageLevelEmitter() {
        super(NAME);
    }

    @Override
    protected String getTooltipKey() {
        return "tile.ae2powertools.storage_level_emitter.tooltip";
    }

    @Override
    protected Class<? extends TileEntity> getTileClass() {
        return TileStorageLevelEmitter.class;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileStorageLevelEmitter();
    }

    // --- Redstone output ---

    @Override
    public boolean canProvidePower(IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileStorageLevelEmitter) {
            return ((TileStorageLevelEmitter) te).getWeakRedstoneSignal();
        }

        return 0;
    }

    @Override
    public int getStrongPower(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileStorageLevelEmitter) {
            return ((TileStorageLevelEmitter) te).getStrongRedstoneSignal();
        }

        return 0;
    }
}
