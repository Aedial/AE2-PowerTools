package com.ae2powertools.features.scanner.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.ae2powertools.features.scanner.data.FatalNetworkError;
import com.ae2powertools.features.scanner.data.PatternIssue;
import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.data.client.ChokeLocationClient;
import com.ae2powertools.features.scanner.data.client.ChunkLocationClient;
import com.ae2powertools.features.scanner.data.client.LoopLocationClient;
import com.ae2powertools.features.scanner.data.client.MissingDeviceClient;
import com.ae2powertools.features.scanner.gui.tab.ChokepointScannerTab;
import com.ae2powertools.features.scanner.gui.tab.FatalErrorScannerTab;
import com.ae2powertools.features.scanner.gui.tab.LoopScannerTab;
import com.ae2powertools.features.scanner.gui.tab.MissingChannelScannerTab;
import com.ae2powertools.features.scanner.gui.tab.PatternIssueScannerTab;
import com.ae2powertools.features.scanner.gui.tab.UnloadedChunkScannerTab;


/**
 * Immutable scanner tab index for the UI order.
 */
public final class ScannerTabRegistry {

    public static final ScannerTab<LoopLocationClient> LOOPS = new LoopScannerTab();
    public static final ScannerTab<ChunkLocationClient> UNLOADED_CHUNKS = new UnloadedChunkScannerTab();
    public static final ScannerTab<ChokeLocationClient> CHOKEPOINTS = new ChokepointScannerTab();
    public static final ScannerTab<MissingDeviceClient> MISSING_CHANNELS = new MissingChannelScannerTab();
    public static final ScannerTab<FatalNetworkError> FATAL_ERRORS = new FatalErrorScannerTab();
    public static final ScannerTab<PatternIssue> PATTERNS = new PatternIssueScannerTab();

    private static final Map<ScannerTabId, ScannerTab<?>> BY_ID = new EnumMap<>(ScannerTabId.class);
    private static final List<ScannerTab<?>> DISPLAY_TABS;

    static {
        register(LOOPS);
        register(UNLOADED_CHUNKS);
        register(CHOKEPOINTS);
        register(MISSING_CHANNELS);
        register(FATAL_ERRORS);
        register(PATTERNS);

        List<ScannerTab<?>> displayTabs = new ArrayList<>();
        for (ScannerTabId id : ScannerTabId.getDisplayOrder()) displayTabs.add(BY_ID.get(id));
        DISPLAY_TABS = Collections.unmodifiableList(displayTabs);
    }

    private ScannerTabRegistry() {}

    public static ScannerTab<?> get(ScannerTabId id) {
        return BY_ID.get(id);
    }

    public static List<ScannerTab<?>> getAllTabs() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static List<ScannerTab<?>> getDisplayTabs() {
        return DISPLAY_TABS;
    }

    private static void register(ScannerTab<?> tab) {
        BY_ID.put(tab.getDescriptor().getId(), tab);
    }
}
