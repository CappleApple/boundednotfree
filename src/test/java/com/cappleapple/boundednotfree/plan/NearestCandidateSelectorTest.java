package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NearestCandidateSelectorTest {
    @Test
    void keepsOriginalWhenItAlreadyBelongsToTheProfile() {
        assertEquals("cold", NearestCandidateSelector.nearest(Set.of("cold", "warm"), "cold",
                ignored -> List.<Long>of(), Comparator.naturalOrder(), "warm", point -> point));
    }

    @Test
    void choosesNearestCandidateInsteadOfCoordinateNoise() {
        Map<String, List<Long>> fitness = Map.of(
                "cold", List.of(10L, 20L),
                "warm", List.of(80L));

        assertEquals("cold", NearestCandidateSelector.nearest(Set.of("warm", "cold"), "outside",
                fitness::get, Comparator.naturalOrder(), "warm", value -> value));
    }

    @Test
    void fallsBackDeterministicallyWhenProviderHasNoPointsForCandidates() {
        assertEquals("fallback", NearestCandidateSelector.nearest(Set.of("custom"), "outside",
                ignored -> List.<Long>of(), Comparator.naturalOrder(), "fallback", point -> point));
    }
}
