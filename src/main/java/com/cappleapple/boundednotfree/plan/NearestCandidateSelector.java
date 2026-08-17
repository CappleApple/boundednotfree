package com.cappleapple.boundednotfree.plan;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Pure ranking loop shared by climate-aware biome selection and its unit tests. */
final class NearestCandidateSelector {
    private NearestCandidateSelector() {}

    static <T, P> T nearest(Set<T> candidates, T original, Function<T, List<P>> points,
                            Comparator<T> order, T fallback, ToLongFunction<P> fitness) {
        if (candidates.contains(original)) return original;
        T best = null;
        long bestFitness = Long.MAX_VALUE;
        for (T candidate : candidates.stream().sorted(order).toList()) {
            for (P point : points.apply(candidate)) {
                long candidateFitness = fitness.applyAsLong(point);
                if (candidateFitness < bestFitness) {
                    bestFitness = candidateFitness;
                    best = candidate;
                }
            }
        }
        return best == null ? fallback : best;
    }
}
