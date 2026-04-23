package com.ae2powertools.features.locator;

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


/**
 * Server -> Client packet to sync network component scan results.
 * Transmits all component types with their item representations and locations.
 */
public class PacketLocatorSync implements IMessage {

    private long deviceId;
    private int totalNodes;
    private boolean subnetScanEnabled;
    private List<ComponentTypeData> componentTypes;

    /**
     * Wire data for a single component type.
     */
    private static class ComponentTypeData {
        ItemStack itemStack;
        List<LocationData> locations;

        ComponentTypeData() {
            this.locations = new ArrayList<>();
        }

        ComponentTypeData(ItemStack itemStack, List<LocationData> locations) {
            this.itemStack = itemStack;
            this.locations = locations;
        }
    }

    /**
     * Wire data for a single location.
     */
    private static class LocationData {
        BlockPos pos;
        int dimension;

        LocationData() {}

        LocationData(BlockPos pos, int dimension) {
            this.pos = pos;
            this.dimension = dimension;
        }
    }

    public PacketLocatorSync() {
        this.componentTypes = new ArrayList<>();
    }

    public PacketLocatorSync(long deviceId, ComponentScanner.ScanResult result, boolean subnetScanEnabled) {
        this.deviceId = deviceId;
        this.totalNodes = result.totalNodes;
        this.subnetScanEnabled = subnetScanEnabled;
        this.componentTypes = new ArrayList<>();

        for (ComponentScanner.ComponentType type : result.componentTypes) {
            List<LocationData> locations = new ArrayList<>();
            for (ComponentScanner.ComponentLocation loc : type.locations) {
                locations.add(new LocationData(loc.pos, loc.dimension));
            }

            componentTypes.add(new ComponentTypeData(type.itemStack, locations));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        deviceId = buf.readLong();
        totalNodes = buf.readInt();
        subnetScanEnabled = buf.readBoolean();

        int typeCount = buf.readInt();
        componentTypes = new ArrayList<>(typeCount);

        for (int i = 0; i < typeCount; i++) {
            ComponentTypeData type = new ComponentTypeData();
            type.itemStack = ByteBufUtils.readItemStack(buf);

            int locCount = buf.readInt();
            type.locations = new ArrayList<>(locCount);

            for (int j = 0; j < locCount; j++) {
                LocationData loc = new LocationData();
                loc.pos = BlockPos.fromLong(buf.readLong());
                loc.dimension = buf.readInt();
                type.locations.add(loc);
            }

            componentTypes.add(type);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(deviceId);
        buf.writeInt(totalNodes);
        buf.writeBoolean(subnetScanEnabled);
        buf.writeInt(componentTypes.size());

        for (ComponentTypeData type : componentTypes) {
            ByteBufUtils.writeItemStack(buf, type.itemStack);
            buf.writeInt(type.locations.size());

            for (LocationData loc : type.locations) {
                buf.writeLong(loc.pos.toLong());
                buf.writeInt(loc.dimension);
            }
        }
    }

    /**
     * Handler processes the packet on the client.
     */
    public static class Handler implements IMessageHandler<PacketLocatorSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketLocatorSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));

            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleClient(PacketLocatorSync message) {
            LocatorClientState.setActiveDeviceId(message.deviceId);

            // Convert wire data to client-side state
            List<LocatorClientState.ComponentTypeClient> types = new ArrayList<>();

            for (ComponentTypeData typeData : message.componentTypes) {
                List<LocatorClientState.ComponentLocationClient> locations = new ArrayList<>();

                for (LocationData locData : typeData.locations) {
                    locations.add(new LocatorClientState.ComponentLocationClient(
                        locData.pos, locData.dimension
                    ));
                }

                types.add(new LocatorClientState.ComponentTypeClient(
                    typeData.itemStack, locations
                ));
            }

            LocatorClientState.updateData(message.deviceId, types, message.totalNodes, message.subnetScanEnabled);

            // Open the GUI after receiving data
            Minecraft.getMinecraft().displayGuiScreen(new GuiComponentLocator());
        }
    }
}
