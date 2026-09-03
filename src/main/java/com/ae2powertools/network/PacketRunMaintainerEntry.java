package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;


/**
 * Packet to force a single maintainer entry to run immediately.
 */
public class PacketRunMaintainerEntry implements IMessage {

    private BlockPos pos;
    private int entryIndex;

    public PacketRunMaintainerEntry() {
    }

    public PacketRunMaintainerEntry(BlockPos pos, int entryIndex) {
        this.pos = pos;
        this.entryIndex = entryIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        entryIndex = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(entryIndex);
    }

    public static class Handler implements IMessageHandler<PacketRunMaintainerEntry, IMessage> {
        @Override
        public IMessage onMessage(PacketRunMaintainerEntry message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(message.pos);
                if (!(te instanceof TileBetterLevelMaintainer)) return;

                TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
                maintainer.runEntryNow(message.entryIndex);
            });

            return null;
        }
    }
}