package com.ae2powertools.features.maintainer;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.Tags;


/**
 * The Better Level Maintainer block.
 * Maintains item quantities in the AE2 network by automatically scheduling crafting jobs.
 */
public class BlockBetterLevelMaintainer extends Block {

    public static final String NAME = "better_level_maintainer";

    public BlockBetterLevelMaintainer() {
        super(Material.IRON);
        setRegistryName(Tags.MODID, NAME);
        setTranslationKey(Tags.MODID + "." + NAME);
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.REDSTONE);
        setHardness(2.0F);
        setResistance(10.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        tooltip.add(TextFormatting.AQUA + I18n.format("tile.ae2powertools.better_level_maintainer.tooltip"));
        tooltip.add(TextFormatting.YELLOW + I18n.format("tile.ae2powertools.better_level_maintainer.warning"));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileBetterLevelMaintainer();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                     EntityPlayer player, EnumHand hand,
                                     EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileBetterLevelMaintainer) {
            player.openGui(AE2PowerTools.instance, GuiHandler.GUI_MAINTAINER, world,
                    pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        return false;
    }
}
