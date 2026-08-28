package com.ae2powertools.features.scanner.gui;


/**
 * Data used by a collapsible list group.
 */
public final class ScannerRowGroup {

    private final ScannerGroupKey key;
    private final String title;
    private final String tooltip;

    public ScannerRowGroup(ScannerGroupKey key, String title, String tooltip) {
        this.key = key;
        this.title = title;
        this.tooltip = tooltip;
    }

    public ScannerGroupKey getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getTooltip() {
        return tooltip;
    }
}
