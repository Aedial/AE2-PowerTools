package com.ae2powertools.features.maintainer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import com.ae2powertools.features.crafter.ContainerAutoCrafter;
import com.ae2powertools.features.crafter.ContainerCrafterBatch;
import com.ae2powertools.features.crafter.ContainerCrafterSpeed;
import com.ae2powertools.features.crafter.CrafterGuiHandler;
import com.ae2powertools.features.crafter.GuiAutoCrafter;
import com.ae2powertools.features.crafter.GuiCrafterBatch;
import com.ae2powertools.features.crafter.GuiCrafterSpeed;
import com.ae2powertools.features.crafter.TileAutoCrafter;


/**
 * GUI handler for AE2 PowerTools blocks.
 */
public class GuiHandler implements IGuiHandler {

    public static final int GUI_MAINTAINER = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);

        // Maintainer GUI
        if (id == GUI_MAINTAINER) {
            if (!(te instanceof TileBetterLevelMaintainer)) return null;

            TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
            return new ContainerBetterLevelMaintainer(player.inventory, maintainer);
        }

        // AutoCrafter GUIs
        if (te instanceof TileAutoCrafter) {
            TileAutoCrafter crafter = (TileAutoCrafter) te;

            switch (id) {
                case CrafterGuiHandler.GUI_CRAFTER:
                    return new ContainerAutoCrafter(player.inventory, crafter);
                case CrafterGuiHandler.GUI_CRAFTER_BATCH:
                    return new ContainerCrafterBatch(player.inventory, crafter);
                case CrafterGuiHandler.GUI_CRAFTER_SPEED:
                    return new ContainerCrafterSpeed(player.inventory, crafter);
            }
        }

        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity te = world.getTileEntity(pos);

        // Maintainer GUI
        if (id == GUI_MAINTAINER) {
            if (!(te instanceof TileBetterLevelMaintainer)) return null;

            TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
            return new GuiBetterLevelMaintainer(
                    new ContainerBetterLevelMaintainer(player.inventory, maintainer));
        }

        // AutoCrafter GUIs
        if (te instanceof TileAutoCrafter) {
            TileAutoCrafter crafter = (TileAutoCrafter) te;

            switch (id) {
                case CrafterGuiHandler.GUI_CRAFTER:
                    return new GuiAutoCrafter(new ContainerAutoCrafter(player.inventory, crafter));
                case CrafterGuiHandler.GUI_CRAFTER_BATCH:
                    return new GuiCrafterBatch(new ContainerCrafterBatch(player.inventory, crafter));
                case CrafterGuiHandler.GUI_CRAFTER_SPEED:
                    return new GuiCrafterSpeed(new ContainerCrafterSpeed(player.inventory, crafter));
            }
        }

        return null;
    }
}
