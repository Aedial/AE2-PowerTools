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

    @Mod.EventBusSubscriber(modid = Tags.MODID)
    public static class ConfigSyncHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(Tags.MODID)) ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }
    }
}
