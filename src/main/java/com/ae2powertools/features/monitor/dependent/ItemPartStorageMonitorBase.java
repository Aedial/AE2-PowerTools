package com.ae2powertools.features.monitor.dependent;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import appeng.parts.AEBasePart;

import com.ae2powertools.Tags;
import com.ae2powertools.PowerToolsCreativeTab;


/**
 * Abstract base class for cable part items (Storage Display, Storage Level Emitter).
 *
 * @param <P> the part type this item creates
 */
public abstract class ItemPartStorageMonitorBase<P extends AEBasePart> extends Item implements IPartItem<P> {

    protected ItemPartStorageMonitorBase(String registryName) {
        setRegistryName(Tags.MODID, registryName);
        setTranslationKey(Tags.MODID + "." + registryName);
        setCreativeTab(PowerToolsCreativeTab.instance);
        setMaxStackSize(64);
    }

    /**
     * Returns the tooltip translation key for the first line.
     */
    protected abstract String getTooltipKey();

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                       EnumFacing facing, float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, facing, player, hand, world);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add(TextFormatting.AQUA + I18n.format(getTooltipKey()));
    }
}
