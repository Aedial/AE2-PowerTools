package com.ae2powertools.features.monitor.alarm;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.ae2powertools.features.monitor.dependent.BlockStorageMonitorBase;


/**
 * Alarm variant of the storage monitor. Reuses the emitter's on/off model structure.
 */
public class BlockLevelMonitorAlarm extends BlockStorageMonitorBase {

    public static final String NAME = "level_monitor_alarm";
    public static final PropertyBool STATE = PropertyBool.create("state");

    public BlockLevelMonitorAlarm() {
        super(NAME);
        setDefaultState(blockState.getBaseState().withProperty(STATE, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, STATE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileLevelMonitorAlarm) {
            return state.withProperty(STATE, ((TileLevelMonitorAlarm) te).isOn());
        }

        return state;
    }

    @Override
    protected String getTooltipKey() {
        return "tile.ae2powertools.level_monitor_alarm.tooltip";
    }

    @Override
    protected List<String> getAdditionalTooltipKeys() {
        return Collections.singletonList("tile.ae2powertools.level_monitor_alarm.tooltip2");
    }

    @Override
    protected Class<? extends TileEntity> getTileClass() {
        return TileLevelMonitorAlarm.class;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileLevelMonitorAlarm();
    }
}