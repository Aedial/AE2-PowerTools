package com.ae2powertools.util.upgrade;

import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.api.config.Upgrades;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.items.IUpgradeModule;


/**
 * Describes one upgrade card type that a picker-enabled upgrade inventory supports.
 * The matcher is item-driven rather than enum-driven so future custom upgrades can
 * participate in the same UI without depending on AE2's {@link Upgrades} enum.
 */
public final class UpgradeCardDefinition {

    private final ItemStack previewStack;
    private final Predicate<ItemStack> matcher;
    private final String tooltipSuffix;

    public UpgradeCardDefinition(ItemStack previewStack, Predicate<ItemStack> matcher, String tooltipSuffix) {
        this.previewStack = previewStack.copy();
        this.matcher = matcher;
        this.tooltipSuffix = tooltipSuffix;
    }

    public ItemStack getPreviewStack() {
        return previewStack.copy();
    }

    public String getTooltipSuffix() {
        return tooltipSuffix;
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && matcher.test(stack);
    }

    /**
     * Convenience helper for AE2's built-in upgrade cards.
     * Custom upgrades can use the constructor directly with any preview stack and matcher.
     */
    public static UpgradeCardDefinition ae2Card(IItemDefinition definition, Upgrades upgrade, String tooltipSuffix) {
        return new UpgradeCardDefinition(
            definition.maybeStack(1).orElse(ItemStack.EMPTY),
            stack -> stack.getItem() instanceof IUpgradeModule
                && ((IUpgradeModule) stack.getItem()).getType(stack) == upgrade,
            tooltipSuffix);
    }
}