package com.ae2powertools.util.upgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotDisabled;


/**
 * Shared helpers for picker-driven upgrade inventories.
 */
public final class UpgradeInventoryUtil {

    public enum SlotLayout {
        HORIZONTAL,
        VERTICAL
    }

    private static final int UPGRADE_ICON = 13 * 16 + 15;
    private static final int SLOT_SPACING = 18;

    private UpgradeInventoryUtil() {}

    public static List<AppEngSlot> createPassiveUpgradeSlots(
            IItemHandler inventory,
            int startX,
            int startY,
            int slotCount,
            SlotLayout layout) {
        if (inventory == null || slotCount <= 0) return Collections.emptyList();

        List<AppEngSlot> slots = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            int x = startX + (layout == SlotLayout.HORIZONTAL ? slot * SLOT_SPACING : 0);
            int y = startY + (layout == SlotLayout.VERTICAL ? slot * SLOT_SPACING : 0);
            slots.add(new PassiveUpgradeSlot(inventory, slot, x, y));
        }

        return Collections.unmodifiableList(slots);
    }

    public static boolean installFromPlayerInventory(
            ISelectableUpgradeInventory inventory,
            EntityPlayer player,
            int upgradeSlot,
            int playerSlot) {
        if (inventory == null || player == null) return false;
        if (upgradeSlot < 0 || upgradeSlot >= inventory.getSlots()) return false;
        if (playerSlot < 0 || playerSlot >= player.inventory.mainInventory.size()) return false;

        ItemStack playerStack = player.inventory.mainInventory.get(playerSlot);
        if (playerStack.isEmpty() || !inventory.canInstallUpgrade(upgradeSlot, playerStack)) return false;

        ItemStack singleCard = playerStack.copy();
        singleCard.setCount(1);

        ItemStack installed = inventory.getStackInSlot(upgradeSlot);
        if (!installed.isEmpty() && areItemStacksEqual(installed, singleCard)) return false;

        ItemStack removed = inventory.extractItem(upgradeSlot, 1, false);
        ItemStack remainder = inventory.insertItem(upgradeSlot, singleCard, false);
        if (!remainder.isEmpty()) {
            if (!removed.isEmpty()) inventory.insertItem(upgradeSlot, removed, false);

            return false;
        }

        playerStack.shrink(1);
        if (playerStack.getCount() <= 0) {
            player.inventory.mainInventory.set(playerSlot, ItemStack.EMPTY);
        }

        if (!removed.isEmpty()) giveToPlayerOrDrop(player, removed);

        player.inventory.markDirty();

        return true;
    }

    public static boolean removeToPlayerInventory(ISelectableUpgradeInventory inventory, EntityPlayer player, int upgradeSlot) {
        if (inventory == null || player == null) return false;
        if (upgradeSlot < 0 || upgradeSlot >= inventory.getSlots()) return false;

        ItemStack removed = inventory.extractItem(upgradeSlot, 1, false);
        if (removed.isEmpty()) return false;

        giveToPlayerOrDrop(player, removed);
        player.inventory.markDirty();

        return true;
    }

    private static void giveToPlayerOrDrop(EntityPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;

        // the item is voided if the inventory is full in creative mode,
        // so we need to drop it instead of trying to add it to the inventory
        if (player.capabilities.isCreativeMode && !hasRoomFor(player.inventory, stack)) {
            player.dropItem(stack, false);
            return;
        }

        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    private static boolean hasRoomFor(InventoryPlayer inventory, ItemStack stack) {
        for (ItemStack invStack : inventory.mainInventory) {
            if (invStack.isEmpty()) return true;
            if (!areItemStacksEqual(invStack, stack)) continue;

            int maxSize = Math.min(invStack.getMaxStackSize(), inventory.getInventoryStackLimit());
            if (invStack.getCount() < maxSize) return true;
        }

        return false;
    }

    private static boolean areItemStacksEqual(ItemStack left, ItemStack right) {
        return ItemStack.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static class PassiveUpgradeSlot extends SlotDisabled {

        private PassiveUpgradeSlot(IItemHandler inventory, int slotIndex, int x, int y) {
            super(inventory, slotIndex, x, y);
            setIIcon(UPGRADE_ICON);
            setNotDraggable();
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}