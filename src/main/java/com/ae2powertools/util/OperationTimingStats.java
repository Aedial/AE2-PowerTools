package com.ae2powertools.util;


/**
 * Rolling timing stats for server-side work samples.
 * Values reset when the tile entity is recreated after world load.
 */
public final class OperationTimingStats {

    private long sampleCount;
    private long lastDurationNanos;
    private long totalDurationNanos;
    private long maxDurationNanos;

    public boolean hasSamples() {
        return sampleCount > 0;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public long getLastDurationNanos() {
        return lastDurationNanos;
    }

    public long getAverageDurationNanos() {
        if (sampleCount <= 0) return 0L;

        return totalDurationNanos / sampleCount;
    }

    public long getMaxDurationNanos() {
        return maxDurationNanos;
    }

    public void recordSample(long durationNanos) {
        long clampedDuration = Math.max(0L, durationNanos);

        sampleCount++;
        lastDurationNanos = clampedDuration;
        totalDurationNanos = SaturatingMath.saturatingAdd(totalDurationNanos, clampedDuration);

        if (clampedDuration > maxDurationNanos) maxDurationNanos = clampedDuration;
    }
}