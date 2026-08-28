package com.ae2powertools.features.scanner.gui.tab;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.ae2powertools.features.scanner.client.ScannerSession;
import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.data.client.LoopLocationClient;
import com.ae2powertools.features.scanner.gui.ScannerDisplayText;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerRowGroup;
import com.ae2powertools.features.scanner.gui.ScannerSortContext;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTabDescriptor;
import com.ae2powertools.features.scanner.gui.ScannerTabState;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Scanner tab used to list and draw detected network loops.
 */
public final class LoopScannerTab extends AbstractScannerTab<LoopLocationClient> {

    public LoopScannerTab() {
        super(new ScannerTabDescriptor(ScannerTabId.LOOPS, ScannerTabDescriptor.IconType.TEXT, "∞",
            "gui.ae2powertools.scanner.tab_loops", 0xFF66AAFF, 0xFF4444, 1.0f, 0.27f, 0.27f));
    }

    @Override
    public Comparator<LoopLocationClient> createComparator(ScannerSortContext context) {
        return (left, right) -> {
            int dimensionCompare = compareDimension(left.dimension, right.dimension, context);
            if (dimensionCompare != 0) return dimensionCompare;

            if (context.getSortMode() == ScannerSortMode.NAME) {
                int nameCompare = ScannerDisplayText.compareDisplayText(left.description, right.description);
                if (nameCompare != 0) return nameCompare;
            }

            return Double.compare(distanceFromAnchor(left, context), distanceFromAnchor(right, context));
        };
    }

    @Override
    protected ScannerRowGroup getGroup(LoopLocationClient entry) {
        String title = I18n.format("gui.ae2powertools.scanner.dimension_format", entry.dimensionName, entry.dimension);
        return new ScannerRowGroup(new ScannerGroupKey(getDescriptor().getId(), "dimension:" + entry.dimension), title, null);
    }

    @Override
    protected String getRowText(LoopLocationClient entry, ScannerViewContext viewContext) {
        return entry.description + " " + ScannerDisplayText.coordinates(entry.pos)
            + ScannerDisplayText.currentDistanceSuffix(entry, viewContext);
    }

    @Override
    protected String getHudText(LoopLocationClient entry, ScannerViewContext viewContext) {
        return entry.description + " " + ScannerDisplayText.coordinates(entry.pos) + ": "
            + ScannerDisplayText.overlayDistance(entry.getDistanceFrom(viewContext.getPlayerPosition()));
    }

    @Override
    public List<String> getFooterLines(ScannerSession session, ScannerTabState<LoopLocationClient> state) {
        if (state.getEntries().isEmpty()) return super.getFooterLines(session, state);

        return Collections.singletonList(I18n.format("gui.ae2powertools.scanner.loops_info"));
    }
}
