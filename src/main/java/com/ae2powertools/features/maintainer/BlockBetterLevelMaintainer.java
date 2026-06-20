package com.ae2powertools.features.maintainer;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.storage.data.IAEItemStack;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.PowerToolsCreativeTab;
import com.ae2powertools.Tags;
import com.ae2powertools.features.GuiHandler;


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
        setCreativeTab(PowerToolsCreativeTab.instance);
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
        ItemStack heldItem = player.getHeldItem(hand);
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof IMemoryCard) {
            if (world.isRemote) return true;

            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileBetterLevelMaintainer)) return false;

            handleMemoryCard(player, heldItem, (IMemoryCard) heldItem.getItem(), (TileBetterLevelMaintainer) te);
            return true;
        }

        if (world.isRemote) return true;

        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileBetterLevelMaintainer) {
            player.openGui(AE2PowerTools.instance, GuiHandler.GUI_MAINTAINER, world,
                    pos.getX(), pos.getY(), pos.getZ());
            return true;
        }

        return false;
    }

    private void handleMemoryCard(EntityPlayer player, ItemStack memCardStack, IMemoryCard memoryCard, TileBetterLevelMaintainer maintainer) {
        if (player.isSneaking()) {
            saveToMemoryCard(player, memCardStack, memoryCard, maintainer);
            return;
        }

        loadFromMemoryCard(player, memCardStack, memoryCard, maintainer);
    }

    private void saveToMemoryCard(EntityPlayer player, ItemStack memCardStack, IMemoryCard memoryCard, TileBetterLevelMaintainer maintainer) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("openRows", maintainer.getOpenRows());
        data.setString("tooltip", "tile.ae2powertools.better_level_maintainer.memory_card.tooltip");

        NBTTagList entriesList = new NBTTagList();
        int maxEntries = maintainer.getOpenRows() * TileBetterLevelMaintainer.ENTRIES_PER_ROW;
        for (int i = 0; i < maxEntries; i++) {
            MaintainerEntry entry = maintainer.getEntry(i);
            entriesList.appendTag(entry != null ? entry.writeToNBT() : new NBTTagCompound());
        }

        data.setTag("entries", entriesList);

        memoryCard.setMemoryCardContents(memCardStack, getTranslationKey(), data);
        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
    }

    private void loadFromMemoryCard(EntityPlayer player, ItemStack memCardStack, IMemoryCard memoryCard, TileBetterLevelMaintainer maintainer) {
        String savedName = memoryCard.getSettingsName(memCardStack);
        if (!getTranslationKey().equals(savedName)) {
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return;
        }

        NBTTagCompound data = memoryCard.getData(memCardStack);
        NBTTagList entriesList = data.getTagList("entries", Constants.NBT.TAG_COMPOUND);
        int maxEntries = Math.max(entriesList.tagCount(), maintainer.getOpenRows() * TileBetterLevelMaintainer.ENTRIES_PER_ROW);

        for (int i = 0; i < maxEntries; i++) {
            if (i >= entriesList.tagCount()) {
                maintainer.clearEntry(i);
                continue;
            }

            MaintainerEntry entry = new MaintainerEntry();
            entry.readFromNBT(entriesList.getCompoundTagAt(i));
            if (!entry.hasRecipe()) {
                maintainer.clearEntry(i);
                continue;
            }

            IAEItemStack target = entry.getTargetItem();
            maintainer.setEntry(i, target != null ? target.copy() : null, entry.getTargetQuantity(), entry.getBatchSize(), entry.getFrequencySeconds());
            if (!entry.isEnabled()) {
                MaintainerEntry loadedEntry = maintainer.getEntry(i);
                if (loadedEntry != null && loadedEntry.isEnabled()) {
                    maintainer.toggleEntryEnabled(i);
                }
            }
        }

        memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
    }
}
