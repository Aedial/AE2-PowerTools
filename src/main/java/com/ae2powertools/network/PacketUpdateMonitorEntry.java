package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.dependent.ComparisonMode;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Client -> server packet to update an existing monitored entry's settings
 * (comparison mode, thresholds, enabled flag) without changing its resource.
 *
 * If the index is out of range, the packet is ignored.
 */
public class PacketUpdateMonitorEntry implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;
    private int index;
    private int comparisonId;
    private long threshold;
    private long lowerThreshold;
    private boolean enabled;

    public PacketUpdateMonitorEntry() {}

    public PacketUpdateMonitorEntry(IStorageMonitorHost host, int index, ComparisonMode comparison, long threshold, long lowerThreshold, boolean enabled) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.index = index;
        this.comparisonId = comparison.getId();
        this.threshold = threshold;
        this.lowerThreshold = lowerThreshold;
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
        index = buf.readInt();
        comparisonId = buf.readInt();
        threshold = buf.readLong();
        lowerThreshold = buf.readLong();
        enabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(side);
        buf.writeInt(index);
        buf.writeInt(comparisonId);
        buf.writeLong(threshold);
        buf.writeLong(lowerThreshold);
        buf.writeBoolean(enabled);
    }

    public static class Handler implements IMessageHandler<PacketUpdateMonitorEntry, IMessage> {

        @Override
        public IMessage onMessage(PacketUpdateMonitorEntry message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (host == null) return;
                if (message.index < 0 || message.index >= host.getEntries().size()) return;

                // Replace the entry in-place, preserving the resource but updating its settings.
                MonitoredEntry old = host.getEntries().get(message.index);
                MonitoredEntry updated = new MonitoredEntry(
                    old.getResource(),
                    ComparisonMode.fromId(message.comparisonId),
                    message.threshold,
                    message.lowerThreshold,
                    message.enabled
                );

                host.setEntry(message.index, updated);
            });

            return null;
        }
    }
}
