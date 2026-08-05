package com.ae2powertools.features.monitor.emitter;


/**
 * Controls whether a storage level emitter only provides weak redstone power
 * or also provides strong power to adjacent blocks.
 */
public enum EmitterRedstonePower {

    WEAK(0, "gui.ae2powertools.storage_emitter.redstone_signal.weak", 0),
    STRONG(1, "gui.ae2powertools.storage_emitter.redstone_signal.strong", 1);

    private static final int ICON_INDEX_START = 0 * 16 + 0;

    private final int id;
    private final String langKey;
    private final int iconIndex;

    EmitterRedstonePower(int id, String langKey, int iconIndex) {
        this.id = id;
        this.langKey = langKey;
        this.iconIndex = iconIndex;
    }

    public int getId() {
        return id;
    }

    public String getLangKey() {
        return langKey;
    }

    public int getIconIndex() {
        return ICON_INDEX_START + iconIndex;
    }

    public EmitterRedstonePower next() {
        return this == WEAK ? STRONG : WEAK;
    }

    public static EmitterRedstonePower fromId(int id) {
        return id == STRONG.id ? STRONG : WEAK;
    }
}