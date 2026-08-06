package com.ae2powertools.features.monitor.dependent;

import java.util.List;

import javax.annotation.Nonnull;
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
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.items.IMemoryCard;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.Tags;
import com.ae2powertools.features.GuiHandler;
import com.ae2powertools.PowerToolsCreativeTab;


/**
 * Abstract base class for all storage monitoring blocks (Storage Display, Storage Level Emitter).
 * Provides shared constructor setup, tooltip rendering, tile entity creation,
 * and GUI opening on right-click.
 */
public abstract class BlockStorageMonitorBase extends Block {

    public BlockStorageMonitorBase(String name) {
        super(Material.IRON);
        setRegistryName(Tags.MODID, name);
        setTranslationKey(Tags.MODID + "." + name);
        setCreativeTab(PowerToolsCreativeTab.instance);
        setHardness(2.0F);
        setResistance(10.0F);
    }

    /**
     * Returns the tooltip translation key for the first line
     * (e.g. "tile.ae2powertools.storage_display.tooltip").
     */
    protected abstract String getTooltipKey();

    /**
     * Returns additional tooltip keys to display after the first one, or null/empty if no additional lines.
     * The base implementation returns null, which is treated as no additional lines.
     */
    @Nullable
    protected List<String> getAdditionalTooltipKeys() {
        return null;
    }

    /**
     * Returns the expected tile entity class for this block.
     * Used by {@link #onBlockActivated} to validate the tile before opening the GUI.
     */
    protected abstract Class<? extends TileEntity> getTileClass();

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
            @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add(TextFormatting.AQUA + I18n.format(getTooltipKey()));

        List<String> additionalKeys = getAdditionalTooltipKeys();
        if (additionalKeys != null) {
            for (String key : additionalKeys) {
                tooltip.add(TextFormatting.AQUA + I18n.format(key));
            }
        }
    }

    @Override
    public boolean hasTileEntity(@Nonnull IBlockState state) {
        return true;
    }

    @Override
    public boolean onBlockActivated(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState state,
                                    EntityPlayer player, @Nonnull EnumHand hand,
                                    @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof IMemoryCard) {
            if (world.isRemote) return true;

            TileEntity te = world.getTileEntity(pos);
            if (getTileClass().isInstance(te) && te instanceof IStorageMonitorHost) {
                return MonitorMemoryCardHelper.handleMemoryCard(world, player, heldItem, (IStorageMonitorHost) te);
            }

            return false;
        }

        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (getTileClass().isInstance(te)) {
            player.openGui(AE2PowerTools.instance, GuiHandler.GUI_STORAGE_MONITOR, world,
                    pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        return false;
    }
}
