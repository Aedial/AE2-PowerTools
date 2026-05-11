package com.ae2powertools.util;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;


/**
 * Small compatibility bridge for AE2 Fluid Crafting's packet/drop items.
 * The maintainer still uses stable lookup keys where needed, while routing crafted results
 * and stock checks through the underlying fluid or gas storage channels.
 */
public final class Ae2FluidCraftingCompat {

    private static final String MOD_ID = "ae2fc";
    private static final String GAS_MOD_ID = "mekeng";
    private static final ResourceLocation FLUID_DROP_ID = new ResourceLocation(MOD_ID, "fluid_drop");
    private static final ResourceLocation FLUID_PACKET_ID = new ResourceLocation(MOD_ID, "fluid_packet");

    private Ae2FluidCraftingCompat() {
    }

    /**
     * Converts AE2 Fluid Crafting fluid packets into the droplet form exposed to AE2.
     * Non-AE2FC stacks are returned unchanged.
     */
    @Nullable
    public static IAEItemStack canonicalize(@Nullable IAEItemStack stack) {
        if (!Loader.isModLoaded(MOD_ID) || stack == null) return stack;
        if (isFluidPacket(stack)) {
            FluidStack fluid = readPacketFluid(stack);
            if (fluid == null) return stack;

            IAEItemStack dropStack = createDropStack(fluid);
            if (dropStack != null) {
                dropStack.setCraftable(stack.isCraftable());
                dropStack.setCountRequestable(stack.getCountRequestable());
            }

            return dropStack != null ? dropStack : stack;
        }

        return isGasEnabled() ? Ae2FluidCraftingGasCompat.canonicalize(stack) : stack;
    }

    /**
     * Returns the AE2FC-native display form for fluid or gas targets.
     * Display-only packet stacks keep the GUI readable while the stored target remains in the
     * canonical drop form expected by the maintainer's crafting logic.
     */
    public static ItemStack getDisplayStack(@Nullable IAEItemStack stack) {
        if (stack == null) return ItemStack.EMPTY;
        if (!Loader.isModLoaded(MOD_ID)) return stack.createItemStack();

        FluidStack fluid = extractFluid(stack);
        if (fluid != null) {
            ItemStack displayStack = createDisplayPacketStack(fluid);
            return displayStack.isEmpty() ? stack.createItemStack() : displayStack;
        }

        return isGasEnabled() ? Ae2FluidCraftingGasCompat.getDisplayStack(stack) : stack.createItemStack();
    }

    /**
     * Converts a canonical droplet remainder back into packet form when the original caller
     * provided a fluid packet.
     */
    @Nullable
    public static IAEItemStack restoreOriginalForm(@Nullable IAEItemStack original, @Nullable IAEItemStack stack) {
        if (!Loader.isModLoaded(MOD_ID) || original == null || stack == null) return stack;
        if (isFluidPacket(original)) {
            if (isFluidPacket(stack)) return stack;

            FluidStack fluid = extractFluid(stack);
            if (fluid == null) return stack;

            IAEItemStack restoredStack = restoreOriginalForm(original, fluid);
            if (restoredStack != null) {
                restoredStack.setCraftable(stack.isCraftable());
                restoredStack.setCountRequestable(stack.getCountRequestable());
            }

            return restoredStack != null ? restoredStack : stack;
        }

        return isGasEnabled() ? Ae2FluidCraftingGasCompat.restoreOriginalForm(original, stack) : stack;
    }

    /**
     * Returns true when the AE2FC stack lives in a dedicated fluid or gas storage channel.
     */
    public static boolean usesExternalStorage(@Nullable IAEItemStack stack) {
        if (!Loader.isModLoaded(MOD_ID) || stack == null) return false;
        if (extractFluid(stack) != null) return true;

        return isGasEnabled() && Ae2FluidCraftingGasCompat.isGasStack(stack);
    }

    /**
     * Reads the maintained quantity from the dedicated AE2FC storage channel.
     * Returns {@code -1} when the stack is not an AE2FC fluid or gas target.
     */
    public static long getExternalStoredQuantity(IStorageGrid storageGrid, @Nullable IAEItemStack targetItem) {
        if (!usesExternalStorage(targetItem)) return -1;

        FluidStack fluid = extractFluid(targetItem);
        if (fluid != null) {
            IMEMonitor<IAEFluidStack> fluidStorage = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            IAEFluidStack fluidKey = AEFluidStack.fromFluidStack(fluid);
            if (fluidKey == null) return 0;

            IAEFluidStack storedFluid = fluidStorage.getStorageList().findPrecise(fluidKey);
            return storedFluid != null ? storedFluid.getStackSize() : 0;
        }

        return Ae2FluidCraftingGasCompat.getStoredQuantity(storageGrid, targetItem);
    }

