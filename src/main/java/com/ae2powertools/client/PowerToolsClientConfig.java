package com.ae2powertools.client;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import com.ae2powertools.Tags;


/**
 * Client-side configuration for AE2 PowerTools.
 */
@Config(modid = Tags.MODID, name = Tags.MODID + "/client", category = "client")
@Config.LangKey("ae2powertools.config.client")
public class PowerToolsClientConfig {

    @Config.LangKey("ae2powertools.config.client.scanner")
    public static final Scanner scanner = new Scanner();

    @Config.LangKey("ae2powertools.config.client.maintainer")
    public static final Maintainer maintainer = new Maintainer();

    public static class Maintainer {
        @Config.LangKey("ae2powertools.config.client.maintainer.useTallView")
        public boolean useTallView = false;

        public boolean isUseTallView() {
            return useTallView;
        }

        public void setUseTallView(boolean value) {
            if (useTallView == value) return;

            useTallView = value;
            ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }
    }

    public static class Scanner {
        @Config.LangKey("ae2powertools.config.client.scanner.arrowScalePercent")
        @Config.RangeInt(min = 10, max = 1000)
        public int arrowScalePercent = 100;

        @Config.LangKey("ae2powertools.config.client.scanner.textScalePercent")
        @Config.RangeInt(min = 10, max = 1000)
        public int textScalePercent = 100;

        @Config.LangKey("ae2powertools.config.client.scanner.adaptiveTextScale")
        public boolean adaptiveTextScale = true;

        @Config.LangKey("ae2powertools.config.client.scanner.adaptiveTextScaleMinPercent")
        @Config.RangeInt(min = 10, max = 1000)
        public int adaptiveTextScaleMinPercent = 100;

        @Config.LangKey("ae2powertools.config.client.scanner.adaptiveTextScaleMaxPercent")
        @Config.RangeInt(min = 10, max = 1000)
        public int adaptiveTextScaleMaxPercent = 200;

        // Helper methods to get float values
        public float getArrowScale() {
            return arrowScalePercent / 100.0f;
        }

        public float getTextScale() {
            return textScalePercent / 100.0f;
        }

        public float getAdaptiveMin() {
            return adaptiveTextScaleMinPercent / 100.0f;
        }

        public float getAdaptiveMax() {
            return adaptiveTextScaleMaxPercent / 100.0f;
        }
    }

    @Mod.EventBusSubscriber(modid = Tags.MODID, value = Side.CLIENT)
    public static class ConfigSyncHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(Tags.MODID)) ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }
    }
}
