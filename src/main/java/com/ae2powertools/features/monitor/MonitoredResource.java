package com.ae2powertools.features.monitor;

import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;
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

    public static MonitoredResource ofItem(IAEItemStack stack) {
        return new MonitoredResource(ResourceType.ITEM, stack.copy(), stack.getDefinition().getDisplayName());
    }

    public static MonitoredResource ofFluid(IAEFluidStack stack) {
        return new MonitoredResource(ResourceType.FLUID, stack.copy(), stack.getFluidStack().getLocalizedName());
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
        return displayName;
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

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBT_TYPE, type.getId());
        tag.setString(NBT_NAME, displayName);

        if (stack != null) {
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            tag.setTag(NBT_STACK, stackTag);
        }

        return tag;
    }

    @Nullable
    public static MonitoredResource readFromNBT(NBTTagCompound tag) {
        ResourceType type = ResourceType.fromId(tag.getInteger(NBT_TYPE));
        String name = tag.getString(NBT_NAME);
        NBTTagCompound stackTag = tag.hasKey(NBT_STACK) ? tag.getCompoundTag(NBT_STACK) : null;

        IAEStack<?> stack = deserializeStack(type, stackTag);

        return new MonitoredResource(type, stack, name);
    }

    // --- ByteBuf serialization (for network packets) ---

    public void writeToBuf(ByteBuf buf) {
        buf.writeInt(type.getId());
        ByteBufUtils.writeUTF8String(buf, displayName);

        NBTTagCompound stackTag = new NBTTagCompound();
        if (stack != null) stack.writeToNBT(stackTag);
        ByteBufUtils.writeTag(buf, stackTag);
    }

    public static MonitoredResource readFromBuf(ByteBuf buf) {
        ResourceType type = ResourceType.fromId(buf.readInt());
        String name = ByteBufUtils.readUTF8String(buf);
        NBTTagCompound stackTag = ByteBufUtils.readTag(buf);

        IAEStack<?> stack = deserializeStack(type, stackTag);

        return new MonitoredResource(type, stack, name);
    }

    // --- Internal helpers ---

    @Nullable
    private static IAEStack<?> deserializeStack(ResourceType type, @Nullable NBTTagCompound stackTag) {
        if (stackTag == null || stackTag.isEmpty()) return null;

        switch (type) {
            case ITEM:
                return AEItemStack.fromNBT(stackTag);

            case FLUID:
                IStorageChannel<IAEFluidStack> fluidChannel =
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                return fluidChannel.createFromNBT(stackTag);

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

    /**
     * Deserialize a gas stack via @Optional.Method.
     * Only called when mekeng is confirmed loaded.
     */
    @Nullable
    @Optional.Method(modid = "mekeng")
    private static IAEStack<?> deserializeGasStack(NBTTagCompound stackTag) {
        return AEApi.instance().storage()
            .getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class)
            .createFromNBT(stackTag);
    }

    /**
     * Deserialize an essentia stack via @Optional.Method.
     * Only called when thaumicenergistics is confirmed loaded.
     */
    @Nullable
    @Optional.Method(modid = "thaumicenergistics")
    private static IAEStack<?> deserializeEssentiaStack(NBTTagCompound stackTag) {
        return AEApi.instance().storage()
            .getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class)
            .createFromNBT(stackTag);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonitoredResource)) return false;

        MonitoredResource that = (MonitoredResource) o;
        if (type != that.type) return false;

        // Compare stacks ignoring quantity
        if (stack == null && that.stack == null) return Objects.equals(displayName, that.displayName);
        if (stack == null || that.stack == null) return false;

        return stack.getChannel().equals(that.stack.getChannel());
    }

    @Override
    public int hashCode() {
        // Use display name as fallback when stack is null
        if (stack == null) return 31 * type.hashCode() + displayName.hashCode();

        // Use a hash based on type + stack identity (not quantity)
        NBTTagCompound tag = new NBTTagCompound();
        stack.writeToNBT(tag);
        tag.removeTag("Cnt"); // Remove count to make it quantity-independent

        return 31 * type.hashCode() + tag.hashCode();
    }

    @Override
    public String toString() {
        return "MonitoredResource{" + type.getName() + ": " + displayName + "}";
    }


    /**
     * Lightweight key class for map lookups, based on resource type and stack identity.
     */
    public static final class MonitoredResourceKey {

        private final ResourceType type;
        @Nullable
        private final IAEStack<?> stack;
        private final String displayName;
        private int hashCode;

        private MonitoredResourceKey(MonitoredResource resource) {
            this.type = resource.type;
            this.stack = resource.stack;
            this.displayName = resource.displayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MonitoredResourceKey)) return false;

            MonitoredResourceKey that = (MonitoredResourceKey) o;
            if (type != that.type) return false;
            if (stack == null && that.stack == null) return Objects.equals(displayName, that.displayName);
            if (stack == null || that.stack == null) return false;

            return stack.getChannel().equals(that.stack.getChannel());
        }

        @Override
        public int hashCode() {
            this.hashCode = this.stack.hashCode();
            return hashCode;
        }
    }
}
