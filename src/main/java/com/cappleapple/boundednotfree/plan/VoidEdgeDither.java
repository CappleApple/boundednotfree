package com.cappleapple.boundednotfree.plan;

/** Deterministically erodes a coherent band inside the nominal VOID boundary. */
final class VoidEdgeDither {
    private VoidEdgeDither() {}

    static boolean inside(boolean nominalInside, double blocksToEdge, double width,
                          int worldX, int worldZ, long seed) {
        if (!nominalInside || width <= 0) return nominalInside;
        if (blocksToEdge >= width) return true;
        double scale = Math.max(4.0, width / 3.0);
        double coarse = valueNoise(worldX / scale, worldZ / scale, seed);
        double detail = valueNoise(worldX / (scale * 0.43), worldZ / (scale * 0.43), seed ^ 0x564F494445444745L);
        double depth = width * clamp01(coarse * 0.72 + detail * 0.28);
        return blocksToEdge >= depth;
    }

    private static double valueNoise(double x, double z, long seed) {
        long x0 = (long)Math.floor(x), z0 = (long)Math.floor(z);
        double tx = smooth(x - x0), tz = smooth(z - z0);
        double a = unit(hash(x0, z0, seed));
        double b = unit(hash(x0 + 1, z0, seed));
        double c = unit(hash(x0, z0 + 1, seed));
        double d = unit(hash(x0 + 1, z0 + 1, seed));
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static long hash(long x, long z, long seed) {
        long value = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xD1B54A32D192ED03L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static double smooth(double value) { return value * value * (3.0 - 2.0 * value); }
    private static double lerp(double a, double b, double factor) { return a + (b - a) * factor; }
    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
}
