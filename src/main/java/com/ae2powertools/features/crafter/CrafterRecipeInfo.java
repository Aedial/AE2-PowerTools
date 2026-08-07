package com.ae2powertools.features.crafter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Holds the cached analysis of a crafting recipe.
 * Identifies which ingredients are reusable, consume durability, self-duplicate, or are consumed.
 * <p>
 * This analysis is performed once when a pattern is inserted, and cached to avoid
 * expensive recipe simulation on every craft operation.
 */
public class CrafterRecipeInfo {

    /**
     * Represents a single ingredient and its behavior in the recipe.
     */
    public static class IngredientInfo {
        private final IAEItemStack item;
        private final int slotIndex;
        private final IngredientType type;
        private final int requiredCount;
        @Nullable
        private final ItemStackKey itemKey;
        
        /**
         * For TRANSFORMED items: the item that this ingredient becomes after crafting.
         * For example, a filled bucket becomes an empty bucket.
         * Null for CONSUMED, REUSABLE, DURABILITY, and DUPLICATION types.
         */
        @Nullable
        private final IAEItemStack remainingItem;

        /**
         * For DURABILITY items: how much durability is consumed per craft.
         * Detected during recipe simulation by comparing input/output damage values.
         * Default is 1 if not detected.
         */
        private final int durabilityPerCraft;

        public IngredientInfo(IAEItemStack item, int slotIndex, IngredientType type, int requiredCount) {
            this(item, slotIndex, type, requiredCount, null, 1);
        }

        public IngredientInfo(IAEItemStack item, int slotIndex, IngredientType type, int requiredCount, 
                              @Nullable IAEItemStack remainingItem) {
            this(item, slotIndex, type, requiredCount, remainingItem, 1);
        }

        public IngredientInfo(IAEItemStack item, int slotIndex, IngredientType type, int requiredCount, 
                              @Nullable IAEItemStack remainingItem, int durabilityPerCraft) {
            this.item = item;
            this.slotIndex = slotIndex;
            this.type = type;
            this.requiredCount = requiredCount;
            this.itemKey = item != null ? new ItemStackKey(item) : null;
            this.remainingItem = remainingItem;
            this.durabilityPerCraft = Math.max(1, durabilityPerCraft);
        }

        public IAEItemStack getItem() {
            return item;
        }

        public int getSlotIndex() {
            return slotIndex;
        }

        public IngredientType getType() {
            return type;
        }

        public int getRequiredCount() {
            return requiredCount;
        }

        @Nullable
        public ItemStackKey getItemKey() {
            return itemKey;
        }

        /**
         * Gets the item that this ingredient transforms into after crafting.
         * Only applicable for TRANSFORMED type ingredients.
         * @return The remaining item, or null if not applicable
         */
        @Nullable
        public IAEItemStack getRemainingItem() {
            return remainingItem;
        }

        /**
         * Gets the durability consumed per craft for DURABILITY type items.
         * @return Durability consumed per craft (always >= 1)
         */
        public int getDurabilityPerCraft() {
            return durabilityPerCraft;
        }

        public NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            if (item != null) tag.setTag("item", item.createItemStack().writeToNBT(new NBTTagCompound()));
            tag.setInteger("slot", slotIndex);
            tag.setInteger("type", type.ordinal());
            tag.setInteger("count", requiredCount);
            tag.setInteger("durability", durabilityPerCraft);

            if (remainingItem != null) {
                tag.setTag("remaining", remainingItem.createItemStack().writeToNBT(new NBTTagCompound()));
            }

            return tag;
        }

