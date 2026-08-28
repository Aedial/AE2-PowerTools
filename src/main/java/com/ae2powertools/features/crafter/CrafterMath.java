package com.ae2powertools.features.crafter;


final class CrafterMath {

    private CrafterMath() {}

    /**
     * Ceiling division for positive divisors without overflowing the numerator.
     */
    static long ceilDivPositive(long dividend, long divisor) {
        if (dividend <= 0L) return 0L;
        if (divisor <= 0L) return Long.MAX_VALUE;

        return 1L + ((dividend - 1L) / divisor);
    }
}