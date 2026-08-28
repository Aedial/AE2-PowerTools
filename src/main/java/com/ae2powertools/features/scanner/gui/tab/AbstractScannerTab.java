package com.ae2powertools.features.scanner.gui.tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ae2powertools.features.scanner.client.ScannerSession;
import com.ae2powertools.features.scanner.data.ScannerIssue;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;
import com.ae2powertools.features.scanner.gui.IssueOverlayPosition;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerHudLine;
import com.ae2powertools.features.scanner.gui.ScannerListRow;
import com.ae2powertools.features.scanner.gui.ScannerRowGroup;
import com.ae2powertools.features.scanner.gui.ScannerSortContext;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTab;
import com.ae2powertools.features.scanner.gui.ScannerTabDescriptor;
import com.ae2powertools.features.scanner.gui.ScannerTabState;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Base class used by scanner tabs that group results by location.
 */
public abstract class AbstractScannerTab<T extends ScannerIssue> implements ScannerTab<T> {

    private final ScannerTabDescriptor descriptor;

    protected AbstractScannerTab(ScannerTabDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public final ScannerTabDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public List<ScannerListRow> buildRows(ScannerTabState<T> state, ScannerSortMode sortMode,
            ScannerViewContext viewContext, Set<ScannerGroupKey> collapsedGroups) {
        List<T> sorted = state.getSortedEntries(this, sortMode, viewContext);
        if (sorted.isEmpty()) return Collections.emptyList();

        Map<ScannerGroupKey, Integer> groupCounts = new LinkedHashMap<>();
        Map<ScannerGroupKey, ScannerRowGroup> groups = new LinkedHashMap<>();
        for (T entry : sorted) {
            ScannerRowGroup group = getGroup(entry);
            groups.putIfAbsent(group.getKey(), group);
            groupCounts.merge(group.getKey(), 1, Integer::sum);
        }

        List<ScannerListRow> rows = new ArrayList<>();
        ScannerGroupKey previousGroup = null;
        for (int index = 0; index < sorted.size(); index++) {
            T entry = sorted.get(index);
            ScannerRowGroup group = getGroup(entry);
            ScannerGroupKey groupKey = group.getKey();

            if (!groupKey.equals(previousGroup)) {
                rows.add(ScannerListRow.category(groups.get(groupKey), groupCounts.get(groupKey)));
                previousGroup = groupKey;
            }

            if (collapsedGroups.contains(groupKey)) continue;

            // Adjacent sorted entries define the last branch in each group,
            // so we can cut-off the last tree line.
            boolean isLast = index == sorted.size() - 1
                || !groupKey.equals(getGroup(sorted.get(index + 1)).getKey());
            rows.add(ScannerListRow.entry(entry.getIssueKey(), getRowText(entry, viewContext), isLast));
        }

        return rows;
    }

    @Override
    public List<String> getFooterLines(ScannerSession session, ScannerTabState<T> state) {
        String status = session.getStatusMessage().getFormattedText();
        return status.isEmpty() ? Collections.emptyList() : Collections.singletonList(status);
    }

    @Override
    public String getEntryTooltip(ScannerTabState<T> state, ScannerIssueKey issueKey) {
        T entry = state.findEntry(issueKey);
        return entry == null ? null : getEntryTooltip(entry);
    }

    @Override
    public List<ScannerHudLine> buildHudLines(ScannerTabState<T> state, ScannerViewContext viewContext) {
        if (!viewContext.hasPlayer()) return Collections.emptyList();

        List<ScannerHudLine> lines = new ArrayList<>();
        for (T entry : state.getSelectedEntries()) {
            if (!viewContext.isCurrentDimension(entry.getDimension())) continue;
            lines.add(new ScannerHudLine(getHudText(entry, viewContext), descriptor.getOverlayColor()));
        }

        return lines;
    }

    @Override
    public List<IssueOverlayPosition> buildIssueOverlay(ScannerTabState<T> state) {
        List<IssueOverlayPosition> markers = new ArrayList<>();
        for (T entry : state.getSelectedEntries()) markers.add(getIssueOverlay(entry));

        return markers;
    }

    protected int compareDimension(int first, int second, ScannerSortContext context) {
        boolean firstCurrent = first == context.getAnchor().getDimension();
        boolean secondCurrent = second == context.getAnchor().getDimension();
        if (firstCurrent != secondCurrent) return firstCurrent ? -1 : 1;

        return Integer.compare(first, second);
    }

    protected double distanceFromAnchor(T entry, ScannerSortContext context) {
        return entry.getDistanceFrom(context.getAnchor().getPosition());
    }

    protected IssueOverlayPosition getIssueOverlay(T entry) {
        return IssueOverlayPosition.block(entry, descriptor);
    }

    protected String getEntryTooltip(T entry) {
        return null;
    }

    protected abstract ScannerRowGroup getGroup(T entry);

    protected abstract String getRowText(T entry, ScannerViewContext viewContext);

    protected abstract String getHudText(T entry, ScannerViewContext viewContext);
}
