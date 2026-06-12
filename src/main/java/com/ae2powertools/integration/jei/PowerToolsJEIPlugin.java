package com.ae2powertools.integration.jei;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.ingredients.IIngredientRegistry;

import com.ae2powertools.features.crafter.GuiAutoCrafter;
import com.ae2powertools.features.maintainer.GuiBetterLevelMaintainer;
import com.ae2powertools.features.monitor.dependent.GuiStorageMonitor;


/**
 * JEI plugin for AE2 PowerTools.
 * Handles JEI exclusion zones for GUIs.
 */
@JEIPlugin
public class PowerToolsJEIPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        // Register advanced GUI handler for maintainer GUI
        registry.addAdvancedGuiHandlers(new MaintainerGuiHandler());
        // AutoCrafter: the Pattern Multi-Tool panel extends outside the base GUI.
        registry.addAdvancedGuiHandlers(new AutoCrafterGuiHandler());
        // Storage Emitter / Display: the AND/OR side button sits outside guiLeft
        // and would otherwise be hidden under JEI's overlay panel.
        registry.addAdvancedGuiHandlers(new StorageMonitorGuiHandler());

        // Cache the ingredient registry now (it's only available off IModRegistry, NOT IJeiRuntime
        // in JEI 4.8). The storage-emitter GUI uses this later to delegate non-item tooltip
        // building to JEI's own renderers.
        ingredientRegistry = registry.getIngredientRegistry();
    }

    /**
     * Cached at register time so the storage-emitter GUI can delegate its tooltip
     * rendering for non-item ingredients (fluids, gas, essentia) to whichever JEI
     * plugin owns those ingredient types, giving us pixel-identical tooltips to
     * JEI's own without us reimplementing per-mod formatting.
     * <p>
     * Null when JEI hasn't reached its register stage yet (or isn't loaded). Callers
     * must null-check and fall back to manual tooltip building.
     */
    @Nullable
    private static IIngredientRegistry ingredientRegistry;

    /**
     * Exposes JEI's IIngredientRegistry to other parts of the mod that need to query
     * tooltip / mod-name info for arbitrary ingredients. Returns null when JEI is not
     * yet ready or not loaded; callers must handle that case.
     */
    @Nullable
    public static IIngredientRegistry getIngredientRegistry() {
        return ingredientRegistry;
    }

    /**
     * GUI handler for the Better Level Maintainer GUI.
     * Provides JEI with exclusion zones for the style toggle button.
     */
    public static class MaintainerGuiHandler implements IAdvancedGuiHandler<GuiBetterLevelMaintainer> {

        @Override
        @Nonnull
        public Class<GuiBetterLevelMaintainer> getGuiContainerClass() {
            return GuiBetterLevelMaintainer.class;
        }

        @Nullable
        @Override
        public List<Rectangle> getGuiExtraAreas(@Nonnull GuiBetterLevelMaintainer gui) {
            return gui.getJEIExclusionArea();
        }

        @Nullable
        @Override
        public Object getIngredientUnderMouse(@Nonnull GuiBetterLevelMaintainer gui, int mouseX, int mouseY) {
            return null;
        }
    }

    /**
     * GUI handler for the AutoCrafter GUI.
     * Provides JEI with the exclusion zone for the Pattern Multi-Tool panel.
     */
    public static class AutoCrafterGuiHandler implements IAdvancedGuiHandler<GuiAutoCrafter> {

        @Override
        @Nonnull
        public Class<GuiAutoCrafter> getGuiContainerClass() {
            return GuiAutoCrafter.class;
        }

        @Nullable
        @Override
        public List<Rectangle> getGuiExtraAreas(@Nonnull GuiAutoCrafter gui) {
            return gui.getJEIExclusionArea();
        }

        @Nullable
        @Override
        public Object getIngredientUnderMouse(@Nonnull GuiAutoCrafter gui, int mouseX, int mouseY) {
            return null;
        }
    }

    /**
     * GUI handler for the ME Storage Level Emitter / Display.
     * Provides JEI with the exclusion zone for the AND/OR side button drawn outside guiLeft.
     */
    public static class StorageMonitorGuiHandler implements IAdvancedGuiHandler<GuiStorageMonitor> {

        @Override
        @Nonnull
        public Class<GuiStorageMonitor> getGuiContainerClass() {
            return GuiStorageMonitor.class;
        }

        @Nullable
        @Override
        public List<Rectangle> getGuiExtraAreas(@Nonnull GuiStorageMonitor gui) {
            return gui.getJEIExclusionArea();
        }

        @Nullable
        @Override
        public Object getIngredientUnderMouse(@Nonnull GuiStorageMonitor gui, int mouseX, int mouseY) {
            return null;
        }
    }
}
