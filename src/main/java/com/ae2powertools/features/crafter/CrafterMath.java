package com.ae2powertools.features.crafter;


final class CrafterMath {

    private CrafterMath() {}

    /**
     * Saturating multiply for non-negative crafter counts and timings.
     */
    static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;

        return left * right;
    }

    /**
     * Saturating add for non-negative crafter counts and accumulators.
     */
    static long saturatingAdd(long left, long right) {
        if (left <= 0L) return Math.max(0L, right);
        if (right <= 0L) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;

        return left + right;
    }

    /**
     * Ceiling division for positive divisors without overflowing the numerator.
     */
    static long ceilDivPositive(long dividend, long divisor) {
        if (dividend <= 0L) return 0L;
        if (divisor <= 0L) return Long.MAX_VALUE;

        return 1L + ((dividend - 1L) / divisor);
    }
}