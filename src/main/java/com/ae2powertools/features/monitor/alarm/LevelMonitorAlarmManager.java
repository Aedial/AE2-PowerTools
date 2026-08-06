package com.ae2powertools.features.monitor.alarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.ae2powertools.network.PacketSyncLevelMonitorAlarms;
import com.ae2powertools.network.PowerToolsNetwork;


/**
 * Tracks loaded alarm tiles and mirrors each player's active alarms to the client.
 */
public final class LevelMonitorAlarmManager {

    private static final Set<TileLevelMonitorAlarm> LOADED_ALARMS =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private LevelMonitorAlarmManager() {}

    public static void register(TileLevelMonitorAlarm tile) {
        if (tile == null || tile.getWorld().isRemote) return;

        LOADED_ALARMS.add(tile);
    }

    public static void unregister(TileLevelMonitorAlarm tile) {
        if (tile == null) return;

        List<EntityPlayerMP> affectedPlayers = tile.getConnectedRegisteredPlayers();
        LOADED_ALARMS.remove(tile);

        for (EntityPlayerMP player : affectedPlayers) syncPlayer(player);
    }

    public static void handlePlayerLogin(EntityPlayerMP player) {
        for (TileLevelMonitorAlarm tile : snapshotLoadedAlarms()) {
            if (!tile.isInvalid() && tile.isPlayerRegistered(player)) {
                tile.onRegisteredPlayerAvailabilityChanged();
            }
        }

        syncPlayer(player);
    }

    public static void handlePlayerLogout(EntityPlayerMP player) {
        for (TileLevelMonitorAlarm tile : snapshotLoadedAlarms()) {
            if (!tile.isInvalid() && tile.isPlayerRegistered(player)) {
                tile.onRegisteredPlayerAvailabilityChanged();
            }
        }
    }

    public static void syncPlayer(EntityPlayerMP player) {
        if (player == null) return;

        List<AlarmLocation> alarms = new ArrayList<>();
        UUID playerId = player.getUniqueID();

        for (TileLevelMonitorAlarm tile : snapshotLoadedAlarms()) {
            if (!tile.isActiveFor(playerId)) continue;

            alarms.add(new AlarmLocation(tile.getWorld().provider.getDimension(), tile.getPos()));
        }

        PowerToolsNetwork.INSTANCE.sendTo(new PacketSyncLevelMonitorAlarms(alarms), player);
    }

    public static void syncRegisteredPlayers(TileLevelMonitorAlarm tile) {
        if (tile == null) return;

        for (EntityPlayerMP player : tile.getConnectedRegisteredPlayers()) syncPlayer(player);
    }

    private static List<TileLevelMonitorAlarm> snapshotLoadedAlarms() {
        return new ArrayList<>(LOADED_ALARMS);
    }
}