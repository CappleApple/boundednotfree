package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.BoundedNotFree;
import com.cappleapple.boundednotfree.boundary.BoundaryFactory;
import com.cappleapple.boundednotfree.config.ConfigLoader;
import com.cappleapple.boundednotfree.config.LayoutConfig;
import com.cappleapple.boundednotfree.plan.DimensionPlan;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.IOException;

/** A single preview's layout; never registered as a live server plan or persisted to a world. */
public record PreviewLayout(DimensionPlan plan, Climate.Sampler sampler) {
    public static PreviewLayout load(ResourceLocation dimension, long worldSeed, RegistryAccess registries,
                                     ChunkGenerator generator, RandomState random) throws IOException {
        ConfigLoader.Loaded loaded = ConfigLoader.load();
        PreviewLayout preview = create(loaded.config(), dimension, worldSeed, registries, generator, random);
        if (preview.plan != null) {
            BoundedNotFree.LOGGER.info("Activated Genesis layout preview for {} with seed {} (SHA-256 {})",
                    dimension, preview.plan.seed(), loaded.hash());
            preview.plan.validation().forEach(message -> BoundedNotFree.LOGGER.warn("[Genesis preview: {}] {}", dimension, message));
        }
        return preview;
    }

    public static PreviewLayout create(LayoutConfig root, ResourceLocation dimension, long worldSeed,
                                       RegistryAccess registries, ChunkGenerator generator, RandomState random) {
        LayoutConfig.Dimension config = root.dimensions.get(dimension.toString());
        if (config == null || !config.enabled) return new PreviewLayout(null, random.sampler());
        long seed = config.customLayoutSeed != null ? config.customLayoutSeed : worldSeed ^ config.layoutSalt;
        DimensionPlan plan = new DimensionPlan(dimension, root, config, BoundaryFactory.create(config, seed), seed,
                registries.registryOrThrow(Registries.BIOME), registries.registryOrThrow(Registries.STRUCTURE));
        // Match server climate setup using only this preview's RandomState. Genesis owns the
        // source/generator from Create World, so neither is replaced or registered in LayoutRuntime.
        plan.installClimateTargets(generator.getBiomeSource(), random.sampler(), random.router(),
                registries.registryOrThrow(Registries.DENSITY_FUNCTION), generator.getSeaLevel(),
                generator.getMinY(), generator.getMinY() + generator.getGenDepth());
        if (plan.noiseRouterInfluenceReady() && generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            ClimateInfluenceRouter.install(random, plan, noiseGenerator.generatorSettings().value().spawnTarget());
        }
        return new PreviewLayout(plan, random.sampler());
    }
}