package com.ae2powertools.features.scanner.gui;


import com.ae2powertools.features.scanner.data.ScannerIssueKey;

/**
 * Row data used by the scanner list widget.
 */
public final class ScannerListRow {

    public enum Type {
        CATEGORY,
        ENTRY
    }

    private final Type type;
    private final String text;
    private final ScannerGroupKey groupKey;
    private final String tooltip;
    private final ScannerIssueKey issueKey;
    private final boolean lastInGroup;

    private ScannerListRow(Type type, String text, ScannerGroupKey groupKey, String tooltip,
            ScannerIssueKey issueKey, boolean lastInGroup) {
        this.type = type;
        this.text = text;
        this.groupKey = groupKey;
        this.tooltip = tooltip;
        this.issueKey = issueKey;
        this.lastInGroup = lastInGroup;
    }

    public static ScannerListRow category(ScannerRowGroup group, int count) {
        return new ScannerListRow(Type.CATEGORY, group.getTitle() + " (" + count + ')', group.getKey(),
            group.getTooltip(), null, false);
    }

    public static ScannerListRow entry(ScannerIssueKey issueKey, String text, boolean lastInGroup) {
        return new ScannerListRow(Type.ENTRY, text, null, null, issueKey, lastInGroup);
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public ScannerGroupKey getGroupKey() {
        return groupKey;
    }

    public String getTooltip() {
        return tooltip;
    }

    public ScannerIssueKey getIssueKey() {
        return issueKey;
    }

    public boolean isLastInGroup() {
        return lastInGroup;
    }
}
