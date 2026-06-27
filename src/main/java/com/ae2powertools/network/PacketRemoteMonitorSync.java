package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.remotemonitor.RemoteMonitorClientState;


/**
 * Full state sync for a Remote Storage Monitor session.
 * Carries the selected resources, current polling rate, latest per-slot deltas,
 * and the current per-slot quantities used to render percent-of-total changes.
 */
public class PacketRemoteMonitorSync implements IMessage {

    private long deviceId;
    private int refreshRate;
    private MonitoredResource[] resources;
    private long[] deltas;
    private long[] currentQuantities;

    public PacketRemoteMonitorSync() {}

    public PacketRemoteMonitorSync(long deviceId, int refreshRate, MonitoredResource[] resources, long[] deltas,
            long[] currentQuantities) {
        this.deviceId = deviceId;
        this.refreshRate = refreshRate;
        this.resources = resources;
        this.deltas = deltas;
        this.currentQuantities = currentQuantities;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
        this.refreshRate = buf.readInt();

        int slotCount = buf.readInt();
        this.resources = new MonitoredResource[slotCount];
        this.deltas = new long[slotCount];
        this.currentQuantities = new long[slotCount];
        for (int i = 0; i < slotCount; i++) {
            if (buf.readBoolean()) this.resources[i] = MonitoredResource.readFromBuf(buf);
            this.deltas[i] = buf.readLong();
            this.currentQuantities[i] = buf.readLong();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
        buf.writeInt(this.refreshRate);
        buf.writeInt(this.resources.length);

        for (int i = 0; i < this.resources.length; i++) {
            MonitoredResource resource = this.resources[i];
            buf.writeBoolean(resource != null);
            if (resource != null) resource.writeToBuf(buf);
            buf.writeLong(this.deltas[i]);
            buf.writeLong(this.currentQuantities[i]);
        }
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorSync, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                RemoteMonitorClientState.syncState(
                    message.deviceId,
                    message.refreshRate,
                    message.resources,
                    message.deltas,
                    message.currentQuantities));
            return null;
        }
    }
}