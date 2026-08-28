package com.ae2powertools.features.scanner.gui;


/**
 * Text and color used in the scanner HUD.
 */
public final class ScannerHudLine {

    private final String text;
    private final int color;

    public ScannerHudLine(String text, int color) {
        this.text = text;
        this.color = color;
    }

    public String getText() {
        return text;
    }

    public int getColor() {
        return color;
    }
}