    /**
     * Injects AE2FC crafted outputs into their dedicated fluid or gas storage channels and
     * converts any remainder back into the original packet/drop form.
     */
    @Nullable
    public static IAEItemStack injectIntoExternalStorage(
            IStorageGrid storageGrid,
            @Nullable IAEItemStack items,
            Actionable mode,
            IActionSource actionSource) {
        if (items == null) return null;

        FluidStack fluid = extractFluid(items);
        if (fluid != null) {
            IMEMonitor<IAEFluidStack> fluidStorage = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            IAEFluidStack fluidStack = AEFluidStack.fromFluidStack(fluid);
            if (fluidStack == null) return items;

            IAEFluidStack remainder = fluidStorage.injectItems(fluidStack, mode, actionSource);
            return restoreOriginalForm(items, remainder != null ? remainder.getFluidStack() : null);
        }

        return isGasEnabled() ? Ae2FluidCraftingGasCompat.injectCraftedItems(storageGrid, items, mode, actionSource) : items;
    }

    /**
     * Extracts the underlying fluid from either AE2FC packet or droplet form.
     */
    @Nullable
    public static FluidStack extractFluid(@Nullable IAEItemStack stack) {
        if (!Loader.isModLoaded(MOD_ID) || stack == null) return null;
        if (isFluidPacket(stack)) return readPacketFluid(stack);
        if (isFluidDrop(stack)) return readDropFluid(stack);

        return null;
    }

    /**
     * Rebuilds an AE2FC fluid item from a fluid remainder, preserving packet form when the
     * original request used packets.
     */
    @Nullable
    public static IAEItemStack restoreOriginalForm(@Nullable IAEItemStack original, @Nullable FluidStack fluid) {
        if (!Loader.isModLoaded(MOD_ID) || fluid == null || fluid.amount <= 0) return null;

        return original != null && isFluidPacket(original) ? createPacketStack(fluid) : createDropStack(fluid);
    }

    private static boolean isGasEnabled() {
        return Loader.isModLoaded(GAS_MOD_ID);
    }

    private static boolean isFluidPacket(IAEItemStack stack) {
        return matchesItemId(stack.getDefinition(), FLUID_PACKET_ID);
    }

    private static boolean isFluidDrop(IAEItemStack stack) {
        return matchesItemId(stack.getDefinition(), FLUID_DROP_ID);
    }

    private static boolean matchesItemId(ItemStack stack, ResourceLocation itemId) {
        if (stack.isEmpty()) return false;

        Item item = stack.getItem();
        return item != null && itemId.equals(item.getRegistryName());
    }

    @Nullable
    private static FluidStack readPacketFluid(IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();
        if (!definition.hasTagCompound()) return null;

        NBTTagCompound tag = definition.getTagCompound();
        if (tag == null || !tag.hasKey("FluidStack")) return null;

        FluidStack fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("FluidStack"));
        return fluid != null && fluid.amount > 0 ? fluid : null;
    }

    @Nullable
    private static FluidStack readDropFluid(IAEItemStack stack) {
        if (!isFluidDrop(stack) || stack.getStackSize() <= 0 || stack.getStackSize() > Integer.MAX_VALUE) return null;

        ItemStack definition = stack.getDefinition();
        if (!definition.hasTagCompound()) return null;

        NBTTagCompound tag = definition.getTagCompound();
        if (tag == null || !tag.hasKey("Fluid")) return null;

        Fluid fluidType = FluidRegistry.getFluid(tag.getString("Fluid"));
        if (fluidType == null) return null;

        FluidStack fluid = new FluidStack(fluidType, (int) stack.getStackSize());
        if (tag.hasKey("FluidTag")) fluid.tag = tag.getCompoundTag("FluidTag").copy();

        return fluid;
    }

    @Nullable
    private static IAEItemStack createDropStack(FluidStack fluid) {
        if (fluid.amount <= 0) return null;

        Item dropItem = ForgeRegistries.ITEMS.getValue(FLUID_DROP_ID);
        if (dropItem == null) return null;

        ItemStack dropStack = new ItemStack(dropItem, fluid.amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Fluid", fluid.getFluid().getName());
        if (fluid.tag != null) {
            tag.setTag("FluidTag", fluid.tag.copy());
        }
        dropStack.setTagCompound(tag);

        IAEItemStack aeDropStack = AEItemStack.fromItemStack(dropStack);
        if (aeDropStack == null) return null;

        aeDropStack.setStackSize(fluid.amount);
        return aeDropStack;
    }

    @Nullable
    private static IAEItemStack createPacketStack(FluidStack fluid) {
        if (fluid.amount <= 0) return null;

        Item packetItem = ForgeRegistries.ITEMS.getValue(FLUID_PACKET_ID);
        if (packetItem == null) return null;

        ItemStack packetStack = new ItemStack(packetItem);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluid.writeToNBT(fluidTag);
        tag.setTag("FluidStack", fluidTag);
        packetStack.setTagCompound(tag);

        return AEItemStack.fromItemStack(packetStack);
    }

    private static ItemStack createDisplayPacketStack(FluidStack fluid) {
        if (fluid.amount <= 0) return ItemStack.EMPTY;

        Item packetItem = ForgeRegistries.ITEMS.getValue(FLUID_PACKET_ID);
        if (packetItem == null) return ItemStack.EMPTY;

        FluidStack displayFluid = fluid.copy();
        displayFluid.amount = 1000;

        ItemStack packetStack = new ItemStack(packetItem);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound fluidTag = new NBTTagCompound();
        displayFluid.writeToNBT(fluidTag);
        tag.setTag("FluidStack", fluidTag);
        tag.setBoolean("DisplayOnly", true);
        packetStack.setTagCompound(tag);
        return packetStack;
    }

}