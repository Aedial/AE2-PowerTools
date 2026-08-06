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
 * Packet sent by the client when the wrench tab button is clicked in the main
 * Storage Emitter / Display GUI. Triggers the server to open the polling-rate
 * sub-GUI (priority-style screen with +/- buttons).
 * <p>
 * Modeled after {@code PacketOpenCrafterSubGui} for consistency with the rest
 * of the codebase.
 * <p>
 * Supports both block tiles and cable parts: the side byte is -1 for tiles and
 * the AEPartLocation ordinal for parts, mirroring every other monitor packet.
 */
public class PacketOpenStorageMonitorPollingRate implements IMessage {

    private BlockPos pos;
    /** -1 for block-tile hosts, AEPartLocation ordinal for cable parts. */
    private byte side;

    public PacketOpenStorageMonitorPollingRate() {}

    public PacketOpenStorageMonitorPollingRate(IStorageMonitorHost host) {
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

    public static class Handler implements IMessageHandler<PacketOpenStorageMonitorPollingRate, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenStorageMonitorPollingRate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(world, message.pos, message.side);
                if (host == null) return;

                // Open the polling-rate GUI: tiles use the plain ID, parts use a side-encoded
                // ID so GuiHandler resolves THIS part (one cable bus may host several monitor parts).
                AEPartLocation hostSide = host.getHostSide();
                int guiId = (hostSide == null || hostSide == AEPartLocation.INTERNAL)
                    ? GuiHandler.GUI_STORAGE_MONITOR_POLLING_RATE
                    : GuiHandler.encodePartGuiId(GuiHandler.GUI_PART_STORAGE_MONITOR_POLLING_RATE, hostSide);

                player.openGui(AE2PowerTools.instance, guiId, world,
                    message.pos.getX(), message.pos.getY(), message.pos.getZ());
            });

            return null;
        }
    }
}
