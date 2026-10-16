package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Fallback names captured by the importing client.
 */
public class PacketCaptureMonitorDisplayNames implements IMessage {

    private BlockPos pos;
    private byte side;
    private int entriesVersion;
    private List<Integer> entryIndexes;
    private List<String> displayNames;

    public PacketCaptureMonitorDisplayNames() {
        this.entryIndexes = new ArrayList<>();
        this.displayNames = new ArrayList<>();
    }

    public PacketCaptureMonitorDisplayNames(BlockPos pos, byte side, int entriesVersion,
            List<Integer> entryIndexes, List<String> displayNames) {
        this.pos = pos;
        this.side = side;
        this.entriesVersion = entriesVersion;
        this.entryIndexes = new ArrayList<>(entryIndexes);
        this.displayNames = new ArrayList<>(displayNames);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.side = buf.readByte();
        this.entriesVersion = buf.readInt();

        int count = buf.readInt();
        this.entryIndexes = new ArrayList<>(count);
        this.displayNames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.entryIndexes.add(buf.readInt());
            this.displayNames.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeByte(this.side);
        buf.writeInt(this.entriesVersion);
        buf.writeInt(this.entryIndexes.size());

        for (int i = 0; i < this.entryIndexes.size(); i++) {
            buf.writeInt(this.entryIndexes.get(i));
            ByteBufUtils.writeUTF8String(buf, this.displayNames.get(i));
        }
    }

    public static class Handler implements IMessageHandler<PacketCaptureMonitorDisplayNames, IMessage> {

        @Override
        public IMessage onMessage(PacketCaptureMonitorDisplayNames message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.pos, message.side);
                if (host == null || host.getMonitorLogic().getEntriesVersion() != message.entriesVersion) return;

                List<MonitoredEntry> entries = new ArrayList<>(host.getEntries());
                boolean changed = false;
                for (int i = 0; i < message.entryIndexes.size(); i++) {
                    int entryIndex = message.entryIndexes.get(i);
                    if (entryIndex < 0 || entryIndex >= entries.size()) continue;

                    String displayName = message.displayNames.get(i);
                    if (displayName.isEmpty()) continue;

                    MonitoredEntry existing = entries.get(entryIndex);
                    MonitoredResource resource = existing.getResource();
                    if (resource == null || resource.getFallbackDisplayName() != null) continue;

                    MonitoredEntry updated = new MonitoredEntry(
                        resource.withFallbackDisplayName(displayName),
                        existing.getComparison(),
                        existing.getThreshold(),
                        existing.getLowerThreshold(),
                        existing.isEnabled());
                    updated.setLastQuantity(existing.getLastQuantity());
                    updated.setLastConditionMet(existing.isLastConditionMet());
                    entries.set(entryIndex, updated);
                    changed = true;
                }

                if (changed) host.setEntries(entries);
            });

            return null;
        }
    }
}
