package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.boundary.BasicBoundary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryClampTest {
    @Test
    void leavesSafeCoordinatesUnchanged() {
        BasicBoundary circle = new BasicBoundary(BasicBoundary.Type.CIRCLE, 100, 100);
        BoundaryClamp.Position result = BoundaryClamp.clamp(circle, 10, -20, 40, 20, 1);
        assertEquals(40, result.x());
        assertEquals(20, result.z());
    }

    @Test
    void clampsToTheRequestedInsetFromTheEdge() {
        BasicBoundary circle = new BasicBoundary(BasicBoundary.Type.CIRCLE, 100, 100);
        BoundaryClamp.Position result = BoundaryClamp.clamp(circle, 10, -20, 210, -20, 1);
        assertEquals(109, result.x(), 1.0e-9);
        assertEquals(-20, result.z(), 1.0e-9);
        assertEquals(1, circle.measure(result.x() - 10, result.z() + 20).blocksToEdge(), 1.0e-9);
    }

    @Test
    void handlesNonCircularBoundariesAlongTheTravelDirection() {
        BasicBoundary square = new BasicBoundary(BasicBoundary.Type.SQUARE, 100, 100);
        BoundaryClamp.Position result = BoundaryClamp.clamp(square, 0, 0, 200, 200, 2);
        assertTrue(square.contains(result.x(), result.z()));
        assertEquals(2, square.measure(result.x(), result.z()).blocksToEdge(), 1.0e-9);
    }

    @Test
    void fallsBackToTheCenterWhenTheInsetExceedsTheWorld() {
        BasicBoundary circle = new BasicBoundary(BasicBoundary.Type.CIRCLE, 3, 3);
        BoundaryClamp.Position result = BoundaryClamp.clamp(circle, 12, -7, 100, 100, 20);
        assertEquals(12, result.x());
        assertEquals(-7, result.z());
    }
}
