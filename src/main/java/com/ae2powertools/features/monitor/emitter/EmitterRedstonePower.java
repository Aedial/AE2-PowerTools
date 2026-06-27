package com.ae2powertools.features.monitor.emitter;


/**
 * Controls whether a storage level emitter only provides weak redstone power
 * or also provides strong power to adjacent blocks.
 */
public enum EmitterRedstonePower {

    WEAK(0, "gui.ae2powertools.storage_emitter.redstone_signal.weak"),
    STRONG(1, "gui.ae2powertools.storage_emitter.redstone_signal.strong");

    private final int id;
    private final String langKey;

    EmitterRedstonePower(int id, String langKey) {
        this.id = id;
        this.langKey = langKey;
    }

    public int getId() {
        return id;
    }

    public String getLangKey() {
        return langKey;
    }

    public EmitterRedstonePower next() {
        return this == WEAK ? STRONG : WEAK;
    }

    public static EmitterRedstonePower fromId(int id) {
        return id == STRONG.id ? STRONG : WEAK;
    }
}