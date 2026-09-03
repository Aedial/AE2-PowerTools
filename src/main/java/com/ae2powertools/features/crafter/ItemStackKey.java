package com.ae2powertools.features.crafter;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;


/**
 * Reusable key for ItemStack identity in maps.
 */
final class ItemStackKey {

    private final IAEItemStack item;
    private final boolean durabilityResource;
    private final boolean ignoresDamage;
    private final int durabilityPerCraft;
    private final int hash;

    ItemStackKey(IAEItemStack item) {
        this(item, false, false, 0);
    }

    ItemStackKey(IAEItemStack item, boolean durabilityResource, boolean ignoresDamage,
                 int durabilityPerCraft) {
        this.item = item;
        this.durabilityResource = durabilityResource;
        this.ignoresDamage = ignoresDamage;
        this.durabilityPerCraft = durabilityResource ? Math.max(1, durabilityPerCraft) : 0;

        // Compute the hash once up front so hot-path map lookups do not keep cloning stacks.
        ItemStack stack = item.createItemStack();
        int stackHash = stack.getItem().hashCode();
        if (!ignoresDamage) stackHash ^= stack.getMetadata() * 31;
        if (stack.hasTagCompound()) stackHash ^= stack.getTagCompound().hashCode();
        this.hash = stackHash ^ (durabilityResource ? 1 : 0) ^ (this.durabilityPerCraft * 31);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemStackKey)) return false;

        ItemStackKey other = (ItemStackKey) obj;
        if (durabilityResource != other.durabilityResource || ignoresDamage != other.ignoresDamage
                || durabilityPerCraft != other.durabilityPerCraft) return false;

        ItemStack stack = item.createItemStack();
        ItemStack otherStack = other.item.createItemStack();
        if (stack.getItem() != otherStack.getItem()) return false;
        if (!ignoresDamage && stack.getMetadata() != otherStack.getMetadata()) return false;

        return ItemStack.areItemStackTagsEqual(stack, otherStack);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    boolean isDurabilityResource() {
        return durabilityResource;
    }
}