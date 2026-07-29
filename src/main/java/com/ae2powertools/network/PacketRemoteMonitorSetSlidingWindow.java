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
 * Client-to-server packet that updates the sliding window for a device.
 */
public class PacketRemoteMonitorSetSlidingWindow implements IMessage {

    private long deviceId;
    private int slidingWindow;

    public PacketRemoteMonitorSetSlidingWindow() {}

    public PacketRemoteMonitorSetSlidingWindow(long deviceId, int slidingWindow) {
        this.deviceId = deviceId;
        this.slidingWindow = slidingWindow;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
        this.slidingWindow = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
        buf.writeInt(this.slidingWindow);
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorSetSlidingWindow, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorSetSlidingWindow message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack stack = ItemRemoteStorageMonitor.getMonitorInInventory(player, message.deviceId);
                if (stack.isEmpty() || !(stack.getItem() instanceof IWirelessTermHandler)) return;

                RemoteMonitorSessionManager.RemoteMonitorSession session = RemoteMonitorSessionManager.getOrCreateSession(
                    (IWirelessTermHandler) stack.getItem(),
                    player,
                    stack,
                    message.deviceId);
                session.setSlidingWindow((IWirelessTermHandler) stack.getItem(), player, stack, message.slidingWindow);
                ItemRemoteStorageMonitor.syncToClient(player, message.deviceId);
            });
            return null;
        }
    }
}