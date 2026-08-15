package com.cappleapple.boundednotfree.api;

public interface BoundaryShape {
    String type();

    double normalizedDistance(double x, double z);

    double directionalRadius(double x, double z);

    default boolean contains(double x, double z) {
        return normalizedDistance(x, z) <= 1.0;
    }

    default DistanceMetric measure(double x, double z) {
        double blocks = Math.hypot(x, z);
        double normalized = normalizedDistance(x, z);
        double radius = directionalRadius(x, z);
        return new DistanceMetric(blocks, normalized, Math.max(0.0, radius - blocks), Math.max(0.0, 1.0 - normalized));
    }
}
