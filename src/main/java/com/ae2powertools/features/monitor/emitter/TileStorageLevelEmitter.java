package com.ae2powertools.features.monitor.emitter;

import java.io.IOException;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;

import com.ae2powertools.features.monitor.dependent.MonitorHostType;
import com.ae2powertools.features.monitor.dependent.LevelEmitterLogic;
import com.ae2powertools.features.monitor.dependent.TileStorageMonitorBase;


/**
 * Tile entity for the ME Storage Level Emitter block.
 * Emits redstone when the monitored entries' overall condition is met.
 */
public class TileStorageLevelEmitter extends TileStorageMonitorBase implements IEmitterCardHost {

    private final LevelEmitterLogic emitterLogic;
    private final EmitterUpgradeInventory upgrades = new EmitterUpgradeInventory(this);

    public TileStorageLevelEmitter() {
        this.emitterLogic = new LevelEmitterLogic(monitorLogic);
    }

    @Override
    public UpgradeInventory getUpgradeInventory() {
        return upgrades;
    }

    @Override
    public int getInstalledUpgrades(Upgrades upgrade) {
        return upgrades.getInstalledUpgrades(upgrade);
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
        if (inv != upgrades) return;

        markDirtyAndSave();
        monitorLogic.refresh();
        markForUpdate();
    }

    // --- AE2 Grid ticking ---

    @Override
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull IGridNode node, int ticksSinceLastCall) {
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
        return emitterLogic.isEmitting() ? emitterLogic.getRedstoneStrength() : 0;
    }

    /**
     * Strong power is only exposed in strong-output mode.
     */
    public int getStrongRedstoneSignal() {
        return emitterLogic.isEmitting() && emitterLogic.emitsStrongSignal()
            ? emitterLogic.getRedstoneStrength()
            : 0;
    }

    @Override
    public EmitterRedstonePower getRedstonePower() {
        return emitterLogic.getRedstonePower();
    }

    @Override
    public void setRedstonePower(EmitterRedstonePower signalStrength) {
        if (emitterLogic.getRedstonePower() == signalStrength) return;

        emitterLogic.setRedstonePower(signalStrength);
        markDirtyAndSave();
        notifyOutputChanged(true);
    }

    @Override
    public int getRedstoneStrength() {
        return emitterLogic.getRedstoneStrength();
    }

    @Override
    public void setRedstoneStrength(int strength) {
        int clampedStrength = LevelEmitterLogic.clampRedstoneStrength(strength);
        if (emitterLogic.getRedstoneStrength() == clampedStrength) return;

        emitterLogic.setRedstoneStrength(clampedStrength);
        markDirtyAndSave();
        notifyOutputChanged(emitterLogic.emitsStrongSignal());
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
    public void triggerManualPoll() {
        if (world == null) return;

        monitorLogic.refresh();

        if (emitterLogic.evaluate()) notifyOutputChanged();
    }

    @Override
    public MonitorHostType getHostType() {
        return MonitorHostType.EMITTER;
    }

    // --- NBT ---

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        upgrades.readFromNBT(tag, "upgrades");
        emitterLogic.readFromNBT(tag);
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        upgrades.writeToNBT(tag, "upgrades");
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
