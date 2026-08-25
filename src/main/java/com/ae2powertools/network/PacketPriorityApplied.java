package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.client.BlockHighlightRenderer;


/**
 * Packet sent from server to client when priority is successfully applied via tuner.
 * Triggers a green highlight effect on the block.
 */
public class PacketPriorityApplied implements IMessage {

    private BlockPos pos;
    private int dimension;
    private int priority;

    public PacketPriorityApplied() {
    }

    public PacketPriorityApplied(BlockPos pos, int dimension, int priority) {
        this.pos = pos;
        this.dimension = dimension;
        this.priority = priority;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.dimension = buf.readInt();
        this.priority = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(dimension);
        buf.writeInt(priority);
    }

    public static class Handler implements IMessageHandler<PacketPriorityApplied, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketPriorityApplied message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                // Only process if player is in the same dimension
                if (Minecraft.getMinecraft().player.dimension != message.dimension) return;

                // Add green highlight that fades over 5 seconds
                BlockHighlightRenderer.addPriorityHighlight(message.pos, 5000);

                // Show success message
                String msg = I18n.format(
                    "ae2powertools.priority.applied",
                    message.priority,
                    message.pos.getX(), message.pos.getY(), message.pos.getZ()
                );
                Minecraft.getMinecraft().player.sendMessage(new TextComponentString(msg));
            });

            return null;
        }
    }
}
