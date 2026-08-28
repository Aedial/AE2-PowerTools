package com.ae2powertools.features.scanner.data;

import java.util.Objects;


/**
 * Stable key for a scanned result.
 * <p>
 * Selections use this key rather than a packet or list position, so a
 * full scanner sync may add, remove, or reorder results without changing
 * another selection by accident.
 */
public final class ScannerIssueKey {

    private final ScannerTabId tabId;
    private final String key;

    public ScannerIssueKey(ScannerTabId tabId, String key) {
        this.tabId = Objects.requireNonNull(tabId, "tabId");
        this.key = Objects.requireNonNull(key, "key");
    }

    public ScannerTabId getTabId() {
        return tabId;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ScannerIssueKey)) return false;

        ScannerIssueKey other = (ScannerIssueKey) obj;
        return tabId == other.tabId && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return 31 * tabId.hashCode() + key.hashCode();
    }

    @Override
    public String toString() {
        return tabId.name() + ':' + key;
    }
}
