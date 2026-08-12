package com.ae2powertools.items;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.PowerToolsCreativeTab;
import com.ae2powertools.Tags;


/**
 * Simple companion item that enables directional arrows to active alarm positions while held.
 */
public class ItemLevelMonitorAlarmLocator extends Item {

    public ItemLevelMonitorAlarmLocator() {
        setRegistryName(Tags.MODID, "level_monitor_alarm_locator");
        setTranslationKey(Tags.MODID + ".level_monitor_alarm_locator");
        setMaxStackSize(1);
        setCreativeTab(PowerToolsCreativeTab.instance);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
            @Nonnull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);

        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2powertools.level_monitor_alarm_locator.tip1"));
    }
}