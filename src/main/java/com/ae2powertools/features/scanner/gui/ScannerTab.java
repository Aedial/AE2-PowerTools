package com.ae2powertools.features.scanner.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.ae2powertools.features.scanner.client.ScannerSession;
import com.ae2powertools.features.scanner.data.ScannerIssue;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;


/**
 * Interface used by a scanner tab.
 * <p>
 * Scanner results are converted into generic rows, HUD lines, and in-world
 * drawing data. The GUI and renderer only use those common values.
 */
public interface ScannerTab<T extends ScannerIssue> {

    ScannerTabDescriptor getDescriptor();

    Comparator<T> createComparator(ScannerSortContext context);

    List<ScannerListRow> buildRows(ScannerTabState<T> state, ScannerSortMode sortMode,
            ScannerViewContext viewContext, Set<ScannerGroupKey> collapsedGroups);

    List<String> getFooterLines(ScannerSession session, ScannerTabState<T> state);

    String getEntryTooltip(ScannerTabState<T> state, ScannerIssueKey issueKey);

    List<ScannerHudLine> buildHudLines(ScannerTabState<T> state, ScannerViewContext viewContext);

    List<IssueOverlayPosition> buildIssueOverlay(ScannerTabState<T> state);
}
