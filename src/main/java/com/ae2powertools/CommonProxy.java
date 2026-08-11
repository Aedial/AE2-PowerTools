package com.ae2powertools;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import appeng.api.AEApi;

import com.ae2powertools.features.monitor.alarm.LevelMonitorAlarmEventHandler;
import com.ae2powertools.features.tuner.PriorityTunerEventHandler;


/**
 * Common proxy for server-side initialization.
 */
public class CommonProxy {

    private static final String TOP_MODID = "theoneprobe";

    public void preInit(FMLPreInitializationEvent event) {
        // Register TOP integration (must be on common, even though TOP is mostly client-side)
        if (Loader.isModLoaded(TOP_MODID)) registerTheOneProbeIntegration();

        MinecraftForge.EVENT_BUS.register(new PriorityTunerEventHandler());
        MinecraftForge.EVENT_BUS.register(new LevelMonitorAlarmEventHandler());
    }

    public void init(FMLInitializationEvent event) {
        AEApi.instance().registries().wireless().registerWirelessHandler(ItemRegistry.REMOTE_STORAGE_MONITOR);
    }

    @Optional.Method(modid = TOP_MODID)
    private static void registerTheOneProbeIntegration() {
        com.ae2powertools.integration.theoneprobe.PowerToolsTheOneProbePlugin.register();
    }
}
