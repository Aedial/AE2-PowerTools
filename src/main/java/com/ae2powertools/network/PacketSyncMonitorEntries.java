package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.dependent.GuiStorageMonitor;


/**
 * Server -> client packet pushing the full Storage Emitter / Display entry list
 * (resource + comparison + threshold + enabled flag for each entry).
 * <p>
 * Sent from {@link com.ae2powertools.features.monitor.dependent.ContainerStorageMonitor}
 * inside detectAndSendChanges() whenever the host's entry-version counter has
 * changed since the last sync, i.e. whenever an entry is added, removed,
 * replaced, or otherwise mutated server-side.
 * <p>
 * This is what makes the GUI update after the user picks an item in the selector
 * or cycles a comparator - the live per-entry quantity / condition flags are
 * sent separately by {@link PacketStorageEntryStateSync}, but the underlying
 * entry list itself is heavyweight and only sent when actually changed.
 */
public class PacketSyncMonitorEntries implements IMessage {

    private List<MonitoredEntry> entries;

    public PacketSyncMonitorEntries() {
        this.entries = new ArrayList<>();
    }

    public PacketSyncMonitorEntries(List<MonitoredEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(MonitoredEntry.readFromBuf(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entries.size());
        for (MonitoredEntry entry : entries) entry.writeToBuf(buf);
    }

    public List<MonitoredEntry> getEntries() {
        return entries;
    }

    public static class Handler implements IMessageHandler<PacketSyncMonitorEntries, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncMonitorEntries message, MessageContext ctx) {
            // Hand off to the client thread; the open GUI (if any) consumes the data.
            Minecraft.getMinecraft().addScheduledTask(() ->
                GuiStorageMonitor.handleEntriesSync(message.getEntries()));

            return null;
        }
    }
}
