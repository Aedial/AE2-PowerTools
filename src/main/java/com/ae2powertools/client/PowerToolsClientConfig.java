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

    @Config.LangKey("ae2powertools.config.client.monitor")
    public static final Monitor monitor = new Monitor();

    @Config.LangKey("ae2powertools.config.client.locator")
    public static final Locator locator = new Locator();

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

        // Internal preferences: per-tab sort modes (0=Distance, 1=Name). Not user-facing.
        @Config.Comment("Internal scanner preference. Per-tab sort mode: 0=Distance, 1=Name.")
        public int sortModeLoops = 0;
        public int sortModeChunks = 0;
        public int sortModeChokepoints = 0;
        public int sortModeMissing = 0;

        public int getSortMode(int tabOrdinal) {
            switch (tabOrdinal) {
                case 0: return sortModeLoops;
                case 1: return sortModeChunks;
                case 2: return sortModeChokepoints;
                case 3: return sortModeMissing;
                default: return 0;
            }
        }

        public void setSortMode(int tabOrdinal, int value) {
            switch (tabOrdinal) {
                case 0: if (sortModeLoops == value) return; sortModeLoops = value; break;
                case 1: if (sortModeChunks == value) return; sortModeChunks = value; break;
                case 2: if (sortModeChokepoints == value) return; sortModeChokepoints = value; break;
                case 3: if (sortModeMissing == value) return; sortModeMissing = value; break;
                default: return;
            }

            ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }

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
            if (event.getModID().equals(Tags.MODID)) {
                ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
                monitor.reParseColors();
            }
        }
    }

    public static class Monitor {

        @Config.LangKey("ae2powertools.config.client.monitor.displayRenderDistance")
        @Config.Comment("Maximum distance (in blocks) at which ME Storage Displays render content.")
        @Config.RangeInt(min = 4, max = 128)
        public int displayRenderDistance = 16;

        @Config.LangKey("ae2powertools.config.client.monitor.displayColorAbove")
        @Config.Comment("Corner indicator color (ARGB hex) when quantity is at or above the color threshold.")
        public String displayColorAbove = "FF00CC00";

        @Config.LangKey("ae2powertools.config.client.monitor.displayColorBelow")
        @Config.Comment("Corner indicator color (ARGB hex) when quantity is below the color threshold.")
        public String displayColorBelow = "FFCCCC00";

        /** Parsed ARGB int for the "above threshold" color. Cached on config load. */
        private transient int parsedColorAbove = 0xFF00CC00;

        /** Parsed ARGB int for the "below threshold" color. Cached on config load. */
        private transient int parsedColorBelow = 0xFFCCCC00;

        public int getColorAbove() {
            return parsedColorAbove;
        }

        public int getColorBelow() {
            return parsedColorBelow;
        }

        /**
         * Re-parses the hex color strings into int values.
         * Called after config sync to update the cached parsed values.
         */
        public void reParseColors() {
            parsedColorAbove = parseArgb(displayColorAbove, 0xFF00CC00);
            parsedColorBelow = parseArgb(displayColorBelow, 0xFFCCCC00);
        }

        private static int parseArgb(String hex, int fallback) {
            if (hex == null || hex.isEmpty()) return fallback;

            // Strip leading # or 0x if present
            String clean = hex.startsWith("#") ? hex.substring(1)
                         : hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2)
                         : hex;

            try {
                return (int) Long.parseLong(clean, 16);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    public static class Locator {
        @Config.LangKey("ae2powertools.config.client.locator.useTallView")
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
}
