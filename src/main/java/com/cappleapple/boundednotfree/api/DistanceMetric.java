package com.cappleapple.boundednotfree.api;

public record DistanceMetric(double blocksFromCenter, double normalizedFromCenter,
                             double blocksToEdge, double normalizedToEdge) {
    public boolean inside() {
        return normalizedFromCenter <= 1.0;
    }
}
