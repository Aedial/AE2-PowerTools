package com.ae2powertools.features.monitor.display;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.monitor.dependent.BlockStorageMonitorBase;


/**
 * ME Storage Display block.
 * Directional block that shows configured resource icon, quantity, and color indicator
 * on its facing face via TESR rendering.
 */
public class BlockStorageDisplay extends BlockStorageMonitorBase {

    public static final String NAME = "storage_display";
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public BlockStorageDisplay() {
        super(NAME);
        setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected String getTooltipKey() {
        return "tile.ae2powertools.storage_display.tooltip";
    }

    @Override
    protected Class<? extends TileEntity> getTileClass() {
        return TileStorageDisplay.class;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@Nonnull World world, @Nonnull IBlockState state) {
        return new TileStorageDisplay();
    }

    // --- Directional block ---

    @Override
    @Nonnull
    public IBlockState getStateForPlacement(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull EnumFacing facing,
                                            float hitX, float hitY, float hitZ,
                                            int meta, @Nonnull EntityLivingBase placer) {
        return this.getDefaultState().withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer));
    }

    @Override
    public void onBlockPlacedBy(World world, @Nonnull BlockPos pos, @Nonnull IBlockState state,
                                @Nonnull EntityLivingBase placer, @Nonnull ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileStorageDisplay) {
            ((TileStorageDisplay) te).setFacing(state.getValue(FACING));
        }
    }

    @Override
    @Nonnull
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    @Nonnull
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta);
        return this.getDefaultState().withProperty(FACING, facing);
    }

    // === Render layer / opacity ===
    // The front-face overlay (corner + center elements in the model) uses textures with
    // transparent regions. CUTOUT_MIPPED honors per-pixel alpha cutoff, letting
    // the corner and center pieces correctly show through one another.

    @Override
    @Nonnull
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean isOpaqueCube(@Nonnull IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(@Nonnull IBlockState state) {
        return true;
    }
}
