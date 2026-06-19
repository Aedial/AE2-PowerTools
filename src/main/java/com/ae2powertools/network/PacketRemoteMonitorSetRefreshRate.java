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
 * Client-to-server packet that updates the polling interval for a device.
 */
public class PacketRemoteMonitorSetRefreshRate implements IMessage {

    private long deviceId;
    private int refreshRate;

    public PacketRemoteMonitorSetRefreshRate() {}

    public PacketRemoteMonitorSetRefreshRate(long deviceId, int refreshRate) {
        this.deviceId = deviceId;
        this.refreshRate = refreshRate;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
        this.refreshRate = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
        buf.writeInt(this.refreshRate);
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorSetRefreshRate, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorSetRefreshRate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack stack = ItemRemoteStorageMonitor.findMonitorByDeviceId(player, message.deviceId);
                if (stack.isEmpty() || !(stack.getItem() instanceof IWirelessTermHandler)) return;

                RemoteMonitorSessionManager.RemoteMonitorSession session = RemoteMonitorSessionManager.getOrCreateSession(
                    (IWirelessTermHandler) stack.getItem(),
                    player,
                    stack,
                    message.deviceId);
                session.noteSyncRequest((IWirelessTermHandler) stack.getItem(), player, stack);
                session.setRefreshRate((IWirelessTermHandler) stack.getItem(), player, stack, message.refreshRate);
                ItemRemoteStorageMonitor.syncToClient(player, message.deviceId);
            });
            return null;
        }
    }
}