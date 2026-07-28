package com.ae2powertools.features.crafter.terminal;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.helpers.ItemHandlerUtil;

import com.ae2powertools.Tags;
import com.ae2powertools.features.crafter.CrafterEntry;
import com.ae2powertools.features.crafter.TileAutoCrafter;

import static appeng.helpers.ItemStackHelper.stackWriteToNBT;


/**
 * Server-side tracker mirroring AE2's Interface Terminal row model for a single AutoCrafter.
 */
public class AutoCrafterTerminalTracker {

    public static final int ACTIVE_SLOT_COUNT = TileAutoCrafter.ENTRY_COUNT;
    public static final int TOTAL_SLOT_COUNT = 18;
    public static final int TERMINAL_ROW_COUNT = 2;

    public static final String TAG_AUTO_CRAFTER = "ae2ptAutoCrafter";
    public static final String TAG_ACTIVE_SLOTS = "ae2ptActiveSlots";

    private static final String UNLOCALIZED_NAME = "tile." + Tags.MODID + ".auto_crafter";

    private final TileAutoCrafter tile;
    private final AppEngInternalInventory clientInventory;
    private final long id;
    private final long sortBy;
    private final BlockPos pos;
    private final int dimension;

    public AutoCrafterTerminalTracker(TileAutoCrafter tile) {
        this.tile = tile;
        this.clientInventory = new AppEngInternalInventory(null, TOTAL_SLOT_COUNT, 1);
        this.id = computeId(tile);
        this.sortBy = computeSortValue(tile.getPos());
        this.pos = tile.getPos();
        this.dimension = tile.getWorld().provider.getDimension();
    }

    public TileAutoCrafter getTile() {
        return tile;
    }

    public long getId() {
        return id;
    }

    public int getActiveSlotCount() {
        return ACTIVE_SLOT_COUNT;
    }

    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= ACTIVE_SLOT_COUNT) return ItemStack.EMPTY;

        CrafterEntry entry = tile.getEntry(slot);
        if (entry == null || !entry.hasPattern()) return ItemStack.EMPTY;

        ItemStack stack = entry.getPatternStack();
        return stack != null ? stack : ItemStack.EMPTY;
    }

    public void appendFullSync(NBTTagCompound data) {
        String name = '=' + Long.toString(id, Character.MAX_RADIX);
        NBTTagCompound tag = new NBTTagCompound();
        writeMetadata(tag);

        for (int slot = 0; slot < TOTAL_SLOT_COUNT; slot++) {
            writeSlot(tag, slot, getStack(slot));
        }

        data.setTag(name, tag);
    }

    public void appendDiffSync(NBTTagCompound data) {
        String name = '=' + Long.toString(id, Character.MAX_RADIX);
        NBTTagCompound tag = null;

        for (int slot = 0; slot < TOTAL_SLOT_COUNT; slot++) {
            ItemStack current = getStack(slot);
            ItemStack cached = clientInventory.getStackInSlot(slot);
            if (!isDifferent(current, cached)) continue;

            if (tag == null) {
                tag = new NBTTagCompound();
                writeMetadata(tag);
            }

            writeSlot(tag, slot, current);
        }

        if (tag != null) data.setTag(name, tag);
    }

    private void writeMetadata(NBTTagCompound tag) {
        tag.setLong("sortBy", sortBy);
        tag.setString("un", UNLOCALIZED_NAME);
        tag.setTag("pos", net.minecraft.nbt.NBTUtil.createPosTag(pos));
        tag.setInteger("dim", dimension);
        tag.setInteger("numUpgrades", TERMINAL_ROW_COUNT - 1);
        tag.setBoolean(TAG_AUTO_CRAFTER, true);
        tag.setInteger(TAG_ACTIVE_SLOTS, ACTIVE_SLOT_COUNT);
    }

    private void writeSlot(NBTTagCompound tag, int slot, ItemStack stack) {
        NBTTagCompound itemTag = new NBTTagCompound();
        if (!stack.isEmpty()) stackWriteToNBT(stack, itemTag);

        tag.setTag(Integer.toString(slot), itemTag);
        ItemHandlerUtil.setStackInSlot(clientInventory, slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    private static boolean isDifferent(ItemStack first, ItemStack second) {
        if (first.isEmpty() && second.isEmpty()) return false;
        if (first.isEmpty() || second.isEmpty()) return true;

        return !ItemStack.areItemStacksEqual(first, second);
    }

    private static long computeSortValue(BlockPos pos) {
        return ((long) pos.getZ() << 24) ^ ((long) pos.getX() << 8) ^ pos.getY();
    }

    private static long computeId(TileAutoCrafter tile) {
        long dimensionBits = Integer.toUnsignedLong(tile.getWorld().provider.getDimension());
        long positionBits = tile.getPos().toLong();
        return 0x6000000000000000L ^ positionBits ^ (dimensionBits << 32) ^ (dimensionBits << 7);
    }
}