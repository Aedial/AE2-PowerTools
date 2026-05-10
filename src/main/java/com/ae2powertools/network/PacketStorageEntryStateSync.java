package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.dependent.GuiStorageMonitor;


/**
 * Server -> client packet pushing the live per-entry state for an open
 * Storage Emitter / Display GUI: each entry's last-looked-up quantity and
 * whether its individual condition is currently met.
 *
 * Sent by {@link com.ae2powertools.features.monitor.dependent.ContainerStorageMonitor}
 * inside detectAndSendChanges() whenever any entry's quantity or condition
 * state has changed since the last sync.
 */
public class PacketStorageEntryStateSync implements IMessage {

    private long[] quantities;
    private boolean[] conditionsMet;

    public PacketStorageEntryStateSync() {
        this.quantities = new long[0];
        this.conditionsMet = new boolean[0];
    }

    public PacketStorageEntryStateSync(long[] quantities, boolean[] conditionsMet) {
        this.quantities = quantities;
        this.conditionsMet = conditionsMet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        quantities = new long[count];
        conditionsMet = new boolean[count];

        for (int i = 0; i < count; i++) {
            quantities[i] = buf.readLong();
            conditionsMet[i] = buf.readBoolean();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(quantities.length);

        for (int i = 0; i < quantities.length; i++) {
            buf.writeLong(quantities[i]);
            buf.writeBoolean(conditionsMet[i]);
        }
    }

    public long[] getQuantities() {
        return quantities;
    }

    public boolean[] getConditionsMet() {
        return conditionsMet;
    }

    public static class Handler implements IMessageHandler<PacketStorageEntryStateSync, IMessage> {

        @Override
        public IMessage onMessage(PacketStorageEntryStateSync message, MessageContext ctx) {
            // Hand off to the client thread; the open GUI (if any) consumes the data.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiStorageMonitor.handleEntryStateSync(message.getQuantities(), message.getConditionsMet());
            });

            return null;
        }
    }
}
