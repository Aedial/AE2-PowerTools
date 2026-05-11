package com.ae2powertools.features.monitor.dependent;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.AEBaseTile;
import appeng.util.Platform;

import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.util.PowerStateClientFlags;
import com.ae2powertools.util.TickManagerHelper;


/**
 * Abstract base tile for block-based storage monitoring dependents (Storage Display, Storage Level Emitter).
 * Provides AE2 grid proxy lifecycle, grid-managed ticking, and delegates to {@link MonitorLogic}.
 *
 * Queries the AE2 network directly for resource quantities via the grid proxy.
 * Uses 0.5 AE/t idle power and requires a channel (matching AE2's reporting parts).
 */
public abstract class TileStorageMonitorBase extends AEBaseTile
        implements IGridTickable, IActionHost, IGridProxyable,
                   IMonitorLogicHost, IStorageMonitorHost, IPowerChannelState {

    protected final AENetworkProxy gridProxy;
    protected final MonitorLogic monitorLogic;

    private int clientFlags;

    protected TileStorageMonitorBase() {
        this.gridProxy = new AENetworkProxy(this, "proxy", this.getItemFromTile(this), true);
        // Match AE2 reporting part costs: require channel, 0.5 AE/t idle power
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
        this.gridProxy.setIdlePowerUsage(0.5);
        this.monitorLogic = new MonitorLogic(this);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        int rate = monitorLogic.getRefreshRate();
        return new TickingRequest(rate, rate, false, false);
    }

    @Override
    public World getHostWorld() { return world; }

    @Override
    public BlockPos getHostPos() { return pos; }

    @Override
    public ItemStack getBackButtonStack() {
        if (getBlockType() == null) return ItemStack.EMPTY;

        return new ItemStack(getBlockType());
    }

    /** Block tiles never live on a cable bus side: report {@link AEPartLocation#INTERNAL}. */
    @Override
    public AEPartLocation getHostSide() { return AEPartLocation.INTERNAL; }

    @Override
    public void markDirtyAndSave() { markDirty(); }

    @Override
    public void onRefreshRateChanged() {
        if (gridProxy.isReady()) {
            TickManagerHelper.reRegisterTickable(gridProxy.getNode(), this);
        }
    }

    // --- IStorageMonitorHost delegation ---

    @Override
    public int getRefreshRate() { return monitorLogic.getRefreshRate(); }

    @Override
    public void setRefreshRate(int rate) { monitorLogic.setRefreshRate(rate); }

    @Override
    public List<MonitoredEntry> getEntries() { return monitorLogic.getEntries(); }

    @Override
    public void setEntries(List<MonitoredEntry> entries) { monitorLogic.setEntries(entries); }

    @Override
    public void setEntry(int index, MonitoredEntry entry) { monitorLogic.setEntry(index, entry); }

    @Nullable @Override
    public MonitoredResource getFirstResource() { return monitorLogic.getFirstResource(); }

    @Override
    public MatchMode getMatchMode() { return monitorLogic.getMatchMode(); }

    @Override
    public void setMatchMode(MatchMode mode) { monitorLogic.setMatchMode(mode); }

    @Override
    public boolean isConditionMet() { return monitorLogic.isConditionMet(); }

    @Override
    public long getFirstEntryQuantity() { return monitorLogic.getFirstEntryQuantity(); }

    @Override
    public MonitorLogic getMonitorLogic() { return monitorLogic; }

    // --- AE2 Grid integration ---

    @Override
    public AENetworkProxy getProxy() { return gridProxy; }

    @Override
    public boolean isPowered() {
        if (Platform.isServer()) return gridProxy.isPowered();

        return PowerStateClientFlags.isPowered(clientFlags);
    }

    @Override
    public boolean isActive() {
        if (Platform.isServer()) return gridProxy.isActive();

        return PowerStateClientFlags.isActive(clientFlags);
    }

    @Override
    public DimensionalCoord getLocation() { return new DimensionalCoord(this); }

    @Override
    public void gridChanged() {}

    @MENetworkEventSubscribe
    public void onChannelStateChanged(MENetworkChannelsChanged event) {
        markForUpdate();
    }

    @MENetworkEventSubscribe
    public void onPowerStateChanged(MENetworkPowerStatusChange event) {
        markForUpdate();
    }

    @Override
    public IGridNode getGridNode(AEPartLocation dir) { return gridProxy.getNode(); }

    @Override
    public AECableType getCableConnectionType(AEPartLocation dir) { return AECableType.SMART; }

    @Override
    public void securityBreak() { world.destroyBlock(pos, true); }

    @Override
    public IGridNode getActionableNode() { return gridProxy.getNode(); }

    @Override
    public void validate() { super.validate(); gridProxy.validate(); }

    @Override
    public void invalidate() { super.invalidate(); gridProxy.invalidate(); }

    @Override
    public void onChunkUnload() { super.onChunkUnload(); gridProxy.onChunkUnload(); }

    @Override
    public void onReady() { super.onReady(); gridProxy.onReady(); }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        int oldClientFlags = clientFlags;
        clientFlags = data.readByte() & 0xFF;
        return changed || oldClientFlags != clientFlags;
    }

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeByte(PowerStateClientFlags.collect(gridProxy));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) { super.readFromNBT(tag); gridProxy.readFromNBT(tag); }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) { super.writeToNBT(tag); gridProxy.writeToNBT(tag); return tag; }
}
