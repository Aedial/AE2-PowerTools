package com.ae2powertools.features.crafter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Packet containing full crafter state synced from server to client.
 * Uses compressed NBT for efficient transfer of all entry data.
 * 
 * Sent when:
 * - GUI is first opened
 * - Switching to overview mode
 * - Periodically while GUI is open (every ~10 ticks)
 */
public class PacketCrafterStateSync implements IMessage {

    private NBTTagCompound data;

    public PacketCrafterStateSync() {
        this.data = new NBTTagCompound();
    }

    public PacketCrafterStateSync(NBTTagCompound data) {
        this.data = data;
    }

    /**
     * Creates a sync packet from a TileAutoCrafter.
     * Includes all data needed for client-side GUI display.
     */
    public static PacketCrafterStateSync fromTile(TileAutoCrafter tile) {
        NBTTagCompound data = new NBTTagCompound();

        // Global settings
        data.setInteger("speedTicks", tile.getSpeedTicks());
        data.setInteger("batchSize", tile.getBatchSize());
        data.setInteger("effectiveBatchSize", tile.getEffectiveMaxBatchSize());
        data.setInteger("currentPage", tile.getCurrentPage());

        // All entries
        NBTTagList entriesList = new NBTTagList();
        for (int i = 0; i < TileAutoCrafter.ENTRY_COUNT; i++) {
            CrafterEntry entry = tile.getEntry(i);
            NBTTagCompound entryTag = new NBTTagCompound();

            // State
            entryTag.setInteger("state", entry.getState().ordinal());
            entryTag.setBoolean("enabled", entry.isEnabled());

            // Per-entry ignore NBT setting
            entryTag.setBoolean("ignoreNbt", entry.isIgnoreNbt());

            // Metrics
            entryTag.setLong("metricsTotal", entry.getMetricsTotal());
            entryTag.setLong("metricsError", entry.getMetricsError());
            entryTag.setLong("metricsTotalActualCrafted", entry.getMetricsTotalActualCrafted());
            entryTag.setLong("metricsTotalMaxPossible", entry.getMetricsTotalMaxPossible());
            entryTag.setInteger("lastRequestedBatch", entry.getLastRequestedBatchSize());
            entryTag.setInteger("lastActualBatch", entry.getLastActualBatchSize());

            // Display data (output item, input grid) - only if pattern exists
            if (entry.hasPattern() && entry.hasValidRecipeInfo()) {
                entryTag.setBoolean("hasDisplayData", true);

                // Output item
                if (entry.getOutputItem() != null) {
                    NBTTagCompound outputTag = new NBTTagCompound();
                    entry.getOutputItem().createItemStack().writeToNBT(outputTag);
                    entryTag.setTag("output", outputTag);
                }

                // Input grid (9 slots)
                NBTTagList gridList = new NBTTagList();
                appeng.api.storage.data.IAEItemStack[] inputGrid = entry.getInputGrid();
                if (inputGrid != null) {
                    for (int j = 0; j < 9; j++) {
                        NBTTagCompound slotTag = new NBTTagCompound();
                        if (inputGrid[j] != null) inputGrid[j].createItemStack().writeToNBT(slotTag);
                        gridList.appendTag(slotTag);
                    }
                }
                entryTag.setTag("inputGrid", gridList);

                // Catalyst info from recipe
                CrafterRecipeInfo info = entry.getRecipeInfo();
                if (info != null) {
                    NBTTagList catalystList = new NBTTagList();
                    for (CrafterRecipeInfo.IngredientInfo catalyst : info.getCatalystSlots()) {
                        NBTTagCompound catTag = new NBTTagCompound();
                        catTag.setInteger("slot", catalyst.getSlotIndex());
                        if (catalyst.getItem() != null) {
                            NBTTagCompound itemTag = new NBTTagCompound();
                            catalyst.getItem().createItemStack().writeToNBT(itemTag);
                            catTag.setTag("item", itemTag);
                        }
                        catalystList.appendTag(catTag);
                    }
                    entryTag.setTag("catalysts", catalystList);
                }

                // Catalyst inventory (actual items in slots)
                NBTTagList catalystInvList = new NBTTagList();
                for (int j = 0; j < CrafterEntry.CATALYST_SLOTS; j++) {
                    NBTTagCompound slotTag = new NBTTagCompound();
                    net.minecraft.item.ItemStack stack = entry.getCatalystStack(j);
                    if (!stack.isEmpty()) stack.writeToNBT(slotTag);
                    catalystInvList.appendTag(slotTag);
                }
                entryTag.setTag("catalystInventory", catalystInvList);
            } else {
                entryTag.setBoolean("hasDisplayData", false);
            }

            // Error details
            java.util.List<String> errorDetails = entry.getErrorDetails();
            if (!errorDetails.isEmpty()) {
                NBTTagList errorList = new NBTTagList();
                for (String error : errorDetails) {
                    NBTTagCompound errorTag = new NBTTagCompound();
                    errorTag.setString("msg", error);
                    errorList.appendTag(errorTag);
                }
                entryTag.setTag("errors", errorList);
            }

            entriesList.appendTag(entryTag);
        }
        data.setTag("entries", entriesList);

        return new PacketCrafterStateSync(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int length = buf.readInt();
            if (length <= 0) {
                this.data = new NBTTagCompound();
                return;
            }

            try (GZIPInputStream gzis = new GZIPInputStream(new ByteBufInputStream(buf))) {
                this.data = CompressedStreamTools.read(new DataInputStream(gzis));
            }
        } catch (IOException e) {
            this.data = new NBTTagCompound();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        try {
            // Write placeholder for length
            int lengthIndex = buf.writerIndex();
            buf.writeInt(0);

            int startIndex = buf.writerIndex();

            try (GZIPOutputStream gzos = new GZIPOutputStream(new ByteBufOutputStream(buf))) {
                CompressedStreamTools.write(this.data, new DataOutputStream(gzos));
            }

            int endIndex = buf.writerIndex();
            int length = endIndex - startIndex;

            // Go back and write actual length
            buf.setInt(lengthIndex, length);
        } catch (IOException e) {
            // Error compressing data - write 0 length
            buf.writeInt(0);
        }
    }

    public NBTTagCompound getData() {
        return data;
    }

    public static class Handler implements IMessageHandler<PacketCrafterStateSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCrafterStateSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;

                if (screen instanceof GuiAutoCrafter) {
                    ((GuiAutoCrafter) screen).handleStateSync(message.getData());
                }
            });

            return null;
        }
    }
}
