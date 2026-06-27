package com.ae2powertools.features.monitor.emitter;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.ImmutableList;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.util.Platform;

import com.ae2powertools.Tags;
import com.ae2powertools.features.monitor.dependent.MonitorHostType;
import com.ae2powertools.features.monitor.dependent.LevelEmitterLogic;
import com.ae2powertools.features.monitor.dependent.PartStorageMonitorBase;


/**
 * Cable part variant of the ME Storage Level Emitter.
 * Attaches to AE2 cables, uses grid ticking for refresh, provides redstone on its face.
 */
public class PartStorageLevelEmitter extends PartStorageMonitorBase implements IEmitterRedstoneHost {

    // Part model resources
    private static final ResourceLocation MODEL_BASE =
        new ResourceLocation(Tags.MODID, "part/storage_level_emitter_base");
    private static final ResourceLocation MODEL_STATUS_OFF =
        new ResourceLocation(Tags.MODID, "part/storage_level_emitter_off");
    private static final ResourceLocation MODEL_STATUS_ON =
        new ResourceLocation(Tags.MODID, "part/storage_level_emitter_on");

    public static final PartModel MODEL_OFF = new PartModel(MODEL_BASE, MODEL_STATUS_OFF);
    public static final PartModel MODEL_ON = new PartModel(MODEL_BASE, MODEL_STATUS_ON);

    @PartModels
    public static List<IPartModel> getModels() {
        return ImmutableList.of(MODEL_OFF, MODEL_ON);
    }

    private final LevelEmitterLogic emitterLogic;

    public PartStorageLevelEmitter(ItemStack is) {
        super(is);
        this.emitterLogic = new LevelEmitterLogic(monitorLogic);
    }

    // --- Grid ticking ---

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        World world = getHostWorld();
        if (world == null) return TickRateModulation.IDLE;

        monitorLogic.refresh();

        if (emitterLogic.evaluate()) notifyOutputChanged();

        return TickRateModulation.SAME;
    }

    // --- Redstone output (part face only) ---

    @Override
    public int isProvidingStrongPower() {
        return emitterLogic.isEmitting() && emitterLogic.emitsStrongSignal()
            ? emitterLogic.getRedstoneStrength()
            : 0;
    }

    @Override
    public int isProvidingWeakPower() {
        return emitterLogic.isEmitting() ? emitterLogic.getRedstoneStrength() : 0;
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

        // Always notify neighbors, since changing strength may change whether strong power is emitted
        notifyOutputChanged(true);
    }

    @Override
    public int getRedstoneStrength() {
        return emitterLogic.getRedstoneStrength();
    }

    @Override
    public void setRedstoneStrength(int strength) {
        int clampedStrength = Math.max(MIN_REDSTONE_STRENGTH, Math.min(MAX_REDSTONE_STRENGTH, strength));
        if (emitterLogic.getRedstoneStrength() == clampedStrength) return;

        emitterLogic.setRedstoneStrength(clampedStrength);
        markDirtyAndSave();
        notifyOutputChanged(emitterLogic.emitsStrongSignal());
    }

    // --- Part model ---

    @Override
    public IPartModel getStaticModels() {
        // TODO: should show a model with has channels and power / on / off.
        return emitterLogic.isEmitting() ? MODEL_ON : MODEL_OFF;
    }

    // --- Collision box (small emitter shape) ---

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(7, 7, 11, 9, 9, 16);
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

    // --- NBT persistence ---

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        emitterLogic.readFromNBT(tag);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        emitterLogic.writeToNBT(tag);
    }

    // --- Network sync ---

    @Override
    public void writeToStream(ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(emitterLogic.isEmitting());
    }

    @Override
    public boolean readFromStream(ByteBuf data) throws IOException {
        boolean changed = super.readFromStream(data);
        return emitterLogic.readFromStream(data) || changed;
    }

    private void notifyOutputChanged() {
        notifyOutputChanged(emitterLogic.emitsStrongSignal());
    }

    private void notifyOutputChanged(boolean includeStrongPropagation) {
        if (getHost() == null) return;

        TileEntity te = getHost().getTile();
        if (te == null) return;

        Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos());

        if (includeStrongPropagation) {
            Platform.notifyBlocksOfNeighbors(te.getWorld(), te.getPos().offset(getSide().getFacing()));
        }

        getHost().markForUpdate();
    }
}
