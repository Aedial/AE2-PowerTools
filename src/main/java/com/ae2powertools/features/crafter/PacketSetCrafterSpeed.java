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
 * Packet to set the speed (in ticks) of the crafter.
 */
public class PacketSetCrafterSpeed implements IMessage {

    private BlockPos pos;
    private int speedTicks;

    public PacketSetCrafterSpeed() {}

    public PacketSetCrafterSpeed(BlockPos pos, int speedTicks) {
        this.pos = pos;
        this.speedTicks = speedTicks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        this.pos = new BlockPos(x, y, z);
        this.speedTicks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(speedTicks);
    }

    public static class Handler implements IMessageHandler<PacketSetCrafterSpeed, IMessage> {

        @Override
        public IMessage onMessage(PacketSetCrafterSpeed message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileAutoCrafter) {
                    TileAutoCrafter crafter = (TileAutoCrafter) te;
                    crafter.setSpeedTicks(message.speedTicks);
                }
            });

            return null;
        }
    }
}
