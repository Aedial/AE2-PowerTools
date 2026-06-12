package com.ae2powertools.features.monitor.dependent;


/**
 * Identifies the type of storage monitor host.
 * Used by the shared container/GUI to adapt its behavior and appearance.
 */
public enum MonitorHostType {

    /** ME Storage Level Emitter: emits redstone based on threshold. */
    EMITTER("storage_emitter"),

    /** ME Storage Display: shows resource icon + quantity, with corner colour driven by threshold. */
    DISPLAY("storage_display");

    private final String titleLangKey;

    MonitorHostType(String titleLangKey) {
        this.titleLangKey = titleLangKey;
    }

    public String getTitleLangKey() {
        return "gui.ae2powertools." + titleLangKey + ".title";
    }
}
