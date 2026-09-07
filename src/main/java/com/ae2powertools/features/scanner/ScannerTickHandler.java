package com.ae2powertools.features.scanner;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.ae2powertools.items.ItemNetworkHealthScanner;


/**
 * Server-side tick handler for processing network scans and syncing results.
 */
public class ScannerTickHandler {

    private static final int SYNC_INTERVAL = 20; // Ticks between syncs

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        // Process all active scan sessions
        ScanSessionManager.tickSessions();

        // Send completion messages for sessions that just finished
        for (ScanSessionManager.SessionKey key : ScanSessionManager.getCompletedThisTick()) {
            EntityPlayerMP player = findPlayer(server, key.getPlayerId());

            if (player != null) {
                ScanSessionManager.ScanSession session = ScanSessionManager.getSession(key.getPlayerId(), key.getDeviceId());
                if (session != null) {
                    player.sendMessage(ScanSessionManager.buildCompletionMessage(session));
                }
            }
        }

        // Sync to clients periodically
        Set<ScanSessionManager.SessionKey> toRemove = new HashSet<>();

        for (Map.Entry<ScanSessionManager.SessionKey, ScanSessionManager.ScanSession> entry : ScanSessionManager.getSessionEntries()) {
            ScanSessionManager.SessionKey key = entry.getKey();
            ScanSessionManager.ScanSession session = entry.getValue();

            if (session.getScanner().isComplete()) {
                session.resetSyncCounter();

                if (session.isCompletionSynced()) continue;

                EntityPlayerMP player = findPlayer(server, key.getPlayerId());
                if (player != null) {
                    ItemNetworkHealthScanner.syncToClient(player, key.getDeviceId());
                    session.markCompletionSynced();
                } else {
                    toRemove.add(key);
                }

                continue;
            }

            int counter = session.incrementSyncCounter();

            boolean shouldSync = counter >= SYNC_INTERVAL;

            if (shouldSync) {
                session.resetSyncCounter();

                // Find the player and sync
                EntityPlayerMP player = findPlayer(server, key.getPlayerId());
                if (player != null) {
                    ItemNetworkHealthScanner.syncToClient(player, key.getDeviceId());
                } else {
                    // Player disconnected, remove session
                    toRemove.add(key);
                }
            }
        }

        // Clean up disconnected players
        for (ScanSessionManager.SessionKey key : toRemove) {
            ScanSessionManager.endSession(key.getPlayerId(), key.getDeviceId());
        }
    }

    private EntityPlayerMP findPlayer(MinecraftServer server, UUID playerId) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.getUniqueID().equals(playerId)) return player;
        }

        return null;
    }
}
