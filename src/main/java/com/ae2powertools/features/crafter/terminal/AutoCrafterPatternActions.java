package com.ae2powertools.features.crafter.terminal;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;

import com.ae2powertools.features.crafter.CrafterEntry;
import com.ae2powertools.features.crafter.TileAutoCrafter;


/**
 * Shared AutoCrafter pattern contract used by both the dedicated GUI and terminal integrations.
 */
public final class AutoCrafterPatternActions {

    private AutoCrafterPatternActions() {}

    /**
     * Returns true if the given stack is a valid crafting pattern for the given AutoCrafter.
     */
    public static boolean isValidPattern(TileAutoCrafter tile, ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (!(stack.getItem() instanceof ICraftingPatternItem)) return false;

        ICraftingPatternItem patternItem = (ICraftingPatternItem) stack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(stack, tile.getWorld());
        return details != null && details.isCraftable();
    }

    /**
     * Apply a pattern change through the tile's canonical mutation path.
     * <p>
     * {@link TileAutoCrafter#simulatePattern(int, ItemStack)} is the authoritative write path for
     * pattern changes because it both stores the stack and rebuilds the cached recipe analysis.
     */
    public static void setPattern(TileAutoCrafter tile, int entryIndex, @Nullable ItemStack patternStack) {
        if (patternStack != null && patternStack.isEmpty()) patternStack = null;

        tile.simulatePattern(entryIndex, patternStack);
        tile.markDirty();
    }

    /**
     * Ejects all catalysts from the given AutoCrafter row to the given player.
     */
    public static void ejectCatalystsToPlayer(TileAutoCrafter tile, int entryIndex, EntityPlayer player) {
        CrafterEntry entry = tile.getEntry(entryIndex);
        if (entry == null) return;

        for (int catalystIndex = 0; catalystIndex < CrafterEntry.CATALYST_SLOTS; catalystIndex++) {
            ItemStack stack = entry.getCatalystStack(catalystIndex);
            if (stack.isEmpty()) continue;

            ItemStack ejected = stack.copy();
            entry.setCatalystStack(catalystIndex, ItemStack.EMPTY);

            if (!player.inventory.addItemStackToInventory(ejected)) {
                player.dropItem(ejected, false);
            }
        }

        tile.validateCatalysts(entryIndex);
    }
}