package com.ae2powertools.network;

import java.util.Collections;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Client → server packet:
 * - If {@code targetIndex} is >= 0 and {@code content} is non-null: replace the resource of
 *   the entry at that index, preserving its comparison/threshold/enabled flag.
 * - If {@code content} is null: clear all entries (legacy "clear" behavior).
 * <p>
 * Carries the side byte (-1 for tiles, AEPartLocation ordinal for parts) so the server
 * can resolve the correct host on a cable bus that may carry multiple monitor parts.
 */
public class PacketSelectMonitorContent implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;
    private boolean hasContent;
    private MonitoredResource content;
    /** >= 0: replace the resource of the entry at that index. The selector always sends an explicit index. */
    private int targetIndex;

    public PacketSelectMonitorContent() {}

    public PacketSelectMonitorContent(IStorageMonitorHost host, MonitoredResource content, int targetIndex) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.hasContent = content != null;
        this.content = content;
        this.targetIndex = targetIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
        hasContent = buf.readBoolean();
        targetIndex = buf.readInt();

        if (hasContent) {
            content = MonitoredResource.readFromBuf(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(side);
        buf.writeBoolean(hasContent);
        buf.writeInt(targetIndex);

        if (hasContent && content != null) {
            content.writeToBuf(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketSelectMonitorContent, IMessage> {

        @Override
        public IMessage onMessage(PacketSelectMonitorContent message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (host == null) return;

                List<MonitoredEntry> entries = host.getEntries();

                // Clear-all action: blow away every slot back to placeholders.
                // setEntries() will re-pad to GRID_CAPACITY internally.
                if (!message.hasContent || message.content == null) {
                    host.setEntries(Collections.emptyList());
                    return;
                }

                if (message.targetIndex < 0 || message.targetIndex >= entries.size()) return;

                // Preserve the slot's existing comparator/threshold/enabled state, the user
                // may have configured those before picking a resource for this slot.
                MonitoredEntry old = entries.get(message.targetIndex);
                MonitoredEntry replaced = new MonitoredEntry(
                    message.content,
                    old.getComparison(),
                    old.getThreshold(),
                    old.isEnabled()
                );

                host.setEntry(message.targetIndex, replaced);
            });

            return null;
        }
    }
}
