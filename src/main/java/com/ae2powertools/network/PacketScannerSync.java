package com.ae2powertools.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.features.scanner.ChannelChokepoint;
import com.ae2powertools.features.scanner.ChannelChokepoint.DirectionFlow;
import com.ae2powertools.features.scanner.ChunkLocation;
import com.ae2powertools.features.scanner.IssueLocation;
import com.ae2powertools.features.scanner.MissingChannelDevice;
import com.ae2powertools.features.scanner.NetworkScanner;
import com.ae2powertools.features.scanner.ScanSessionManager;
import com.ae2powertools.features.scanner.ScannerClientState;
import com.ae2powertools.features.scanner.ScannerClientState.ChunkLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.ChokeLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.ConnectionFlowClient;
import com.ae2powertools.features.scanner.ScannerClientState.LoopLocationClient;
import com.ae2powertools.features.scanner.ScannerClientState.MissingDeviceClient;


/**
 * Server -> Client packet to sync network scan results.
 */
public class PacketScannerSync implements IMessage {

    private long deviceId;
    private boolean hasSession;
    private boolean isComplete;
    private String statusMessage;
    private List<LoopLocationData> loopLocations;
    private List<ChunkLocationData> chunkLocations;
    private List<MissingDeviceData> missingDevices;
    private List<ChokeLocationData> chokeLocations;

    /**
     * Data structure for transmitting loop locations.
     */
    private static class LoopLocationData {
        BlockPos pos;
        int dimension;
        String dimensionName;
        String blockName;
        String description;
        boolean isLoaded;

        LoopLocationData() {}

        LoopLocationData(BlockPos pos, int dimension, String dimensionName,
                String blockName, String description, boolean isLoaded) {
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.isLoaded = isLoaded;
        }
    }

    /**
     * Data structure for transmitting unloaded chunk locations.
     */
    private static class ChunkLocationData {
        int chunkX;
        int chunkZ;
        int dimension;
        String dimensionName;

        ChunkLocationData() {}

