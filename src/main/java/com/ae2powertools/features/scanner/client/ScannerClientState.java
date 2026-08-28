package com.ae2powertools.features.scanner.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.ae2powertools.client.PowerToolsClientConfig;
import com.ae2powertools.features.scanner.ScannerSyncSnapshot;
import com.ae2powertools.features.scanner.data.ScannerTabId;
import com.ae2powertools.features.scanner.gui.ScannerSortMode;


/**
 * Client state used by Network Health Scanner GUIs and overlays.
 * <p>
 * A {@link ScannerSession} is kept for each physical scanner device ID.
 * Scanner state is split into tab-specific classes so selection, sorting,
 * and filtering logic can be shared.
 */
@SideOnly(Side.CLIENT)
public final class ScannerClientState {

    private static final Map<Long, ScannerSession> SESSIONS = new HashMap<>();

    private ScannerClientState() {}

    public static boolean hasSession(long deviceId) {
        return SESSIONS.containsKey(deviceId);
    }

    public static ScannerSession getSession(long deviceId) {
        return SESSIONS.get(deviceId);
    }

    public static ScannerSession getOrCreateSession(long deviceId) {
        return SESSIONS.computeIfAbsent(deviceId, ScannerSession::new);
    }

    public static void removeSession(long deviceId) {
        SESSIONS.remove(deviceId);
    }

    public static void initSubnetState(long deviceId, boolean subnetScanEnabled) {
        getOrCreateSession(deviceId).setSubnetScanEnabled(subnetScanEnabled);
    }

    public static void applySync(long deviceId, boolean hasSession, ScannerSyncSnapshot snapshot) {
        if (!hasSession) {
            removeSession(deviceId);
            return;
        }

        getOrCreateSession(deviceId).applySync(snapshot);
    }

    public static ScannerSortMode getSortMode(ScannerTabId tabId) {
        return PowerToolsClientConfig.scanner.getSortMode(tabId) == 1
            ? ScannerSortMode.NAME : ScannerSortMode.DISTANCE;
    }

    public static void toggleSortMode(ScannerSession session) {
        ScannerTabId tabId = session.getActiveTabId();
        ScannerSortMode current = getSortMode(tabId);
        PowerToolsClientConfig.scanner.setSortMode(tabId,
            current == ScannerSortMode.DISTANCE ? ScannerSortMode.NAME.ordinal() : ScannerSortMode.DISTANCE.ordinal());

        // Reset the anchor so the new sort uses the player's current position
        session.resetCurrentSortAnchor();
    }
}
