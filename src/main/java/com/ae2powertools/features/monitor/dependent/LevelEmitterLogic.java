package com.ae2powertools.features.monitor.dependent;

import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.features.monitor.emitter.EmitterRedstoneStrength;


/**
 * Logic for the ME Storage Level Emitter host.
 * Composes {@link MonitorLogic} and tracks redstone emitting state.
 *
 * The emitter emits redstone when the monitor's overall condition is met
 * (AND/OR across all per-entry threshold evaluations).
 */
public class LevelEmitterLogic {

    private static final String NBT_REDSTONE_SIGNAL_STRENGTH = "RedstoneSignalStrength";

    private final MonitorLogic monitorLogic;

    /** Whether the emitter is currently producing a redstone signal */
    private boolean emitting;

    /** Whether the emitter should provide strong power in addition to weak power. */
    private EmitterRedstoneStrength redstoneSignalStrength = EmitterRedstoneStrength.WEAK;

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
        return redstoneSignalStrength == EmitterRedstoneStrength.STRONG;
    }

    public EmitterRedstoneStrength getRedstoneSignalStrength() {
        return redstoneSignalStrength;
    }

    public void setRedstoneSignalStrength(EmitterRedstoneStrength redstoneSignalStrength) {
        this.redstoneSignalStrength = redstoneSignalStrength == null
            ? EmitterRedstoneStrength.WEAK
            : redstoneSignalStrength;
    }

    // --- NBT (delegates to the shared monitor logic; emitting is transient) ---

    public void writeToNBT(NBTTagCompound tag) {
        monitorLogic.writeToNBT(tag);
        tag.setInteger(NBT_REDSTONE_SIGNAL_STRENGTH, redstoneSignalStrength.getId());
    }

    public void readFromNBT(NBTTagCompound tag) {
        monitorLogic.readFromNBT(tag);
        redstoneSignalStrength = tag.hasKey(NBT_REDSTONE_SIGNAL_STRENGTH)
            ? EmitterRedstoneStrength.fromId(tag.getInteger(NBT_REDSTONE_SIGNAL_STRENGTH))
            : EmitterRedstoneStrength.WEAK;
    }
}
