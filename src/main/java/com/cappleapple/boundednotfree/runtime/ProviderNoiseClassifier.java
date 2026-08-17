package com.cappleapple.boundednotfree.runtime;

import java.util.Locale;
import java.util.Set;

/** Keeps provider-coordinate terrain influence out of independently local subsurface systems. */
final class ProviderNoiseClassifier {
    private static final Set<String> TECTONIC_TERRAIN_PARAMETERS = Set.of(
            "tectonic:parameter/continentalness",
            "tectonic:parameter/erosion",
            "tectonic:parameter/ridge");

    private ProviderNoiseClassifier() {}

    static boolean isTectonicTerrainParameter(String id) {
        return id != null && TECTONIC_TERRAIN_PARAMETERS.contains(id.toLowerCase(Locale.ROOT));
    }

    static boolean isSubsurface(String id) {
        String path = id.toLowerCase(Locale.ROOT);
        return path.contains("aquifer") || path.contains("barrier") || path.contains("fluid")
                || path.contains("lava") || path.contains("vein") || path.contains("ore")
                || path.contains("cave") || path.contains("spaghetti") || path.contains("noodle")
                || path.contains("pillar") || path.contains("underground") || path.contains("entrance");
    }
}
