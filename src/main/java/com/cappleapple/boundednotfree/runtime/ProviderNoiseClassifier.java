package com.cappleapple.boundednotfree.runtime;

import java.util.Locale;

/** Keeps provider-coordinate terrain influence out of independently local subsurface systems. */
final class ProviderNoiseClassifier {
    private ProviderNoiseClassifier() {}

    static boolean isSubsurface(String id) {
        String path = id.toLowerCase(Locale.ROOT);
        return path.contains("aquifer") || path.contains("barrier") || path.contains("fluid")
                || path.contains("lava") || path.contains("vein") || path.contains("ore")
                || path.contains("cave") || path.contains("spaghetti") || path.contains("noodle")
                || path.contains("pillar") || path.contains("underground") || path.contains("entrance");
    }
}
