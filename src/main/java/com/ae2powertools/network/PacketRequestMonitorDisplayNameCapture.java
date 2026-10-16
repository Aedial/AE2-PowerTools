package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.client.MonitoredResourceNameResolver;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Fallback-name capture after a server-only monitor import.
 */
public class PacketRequestMonitorDisplayNameCapture implements IMessage {

    private BlockPos pos;
    private byte side;
    private int entriesVersion;
    private List<Integer> entryIndexes;
    private List<MonitoredResource> resources;

    public PacketRequestMonitorDisplayNameCapture() {
        this.entryIndexes = new ArrayList<>();
        this.resources = new ArrayList<>();
    }

    public PacketRequestMonitorDisplayNameCapture(IStorageMonitorHost host) {
        this();

        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
        this.entriesVersion = host.getMonitorLogic().getEntriesVersion();

        List<MonitoredEntry> entries = host.getEntries();
        for (int index = 0; index < entries.size(); index++) {
            MonitoredResource resource = entries.get(index).getResource();
            if (resource == null || resource.getFallbackDisplayName() != null) continue;

            this.entryIndexes.add(index);
            this.resources.add(resource);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.side = buf.readByte();
        this.entriesVersion = buf.readInt();

        int count = buf.readInt();
        this.entryIndexes = new ArrayList<>(count);
        this.resources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            this.entryIndexes.add(buf.readInt());
            this.resources.add(MonitoredResource.readFromBuf(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
        buf.writeByte(this.side);
        buf.writeInt(this.entriesVersion);
        buf.writeInt(this.resources.size());

        for (int i = 0; i < this.resources.size(); i++) {
            buf.writeInt(this.entryIndexes.get(i));
            this.resources.get(i).writeToBuf(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketRequestMonitorDisplayNameCapture, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestMonitorDisplayNameCapture message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                List<String> displayNames = new ArrayList<>(message.resources.size());
                for (MonitoredResource resource : message.resources) {
                    displayNames.add(MonitoredResourceNameResolver.getDisplayName(resource));
                }

                PowerToolsNetwork.INSTANCE.sendToServer(new PacketCaptureMonitorDisplayNames(
                    message.pos,
                    message.side,
                    message.entriesVersion,
                    message.entryIndexes,
                    displayNames));
            });

            return null;
        }
    }
}
