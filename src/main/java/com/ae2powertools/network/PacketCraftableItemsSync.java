package com.ae2powertools.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.ae2powertools.features.maintainer.ContainerBetterLevelMaintainer;


/**
 * Packet sent from server to client to sync the list of craftable items.
 * This allows the recipe selector overlay to show available recipes without
 * requiring a round-trip packet.
 */
public class PacketCraftableItemsSync implements IMessage {

    private List<IAEItemStack> craftableItems;

    public PacketCraftableItemsSync() {
        this.craftableItems = new ArrayList<>();
    }

    public PacketCraftableItemsSync(List<IAEItemStack> craftableItems) {
        this.craftableItems = craftableItems;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        craftableItems = new ArrayList<>();
        int count = buf.readInt();

        for (int i = 0; i < count; i++) {
            IAEItemStack stack = AEItemStack.fromPacket(buf);
            if (stack != null) craftableItems.add(stack);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(craftableItems.size());

        for (IAEItemStack stack : craftableItems) {
            try {
                stack.writeToPacket(buf);
            } catch (IOException e) {
                // Should not happen for item stacks
            }
        }
    }

    public List<IAEItemStack> getCraftableItems() {
        return craftableItems;
    }

    public static class Handler implements IMessageHandler<PacketCraftableItemsSync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCraftableItemsSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Container container = Minecraft.getMinecraft().player.openContainer;

                if (container instanceof ContainerBetterLevelMaintainer) {
                    ((ContainerBetterLevelMaintainer) container).setCraftableItems(message.getCraftableItems());
                }
            });

            return null;
        }
    }
}
