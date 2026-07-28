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
            EntityPlayerMP player = findPlayer(server, key.getPlayerId());
            if (player == null) {
                staleSessions.add(key);
                continue;
            }

            // TODO: Add some leeway so we do not kill the session with an accidental click.
            //       Maybe a configurable grace period of 30s?
            ItemStack stack = ItemRemoteStorageMonitor.findMonitorByDeviceId(player, key.getDeviceId());
            if (stack.isEmpty() || !(stack.getItem() instanceof IWirelessTermHandler)) {
                staleSessions.add(key);
                continue;
            }

            entry.getValue().tick((IWirelessTermHandler) stack.getItem(), player, stack);
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