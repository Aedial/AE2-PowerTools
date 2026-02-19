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

import com.ae2powertools.features.maintainer.BlockBetterLevelMaintainer;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;


/**
 * Registry for all blocks in the mod.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public class BlockRegistry {

    public static BlockBetterLevelMaintainer BETTER_LEVEL_MAINTAINER;

    public static void init() {
        BETTER_LEVEL_MAINTAINER = new BlockBetterLevelMaintainer();

        // Register tile entities
        GameRegistry.registerTileEntity(TileBetterLevelMaintainer.class,
                new ResourceLocation(Tags.MODID, "better_level_maintainer"));
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(BETTER_LEVEL_MAINTAINER);
    }

    @SubscribeEvent
    public static void registerItemBlocks(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(createItemBlock(BETTER_LEVEL_MAINTAINER));
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void registerModels(ModelRegistryEvent event) {
        registerBlockModel(BETTER_LEVEL_MAINTAINER);
    }

    private static ItemBlock createItemBlock(Block block) {
        ItemBlock itemBlock = new ItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());

        return itemBlock;
    }

    @SideOnly(Side.CLIENT)
    private static void registerBlockModel(Block block) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
                new ModelResourceLocation(block.getRegistryName(), "inventory"));
    }
}
