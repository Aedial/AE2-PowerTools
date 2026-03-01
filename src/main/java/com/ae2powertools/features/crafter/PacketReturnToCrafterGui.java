package com.ae2powertools.features.crafter;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.AE2PowerTools;


/**
 * Packet to return to the main crafter GUI from a sub-GUI (batch or speed).
 * This ensures the server opens the GUI properly, so synced values are correct on first frame.
 */
public class PacketReturnToCrafterGui implements IMessage {

    private BlockPos pos;

    public PacketReturnToCrafterGui() {}

    public PacketReturnToCrafterGui(BlockPos pos) {
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

    public static class Handler implements IMessageHandler<PacketReturnToCrafterGui, IMessage> {

        @Override
        public IMessage onMessage(PacketReturnToCrafterGui message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileAutoCrafter) {
                    player.openGui(AE2PowerTools.instance, CrafterGuiHandler.GUI_CRAFTER, world,
                            message.pos.getX(), message.pos.getY(), message.pos.getZ());
                }
            });

            return null;
        }
    }
}
