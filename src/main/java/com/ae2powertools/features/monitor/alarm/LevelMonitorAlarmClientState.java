package com.ae2powertools.features.monitor.alarm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Client-side mirror of the active alarms affecting the local player.
 */
@SideOnly(Side.CLIENT)
public final class LevelMonitorAlarmClientState {

    private static List<AlarmLocation> activeAlarms = new ArrayList<>();

    private LevelMonitorAlarmClientState() {}

    public static void setActiveAlarms(List<AlarmLocation> alarms) {
        activeAlarms = new ArrayList<>(alarms);
    }

    public static List<AlarmLocation> getActiveAlarms() {
        return Collections.unmodifiableList(activeAlarms);
    }

    public static void clear() {
        activeAlarms.clear();
    }
}