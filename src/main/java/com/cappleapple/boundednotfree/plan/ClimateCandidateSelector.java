package com.cappleapple.boundednotfree.plan;

import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.Comparator;

/** Selects the provider-native biome candidate closest to the sampled climate point. */
final class ClimateCandidateSelector {
    private ClimateCandidateSelector() {}

    static <T> T nearest(Set<T> candidates, T original, Climate.TargetPoint target,
                         Function<T, List<Climate.ParameterPoint>> points,
                         Comparator<T> order, T fallback) {
        return NearestCandidateSelector.nearest(candidates, original, points, order, fallback,
                point -> fitness(point, target));
    }

    /** Matches vanilla's package-private ParameterPoint.fitness implementation. */
    static long fitness(Climate.ParameterPoint point, Climate.TargetPoint target) {
        return square(point.temperature().distance(target.temperature()))
                + square(point.humidity().distance(target.humidity()))
                + square(point.continentalness().distance(target.continentalness()))
                + square(point.erosion().distance(target.erosion()))
                + square(point.depth().distance(target.depth()))
                + square(point.weirdness().distance(target.weirdness()))
                + square(point.offset());
    }

    private static long square(long value) {
        return value * value;
    }
}
