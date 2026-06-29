package com.ae2powertools.features.monitor.emitter;


/**
 * Implemented by emitter hosts that can switch implemented redstone power modes and configurable strength.
 */
public interface IEmitterRedstoneHost {

    int MIN_REDSTONE_STRENGTH = 1;
    int MAX_REDSTONE_STRENGTH = 15;
    int DEFAULT_REDSTONE_STRENGTH = MAX_REDSTONE_STRENGTH;

    EmitterRedstonePower getRedstonePower();

    void setRedstonePower(EmitterRedstonePower signalStrength);

    int getRedstoneStrength();

    void setRedstoneStrength(int strength);
}