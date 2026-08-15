package com.cappleapple.boundednotfree.boundary;

import com.cappleapple.boundednotfree.api.BoundaryShape;
import com.cappleapple.boundednotfree.config.LayoutConfig;
import java.util.List;

public final class BoundaryFactory {
    private static final double MAX_COORDINATE = 29_999_984.0;
    private BoundaryFactory() {}

    public static BoundaryShape create(LayoutConfig.Dimension config, long seed) {
        double x = config.extentX > 0 ? config.extentX : config.radius;
        double z = config.extentZ > 0 ? config.extentZ : config.radius;
        validateExtent(config.centerX, x, "X");
        validateExtent(config.centerZ, z, "Z");
        return switch (config.boundaryShape.toUpperCase(java.util.Locale.ROOT)) {
            case "CIRCLE" -> new BasicBoundary(BasicBoundary.Type.CIRCLE, x, z);
            case "SQUARE" -> new BasicBoundary(BasicBoundary.Type.SQUARE, x, z);
            case "DIAMOND" -> new BasicBoundary(BasicBoundary.Type.DIAMOND, x, z);
            case "HEXAGON" -> new BasicBoundary(BasicBoundary.Type.HEXAGON, x, z);
            case "STAR" -> PolygonBoundary.star(config.starPoints, config.starInnerRadius, config.starOuterRadius, config.starRotation);
            case "ROUNDED_SQUARE" -> new RoundedSquareBoundary(x, z, config.cornerRadius);
            case "ORGANIC" -> new OrganicBoundary(config.radius, config.organicNoiseStrength, config.organicNoiseScale, config.organicNoiseOctaves, seed);
            case "POLYGON" -> new PolygonBoundary("POLYGON", polygonPoints(config, x, z));
            default -> throw new IllegalArgumentException("Unknown boundaryShape: " + config.boundaryShape);
        };
    }

    private static List<PolygonBoundary.Point> polygonPoints(LayoutConfig.Dimension config, double xExtent, double zExtent) {
        return config.polygonVertices.stream().map(p -> new PolygonBoundary.Point(
                p.normalized ? p.x * xExtent : p.x,
                p.normalized ? p.z * zExtent : p.z)).toList();
    }

    private static void validateExtent(double center, double extent, String axis) {
        if (!Double.isFinite(extent) || extent <= 0 || Math.abs(center) + extent > MAX_COORDINATE) {
            throw new IllegalArgumentException(axis + " boundary exceeds Minecraft's usable coordinate limit");
        }
    }
}
