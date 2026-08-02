package com.ae2powertools.util;

import java.util.Locale;


/**
 * Shared formatting utilities.
 */
public final class FormatUtil {

    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    public static final int TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    public static final int TICKS_PER_DAY = TICKS_PER_HOUR * 24;

    private FormatUtil() {}

    /**
     * Formats a time in ticks as a human-readable string (e.g., "1h 30m 15s").
     */
    public static String formatTimeTicks(long ticks) {
        if (ticks <= 0) return "0";

        long days = ticks / TICKS_PER_DAY;
        long hours = (ticks % TICKS_PER_DAY) / TICKS_PER_HOUR;
        long minutes = (ticks % TICKS_PER_HOUR) / TICKS_PER_MINUTE;
        long seconds = (ticks % TICKS_PER_MINUTE) / TICKS_PER_SECOND;
        long remainingTicks = ticks % TICKS_PER_SECOND;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");
        if (remainingTicks > 0) sb.append(remainingTicks).append("t");

        return sb.toString().trim();
    }

    /**
     * Formats a time in seconds as a human-readable string.
     */
    public static String formatTimeSeconds(long seconds) {
        if (seconds <= 0) return "0s";

        return formatTimeTicks(seconds * TICKS_PER_SECOND);
    }

    /**
     * Formats a time in ticks as a human-readable string, rounding to the upper second.
     */
    public static String formatTimeTicksAsSeconds(long ticks) {
        return formatTimeSeconds((ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    /**
     * Formats a duration in nanoseconds as a compact human-readable string.
     */
    public static String formatDurationNanos(long durationNanos) {
        if (durationNanos <= 0L) return "0 ms";

        if (durationNanos < 1_000_000_000L) {
            double millis = durationNanos / 1_000_000.0D;
            return trimTrailingZeros(String.format(Locale.ROOT, "%.3f", millis)) + " ms";
        }

        double seconds = durationNanos / 1_000_000_000.0D;
        return trimTrailingZeros(String.format(Locale.ROOT, "%.3f", seconds)) + " s";
    }

    private static String trimTrailingZeros(String value) {
        int dotIndex = value.indexOf('.');
        if (dotIndex < 0) return value;

        int endIndex = value.length();
        while (endIndex > dotIndex + 1 && value.charAt(endIndex - 1) == '0') endIndex--;
        if (endIndex == dotIndex + 1) endIndex--;

        return value.substring(0, endIndex);
    }
}
