package com.cappleapple.boundednotfree.plan;

/** Pure density shaping for the optional provider-independent cave-wall rim. */
final class RimCaveWall {
    private static final double SOLID_TARGET = 1.0;
    private static final double AIR_TARGET = -1.0;

    private RimCaveWall() {}

    static double apply(double nativeDensity, boolean nominalInside, double blocksToEdge,
                        double width, double floorY, double topY,
                        double surfaceNoiseScale, double surfaceNoiseStrength,
                        double caveNoiseScale, double caveThreshold,
                        int x, int y, int z, long seed) {
        if (!nominalInside || width <= 0 || blocksToEdge < 0 || blocksToEdge >= width) {
            return nativeDensity;
        }

        double surfaceScale = Math.max(1.0, surfaceNoiseScale);
        double broadSurface = signedNoise2d(x / surfaceScale, z / surfaceScale, seed);
        double detailSurface = signedNoise2d(x / Math.max(1.0, surfaceScale * 0.43),
                z / Math.max(1.0, surfaceScale * 0.43), seed ^ 0x4341564557414C4CL);
        double surfaceNoise = broadSurface * 0.74 + detailSurface * 0.26;
        double strength = Math.max(0, surfaceNoiseStrength);
        double top = topY + surfaceNoise * strength;
        double floor = floorY + signedNoise2d(x / (surfaceScale * 1.37), z / (surfaceScale * 1.37),
                seed ^ 0x464C4F4F524E4F49L) * strength * 0.2;

        double verticalBlend = Math.max(4.0, Math.min(12.0, (top - floor) * 0.08));
        double verticalFactor = smooth((y - floor) / verticalBlend) * smooth((top - y) / verticalBlend);
        if (verticalFactor <= 0) return nativeDensity;

        double innerBlend = Math.max(8.0, Math.min(48.0, width * 0.25));
        double bandFactor = smooth((width - blocksToEdge) / innerBlend);
        double result = toward(nativeDensity, SOLID_TARGET, bandFactor * verticalFactor);

        double scale = Math.max(1.0, caveNoiseScale);
        double broad = signedNoise3d(x / scale, y / (scale * 0.72), z / scale,
                seed ^ 0x4341564542524F44L);
        double detailScale = Math.max(1.0, scale * 0.47);
        double detail = signedNoise3d(x / detailScale, y / detailScale, z / detailScale,
                seed ^ 0x4341564544455441L);
        double cheeseField = clamp01(0.5 + (broad * 0.78 + detail * 0.22) * 0.5);

        double tunnel = signedNoise3d(x / (scale * 0.61), y / (scale * 0.38), z / (scale * 0.61),
                seed ^ 0x54554E4E454C534CL);
        double tunnelField = clamp01(1.0 - Math.abs(tunnel) * 4.0) * 0.94;
        double caveField = Math.max(cheeseField, tunnelField);
        double threshold = clamp01(caveThreshold);
        double caveRamp = Math.max(0.08, (1.0 - threshold) * 0.55);
        double caveFactor = smooth((caveField - threshold) / caveRamp);

        double caveMargin = Math.max(4.0, Math.min(12.0, (top - floor) * 0.05));
        double caveVertical = smooth((y - floor - 2.0) / caveMargin)
                * smooth((top - y - 2.0) / caveMargin);
        return toward(result, AIR_TARGET, caveFactor * bandFactor * caveVertical);
    }

    private static double signedNoise2d(double x, double z, long seed) {
        long x0 = (long)Math.floor(x), z0 = (long)Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = unit(hash(x0, 0, z0, seed));
        double b = unit(hash(x0 + 1, 0, z0, seed));
        double c = unit(hash(x0, 0, z0 + 1, seed));
        double d = unit(hash(x0 + 1, 0, z0 + 1, seed));
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz) * 2.0 - 1.0;
    }

    private static double signedNoise3d(double x, double y, double z, long seed) {
        long x0 = (long)Math.floor(x), y0 = (long)Math.floor(y), z0 = (long)Math.floor(z);
        double tx = smooth(x - x0), ty = smooth(y - y0), tz = smooth(z - z0);
        double x00 = lerp(unit(hash(x0, y0, z0, seed)), unit(hash(x0 + 1, y0, z0, seed)), tx);
        double x10 = lerp(unit(hash(x0, y0 + 1, z0, seed)), unit(hash(x0 + 1, y0 + 1, z0, seed)), tx);
        double x01 = lerp(unit(hash(x0, y0, z0 + 1, seed)), unit(hash(x0 + 1, y0, z0 + 1, seed)), tx);
        double x11 = lerp(unit(hash(x0, y0 + 1, z0 + 1, seed)), unit(hash(x0 + 1, y0 + 1, z0 + 1, seed)), tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz) * 2.0 - 1.0;
    }

    private static long hash(long x, long y, long z, long seed) {
        long value = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xD1B54A32D192ED03L
                ^ z * 0x94D049BB133111EBL;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double toward(double value, double target, double factor) {
        return lerp(value, target, clamp01(factor));
    }

    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static double smooth(double value) {
        double clamped = clamp01(value);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }
    private static double lerp(double a, double b, double factor) { return a + (b - a) * factor; }
    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
}
