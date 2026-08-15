package com.cappleapple.boundednotfree.boundary;

import com.cappleapple.boundednotfree.api.BoundaryShape;

public record OrganicBoundary(double baseRadius, double noiseStrength, double noiseScale, int octaves, long seed) implements BoundaryShape {
    public OrganicBoundary {
        if (baseRadius <= 0 || noiseStrength < 0 || noiseStrength >= baseRadius) throw new IllegalArgumentException("Organic radius/noise is invalid");
        if (noiseScale <= 0 || octaves < 1 || octaves > 8) throw new IllegalArgumentException("Organic noise settings are invalid");
    }
    @Override public String type() { return "ORGANIC"; }
    @Override public double normalizedDistance(double x, double z) { return Math.hypot(x, z) / directionalRadius(x, z); }
    @Override public double directionalRadius(double x, double z) {
        double angle = Math.atan2(z, x);
        double value = 0, amplitude = 1, total = 0, frequency = 1;
        for (int octave = 0; octave < octaves; octave++) {
            double sx = Math.cos(angle) * baseRadius * noiseScale * frequency;
            double sz = Math.sin(angle) * baseRadius * noiseScale * frequency;
            value += valueNoise(sx, sz, seed + octave * 0x9E3779B97F4A7C15L) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2;
        }
        return baseRadius + noiseStrength * value / total;
    }
    private static double valueNoise(double x, double z, long seed) {
        long ix = (long)Math.floor(x), iz = (long)Math.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        double a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed), c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz);
    }
    private static double hash(long x, long z, long seed) {
        long n = seed ^ x * 0x632BE59BD9B4E019L ^ z * 0x9E3779B97F4A7C15L;
        n = (n ^ (n >>> 30)) * 0xBF58476D1CE4E5B9L;
        n = (n ^ (n >>> 27)) * 0x94D049BB133111EBL;
        return ((n ^ (n >>> 31)) >>> 11) * 0x1.0p-52 - 1.0;
    }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
