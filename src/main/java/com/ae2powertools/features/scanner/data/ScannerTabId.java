package com.ae2powertools.features.scanner.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * IDs assigned to the Network Health Scanner tabs, representing the display order.
 */
public enum ScannerTabId {
    LOOPS,
    UNLOADED_CHUNKS,
    CHOKEPOINTS,
    MISSING_CHANNELS,
    FATAL_ERRORS,
    PATTERNS;

    private static final List<ScannerTabId> DISPLAY_ORDER = Collections.unmodifiableList(Arrays.asList(
        LOOPS,
        UNLOADED_CHUNKS,
        MISSING_CHANNELS,
        CHOKEPOINTS,
        PATTERNS,
        FATAL_ERRORS));

    public static List<ScannerTabId> getDisplayOrder() {
        return DISPLAY_ORDER;
    }
}
