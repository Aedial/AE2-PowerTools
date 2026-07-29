package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.features.IWirelessTermHandler;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.remotemonitor.RemoteMonitorSessionManager;
import com.ae2powertools.items.ItemRemoteStorageMonitor;


/**
 * Client-to-server packet that assigns or clears a monitored resource slot.
 */
public class PacketRemoteMonitorSelectSlot implements IMessage {

    private long deviceId;
    private int slotIndex;
    private boolean hasResource;
    private MonitoredResource resource;

    public PacketRemoteMonitorSelectSlot() {}

    public PacketRemoteMonitorSelectSlot(long deviceId, int slotIndex, MonitoredResource resource) {
        this.deviceId = deviceId;
        this.slotIndex = slotIndex;
        this.hasResource = resource != null;
        this.resource = resource;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
        this.slotIndex = buf.readInt();
        this.hasResource = buf.readBoolean();
        if (this.hasResource) this.resource = MonitoredResource.readFromBuf(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
        buf.writeInt(this.slotIndex);
        buf.writeBoolean(this.hasResource);
        if (this.hasResource) this.resource.writeToBuf(buf);
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorSelectSlot, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorSelectSlot message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack stack = ItemRemoteStorageMonitor.getMonitorInInventory(player, message.deviceId);
                if (stack.isEmpty() || !(stack.getItem() instanceof IWirelessTermHandler)) return;

                RemoteMonitorSessionManager.RemoteMonitorSession session = RemoteMonitorSessionManager.getOrCreateSession(
                    (IWirelessTermHandler) stack.getItem(),
                    player,
                    stack,
                    message.deviceId);
                session.setResource((IWirelessTermHandler) stack.getItem(), player, stack, message.slotIndex,
                    message.hasResource ? message.resource : null);
                ItemRemoteStorageMonitor.syncToClient(player, message.deviceId);
            });
            return null;
        }
    }
}