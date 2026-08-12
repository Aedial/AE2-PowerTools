package com.ae2powertools.client;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import com.ae2powertools.features.monitor.dependent.DisplayLogic;
import com.ae2powertools.features.monitor.display.TileStorageDisplay;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Block color handler for the ME Storage Display block.
 * <p>
 * Tint index 0 returns the live corner indicator color from the tile entity's
 * {@code DisplayLogic} when a live tile is available, or the neutral idle color otherwise
 * (used by the four corner triangles in the front-face overlay).
 * <p>
 * Tint index 1 returns a fixed cream color used by the screen "backdrop" element
 * on the front face, so the display reads as a warm CRT-like surface.
 * <p>
 * Both overlays sit at the same z-depth: they are interlocking layers that match each other.
 */
@SideOnly(Side.CLIENT)
public class DisplayBlockColor implements IBlockColor, IItemColor {

    /** Opaque white = no tint. */
    private static final int NO_TINT = 0xFFFFFFFF;

    /** Backdrop tint applied to the screen center on the front face (ARGB). */
    private static final int CENTER = 0xFFFFFFFF;  // 0xFFE9DBA9;

    public static int getCenterTint() {
        return CENTER;
    }

    @Override
    public int colorMultiplier(@Nonnull IBlockState state, @Nullable IBlockAccess world,
            @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex == 1) return CENTER;
        if (tintIndex != 0) return NO_TINT;

        return getCornerTint(world, pos);
    }

    @Override
    public int colorMultiplier(@Nonnull ItemStack stack, int tintIndex) {
        if (tintIndex == 1) return CENTER;
        if (tintIndex != 0) return NO_TINT;

        return DisplayLogic.getIdleCornerColor();
    }

    private static int getCornerTint(@Nullable IBlockAccess world, @Nullable BlockPos pos) {
        if (world == null || pos == null) return DisplayLogic.getIdleCornerColor();

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileStorageDisplay)) return DisplayLogic.getIdleCornerColor();

        return ((TileStorageDisplay) te).getDisplayLogic().getCornerColor();
    }
}
