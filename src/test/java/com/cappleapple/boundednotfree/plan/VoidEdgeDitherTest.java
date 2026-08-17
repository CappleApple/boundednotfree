package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VoidEdgeDitherTest {
    @Test
    void zeroWidthPreservesTheNominalBoundary() {
        assertTrue(VoidEdgeDither.inside(true, 0, 0, 10, 20, 42));
        assertFalse(VoidEdgeDither.inside(false, 0, 0, 10, 20, 42));
    }

    @Test
    void neverExtendsTerrainPastTheNominalBoundary() {
        for (int x = -64; x <= 64; x++) {
            assertFalse(VoidEdgeDither.inside(false, 0, 48, x, 7, 42));
        }
    }

    @Test
    void preservesEveryColumnBeyondTheConfiguredBand() {
        for (int x = -64; x <= 64; x++) {
            assertTrue(VoidEdgeDither.inside(true, 48, 48, x, 7, 42));
        }
    }

    @Test
    void transitionBandContainsBothTerrainAndVoidColumns() {
        boolean terrain = false, voidColumn = false;
        for (int x = -128; x <= 128; x++) {
            boolean inside = VoidEdgeDither.inside(true, 24, 48, x, 0, 42);
            terrain |= inside;
            voidColumn |= !inside;
        }
        assertTrue(terrain);
        assertTrue(voidColumn);
    }
}
