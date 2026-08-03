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
 * Client-to-server request for the latest Remote Storage Monitor snapshot.
 * Visible client surfaces pull the current server-tracked deltas on demand.
 */
public class PacketRemoteMonitorRequestSync implements IMessage {

    private long deviceId;
    private boolean forceSync;

    public PacketRemoteMonitorRequestSync() {}

    public PacketRemoteMonitorRequestSync(long deviceId, boolean forceSync) {
        this.deviceId = deviceId;
        this.forceSync = forceSync;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
        this.forceSync = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
        buf.writeBoolean(this.forceSync);
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorRequestSync, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorRequestSync message, MessageContext ctx) {
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

                ItemRemoteStorageMonitor.syncToClient(player, session.getDeviceId());
            });
            return null;
        }
    }
}