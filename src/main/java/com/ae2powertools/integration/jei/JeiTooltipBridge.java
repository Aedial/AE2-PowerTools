package com.ae2powertools.integration.jei;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.Optional;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IIngredientRenderer;

import com.ae2powertools.features.monitor.MonitoredResource;


/**
 * Builds vanilla / JEI-quality tooltips for the storage emitter's resource cells and
 * selector slots without forcing the GUI code to know about every backing API.
 * <p>
 * Strategy:
 * <ol>
 *   <li>For items, defer to the GUI's vanilla {@code renderToolTip} (handled in the GUI).</li>
 *   <li>For non-items, ask JEI's {@link IIngredientRegistry#getIngredientRenderer(Object)}
 *       for the renderer registered against the underlying object (FluidStack, GasStack,
 *       AspectStack, ...). If a renderer is found, call {@code getTooltip} on it -- this
 *       gives us identical output to JEI's own ingredient list overlay.</li>
 *   <li>If JEI isn't loaded or doesn't know the ingredient type, fall back to a manual
 *       tooltip composed of display name + mod source + type label.</li>
 * </ol>
 */
public final class JeiTooltipBridge {

    private JeiTooltipBridge() {}

    /**
     * Returns the tooltip lines for the given resource, ready to feed to
     * {@code GuiUtils.drawHoveringText}.
     * <p>
     * Items are NOT handled here, callers should detect items first and use vanilla's
     * {@code GuiScreen.renderToolTip(ItemStack, ...)} instead, which preserves rarity
     * coloring and advanced flag behavior.
     */
    public static List<String> buildTooltip(MonitoredResource resource) {
        ITooltipFlag flag = Minecraft.getMinecraft().gameSettings.advancedItemTooltips
            ? ITooltipFlag.TooltipFlags.ADVANCED
            : ITooltipFlag.TooltipFlags.NORMAL;

        // Try JEI's renderer first if JEI is loaded and ready.
        List<String> jei = tryJeiTooltip(resource, flag);
        if (jei != null && !jei.isEmpty()) return jei;

        return manualTooltip(resource);
    }

    /**
     * Looks up the JEI ingredient renderer for the resource's underlying object and
     * asks it for the tooltip. Returns null when JEI isn't available, the ingredient
     * isn't registered with JEI, or anything unexpected happens.
     */
    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> tryJeiTooltip(MonitoredResource resource, ITooltipFlag flag) {
        if (!Loader.isModLoaded("jei")) return null;

        IIngredientRegistry registry = PowerToolsJEIPlugin.getIngredientRegistry();
        if (registry == null) return null;

        Object ingredient = extractIngredient(resource);
        if (ingredient == null) return null;

        // getIngredientRenderer(Object) raw-returns IIngredientRenderer<?>; the unchecked
        // cast is unavoidable because we resolve the type at runtime via the ingredient's
        // own class. JEI guarantees the renderer matches the ingredient it was looked up by.
        IIngredientRenderer renderer;
        try {
            renderer = registry.getIngredientRenderer(ingredient);
        } catch (IllegalArgumentException ex) {
            // JEI throws when no renderer is registered for that ingredient type.
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();
        return renderer.getTooltip(mc, ingredient, flag);
    }

    /**
     * Extracts the underlying ingredient object (FluidStack, GasStack, AspectStack)
     * from a MonitoredResource. Returns null when the type isn't recognised.
     * <p>
     * Items are intentionally NOT extracted here: callers handle items separately to
     * preserve vanilla rarity tooltip rendering.
     */
    @Nullable
    private static Object extractIngredient(MonitoredResource resource) {
        IAEStack<?> stack = resource.getStack();
        if (stack == null) return null;

        if (stack instanceof IAEFluidStack) {
            return ((IAEFluidStack) stack).getFluidStack();
        }

        // Gas / essentia paths are guarded by mod presence and use reflection-free
        // @Optional methods -- the bytecode for those branches is stripped if the
        // corresponding mod is absent at load time.
        if (Loader.isModLoaded("mekeng")) {
            Object gas = extractGas(stack);
            if (gas != null) return gas;
        }
        if (Loader.isModLoaded("thaumicenergistics")) {
            Object aspect = extractAspect(stack);
            if (aspect != null) return aspect;
        }

        return null;
    }

    @Optional.Method(modid = "mekeng")
    @Nullable
    private static Object extractGas(IAEStack<?> stack) {
        if (!(stack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return null;
        return ((com.mekeng.github.common.me.data.IAEGasStack) stack).getGasStack();
    }

    @Optional.Method(modid = "thaumicenergistics")
    @Nullable
    private static Object extractAspect(IAEStack<?> stack) {
        if (!(stack instanceof thaumicenergistics.api.storage.IAEEssentiaStack)) return null;
        // EssentiaStack is the wrapper JEI's thaumicenergistics integration (if any) would
        // register against. We pass it raw and let JEI's lookup decide whether it has a renderer.
        return ((thaumicenergistics.api.storage.IAEEssentiaStack) stack).getStack();
    }

    /**
     * Manual fallback tooltip: display name + best-effort mod source line + type label.
     * Used when JEI isn't loaded or doesn't know how to render this ingredient.
     */
    private static List<String> manualTooltip(MonitoredResource resource) {
        List<String> lines = new ArrayList<>();
        lines.add(resource.getDisplayName());

        String modName = lookupModName(resource);
        if (modName != null) lines.add("§9§o" + modName);

        lines.add("§7" + resource.getType().getName());
        return lines;
    }

    /**
     * Best-effort mod-name lookup for non-item resources. Returns null when we can't
     * confidently determine a source mod -- better to skip the line than show garbage.
     */
    @Nullable
    private static String lookupModName(MonitoredResource resource) {
        IAEStack<?> stack = resource.getStack();
        if (stack instanceof IAEFluidStack) {
            FluidStack fs = ((IAEFluidStack) stack).getFluidStack();
            if (fs == null || fs.getFluid() == null) return null;
            // Forge gives no direct namespace for fluids in 1.12; use the still-texture's namespace.
            net.minecraft.util.ResourceLocation still = fs.getFluid().getStill();
            if (still == null) return null;
            ModContainer mc = Loader.instance().getIndexedModList().get(still.getNamespace());
            return mc != null ? mc.getName() : still.getNamespace();
        }
        if (stack instanceof IAEItemStack) {
            ItemStack is = ((IAEItemStack) stack).getDefinition();
            if (is.isEmpty() || is.getItem().getRegistryName() == null) return null;
            String ns = is.getItem().getRegistryName().getNamespace();
            ModContainer mc = Loader.instance().getIndexedModList().get(ns);
            return mc != null ? mc.getName() : ns;
        }
        return null;
    }
}
