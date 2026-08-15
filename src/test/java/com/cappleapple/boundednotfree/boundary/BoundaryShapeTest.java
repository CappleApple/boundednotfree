package com.cappleapple.boundednotfree.boundary;

import com.cappleapple.boundednotfree.api.BoundaryShape;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BoundaryShapeTest {
    @Test void circleMetrics() { assertShape(new BasicBoundary(BasicBoundary.Type.CIRCLE, 100, 100), 0, 0, 101, 0); }
    @Test void squareMetrics() { assertShape(new BasicBoundary(BasicBoundary.Type.SQUARE, 100, 100), 90, 90, 101, 0); }
    @Test void diamondMetrics() { assertShape(new BasicBoundary(BasicBoundary.Type.DIAMOND, 100, 100), 40, 40, 60, 60); }
    @Test void hexagonMetrics() { assertShape(new BasicBoundary(BasicBoundary.Type.HEXAGON, 100, 100), 0, 90, 0, 110); }
    @Test void starMetrics() { assertShape(PolygonBoundary.star(5, 50, 100, 0), 10, 0, 120, 0); }
    @Test void roundedSquareMetrics() { assertShape(new RoundedSquareBoundary(100, 100, 20), 0, 0, 120, 0); }
    @Test void organicMetrics() { assertShape(new OrganicBoundary(100, 10, .01, 2, 42), 0, 0, 150, 0); }
    @Test void polygonMetrics() { assertShape(new PolygonBoundary("POLYGON", List.of(new PolygonBoundary.Point(-100,-100), new PolygonBoundary.Point(100,-100), new PolygonBoundary.Point(100,100), new PolygonBoundary.Point(-100,100))), 0, 0, 110, 0); }

    @Test void organicIsQueryOrderIndependent() {
        OrganicBoundary first = new OrganicBoundary(1000, 200, .001, 3, 99);
        double expected = first.directionalRadius(12, 47);
        for (int i = 0; i < 100; i++) first.directionalRadius(i * 17, i * -31);
        assertEquals(expected, first.directionalRadius(12, 47));
        assertEquals(expected, new OrganicBoundary(1000, 200, .001, 3, 99).directionalRadius(12, 47));
    }

    @Test void rejectsInvalidPolygonAndStar() {
        assertThrows(IllegalArgumentException.class, () -> new PolygonBoundary("POLYGON", List.of(new PolygonBoundary.Point(0,0), new PolygonBoundary.Point(1,1))));
        assertThrows(IllegalArgumentException.class, () -> PolygonBoundary.star(2, 10, 20, 0));
        assertThrows(IllegalArgumentException.class, () -> PolygonBoundary.star(5, 20, 10, 0));
    }

    private static void assertShape(BoundaryShape shape, double insideX, double insideZ, double outsideX, double outsideZ) {
        assertTrue(shape.contains(insideX, insideZ), shape.type() + " inside");
        assertFalse(shape.contains(outsideX, outsideZ), shape.type() + " outside");
        assertEquals(0, shape.normalizedDistance(0, 0));
        assertEquals(1, shape.normalizedDistance(shape.directionalRadius(1, 0), 0), 1e-6);
        assertTrue(shape.measure(insideX, insideZ).normalizedToEdge() >= 0);
    }
}
