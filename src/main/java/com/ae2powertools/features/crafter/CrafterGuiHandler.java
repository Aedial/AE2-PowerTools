package com.ae2powertools.features.crafter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;


/**
 * GUI handler for the AutoCrafter GUIs.
 */
public class CrafterGuiHandler implements IGuiHandler {

    public static final int GUI_CRAFTER = 100;
    public static final int GUI_CRAFTER_BATCH = 101;
    public static final int GUI_CRAFTER_SPEED = 102;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);

        if (!(te instanceof TileAutoCrafter)) return null;

        TileAutoCrafter crafter = (TileAutoCrafter) te;

        switch (id) {
            case GUI_CRAFTER:
                // Force sync to client when GUI opens to ensure fresh data
                crafter.markForUpdate();
                return new ContainerAutoCrafter(player.inventory, crafter);

            case GUI_CRAFTER_BATCH:
                return new ContainerCrafterBatch(player.inventory, crafter);

            case GUI_CRAFTER_SPEED:
                return new ContainerCrafterSpeed(player.inventory, crafter);
        }

        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);

        if (!(te instanceof TileAutoCrafter)) return null;

        TileAutoCrafter crafter = (TileAutoCrafter) te;

        switch (id) {
            case GUI_CRAFTER:
                return new GuiAutoCrafter(new ContainerAutoCrafter(player.inventory, crafter));

            case GUI_CRAFTER_BATCH:
                return new GuiCrafterBatch(new ContainerCrafterBatch(player.inventory, crafter));

            case GUI_CRAFTER_SPEED:
                return new GuiCrafterSpeed(new ContainerCrafterSpeed(player.inventory, crafter));
        }

        return null;
    }
}
