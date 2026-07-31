package com.ae2powertools.features.crafter;

import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.util.FormatUtil;


/**
 * Shared tooltip helpers for AutoCrafter probe integrations.
 */
public final class AutoCrafterProbeHelper {

    public static final String NEXT_OPERATION_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.next_operation";
    public static final String ERROR_CATALYST_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.user_error.catalyst";
    public static final String ERROR_PATTERN_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.user_error.pattern";
    public static final String ERROR_CATALYST_PATTERN_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.user_error.catalyst_pattern";
    public static final String PATTERN_SUMMARY_NO_PATTERNS_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.empty";
    public static final String PATTERN_SUMMARY_ACTIVE_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.active";
    public static final String PATTERN_SUMMARY_FULL_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.full";
    public static final String PATTERN_SUMMARY_DISABLED_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.disabled";
    public static final String PATTERN_SUMMARY_ACTIVE_FULL_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.active_full";
    public static final String PATTERN_SUMMARY_ACTIVE_DISABLED_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.active_disabled";
    public static final String PATTERN_SUMMARY_FULL_DISABLED_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.full_disabled";
    public static final String PATTERN_SUMMARY_ACTIVE_FULL_DISABLED_TOOLTIP_KEY = "tooltip.ae2powertools.crafter.patterns.active_full_disabled";

    public static final String WAILA_HAS_VALID_TILE = "ae2powertoolsHasScheduledOperation";
    public static final String WAILA_TICKS_UNTIL_NEXT_OPERATION_KEY = "ae2powertoolsTicksUntilNextOperation";
    public static final String WAILA_ACTIVE_PATTERN_COUNT_KEY = "ae2powertoolsActivePatternCount";
    public static final String WAILA_FULL_PATTERN_COUNT_KEY = "ae2powertoolsFullPatternCount";
    public static final String WAILA_DISABLED_PATTERN_COUNT_KEY = "ae2powertoolsDisabledPatternCount";
    public static final String WAILA_HAS_MISSING_CATALYST_ERROR_KEY = "ae2powertoolsHasMissingCatalystError";
    public static final String WAILA_HAS_PATTERN_ERROR_KEY = "ae2powertoolsHasPatternError";

    private AutoCrafterProbeHelper() {}

    public static String formatRemainingTime(long ticks) {
        // TODO: Should we shorten this?
        //       As in, not show seconds if it's more than a minute, etc.
        return FormatUtil.formatTimeTicksAsSeconds(Math.max(0L, ticks));
    }

    public static ProbeData collectData(TileAutoCrafter crafter) {
        if (crafter == null) return ProbeData.invalid();

        int activePatternCount = 0;
        int fullPatternCount = 0;
        int disabledPatternCount = 0;
        boolean hasMissingCatalystError = false;
        boolean hasPatternError = false;

        for (CrafterEntry entry : crafter.getEntries()) {
            if (entry == null || !entry.hasPattern()) continue;

            CrafterState state = entry.getState();
            if (state == CrafterState.MISSING_CATALYST) hasMissingCatalystError = true;
            if (state == CrafterState.SIMULATION_FAILED) hasPatternError = true;

            if (!entry.hasValidRecipeInfo()) continue;

            if (!entry.isEnabled()) {
                disabledPatternCount++;
                continue;
            }

            // "Full" means the recipe is enabled but blocked because
            // it is holding outputs that could not fit back into the network.
            if (entry.hasPendingOutputs()) {
                fullPatternCount++;
                continue;
            }

            activePatternCount++;
        }

        return new ProbeData(
            true,
            crafter.getTicksUntilNextOperation(),
            activePatternCount,
            fullPatternCount,
            disabledPatternCount,
            hasMissingCatalystError,
            hasPatternError);
    }

    // WAILA uses NBT data to pass information from server to the tooltip provider (on client),
    // so these helper methods are used to read and write the relevant data to the NBT tag.
    // Much better way than the cursed stuff TOP needs, tbh...
    public static boolean shouldRenderAutoCrafter(NBTTagCompound tag) {
        return tag != null && tag.getBoolean(WAILA_HAS_VALID_TILE);
    }

    public static ProbeData readWailaData(NBTTagCompound tag) {
        if (!shouldRenderAutoCrafter(tag)) return ProbeData.invalid();

        return new ProbeData(
            true,
            tag.getLong(WAILA_TICKS_UNTIL_NEXT_OPERATION_KEY),
            tag.getInteger(WAILA_ACTIVE_PATTERN_COUNT_KEY),
            tag.getInteger(WAILA_FULL_PATTERN_COUNT_KEY),
            tag.getInteger(WAILA_DISABLED_PATTERN_COUNT_KEY),
            tag.getBoolean(WAILA_HAS_MISSING_CATALYST_ERROR_KEY),
            tag.getBoolean(WAILA_HAS_PATTERN_ERROR_KEY));
    }

    public static long getWailaRemainingTicks(NBTTagCompound tag) {
        if (tag == null) return 0L;

        return tag.getLong(WAILA_TICKS_UNTIL_NEXT_OPERATION_KEY);
    }

