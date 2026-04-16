package com.ae2powertools;

import javax.annotation.Nonnull;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;


/**
 * Creative tab for the AE2 PowerTools mod.
 * Uses the Network Health Scanner as the tab icon.
 */
public final class PowerToolsCreativeTab extends CreativeTabs {

    public static PowerToolsCreativeTab instance = null;

    public PowerToolsCreativeTab() {
        super(Tags.MODID);
    }

    public static void init() {
        instance = new PowerToolsCreativeTab();
    }

    @Override
    @Nonnull
    public ItemStack getIcon() {
        return this.createIcon();
    }

    @Override
    @Nonnull
    public ItemStack createIcon() {
        try {
            if (ItemRegistry.NETWORK_HEALTH_SCANNER != null) {
                return new ItemStack(ItemRegistry.NETWORK_HEALTH_SCANNER);
            }
        } catch (Throwable t) {
            // fall through to fallback
        }

        return new ItemStack(Items.REDSTONE);
    }
}
