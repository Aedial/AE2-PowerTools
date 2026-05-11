package com.ae2powertools.util;


/**
 * Utility class for formatting polling rate values for display.
 * Replicated from CELLS for use in Monitoring Manager and its dependents.
 */
public final class PollingRateUtils {

    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    public static final int TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    public static final int TICKS_PER_DAY = TICKS_PER_HOUR * 24;

    private PollingRateUtils() {}

    /**
     * Format a polling rate value in ticks for display.
     *
     * @param ticks the polling rate in game ticks
     * @return human-readable string (e.g., "5s", "2m 10s", "1d 2h 3m 4s")
     */
    public static String format(long ticks) {
        if (ticks <= 0) return "0";

        StringBuilder sb = new StringBuilder();

        if (ticks >= TICKS_PER_DAY) {
            long days = ticks / TICKS_PER_DAY;
            sb.append(days).append("d ");
            ticks %= TICKS_PER_DAY;
        }

        if (ticks >= TICKS_PER_HOUR) {
            long hours = ticks / TICKS_PER_HOUR;
            sb.append(hours).append("h ");
            ticks %= TICKS_PER_HOUR;
        }

        if (ticks >= TICKS_PER_MINUTE) {
            long minutes = ticks / TICKS_PER_MINUTE;
            sb.append(minutes).append("m ");
            ticks %= TICKS_PER_MINUTE;
        }

        if (ticks >= TICKS_PER_SECOND) {
            long seconds = ticks / TICKS_PER_SECOND;
            sb.append(seconds).append("s ");
            ticks %= TICKS_PER_SECOND;
        }

        if (ticks > 0) {
            sb.append(ticks).append("t");
        }

        return sb.toString().trim();
    }
}
