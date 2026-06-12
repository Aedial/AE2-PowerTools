package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Client -> server packet to remove a monitored entry by index.
 */
public class PacketRemoveMonitorEntry implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;
    private int index;

    public PacketRemoveMonitorEntry() {}

    public PacketRemoveMonitorEntry(IStorageMonitorHost host, int index) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.index = index;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
        index = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(side);
        buf.writeInt(index);
    }

    public static class Handler implements IMessageHandler<PacketRemoveMonitorEntry, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoveMonitorEntry message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (host == null) return;

                java.util.List<MonitoredEntry> entries = host.getEntries();
                if (message.index < 0 || message.index >= entries.size()) return;

                MonitoredEntry old = entries.get(message.index);
                MonitoredEntry cleared = new MonitoredEntry(
                    null,
                    old.getComparison(),
                    old.getThreshold(),
                    old.getLowerThreshold(),
                    old.isEnabled()
                );
                host.setEntry(message.index, cleared);
            });

            return null;
        }
    }
}
