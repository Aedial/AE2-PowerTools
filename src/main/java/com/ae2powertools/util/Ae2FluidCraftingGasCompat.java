package com.ae2powertools.util;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;


/**
 * Gas-only AE2FC compat is isolated here so the base maintainer can load safely
 * when Mekanism Energistics is not installed.
 */
public final class Ae2FluidCraftingGasCompat {

    private static final String MOD_ID = "ae2fc";
    private static final String GAS_MOD_ID = "mekeng";
    private static final ResourceLocation GAS_DROP_ID = new ResourceLocation(MOD_ID, "gas_drop");
    private static final ResourceLocation GAS_PACKET_ID = new ResourceLocation(MOD_ID, "gas_packet");

    private Ae2FluidCraftingGasCompat() {
    }

    @Optional.Method(modid = GAS_MOD_ID)
    @Nullable
    public static IAEItemStack canonicalize(@Nullable IAEItemStack stack) {
        if (stack == null || !isGasPacket(stack)) return stack;

        GasStack gas = readPacketGas(stack);
        if (gas == null) return stack;

        IAEItemStack dropStack = createDropStack(gas);
        if (dropStack != null) {
            dropStack.setCraftable(stack.isCraftable());
            dropStack.setCountRequestable(stack.getCountRequestable());
        }

        return dropStack != null ? dropStack : stack;
    }

    @Optional.Method(modid = GAS_MOD_ID)
    public static ItemStack getDisplayStack(@Nullable IAEItemStack stack) {
        if (stack == null) return ItemStack.EMPTY;

        GasStack gas = extractGas(stack);
        if (gas == null) return stack.createItemStack();

        ItemStack displayStack = createDisplayPacketStack(gas);
        return displayStack.isEmpty() ? stack.createItemStack() : displayStack;
    }

    @Optional.Method(modid = GAS_MOD_ID)
    public static boolean isGasStack(@Nullable IAEItemStack stack) {
        return stack != null && (isGasPacket(stack) || isGasDrop(stack));
    }

    @Optional.Method(modid = GAS_MOD_ID)
    public static long getStoredQuantity(IStorageGrid storageGrid, @Nullable IAEItemStack targetItem) {
        GasStack gas = extractGas(targetItem);
        if (gas == null) return 0;

        IMEMonitor<IAEGasStack> gasStorage = storageGrid.getInventory(
                AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));
        IAEGasStack gasKey = AEGasStack.of(gas);
        if (gasKey == null) return 0;

