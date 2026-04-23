package com.ae2powertools;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.ae2powertools.client.BlockHighlightRenderer;
import com.ae2powertools.features.locator.LocatorRenderer;
import com.ae2powertools.features.scanner.ScannerRenderer;


/**
 * Client proxy for client-side initialization.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register client-side event handlers
        MinecraftForge.EVENT_BUS.register(new ScannerRenderer());
        MinecraftForge.EVENT_BUS.register(new LocatorRenderer());
        MinecraftForge.EVENT_BUS.register(new BlockHighlightRenderer());
    }
}
