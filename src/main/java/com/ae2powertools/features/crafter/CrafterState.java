package com.ae2powertools.features.crafter;


/**
 * Represents the current state of a crafter recipe entry.
 * Used for visual feedback in the GUI.
 */
public enum CrafterState {
    /**
     * Entry has no pattern set.
     */
    NO_PATTERN(0x00000000, 0x808080),

    /**
     * Recipe is disabled by user (pattern exists but manually disabled).
     */
    DISABLED(0x40303040, 0x808080),

    /**
     * Recipe is idle, waiting for the next scheduled run.
     * Slight green background to indicate everything is working.
     */
    IDLE(0x3040A040, 0x40A040),

    /**
     * Missing reusable/catalyst items in internal inventory.
     */
    MISSING_CATALYST(0x40FF8000, 0xD06000),

    /**
     * Not enough input items available in network.
     */
    MISSING_INPUT(0x40FF4040, 0xD03030),

    /**
     * Not enough space in network for output.
     */
    NO_OUTPUT_SPACE(0x40FFFF00, 0xB0B000),

    /**
     * Recipe simulation failed (invalid pattern or crafting error).
     */
    SIMULATION_FAILED(0x40C040FF, 0xA030D0),

    /**
     * Holding output, waiting for space in network.
     */
    HOLDING_OUTPUT(0x4080C0FF, 0x50A0D0);

    private final int backgroundColor;
    private final int textColor;

    CrafterState(int backgroundColor, int textColor) {
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public boolean isError() {
        return this == MISSING_CATALYST || this == MISSING_INPUT ||
               this == NO_OUTPUT_SPACE || this == SIMULATION_FAILED;
    }
}
