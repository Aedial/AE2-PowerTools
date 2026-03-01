package com.ae2powertools.features.crafter;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Packet sent from client to server to request a full state sync.
 * Sent when:
 * - GUI is first opened
 * - Switching to overview mode
 * - Client needs fresh data
 */
public class PacketRequestCrafterSync implements IMessage {

    private BlockPos pos;

    public PacketRequestCrafterSync() {}

    public PacketRequestCrafterSync(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        this.pos = new BlockPos(x, y, z);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
    }

    public static class Handler implements IMessageHandler<PacketRequestCrafterSync, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestCrafterSync message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileAutoCrafter) {
                    TileAutoCrafter crafter = (TileAutoCrafter) te;

                    // Send full state sync packet back to the requesting player
                    PacketCrafterStateSync syncPacket = PacketCrafterStateSync.fromTile(crafter);
                    PowerToolsNetwork.INSTANCE.sendTo(syncPacket, player);
                }
            });

            return null;
        }
    }
}
