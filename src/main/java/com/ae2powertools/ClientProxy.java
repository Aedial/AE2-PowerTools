
package com.ae2powertools;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.ae2powertools.client.BlockHighlightRenderer;
import com.ae2powertools.client.DisplayBlockColor;
import com.ae2powertools.client.HudOverlayManager;
import com.ae2powertools.features.monitor.alarm.LevelMonitorAlarmArrowRenderer;
import com.ae2powertools.features.monitor.alarm.LevelMonitorAlarmOverlay;
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

    private static final String WAILA_MODID = "waila";

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        if (Loader.isModLoaded(WAILA_MODID)) registerWailaIntegration();

        ScannerRenderer scannerRenderer = new ScannerRenderer();
        LocatorRenderer locatorRenderer = new LocatorRenderer();
        RemoteMonitorOverlay remoteMonitorOverlay = new RemoteMonitorOverlay();
        LevelMonitorAlarmOverlay levelMonitorAlarmOverlay = new LevelMonitorAlarmOverlay();
        LevelMonitorAlarmArrowRenderer levelMonitorAlarmArrowRenderer = new LevelMonitorAlarmArrowRenderer();

        HudOverlayManager.register(scannerRenderer);
        HudOverlayManager.register(locatorRenderer);
        HudOverlayManager.register(remoteMonitorOverlay);
        HudOverlayManager.register(levelMonitorAlarmOverlay);
        HudOverlayManager.register(levelMonitorAlarmArrowRenderer);

        // Register client-side event handlers
        MinecraftForge.EVENT_BUS.register(HudOverlayManager.INSTANCE);
        MinecraftForge.EVENT_BUS.register(scannerRenderer);
        MinecraftForge.EVENT_BUS.register(locatorRenderer);
        MinecraftForge.EVENT_BUS.register(levelMonitorAlarmArrowRenderer);
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
            ItemRegistry.STORAGE_DISPLAY_PART,
            ItemRegistry.STORAGE_DISPLAY_SMALLER_PART,
            ItemRegistry.STORAGE_DISPLAY_SMALLLER_PART);
    }

    @Optional.Method(modid = WAILA_MODID)
    private static void registerWailaIntegration() {
        com.ae2powertools.integration.waila.PowerToolsWailaModule.register();
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        DisplayRenderHelper.registerSprites(event.getMap());
    }
}
