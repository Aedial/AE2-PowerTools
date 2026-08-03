package com.ae2powertools.features.maintainer;

import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.util.FormatUtil;


/**
 * Shared tooltip helpers for Better Level Maintainer probe integrations.
 */
public final class MaintainerProbeHelper {

    public static final String PERFORMANCE_NONE_TOOLTIP_KEY = "tooltip.ae2powertools.performance.none";
    public static final String PERFORMANCE_SUMMARY_TOOLTIP_KEY = "tooltip.ae2powertools.performance.summary";

    public static final String WAILA_HAS_VALID_TILE_KEY = "ae2powertoolsHasMaintainerProbeData";
    public static final String WAILA_HAS_TIMING_SAMPLE_KEY = "ae2powertoolsHasMaintainerTimingSample";
    public static final String WAILA_LAST_WORK_DURATION_NANOS_KEY = "ae2powertoolsMaintainerLastWorkDurationNanos";
    public static final String WAILA_AVERAGE_WORK_DURATION_NANOS_KEY = "ae2powertoolsMaintainerAverageWorkDurationNanos";
    public static final String WAILA_MAX_WORK_DURATION_NANOS_KEY = "ae2powertoolsMaintainerMaxWorkDurationNanos";

    private MaintainerProbeHelper() {}

    public static String formatDuration(long durationNanos) {
        return FormatUtil.formatDurationNanos(Math.max(0L, durationNanos));
    }

    public static ProbeData collectData(TileBetterLevelMaintainer maintainer) {
        if (maintainer == null) return ProbeData.invalid();

        return new ProbeData(
            true,
            maintainer.hasWorkTimingSamples(),
            maintainer.getLastWorkDurationNanos(),
            maintainer.getAverageWorkDurationNanos(),
            maintainer.getMaxWorkDurationNanos());
    }

    public static boolean shouldRenderMaintainer(NBTTagCompound tag) {
        return tag != null && tag.getBoolean(WAILA_HAS_VALID_TILE_KEY);
    }

    public static ProbeData readWailaData(NBTTagCompound tag) {
        if (!shouldRenderMaintainer(tag)) return ProbeData.invalid();

        return new ProbeData(
            true,
            tag.getBoolean(WAILA_HAS_TIMING_SAMPLE_KEY),
            tag.getLong(WAILA_LAST_WORK_DURATION_NANOS_KEY),
            tag.getLong(WAILA_AVERAGE_WORK_DURATION_NANOS_KEY),
            tag.getLong(WAILA_MAX_WORK_DURATION_NANOS_KEY));
    }

    public static void writeWailaData(TileBetterLevelMaintainer maintainer, NBTTagCompound tag) {
        if (maintainer == null || tag == null) return;

        ProbeData data = collectData(maintainer);

        tag.setBoolean(WAILA_HAS_VALID_TILE_KEY, data.isValid());
        tag.setBoolean(WAILA_HAS_TIMING_SAMPLE_KEY, data.hasTimingSample());
        tag.setLong(WAILA_LAST_WORK_DURATION_NANOS_KEY, data.getLastWorkDurationNanos());
        tag.setLong(WAILA_AVERAGE_WORK_DURATION_NANOS_KEY, data.getAverageWorkDurationNanos());
        tag.setLong(WAILA_MAX_WORK_DURATION_NANOS_KEY, data.getMaxWorkDurationNanos());
    }

    public static final class ProbeData {

        private final boolean valid;
        private final boolean timingSample;
        private final long lastWorkDurationNanos;
        private final long averageWorkDurationNanos;
        private final long maxWorkDurationNanos;

        private ProbeData(boolean valid,
                          boolean timingSample,
                          long lastWorkDurationNanos,
                          long averageWorkDurationNanos,
                          long maxWorkDurationNanos) {
            this.valid = valid;
            this.timingSample = timingSample;
            this.lastWorkDurationNanos = Math.max(0L, lastWorkDurationNanos);
            this.averageWorkDurationNanos = Math.max(0L, averageWorkDurationNanos);
            this.maxWorkDurationNanos = Math.max(0L, maxWorkDurationNanos);
        }

        public static ProbeData invalid() {
            return new ProbeData(false, false, 0L, 0L, 0L);
        }

        public boolean isValid() {
            return valid;
        }

        public boolean hasTimingSample() {
            return timingSample;
        }

        public long getLastWorkDurationNanos() {
            return lastWorkDurationNanos;
        }

        public long getAverageWorkDurationNanos() {
            return averageWorkDurationNanos;
        }

        public long getMaxWorkDurationNanos() {
            return maxWorkDurationNanos;
        }

        public String getTimingKey() {
            if (!timingSample) return PERFORMANCE_NONE_TOOLTIP_KEY;

            return PERFORMANCE_SUMMARY_TOOLTIP_KEY;
        }

        public String[] getTimingArgs() {
            if (!timingSample) return new String[0];

            return new String[] {
                formatDuration(lastWorkDurationNanos),
                formatDuration(averageWorkDurationNanos),
                formatDuration(maxWorkDurationNanos)
            };
        }
    }
}