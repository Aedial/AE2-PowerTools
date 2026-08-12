package com.ae2powertools.features.monitor.emitter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.item.ItemStack;
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

    /**
     * Block property for the state (off/on).
     * This determines which model variant to render.
     */
    public static final PropertyBool STATE = PropertyBool.create("state");

    public BlockStorageLevelEmitter() {
        super(NAME);

        setDefaultState(blockState.getBaseState().withProperty(STATE, true));
    }

    @Override
    @Nonnull
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, STATE);
    }

    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        // State is stored in TileEntity, not in metadata
        return 0;
    }

    @Override
    @Nonnull
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    @Nonnull
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileStorageLevelEmitter) {
            boolean isOn = ((TileStorageLevelEmitter) te).isOn();
            return state.withProperty(STATE, isOn);
        }

        return state;
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
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new TileStorageLevelEmitter();
    }

    // --- Redstone output ---

    @Override
    public boolean canProvidePower(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    public int getWeakPower(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileStorageLevelEmitter) {
            return ((TileStorageLevelEmitter) te).getWeakRedstoneSignal();
        }

        return 0;
    }

    @Override
    public int getStrongPower(@Nonnull IBlockState state, IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileStorageLevelEmitter) {
            return ((TileStorageLevelEmitter) te).getStrongRedstoneSignal();
        }

        return 0;
    }

    @Override
    public void breakBlock(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        TileEntity te = world.getTileEntity(pos);

        // Drop any installed upgrade cards when the block is broken
        if (te instanceof IEmitterCardHost) {
            for (ItemStack stack : ((IEmitterCardHost) te).getUpgradeInventory()) {
                if (!stack.isEmpty()) spawnAsEntity(world, pos, stack.copy());
            }
        }

        super.breakBlock(world, pos, state);
    }
}
