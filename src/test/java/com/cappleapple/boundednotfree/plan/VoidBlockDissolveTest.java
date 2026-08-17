package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VoidBlockDissolveTest {
    @Test
    void zeroWidthPreservesInsideBlocksAndNeverExtendsOutside() {
        assertTrue(VoidBlockDissolve.keepsBlock(true, 0, 0, 8, 1, 2, 3, 42));
        assertFalse(VoidBlockDissolve.keepsBlock(false, 0, 0, 8, 1, 2, 3, 42));
    }

    @Test
    void preservesBlocksBeyondTheConfiguredBandAndRemovesTheExactEdge() {
        assertTrue(VoidBlockDissolve.keepsBlock(true, 64, 64, 8, 1, 2, 3, 42));
        assertFalse(VoidBlockDissolve.keepsBlock(true, 0, 64, 8, 1, 2, 3, 42));
    }

    @Test
    void middleOfBandContainsBothBlocksAndHolesAcrossThreeDimensions() {
        boolean block = false, hole = false;
        for (int x = -32; x <= 32; x++) for (int y = -64; y <= 128; y++) {
            boolean kept = VoidBlockDissolve.keepsBlock(true, 32, 64, 8, x, y, 11, 42);
            block |= kept;
            hole |= !kept;
        }
        assertTrue(block);
        assertTrue(hole);
    }

    @Test
    void resultIsDeterministic() {
        boolean first = VoidBlockDissolve.keepsBlock(true, 24, 64, 12, 17, 91, -44, 1234);
        assertEquals(first, VoidBlockDissolve.keepsBlock(true, 24, 64, 12, 17, 91, -44, 1234));
    }
}
