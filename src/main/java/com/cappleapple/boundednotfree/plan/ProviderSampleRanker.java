package com.cappleapple.boundednotfree.plan;

/** Scores a provider-native terrain patch for the biome it will represent. */
final class ProviderSampleRanker {
    private ProviderSampleRanker() {}

    static double score(boolean ocean, boolean mountain, double climateTerrainScore,
                        int minimumSurface, int maximumSurface, double averageSurface, int seaLevel,
                        int maxBuildHeight) {
        if (mountain) {
            double verticalSpan = Math.max(64, maxBuildHeight - seaLevel);
            double expectedMinimum = seaLevel + Math.min(40, verticalSpan * 0.15);
            double expectedAverage = seaLevel + Math.min(112, verticalSpan * 0.45);
            double expectedMaximum = seaLevel + Math.min(192, verticalSpan * 0.75);
            return -Math.abs(minimumSurface - expectedMinimum) * 2.0
                    - Math.abs(averageSurface - expectedAverage) * 4.0
                    - Math.abs(maximumSurface - expectedMaximum);
        }
        if (ocean) return -maximumSurface * 4.0 - averageSurface - (maximumSurface - minimumSurface) * 0.25;
        double expected = seaLevel + clamp(climateTerrainScore, -1.5, 3.0) * 24.0;
        return -Math.abs(averageSurface - expected) - (maximumSurface - minimumSurface) * 0.1;
    }

    static double penalizeCeilingClipping(double score, int maximumSurface, int maxBuildHeight) {
        return score - Math.max(0, maximumSurface - (maxBuildHeight - 33)) * 10_000.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
