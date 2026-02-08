package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.items.ItemNetworkHealthScanner;
import com.ae2powertools.features.scanner.ScanSessionManager;


/**
 * Client -> Server packet to cancel network scan for a specific device.
 */
public class PacketScannerCancel implements IMessage {

    private long deviceId;

    public PacketScannerCancel() {}

    public PacketScannerCancel(long deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deviceId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(deviceId);
    }

    public static class Handler implements IMessageHandler<PacketScannerCancel, IMessage> {

        @Override
        public IMessage onMessage(PacketScannerCancel message, MessageContext ctx) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) return null;

            EntityPlayerMP player = ctx.getServerHandler().player;
            long deviceId = message.deviceId;

            server.addScheduledTask(() -> {
                ScanSessionManager.ScanSession session = ScanSessionManager.getSession(player, deviceId);

                if (session != null) {
                    int nodesProcessed = session.getScanner().getNodesProcessed();
                    boolean wasComplete = session.getScanner().isComplete();

                    ScanSessionManager.endSession(player, deviceId);

                    if (!wasComplete) {
                        player.sendMessage(new TextComponentTranslation(
                            "item.ae2powertools.network_health_scanner.cancelled", nodesProcessed));
                    }

                    // Sync to client that session ended
                    ItemNetworkHealthScanner.syncToClient(player, deviceId);
                }
            });

            return null;
        }
    }
}
