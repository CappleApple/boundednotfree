package com.cappleapple.boundednotfree.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocateSearchBoundsTest {
    @Test
    void keepsTheVanillaRadiusWhenTheEdgeIsFartherAway() {
        assertEquals(256, LocateSearchBounds.blockRadius(500, 256));
    }

    @Test
    void stopsBeforeTheFirstWholeBlockPastTheEdge() {
        assertEquals(127, LocateSearchBounds.blockRadius(127.999, 256));
        assertEquals(0, LocateSearchBounds.blockRadius(0.999, 256));
    }

    @Test
    void rejectsInvalidOrNonPositiveClearance() {
        assertEquals(0, LocateSearchBounds.blockRadius(0, 256));
        assertEquals(0, LocateSearchBounds.blockRadius(Double.NaN, 256));
        assertEquals(0, LocateSearchBounds.blockRadius(100, 0));
    }
}