        ChunkLocationData(int chunkX, int chunkZ, int dimension, String dimensionName) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
        }
    }

    /**
     * Data structure for transmitting missing channel device information.
     */
    private static class MissingDeviceData {
        BlockPos pos;
        int dimension;
        String dimensionName;
        ItemStack itemStack;
        String description;

        MissingDeviceData() {}

        MissingDeviceData(BlockPos pos, int dimension, String dimensionName,
                ItemStack itemStack, String description) {
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.itemStack = itemStack != null ? itemStack.copy() : ItemStack.EMPTY;
            this.description = description;
        }
    }

    /**
     * Data structure for transmitting channel chokepoint locations.
     */
    private static class ChokeLocationData {
        BlockPos pos;
        int dimension;
        String dimensionName;
        String blockName;
        String description;
        int usedChannels;
        int demandedChannels;
        int capacity;
        List<ConnectionFlowData> connectionFlows;

        ChokeLocationData() {
            this.connectionFlows = new ArrayList<>();
        }

        ChokeLocationData(BlockPos pos, int dimension, String dimensionName, String blockName,
                String description, int usedChannels, int demandedChannels, int capacity) {
            this.pos = pos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
            this.blockName = blockName;
            this.description = description;
            this.usedChannels = usedChannels;
            this.demandedChannels = demandedChannels;
            this.capacity = capacity;
            this.connectionFlows = new ArrayList<>();
        }
    }

    /**
     * Data structure for transmitting connection flow information.
     */
    private static class ConnectionFlowData {
        int directionOrdinal; // EnumFacing ordinal, or -1 for internal
        int channels;
        int demandedChannels;
        BlockPos connectedPos;
        String connectedDescription;

        ConnectionFlowData() {}

        ConnectionFlowData(int directionOrdinal, int channels, int demandedChannels,
                BlockPos connectedPos, String connectedDescription) {
            this.directionOrdinal = directionOrdinal;
            this.channels = channels;
            this.demandedChannels = demandedChannels;
            this.connectedPos = connectedPos;
            this.connectedDescription = connectedDescription;
        }
    }

    public PacketScannerSync() {
        this.loopLocations = new ArrayList<>();
        this.chunkLocations = new ArrayList<>();
        this.missingDevices = new ArrayList<>();
        this.chokeLocations = new ArrayList<>();
    }

    public PacketScannerSync(ScanSessionManager.ScanSession session, long deviceId) {
        this.deviceId = deviceId;
        this.hasSession = true;
        this.loopLocations = new ArrayList<>();
        this.chunkLocations = new ArrayList<>();
        this.missingDevices = new ArrayList<>();
        this.chokeLocations = new ArrayList<>();

        if (session != null) {
            NetworkScanner scanner = session.getScanner();
            this.isComplete = scanner.isComplete();
            this.statusMessage = scanner.getStatusMessage();

            // Add loop locations
            for (IssueLocation loc : scanner.getDetectedLoops()) {
                String blockName = loc.getBlockState().getBlock().getLocalizedName();
                loopLocations.add(new LoopLocationData(
                    loc.getPos(),
                    loc.getDimension(),
                    loc.getDimensionName(),
                    blockName,
                    loc.getDescription(),
                    loc.isLoaded()
                ));
            }

            // Add unloaded chunk locations
            for (ChunkLocation chunk : scanner.getUnloadedChunks()) {
                chunkLocations.add(new ChunkLocationData(
                    chunk.getChunkX(),
                    chunk.getChunkZ(),
                    chunk.getDimension(),
                    chunk.getDimensionName()
                ));
            }

            // Add missing channel devices
            for (MissingChannelDevice device : scanner.getMissingDevices()) {
                missingDevices.add(new MissingDeviceData(
                    device.getPos(),
                    device.getDimension(),
                    device.getDimensionName(),
                    device.getItemStack(),
                    device.getDescription()
                ));
            }

            // Add channel chokepoint locations
            for (ChannelChokepoint choke : scanner.getChokepoints()) {
                String blockName = choke.getBlockState().getBlock().getLocalizedName();
                ChokeLocationData chokeData = new ChokeLocationData(
                    choke.getPos(),
                    choke.getDimension(),
                    choke.getDimensionName(),
                    blockName,
                    choke.getDescription(),
                    choke.getUsedChannels(),
                    choke.getDemandedChannels(),
                    choke.getCapacity()
                );

                // Add connection flows
                for (DirectionFlow flow : choke.getConnectionFlows()) {
                    int dirOrdinal = flow.direction != null ? flow.direction.ordinal() : -1;
                    chokeData.connectionFlows.add(new ConnectionFlowData(
                        dirOrdinal,
                        flow.channels,
                        flow.demandedChannels,
                        flow.connectedPos,
                        flow.connectedDescription
                    ));
                }

                chokeLocations.add(chokeData);
            }
        } else {
            this.isComplete = true;
            this.statusMessage = "";
        }
    }

    /**
     * Create a packet indicating no session.
     */
    public static PacketScannerSync noSession(long deviceId) {
        PacketScannerSync packet = new PacketScannerSync();
        packet.deviceId = deviceId;
        packet.hasSession = false;
        packet.isComplete = true;
        packet.statusMessage = "";

        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deviceId = buf.readLong();
        hasSession = buf.readBoolean();
        isComplete = buf.readBoolean();
        statusMessage = ByteBufUtils.readUTF8String(buf);

        // Read loop locations
        int loopCount = buf.readInt();
        loopLocations = new ArrayList<>(loopCount);
        for (int i = 0; i < loopCount; i++) {
            LoopLocationData data = new LoopLocationData();
            data.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            data.dimension = buf.readInt();
            data.dimensionName = ByteBufUtils.readUTF8String(buf);
            data.blockName = ByteBufUtils.readUTF8String(buf);
            data.description = ByteBufUtils.readUTF8String(buf);
            data.isLoaded = buf.readBoolean();
            loopLocations.add(data);
        }

        // Read chunk locations
        int chunkCount = buf.readInt();
        chunkLocations = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            ChunkLocationData data = new ChunkLocationData();
            data.chunkX = buf.readInt();
            data.chunkZ = buf.readInt();
            data.dimension = buf.readInt();
            data.dimensionName = ByteBufUtils.readUTF8String(buf);
            chunkLocations.add(data);
        }

        // Read missing device locations
        int missingCount = buf.readInt();
        missingDevices = new ArrayList<>(missingCount);
        for (int i = 0; i < missingCount; i++) {
            MissingDeviceData data = new MissingDeviceData();
            data.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            data.dimension = buf.readInt();
            data.dimensionName = ByteBufUtils.readUTF8String(buf);
            data.itemStack = ByteBufUtils.readItemStack(buf);
            data.description = ByteBufUtils.readUTF8String(buf);
            missingDevices.add(data);
        }

        // Read chokepoint locations
        int chokeCount = buf.readInt();
        chokeLocations = new ArrayList<>(chokeCount);
        for (int i = 0; i < chokeCount; i++) {
            ChokeLocationData data = new ChokeLocationData();
            data.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            data.dimension = buf.readInt();
            data.dimensionName = ByteBufUtils.readUTF8String(buf);
            data.blockName = ByteBufUtils.readUTF8String(buf);
            data.description = ByteBufUtils.readUTF8String(buf);
            data.usedChannels = buf.readInt();
            data.demandedChannels = buf.readInt();
            data.capacity = buf.readInt();

            // Read connection flows
            int flowCount = buf.readInt();
            data.connectionFlows = new ArrayList<>(flowCount);
            for (int j = 0; j < flowCount; j++) {
                ConnectionFlowData flowData = new ConnectionFlowData();
                flowData.directionOrdinal = buf.readInt();
                flowData.channels = buf.readInt();
                flowData.demandedChannels = buf.readInt();
                flowData.connectedPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
                flowData.connectedDescription = ByteBufUtils.readUTF8String(buf);
                data.connectionFlows.add(flowData);
            }

            chokeLocations.add(data);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(deviceId);
        buf.writeBoolean(hasSession);
        buf.writeBoolean(isComplete);
        ByteBufUtils.writeUTF8String(buf, statusMessage != null ? statusMessage : "");

        // Write loop locations
        buf.writeInt(loopLocations.size());
        for (LoopLocationData data : loopLocations) {
            buf.writeInt(data.pos.getX());
            buf.writeInt(data.pos.getY());
            buf.writeInt(data.pos.getZ());
            buf.writeInt(data.dimension);
            ByteBufUtils.writeUTF8String(buf, data.dimensionName);
            ByteBufUtils.writeUTF8String(buf, data.blockName);
            ByteBufUtils.writeUTF8String(buf, data.description);
            buf.writeBoolean(data.isLoaded);
        }

        // Write chunk locations
        buf.writeInt(chunkLocations.size());
        for (ChunkLocationData data : chunkLocations) {
            buf.writeInt(data.chunkX);
            buf.writeInt(data.chunkZ);
            buf.writeInt(data.dimension);
            ByteBufUtils.writeUTF8String(buf, data.dimensionName);
        }

        // Write missing device locations
        buf.writeInt(missingDevices.size());
        for (MissingDeviceData data : missingDevices) {
            buf.writeInt(data.pos.getX());
            buf.writeInt(data.pos.getY());
            buf.writeInt(data.pos.getZ());
            buf.writeInt(data.dimension);
            ByteBufUtils.writeUTF8String(buf, data.dimensionName);
            ByteBufUtils.writeItemStack(buf, data.itemStack);
            ByteBufUtils.writeUTF8String(buf, data.description);
        }

        // Write chokepoint locations
        buf.writeInt(chokeLocations.size());
        for (ChokeLocationData data : chokeLocations) {
            buf.writeInt(data.pos.getX());
            buf.writeInt(data.pos.getY());
            buf.writeInt(data.pos.getZ());
            buf.writeInt(data.dimension);
            ByteBufUtils.writeUTF8String(buf, data.dimensionName);
            ByteBufUtils.writeUTF8String(buf, data.blockName);
            ByteBufUtils.writeUTF8String(buf, data.description);
            buf.writeInt(data.usedChannels);
            buf.writeInt(data.demandedChannels);
            buf.writeInt(data.capacity);

            // Write connection flows
            buf.writeInt(data.connectionFlows.size());
            for (ConnectionFlowData flowData : data.connectionFlows) {
                buf.writeInt(flowData.directionOrdinal);
                buf.writeInt(flowData.channels);
                buf.writeInt(flowData.demandedChannels);
                buf.writeInt(flowData.connectedPos.getX());
                buf.writeInt(flowData.connectedPos.getY());
                buf.writeInt(flowData.connectedPos.getZ());
                ByteBufUtils.writeUTF8String(buf, flowData.connectedDescription);
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketScannerSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketScannerSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                long deviceId = message.deviceId;

                ScannerClientState.setActiveSession(deviceId, message.hasSession);
                ScannerClientState.setScanComplete(deviceId, message.isComplete);
                ScannerClientState.setStatusMessage(deviceId, message.statusMessage);

                // Set loop locations
                List<LoopLocationClient> clientLoops = new ArrayList<>();
                for (LoopLocationData data : message.loopLocations) {
                    clientLoops.add(new LoopLocationClient(
                        data.pos,
                        data.dimension,
                        data.dimensionName,
                        data.blockName,
                        data.description,
                        data.isLoaded
                    ));
                }
                ScannerClientState.setLoopLocations(deviceId, clientLoops);

                // Set chunk locations
                List<ChunkLocationClient> clientChunks = new ArrayList<>();
                for (ChunkLocationData data : message.chunkLocations) {
                    clientChunks.add(new ChunkLocationClient(
                        data.chunkX,
                        data.chunkZ,
                        data.dimension,
                        data.dimensionName
                    ));
                }
                ScannerClientState.setChunkLocations(deviceId, clientChunks);

                // Set missing device locations
                List<MissingDeviceClient> clientMissing = new ArrayList<>();
                for (MissingDeviceData data : message.missingDevices) {
                    clientMissing.add(new MissingDeviceClient(
                        data.pos,
                        data.dimension,
                        data.dimensionName,
                        data.itemStack,
                        data.description
                    ));
                }
                ScannerClientState.setMissingDevices(deviceId, clientMissing);

                // Set chokepoint locations
                List<ChokeLocationClient> clientChokes = new ArrayList<>();
                for (ChokeLocationData data : message.chokeLocations) {
                    List<ConnectionFlowClient> flows = new ArrayList<>();
                    for (ConnectionFlowData flowData : data.connectionFlows) {
                        flows.add(new ConnectionFlowClient(
                            flowData.directionOrdinal,
                            flowData.channels,
                            flowData.demandedChannels,
                            flowData.connectedPos,
                            flowData.connectedDescription
                        ));
                    }

                    clientChokes.add(new ChokeLocationClient(
                        data.pos,
                        data.dimension,
                        data.dimensionName,
                        data.blockName,
                        data.description,
                        data.usedChannels,
                        data.demandedChannels,
                        data.capacity,
                        flows
                    ));
                }
                ScannerClientState.setChokeLocations(deviceId, clientChokes);
            });

            return null;
        }
    }
}
