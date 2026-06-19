package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.remotemonitor.GuiRemoteMonitor;
import com.ae2powertools.features.remotemonitor.RemoteMonitorClientState;


/**
 * Server-to-client packet with the currently available network resources for the selector modal.
 */
public class PacketRemoteMonitorContentsSync implements IMessage {

    private long deviceId;
    private List<MonitoredResource> resources;

    public PacketRemoteMonitorContentsSync() {}

    public PacketRemoteMonitorContentsSync(long deviceId, List<MonitoredResource> resources) {
        this.deviceId = deviceId;
        this.resources = resources;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
        int size = buf.readInt();
        this.resources = new ArrayList<>(size);
        for (int i = 0; i < size; i++) this.resources.add(MonitoredResource.readFromBuf(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
        buf.writeInt(this.resources.size());
        for (MonitoredResource resource : this.resources) {
            resource.writeToBuf(buf);
        }
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorContentsSync, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorContentsSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                RemoteMonitorClientState.setSelectorResources(message.deviceId, message.resources);
                GuiRemoteMonitor.receiveSelectorResources(message.deviceId, message.resources);
            });
            return null;
        }
    }
}