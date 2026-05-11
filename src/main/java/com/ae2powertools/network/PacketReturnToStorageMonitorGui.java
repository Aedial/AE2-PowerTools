package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.util.AEPartLocation;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.features.GuiHandler;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Packet sent by the client when the back tab button is clicked in the
 * Storage Emitter polling-rate sub-GUI. Triggers the server to re-open the
 * main Storage Emitter / Display GUI, which guarantees synced container
 * fields land before the first client render frame.
 * <p>
 * Modeled after {@code PacketReturnToCrafterGui} for consistency.
 * <p>
 * Supports both block tiles and cable parts via the side byte (-1 for tiles).
 */
public class PacketReturnToStorageMonitorGui implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;

    public PacketReturnToStorageMonitorGui() {}

    public PacketReturnToStorageMonitorGui(IStorageMonitorHost host) {
        this.pos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.side = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeByte(side);
    }

    public static class Handler implements IMessageHandler<PacketReturnToStorageMonitorGui, IMessage> {

        @Override
        public IMessage onMessage(PacketReturnToStorageMonitorGui message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(world, message.pos, message.side);
                if (host == null) return;

                // Re-open the main GUI: parts use the side-encoded ID so GuiHandler resolves
                // the correct sibling on the cable bus.
                AEPartLocation hostSide = host.getHostSide();
                int guiId = (hostSide == null || hostSide == AEPartLocation.INTERNAL)
                    ? GuiHandler.GUI_STORAGE_MONITOR
                    : GuiHandler.encodePartGuiId(GuiHandler.GUI_PART_STORAGE_MONITOR, hostSide);

                player.openGui(AE2PowerTools.instance, guiId, world,
                    message.pos.getX(), message.pos.getY(), message.pos.getZ());
            });

            return null;
        }
    }
}
