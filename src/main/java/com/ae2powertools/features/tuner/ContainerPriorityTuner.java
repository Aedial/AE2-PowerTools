package com.ae2powertools.features.tuner;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import com.ae2powertools.items.ItemPriorityTuner;


/**
 * Container for the Priority Tuner GUI.
 * Allows the player to set the stored priority value.
 */
public class ContainerPriorityTuner extends Container {

    private final EnumHand hand;
    private final ItemStack tunerStack;

    public ContainerPriorityTuner(InventoryPlayer playerInventory, EnumHand hand) {
        this.hand = hand;
        this.tunerStack = playerInventory.player.getHeldItem(hand);
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        ItemStack currentStack = player.getHeldItem(hand);

        return !currentStack.isEmpty() && currentStack.getItem() instanceof ItemPriorityTuner;
    }

    /**
     * Get the priority stored in the tuner.
     */
    public int getPriority() {
        if (tunerStack.getItem() instanceof ItemPriorityTuner) {
            return ((ItemPriorityTuner) tunerStack.getItem()).getStoredPriority(tunerStack);
        }

        return 0;
    }

    /**
     * Set the priority stored in the tuner.
     */
    public void setPriority(int priority) {
        if (tunerStack.getItem() instanceof ItemPriorityTuner) {
            ((ItemPriorityTuner) tunerStack.getItem()).setStoredPriority(tunerStack, priority);
        }
    }

    public EnumHand getHand() {
        return hand;
    }
}
