package com.ae2powertools.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.features.IWirelessTermHandler;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.remotemonitor.RemoteMonitorNetworkHelper;
import com.ae2powertools.items.ItemRemoteStorageMonitor;


/**
 * Client-to-server packet requesting the current selector resource list for a device.
 */
public class PacketRemoteMonitorRequestContents implements IMessage {

    private long deviceId;

    public PacketRemoteMonitorRequestContents() {}

    public PacketRemoteMonitorRequestContents(long deviceId) {
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

    public static class Handler implements IMessageHandler<PacketRemoteMonitorRequestContents, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorRequestContents message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack stack = ItemRemoteStorageMonitor.getMonitorInInventory(player, message.deviceId);
                if (stack.isEmpty() || !(stack.getItem() instanceof IWirelessTermHandler)) return;

                List<MonitoredResource> resources = RemoteMonitorNetworkHelper.queryAllResources(
                    (IWirelessTermHandler) stack.getItem(),
                    player,
                    stack);
                PowerToolsNetwork.INSTANCE.sendTo(new PacketRemoteMonitorContentsSync(message.deviceId, resources), player);
            });
            return null;
        }
    }
}