        IAEGasStack storedGas = gasStorage.getStorageList().findPrecise(gasKey);
        return storedGas != null ? storedGas.getStackSize() : 0;
    }

    @Optional.Method(modid = GAS_MOD_ID)
    @Nullable
    public static IAEItemStack injectCraftedItems(
            IStorageGrid storageGrid,
            @Nullable IAEItemStack items,
            Actionable mode,
            IActionSource actionSource) {
        if (items == null) return null;

        GasStack gas = extractGas(items);
        if (gas == null) return items;

        IMEMonitor<IAEGasStack> gasStorage = storageGrid.getInventory(
                AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));
        IAEGasStack gasStack = AEGasStack.of(gas);
        if (gasStack == null) return items;

        IAEGasStack remainder = gasStorage.injectItems(gasStack, mode, actionSource);
        return restoreOriginalForm(items, remainder != null ? remainder.getGasStack() : null);
    }

    @Optional.Method(modid = GAS_MOD_ID)
    @Nullable
    public static IAEItemStack restoreOriginalForm(@Nullable IAEItemStack original, @Nullable IAEItemStack stack) {
        if (original == null || stack == null || !isGasPacket(original) || isGasPacket(stack)) return stack;

        GasStack gas = extractGas(stack);
        if (gas == null) return stack;

        IAEItemStack restoredStack = restoreOriginalForm(original, gas);
        if (restoredStack != null) {
            restoredStack.setCraftable(stack.isCraftable());
            restoredStack.setCountRequestable(stack.getCountRequestable());
        }

        return restoredStack != null ? restoredStack : stack;
    }

    @Optional.Method(modid = GAS_MOD_ID)
    @Nullable
    public static GasStack extractGas(@Nullable IAEItemStack stack) {
        if (stack == null) return null;
        if (isGasPacket(stack)) return readPacketGas(stack);
        if (isGasDrop(stack)) return readDropGas(stack);

        return null;
    }

    @Optional.Method(modid = GAS_MOD_ID)
    @Nullable
    public static IAEItemStack restoreOriginalForm(@Nullable IAEItemStack original, @Nullable GasStack gas) {
        if (gas == null || gas.amount <= 0 || gas.getGas() == null) return null;

        return original != null && isGasPacket(original) ? createPacketStack(gas) : createDropStack(gas);
    }

    private static boolean isGasPacket(IAEItemStack stack) {
        return matchesItemId(stack.getDefinition(), GAS_PACKET_ID);
    }

    private static boolean isGasDrop(IAEItemStack stack) {
        return matchesItemId(stack.getDefinition(), GAS_DROP_ID);
    }

    private static boolean matchesItemId(ItemStack stack, ResourceLocation itemId) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        return itemId.equals(item.getRegistryName());
    }

    @Nullable
    private static GasStack readPacketGas(IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();
        if (!definition.hasTagCompound()) return null;

        NBTTagCompound tag = definition.getTagCompound();
        if (tag == null || !tag.hasKey("GasStack")) return null;

        GasStack gas = GasStack.readFromNBT(tag.getCompoundTag("GasStack"));
        return gas != null && gas.amount > 0 && gas.getGas() != null ? gas : null;
    }

    @Nullable
    private static GasStack readDropGas(IAEItemStack stack) {
        if (!isGasDrop(stack) || stack.getStackSize() <= 0 || stack.getStackSize() > Integer.MAX_VALUE) return null;

        ItemStack definition = stack.getDefinition();
        if (!definition.hasTagCompound()) return null;

        NBTTagCompound tag = definition.getTagCompound();
        if (tag == null || !tag.hasKey("Gas")) return null;

        Gas gasType = GasRegistry.getGas(tag.getString("Gas"));
        if (gasType == null) return null;

        return new GasStack(gasType, (int) stack.getStackSize());
    }

    @Nullable
    private static IAEItemStack createDropStack(GasStack gas) {
        if (gas.amount <= 0 || gas.getGas() == null) return null;

        Item dropItem = ForgeRegistries.ITEMS.getValue(GAS_DROP_ID);
        if (dropItem == null) return null;

        ItemStack dropStack = new ItemStack(dropItem, gas.amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Gas", gas.getGas().getName());
        dropStack.setTagCompound(tag);

        IAEItemStack aeDropStack = AEItemStack.fromItemStack(dropStack);
        if (aeDropStack == null) return null;

        aeDropStack.setStackSize(gas.amount);
        return aeDropStack;
    }

    @Nullable
    private static IAEItemStack createPacketStack(GasStack gas) {
        if (gas.amount <= 0 || gas.getGas() == null) return null;

        Item packetItem = ForgeRegistries.ITEMS.getValue(GAS_PACKET_ID);
        if (packetItem == null) return null;

        ItemStack packetStack = new ItemStack(packetItem);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound gasTag = new NBTTagCompound();
        gas.write(gasTag);
        tag.setTag("GasStack", gasTag);
        packetStack.setTagCompound(tag);

        return AEItemStack.fromItemStack(packetStack);
    }

    private static ItemStack createDisplayPacketStack(GasStack gas) {
        if (gas.amount <= 0 || gas.getGas() == null) return ItemStack.EMPTY;

        Item packetItem = ForgeRegistries.ITEMS.getValue(GAS_PACKET_ID);
        if (packetItem == null) return ItemStack.EMPTY;

        GasStack displayGas = gas.copy();
        displayGas.amount = 1000;

        ItemStack packetStack = new ItemStack(packetItem);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound gasTag = new NBTTagCompound();
        displayGas.write(gasTag);
        tag.setTag("GasStack", gasTag);
        tag.setBoolean("DisplayOnly", true);
        packetStack.setTagCompound(tag);
        return packetStack;
    }
}