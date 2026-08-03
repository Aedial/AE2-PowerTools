package com.ae2powertools.features.monitor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.features.monitor.alarm.TileLevelMonitorAlarm;
import com.ae2powertools.features.monitor.emitter.EmitterRedstonePower;
import com.ae2powertools.features.monitor.emitter.IEmitterCardHost;


/**
 * Shared tooltip helpers for Storage Level Emitter and Level Monitor Alarm probe integrations.
 */
public final class StorageMonitorProbeHelper {

    public static final String EMITTER_FUZZY_CARD_TOOLTIP_KEY = "tooltip.ae2powertools.storage_emitter.card.fuzzy";
    public static final String EMITTER_CRAFTING_CARD_TOOLTIP_KEY = "tooltip.ae2powertools.storage_emitter.card.crafting";
    public static final String EMITTER_REDSTONE_WEAK_TOOLTIP_KEY = "tooltip.ae2powertools.storage_emitter.redstone.weak";
    public static final String EMITTER_REDSTONE_STRONG_TOOLTIP_KEY = "tooltip.ae2powertools.storage_emitter.redstone.strong";

    public static final String WAILA_HAS_EMITTER_DATA_KEY = "ae2powertoolsHasEmitterProbeData";
    public static final String WAILA_EMITTER_HAS_FUZZY_CARD_KEY = "ae2powertoolsEmitterHasFuzzyCard";
    public static final String WAILA_EMITTER_HAS_CRAFTING_CARD_KEY = "ae2powertoolsEmitterHasCraftingCard";
    public static final String WAILA_EMITTER_REDSTONE_POWER_KEY = "ae2powertoolsEmitterRedstonePower";

    public static final String WAILA_HAS_ALARM_DATA_KEY = "ae2powertoolsHasAlarmProbeData";
    public static final String WAILA_ALARM_PLAYER_REGISTERED_KEY = "ae2powertoolsAlarmPlayerRegistered";

    private StorageMonitorProbeHelper() {}

    public static EmitterProbeData collectEmitterData(IEmitterCardHost emitter) {
        if (emitter == null) return EmitterProbeData.invalid();

        return new EmitterProbeData(
            true,
            emitter.hasFuzzyCard(),
            emitter.hasCraftingCard(),
            emitter.getRedstonePower());
    }

    public static boolean shouldRenderEmitter(NBTTagCompound tag) {
        return tag != null && tag.getBoolean(WAILA_HAS_EMITTER_DATA_KEY);
    }

    public static EmitterProbeData readEmitterWailaData(NBTTagCompound tag) {
        if (!shouldRenderEmitter(tag)) return EmitterProbeData.invalid();

        return new EmitterProbeData(
            true,
            tag.getBoolean(WAILA_EMITTER_HAS_FUZZY_CARD_KEY),
            tag.getBoolean(WAILA_EMITTER_HAS_CRAFTING_CARD_KEY),
            EmitterRedstonePower.fromId(tag.getInteger(WAILA_EMITTER_REDSTONE_POWER_KEY)));
    }

    public static void writeEmitterWailaData(IEmitterCardHost emitter, NBTTagCompound tag) {
        if (emitter == null || tag == null) return;

        EmitterProbeData data = collectEmitterData(emitter);

        tag.setBoolean(WAILA_HAS_EMITTER_DATA_KEY, data.isValid());
        tag.setBoolean(WAILA_EMITTER_HAS_FUZZY_CARD_KEY, data.hasFuzzyCard());
        tag.setBoolean(WAILA_EMITTER_HAS_CRAFTING_CARD_KEY, data.hasCraftingCard());
        tag.setInteger(WAILA_EMITTER_REDSTONE_POWER_KEY, data.getRedstonePower().getId());
    }

    public static AlarmProbeData collectAlarmData(TileLevelMonitorAlarm alarm, EntityPlayer player) {
        if (alarm == null) return AlarmProbeData.invalid();

        return new AlarmProbeData(true, alarm.isPlayerRegistered(player));
    }

    public static boolean shouldRenderAlarm(NBTTagCompound tag) {
        return tag != null && tag.getBoolean(WAILA_HAS_ALARM_DATA_KEY);
    }

    public static AlarmProbeData readAlarmWailaData(NBTTagCompound tag) {
        if (!shouldRenderAlarm(tag)) return AlarmProbeData.invalid();

        return new AlarmProbeData(true, tag.getBoolean(WAILA_ALARM_PLAYER_REGISTERED_KEY));
    }

    public static void writeAlarmWailaData(TileLevelMonitorAlarm alarm, EntityPlayer player, NBTTagCompound tag) {
        if (alarm == null || tag == null) return;

        AlarmProbeData data = collectAlarmData(alarm, player);

        tag.setBoolean(WAILA_HAS_ALARM_DATA_KEY, data.isValid());
        tag.setBoolean(WAILA_ALARM_PLAYER_REGISTERED_KEY, data.isRegistered());
    }

    public static final class EmitterProbeData {

        private final boolean valid;
        private final boolean fuzzyCard;
        private final boolean craftingCard;
        private final EmitterRedstonePower redstonePower;

        private EmitterProbeData(boolean valid,
                                 boolean fuzzyCard,
                                 boolean craftingCard,
                                 EmitterRedstonePower redstonePower) {
            this.valid = valid;
            this.fuzzyCard = fuzzyCard;
            this.craftingCard = craftingCard;
            this.redstonePower = redstonePower == null ? EmitterRedstonePower.WEAK : redstonePower;
        }

        public static EmitterProbeData invalid() {
            return new EmitterProbeData(false, false, false, EmitterRedstonePower.WEAK);
        }

        public boolean isValid() {
            return valid;
        }

        public boolean hasFuzzyCard() {
            return fuzzyCard;
        }

        public boolean hasCraftingCard() {
            return craftingCard;
        }

        public EmitterRedstonePower getRedstonePower() {
            return redstonePower;
        }

        public String getRedstoneModeKey() {
            if (redstonePower == EmitterRedstonePower.STRONG) {
                return EMITTER_REDSTONE_STRONG_TOOLTIP_KEY;
            }

            return EMITTER_REDSTONE_WEAK_TOOLTIP_KEY;
        }
    }

    public static final class AlarmProbeData {

        private final boolean valid;
        private final boolean registered;

        private AlarmProbeData(boolean valid, boolean registered) {
            this.valid = valid;
            this.registered = registered;
        }

        public static AlarmProbeData invalid() {
            return new AlarmProbeData(false, false);
        }

        public boolean isValid() {
            return valid;
        }

        public boolean isRegistered() {
            return registered;
        }

        public String getRegistrationKey() {
            return registered
                ? "gui.ae2powertools.level_monitor_alarm.registration.registered"
                : "gui.ae2powertools.level_monitor_alarm.registration.unregistered";
        }
    }
}