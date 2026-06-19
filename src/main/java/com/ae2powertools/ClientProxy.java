
package com.ae2powertools;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.ae2powertools.client.BlockHighlightRenderer;
import com.ae2powertools.client.DisplayBlockColor;
import com.ae2powertools.features.monitor.display.DisplayRenderHelper;
import com.ae2powertools.features.monitor.display.TESRStorageDisplay;
import com.ae2powertools.features.monitor.display.TileStorageDisplay;
import com.ae2powertools.features.locator.LocatorRenderer;
import com.ae2powertools.features.remotemonitor.RemoteMonitorOverlay;
import com.ae2powertools.features.scanner.ScannerRenderer;


/**
 * Client proxy for client-side initialization.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register client-side event handlers
        MinecraftForge.EVENT_BUS.register(new ScannerRenderer());
        MinecraftForge.EVENT_BUS.register(new LocatorRenderer());
        MinecraftForge.EVENT_BUS.register(new RemoteMonitorOverlay());
        MinecraftForge.EVENT_BUS.register(new BlockHighlightRenderer());

        // Register TESRs for dynamic content rendering (item icon + quantity text)
        ClientRegistry.bindTileEntitySpecialRenderer(TileStorageDisplay.class, new TESRStorageDisplay());

        DisplayBlockColor displayColor = new DisplayBlockColor();

        // Register block + item color handlers for the display's tinted center / corner overlays.
        Minecraft.getMinecraft().getBlockColors()
            .registerBlockColorHandler(displayColor, BlockRegistry.STORAGE_DISPLAY);
        Minecraft.getMinecraft().getItemColors().registerItemColorHandler(
            displayColor,
            Item.getItemFromBlock(BlockRegistry.STORAGE_DISPLAY),
            ItemRegistry.STORAGE_DISPLAY_PART);
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        DisplayRenderHelper.registerSprites(event.getMap());
    }
}
