package com.ae2powertools.util;


/**
 * Helper methods for non-negative long arithmetic that clamps on overflow.
 */
public final class SaturatingMath {

    private SaturatingMath() {}

    public static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;

        return left * right;
    }

    public static long saturatingAdd(long left, long right) {
        if (left <= 0L) return Math.max(0L, right);
        if (right <= 0L) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;

        return left + right;
    }
}