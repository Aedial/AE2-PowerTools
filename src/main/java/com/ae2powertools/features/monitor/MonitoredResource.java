package com.ae2powertools.features.monitor;

import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;


/**
 * Universal wrapper for any AE2 resource (item, fluid, gas, essentia).
 * Handles serialization, display name, equality, and working with optional mod channels.
 *
 * The wrapper stores the resource type and serialized NBT data, so it can be reconstructed
 * even when the original mod (gas/essentia) isn't loaded (i.e., only display/name
 * data is preserved but the stack cannot be resolved).
 */
public class MonitoredResource {

    private final ResourceType type;

    /**
     * The underlying AE stack. May be null if the mod that provides the channel is not loaded
     * (e.g., gas resource on a server without Mekanism Energistics).
     */
    @Nullable
    private final IAEStack<?> stack;

    /**
     * Display name, cached at creation time for tooltip use even if the stack can't be resolved.
     */
    private final String displayName;

    private MonitoredResource(ResourceType type, @Nullable IAEStack<?> stack, String displayName) {
        this.type = type;
        this.stack = stack;
        this.displayName = displayName;
    }

    // --- Factory methods ---

    public static MonitoredResource ofItem(IAEItemStack stack, boolean saveDisplayName) {
        String displayName = saveDisplayName ? stack.getDefinition().getDisplayName() : null;
        return new MonitoredResource(ResourceType.ITEM, stack.copy(), displayName);
    }

    public static MonitoredResource ofItem(IAEItemStack stack) {
        return ofItem(stack, true);
    }

    public static MonitoredResource ofFluid(IAEFluidStack stack, boolean saveDisplayName) {
        String displayName = saveDisplayName ? stack.getFluidStack().getLocalizedName() : null;
        return new MonitoredResource(ResourceType.FLUID, stack.copy(), displayName);
    }

    public static MonitoredResource ofFluid(IAEFluidStack stack) {
        return ofFluid(stack, true);
    }

    /**
     * Creates a MonitoredResource for a gas stack.
     * Only call when Mekanism Energistics is loaded.
     */
    public static MonitoredResource ofGas(IAEStack<?> stack, String displayName) {
        return new MonitoredResource(ResourceType.GAS, stack.copy(), displayName);
    }

    /**
     * Creates a MonitoredResource for an essentia stack.
     * Only call when Thaumic Energistics is loaded.
     */
    public static MonitoredResource ofEssentia(IAEStack<?> stack, String displayName) {
        return new MonitoredResource(ResourceType.ESSENTIA, stack.copy(), displayName);
    }

    // --- Accessors ---

    public ResourceType getType() {
        return type;
    }

    @Nullable
    public IAEStack<?> getStack() {
        return stack;
    }

    public String getDisplayName() {
        if (displayName != null) return displayName;

        return resolveDisplayName(type, stack);
    }

    /**
     * Returns a key suitable for map lookups. Two MonitoredResources with the same underlying
     * resource (ignoring quantity) should produce equal keys.
     */
    public MonitoredResourceKey toKey() {
        return new MonitoredResourceKey(this);
    }

    // --- NBT serialization ---

