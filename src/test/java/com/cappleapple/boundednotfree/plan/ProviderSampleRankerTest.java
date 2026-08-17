package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProviderSampleRankerTest {
    @Test
    void mountainPoolsPreferNaturalReliefOverFloatingShelvesOrLoneSpikes() {
        double natural = ProviderSampleRanker.score(false, true, 3, 95, 255, 175, 63, 320);
        double floatingShelf = ProviderSampleRanker.score(false, true, 3, 204, 266, 244, 63, 320);
        double loneSpike = ProviderSampleRanker.score(false, true, 3, 70, 240, 110, 63, 320);
        assertTrue(natural > floatingShelf);
        assertTrue(natural > loneSpike);
    }

    @Test
    void oceanPoolsPreferPatchesWhoseHighestPointStaysLow() {
        double ocean = ProviderSampleRanker.score(true, false, -1, 25, 48, 38, 63, 320);
        double hill = ProviderSampleRanker.score(true, false, -1, 30, 130, 80, 63, 320);
        assertTrue(ocean > hill);
    }

    @Test
    void ordinaryBiomesPreferTerrainNearTheirClimateExpectation() {
        double aligned = ProviderSampleRanker.score(false, false, 1, 75, 100, 87, 63, 320);
        double extreme = ProviderSampleRanker.score(false, false, 1, 180, 240, 210, 63, 320);
        assertTrue(aligned > extreme);
    }

    @Test
    void ceilingClippedMountainPatchesLoseToIntactMountains() {
        double clipped = ProviderSampleRanker.penalizeCeilingClipping(1000, 319, 320);
        double intact = ProviderSampleRanker.penalizeCeilingClipping(800, 280, 320);
        assertTrue(intact > clipped);
    }
}
