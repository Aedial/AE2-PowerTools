package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.remotemonitor.GuiRemoteMonitor;
import com.ae2powertools.features.remotemonitor.RemoteMonitorClientState;


/**
 * Server-to-client packet that opens the Remote Storage Monitor GUI for a device.
 */
public class PacketRemoteMonitorOpenGui implements IMessage {

    private long deviceId;

    public PacketRemoteMonitorOpenGui() {}

    public PacketRemoteMonitorOpenGui(long deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.deviceId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.deviceId);
    }

    public static class Handler implements IMessageHandler<PacketRemoteMonitorOpenGui, IMessage> {

        @Override
        public IMessage onMessage(PacketRemoteMonitorOpenGui message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                RemoteMonitorClientState.setActiveDeviceId(message.deviceId);
                Minecraft.getMinecraft().displayGuiScreen(new GuiRemoteMonitor(message.deviceId));
            });
            return null;
        }
    }
}