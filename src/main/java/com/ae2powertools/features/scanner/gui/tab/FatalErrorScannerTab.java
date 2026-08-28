package com.ae2powertools.features.scanner.gui.tab;

import java.util.Comparator;

import net.minecraft.client.resources.I18n;

import com.ae2powertools.features.scanner.data.FatalNetworkError;
import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.gui.ScannerDisplayText;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerRowGroup;
import com.ae2powertools.features.scanner.gui.ScannerSortContext;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTabDescriptor;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Scanner tab used to list and draw fatal network errors.
 */
public final class FatalErrorScannerTab extends AbstractScannerTab<FatalNetworkError> {

    public FatalErrorScannerTab() {
        super(new ScannerTabDescriptor(ScannerTabId.FATAL_ERRORS, ScannerTabDescriptor.IconType.TEXT, "!",
            "gui.ae2powertools.scanner.tab_fatal", 0xFFFF4444, 0xFF4444, 1.0f, 0.27f, 0.27f));
    }

    @Override
    public Comparator<FatalNetworkError> createComparator(ScannerSortContext context) {
        return (left, right) -> {
            int categoryCompare = Integer.compare(left.getCategory().ordinal(), right.getCategory().ordinal());
            if (categoryCompare != 0) return categoryCompare;

            int dimensionCompare = compareDimension(left.getDimension(), right.getDimension(), context);
            if (dimensionCompare != 0) return dimensionCompare;

            String leftName = ScannerDisplayText.stripFormatting(getDisplayText(left));
            String rightName = ScannerDisplayText.stripFormatting(getDisplayText(right));
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
    protected ScannerRowGroup getGroup(FatalNetworkError entry) {
        FatalNetworkError.Category category = entry.getCategory();
        return new ScannerRowGroup(new ScannerGroupKey(getDescriptor().getId(), "category:" + category.name()),
            I18n.format(category.getTitleKey()), I18n.format(category.getTooltipKey()));
    }

    @Override
    protected String getRowText(FatalNetworkError entry, ScannerViewContext viewContext) {
        return getDisplayText(entry) + " " + ScannerDisplayText.coordinates(entry.getPos())
            + ScannerDisplayText.currentDistanceSuffix(entry, viewContext);
    }

    @Override
    protected String getHudText(FatalNetworkError entry, ScannerViewContext viewContext) {
        return getDisplayText(entry) + " " + ScannerDisplayText.coordinates(entry.getPos()) + ": "
            + ScannerDisplayText.overlayDistance(entry.getDistanceFrom(viewContext.getPlayerPosition()));
    }

    public static String getDisplayText(FatalNetworkError error) {
        return I18n.format(error.getCategory().getEntryKey(), error.getDescription());
    }
}
