package com.ae2powertools.util;


/**
 * Shared formatting utilities.
 */
public final class FormatUtil {

    private FormatUtil() {}

    /**
     * Formats a time in seconds as a human-readable string (e.g., "1h 30m 15s").
     */
    public static String formatTime(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";

        int days = totalSeconds / 86400;
        int hours = (totalSeconds % 86400) / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Formats a time in ticks as a human-readable string.
     * Assumes 20 ticks per second.
     */
    public static String formatTimeTicks(int ticks) {
        return formatTime(ticks / 20);
    }
}
