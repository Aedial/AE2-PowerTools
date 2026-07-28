package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.remotemonitor.RemoteMonitorClientState;


/**
 * Full state sync for a Remote Storage Monitor session.
 * Carries the selected resources, refresh interval, sliding window,
 * latest per-slot deltas, and the current per-slot quantities.
 */
public class PacketRemoteMonitorSync implements IMessage {

    private long deviceId;
    private int refreshRate;
    private int slidingWindow;
    private MonitoredResource[] resources;
    private long[] deltas;
    private long[] currentQuantities;

    public PacketRemoteMonitorSync() {}

    public PacketRemoteMonitorSync(long deviceId, int refreshRate, int slidingWindow, MonitoredResource[] resources,
            long[] deltas, long[] currentQuantities) {
        this.deviceId = deviceId;
        this.refreshRate = refreshRate;
        this.slidingWindow = slidingWindow;
        this.resources = resources;
        this.deltas = deltas;
        this.currentQuantities = currentQuantities;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
        this.refreshRate = buf.readInt();
        this.slidingWindow = buf.readInt();

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
        buf.writeInt(this.slidingWindow);
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
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketRemoteMonitorSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));

            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(PacketRemoteMonitorSync message) {
            RemoteMonitorClientState.syncState(
                message.deviceId,
                message.refreshRate,
                message.slidingWindow,
                message.resources,
                message.deltas,
                message.currentQuantities);
        }
    }
}