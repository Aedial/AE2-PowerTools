package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import com.ae2powertools.features.scanner.data.FatalNetworkError;
import com.ae2powertools.features.scanner.data.PatternIssue;
import com.ae2powertools.features.scanner.data.client.ChokeLocationClient;
import com.ae2powertools.features.scanner.data.client.ChunkLocationClient;
import com.ae2powertools.features.scanner.data.client.LoopLocationClient;
import com.ae2powertools.features.scanner.data.client.MissingDeviceClient;


/**
 * Client data received from a scanner sync packet.
 */
public final class ScannerSyncSnapshot {

    private final boolean scanComplete;
    private final boolean subnetScanEnabled;
    private final ITextComponent statusMessage;
    private final List<LoopLocationClient> loops;
    private final List<ChunkLocationClient> chunks;
    private final List<MissingDeviceClient> missingDevices;
    private final List<ChokeLocationClient> chokepoints;
    private final List<FatalNetworkError> fatalErrors;
    private final List<PatternIssue> patternIssues;

    public ScannerSyncSnapshot(boolean scanComplete, boolean subnetScanEnabled, ITextComponent statusMessage,
            List<LoopLocationClient> loops, List<ChunkLocationClient> chunks,
            List<MissingDeviceClient> missingDevices, List<ChokeLocationClient> chokepoints,
            List<FatalNetworkError> fatalErrors, List<PatternIssue> patternIssues) {
        this.scanComplete = scanComplete;
        this.subnetScanEnabled = subnetScanEnabled;
        this.statusMessage = statusMessage == null ? new TextComponentString("") : statusMessage;
        this.loops = immutableCopy(loops);
        this.chunks = immutableCopy(chunks);
        this.missingDevices = immutableCopy(missingDevices);
        this.chokepoints = immutableCopy(chokepoints);
        this.fatalErrors = immutableCopy(fatalErrors);
        this.patternIssues = immutableCopy(patternIssues);
    }

    public boolean isScanComplete() {
        return scanComplete;
    }

    public boolean isSubnetScanEnabled() {
        return subnetScanEnabled;
    }

    public ITextComponent getStatusMessage() {
        return statusMessage;
    }

    public List<LoopLocationClient> getLoops() {
        return loops;
    }

    public List<ChunkLocationClient> getChunks() {
        return chunks;
    }

    public List<MissingDeviceClient> getMissingDevices() {
        return missingDevices;
    }

    public List<ChokeLocationClient> getChokepoints() {
        return chokepoints;
    }

    public List<FatalNetworkError> getFatalErrors() {
        return fatalErrors;
    }

    public List<PatternIssue> getPatternIssues() {
        return patternIssues;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
