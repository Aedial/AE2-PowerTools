package com.ae2powertools.features.maintainer;


/**
 * Represents the current state of a maintainer entry.
 */
public enum MaintainerState {
    /**
     * Recipe is disabled by user.
     */
    DISABLED(0x40303040, 0x808080),

    /**
     * Recipe has finished running and is waiting for the next scheduled run.
     */
    IDLE(0x00000000, 0x707070),

    /**
     * Task is scheduled to run (waiting for its time slot).
     */
    SCHEDULED(0x4080C0FF, 0x50A0D0),

    /**
     * Task is actively running (crafting in progress).
     */
    RUNNING(0x4040FF40, 0x30B030),

    /**
     * Task is running but stalled (e.g., waiting for resources).
     */
    STALLED(0x40FFFF00, 0xB0B000),

    /**
     * Task could not be run (missing recipe, no free CPU, missing resources).
     */
    ERROR(0x40FF4040, 0xD03030),

    /**
     * Post-crafting issue (no space for output).
     */
    POST_ERROR(0x40C040FF, 0xA030D0);

    private final int backgroundColor;
    private final int textColor;

    MaintainerState(int backgroundColor, int textColor) {
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
        return this == ERROR || this == POST_ERROR;
    }

    public boolean isActive() {
        return this == RUNNING || this == SCHEDULED || this == STALLED;
    }
}
