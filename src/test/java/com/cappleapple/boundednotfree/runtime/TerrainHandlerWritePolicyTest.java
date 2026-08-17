package com.cappleapple.boundednotfree.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TerrainHandlerWritePolicyTest {
    @Test
    void voidOutsideRejectsBlocksButStillAllowsClearing() {
        assertFalse(BoundaryWritePolicy.allows(true, false, false, false, false));
        assertTrue(BoundaryWritePolicy.allows(true, false, false, true, false));
    }

    @Test
    void normalOutsideAndInsideVoidWritesRemainUnchanged() {
        assertTrue(BoundaryWritePolicy.allows(false, false, false, false, false));
        assertTrue(BoundaryWritePolicy.allows(true, true, false, false, false));
    }

    @Test
    void barrierColumnsAcceptOnlyBarrierBlocks() {
        assertFalse(BoundaryWritePolicy.allows(false, true, true, false, false));
        assertFalse(BoundaryWritePolicy.allows(false, true, true, true, false));
        assertTrue(BoundaryWritePolicy.allows(false, true, true, false, true));
    }
}
