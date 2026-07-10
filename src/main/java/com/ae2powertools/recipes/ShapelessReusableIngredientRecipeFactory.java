package com.ae2powertools.recipes;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IRecipeFactory;
import net.minecraftforge.common.crafting.JsonContext;


public class ShapelessReusableIngredientRecipeFactory implements IRecipeFactory {

    @Override
    public IRecipe parse(JsonContext context, JsonObject json) {
        String group = JsonUtils.getString(json, "group", "");

        NonNullList<Ingredient> ingredients = NonNullList.create();
        NonNullList<Ingredient> reusableIngredients = NonNullList.create();
        List<Integer> reusableIngredientIndices = new ArrayList<>();

        int ingredientIndex = 0;
        for (JsonElement element : JsonUtils.getJsonArray(json, "ingredients")) {
            Ingredient ingredient = CraftingHelper.getIngredient(element, context);
            ingredients.add(ingredient);

            if (element.isJsonObject() && JsonUtils.getBoolean(element.getAsJsonObject(), "non_consume", false)) {
                reusableIngredients.add(ingredient);
                reusableIngredientIndices.add(ingredientIndex);
            }

            ingredientIndex++;
        }

        if (ingredients.isEmpty()) throw new JsonParseException("No ingredients for shapeless recipe");

        ItemStack result = CraftingHelper.getItemStack(JsonUtils.getJsonObject(json, "result"), context);
        ResourceLocation groupName = group.isEmpty() ? null : new ResourceLocation(group);
        return new ShapelessReusableIngredientRecipe(groupName, ingredients, result,
            reusableIngredients, reusableIngredientIndices);
    }
}