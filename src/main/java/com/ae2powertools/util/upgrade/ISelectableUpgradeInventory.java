package com.ae2powertools.util.upgrade;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


/**
 * Upgrade inventories that want to drive the shared click-to-pick GUI expose the
 * supported card types and a localization prefix through this interface.
 */
public interface ISelectableUpgradeInventory extends IItemHandler {

    List<UpgradeCardDefinition> getSupportedUpgradeCards();

    String getUpgradeTooltipPrefix();

    default UpgradeCardDefinition findUpgradeDefinition(ItemStack stack) {
        if (stack.isEmpty()) return null;

        for (UpgradeCardDefinition definition : getSupportedUpgradeCards()) {
            if (definition.matches(stack)) return definition;
        }

        return null;
    }

    default boolean canInstallUpgrade(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getSlots() || stack.isEmpty()) return false;

        UpgradeCardDefinition definition = findUpgradeDefinition(stack);
        if (definition == null) return false;

        ItemStack current = getStackInSlot(slot);
        if (!current.isEmpty() && definition.matches(current)) return true;

        return isItemValid(slot, stack);
    }
}