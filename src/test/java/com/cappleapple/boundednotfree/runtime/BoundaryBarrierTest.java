package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.boundary.BasicBoundary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryBarrierTest {
    @Test
    void selectsOnlyTheInsideEdgeOfACircle() {
        BasicBoundary circle = new BasicBoundary(BasicBoundary.Type.CIRCLE, 3, 3);
        BoundaryBarrier.Membership membership = (x, z) -> circle.contains(x, z);

        assertTrue(BoundaryBarrier.isBarrierColumn(3, 0, true, membership));
        assertTrue(BoundaryBarrier.isBarrierColumn(2, 2, true, membership));
        assertFalse(BoundaryBarrier.isBarrierColumn(0, 0, true, membership));
        assertFalse(BoundaryBarrier.isBarrierColumn(4, 0, false, membership));
    }

    @Test
    void respectsAnOffsetSquareThroughItsMembershipFunction() {
        BasicBoundary square = new BasicBoundary(BasicBoundary.Type.SQUARE, 4, 4);
        BoundaryBarrier.Membership membership = (x, z) -> square.contains(x - 100, z + 50);

        assertTrue(BoundaryBarrier.isBarrierColumn(104, -50, true, membership));
        assertFalse(BoundaryBarrier.isBarrierColumn(100, -50, true, membership));
    }
}