    private static final String NBT_TYPE = "Type";
    private static final String NBT_STACK = "Stack";
    private static final String NBT_NAME = "Name";
    private static final String NBT_ITEM_ID = "id";
    private static final String NBT_VANILLA_COUNT = "Count";
    private static final String NBT_DAMAGE = "Damage";
    private static final String NBT_ITEM_TAG = "tag";
    private static final String NBT_FLUID_NAME = "FluidName";
    private static final String NBT_FLUID_TAG = "Tag";
    private static final String NBT_GAS_NAME = "gasName";
    private static final String NBT_ASPECT = "Aspect";
    private static final int IDENTITY_STACK_SIZE = 1;

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBT_TYPE, type.getId());
        if (displayName != null && !displayName.isEmpty()) tag.setString(NBT_NAME, displayName);

        if (stack != null) {
            NBTTagCompound stackTag = serializeStack(this.type, this.stack);
            if (!stackTag.isEmpty()) tag.setTag(NBT_STACK, stackTag);
        }

        return tag;
    }

    @Nullable
    public static MonitoredResource readFromNBT(NBTTagCompound tag) {
        ResourceType type = ResourceType.fromId(tag.getInteger(NBT_TYPE));
        String name = tag.hasKey(NBT_NAME) ? tag.getString(NBT_NAME) : null;
        NBTTagCompound stackTag = tag.hasKey(NBT_STACK) ? tag.getCompoundTag(NBT_STACK) : null;

        IAEStack<?> stack = deserializeStack(type, stackTag);

        return new MonitoredResource(type, stack, name);
    }

    // --- ByteBuf serialization (for network packets) ---

    public void writeToBuf(ByteBuf buf) {
        buf.writeInt(type.getId());
        ByteBufUtils.writeUTF8String(buf, displayName != null ? displayName : "");

        NBTTagCompound stackTag = serializeStack(this.type, this.stack);
        ByteBufUtils.writeTag(buf, stackTag);
    }

    public static MonitoredResource readFromBuf(ByteBuf buf) {
        ResourceType type = ResourceType.fromId(buf.readInt());
        String name = ByteBufUtils.readUTF8String(buf);
        if (name.isEmpty()) name = null;
        NBTTagCompound stackTag = ByteBufUtils.readTag(buf);

        IAEStack<?> stack = deserializeStack(type, stackTag);

        return new MonitoredResource(type, stack, name);
    }

    // --- Internal helpers ---

    private static NBTTagCompound serializeStack(ResourceType type, @Nullable IAEStack<?> stack) {
        NBTTagCompound stackTag = new NBTTagCompound();
        if (stack == null) return stackTag;

        switch (type) {
            case ITEM:
                if (stack instanceof IAEItemStack) serializeItemStack((IAEItemStack) stack, stackTag);
                return stackTag;

            case FLUID:
                if (stack instanceof IAEFluidStack) serializeFluidStack((IAEFluidStack) stack, stackTag);
                return stackTag;

            case GAS:
                if (Loader.isModLoaded("mekeng")) serializeGasStack(stack, stackTag);
                return stackTag;

            case ESSENTIA:
                if (Loader.isModLoaded("thaumicenergistics")) serializeEssentiaStack(stack, stackTag);
                return stackTag;

            default:
                return stackTag;
        }
    }

    @Nullable
    private static IAEStack<?> deserializeStack(ResourceType type, @Nullable NBTTagCompound stackTag) {
        if (stackTag == null || stackTag.isEmpty()) return null;

        switch (type) {
            case ITEM:
                return deserializeItemStack(stackTag);

            case FLUID:
                return deserializeFluidStack(stackTag);

            case GAS:
                if (!Loader.isModLoaded("mekeng")) return null;
                return deserializeGasStack(stackTag);

            case ESSENTIA:
                if (!Loader.isModLoaded("thaumicenergistics")) return null;
                return deserializeEssentiaStack(stackTag);

            default:
                return null;
        }
    }

    private static void serializeItemStack(IAEItemStack stack, NBTTagCompound stackTag) {
        ItemStack itemStack = stack.getDefinition();
        if (itemStack.isEmpty() || itemStack.getItem().getRegistryName() == null) return;

        stackTag.setString(NBT_ITEM_ID, itemStack.getItem().getRegistryName().toString());
        stackTag.setShort(NBT_DAMAGE, (short) itemStack.getItemDamage());

        if (itemStack.hasTagCompound()) stackTag.setTag(NBT_ITEM_TAG, itemStack.getTagCompound().copy());
    }

    private static void serializeFluidStack(IAEFluidStack stack, NBTTagCompound stackTag) {
        FluidStack fluidStack = stack.getFluidStack();
        if (fluidStack == null || fluidStack.getFluid() == null) return;

        stackTag.setString(NBT_FLUID_NAME, fluidStack.getFluid().getName());
        if (fluidStack.tag != null) stackTag.setTag(NBT_FLUID_TAG, fluidStack.tag.copy());
    }

    @Nullable
    private static IAEItemStack deserializeItemStack(NBTTagCompound stackTag) {
        if (!stackTag.hasKey(NBT_ITEM_ID)) return null;

        NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString(NBT_ITEM_ID, stackTag.getString(NBT_ITEM_ID));
        itemTag.setByte(NBT_VANILLA_COUNT, (byte) IDENTITY_STACK_SIZE);

        if (stackTag.hasKey(NBT_DAMAGE)) itemTag.setShort(NBT_DAMAGE, stackTag.getShort(NBT_DAMAGE));
        if (stackTag.hasKey(NBT_ITEM_TAG)) itemTag.setTag(NBT_ITEM_TAG, stackTag.getCompoundTag(NBT_ITEM_TAG).copy());

        return AEItemStack.fromItemStack(new ItemStack(itemTag));
    }

    @Nullable
    private static IAEStack<?> deserializeFluidStack(NBTTagCompound stackTag) {
        if (!stackTag.hasKey(NBT_FLUID_NAME)) return null;

        Fluid fluid = FluidRegistry.getFluid(stackTag.getString(NBT_FLUID_NAME));
        if (fluid == null) return null;

        FluidStack fluidStack = new FluidStack(fluid, IDENTITY_STACK_SIZE);
        if (stackTag.hasKey(NBT_FLUID_TAG)) fluidStack.tag = stackTag.getCompoundTag(NBT_FLUID_TAG).copy();

        IStorageChannel<IAEFluidStack> fluidChannel =
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        return fluidChannel.createStack(fluidStack);
    }

    @Optional.Method(modid = "mekeng")
    private static void serializeGasStack(IAEStack<?> stack, NBTTagCompound stackTag) {
        if (!(stack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return;

        mekanism.api.gas.GasStack gasStack = ((com.mekeng.github.common.me.data.IAEGasStack) stack).getGasStack();
        if (gasStack == null || gasStack.getGas() == null) return;

        stackTag.setString(NBT_GAS_NAME, gasStack.getGas().getName());
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static void serializeEssentiaStack(IAEStack<?> stack, NBTTagCompound stackTag) {
        if (!(stack instanceof thaumicenergistics.api.storage.IAEEssentiaStack)) return;

        thaumicenergistics.api.EssentiaStack essentiaStack =
            ((thaumicenergistics.api.storage.IAEEssentiaStack) stack).getStack();
        if (essentiaStack == null || essentiaStack.getAspect() == null) return;

        stackTag.setString(NBT_ASPECT, essentiaStack.getAspect().getTag());
    }

    /**
     * Deserialize a gas stack via @Optional.Method.
     * Only called when mekeng is confirmed loaded.
     */
    @Nullable
    @Optional.Method(modid = "mekeng")
    private static IAEStack<?> deserializeGasStack(NBTTagCompound stackTag) {
        if (!stackTag.hasKey(NBT_GAS_NAME)) return null;

        mekanism.api.gas.Gas gas = mekanism.api.gas.GasRegistry.getGas(stackTag.getString(NBT_GAS_NAME));
        if (gas == null) return null;

        return com.mekeng.github.common.me.data.impl.AEGasStack.of(
            new mekanism.api.gas.GasStack(gas, IDENTITY_STACK_SIZE));
    }

    /**
     * Deserialize an essentia stack via @Optional.Method.
     * Only called when thaumicenergistics is confirmed loaded.
     */
    @Nullable
    @Optional.Method(modid = "thaumicenergistics")
    private static IAEStack<?> deserializeEssentiaStack(NBTTagCompound stackTag) {
        if (!stackTag.hasKey(NBT_ASPECT)) return null;

        thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(stackTag.getString(NBT_ASPECT));
        if (aspect == null) return null;

        return thaumicenergistics.integration.appeng.AEEssentiaStack.fromEssentiaStack(
            new thaumicenergistics.api.EssentiaStack(aspect, IDENTITY_STACK_SIZE));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonitoredResource)) return false;

        MonitoredResource that = (MonitoredResource) o;
        if (type != that.type) return false;

        if (stack == null && that.stack == null) return Objects.equals(displayName, that.displayName);

        return sameIdentity(type, stack, that.stack);
    }

    @Override
    public int hashCode() {
        if (stack == null) return 31 * type.hashCode() + Objects.hashCode(displayName);

        NBTTagCompound identityTag = identityTag(this.type, this.stack);

        return 31 * type.hashCode() + Objects.hashCode(identityTag);
    }

    @Override
    public String toString() {
        return "MonitoredResource{" + type.getName() + ": " + displayName + "}";
    }

    @Nullable
    private static NBTTagCompound identityTag(ResourceType type, @Nullable IAEStack<?> stack) {
        if (stack == null) return null;

        NBTTagCompound tag = serializeStack(type, stack);
        return tag.isEmpty() ? null : tag;
    }

    private static boolean sameIdentity(ResourceType type, @Nullable IAEStack<?> left, @Nullable IAEStack<?> right) {
        if (left == null || right == null) return false;

        NBTTagCompound leftTag = identityTag(type, left);
        if (leftTag == null) return false;

        NBTTagCompound rightTag = identityTag(type, right);
        if (rightTag == null) return false;

        return leftTag.equals(rightTag);
    }

    private static String resolveDisplayName(ResourceType type, @Nullable IAEStack<?> stack) {
        if (stack == null) return "";

        switch (type) {
            case ITEM:
                if (stack instanceof IAEItemStack) {
                    ItemStack itemStack = ((IAEItemStack) stack).getDefinition();
                    if (!itemStack.isEmpty()) return itemStack.getDisplayName();
                }
                return "";

            case FLUID:
                if (stack instanceof IAEFluidStack) {
                    FluidStack fluidStack = ((IAEFluidStack) stack).getFluidStack();
                    if (fluidStack != null) return fluidStack.getLocalizedName();
                }
                return "";

            case GAS:
                if (!Loader.isModLoaded("mekeng")) return "";
                return resolveGasDisplayName(stack);

            case ESSENTIA:
                if (!Loader.isModLoaded("thaumicenergistics")) return "";
                return resolveEssentiaDisplayName(stack);

            default:
                return "";
        }
    }

    @Optional.Method(modid = "mekeng")
    private static String resolveGasDisplayName(IAEStack<?> stack) {
        if (!(stack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return "";

        mekanism.api.gas.GasStack gasStack = ((com.mekeng.github.common.me.data.IAEGasStack) stack).getGasStack();
        if (gasStack == null || gasStack.getGas() == null) return "";

        return gasStack.getGas().getLocalizedName();
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static String resolveEssentiaDisplayName(IAEStack<?> stack) {
        if (!(stack instanceof thaumicenergistics.api.storage.IAEEssentiaStack)) return "";

        thaumicenergistics.api.EssentiaStack essentiaStack =
            ((thaumicenergistics.api.storage.IAEEssentiaStack) stack).getStack();
        if (essentiaStack == null || essentiaStack.getAspect() == null) return "";

        return essentiaStack.getAspect().getName();
    }


    /**
     * Lightweight key class for map lookups, based on resource type and stack identity.
     */
    public static final class MonitoredResourceKey {

        private final ResourceType type;
        @Nullable
        private final NBTTagCompound identityTag;
        private final String displayName;
        private Integer hashCode;

        private MonitoredResourceKey(MonitoredResource resource) {
            this.type = resource.type;
            this.identityTag = identityTag(resource.type, resource.stack);
            this.displayName = resource.displayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MonitoredResourceKey)) return false;

            MonitoredResourceKey that = (MonitoredResourceKey) o;
            if (type != that.type) return false;
            if (identityTag == null && that.identityTag == null) return Objects.equals(displayName, that.displayName);
            if (identityTag == null || that.identityTag == null) return false;

            return identityTag.equals(that.identityTag);
        }

        @Override
        public int hashCode() {
            if (hashCode == null) {
                hashCode = 31 * type.hashCode() + Objects.hashCode(identityTag != null ? identityTag : displayName);
            }
            return hashCode;
        }
    }
}
