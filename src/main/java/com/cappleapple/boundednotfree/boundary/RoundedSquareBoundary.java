package com.cappleapple.boundednotfree.boundary;

import com.cappleapple.boundednotfree.api.BoundaryShape;

public record RoundedSquareBoundary(double extentX, double extentZ, double cornerRadius) implements BoundaryShape {
    public RoundedSquareBoundary {
        if (extentX <= 0 || extentZ <= 0) throw new IllegalArgumentException("Extents must be positive");
        if (cornerRadius < 0 || cornerRadius > Math.min(extentX, extentZ)) throw new IllegalArgumentException("Invalid corner radius");
    }
    @Override public String type() { return "ROUNDED_SQUARE"; }
    @Override public boolean contains(double x, double z) {
        double qx = Math.abs(x) - extentX + cornerRadius;
        double qz = Math.abs(z) - extentZ + cornerRadius;
        return Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) <= cornerRadius;
    }
    @Override public double normalizedDistance(double x, double z) {
        if (x == 0 && z == 0) return 0;
        return Math.hypot(x, z) / directionalRadius(x, z);
    }
    @Override public double directionalRadius(double x, double z) {
        double d = Math.hypot(x, z);
        if (d == 0) return Math.min(extentX, extentZ);
        double dx = x / d, dz = z / d, low = 0, high = Math.hypot(extentX, extentZ) * 2;
        for (int i = 0; i < 56; i++) {
            double mid = (low + high) * 0.5;
            if (contains(dx * mid, dz * mid)) low = mid; else high = mid;
        }
        return low;
    }
}
