package com.ae2powertools.network;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.maintainer.ContainerBetterLevelMaintainer;
import com.ae2powertools.features.maintainer.MaintainerEntry;
import com.ae2powertools.features.maintainer.MaintainerEntrySnapshot;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;


/**
 * Server -> Client packet carrying a diff of {@link MaintainerEntrySnapshot}s
 * plus the current {@code openRows} value for the Better Level Maintainer.
 * <p>
 * The packet carries:
 * <ul>
 *     <li>{@code openRows}, the visible row count (always sent so the GUI
 *         can grow/shrink even when no entry data changed).</li>
 *     <li>A {@code Map<entryIndex, snapshot>} containing only entries whose
 *         snapshot actually changed since the last sync.</li>
 * </ul>
 * On {@code addListener} (initial sync) the container sends a full snapshot
 * for every visible slot at once.
 */
public class PacketMaintainerEntrySync implements IMessage {

    private int openRows;
    private Map<Integer, MaintainerEntrySnapshot> snapshots;

    public PacketMaintainerEntrySync() {
        this.openRows = 0;
        this.snapshots = new HashMap<>();
    }

    public PacketMaintainerEntrySync(int openRows, Map<Integer, MaintainerEntrySnapshot> snapshots) {
        this.openRows = openRows;
        this.snapshots = snapshots;
    }

    public int getOpenRows() {
        return openRows;
    }

    public Map<Integer, MaintainerEntrySnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            this.openRows = buf.readInt();
            int count = buf.readShort() & 0xFFFF;
            this.snapshots = new HashMap<>(count);
            for (int i = 0; i < count; i++) {
                int entryIndex = buf.readShort() & 0xFFFF;
                this.snapshots.put(entryIndex, MaintainerEntrySnapshot.readFromBuf(buf));
            }
        } catch (IOException e) {
            // Corrupted packet: leave snapshots empty so the GUI keeps its previous state
            this.snapshots = new HashMap<>();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            buf.writeInt(openRows);
            buf.writeShort(snapshots.size());
            for (Map.Entry<Integer, MaintainerEntrySnapshot> e : snapshots.entrySet()) {
                buf.writeShort(e.getKey());
                e.getValue().writeToBuf(buf);
            }
        } catch (IOException e) {
            // Should not happen for AE item packet writes; if it does, drop the packet payload
        }
    }

    public static class Handler implements IMessageHandler<PacketMaintainerEntrySync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketMaintainerEntrySync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Container container = Minecraft.getMinecraft().player.openContainer;
                if (!(container instanceof ContainerBetterLevelMaintainer)) return;

                ContainerBetterLevelMaintainer c = (ContainerBetterLevelMaintainer) container;
                TileBetterLevelMaintainer tile = c.getMaintainer();

                // Apply each snapshot to the corresponding entry on the client tile.
                // Empty snapshots replace the entry with a fresh one to clear all
                // transient state (state ordinal, error component, etc.).
                for (Map.Entry<Integer, MaintainerEntrySnapshot> e : message.getSnapshots().entrySet()) {
                    int idx = e.getKey();
                    MaintainerEntrySnapshot snap = e.getValue();

                    // Ensure the entry list is large enough for this index
                    while (tile.getEntryListSize() <= idx) tile.appendEmptyEntry();

                    if (snap.isEmpty()) {
                        // Replace with a fresh entry so transient fields (error, state) reset
                        tile.replaceEntry(idx, new MaintainerEntry());
                    } else {
                        MaintainerEntry entry = tile.getEntry(idx);
                        if (entry != null) snap.applyTo(entry);
                    }
                }

                // Apply openRows last so accessors that depend on it see the updated entries.
                tile.setOpenRowsClient(message.getOpenRows());
            });

            return null;
        }
    }
}
