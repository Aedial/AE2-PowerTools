package com.ae2powertools.features.monitor.emitter;


/**
 * Implemented by emitter hosts that can switch between weak-only and strong
 * redstone output modes.
 */
public interface IEmitterRedstoneStrengthHost {

    EmitterRedstoneStrength getRedstoneSignalStrength();

    void setRedstoneSignalStrength(EmitterRedstoneStrength signalStrength);
}