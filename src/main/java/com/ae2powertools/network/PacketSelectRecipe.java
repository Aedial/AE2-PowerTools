package com.ae2powertools.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.ae2powertools.features.maintainer.MaintainerEntry;
import com.ae2powertools.features.maintainer.TileBetterLevelMaintainer;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;


/**
 * Packet to select a recipe in the recipe selector and set it on an entry.
 * The client overlay will handle reopening the modal dialog.
 */
public class PacketSelectRecipe implements IMessage {

    private BlockPos pos;
    private int entryIndex;
    private IAEItemStack selectedItem;

    public PacketSelectRecipe() {
    }

    public PacketSelectRecipe(BlockPos pos, int entryIndex, IAEItemStack selectedItem) {
        this.pos = pos;
        this.entryIndex = entryIndex;
        this.selectedItem = selectedItem;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        entryIndex = buf.readInt();

        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        selectedItem = AEItemStack.fromNBT(tag);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(entryIndex);

        NBTTagCompound tag = new NBTTagCompound();
        selectedItem.writeToNBT(tag);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketSelectRecipe, IMessage> {
        @Override
        public IMessage onMessage(PacketSelectRecipe message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(message.pos);
                if (!(te instanceof TileBetterLevelMaintainer)) return;

                TileBetterLevelMaintainer maintainer = (TileBetterLevelMaintainer) te;
                MaintainerEntry entry = maintainer.getEntry(message.entryIndex);
                if (entry == null) return;

                // Set the target item
                entry.setTargetItem(message.selectedItem.copy());

                maintainer.updateOpenRows();
                maintainer.markDirty();

            });

            return null;
        }
    }
}
