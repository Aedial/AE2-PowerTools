package com.ae2powertools.features.monitor.dependent;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.PartItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.AEBasePart;
import appeng.util.Platform;

import com.ae2powertools.AE2PowerTools;
import com.ae2powertools.features.GuiHandler;
import com.ae2powertools.features.monitor.MonitoredEntry;
import com.ae2powertools.features.monitor.MonitoredResource;
import com.ae2powertools.util.PowerStateClientFlags;
import com.ae2powertools.util.TickManagerHelper;


/**
 * Abstract base cable part for storage monitoring dependents (Storage Display, Storage Level Emitter).
 * Provides AE2 grid proxy lifecycle, grid-managed ticking, and delegates to {@link MonitorLogic}.
 *
 * Queries the AE2 network directly for resource quantities via the grid proxy.
 * Uses 0.5 AE/t idle power and requires a channel (matching AE2's reporting parts).
 */
public abstract class PartStorageMonitorBase extends AEBasePart
        implements IGridTickable, IMonitorLogicHost, IStorageMonitorHost, IPowerChannelState {

    protected final MonitorLogic monitorLogic;

    private int clientFlags;

    protected PartStorageMonitorBase(ItemStack is) {
        super(is);
        this.monitorLogic = new MonitorLogic(this);
        // Match AE2 reporting part costs: require channel, 0.5 AE/t idle power
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(0.5);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        int rate = monitorLogic.getRefreshRate();
        return new TickingRequest(rate, rate, false, false);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) { return 16; }

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!player.world.isRemote) {
            BlockPos hostPos = getHost().getTile().getPos();
            // Open the part GUI via an encoded ID so GuiHandler can resolve THIS part
            // (not whatever sibling part also lives on the same cable bus).
            int encoded = GuiHandler.encodePartGuiId(GuiHandler.GUI_PART_STORAGE_MONITOR, getSide());
            player.openGui(AE2PowerTools.instance, encoded, player.world,
                    hostPos.getX(), hostPos.getY(), hostPos.getZ());
        }

        return true;
    }

    @Override
    public World getHostWorld() {
        TileEntity te = getHost() != null ? getHost().getTile() : null;
        return te != null ? te.getWorld() : null;
    }

    @Override
    public BlockPos getHostPos() {
        return getHost() != null ? getHost().getTile().getPos() : BlockPos.ORIGIN;
    }

    @Override
    public ItemStack getBackButtonStack() {
        return getItemStack(PartItemStack.NETWORK);
    }

    /**
     * Cable-part hosts identify themselves by the side they're attached to. Used to
     * disambiguate from block tiles when the server resolves a host from a packet's
     * BlockPos (a single cable bus can host multiple monitor parts on different sides).
     */
    @Override
    public AEPartLocation getHostSide() {
        return getSide();
    }

    @Override
    public AENetworkProxy getProxy() {
        return super.getProxy();
    }

    @Override
    public boolean isPowered() {
        if (Platform.isServer()) return getProxy().isPowered();

        return PowerStateClientFlags.isPowered(clientFlags);
    }

    @Override
    public boolean isActive() {
        if (Platform.isServer()) return getProxy().isActive();

        return PowerStateClientFlags.isActive(clientFlags);
    }

    @MENetworkEventSubscribe
    public void onChannelStateChanged(MENetworkChannelsChanged event) {
        if (getHost() != null) getHost().markForUpdate();
    }

    @MENetworkEventSubscribe
    public void onPowerStateChanged(MENetworkPowerStatusChange event) {
        if (getHost() != null) getHost().markForUpdate();
    }

    @Override
    public void markDirtyAndSave() {
        if (getHost() == null) return;
        getHost().markForSave();
    }

    @Override
    public void onRefreshRateChanged() {
        if (getProxy().isReady()) {
            TickManagerHelper.reRegisterTickable(getProxy().getNode(), this);
        }
    }

    // --- IStorageMonitorHost delegation ---

    @Override public int getRefreshRate() { return monitorLogic.getRefreshRate(); }
    @Override public void setRefreshRate(int rate) { monitorLogic.setRefreshRate(rate); }

    @Override public List<MonitoredEntry> getEntries() { return monitorLogic.getEntries(); }
    @Override public void setEntries(List<MonitoredEntry> entries) { monitorLogic.setEntries(entries); }
    @Override public void setEntry(int index, MonitoredEntry entry) { monitorLogic.setEntry(index, entry); }

    @Nullable @Override public MonitoredResource getFirstResource() { return monitorLogic.getFirstResource(); }

    @Override public MatchMode getMatchMode() { return monitorLogic.getMatchMode(); }
    @Override public void setMatchMode(MatchMode mode) { monitorLogic.setMatchMode(mode); }

    @Override public boolean isHysteresisEnabled() { return monitorLogic.isHysteresisEnabled(); }
    @Override public void setHysteresisEnabled(boolean hysteresisEnabled) { monitorLogic.setHysteresisEnabled(hysteresisEnabled); }

    @Override public boolean isConditionMet() { return monitorLogic.isConditionMet(); }
    @Override public long getFirstEntryQuantity() { return monitorLogic.getFirstEntryQuantity(); }
    @Override public MonitorLogic getMonitorLogic() { return monitorLogic; }

    @Override
    public void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeByte(PowerStateClientFlags.collect(getProxy()));
    }

    @Override
    public boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        int oldClientFlags = clientFlags;
        clientFlags = data.readByte() & 0xFF;
        return changed || oldClientFlags != clientFlags;
    }
}
