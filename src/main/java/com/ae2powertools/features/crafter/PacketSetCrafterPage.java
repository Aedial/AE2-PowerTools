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
 * Packet to set the current GUI page for a crafter.
 * This persists the page selection until world reload.
 */
public class PacketSetCrafterPage implements IMessage {

    private BlockPos pos;
    private int page;

    public PacketSetCrafterPage() {}

    public PacketSetCrafterPage(BlockPos pos, int page) {
        this.pos = pos;
        this.page = page;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        this.pos = new BlockPos(x, y, z);
        this.page = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(page);
    }

    public static class Handler implements IMessageHandler<PacketSetCrafterPage, IMessage> {

        @Override
        public IMessage onMessage(PacketSetCrafterPage message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileAutoCrafter) {
                    TileAutoCrafter crafter = (TileAutoCrafter) te;
                    crafter.setCurrentPage(message.page);
                }
            });

            return null;
        }
    }
}
