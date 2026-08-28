package com.ae2powertools.features.scanner.gui;

import java.util.Objects;

import com.ae2powertools.features.scanner.data.ScannerTabId;


/**
 * Key used to group scanner results within a tab.
 */
public final class ScannerGroupKey {

    private final ScannerTabId tabId;
    private final String key;

    public ScannerGroupKey(ScannerTabId tabId, String key) {
        this.tabId = Objects.requireNonNull(tabId, "tabId");
        this.key = Objects.requireNonNull(key, "key");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ScannerGroupKey)) return false;

        ScannerGroupKey other = (ScannerGroupKey) obj;
        return tabId == other.tabId && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return 31 * tabId.hashCode() + key.hashCode();
    }
}
