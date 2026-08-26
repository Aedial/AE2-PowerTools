package com.ae2powertools.util;

import com.ae2powertools.widgets.SmallVanillaButton;


/**
 * Shared controller for the standard 2x4 time adjustment buttons used by the time GUIs.
 */
public class TimeAdjustmentButtons extends StepAdjustmentButtons {

    private static final int[] BASE_DELTAS = {
        FormatUtil.TICKS_PER_SECOND,
        FormatUtil.TICKS_PER_MINUTE,
        FormatUtil.TICKS_PER_HOUR,
        FormatUtil.TICKS_PER_DAY,
    };
    private static final int[] COLUMN_X_OFFSETS = { 14, 52, 90, 128 };
    private static final String[] UNITS = { "s", "m", "h", "d" };

    public TimeAdjustmentButtons() {
        super(BASE_DELTAS, 10, COLUMN_X_OFFSETS, (columnIndex, multiplier, positive) -> {
            String sign = positive ? "+" : "-";
            return sign + multiplier + UNITS[columnIndex];
        });
    }

    public int getAdjustedValue(final SmallVanillaButton button, final long currentValue, final int minimumValue) {
        if (!this.manages(button)) return Integer.MIN_VALUE;

        long adjustedValue = super.getAdjustedValue(button, currentValue, minimumValue, Integer.MAX_VALUE);
        return (int) adjustedValue;
    }

    public static String formatValue(final long ticks) {
        return FormatUtil.formatTimeTicks(ticks);
    }
}