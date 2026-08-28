package com.ae2powertools.features.scanner.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import com.ae2powertools.features.scanner.ScannerSyncSnapshot;
import com.ae2powertools.features.scanner.data.ScannerIssue;
import com.ae2powertools.features.scanner.data.ScannerIssueKey;
import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.gui.IssueOverlayPosition;
import com.ae2powertools.features.scanner.gui.ScannerGroupKey;
import com.ae2powertools.features.scanner.gui.ScannerHudLine;
import com.ae2powertools.features.scanner.gui.ScannerListRow;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;
import com.ae2powertools.features.scanner.gui.ScannerTab;
import com.ae2powertools.features.scanner.gui.ScannerTabRegistry;
import com.ae2powertools.features.scanner.gui.ScannerTabState;
import com.ae2powertools.features.scanner.gui.ScannerViewContext;


/**
 * Per-device client state for scanner results.
 * <p>
 * Each sync replaces every tab's result list. Selections use {@link ScannerIssueKey} values,
 * while the active tab supplies rows, HUD text, and in-world drawing data.
 */
public final class ScannerSession {

    private final long deviceId;
    private final Map<ScannerTabId, ScannerTabState<?>> tabStates = new EnumMap<>(ScannerTabId.class);

    private ITextComponent statusMessage = new TextComponentString("");
    private boolean scanComplete;
    private boolean subnetScanEnabled;
    private ScannerTabId activeTabId = ScannerTabId.LOOPS;
    private long revision;

    public ScannerSession(long deviceId) {
        this.deviceId = deviceId;
        for (ScannerTab<?> tab : ScannerTabRegistry.getAllTabs()) {
            tabStates.put(tab.getDescriptor().getId(), new ScannerTabState<>());
        }
    }

    public long getDeviceId() {
        return deviceId;
    }

    public ITextComponent getStatusMessage() {
        return statusMessage;
    }

    public boolean isScanComplete() {
        return scanComplete;
    }

    public boolean isSubnetScanEnabled() {
        return subnetScanEnabled;
    }

    public void setSubnetScanEnabled(boolean enabled) {
        if (subnetScanEnabled == enabled) return;

        subnetScanEnabled = enabled;
        revision++;
    }

    public ScannerTabId getActiveTabId() {
        return activeTabId;
    }

    public void setActiveTabId(ScannerTabId tabId) {
        activeTabId = tabId;
    }

    public ScannerTab<?> getActiveTab() {
        return ScannerTabRegistry.get(activeTabId);
    }

    public int getEntryCount(ScannerTabId tabId) {
        return getTabState(tabId).getEntryCount();
    }

    public int getCurrentEntryCount() {
        return getTabState(activeTabId).getEntryCount();
    }

    public int getCurrentSelectedCount() {
        return getTabState(activeTabId).getSelectedCount();
    }

    public boolean isCurrentSelection(ScannerIssueKey key) {
        return getTabState(activeTabId).isSelected(key);
    }

    public void toggleCurrentSelection(ScannerIssueKey key) {
        getTabState(activeTabId).toggleSelection(key);
    }

    public void selectOnlyCurrent(ScannerIssueKey key) {
        getTabState(activeTabId).selectOnly(key);
    }

    public void selectAllCurrent() {
        getTabState(activeTabId).selectAll();
    }

    public void deselectAllCurrent() {
        getTabState(activeTabId).deselectAll();
    }

    public void resetCurrentSortAnchor() {
        getTabState(activeTabId).resetSortAnchor();
    }

    public long getRevision() {
        return revision;
    }

    public void clearData() {
        for (ScannerTabState<?> tabState : tabStates.values()) tabState.clear();

        scanComplete = false;
        activeTabId = ScannerTabId.LOOPS;
        revision++;
    }

    public void applySync(ScannerSyncSnapshot snapshot) {
        scanComplete = snapshot.isScanComplete();
        subnetScanEnabled = snapshot.isSubnetScanEnabled();
        statusMessage = snapshot.getStatusMessage();
        replaceEntries(ScannerTabRegistry.LOOPS, snapshot.getLoops());
        replaceEntries(ScannerTabRegistry.UNLOADED_CHUNKS, snapshot.getChunks());
        replaceEntries(ScannerTabRegistry.MISSING_CHANNELS, snapshot.getMissingDevices());
        replaceEntries(ScannerTabRegistry.CHOKEPOINTS, snapshot.getChokepoints());
        replaceEntries(ScannerTabRegistry.FATAL_ERRORS, snapshot.getFatalErrors());
        replaceEntries(ScannerTabRegistry.PATTERNS, snapshot.getPatternIssues());
        revision++;
    }

    public List<ScannerListRow> buildCurrentRows(ScannerSortMode sortMode,
            ScannerViewContext viewContext, Set<ScannerGroupKey> collapsedGroups) {
        return buildRowsUnchecked(getActiveTab(), getRawTabState(activeTabId), sortMode, viewContext, collapsedGroups);
    }

    public List<String> getCurrentFooterLines() {
        return getFooterLinesUnchecked(getActiveTab(), getRawTabState(activeTabId));
    }

    public String getCurrentEntryTooltip(ScannerIssueKey issueKey) {
        return getEntryTooltipUnchecked(getActiveTab(), getRawTabState(activeTabId), issueKey);
    }

    public List<ScannerHudLine> buildHudLines(ScannerViewContext viewContext) {
        return buildHudLinesUnchecked(getActiveTab(), getRawTabState(activeTabId), viewContext);
    }

    public List<IssueOverlayPosition> buildIssueOverlay() {
        return buildIssueOverlayUnchecked(getActiveTab(), getRawTabState(activeTabId));
    }

    @SuppressWarnings("unchecked")
    private <T extends ScannerIssue> ScannerTabState<T> getTabState(ScannerTabId tabId) {
        return (ScannerTabState<T>) tabStates.get(tabId);
    }

    private ScannerTabState<?> getRawTabState(ScannerTabId tabId) {
        return tabStates.get(tabId);
    }

    private <T extends ScannerIssue> void replaceEntries(ScannerTab<T> tab, List<T> entries) {
        ScannerTabState<T> state = getTabState(tab.getDescriptor().getId());
        state.replaceEntries(entries);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<ScannerListRow> buildRowsUnchecked(ScannerTab<?> tab, ScannerTabState<?> state,
            ScannerSortMode sortMode, ScannerViewContext viewContext, Set<ScannerGroupKey> collapsedGroups) {
        return ((ScannerTab) tab).buildRows((ScannerTabState) state, sortMode, viewContext, collapsedGroups);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<String> getFooterLinesUnchecked(ScannerTab<?> tab, ScannerTabState<?> state) {
        return ((ScannerTab) tab).getFooterLines(this, (ScannerTabState) state);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String getEntryTooltipUnchecked(ScannerTab<?> tab, ScannerTabState<?> state, ScannerIssueKey issueKey) {
        return ((ScannerTab) tab).getEntryTooltip((ScannerTabState) state, issueKey);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<ScannerHudLine> buildHudLinesUnchecked(ScannerTab<?> tab, ScannerTabState<?> state,
            ScannerViewContext viewContext) {
        return ((ScannerTab) tab).buildHudLines((ScannerTabState) state, viewContext);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<IssueOverlayPosition> buildIssueOverlayUnchecked(ScannerTab<?> tab, ScannerTabState<?> state) {
        return ((ScannerTab) tab).buildIssueOverlay((ScannerTabState) state);
    }
}
