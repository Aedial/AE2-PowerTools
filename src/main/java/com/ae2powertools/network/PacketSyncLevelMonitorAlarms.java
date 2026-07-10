package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.alarm.AlarmLocation;
import com.ae2powertools.features.monitor.alarm.LevelMonitorAlarmClientState;


/**
 * Server -> client sync for the local player's currently active subscribed alarms.
 */
public class PacketSyncLevelMonitorAlarms implements IMessage {

    private List<AlarmLocation> alarms;

    public PacketSyncLevelMonitorAlarms() {
        this.alarms = new ArrayList<>();
    }

    public PacketSyncLevelMonitorAlarms(List<AlarmLocation> alarms) {
        this.alarms = new ArrayList<>(alarms);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        this.alarms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) this.alarms.add(AlarmLocation.readFromBuf(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.alarms.size());
        for (AlarmLocation alarm : this.alarms) alarm.writeToBuf(buf);
    }

    public static class Handler implements IMessageHandler<PacketSyncLevelMonitorAlarms, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncLevelMonitorAlarms message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() ->
                LevelMonitorAlarmClientState.setActiveAlarms(message.alarms));
            return null;
        }
    }
}