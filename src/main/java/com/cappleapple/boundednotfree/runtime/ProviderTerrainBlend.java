package com.cappleapple.boundednotfree.runtime;

/** Replaces local density with provider-native density through a smooth rim blend band. */
public final class ProviderTerrainBlend {
    private ProviderTerrainBlend() {}

    public static double combine(double local, double sampled, double factor) {
        if (factor <= 0) return local;
        double clamped = Math.min(1, factor);
        return local + (sampled - local) * clamped;
    }
}
