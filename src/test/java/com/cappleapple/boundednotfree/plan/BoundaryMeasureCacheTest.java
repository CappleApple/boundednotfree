package com.cappleapple.boundednotfree.plan;

import com.cappleapple.boundednotfree.api.BoundaryShape;
import com.cappleapple.boundednotfree.api.DistanceMetric;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class BoundaryMeasureCacheTest {
    @Test
    void reusesIntegralCoordinatesOnTheSameThread() {
        CountingCircle circle = new CountingCircle(100);
        BoundaryMeasureCache cache = new BoundaryMeasureCache(circle, 10, -20);

        DistanceMetric first = cache.measure(42, 17);
        DistanceMetric second = cache.measure(42, 17);

        assertSame(first, second);
        assertEquals(1, circle.measurements.get());
        assertEquals(Math.hypot(32, 37), first.blocksFromCenter(), 1e-12);
    }

    @Test
    void bypassesCacheForFractionalCoordinates() {
        CountingCircle circle = new CountingCircle(100);
        BoundaryMeasureCache cache = new BoundaryMeasureCache(circle, 0, 0);

        DistanceMetric first = cache.measure(1.5, 2.5);
        DistanceMetric second = cache.measure(1.5, 2.5);

        assertNotSame(first, second);
        assertEquals(2, circle.measurements.get());
    }

    private static final class CountingCircle implements BoundaryShape {
        private final double radius;
        private final AtomicInteger measurements = new AtomicInteger();

        private CountingCircle(double radius) { this.radius = radius; }
        @Override public String type() { return "TEST_CIRCLE"; }
        @Override public double normalizedDistance(double x, double z) { return Math.hypot(x, z) / radius; }
        @Override public double directionalRadius(double x, double z) { return radius; }
        @Override public DistanceMetric measure(double x, double z) {
            measurements.incrementAndGet();
            return BoundaryShape.super.measure(x, z);
        }
    }
}
