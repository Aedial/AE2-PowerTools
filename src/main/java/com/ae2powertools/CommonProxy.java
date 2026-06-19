package com.ae2powertools;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import appeng.api.AEApi;

import com.ae2powertools.features.tuner.PriorityTunerEventHandler;


/**
 * Common proxy for server-side initialization.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new PriorityTunerEventHandler());
    }

    public void init(FMLInitializationEvent event) {
        AEApi.instance().registries().wireless().registerWirelessHandler(ItemRegistry.REMOTE_STORAGE_MONITOR);
    }
}
