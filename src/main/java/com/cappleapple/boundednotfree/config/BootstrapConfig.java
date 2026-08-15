package com.cappleapple.boundednotfree.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BootstrapConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STRICT_MISSING_SELECTORS = BUILDER
            .comment("Treat selectors that resolve to no registered content as fatal.")
            .define("strictMissingSelectors", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BootstrapConfig() {}
}
