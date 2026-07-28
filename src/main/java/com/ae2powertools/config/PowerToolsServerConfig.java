package com.ae2powertools.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.ae2powertools.Tags;


/**
 * Server-side configuration for AE2 PowerTools.
 * These settings affect gameplay balance and are synced to clients.
 */
@Config(modid = Tags.MODID, name = Tags.MODID + "/server", category = "server")
@Config.LangKey("ae2powertools.config.server")
public class PowerToolsServerConfig {

    @Config.LangKey("ae2powertools.config.server.crafter")
    public static final Crafter crafter = new Crafter();

    @Config.LangKey("ae2powertools.config.server.interfaceTerminal")
    public static final InterfaceTerminal interfaceTerminal = new InterfaceTerminal();

    @Config.LangKey("ae2powertools.config.server.maintainer")
    public static final Maintainer maintainer = new Maintainer();

    public static class Crafter {

        @Config.LangKey("ae2powertools.config.server.crafter.baseCraftsPerOperation")
        @Config.Comment({
            "Base number of crafts performed per operation cycle.",
            "This is a flat multiplier on ALL batch sizes, dictating base throughput.",
            "Example: Set to 20 = 20 crafts per second (at default 1 second cycle).",
            "Multiplied with user's batch size for total crafts per operation.",
            "Higher values = higher throughput."
        })
        @Config.RangeInt(min = 1, max = 1000000)
        public int baseCraftsPerOperation = 1;

        /**
         * Gets the base crafts per operation multiplier.
         * This is multiplied with user's batch size for total crafts per cycle.
         * 
         * @return base crafts per operation (minimum 1)
         */
        public int getBaseCraftsPerOperation() {
            return Math.max(1, baseCraftsPerOperation);
        }

        public void setBaseCraftsPerOperation(int value) {
            if (baseCraftsPerOperation == value) return;

            baseCraftsPerOperation = Math.max(1, Math.min(1000, value));
            ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }
    }

    public static class InterfaceTerminal {

        @Config.LangKey("ae2powertools.config.server.interfaceTerminal.enableAutoCrafterPatternRows")
        @Config.Comment({
            "Expose AE2 AutoCrafters in the Interface Terminal as editable pattern rows.",
            "Requires mixinbooter to be installed. Requires a full game restart after change.",
        })
        @Config.RequiresMcRestart
        public boolean enableAutoCrafterPatternRows = true;

        public boolean isAutoCrafterPatternRowsEnabled() {
            return enableAutoCrafterPatternRows;
        }
    }

    public static class Maintainer {

        @Config.LangKey("ae2powertools.config.server.maintainer.maxConcurrentCalculations")
        @Config.Comment({
            "Maximum number of concurrent crafting job calculations allowed.",
            "Limits server load when many maintainer entries need crafting simultaneously.",
            "Complex crafting trees can be expensive to calculate.",
            "Higher values allow more entries to calculate in parallel but may cause lag."
        })
        @Config.RangeInt(min = 1, max = 32)
        public int maxConcurrentCalculations = 2;

        @Config.LangKey("ae2powertools.config.server.maintainer.maxCpuRetryCount")
        @Config.Comment({
            "Maximum number of times a task can retry waiting for a free CPU before giving up.",
            "Prevents tasks from being stuck indefinitely in the CPU wait queue.",
            "After the limit is reached, the entry is put in an error state until the next run.",
            "Higher values allow longer waits for a free CPU before erroring."
        })
        @Config.RangeInt(min = 1, max = 1000)
        public int maxCpuRetryCount = 10;

        public int getMaxConcurrentCalculations() {
            return Math.max(1, maxConcurrentCalculations);
        }

        public int getMaxCpuRetryCount() {
            return Math.max(1, maxCpuRetryCount);
        }
    }

    @Mod.EventBusSubscriber(modid = Tags.MODID)
    public static class ConfigSyncHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(Tags.MODID)) ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }
    }
}
