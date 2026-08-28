package com.ae2powertools.features.scanner.gui.tab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.resources.I18n;

import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.data.client.ChokeLocationClient;
import com.ae2powertools.features.scanner.data.client.ConnectionFlowClient;
import com.ae2powertools.features.scanner.gui.IssueOverlayPosition;
import com.ae2powertools.features.scanner.gui.ScannerDisplayText;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerRowGroup;
import com.ae2powertools.features.scanner.gui.ScannerSortContext;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTabDescriptor;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Scanner tab used to list and draw channel chokepoints.
 */
public final class ChokepointScannerTab extends AbstractScannerTab<ChokeLocationClient> {

    public ChokepointScannerTab() {
        super(new ScannerTabDescriptor(ScannerTabId.CHOKEPOINTS, ScannerTabDescriptor.IconType.TEXT, "⚡",
            "gui.ae2powertools.scanner.tab_chokepoints", 0xFF66AAFF, 0x66AAFF, 0.4f, 0.67f, 1.0f));
    }

    @Override
    public Comparator<ChokeLocationClient> createComparator(ScannerSortContext context) {
        return (left, right) -> {
            int dimensionCompare = compareDimension(left.dimension, right.dimension, context);
            if (dimensionCompare != 0) return dimensionCompare;

            int excessCompare = Integer.compare(right.getExcessChannels(), left.getExcessChannels());
            if (excessCompare != 0) return excessCompare;

            if (context.getSortMode() == ScannerSortMode.NAME) {
                int nameCompare = ScannerDisplayText.compareDisplayText(left.description, right.description);
                if (nameCompare != 0) return nameCompare;
            }

            return Double.compare(distanceFromAnchor(left, context), distanceFromAnchor(right, context));
        };
    }

    @Override
    protected ScannerRowGroup getGroup(ChokeLocationClient entry) {
        String title = I18n.format("gui.ae2powertools.scanner.dimension_format", entry.dimensionName, entry.dimension);
        return new ScannerRowGroup(new ScannerGroupKey(getDescriptor().getId(), "dimension:" + entry.dimension), title, null);
    }

    @Override
    protected String getRowText(ChokeLocationClient entry, ScannerViewContext viewContext) {
        int excess = entry.getExcessChannels();
        String excessText = excess > 0 ? " (-" + excess + ')' : "";
        return entry.description + " " + ScannerDisplayText.coordinates(entry.pos) + " " + entry.getChannelString()
            + excessText + ScannerDisplayText.currentDistanceSuffix(entry, viewContext);
    }

    @Override
    protected String getHudText(ChokeLocationClient entry, ScannerViewContext viewContext) {
        int excess = entry.getExcessChannels();
        String excessText = excess > 0 ? " (-" + excess + ')' : "";
        return entry.description + " " + ScannerDisplayText.coordinates(entry.pos) + " " + entry.getChannelString()
            + excessText + ": " + ScannerDisplayText.overlayDistance(entry.getDistanceFrom(viewContext.getPlayerPosition()));
    }

    @Override
    protected IssueOverlayPosition getIssueOverlay(ChokeLocationClient entry) {
        List<String> lines = new ArrayList<>();
        lines.add(entry.getChannelString());
        for (ConnectionFlowClient flow : entry.connectionFlows) lines.add(String.valueOf(flow.demandedChannels));

        return IssueOverlayPosition.block(entry, getDescriptor()).withFloatingLines(lines);
    }
}
