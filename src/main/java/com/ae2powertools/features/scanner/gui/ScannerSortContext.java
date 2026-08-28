package com.ae2powertools.features.scanner.gui;


/**
 * Settings used to sort a tab.
 */
public final class ScannerSortContext {

    private final ScannerSortAnchor anchor;
    private final ScannerSortMode sortMode;

    public ScannerSortContext(ScannerSortAnchor anchor, ScannerSortMode sortMode) {
        this.anchor = anchor;
        this.sortMode = sortMode;
    }

    public ScannerSortAnchor getAnchor() {
        return anchor;
    }

    public ScannerSortMode getSortMode() {
        return sortMode;
    }
}
