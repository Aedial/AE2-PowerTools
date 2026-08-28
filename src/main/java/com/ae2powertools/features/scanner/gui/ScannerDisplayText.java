package com.ae2powertools.features.scanner.gui;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;

import com.ae2powertools.features.scanner.data.ScannerIssue;


/**
 * Text helpers used by scanner tabs.
 */
public final class ScannerDisplayText {

    private ScannerDisplayText() {}

    public static String coordinates(BlockPos pos) {
        return String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
    }

    public static String currentDistanceSuffix(ScannerIssue issue, ScannerViewContext context) {
        if (!context.isCurrentDimension(issue.getDimension())) return "";

        double distance = issue.getDistanceFrom(context.getPlayerPosition());
        return distance > 0 ? String.format(" - %.0fm", distance) : "";
    }

    /**
     * Format a distance for the scanner overlay.
     */
    public static String overlayDistance(double distance) {
        return distance < 1000 ? String.format("%.0fm", distance) : String.format("%.1fkm", distance / 1000);
    }

    public static int compareDisplayText(String left, String right) {
        return stripFormatting(left).compareToIgnoreCase(stripFormatting(right));
    }

    public static String stripFormatting(String value) {
        if (value == null) return "";

        String stripped = TextFormatting.getTextWithoutFormattingCodes(value);
        return stripped == null ? value : stripped;
    }
}
