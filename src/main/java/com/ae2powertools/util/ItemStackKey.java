package com.ae2powertools.util;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;


/**
 * Lightweight, immutable key wrapper for hashing/comparing ItemStacks by
 * item, metadata (damage), and NBT. Useful for maps/sets where full
 * ItemStack semantics are desired but stack counts are irrelevant.
 *
 * Based on DiskTerminal's ItemStackKey, duplicated here to avoid a compile dependency.
 */
public final class ItemStackKey {

    private final Item item;
    private final int meta;
    @Nullable
    private final NBTTagCompound nbt;

    // Cached hash code for performance (ItemStackKey is immutable)
    private Integer cachedHashCode = null;

    private ItemStackKey(final Item item, final int meta, @Nullable final NBTTagCompound nbt) {
        this.item = Objects.requireNonNull(item, "item");
        this.meta = meta;
        this.nbt = (nbt == null) ? null : nbt.copy();
    }

    /**
     * Create a key from an ItemStack. Returns null for null/empty stacks.
     */
    @Nullable
    public static ItemStackKey of(@Nullable final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        final Item it = stack.getItem();
        final int m = stack.getItemDamage();
        final NBTTagCompound tag = (stack.hasTagCompound()) ? stack.getTagCompound() : null;

        return new ItemStackKey(it, m, tag);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStackKey)) return false;

        final ItemStackKey that = (ItemStackKey) o;
        return this.meta == that.meta && this.item == that.item && Objects.equals(this.nbt, that.nbt);
    }

    @Override
    public int hashCode() {
        if (this.cachedHashCode != null) return this.cachedHashCode;

        int hash = this.item.hashCode() * 31 + this.meta;
        if (this.nbt != null) hash = hash * 31 + this.nbt.hashCode();
        this.cachedHashCode = hash;

        return this.cachedHashCode;
    }

    @Override
    public String toString() {
        return "ItemStackKey{item=" + this.item.getRegistryName() + "@" + this.meta +
               (this.nbt == null ? "null" : "|" + this.nbt) + "}";
    }
}
