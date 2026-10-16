package com.ae2powertools.features.monitor.client;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.ResourceType;


/**
 * Client-side names for monitored resources.
 * <p>
 * The resolution code is technically common-side, but can call arbitrary client-side
 * code, which may include client-side only methods like getting localized names.
 * For safety, the resolution is bound to the client side.
 */
@SideOnly(Side.CLIENT)
public final class MonitoredResourceNameResolver {

    private MonitoredResourceNameResolver() {}

    /**
     * Returns the current client name, falling back to the persisted text when needed.
     */
    public static String getDisplayName(MonitoredResource resource) {
        if (resource == null) return "";

        String resolvedName = resolveDisplayName(resource.getType(), resource.getStack());
        if (!resolvedName.isEmpty()) return resolvedName;

        String fallbackName = resource.getFallbackDisplayName();
        return fallbackName != null ? fallbackName : "";
    }

    /**
     * Captures the current client name for persistence when the resource is selected.
     */
    public static MonitoredResource captureDisplayName(MonitoredResource resource) {
        if (resource == null) return null;

        String displayName = getDisplayName(resource);
        return displayName.isEmpty() ? resource : resource.withFallbackDisplayName(displayName);
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
}