        public static IngredientInfo readFromNBT(NBTTagCompound tag) {
            IAEItemStack item = null;
            if (tag.hasKey("item")) {
                item = AEItemStack.fromItemStack(new ItemStack(tag.getCompoundTag("item")));
            }
            int slot = tag.getInteger("slot");
            IngredientType type = IngredientType.values()[tag.getInteger("type")];
            int count = tag.getInteger("count");
            int durability = tag.hasKey("durability") ? tag.getInteger("durability") : 1;

            IAEItemStack remaining = null;
            if (tag.hasKey("remaining")) {
                remaining = AEItemStack.fromItemStack(new ItemStack(tag.getCompoundTag("remaining")));
            }

            return new IngredientInfo(item, slot, type, count, remaining, durability);
        }
    }

    /**
     * Type of ingredient behavior in a crafting recipe.
     * <p>
     * Determined by simulating the recipe once and checking what items remain in the crafting grid.
     */
    public enum IngredientType {
        /**
         * Fully consumed in the crafting process (most common case).
         * The item disappears after crafting with no remainder.
         */
        CONSUMED,

        /**
         * Returned completely unchanged after crafting (e.g., Blood Magic Orbs).
         * These items are stored in internal inventory and reused indefinitely.
         * No network request is ever needed for these items and their absence
         * in the internal inventory is treated as a missing catalyst.
         */
        REUSABLE,

        /**
         * Takes durability damage but is returned (e.g., tools in some recipes).
         * NOT stored in internal inventory - requested from network like CONSUMED items.
         * The damaged item is returned as an additional output to the network.
         * <p>
         * For batch crafting, we calculate how many items are needed based on:
         * - Total durability per item
         * - Number of crafts requested
         * - Expected items to break (requested as additional input)
         * <p>
         * Items that survive crafting (with remaining durability) are returned to the network.
         */
        DURABILITY,

        /**
         * Transformed into a different item after crafting (e.g., filled bucket -> empty bucket).
         * The transformed item is treated as an additional output and sent to the network.
         * Unlike REUSABLE, these items ARE consumed and produce a different item.
         */
        TRANSFORMED,

        /**
         * Self-duplicating: the output contains more of the input item than was consumed.
         * Example: a recipe that takes 1 seed but outputs 2 seeds.
         * Requires X items in slots, net consumption = 0 (or negative), but X must be present.
         * The excess is returned to the network as additional output.
         */
        DUPLICATION
    }

    private final List<IngredientInfo> ingredients;
    private final List<IAEItemStack> outputs;
    private final List<IngredientInfo> catalystSlots; // Items that need to be in internal inventory
    private final List<IngredientInfo> consumedItems;
    private final List<IngredientInfo> transformedItems;
    private final boolean[] catalystSlotFlags;
    private final boolean valid;
    private final String errorKey; // Localization key for error message

    /**
     * Creates an invalid recipe info with an error.
     */
    public CrafterRecipeInfo(String errorKey) {
        this.ingredients = new ArrayList<>();
        this.outputs = new ArrayList<>();
        this.catalystSlots = new ArrayList<>();
        this.consumedItems = Collections.emptyList();
        this.transformedItems = Collections.emptyList();
        this.catalystSlotFlags = new boolean[CrafterEntry.CATALYST_SLOTS];
        this.valid = false;
        this.errorKey = errorKey;
    }

    /**
     * Creates a valid recipe info.
     */
    public CrafterRecipeInfo(List<IngredientInfo> ingredients, List<IAEItemStack> outputs) {
        this.ingredients = new ArrayList<>(ingredients);
        this.outputs = new ArrayList<>(outputs);
        this.catalystSlots = new ArrayList<>();
        List<IngredientInfo> consumedItems = new ArrayList<>();
        List<IngredientInfo> transformedItems = new ArrayList<>();
        boolean[] catalystSlotFlags = new boolean[CrafterEntry.CATALYST_SLOTS];
        this.valid = true;
        this.errorKey = null;

        // Identify catalyst slots:
        // - REUSABLE: stored in internal inventory, never consumed
        // - DUPLICATION: item appears in output but must be present in internal inventory (e.g., seeds that duplicate)
        // Note: DURABILITY items are NOT catalysts - they are requested from network and returned when damaged
        for (IngredientInfo info : ingredients) {
            if (info.type == IngredientType.CONSUMED
                    || info.type == IngredientType.TRANSFORMED
                    || info.type == IngredientType.DURABILITY) {
                consumedItems.add(info);
            }

            if (info.type == IngredientType.TRANSFORMED || info.type == IngredientType.DUPLICATION) {
                transformedItems.add(info);
            }

            if (info.type == IngredientType.REUSABLE || info.type == IngredientType.DUPLICATION) {
                catalystSlots.add(info);

                if (info.slotIndex >= 0 && info.slotIndex < catalystSlotFlags.length) {
                    catalystSlotFlags[info.slotIndex] = true;
                }
            }
        }

        this.consumedItems = Collections.unmodifiableList(consumedItems);
        this.transformedItems = Collections.unmodifiableList(transformedItems);
        this.catalystSlotFlags = catalystSlotFlags;
    }

    public List<IngredientInfo> getIngredients() {
        return ingredients;
    }

    public List<IAEItemStack> getOutputs() {
        return outputs;
    }

    public List<IngredientInfo> getCatalystSlots() {
        return catalystSlots;
    }

    public boolean isValid() {
        return valid;
    }

    public String getErrorKey() {
        return errorKey;
    }

    /**
     * Checks if this recipe requires catalyst items in the internal inventory.
     */
    public boolean requiresCatalysts() {
        return !catalystSlots.isEmpty();
    }

    public boolean isCatalystSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < catalystSlotFlags.length && catalystSlotFlags[slotIndex];
    }

    /**
     * Gets items that need to be extracted from the network for each craft.
     * Does NOT include REUSABLE items (in internal inventory, never consumed).
     * Does NOT include DUPLICATION items (in internal inventory as catalysts).
     * <p>
     * DURABILITY items ARE consumed from the network - they are extracted, used,
     * and returned damaged. They are not catalysts stored in internal inventory.
     * <p>
     * TRANSFORMED items ARE consumed and must be extracted,
     * even though they produce different outputs. The outputs are handled separately.
     */
    public List<IngredientInfo> getConsumedItems() {
        return consumedItems;
    }

    /**
     * Gets items that produce additional outputs (beyond the main crafting result).
     * These are TRANSFORMED and DUPLICATION items whose outputs go back to the network.
     */
    public List<IngredientInfo> getTransformedItems() {
        return transformedItems;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("valid", valid);

        if (errorKey != null) tag.setString("error", errorKey);

        NBTTagList ingredientList = new NBTTagList();
        for (IngredientInfo info : ingredients) ingredientList.appendTag(info.writeToNBT());
        tag.setTag("ingredients", ingredientList);

        NBTTagList outputList = new NBTTagList();
        for (IAEItemStack output : outputs) {
            if (output != null) {
                outputList.appendTag(output.createItemStack().writeToNBT(new NBTTagCompound()));
            }
        }
        tag.setTag("outputs", outputList);

        return tag;
    }

    public static CrafterRecipeInfo readFromNBT(NBTTagCompound tag) {
        boolean valid = tag.getBoolean("valid");

        if (!valid) return new CrafterRecipeInfo(tag.getString("error"));

        List<IngredientInfo> ingredients = new ArrayList<>();
        NBTTagList ingredientList = tag.getTagList("ingredients", 10);
        for (int i = 0; i < ingredientList.tagCount(); i++) {
            ingredients.add(IngredientInfo.readFromNBT(ingredientList.getCompoundTagAt(i)));
        }

        List<IAEItemStack> outputs = new ArrayList<>();
        NBTTagList outputList = tag.getTagList("outputs", 10);
        for (int i = 0; i < outputList.tagCount(); i++) {
            IAEItemStack stack = AEItemStack.fromItemStack(new ItemStack(outputList.getCompoundTagAt(i)));
            if (stack != null) outputs.add(stack);
        }

        return new CrafterRecipeInfo(ingredients, outputs);
    }
}
