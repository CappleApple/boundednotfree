package com.cappleapple.boundednotfree.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderTerrainBlendTest {
    @Test void preservesLocalDensityAtTheStartOfTheBlend() {
        assertEquals(0.25, ProviderTerrainBlend.combine(0.25, 1.0, 0));
        assertEquals(0.4375, ProviderTerrainBlend.combine(0.25, 1.0, 0.25));
    }

    @Test void transitionsToTheProviderDensityInBothDirections() {
        assertEquals(0.8125, ProviderTerrainBlend.combine(0.25, 1.0, 0.75));
        assertEquals(1.0, ProviderTerrainBlend.combine(0.25, 1.0, 1.0));
        assertEquals(-1.0, ProviderTerrainBlend.combine(0.75, -1.0, 1.0));
    }
}
