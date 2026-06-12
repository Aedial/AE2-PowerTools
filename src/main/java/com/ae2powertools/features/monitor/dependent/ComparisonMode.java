package com.ae2powertools.features.monitor.dependent;


/**
 * Comparison operator for per-entry threshold evaluation.
 * Each monitored entry has its own threshold and comparison mode,
 * determining when that entry's condition is considered "met".
 */
public enum ComparisonMode {

    LESS(0, "<"),
    LESS_EQUAL(1, "<="),
    GREATER(2, ">"),
    GREATER_EQUAL(3, ">=");

    private final int id;
    private final String symbol;

    ComparisonMode(int id, String symbol) {
        this.id = id;
        this.symbol = symbol;
    }

    /**
     * Evaluates whether the given quantity satisfies the comparison against the threshold.
     */
    public boolean test(long quantity, long threshold) {
        switch (this) {
            case LESS:          return quantity < threshold;
            case LESS_EQUAL:    return quantity <= threshold;
            case GREATER:       return quantity > threshold;
            case GREATER_EQUAL: return quantity >= threshold;
            default:            return false;
        }
    }

    public int getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public static ComparisonMode fromId(int id) {
        for (ComparisonMode mode : values()) {
            if (mode.id == id) return mode;
        }

        return GREATER_EQUAL;
    }

    /**
     * Returns the next mode in the cycle (for toggle button).
     */
    public ComparisonMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
