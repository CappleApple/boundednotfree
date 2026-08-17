package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DimensionPlanProviderMappingTest {
    @Test void foldsAtNativeBlockScaleInsideVerifiedPatch() {
        assertEquals(0, ProviderCoordinateFold.fold(0, 96));
        assertEquals(1, ProviderCoordinateFold.fold(1, 96));
        assertEquals(96, ProviderCoordinateFold.fold(96, 96));
        assertEquals(95, ProviderCoordinateFold.fold(97, 96));
        assertEquals(0, ProviderCoordinateFold.fold(192, 96));
        assertEquals(-96, ProviderCoordinateFold.fold(288, 96));
        assertEquals(0, ProviderCoordinateFold.fold(384, 96));
    }

    @Test void remainsContinuousAcrossPositiveAndNegativeFolds() {
        for (long coordinate = -1_000; coordinate < 1_000; coordinate++) {
            int current = ProviderCoordinateFold.fold(coordinate, 96);
            int next = ProviderCoordinateFold.fold(coordinate + 1, 96);
            assertEquals(1, Math.abs(next - current));
        }
    }
}
