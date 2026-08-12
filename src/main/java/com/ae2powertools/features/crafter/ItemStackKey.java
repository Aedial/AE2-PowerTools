package com.ae2powertools.features.crafter;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;


/**
 * Reusable key for ItemStack identity in maps.
 */
final class ItemStackKey {

    private final IAEItemStack item;
    private final int hash;

    ItemStackKey(IAEItemStack item) {
        this.item = item;

        // Compute the hash once up front so hot-path map lookups do not keep cloning stacks.
        ItemStack stack = item.createItemStack();
        this.hash = stack.getItem().hashCode() ^ (stack.getMetadata() * 31);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemStackKey)) return false;

        ItemStackKey other = (ItemStackKey) obj;
        return item.isSameType(other.item);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}