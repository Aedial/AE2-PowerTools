package com.ae2powertools.integration.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import mezz.jei.api.gui.ICraftingGridHelper;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.wrapper.ICustomCraftingRecipeWrapper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import com.ae2powertools.recipes.ShapelessReusableIngredientRecipe;


public class ReusableCraftingRecipeWrapper implements ICustomCraftingRecipeWrapper {

    private final ICraftingGridHelper craftingGridHelper;
    private final ShapelessReusableIngredientRecipe recipe;

    public ReusableCraftingRecipeWrapper(ICraftingGridHelper craftingGridHelper, ShapelessReusableIngredientRecipe recipe) {
        this.craftingGridHelper = craftingGridHelper;
        this.recipe = recipe;
    }

    public ShapelessReusableIngredientRecipe getRecipe() {
        return recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputs = new ArrayList<>();

        for (Ingredient ingredient : recipe.getIngredients()) {
            inputs.add(Arrays.asList(ingredient.getMatchingStacks()));
        }

        ingredients.setInputLists(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, recipe.getRecipeOutput());
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, IIngredients ingredients) {
        recipeLayout.setShapeless();

        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        List<List<ItemStack>> inputs = ingredients.getInputs(VanillaTypes.ITEM);
        List<List<ItemStack>> outputs = ingredients.getOutputs(VanillaTypes.ITEM);

        craftingGridHelper.setInputs(itemStacks, inputs);
        itemStacks.set(0, outputs.get(0));

        Set<Integer> reusableGuiSlots = mapReusableGuiSlots(itemStacks.getGuiIngredients());
        itemStacks.addTooltipCallback((slotIndex, input, ingredient, tooltip) -> {
            if (!input || ingredient == null || ingredient.isEmpty()) return;
            if (!reusableGuiSlots.contains(slotIndex)) return;

            tooltip.add("");
            tooltip.add(TextFormatting.GREEN + I18n.format("jei.ae2powertools.reusable"));
        });
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName() {
        return recipe.getRegistryName();
    }

    private Set<Integer> mapReusableGuiSlots(Map<Integer, ? extends IGuiIngredient<ItemStack>> guiIngredients) {
        List<Integer> orderedInputSlots = guiIngredients.entrySet().stream()
            .filter(entry -> entry.getValue().isInput())
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());

        Set<Integer> reusableSlots = new HashSet<>();
        for (int ingredientIndex : recipe.getReusableIngredientIndices()) {
            if (ingredientIndex < 0 || ingredientIndex >= orderedInputSlots.size()) continue;
            reusableSlots.add(orderedInputSlots.get(ingredientIndex));
        }

        return reusableSlots;
    }
}