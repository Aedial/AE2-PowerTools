package com.ae2powertools.integration.jei;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IAdvancedGuiHandler;

import com.ae2powertools.Tags;
import com.ae2powertools.features.maintainer.GuiBetterLevelMaintainer;


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
}
