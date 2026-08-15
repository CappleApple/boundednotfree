package com.cappleapple.boundednotfree.runtime;

/** Adds provider-native terrain without disconnecting it from the existing ground through the blend band. */
final class ProviderTerrainBlend {
    private static final double ENTRY_PENALTY = 2.0;

    private ProviderTerrainBlend() {}

    static double combine(double local, double sampled, double factor) {
        if (factor <= 0) return local;
        double clamped = Math.min(1, factor);
        return Math.max(local, sampled - (1 - clamped) * ENTRY_PENALTY);
    }
}