    public static void writeWailaData(TileAutoCrafter crafter, NBTTagCompound tag) {
        if (crafter == null || tag == null) return;

        ProbeData data = collectData(crafter);

        tag.setBoolean(WAILA_HAS_VALID_TILE, data.isValid());
        tag.setLong(WAILA_TICKS_UNTIL_NEXT_OPERATION_KEY, data.getTicksUntilNextOperation());
        tag.setInteger(WAILA_ACTIVE_PATTERN_COUNT_KEY, data.getActivePatternCount());
        tag.setInteger(WAILA_FULL_PATTERN_COUNT_KEY, data.getFullPatternCount());
        tag.setInteger(WAILA_DISABLED_PATTERN_COUNT_KEY, data.getDisabledPatternCount());
        tag.setBoolean(WAILA_HAS_MISSING_CATALYST_ERROR_KEY, data.hasMissingCatalystError());
        tag.setBoolean(WAILA_HAS_PATTERN_ERROR_KEY, data.hasPatternError());
    }

    public static final class ProbeData {

        private final boolean valid;
        private final long ticksUntilNextOperation;
        private final int activePatternCount;
        private final int fullPatternCount;
        private final int disabledPatternCount;
        private final boolean missingCatalystError;
        private final boolean patternError;

        private ProbeData(boolean valid,
                          long ticksUntilNextOperation,
                          int activePatternCount,
                          int fullPatternCount,
                          int disabledPatternCount,
                          boolean missingCatalystError,
                          boolean patternError) {
            this.valid = valid;
            this.ticksUntilNextOperation = Math.max(0L, ticksUntilNextOperation);
            this.activePatternCount = Math.max(0, activePatternCount);
            this.fullPatternCount = Math.max(0, fullPatternCount);
            this.disabledPatternCount = Math.max(0, disabledPatternCount);
            this.missingCatalystError = missingCatalystError;
            this.patternError = patternError;
        }

        public static ProbeData invalid() {
            return new ProbeData(false, 0L, 0, 0, 0, false, false);
        }

        public boolean isValid() {
            return valid;
        }

        public long getTicksUntilNextOperation() {
            return ticksUntilNextOperation;
        }

        public int getActivePatternCount() {
            return activePatternCount;
        }

        public int getFullPatternCount() {
            return fullPatternCount;
        }

        public int getDisabledPatternCount() {
            return disabledPatternCount;
        }

        public boolean hasMissingCatalystError() {
            return missingCatalystError;
        }

        public boolean hasPatternError() {
            return patternError;
        }

        public boolean hasErrorLine() {
            return getErrorMask() != 0;
        }

        public String getErrorKey() {
            switch (getErrorMask()) {
                case 1:
                    return ERROR_CATALYST_TOOLTIP_KEY;

                case 2:
                    return ERROR_PATTERN_TOOLTIP_KEY;

                case 3:
                    return ERROR_CATALYST_PATTERN_TOOLTIP_KEY;

                default:
                    return "";
            }
        }

        public boolean hasPatternSummaryLine() {
            return getPatternMask() != 0;
        }

        public String getPatternSummaryKey() {
            switch (getPatternMask()) {
                case 1:
                    return PATTERN_SUMMARY_ACTIVE_TOOLTIP_KEY;

                case 2:
                    return PATTERN_SUMMARY_FULL_TOOLTIP_KEY;

                case 3:
                    return PATTERN_SUMMARY_ACTIVE_FULL_TOOLTIP_KEY;

                case 4:
                    return PATTERN_SUMMARY_DISABLED_TOOLTIP_KEY;

                case 5:
                    return PATTERN_SUMMARY_ACTIVE_DISABLED_TOOLTIP_KEY;

                case 6:
                    return PATTERN_SUMMARY_FULL_DISABLED_TOOLTIP_KEY;

                case 7:
                    return PATTERN_SUMMARY_ACTIVE_FULL_DISABLED_TOOLTIP_KEY;

                case 0:
                default:
                    return PATTERN_SUMMARY_NO_PATTERNS_TOOLTIP_KEY;
            }
        }

        public String[] getPatternSummaryArgs() {
            switch (getPatternMask()) {
                case 1:
                    return new String[] { Integer.toString(activePatternCount) };

                case 2:
                    return new String[] { Integer.toString(fullPatternCount) };

                case 3:
                    return new String[] {
                        Integer.toString(activePatternCount),
                        Integer.toString(fullPatternCount)
                    };

                case 4:
                    return new String[] { Integer.toString(disabledPatternCount) };

                case 5:
                    return new String[] {
                        Integer.toString(activePatternCount),
                        Integer.toString(disabledPatternCount)
                    };

                case 6:
                    return new String[] {
                        Integer.toString(fullPatternCount),
                        Integer.toString(disabledPatternCount)
                    };

                case 7:
                    return new String[] {
                        Integer.toString(activePatternCount),
                        Integer.toString(fullPatternCount),
                        Integer.toString(disabledPatternCount)
                    };

                case 0:
                default:
                    return new String[0];
            }
        }

        private int getErrorMask() {
            int mask = 0;
            if (missingCatalystError) mask |= 1;
            if (patternError) mask |= 2;
            return mask;
        }

        private int getPatternMask() {
            int mask = 0;
            if (activePatternCount > 0) mask |= 1;
            if (fullPatternCount > 0) mask |= 2;
            if (disabledPatternCount > 0) mask |= 4;
            return mask;
        }
    }
}