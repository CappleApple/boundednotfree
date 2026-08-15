package com.cappleapple.boundednotfree.boundary;

import com.cappleapple.boundednotfree.api.BoundaryShape;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PolygonBoundary implements BoundaryShape {
    public record Point(double x, double z) {}
    private final String type;
    private final List<Point> points;

    public PolygonBoundary(String type, List<Point> points) {
        this.type = type;
        this.points = List.copyOf(points);
        validate(this.points);
    }

    public static PolygonBoundary star(int points, double innerRadius, double outerRadius, double rotationDegrees) {
        if (points < 3) throw new IllegalArgumentException("Star points must be at least 3");
        if (innerRadius <= 0 || outerRadius <= innerRadius) throw new IllegalArgumentException("Star radii require 0 < inner < outer");
        java.util.ArrayList<Point> vertices = new java.util.ArrayList<>(points * 2);
        double rotation = Math.toRadians(rotationDegrees);
        for (int i = 0; i < points * 2; i++) {
            double angle = rotation + Math.PI * i / points;
            double radius = (i & 1) == 0 ? outerRadius : innerRadius;
            vertices.add(new Point(Math.cos(angle) * radius, Math.sin(angle) * radius));
        }
        return new PolygonBoundary("STAR", vertices);
    }

    @Override public String type() { return type; }

    @Override
    public boolean contains(double x, double z) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            Point a = points.get(i), b = points.get(j);
            if (((a.z > z) != (b.z > z)) && x < (b.x - a.x) * (z - a.z) / (b.z - a.z) + a.x) inside = !inside;
        }
        return inside || onBoundary(x, z);
    }

    @Override
    public double normalizedDistance(double x, double z) {
        double d = Math.hypot(x, z);
        if (d == 0) return 0;
        return d / directionalRadius(x, z);
    }

    @Override
    public double directionalRadius(double x, double z) {
        double length = Math.hypot(x, z);
        if (length == 0) return points.stream().mapToDouble(p -> Math.hypot(p.x, p.z)).min().orElse(0);
        double dx = x / length, dz = z / length, nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i), b = points.get((i + 1) % points.size());
            double ex = b.x - a.x, ez = b.z - a.z;
            double cross = dx * ez - dz * ex;
            if (Math.abs(cross) < 1.0e-12) continue;
            double ray = (a.x * ez - a.z * ex) / cross;
            double edge = (a.x * dz - a.z * dx) / cross;
            if (ray >= 0 && edge >= -1.0e-9 && edge <= 1.0 + 1.0e-9) nearest = Math.min(nearest, ray);
        }
        return nearest;
    }

    private boolean onBoundary(double x, double z) {
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i), b = points.get((i + 1) % points.size());
            double cross = (x - a.x) * (b.z - a.z) - (z - a.z) * (b.x - a.x);
            if (Math.abs(cross) < 1.0e-7 && x >= Math.min(a.x, b.x) && x <= Math.max(a.x, b.x)
                    && z >= Math.min(a.z, b.z) && z <= Math.max(a.z, b.z)) return true;
        }
        return false;
    }

    private static void validate(List<Point> points) {
        if (points.size() < 3) throw new IllegalArgumentException("Polygon requires at least 3 vertices");
        Set<Point> unique = new HashSet<>(points);
        if (unique.size() != points.size()) throw new IllegalArgumentException("Polygon contains duplicate vertices");
        double twiceArea = 0;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i), b = points.get((i + 1) % points.size());
            if (!Double.isFinite(a.x) || !Double.isFinite(a.z)) throw new IllegalArgumentException("Polygon coordinates must be finite");
            twiceArea += a.x * b.z - b.x * a.z;
            for (int j = i + 1; j < points.size(); j++) {
                if (j == i || j == (i + 1) % points.size() || (i == 0 && j == points.size() - 1)) continue;
                Point c = points.get(j), d = points.get((j + 1) % points.size());
                if (segmentsIntersect(a, b, c, d)) throw new IllegalArgumentException("Polygon self-intersects");
            }
        }
        if (Math.abs(twiceArea) < 1.0e-9) throw new IllegalArgumentException("Polygon has zero area");
    }

    private static boolean segmentsIntersect(Point a, Point b, Point c, Point d) {
        double abC = orient(a, b, c), abD = orient(a, b, d), cdA = orient(c, d, a), cdB = orient(c, d, b);
        return abC * abD < 0 && cdA * cdB < 0;
    }

    private static double orient(Point a, Point b, Point c) { return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x); }
}
