package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RimCaveWallTest {
    private static final long SEED = 42;

    @Test
    void leavesOutsideAndBeyondTheConfiguredBandNative() {
        assertEquals(0.37, shape(0.37, false, 8, 80, 0.62), 0.0);
        assertEquals(0.37, shape(0.37, true, 96, 80, 0.62), 0.0);
    }

    @Test
    void buildsSolidRockWhenCaveCarvingIsDisabled() {
        assertTrue(shape(-0.8, true, 2, 80, 1.0) > 0.9);
        assertTrue(shape(-0.8, true, 48, 80, 1.0) > 0.9);
    }

    @Test
    void exposedFaceContainsBothRockAndCaveOpenings() {
        Counts counts = countFace(2, 0.62);
        assertTrue(counts.rock() > 100, "expected substantial rock on the exposed wall");
        assertTrue(counts.air() > 20, "expected multiple caves to open onto the exposed wall");
    }

    @Test
    void cavesContinueThroughTheWallInsteadOfMakingOneEmptyChamber() {
        Counts counts = countFace(48, 0.62);
        assertTrue(counts.rock() > 100, "expected rock partitions inside the wall");
        assertTrue(counts.air() > 20, "expected caves inside the wall");
    }

    @Test
    void preservesNativeTerrainAboveAndBelowTheConfiguredWall() {
        assertEquals(0.37, shape(0.37, true, 24, -64, 0.62), 0.0);
        assertEquals(0.37, shape(0.37, true, 24, 208, 0.62), 0.0);
    }

    @Test
    void innerEdgeFadesBackTowardNativeDensity() {
        double fullWall = shape(-0.8, true, 48, 80, 1.0);
        double fadingWall = shape(-0.8, true, 94, 80, 1.0);
        assertTrue(fullWall > fadingWall);
        assertTrue(fadingWall > -0.8);
    }

    @Test
    void caveThresholdControlsHowMuchRockIsExcavated() {
        assertTrue(countFace(2, 0.45).air() > countFace(2, 0.80).air());
    }

    @Test
    void shapeIsDeterministic() {
        double first = RimCaveWall.apply(0.2, true, 32, 96, -48, 192,
                48, 12, 32, 0.62, 17, 95, -11, SEED);
        assertEquals(first, RimCaveWall.apply(0.2, true, 32, 96, -48, 192,
                48, 12, 32, 0.62, 17, 95, -11, SEED), 0.0);
    }

    private static Counts countFace(double edge, double threshold) {
        int rock = 0;
        int air = 0;
        for (int z = -256; z <= 256; z += 4) {
            for (int y = -24; y <= 176; y += 4) {
                double density = RimCaveWall.apply(-0.8, true, edge, 96, -48, 192,
                        48, 12, 32, threshold, 509, y, z, SEED);
                if (density > 0.4) rock++;
                if (density < -0.4) air++;
            }
        }
        return new Counts(rock, air);
    }

    private static double shape(double density, boolean inside, double edge, int y, double threshold) {
        return RimCaveWall.apply(density, inside, edge, 96, -48, 192,
                48, 0, 32, threshold, 17, y, -11, SEED);
    }

    private record Counts(int rock, int air) {}
}
