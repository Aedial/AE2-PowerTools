package com.ae2powertools.features.crafter;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Server -> Client packet carrying the recipe data for one entry, scoped to the
 * page that the server believes the client is currently viewing.
 * <p>
 * Only sent when the snapshot for the current entry actually changes. The packet
 * is tagged with the entryIndex it describes so a stale packet for a previously
 * selected page is detected and discarded by the client.
 */
public class PacketCrafterRecipeSync implements IMessage {

    private int entryIndex;
    private CrafterRecipeSnapshot snapshot;

    public PacketCrafterRecipeSync() {}

    public PacketCrafterRecipeSync(int entryIndex, CrafterRecipeSnapshot snapshot) {
        this.entryIndex = entryIndex;
        this.snapshot = snapshot;
    }

    public int getEntryIndex() { return entryIndex; }
    public CrafterRecipeSnapshot getSnapshot() { return snapshot; }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entryIndex = buf.readByte() & 0xFF;
        this.snapshot = CrafterRecipeSnapshot.readFromBuf(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            buf.writeByte(entryIndex);
            snapshot.writeToBuf(buf);
        } catch (IOException e) {
            // ignored - see PacketCrafterOverviewSync
        }
    }

    public static class Handler implements IMessageHandler<PacketCrafterRecipeSync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCrafterRecipeSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiAutoCrafter) {
                    ((GuiAutoCrafter) screen).handleRecipeSync(message.getEntryIndex(), message.getSnapshot());
                }
            });
            return null;
        }
    }
}
