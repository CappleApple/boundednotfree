package com.cappleapple.boundednotfree.compatcheck;

import com.cappleapple.boundednotfree.config.ConfigLoader;
import com.cappleapple.boundednotfree.config.LayoutConfig;
import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import com.cappleapple.boundednotfree.runtime.PreviewLayout;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;

@Mod("boundednotfree_compat_test")
public final class PreviewCompatibilityChecks {
    public PreviewCompatibilityChecks() {
        NeoForge.EVENT_BUS.register(PreviewCompatibilityChecks.class);
        if (FMLEnvironment.dist == Dist.CLIENT) GenesisClientChecks.register();
    }

    static LayoutConfig fixture() {
        LayoutConfig root = new LayoutConfig();
        LayoutConfig.Dimension dimension = new LayoutConfig.Dimension();
        dimension.enabled = true;
        dimension.centerX = 96;
        dimension.centerZ = -64;
        dimension.radius = 256;
        dimension.outsideMode = "VOID";
        dimension.rimEnabled = true;
        dimension.rimWidth = 32;
        dimension.rimBlendWidth = 16;
        dimension.rimPlacementMode = "REQUIRE";
        dimension.rimSelectors.add("minecraft:frozen_peaks");
        dimension.biomeLayout = "RADIAL";
        dimension.transitionNoiseStrength = 0;
        dimension.macroTransitionWidth = 0;
        LayoutConfig.Band core = new LayoutConfig.Band();
        core.max = 0.5;
        core.selectors.add("minecraft:desert");
        LayoutConfig.Band outer = new LayoutConfig.Band();
        outer.min = 0.5;
        outer.selectors.add("minecraft:plains");
        dimension.radialBands.add(core);
        dimension.radialBands.add(outer);
        root.dimensions.put("minecraft:overworld", dimension);
        return root;
    }

    static void write(LayoutConfig root) throws Exception {
        var file = FMLPaths.CONFIGDIR.get().resolve("boundednotfree/world-layout.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, ConfigLoader.GSON.toJson(root));
    }

    static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }

    static void recordResult(String result) {
        try {
            Files.writeString(FMLPaths.GAMEDIR.get().resolve("compat-result.txt"), result);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void configureServer(ServerAboutToStartEvent event) throws Exception {
        write(fixture());
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent event) {
        try {
            var level = event.getServer().overworld();
            var generator = (NoiseBasedChunkGenerator)level.getChunkSource().getGenerator();
            var active = LayoutRuntime.plan(generator);
            check(active != null, "Server layout was not activated");
            var random = RandomState.create(generator.generatorSettings().value(),
                    level.registryAccess().lookupOrThrow(Registries.NOISE), level.getSeed());
            var preview = PreviewLayout.create(fixture(), Level.OVERWORLD.location(), level.getSeed(),
                    level.registryAccess(), generator, random);
            check(LayoutRuntime.plan(generator) == active, "Preview replaced the server plan");
            int samples = 0;
            for (int x = -320; x <= 512; x += 16) for (int z = -480; z <= 352; z += 16) {
                int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
                var serverSampler = level.getChunkSource().randomState().sampler();
                var expected = active.selectBiome(generator.getBiomeSource().getNoiseBiome(qx, 80, qz, serverSampler),
                        qx, 80, qz, serverSampler);
                var actual = preview.plan().selectBiome(generator.getBiomeSource().getNoiseBiome(qx, 80, qz, preview.sampler()),
                        qx, 80, qz, preview.sampler());
                check(expected.equals(actual), "Preview/server biome mismatch at " + x + "," + z);
                samples++;
            }
            recordResult("PASS: server preview parity, " + samples + " samples");
            System.out.println("BNF_SERVER_PREVIEW_PARITY_PASS samples=" + samples);
            event.getServer().halt(false);
        } catch (Throwable failure) {
            recordResult("FAIL: " + failure);
            failure.printStackTrace();
            System.out.println("BNF_COMPAT_CHECK_FAILED");
            event.getServer().halt(false);
        }
    }
}