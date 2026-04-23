package com.ae2powertools.features.crafter;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Server -> Client packet that informs the client of the current page when a
 * crafter container is opened, BEFORE the vanilla initial slot sync.
 * <p>
 * The server-side container is constructed using {@link TileAutoCrafter#getCurrentPage()},
 * which on the server has the actual page (e.g. 2). The catalyst slots are wired to read
 * from {@code tile.getEntry(entryIndex).getCatalystStack(...)} where {@code entryIndex}
 * matches the current page. On the CLIENT however, the tile's {@code currentPage} field
 * defaults to 0 because it is not synced via chunk/tile updates. So the client constructs
 * its slots pointing at {@code entry[0]}.
 * <p>
 * When vanilla's {@link net.minecraft.network.play.server.SPacketWindowItems} arrives,
 * the catalyst stack from the server's current page would be written into the client's
 * {@code entry[0]} via the slot's item handler ({@code setStackInSlot}). Later, when
 * the user navigates with arrows back to page 1, the leaked stack reappears because
 * {@code entry[0]} on the client now holds it.
 * <p>
 * Sending this packet from {@link ContainerAutoCrafter#addListener} BEFORE
 * {@code super.addListener} ensures the client repoints its slots to the correct entry
 * before the slot data arrives, so writes land in the right per-entry catalyst storage.
 * Both packets travel over the same Netty connection (Forge's SimpleImpl is sent as a
 * vanilla CustomPayload), so FIFO ordering is preserved.
 */
public class PacketCrafterPageInit implements IMessage {

    private int page;

    public PacketCrafterPageInit() {}

    public PacketCrafterPageInit(int page) {
        this.page = page;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.page = buf.readByte() & 0xFF;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(page);
    }

    public static class Handler implements IMessageHandler<PacketCrafterPageInit, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCrafterPageInit message, MessageContext ctx) {
            // Apply on the client thread to keep container state mutations on the main thread,
            // matching how vanilla slot packets are dispatched. This lands BEFORE the scheduled
            // SPacketWindowItems handling because both are queued via addScheduledTask in arrival order.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (!(screen instanceof GuiAutoCrafter)) return;

                GuiAutoCrafter gui = (GuiAutoCrafter) screen;
                ContainerAutoCrafter container = gui.getContainer();
                if (container == null) return;

                // Repoint slots to the server's actual current page BEFORE the initial
                // slot sync arrives. See class javadoc for the full rationale.
                container.setCurrentEntryIndex(message.page);
                gui.acknowledgeServerPage(message.page);
            });
            return null;
        }
    }
}
