package com.ae2powertools.features.maintainer;

import net.minecraft.nbt.NBTTagCompound;

import com.ae2powertools.util.FormatUtil;


/**
 * Shared tooltip helpers for Better Level Maintainer probe integrations.
 */
public final class MaintainerProbeHelper {

    public static final String STATUS_CPU_TOOLTIP_KEY = "tooltip.ae2powertools.maintainer.cpus";
    public static final String STATUS_RECIPE_TOOLTIP_KEY = "tooltip.ae2powertools.maintainer.recipes";
    public static final String PERFORMANCE_NONE_TOOLTIP_KEY = "tooltip.ae2powertools.performance.none";
    public static final String PERFORMANCE_SUMMARY_TOOLTIP_KEY = "tooltip.ae2powertools.performance.summary";

    public static final String WAILA_HAS_VALID_TILE_KEY = "ae2powertoolsHasMaintainerProbeData";
    public static final String WAILA_ACTIVE_CPU_COUNT_KEY = "ae2powertoolsMaintainerActiveCpuCount";
    public static final String WAILA_TOTAL_CPU_COUNT_KEY = "ae2powertoolsMaintainerTotalCpuCount";
    public static final String WAILA_RUNNING_RECIPE_COUNT_KEY = "ae2powertoolsMaintainerRunningRecipeCount";
    public static final String WAILA_TOTAL_RECIPE_COUNT_KEY = "ae2powertoolsMaintainerTotalRecipeCount";
    public static final String WAILA_FAILED_RECIPE_COUNT_KEY = "ae2powertoolsMaintainerFailedRecipeCount";
    public static final String WAILA_POST_ERROR_RECIPE_COUNT_KEY = "ae2powertoolsMaintainerPostErrorRecipeCount";
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
            maintainer.getActiveCpuCount(),
            maintainer.getTotalCpuCount(),
            maintainer.getRunningRecipeCount(),
            maintainer.getTotalRecipeCount(),
            maintainer.getFailedRecipeCount(),
            maintainer.getPostErrorRecipeCount(),
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
            tag.getInteger(WAILA_ACTIVE_CPU_COUNT_KEY),
            tag.getInteger(WAILA_TOTAL_CPU_COUNT_KEY),
            tag.getInteger(WAILA_RUNNING_RECIPE_COUNT_KEY),
            tag.getInteger(WAILA_TOTAL_RECIPE_COUNT_KEY),
            tag.getInteger(WAILA_FAILED_RECIPE_COUNT_KEY),
            tag.getInteger(WAILA_POST_ERROR_RECIPE_COUNT_KEY),
            tag.getBoolean(WAILA_HAS_TIMING_SAMPLE_KEY),
            tag.getLong(WAILA_LAST_WORK_DURATION_NANOS_KEY),
            tag.getLong(WAILA_AVERAGE_WORK_DURATION_NANOS_KEY),
            tag.getLong(WAILA_MAX_WORK_DURATION_NANOS_KEY));
    }

    public static void writeWailaData(TileBetterLevelMaintainer maintainer, NBTTagCompound tag) {
        if (maintainer == null || tag == null) return;

        ProbeData data = collectData(maintainer);

        tag.setBoolean(WAILA_HAS_VALID_TILE_KEY, data.isValid());
        tag.setInteger(WAILA_ACTIVE_CPU_COUNT_KEY, data.getActiveCpuCount());
        tag.setInteger(WAILA_TOTAL_CPU_COUNT_KEY, data.getTotalCpuCount());
        tag.setInteger(WAILA_RUNNING_RECIPE_COUNT_KEY, data.getRunningRecipeCount());
        tag.setInteger(WAILA_TOTAL_RECIPE_COUNT_KEY, data.getTotalRecipeCount());
        tag.setInteger(WAILA_FAILED_RECIPE_COUNT_KEY, data.getFailedRecipeCount());
        tag.setInteger(WAILA_POST_ERROR_RECIPE_COUNT_KEY, data.getPostErrorRecipeCount());
        tag.setBoolean(WAILA_HAS_TIMING_SAMPLE_KEY, data.hasTimingSample());
        tag.setLong(WAILA_LAST_WORK_DURATION_NANOS_KEY, data.getLastWorkDurationNanos());
        tag.setLong(WAILA_AVERAGE_WORK_DURATION_NANOS_KEY, data.getAverageWorkDurationNanos());
        tag.setLong(WAILA_MAX_WORK_DURATION_NANOS_KEY, data.getMaxWorkDurationNanos());
    }

    public static final class ProbeData {

        private final boolean valid;
        private final int activeCpuCount;
        private final int totalCpuCount;
        private final int runningRecipeCount;
        private final int totalRecipeCount;
        private final int failedRecipeCount;
        private final int postErrorRecipeCount;
        private final boolean timingSample;
        private final long lastWorkDurationNanos;
        private final long averageWorkDurationNanos;
        private final long maxWorkDurationNanos;

        private ProbeData(boolean valid,
                          int activeCpuCount,
                          int totalCpuCount,
                          int runningRecipeCount,
                          int totalRecipeCount,
                          int failedRecipeCount,
                          int postErrorRecipeCount,
                          boolean timingSample,
                          long lastWorkDurationNanos,
                          long averageWorkDurationNanos,
                          long maxWorkDurationNanos) {
            this.valid = valid;
            this.activeCpuCount = Math.max(0, activeCpuCount);
            this.totalCpuCount = Math.max(0, totalCpuCount);
            this.runningRecipeCount = Math.max(0, runningRecipeCount);
            this.totalRecipeCount = Math.max(0, totalRecipeCount);
            this.failedRecipeCount = Math.max(0, failedRecipeCount);
            this.postErrorRecipeCount = Math.max(0, postErrorRecipeCount);
            this.timingSample = timingSample;
            this.lastWorkDurationNanos = Math.max(0L, lastWorkDurationNanos);
            this.averageWorkDurationNanos = Math.max(0L, averageWorkDurationNanos);
            this.maxWorkDurationNanos = Math.max(0L, maxWorkDurationNanos);
        }

        public static ProbeData invalid() {
            return new ProbeData(false, 0, 0, 0, 0, 0, 0, false, 0L, 0L, 0L);
        }

        public boolean isValid() {
            return valid;
        }

        public int getActiveCpuCount() {
            return activeCpuCount;
        }

        public int getTotalCpuCount() {
            return totalCpuCount;
        }

        public int getRunningRecipeCount() {
            return runningRecipeCount;
        }

        public int getTotalRecipeCount() {
            return totalRecipeCount;
        }

        public int getFailedRecipeCount() {
            return failedRecipeCount;
        }

        public int getPostErrorRecipeCount() {
            return postErrorRecipeCount;
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

        public String[] getCpuStatusArgs() {
            return new String[] {
                Integer.toString(activeCpuCount),
                Integer.toString(totalCpuCount)
            };
        }

        public String[] getRecipeStatusArgs() {
            return new String[] {
                Integer.toString(runningRecipeCount),
                Integer.toString(totalRecipeCount),
                Integer.toString(failedRecipeCount),
                Integer.toString(postErrorRecipeCount)
            };
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