package com.ae2powertools.features.scanner.gui.tab;

import java.util.Comparator;

import net.minecraft.client.resources.I18n;

import com.ae2powertools.features.scanner.data.PatternIssue;
import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.gui.ScannerDisplayText;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerRowGroup;
import com.ae2powertools.features.scanner.gui.ScannerSortContext;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTabDescriptor;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Scanner tab used to list crafting-pattern issues.
 */
public final class PatternIssueScannerTab extends AbstractScannerTab<PatternIssue> {

    public PatternIssueScannerTab() {
        super(new ScannerTabDescriptor(ScannerTabId.PATTERNS, ScannerTabDescriptor.IconType.PATTERN_TEXTURE, "",
            "gui.ae2powertools.scanner.tab_patterns", 0xFFE0C060, 0xD8B45A, 0.85f, 0.71f, 0.35f));
    }

    @Override
    public Comparator<PatternIssue> createComparator(ScannerSortContext context) {
        return (left, right) -> {
            int categoryCompare = Integer.compare(left.getCategory().ordinal(), right.getCategory().ordinal());
            if (categoryCompare != 0) return categoryCompare;

            int dimensionCompare = compareDimension(left.getDimension(), right.getDimension(), context);
            if (dimensionCompare != 0) return dimensionCompare;

            String leftName = ScannerDisplayText.stripFormatting(left.getDescription());
            String rightName = ScannerDisplayText.stripFormatting(right.getDescription());
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
    protected ScannerRowGroup getGroup(PatternIssue entry) {
        PatternIssue.Category category = entry.getCategory();
        return new ScannerRowGroup(new ScannerGroupKey(getDescriptor().getId(), "category:" + category.name()),
            I18n.format(category.getTitleKey()), I18n.format(category.getTooltipKey()));
    }

    @Override
    protected String getRowText(PatternIssue entry, ScannerViewContext viewContext) {
        return entry.getDescription() + " " + ScannerDisplayText.coordinates(entry.getPos())
            + ScannerDisplayText.currentDistanceSuffix(entry, viewContext);
    }

    @Override
    protected String getHudText(PatternIssue entry, ScannerViewContext viewContext) {
        return entry.getDescription() + " " + ScannerDisplayText.coordinates(entry.getPos()) + ": "
            + ScannerDisplayText.overlayDistance(entry.getDistanceFrom(viewContext.getPlayerPosition()));
    }

    @Override
    protected String getEntryTooltip(PatternIssue entry) {
        return entry.getSummary();
    }
}
