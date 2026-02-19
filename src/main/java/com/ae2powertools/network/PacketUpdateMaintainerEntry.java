package com.ae2powertools.network;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;


/**
 * Packet to update a maintainer entry with new values.
 */
public class PacketUpdateMaintainerEntry implements IMessage {

    private BlockPos pos;
    private int entryIndex;
    @Nullable
    private IAEItemStack targetItem;
    private long targetQuantity;
    private long batchSize;
    private int frequencySeconds;
    private boolean enabled;

    public PacketUpdateMaintainerEntry() {
    }

    public PacketUpdateMaintainerEntry(BlockPos pos, int entryIndex, @Nullable IAEItemStack targetItem,
                                        long targetQuantity, long batchSize, int frequencySeconds, boolean enabled) {
        this.pos = pos;
        this.entryIndex = entryIndex;
        this.targetItem = targetItem;
        this.targetQuantity = targetQuantity;
        this.batchSize = batchSize;
        this.frequencySeconds = frequencySeconds;
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        entryIndex = buf.readInt();

        boolean hasItem = buf.readBoolean();
        if (hasItem) {
            NBTTagCompound tag = ByteBufUtils.readTag(buf);
            targetItem = AEItemStack.fromNBT(tag);
        } else {
            targetItem = null;
        }

        targetQuantity = buf.readLong();
        batchSize = buf.readLong();
        frequencySeconds = buf.readInt();
        enabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(entryIndex);

        boolean hasItem = targetItem != null;
        buf.writeBoolean(hasItem);
        if (hasItem) {
            NBTTagCompound tag = new NBTTagCompound();
            targetItem.writeToNBT(tag);
            ByteBufUtils.writeTag(buf, tag);
        }

        buf.writeLong(targetQuantity);
        buf.writeLong(batchSize);
        buf.writeInt(frequencySeconds);
        buf.writeBoolean(enabled);
    }

    public static class Handler implements IMessageHandler<PacketUpdateMaintainerEntry, IMessage> {
        @Override
        public IMessage onMessage(PacketUpdateMaintainerEntry message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(message.pos);

                if (te instanceof TileBetterLevelMaintainer) {
                    TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;

                    // Update the entry
                    maintainer.setEntry(
                            message.entryIndex,
                            message.targetItem,
                            message.targetQuantity,
                            message.batchSize,
                            message.frequencySeconds
                    );

                    // Update enabled state if needed
                    if (maintainer.getEntry(message.entryIndex) != null) {
                        if (maintainer.getEntry(message.entryIndex).isEnabled() != message.enabled) {
                            maintainer.toggleEntryEnabled(message.entryIndex);
                        }
                    }
                }
            });

            return null;
        }
    }
}
