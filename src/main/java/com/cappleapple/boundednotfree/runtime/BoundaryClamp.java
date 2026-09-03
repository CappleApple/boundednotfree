package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.api.BoundaryShape;

final class BoundaryClamp {
    record Position(double x, double z) {}

    private BoundaryClamp() {}

    static Position clamp(BoundaryShape boundary, double centerX, double centerZ,
                          double worldX, double worldZ, double inset) {
        double safeInset = Math.max(0.0, inset);
        double localX = worldX - centerX;
        double localZ = worldZ - centerZ;
        double distance = Math.hypot(localX, localZ);
        double radius = boundary.directionalRadius(localX, localZ);
        if (Double.isFinite(distance) && Double.isFinite(radius)
                && distance <= Math.max(0.0, radius - safeInset)) {
            return new Position(worldX, worldZ);
        }
        if (!Double.isFinite(distance) || !Double.isFinite(radius) || distance == 0.0) {
            return new Position(centerX, centerZ);
        }
        double scale = Math.max(0.0, radius - safeInset) / distance;
        return new Position(centerX + localX * scale, centerZ + localZ * scale);
    }
}
