package com.cappleapple.boundednotfree.plan;

import com.cappleapple.boundednotfree.api.DistanceMetric;

/** Pure rim falloff math shared by runtime world generation and unit tests. */
public final class RimInfluence {
    private RimInfluence() {}

    public static double factor(boolean enabled, double rimWidth, double blendWidth, DistanceMetric metric) {
        if (!enabled || rimWidth <= 0 || !metric.inside() || metric.blocksToEdge() > rimWidth) return 0;
        double width = Math.max(0, Math.min(blendWidth, rimWidth));
        if (width == 0) return 1;
        double linear = clamp01((rimWidth - metric.blocksToEdge()) / width);
        return linear * linear * (3 - 2 * linear);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
