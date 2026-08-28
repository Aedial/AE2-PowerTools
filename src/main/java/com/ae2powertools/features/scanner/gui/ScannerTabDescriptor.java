package com.ae2powertools.features.scanner.gui;

import com.ae2powertools.features.scanner.data.ScannerTabId;


/**
 * Metadata for drawing the scanner tab.
 */
public final class ScannerTabDescriptor {

    public enum IconType {
        TEXT,
        PATTERN_TEXTURE
    }

    private final ScannerTabId id;
    private final IconType iconType;
    private final String iconText;
    private final String tooltipKey;
    private final int activeCountColor;
    private final int overlayColor;
    private final float red;
    private final float green;
    private final float blue;

    public ScannerTabDescriptor(ScannerTabId id, IconType iconType, String iconText, String tooltipKey,
            int activeCountColor, int overlayColor, float red, float green, float blue) {
        this.id = id;
        this.iconType = iconType;
        this.iconText = iconText;
        this.tooltipKey = tooltipKey;
        this.activeCountColor = activeCountColor;
        this.overlayColor = overlayColor;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public ScannerTabId getId() {
        return id;
    }

    public IconType getIconType() {
        return iconType;
    }

    public String getIconText() {
        return iconText;
    }

    public String getTooltipKey() {
        return tooltipKey;
    }

    public int getCountColor(int count, int inactiveColor) {
        return count > 0 ? activeCountColor : inactiveColor;
    }

    public int getOverlayColor() {
        return overlayColor;
    }

    public float getRed() {
        return red;
    }

    public float getGreen() {
        return green;
    }

    public float getBlue() {
        return blue;
    }
}
