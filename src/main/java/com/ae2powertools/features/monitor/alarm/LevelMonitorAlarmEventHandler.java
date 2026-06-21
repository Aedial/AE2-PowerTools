package com.ae2powertools.features.monitor.alarm;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;


/**
 * Wakes subscribed alarms when their first registered player connects, and lets them sleep again
 * when the last one disconnects.
 */
public class LevelMonitorAlarmEventHandler {

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            LevelMonitorAlarmManager.handlePlayerLogin((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            LevelMonitorAlarmManager.handlePlayerLogout((EntityPlayerMP) event.player);
        }
    }
}