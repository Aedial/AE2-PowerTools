package com.ae2powertools.items;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.core.CreativeTab;

import com.ae2powertools.Tags;


/**
 * Speed Upgrade for the AE2 AutoCrafter.
 * 
 * Tiers I-IV provide multiplicative batch size bonuses:
 * - Tier I:   x8 batch size
 * - Tier II:  x64 batch size
 * - Tier III: x512 batch size
 * - Tier IV:  x4096 batch size
 * 
 * Speed upgrades are NOT compatible with each other (only one can be active).
 */
public class ItemCrafterSpeedUpgrade extends Item {

    /**
     * Batch multipliers for each tier (index 0 = tier I).
     */
    public static final int[] TIER_MULTIPLIERS = { 8, 64, 512, 4096 };

    /**
     * Roman numerals for tier names.
     */
    public static final String[] TIER_NAMES = { "I", "II", "III", "IV" };

    public ItemCrafterSpeedUpgrade() {
        this.setRegistryName(Tags.MODID, "crafter_speed_upgrade");
        this.setTranslationKey(Tags.MODID + ".crafter_speed_upgrade");
        this.setMaxStackSize(64);
        this.setHasSubtypes(true);
        this.setMaxDamage(0);
        this.setCreativeTab(CreativeTab.instance);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int tier = getTier(stack);
        return super.getTranslationKey() + "_" + TIER_NAMES[tier].toLowerCase();
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!this.isInCreativeTab(tab)) return;

        for (int tier = 0; tier < TIER_MULTIPLIERS.length; tier++) {
            items.add(new ItemStack(this, 1, tier));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        int tier = getTier(stack);
        int multiplier = getMultiplier(stack);

        tooltip.add(I18n.format("item.ae2powertools.crafter_speed_upgrade.tooltip", multiplier));
    }

    /**
     * Gets the tier (0-3) of an upgrade item.
     */
    public static int getTier(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemCrafterSpeedUpgrade)) return 0;

        int meta = stack.getMetadata();
        return Math.max(0, Math.min(meta, TIER_MULTIPLIERS.length - 1));
    }

    /**
     * Gets the batch multiplier for an upgrade item.
     */
    public static int getMultiplier(ItemStack stack) {
        return TIER_MULTIPLIERS[getTier(stack)];
    }

    /**
     * Gets the batch multiplier for a specific tier (0-based).
     */
    public static int getMultiplierForTier(int tier) {
        if (tier < 0 || tier >= TIER_MULTIPLIERS.length) return 1;

        return TIER_MULTIPLIERS[tier];
    }

    /**
     * Checks if the given item is a crafter speed upgrade.
     */
    public static boolean isSpeedUpgrade(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemCrafterSpeedUpgrade;
    }
}
