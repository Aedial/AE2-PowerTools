package com.ae2powertools.features.scanner.gui.tab;

import java.util.Comparator;

import net.minecraft.client.resources.I18n;

import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.data.client.MissingDeviceClient;
import com.ae2powertools.features.scanner.gui.ScannerDisplayText;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerRowGroup;
import com.ae2powertools.features.scanner.gui.ScannerSortContext;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTabDescriptor;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Scanner tab used to list and draw devices without a channel.
 */
public final class MissingChannelScannerTab extends AbstractScannerTab<MissingDeviceClient> {

    public MissingChannelScannerTab() {
        super(new ScannerTabDescriptor(ScannerTabId.MISSING_CHANNELS, ScannerTabDescriptor.IconType.TEXT, "✗",
            "gui.ae2powertools.scanner.tab_missing", 0xFFFF6666, 0xFF6666, 1.0f, 0.4f, 0.4f));
    }

    @Override
    public Comparator<MissingDeviceClient> createComparator(ScannerSortContext context) {
        return (left, right) -> {
            int dimensionCompare = compareDimension(left.dimension, right.dimension, context);
            if (dimensionCompare != 0) return dimensionCompare;

            String leftName = ScannerDisplayText.stripFormatting(left.getDisplayName());
            String rightName = ScannerDisplayText.stripFormatting(right.getDisplayName());
            if (context.getSortMode() == ScannerSortMode.NAME) {
                int nameCompare = leftName.compareToIgnoreCase(rightName);
                if (nameCompare != 0) return nameCompare;

                return Double.compare(distanceFromAnchor(left, context), distanceFromAnchor(right, context));
            }

            int distanceCompare = Double.compare(distanceFromAnchor(left, context), distanceFromAnchor(right, context));
            return distanceCompare != 0 ? distanceCompare : leftName.compareToIgnoreCase(rightName);
        };
    }

    @Override
    protected ScannerRowGroup getGroup(MissingDeviceClient entry) {
        String title = I18n.format("gui.ae2powertools.scanner.dimension_format", entry.dimensionName, entry.dimension);
        return new ScannerRowGroup(new ScannerGroupKey(getDescriptor().getId(), "dimension:" + entry.dimension), title, null);
    }

    @Override
    protected String getRowText(MissingDeviceClient entry, ScannerViewContext viewContext) {
        return entry.getDisplayName() + " " + ScannerDisplayText.coordinates(entry.pos)
            + ScannerDisplayText.currentDistanceSuffix(entry, viewContext);
    }

    @Override
    protected String getHudText(MissingDeviceClient entry, ScannerViewContext viewContext) {
        return entry.getDisplayName() + " " + ScannerDisplayText.coordinates(entry.pos) + ": "
            + ScannerDisplayText.overlayDistance(entry.getDistanceFrom(viewContext.getPlayerPosition()));
    }
}
