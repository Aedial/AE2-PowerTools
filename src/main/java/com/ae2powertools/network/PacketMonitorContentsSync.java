package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.dependent.GuiStorageMonitor;


/**
 * Server → client packet containing the full list of cached resources for the content selector modal.
 * Sent in response to {@link PacketRequestMonitorContents}.
 */
public class PacketMonitorContentsSync implements IMessage {

    private List<MonitoredResource> resources;

    public PacketMonitorContentsSync() {
        this.resources = new ArrayList<>();
    }

    public PacketMonitorContentsSync(List<MonitoredResource> resources) {
        this.resources = resources;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        resources = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            resources.add(MonitoredResource.readFromBuf(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(resources.size());

        for (MonitoredResource resource : resources) {
            resource.writeToBuf(buf);
        }
    }

    public List<MonitoredResource> getResources() {
        return resources;
    }

    public static class Handler implements IMessageHandler<PacketMonitorContentsSync, IMessage> {

        @Override
        public IMessage onMessage(PacketMonitorContentsSync message, MessageContext ctx) {
            // Handle on client thread
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiStorageMonitor.handleContentsSync(message.getResources());
            });

            return null;
        }
    }
}
