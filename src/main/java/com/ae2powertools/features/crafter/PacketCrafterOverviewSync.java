package com.ae2powertools.features.crafter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Server -> Client packet carrying a diff of {@link CrafterOverviewSnapshot}s.
 * <p>
 * Each packet maps {entryIndex -> snapshot}. The server only includes entries
 * whose snapshot actually changed since the last sync, which is what makes this
 * a "diff" packet. On addListener (initial sync) all 12 entries are sent at once.
 * <p>
 * This replaces the previous monolithic compressed-NBT PacketCrafterStateSync,
 * which was sent every 10 ticks regardless of whether anything changed.
 */
public class PacketCrafterOverviewSync implements IMessage {

    private Map<Integer, CrafterOverviewSnapshot> snapshots;

    public PacketCrafterOverviewSync() {
        this.snapshots = new HashMap<>();
    }

    public PacketCrafterOverviewSync(Map<Integer, CrafterOverviewSnapshot> snapshots) {
        this.snapshots = snapshots;
    }

    public Map<Integer, CrafterOverviewSnapshot> getSnapshots() {
        return snapshots;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readByte() & 0xFF;
        this.snapshots = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            int entryIndex = buf.readByte() & 0xFF;
            this.snapshots.put(entryIndex, CrafterOverviewSnapshot.readFromBuf(buf));
        }

    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            buf.writeByte(snapshots.size());
            for (Map.Entry<Integer, CrafterOverviewSnapshot> e : snapshots.entrySet()) {
                buf.writeByte(e.getKey());
                e.getValue().writeToBuf(buf);
            }
        } catch (IOException e) {
            // Should not happen for AE item packet writes; if it does, drop the packet payload
        }
    }

    public static class Handler implements IMessageHandler<PacketCrafterOverviewSync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCrafterOverviewSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiAutoCrafter) {
                    ((GuiAutoCrafter) screen).handleOverviewSync(message.getSnapshots());
                }
            });
            return null;
        }
    }
}
