package com.ae2powertools.features.remotemonitor;


public final class RemoteMonitorMath {

    private RemoteMonitorMath() {}

    public static double calculateChangePercent(long delta, long currentQuantity) {
        if (delta == 0L) return 0.0D;

        double previousQuantity = currentQuantity - (double) delta;

        // Normalize against the larger endpoint so both gains and losses remain readable
        // and the displayed share stays within the 0-100% range.
        double denominator = Math.max(Math.abs(previousQuantity), Math.abs((double) currentQuantity));
        if (denominator <= 0.0D) return 0.0D;

        return Math.abs((double) delta) * 100.0D / denominator;
    }
}