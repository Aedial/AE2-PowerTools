package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.items.ItemNetworkHealthScanner;


/**
 * Client -> Server packet to toggle the subnet scanning flag on the held Network Health Scanner.
 * The server modifies the item's NBT so the preference persists across rescans.
 */
public class PacketScannerToggleSubnet implements IMessage {

    public PacketScannerToggleSubnet() {}

    @Override
    public void fromBytes(ByteBuf buf) {
        // No payload needed. The held scanner is resolved on the server.
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No payload needed.
    }

    public static class Handler implements IMessageHandler<PacketScannerToggleSubnet, IMessage> {

        @Override
        public IMessage onMessage(PacketScannerToggleSubnet message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handleServer(player));

            return null;
        }

        private static void handleServer(EntityPlayerMP player) {
            ItemStack scanner = ItemNetworkHealthScanner.getHeldScanner(player);
            if (scanner.isEmpty()) return;

            ItemNetworkHealthScanner.toggleSubnetScan(scanner);
        }
    }
}