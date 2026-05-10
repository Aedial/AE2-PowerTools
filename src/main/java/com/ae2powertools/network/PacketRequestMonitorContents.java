package com.ae2powertools.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.features.monitor.dependent.IStorageMonitorHost;
import com.ae2powertools.features.monitor.dependent.StorageMonitorHostResolver;


/**
 * Client → server packet requesting the full list of network resources.
 * Triggered when the user opens the content selector modal.
 * Queries the AE2 network directly via the host's shared monitor logic.
 * The server responds with a {@link PacketMonitorContentsSync}.
 */
public class PacketRequestMonitorContents implements IMessage {

    private BlockPos hostPos;
    /** -1 for block-tile hosts, AEPartLocation ordinal (0..5) for cable parts. */
    private byte side;

    public PacketRequestMonitorContents() {}

    public PacketRequestMonitorContents(IStorageMonitorHost host) {
        this.hostPos = host.getHostPos();
        this.side = StorageMonitorHostResolver.encodeSide(host.getHostSide());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hostPos = BlockPos.fromLong(buf.readLong());
        side = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(hostPos.toLong());
        buf.writeByte(side);
    }

    public static class Handler implements IMessageHandler<PacketRequestMonitorContents, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestMonitorContents message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                IStorageMonitorHost host = StorageMonitorHostResolver.resolve(player.world, message.hostPos, message.side);
                if (host == null) return;

                // Query all network resources directly from the AE2 grid
                List<MonitoredResource> resources = host.getMonitorLogic().queryAllNetworkResources();

                PowerToolsNetwork.INSTANCE.sendTo(
                    new PacketMonitorContentsSync(resources), player);
            });

            return null;
        }
    }
}
