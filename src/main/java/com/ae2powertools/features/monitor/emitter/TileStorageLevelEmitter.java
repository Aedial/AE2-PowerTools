package com.ae2powertools.features.monitor.emitter;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.util.Platform;

import com.ae2powertools.features.monitor.dependent.MonitorHostType;
import com.ae2powertools.features.monitor.dependent.LevelEmitterLogic;
import com.ae2powertools.features.monitor.dependent.TileStorageMonitorBase;


/**
 * Tile entity for the ME Storage Level Emitter block.
 * Emits redstone when the monitored entries' overall condition is met.
 */
public class TileStorageLevelEmitter extends TileStorageMonitorBase implements IEmitterRedstoneStrengthHost {

    private final LevelEmitterLogic emitterLogic;

    public TileStorageLevelEmitter() {
        this.emitterLogic = new LevelEmitterLogic(monitorLogic);
    }

    // --- AE2 Grid ticking ---

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (world == null) return TickRateModulation.IDLE;

        monitorLogic.refresh();
        if (emitterLogic.evaluate()) notifyOutputChanged();

        return TickRateModulation.SAME;
    }

    // --- Redstone output ---

    /**
     * Weak power is always available while the emitter is active so redstone wire
     * keeps working in both modes.
     */
    public int getWeakRedstoneSignal() {
        // TODO: Make strength configurable (default 15, but should allow 1-15)
        //       Main issue is the UX of configuring that in the GUI
        return emitterLogic.isEmitting() ? 15 : 0;
    }

    /**
     * Strong power is only exposed in strong-output mode.
     */
    public int getStrongRedstoneSignal() {
        return emitterLogic.isEmitting() && emitterLogic.emitsStrongSignal() ? 15 : 0;
    }

    @Override
    public EmitterRedstoneStrength getRedstoneSignalStrength() {
        return emitterLogic.getRedstoneSignalStrength();
    }

    @Override
    public void setRedstoneSignalStrength(EmitterRedstoneStrength signalStrength) {
        if (emitterLogic.getRedstoneSignalStrength() == signalStrength) return;

        emitterLogic.setRedstoneSignalStrength(signalStrength);
        markDirtyAndSave();
        notifyOutputChanged(true);
    }

    public boolean isOn() {
        return emitterLogic.isEmitting();
    }

    // --- IMonitorLogicHost ---

    @Override
    public void onConditionChanged(boolean oldMet, boolean newMet) {
        if (emitterLogic.evaluate()) notifyOutputChanged(emitterLogic.emitsStrongSignal());
    }

    @Override
    public MonitorHostType getHostType() {
        return MonitorHostType.EMITTER;
    }

    // --- NBT ---

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        emitterLogic.readFromNBT(tag);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        emitterLogic.writeToNBT(tag);
        return tag;
    }

    @Override
    protected void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        emitterLogic.writeToStream(data);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        return emitterLogic.readFromStream(data) || changed;
    }

    private void notifyOutputChanged() {
        notifyOutputChanged(emitterLogic.emitsStrongSignal());
    }

    private void notifyOutputChanged(boolean includeStrongPropagation) {
        if (world == null) return;

        markForUpdate();  // re-render block for redstone state visuals

        // Propagate redstone updates to neighbors so they can react to the new signal strength
        Platform.notifyBlocksOfNeighbors(world, pos);

        if (!includeStrongPropagation) return;

        // Strong power can flow through an adjacent solid block, so the block at each face
        // also needs its own neighbor update to wake anything attached beyond it.
        for (EnumFacing face : EnumFacing.VALUES) {
            Platform.notifyBlocksOfNeighbors(world, pos.offset(face));
        }
    }
}
