package com.ae2powertools;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.crafter.BlockAutoCrafter;
import com.ae2powertools.features.crafter.TileAutoCrafter;
import com.ae2powertools.features.maintainer.BlockBetterLevelMaintainer;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;
import com.ae2powertools.features.monitor.display.BlockStorageDisplay;
import com.ae2powertools.features.monitor.display.ItemBlockStorageDisplay;
import com.ae2powertools.features.monitor.display.TileStorageDisplay;
import com.ae2powertools.features.monitor.alarm.BlockLevelMonitorAlarm;
import com.ae2powertools.features.monitor.alarm.ItemBlockLevelMonitorAlarm;
import com.ae2powertools.features.monitor.alarm.TileLevelMonitorAlarm;
import com.ae2powertools.features.monitor.emitter.BlockStorageLevelEmitter;
import com.ae2powertools.features.monitor.emitter.ItemBlockStorageLevelEmitter;
import com.ae2powertools.features.monitor.emitter.TileStorageLevelEmitter;


/**
 * Registry for all blocks in the mod.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public class BlockRegistry {

    public static BlockBetterLevelMaintainer BETTER_LEVEL_MAINTAINER;
    public static BlockAutoCrafter AUTO_CRAFTER;
    public static BlockStorageLevelEmitter STORAGE_LEVEL_EMITTER;
    public static BlockStorageDisplay STORAGE_DISPLAY;
    public static BlockLevelMonitorAlarm LEVEL_MONITOR_ALARM;

    public static void init() {
        BETTER_LEVEL_MAINTAINER = new BlockBetterLevelMaintainer();
        AUTO_CRAFTER = new BlockAutoCrafter();
        STORAGE_LEVEL_EMITTER = new BlockStorageLevelEmitter();
        STORAGE_DISPLAY = new BlockStorageDisplay();
        LEVEL_MONITOR_ALARM = new BlockLevelMonitorAlarm();

        // Register tile entities
        GameRegistry.registerTileEntity(TileBetterLevelMaintainer.class,
                new ResourceLocation(Tags.MODID, "better_level_maintainer"));
        GameRegistry.registerTileEntity(TileAutoCrafter.class,
                new ResourceLocation(Tags.MODID, "auto_crafter"));
        GameRegistry.registerTileEntity(TileStorageLevelEmitter.class,
                new ResourceLocation(Tags.MODID, "storage_level_emitter"));
        GameRegistry.registerTileEntity(TileStorageDisplay.class,
                new ResourceLocation(Tags.MODID, "storage_display"));
        GameRegistry.registerTileEntity(TileLevelMonitorAlarm.class,
            new ResourceLocation(Tags.MODID, "level_monitor_alarm"));
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(
                BETTER_LEVEL_MAINTAINER,
                AUTO_CRAFTER,
                STORAGE_LEVEL_EMITTER,
                STORAGE_DISPLAY,
                LEVEL_MONITOR_ALARM
        );
    }

    @SubscribeEvent
    public static void registerItemBlocks(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(
                createItemBlock(BETTER_LEVEL_MAINTAINER),
                createItemBlock(AUTO_CRAFTER),
                createCustomItemBlock(new ItemBlockStorageLevelEmitter(STORAGE_LEVEL_EMITTER)),
                createCustomItemBlock(new ItemBlockStorageDisplay(STORAGE_DISPLAY)),
                createCustomItemBlock(new ItemBlockLevelMonitorAlarm(LEVEL_MONITOR_ALARM))
        );
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void registerModels(ModelRegistryEvent event) {
        registerBlockModel(BETTER_LEVEL_MAINTAINER);
        registerBlockModel(AUTO_CRAFTER);
        registerBlockModel(STORAGE_LEVEL_EMITTER);
        registerBlockModel(STORAGE_DISPLAY);
        registerBlockModel(LEVEL_MONITOR_ALARM);
    }

    private static ItemBlock createItemBlock(Block block) {
        ItemBlock itemBlock = new ItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());

        return itemBlock;
    }

    /**
     * Sets the registry name on a custom ItemBlock (e.g. ItemBlockStorageLevelEmitter)
     * that already extends ItemBlock but doesn't call setRegistryName in its constructor.
     */
    private static ItemBlock createCustomItemBlock(ItemBlock itemBlock) {
        itemBlock.setRegistryName(itemBlock.getBlock().getRegistryName());

        return itemBlock;
    }

    @SideOnly(Side.CLIENT)
    private static void registerBlockModel(Block block) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
                new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }
}
