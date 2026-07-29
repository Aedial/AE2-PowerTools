package com.ae2powertools.features.locator;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.items.ItemNetworkComponentLocator;


/**
 * Client -> Server packet to toggle the subnet scanning flag on the held Network Component Locator.
 * The server modifies the item's NBT to persist the toggle state.
 */
public class PacketLocatorToggleSubnet implements IMessage {

    public PacketLocatorToggleSubnet() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No data needed - server finds the held locator itself
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No data needed
    }

    public static class Handler implements IMessageHandler<PacketLocatorToggleSubnet, IMessage> {

        @Override
        public IMessage onMessage(PacketLocatorToggleSubnet message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handleServer(player));

            return null;
        }

        private static void handleServer(EntityPlayerMP player) {
            ItemStack locator = ItemNetworkComponentLocator.getHeldLocator(player);
            if (locator.isEmpty()) return;

            ItemNetworkComponentLocator.toggleSubnetScan(locator);
        }
    }
}
