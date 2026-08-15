package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DimensionPlanProviderMappingTest {
    @Test void foldsAtNativeBlockScaleInsideVerifiedPatch() {
        assertEquals(0, DimensionPlan.foldProviderOffset(0));
        assertEquals(1, DimensionPlan.foldProviderOffset(1));
        assertEquals(96, DimensionPlan.foldProviderOffset(96));
        assertEquals(95, DimensionPlan.foldProviderOffset(97));
        assertEquals(0, DimensionPlan.foldProviderOffset(192));
        assertEquals(-96, DimensionPlan.foldProviderOffset(288));
        assertEquals(0, DimensionPlan.foldProviderOffset(384));
    }

    @Test void remainsContinuousAcrossPositiveAndNegativeFolds() {
        for (long coordinate = -1_000; coordinate < 1_000; coordinate++) {
            int current = DimensionPlan.foldProviderOffset(coordinate);
            int next = DimensionPlan.foldProviderOffset(coordinate + 1);
            assertEquals(1, Math.abs(next - current));
        }
    }
}
