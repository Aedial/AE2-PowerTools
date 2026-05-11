package com.ae2powertools.features.monitor.dependent;


/**
 * Determines how multiple entry conditions are combined.
 * Each entry independently evaluates (quantity COMP threshold) to a boolean.
 * - AND: all entries must be met for the overall condition to be true
 * - OR: any entry being met makes the overall condition true
 */
public enum MatchMode {

    /** All entries must have their condition met */
    AND(0, "and", "&"),

    /** Any entry having its condition met is sufficient */
    OR(1, "or", "|");

    private final int id;
    private final String name;
    private final String symbol;

    MatchMode(int id, String name, String symbol) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public static MatchMode fromId(int id) {
        for (MatchMode mode : values()) {
            if (mode.id == id) return mode;
        }

        return AND;
    }

    /**
     * Returns the next mode in the cycle (for toggle button).
     */
    public MatchMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
