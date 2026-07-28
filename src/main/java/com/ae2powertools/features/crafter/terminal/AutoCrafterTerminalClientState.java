package com.ae2powertools.features.crafter.terminal;

import java.util.HashMap;
import java.util.Map;


/**
 * Client-side metadata for AutoCrafter rows shown inside AE2's Interface Terminal.
 */
public final class AutoCrafterTerminalClientState {

    private static final Map<Long, Integer> activeSlotCounts = new HashMap<>();

    private AutoCrafterTerminalClientState() {}

    public static void clear() {
        activeSlotCounts.clear();
    }

    public static void setActiveSlotCount(long id, int activeSlotCount) {
        activeSlotCounts.put(id, activeSlotCount);
    }

    public static boolean isAutoCrafter(long id) {
        return activeSlotCounts.containsKey(id);
    }

    public static int getActiveSlotCount(long id) {
        Integer activeSlotCount = activeSlotCounts.get(id);
        return activeSlotCount != null ? activeSlotCount : 0;
    }

    public static boolean isDisabledSlot(long id, int slotIndex) {
        int activeSlotCount = getActiveSlotCount(id);
        return activeSlotCount > 0 && slotIndex >= activeSlotCount;
    }
}