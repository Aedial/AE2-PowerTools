package com.ae2powertools;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.items.ItemCardsDistributor;
import com.ae2powertools.items.ItemNetworkHealthScanner;
import com.ae2powertools.items.ItemPriorityTuner;


/**
 * Registry for all items in the mod.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public class ItemRegistry {

    public static ItemNetworkHealthScanner NETWORK_HEALTH_SCANNER;
    public static ItemPriorityTuner PRIORITY_TUNER;
    public static ItemCardsDistributor CARDS_DISTRIBUTOR;

    public static void init() {
        NETWORK_HEALTH_SCANNER = new ItemNetworkHealthScanner();
        PRIORITY_TUNER = new ItemPriorityTuner();
        CARDS_DISTRIBUTOR = new ItemCardsDistributor();
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(
            NETWORK_HEALTH_SCANNER,
            PRIORITY_TUNER,
            CARDS_DISTRIBUTOR
        );
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void registerModels(ModelRegistryEvent event) {
        registerItemModel(NETWORK_HEALTH_SCANNER);
        registerItemModel(PRIORITY_TUNER);
        registerItemModel(CARDS_DISTRIBUTOR);
    }

    @SideOnly(Side.CLIENT)
    private static void registerItemModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
