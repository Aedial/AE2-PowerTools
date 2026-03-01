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
 * Packet to open a sub-GUI (batch or speed configuration) for the crafter.
 */
public class PacketOpenCrafterSubGui implements IMessage {

    public enum SubGui {
        BATCH,
        SPEED
    }

    private BlockPos pos;
    private SubGui subGui;

    public PacketOpenCrafterSubGui() {}

    public PacketOpenCrafterSubGui(BlockPos pos, SubGui subGui) {
        this.pos = pos;
        this.subGui = subGui;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        this.pos = new BlockPos(x, y, z);
        this.subGui = SubGui.values()[buf.readInt()];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(subGui.ordinal());
    }

    public static class Handler implements IMessageHandler<PacketOpenCrafterSubGui, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenCrafterSubGui message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.world;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = world.getTileEntity(message.pos);
                if (te instanceof TileAutoCrafter) {
                    int guiId;
                    if (message.subGui == SubGui.BATCH) {
                        guiId = CrafterGuiHandler.GUI_CRAFTER_BATCH;
                    } else {
                        guiId = CrafterGuiHandler.GUI_CRAFTER_SPEED;
                    }

                    player.openGui(AE2PowerTools.instance, guiId, world,
                            message.pos.getX(), message.pos.getY(), message.pos.getZ());
                }
            });

            return null;
        }
    }
}
