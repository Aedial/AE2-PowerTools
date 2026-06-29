package com.ae2powertools.features.monitor.dependent;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.features.monitor.emitter.EmitterRedstonePower;
import com.ae2powertools.features.monitor.emitter.IEmitterRedstoneHost;


/**
 * Logic for the ME Storage Level Emitter host.
 * Composes {@link MonitorLogic} and tracks redstone emitting state.
 *
 * The emitter emits redstone when the monitor's overall condition is met
 * (AND/OR across all per-entry threshold evaluations).
 */
public class LevelEmitterLogic {

    private static final String NBT_REDSTONE_POWER = "RedstoneSignalStrength";
    private static final String NBT_REDSTONE_STRENGTH = "RedstoneStrength";

    private final MonitorLogic monitorLogic;

    /** Whether the emitter is currently producing a redstone signal */
    private boolean emitting;

    /** Whether the emitter should provide strong power or weak power. */
    private EmitterRedstonePower redstonePower = EmitterRedstonePower.WEAK;

    /** Actual emitted redstone strength while active, from 1 to 15. */
    private int redstoneStrength = IEmitterRedstoneHost.DEFAULT_REDSTONE_STRENGTH;

    public LevelEmitterLogic(MonitorLogic monitorLogic) {
        this.monitorLogic = monitorLogic;
    }

    /**
     * Evaluate the redstone output state based on the dependent's condition.
     *
     * @return true if the emitting state changed (host should notify neighbors)
     */
    public boolean evaluate() {
        boolean shouldEmit = monitorLogic.isConditionMet();

        if (shouldEmit == emitting) return false;

        emitting = shouldEmit;
        return true;
    }

    // --- Getters ---

    public boolean isEmitting() {
        return emitting;
    }

    public boolean emitsStrongSignal() {
        return redstonePower == EmitterRedstonePower.STRONG;
    }

    public EmitterRedstonePower getRedstonePower() {
        return redstonePower;
    }

    public void setRedstonePower(EmitterRedstonePower redstonePower) {
        this.redstonePower = redstonePower == null
            ? EmitterRedstonePower.WEAK
            : redstonePower;
    }

    public int getRedstoneStrength() {
        return redstoneStrength;
    }

    public void setRedstoneStrength(int redstoneStrength) {
        this.redstoneStrength = clampRedstoneStrength(redstoneStrength);
    }

    // --- NBT (delegates to the shared monitor logic; emitting is transient) ---

    public void writeToNBT(NBTTagCompound tag) {
        monitorLogic.writeToNBT(tag);
        tag.setInteger(NBT_REDSTONE_POWER, redstonePower.getId());
        tag.setInteger(NBT_REDSTONE_STRENGTH, redstoneStrength);
    }

    public void readFromNBT(NBTTagCompound tag) {
        monitorLogic.readFromNBT(tag);
        redstonePower = tag.hasKey(NBT_REDSTONE_POWER)
            ? EmitterRedstonePower.fromId(tag.getInteger(NBT_REDSTONE_POWER))
            : EmitterRedstonePower.WEAK;
        redstoneStrength = tag.hasKey(NBT_REDSTONE_STRENGTH)
            ? clampRedstoneStrength(tag.getInteger(NBT_REDSTONE_STRENGTH))
            : IEmitterRedstoneHost.DEFAULT_REDSTONE_STRENGTH;
    }

    public void writeToStream(ByteBuf data) {
        data.writeBoolean(emitting);
    }

    public boolean readFromStream(ByteBuf data) {
        boolean newEmitting = data.readBoolean();
        if (emitting == newEmitting) return false;

        emitting = newEmitting;
        return true;
    }

    public static int clampRedstoneStrength(int redstoneStrength) {
        if (redstoneStrength < IEmitterRedstoneHost.MIN_REDSTONE_STRENGTH) {
            return IEmitterRedstoneHost.MIN_REDSTONE_STRENGTH;
        }

        if (redstoneStrength > IEmitterRedstoneHost.MAX_REDSTONE_STRENGTH) {
            return IEmitterRedstoneHost.MAX_REDSTONE_STRENGTH;
        }

        return redstoneStrength;
    }
}
