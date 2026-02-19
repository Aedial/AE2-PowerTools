package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.features.maintainer.GuiHandler;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;


/**
 * Packet to open (or reopen) the main maintainer GUI.
 * Used when closing a modal to return to the main GUI.
 */
public class PacketOpenMaintainerGui implements IMessage {

    private BlockPos pos;

    public PacketOpenMaintainerGui() {
    }

    public PacketOpenMaintainerGui(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
    }

    public static class Handler implements IMessageHandler<PacketOpenMaintainerGui, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenMaintainerGui message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(message.pos);
                if (!(te instanceof TileBetterLevelMaintainer)) return;

                player.openGui(AE2PowerTools.instance, GuiHandler.GUI_MAINTAINER,
                        player.world, message.pos.getX(), message.pos.getY(), message.pos.getZ());
            });

            return null;
        }
    }
}
