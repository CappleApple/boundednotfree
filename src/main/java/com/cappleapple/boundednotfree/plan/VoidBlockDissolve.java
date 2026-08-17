package com.cappleapple.boundednotfree.plan;

/** Deterministic three-dimensional breakup for the inward edge of a VOID boundary. */
final class VoidBlockDissolve {
    private VoidBlockDissolve() {}

    static boolean keepsBlock(boolean nominalInside, double blocksToEdge, double width, double noiseScale,
                              int x, int y, int z, long seed) {
        if (!nominalInside) return false;
        if (width <= 0 || blocksToEdge >= width) return true;
        if (blocksToEdge <= 0) return false;

        double progress = 1.0 - blocksToEdge / width;
        double threshold = smooth(progress);
        double scale = Math.max(1.0, noiseScale);
        double broad = normalizedNoise(x / scale, y / scale, z / scale, seed);
        double detail = normalizedNoise(x / Math.max(1.0, scale * 0.5),
                y / Math.max(1.0, scale * 0.5), z / Math.max(1.0, scale * 0.5),
                seed ^ 0xD1550A7E5EEDL);
        return broad * 0.72 + detail * 0.28 > threshold;
    }

    private static double normalizedNoise(double x, double y, double z, long seed) {
        long x0 = (long)Math.floor(x), y0 = (long)Math.floor(y), z0 = (long)Math.floor(z);
        double tx = smooth(x - x0), ty = smooth(y - y0), tz = smooth(z - z0);
        double x00 = lerp(hash(x0, y0, z0, seed), hash(x0 + 1, y0, z0, seed), tx);
        double x10 = lerp(hash(x0, y0 + 1, z0, seed), hash(x0 + 1, y0 + 1, z0, seed), tx);
        double x01 = lerp(hash(x0, y0, z0 + 1, seed), hash(x0 + 1, y0, z0 + 1, seed), tx);
        double x11 = lerp(hash(x0, y0 + 1, z0 + 1, seed), hash(x0 + 1, y0 + 1, z0 + 1, seed), tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    private static double hash(long x, long y, long z, long seed) {
        long value = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xD1B54A32D192ED03L
                ^ z * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double value) {
        double clamped = Math.max(0, Math.min(1, value));
        return clamped * clamped * (3 - 2 * clamped);
    }

    private static double lerp(double a, double b, double factor) {
        return a + (b - a) * factor;
    }
}
