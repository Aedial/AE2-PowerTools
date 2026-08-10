package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Client-to-server packet that triggers one immediate poll on a storage monitor host.
 */
public class PacketStorageMonitorPollNow implements IMessage {

    private BlockPos pos;
    private byte side;

    public PacketStorageMonitorPollNow() {}

    public PacketStorageMonitorPollNow(IStorageMonitorHost host) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.side = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeByte(this.side);
    }

    public static class Handler implements IMessageHandler<PacketStorageMonitorPollNow, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageMonitorPollNow message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (host == null) return;

                host.triggerManualPoll();
            });

            return null;
        }
    }
}