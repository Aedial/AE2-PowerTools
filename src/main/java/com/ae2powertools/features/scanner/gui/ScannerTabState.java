package com.ae2powertools.features.scanner.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ae2powertools.features.scanner.data.ScannerIssue;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;


/**
 * Mutable state used by a tab in a {@link ScannerSession}.
 * Result lists are replaced by sync while selections are retained by key.
 */
public final class ScannerTabState<T extends ScannerIssue> {

    private List<T> entries = Collections.emptyList();
    private final Set<ScannerIssueKey> selected = new LinkedHashSet<>();
    private long revision;

    private List<T> sortedEntries = Collections.emptyList();
    private long sortedRevision = -1L;
    private ScannerSortMode sortedMode;
    private ScannerSortAnchor sortAnchor;

    public void replaceEntries(List<T> updatedEntries) {
        entries = Collections.unmodifiableList(new ArrayList<>(updatedEntries));
        selected.retainAll(collectKeys(entries));
        revision++;
        invalidateSortedEntries();
    }

    public void clear() {
        entries = Collections.emptyList();
        selected.clear();
        revision++;
        invalidateSortedEntries();
        sortAnchor = null;
    }

    public List<T> getEntries() {
        return entries;
    }

    public int getEntryCount() {
        return entries.size();
    }

    public boolean isSelected(ScannerIssueKey key) {
        return selected.contains(key);
    }

    public int getSelectedCount() {
        return selected.size();
    }

    public void toggleSelection(ScannerIssueKey key) {
        if (!containsKey(key)) return;
        if (!selected.add(key)) selected.remove(key);
    }

    public void selectOnly(ScannerIssueKey key) {
        if (!containsKey(key)) return;

        selected.clear();
        selected.add(key);
    }

    public void selectAll() {
        selected.clear();
        for (T entry : entries) selected.add(entry.getIssueKey());
    }

    public void deselectAll() {
        selected.clear();
    }

    public List<T> getSelectedEntries() {
        List<T> result = new ArrayList<>();
        for (T entry : entries) {
            if (selected.contains(entry.getIssueKey())) result.add(entry);
        }

        return result;
    }

    public T findEntry(ScannerIssueKey key) {
        for (T entry : entries) {
            if (entry.getIssueKey().equals(key)) return entry;
        }

        return null;
    }

    public void resetSortAnchor() {
        sortAnchor = null;
        invalidateSortedEntries();
    }

    public List<T> getSortedEntries(ScannerTab<T> tab, ScannerSortMode sortMode, ScannerViewContext viewContext) {
        if (sortAnchor == null) sortAnchor = viewContext.createSortAnchor();

        if (sortedRevision == revision && sortedMode == sortMode) return sortedEntries;

        List<T> sorted = new ArrayList<>(entries);
        Comparator<T> comparator = tab.createComparator(new ScannerSortContext(sortAnchor, sortMode));
        comparator = comparator.thenComparing(entry -> entry.getSortingKey());
        sorted.sort(comparator);

        sortedEntries = Collections.unmodifiableList(sorted);
        sortedRevision = revision;
        sortedMode = sortMode;

        return sortedEntries;
    }

    private boolean containsKey(ScannerIssueKey key) {
        for (T entry : entries) {
            if (entry.getIssueKey().equals(key)) return true;
        }

        return false;
    }

    private Set<ScannerIssueKey> collectKeys(List<T> source) {
        Set<ScannerIssueKey> result = new LinkedHashSet<>();
        for (T entry : source) result.add(entry.getIssueKey());

        return result;
    }

    private void invalidateSortedEntries() {
        sortedEntries = Collections.emptyList();
        sortedRevision = -1L;
        sortedMode = null;
    }
}
