package com.ae2powertools.features.remotemonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import appeng.api.features.IWirelessTermHandler;

import com.ae2powertools.items.ItemRemoteStorageMonitor;


/**
 * Server-side tick handler that polls remote monitor sessions at their configured rate.
 */
public class RemoteMonitorTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        List<RemoteMonitorSessionManager.SessionKey> staleSessions = new ArrayList<>();
        for (Map.Entry<RemoteMonitorSessionManager.SessionKey, RemoteMonitorSessionManager.RemoteMonitorSession> entry
            : RemoteMonitorSessionManager.getSessionEntries()) {

            RemoteMonitorSessionManager.SessionKey key = entry.getKey();
            RemoteMonitorSessionManager.RemoteMonitorSession session = entry.getValue();
            EntityPlayerMP player = findPlayer(server, key.getPlayerId());
            if (player == null) {
                staleSessions.add(key);
                continue;
            }

            ItemStack stack = ItemRemoteStorageMonitor.getMonitorInInventory(player, key.getDeviceId());
            if (stack.isEmpty() || !(stack.getItem() instanceof IWirelessTermHandler)) {
                if (session.shouldExpireMissingMonitor(player.world.getTotalWorldTime())) {
                    staleSessions.add(key);
                }

                continue;
            }

            session.markMonitorPresent();
            session.tick((IWirelessTermHandler) stack.getItem(), player, stack);
        }

        for (RemoteMonitorSessionManager.SessionKey key : staleSessions) {
            RemoteMonitorSessionManager.endSession(key.getPlayerId(), key.getDeviceId());
        }
    }

    private EntityPlayerMP findPlayer(MinecraftServer server, UUID playerId) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.getUniqueID().equals(playerId)) return player;
        }

        return null;
    }
}