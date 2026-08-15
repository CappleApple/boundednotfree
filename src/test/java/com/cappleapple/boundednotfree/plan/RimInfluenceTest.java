package com.cappleapple.boundednotfree.plan;

import com.cappleapple.boundednotfree.api.DistanceMetric;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RimInfluenceTest {
    @Test void isZeroWhenDisabledOutsideOrPastTheRim() {
        assertEquals(0, factor(false, 0));
        assertEquals(0, RimInfluence.factor(true, 256, 128, new DistanceMetric(101, 1.01, -1, -0.01)));
        assertEquals(0, factor(true, 257));
    }

    @Test void smoothlyBlendsFromTheInnerRimBoundary() {
        assertEquals(0, factor(true, 256), 1e-12);
        assertEquals(0.15625, factor(true, 224), 1e-12);
        assertEquals(0.5, factor(true, 192), 1e-12);
        assertEquals(0.84375, factor(true, 160), 1e-12);
        assertEquals(1, factor(true, 128), 1e-12);
    }

    @Test void remainsFullyInfluencedThroughTheOuterHalfOfTheRim() {
        assertEquals(1, factor(true, 64), 1e-12);
        assertEquals(1, factor(true, 0), 1e-12);
    }

    @Test void zeroBlendWidthMakesTheWholeRimFullyInfluenced() {
        assertEquals(1, RimInfluence.factor(true, 256, 0, metric(256)), 1e-12);
        assertEquals(1, RimInfluence.factor(true, 256, 0, metric(1)), 1e-12);
    }

    @Test void blendWidthLargerThanTheRimIsClampedToTheRim() {
        assertEquals(0, RimInfluence.factor(true, 256, 1024, metric(256)), 1e-12);
        assertEquals(0.5, RimInfluence.factor(true, 256, 1024, metric(128)), 1e-12);
        assertEquals(1, RimInfluence.factor(true, 256, 1024, metric(0)), 1e-12);
    }

    private static double factor(boolean enabled, double edgeDistance) {
        return RimInfluence.factor(enabled, 256, 128, metric(edgeDistance));
    }

    private static DistanceMetric metric(double edgeDistance) {
        return new DistanceMetric(1000 - edgeDistance, (1000 - edgeDistance) / 1000, edgeDistance, edgeDistance / 1000);
    }
}
