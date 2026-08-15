package com.cappleapple.boundednotfree.boundary;

import com.cappleapple.boundednotfree.api.BoundaryShape;

public record BasicBoundary(Type shape, double extentX, double extentZ) implements BoundaryShape {
    public enum Type { CIRCLE, SQUARE, DIAMOND, HEXAGON }

    public BasicBoundary {
        if (extentX <= 0 || extentZ <= 0) throw new IllegalArgumentException("Boundary extents must be positive");
    }

    @Override public String type() { return shape.name(); }

    @Override
    public double normalizedDistance(double x, double z) {
        double nx = x / extentX;
        double nz = z / extentZ;
        return switch (shape) {
            case CIRCLE -> Math.hypot(nx, nz);
            case SQUARE -> Math.max(Math.abs(nx), Math.abs(nz));
            case DIAMOND -> Math.abs(nx) + Math.abs(nz);
            case HEXAGON -> Math.max(Math.abs(nz), Math.max(Math.abs(Math.sqrt(3.0) * nx + nz) / 2.0,
                    Math.abs(Math.sqrt(3.0) * nx - nz) / 2.0));
        };
    }

    @Override
    public double directionalRadius(double x, double z) {
        double distance = Math.hypot(x, z);
        if (distance == 0) return Math.min(extentX, extentZ);
        double normalized = normalizedDistance(x, z);
        return normalized == 0 ? Double.POSITIVE_INFINITY : distance / normalized;
    }
}
