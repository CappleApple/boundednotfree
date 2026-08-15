package com.cappleapple.boundednotfree.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderNoiseClassifierTest {
    @Test void leavesSurfaceTerrainNoisesEligibleForProviderSampling() {
        assertFalse(ProviderNoiseClassifier.isSubsurface("tectonic:mountain_ridges/base"));
        assertFalse(ProviderNoiseClassifier.isSubsurface("tectonic:noise/full_continents"));
        assertFalse(ProviderNoiseClassifier.isSubsurface("minecraft:jagged"));
    }

    @Test void keepsCavesFluidsAndVeinsInLocalCoordinates() {
        assertTrue(ProviderNoiseClassifier.isSubsurface("tectonic:cave/cheese"));
        assertTrue(ProviderNoiseClassifier.isSubsurface("tectonic:underground_river/height"));
        assertTrue(ProviderNoiseClassifier.isSubsurface("tectonic:lava_tunnel/ridges"));
        assertTrue(ProviderNoiseClassifier.isSubsurface("minecraft:noodle"));
        assertTrue(ProviderNoiseClassifier.isSubsurface("minecraft:ore_vein_a"));
    }
}
