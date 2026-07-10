package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;
import com.ae2powertools.features.monitor.emitter.IEmitterRedstoneHost;


/**
 * Client -> server packet to update an emitter host's configurable redstone
 * strength value.
 */
public class PacketSetEmitterRedstoneStrength implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;
    private int strength;

    public PacketSetEmitterRedstoneStrength() {}

    public PacketSetEmitterRedstoneStrength(IStorageMonitorHost host, int strength) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.strength = strength;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
        strength = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(side);
        buf.writeInt(strength);
    }

    public static class Handler implements IMessageHandler<PacketSetEmitterRedstoneStrength, IMessage> {

        @Override
        public IMessage onMessage(PacketSetEmitterRedstoneStrength message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (!(host instanceof IEmitterRedstoneHost)) return;

                ((IEmitterRedstoneHost) host).setRedstoneStrength(message.strength);
            });

            return null;
        }
    }
}