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
 * Packet to toggle the enabled state of a crafter entry.
 */
public class PacketToggleCrafterEntry implements IMessage {

    private BlockPos pos;
    private int entryIndex;

    public PacketToggleCrafterEntry() {}

    public PacketToggleCrafterEntry(BlockPos pos, int entryIndex) {
        this.pos = pos;
        this.entryIndex = entryIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        this.pos = new BlockPos(x, y, z);
        this.entryIndex = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(entryIndex);
    }

    public static class Handler implements IMessageHandler<PacketToggleCrafterEntry, IMessage> {

        @Override
        public IMessage onMessage(PacketToggleCrafterEntry message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileAutoCrafter) {
                    TileAutoCrafter crafter = (TileAutoCrafter) te;
                    crafter.toggleEntry(message.entryIndex);
                }
            });

            return null;
        }
    }
}
