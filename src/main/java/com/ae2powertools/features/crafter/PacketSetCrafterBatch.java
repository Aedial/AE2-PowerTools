package com.ae2powertools.features.crafter;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;


/**
 * Packet to set the batch size of the crafter.
 */
public class PacketSetCrafterBatch implements IMessage {

    private BlockPos pos;
    private int batchSize;

    public PacketSetCrafterBatch() {}

    public PacketSetCrafterBatch(BlockPos pos, int batchSize) {
        this.pos = pos;
        this.batchSize = batchSize;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        this.pos = new BlockPos(x, y, z);
        this.batchSize = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(batchSize);
    }

    public static class Handler implements IMessageHandler<PacketSetCrafterBatch, IMessage> {

        @Override
        public IMessage onMessage(PacketSetCrafterBatch message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileAutoCrafter) {
                    TileAutoCrafter crafter = (TileAutoCrafter) te;
                    crafter.setBatchSize(message.batchSize);
                }
            });

            return null;
        }
    }
}
