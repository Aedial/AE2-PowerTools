package com.ae2powertools.features.maintainer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;


/**
 * GUI handler for the Better Level Maintainer.
 */
public class GuiHandler implements IGuiHandler {

    public static final int GUI_MAINTAINER = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_MAINTAINER) return null;

        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileBetterLevelMaintainer)) return null;

        TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
        return new ContainerBetterLevelMaintainer(player.inventory, maintainer);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != GUI_MAINTAINER) return null;

        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileBetterLevelMaintainer)) return null;

        TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
        return new GuiBetterLevelMaintainer(
                new ContainerBetterLevelMaintainer(player.inventory, maintainer));
    }
}
