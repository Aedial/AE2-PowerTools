package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.features.IWirelessTermHandler;

import com.ae2powertools.features.remotemonitor.RemoteMonitorSessionManager;
import com.ae2powertools.items.ItemRemoteStorageMonitor;


/**
 * Client-to-server packet that triggers one immediate poll on a remote monitor session.
 */
public class PacketRemoteMonitorPollNow implements IMessage {

    private long deviceId;

    public PacketRemoteMonitorPollNow() {}

    public PacketRemoteMonitorPollNow(long deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorPollNow, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorPollNow message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                ItemStack stack = ItemRemoteStorageMonitor.getMonitorInInventory(player, message.deviceId);
                if (stack.isEmpty() || !(stack.getItem() instanceof IWirelessTermHandler)) return;

                IWirelessTermHandler handler = (IWirelessTermHandler) stack.getItem();
                RemoteMonitorSessionManager.RemoteMonitorSession session = RemoteMonitorSessionManager.getOrCreateSession(
                    handler,
                    player,
                    stack,
                    message.deviceId);

                session.triggerManualPoll(handler, player, stack);
                ItemRemoteStorageMonitor.syncToClient(player, message.deviceId);
            });

            return null;
        }
    }
}