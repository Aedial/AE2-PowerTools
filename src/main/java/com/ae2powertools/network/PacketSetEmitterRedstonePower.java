package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;
import com.ae2powertools.features.monitor.emitter.EmitterRedstonePower;
import com.ae2powertools.features.monitor.emitter.IEmitterRedstoneHost;


/**
 * Client -> server packet to toggle an emitter host between weak-only and
 * strong redstone output.
 */
public class PacketSetEmitterRedstonePower implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;
    private int signalStrengthId;

    public PacketSetEmitterRedstonePower() {}

    public PacketSetEmitterRedstonePower(IStorageMonitorHost host, EmitterRedstonePower signalStrength) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.signalStrengthId = signalStrength.getId();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
        signalStrengthId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(side);
        buf.writeInt(signalStrengthId);
    }

    public static class Handler implements IMessageHandler<PacketSetEmitterRedstonePower, IMessage> {

        @Override
        public IMessage onMessage(PacketSetEmitterRedstonePower message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (!(host instanceof IEmitterRedstoneHost)) return;

                ((IEmitterRedstoneHost) host).setRedstonePower(
                    EmitterRedstonePower.fromId(message.signalStrengthId));
            });

            return null;
        }
    }
}