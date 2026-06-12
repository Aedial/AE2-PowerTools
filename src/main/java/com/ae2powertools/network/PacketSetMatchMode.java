package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.MatchMode;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Client -> server packet to set the AND/OR match mode on a monitor host.
 */
public class PacketSetMatchMode implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;
    private int matchModeId;

    public PacketSetMatchMode() {}

    public PacketSetMatchMode(IStorageMonitorHost host, MatchMode mode) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.matchModeId = mode.getId();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
        matchModeId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(side);
        buf.writeInt(matchModeId);
    }

    public static class Handler implements IMessageHandler<PacketSetMatchMode, IMessage> {

        @Override
        public IMessage onMessage(PacketSetMatchMode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (host == null) return;

                host.setMatchMode(MatchMode.fromId(message.matchModeId));
            });

            return null;
        }
    }
}
