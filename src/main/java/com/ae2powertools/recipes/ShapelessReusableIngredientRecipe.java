package com.ae2powertools.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.oredict.ShapelessOreRecipe;


public class ShapelessReusableIngredientRecipe extends ShapelessOreRecipe {

    private final NonNullList<Ingredient> reusableIngredients;
    private final List<Integer> reusableIngredientIndices;

    public ShapelessReusableIngredientRecipe(ResourceLocation group, NonNullList<Ingredient> input,
                                            @Nonnull ItemStack result,
                                            NonNullList<Ingredient> reusableIngredients,
                                            List<Integer> reusableIngredientIndices) {
        super(group, input, result);
        this.reusableIngredients = NonNullList.create();
        this.reusableIngredients.addAll(reusableIngredients);
        this.reusableIngredientIndices = new ArrayList<>(reusableIngredientIndices);
    }

    public List<Integer> getReusableIngredientIndices() {
        return Collections.unmodifiableList(reusableIngredientIndices);
    }

    public List<Ingredient> getReusableIngredients() {
        return Collections.unmodifiableList(reusableIngredients);
    }

    public boolean usesIngredient(ItemStack stack) {
        if (stack.isEmpty()) return false;

        for (Ingredient ingredient : getIngredients()) {
            if (ingredient.apply(stack)) return true;
        }

        return false;
    }

    @Override
    @Nonnull
    public NonNullList<ItemStack> getRemainingItems(@Nonnull InventoryCrafting inv) {
        NonNullList<ItemStack> remaining = ForgeHooks.defaultRecipeGetRemainingItems(inv);
        if (reusableIngredients.isEmpty()) return remaining;

        List<Integer> claimedSlots = new ArrayList<>();

        // Shapeless recipes can place the reusable ingredient in any occupied slot.
        // After the normal remaining-items pass, restore the original ingredient in
        // the first unclaimed matching slot for each reusable entry.
        for (Ingredient reusableIngredient : reusableIngredients) {
            int slot = findReusableSlot(inv, reusableIngredient, claimedSlots);
            if (slot < 0) continue;

            claimedSlots.add(slot);

            if (!remaining.get(slot).isEmpty()) continue;

            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            ItemStack survivor = stack.copy();
            survivor.setCount(1);
            remaining.set(slot, survivor);
        }

        return remaining;
    }

    @Override
    public boolean isDynamic() {
        // JEI renders this recipe through a custom wrapper so it can mark reusable
        // ingredients in recipe GUIs without duplicating the default crafting entry.
        return true;
    }

    private int findReusableSlot(InventoryCrafting inv, Ingredient reusableIngredient, List<Integer> claimedSlots) {
        for (int slot = 0; slot < inv.getSizeInventory(); slot++) {
            if (claimedSlots.contains(slot)) continue;

            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (reusableIngredient.apply(stack)) return slot;
        }

        return -1;
    }
}