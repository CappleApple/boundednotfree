package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.BoundedNotFree;
import com.cappleapple.boundednotfree.boundary.BoundaryFactory;
import com.cappleapple.boundednotfree.config.BootstrapConfig;
import com.cappleapple.boundednotfree.config.ConfigLoader;
import com.cappleapple.boundednotfree.config.LayoutConfig;
import com.cappleapple.boundednotfree.persistence.LayoutSavedData;
import com.cappleapple.boundednotfree.plan.DimensionPlan;
import com.cappleapple.boundednotfree.plan.StructurePlanner;
import com.google.gson.JsonParseException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class LayoutRuntime {
    private static final Map<ChunkGenerator, DimensionPlan> PLANS = new ConcurrentHashMap<>();
    private static final Map<ChunkGenerator, Map<ResourceLocation, AtomicInteger>> STRUCTURE_COUNTS = new ConcurrentHashMap<>();
    private static volatile ConfigLoader.Loaded loaded;

    private LayoutRuntime() {}

    @SubscribeEvent
    public static void serverAboutToStart(ServerAboutToStartEvent event) {
        PLANS.clear();
        try {
            loaded = ConfigLoader.load();
            BoundedNotFree.LOGGER.info("Loaded world layout schema {} from {} (SHA-256 {})", loaded.config().schemaVersion, loaded.path(), loaded.hash());
        } catch (IOException exception) {
            throw new IllegalStateException("Bounded Not Free configuration failed to load", exception);
        }
    }

    @SubscribeEvent
    public static void levelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || loaded == null) return;
        ResourceLocation id = level.dimension().location();
        LayoutConfig root = lockedConfig(level);
        LayoutConfig.Dimension config = root.dimensions.get(id.toString());
        if (config == null || !config.enabled) return;
        long seed = config.customLayoutSeed != null ? config.customLayoutSeed : level.getSeed() ^ config.layoutSalt;
        DimensionPlan plan = new DimensionPlan(id, root, config, BoundaryFactory.create(config, seed), seed,
                level.registryAccess().registryOrThrow(Registries.BIOME), level.registryAccess().registryOrThrow(Registries.STRUCTURE));
        plan.installClimateTargets(level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState().sampler(), level.getChunkSource().randomState().router(),
                level.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION),
                level.getSeaLevel(), level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (plan.noiseRouterInfluenceReady() && level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator) {
            var result = ClimateInfluenceRouter.install(level.getChunkSource().randomState(), plan, noiseGenerator.generatorSettings().value().spawnTarget());
            BoundedNotFree.LOGGER.info("Installed layout terrain influence for {} using {} rim parameter point(s) from {}, rim target {}, rim style {}, strategy {}, provider samples {}, router replacements {}",
                    id, plan.rimClimatePointCount(), plan.climateParameterSource(), plan.climateTargetDescription(),
                    plan.config().rimTerrainStyle, result.strategy(), plan.providerSampleDescription(), result.replacements());
        } else if (plan.noiseRouterInfluenceReady()) {
            plan.addValidation("Layout terrain influence requires a NoiseBasedChunkGenerator, but the active generator is "
                    + level.getChunkSource().getGenerator().getClass().getName());
        }
        plan.installStructureReservations(StructurePlanner.plan(level, plan));
        if (!plan.validation().isEmpty()) {
            plan.validation().forEach(message -> BoundedNotFree.LOGGER.warn("[{}] {}", id, message));
            if (BootstrapConfig.STRICT_MISSING_SELECTORS.get() || "FAIL_WORLD_CREATION".equalsIgnoreCase(root.requiredContentFailurePolicy)) {
                throw new IllegalStateException("World layout validation failed for " + id + ": " + String.join("; ", plan.validation()));
            }
        }
        PLANS.put(level.getChunkSource().getGenerator(), plan);
        applyGameplayBorder(level, plan);
        BoundedNotFree.LOGGER.info("Activated {} layout for {} with {} biome and {} structure reservations", plan.boundary().type(), id,
                plan.biomeReservations().size(), plan.structureReservations().size());
    }

    // Keep plans until the server ends. Threaded chunk lifecycles can emit transient level-unload
    // notifications while the owning ServerLevel and generator remain active.
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) { PLANS.clear(); STRUCTURE_COUNTS.clear(); loaded = null; }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0) return;
        DimensionPlan plan = plan(player.serverLevel().getChunkSource().getGenerator());
        if (plan == null || !"CUSTOM_SHAPE".equalsIgnoreCase(plan.config().gameplayBorder) || plan.measure(player.getX(), player.getZ()).inside()) return;
        double dx = player.getX() - plan.config().centerX, dz = player.getZ() - plan.config().centerZ;
        double radius = plan.boundary().directionalRadius(dx, dz);
        double scale = Math.max(0, radius - 1.0) / Math.max(1.0, Math.hypot(dx, dz));
        player.teleportTo(plan.config().centerX + dx * scale, player.getY(), plan.config().centerZ + dz * scale);
    }

    public static DimensionPlan plan(ChunkGenerator generator) { return PLANS.get(generator); }
    public static net.minecraft.world.level.biome.BiomeResolver constrainedResolver(ChunkGenerator generator, net.minecraft.world.level.biome.BiomeResolver original) {
        DimensionPlan plan = PLANS.get(generator);
        return plan == null ? original : (x, y, z, sampler) ->
                plan.selectBiome(original.getNoiseBiome(x, y, z, sampler), x, y, z, sampler);
    }
    public static boolean structureAllowed(ChunkGenerator generator, net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> structure, int chunkX, int chunkZ) {
        DimensionPlan plan = PLANS.get(generator);
        if (plan == null) return true;
        ResourceLocation id = structure.unwrapKey().map(key -> key.location()).orElse(null);
        int generated = id == null ? 0 : STRUCTURE_COUNTS.getOrDefault(generator, Map.of()).getOrDefault(id, new AtomicInteger()).get();
        return generated < plan.maxCount(structure) && plan.structureAllowed(structure, chunkX, chunkZ);
    }

    public static void recordStructure(ChunkGenerator generator, net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.Structure> structure) {
        structure.unwrapKey().ifPresent(key -> STRUCTURE_COUNTS.computeIfAbsent(generator, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key.location(), ignored -> new AtomicInteger()).incrementAndGet());
    }

    public static void sendInfo(CommandSourceStack source) {
        DimensionPlan plan = plan(source.getLevel().getChunkSource().getGenerator());
        if (plan == null) { source.sendSuccess(() -> Component.literal("Bounded Not Free: disabled for " + source.getLevel().dimension().location()), false); return; }
        var metric = plan.measure(source.getPosition().x, source.getPosition().z);
        String zone = plan.progressionZone(source.getPosition().x, source.getPosition().z);
        source.sendSuccess(() -> Component.literal("Boundary=" + plan.boundary().type() + " center=" + plan.config().centerX + "," + plan.config().centerZ
                + " normalized=" + format(metric.normalizedFromCenter()) + " edge=" + format(metric.blocksToEdge()) + " zone=" + (zone == null ? "none" : zone)
                + " influence=" + format(plan.effectiveClimateInfluenceFactor(source.getPosition().x, source.getPosition().z))
                + " target=" + plan.climateTargetDescription() + " rimStyle=" + plan.config().rimTerrainStyle
                + " seed=" + plan.seed()), false);
    }

    public static void sendValidation(CommandSourceStack source) {
        DimensionPlan plan = plan(source.getLevel().getChunkSource().getGenerator());
        if (plan == null) { source.sendFailure(Component.literal("No active layout for this dimension.")); return; }
        if (plan.validation().isEmpty()) source.sendSuccess(() -> Component.literal("Layout validation passed; " + plan.biomeReservations().size()
                + " biome and " + plan.structureReservations().size() + " structure reservations planned."), false);
        else plan.validation().forEach(message -> source.sendFailure(Component.literal(message)));
    }

    public static void sendCompatibility(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        source.sendSuccess(() -> Component.literal("Generator=" + generator.getClass().getName() + " biomeSource=" + generator.getBiomeSource().getClass().getName()
                + " biomes=" + level.registryAccess().registryOrThrow(Registries.BIOME).size() + " structures=" + level.registryAccess().registryOrThrow(Registries.STRUCTURE).size()
                + " climateTarget=" + (plan(generator) != null && plan(generator).climateInfluenceReady())
                + " climateInfluence=" + (plan(generator) != null && plan(generator).climateInfluenceActive())
                + " strategy=" + (plan(generator) == null ? "NONE" : plan(generator).climateInfluenceStrategy())
                + " providerSample=" + (plan(generator) == null ? "none" : plan(generator).providerSampleDescription())
                + " TerraBlender=" + ModList.get().isLoaded("terrablender") + " Tectonic=" + ModList.get().isLoaded("tectonic")
                + " RegionsUnexplored=" + ModList.get().isLoaded("regions_unexplored") + " Lithostitched=" + ModList.get().isLoaded("lithostitched")), false);
    }

    public static void exportPreview(CommandSourceStack source) {
        DimensionPlan plan = plan(source.getLevel().getChunkSource().getGenerator());
        if (plan == null) { source.sendFailure(Component.literal("No active layout for this dimension.")); return; }
        Path directory = source.getServer().getWorldPath(LevelResource.ROOT).resolve("boundednotfree-previews");
        String baseName = plan.dimension().getNamespace() + "_" + plan.dimension().getPath();
        Path svg = directory.resolve(baseName + ".svg");
        Path json = directory.resolve(baseName + ".json");
        try {
            Files.createDirectories(directory);
            Files.writeString(svg, previewSvg(plan), StandardCharsets.UTF_8);
            Map<String, Object> report = new java.util.LinkedHashMap<>();
            report.put("dimension", plan.dimension().toString());
            report.put("boundary", plan.boundary().type());
            report.put("center", Map.of("x", plan.config().centerX, "z", plan.config().centerZ));
            report.put("seed", plan.seed());
            report.put("configSha256", loaded == null ? "unavailable" : loaded.hash());
            report.put("rimPlacementMode", plan.config().rimPlacementMode);
            report.put("rimInfluenceStrength", plan.config().rimInfluenceStrength);
            report.put("rimTerrainStyle", plan.config().rimTerrainStyle);
            report.put("rimCaveWallWidth", plan.config().rimCaveWallWidth);
            report.put("rimCaveWallFloorY", plan.config().rimCaveWallFloorY);
            report.put("rimCaveWallTopY", plan.config().rimCaveWallTopY);
            report.put("rimCaveWallSurfaceNoiseScale", plan.config().rimCaveWallSurfaceNoiseScale);
            report.put("rimCaveWallSurfaceNoiseStrength", plan.config().rimCaveWallSurfaceNoiseStrength);
            report.put("rimCaveWallCaveScale", plan.config().rimCaveWallCaveScale);
            report.put("rimCaveWallCaveThreshold", plan.config().rimCaveWallCaveThreshold);
            report.put("rimClimateTarget", plan.climateTargetDescription());
            report.put("rimInfluenceStrategy", plan.climateInfluenceStrategy());
            report.put("climateParameterSource", plan.climateParameterSource());
            report.put("providerSample", plan.providerSampleDescription());
            report.put("biomeReservations", plan.biomeReservations().stream().map(reservation -> Map.of(
                    "selector", reservation.selector(), "x", reservation.x(), "z", reservation.z(), "radius", reservation.radius())).toList());
            report.put("structureReservations", plan.structureReservations().stream().map(reservation -> Map.of(
                    "structure", reservation.structure().unwrapKey().map(key -> key.location().toString()).orElse("unknown"),
                    "chunkX", reservation.chunkX(), "chunkZ", reservation.chunkZ())).toList());
            report.put("validation", plan.validation());
            Files.writeString(json, ConfigLoader.GSON.toJson(report), StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal("Exported layout preview to " + svg.toAbsolutePath() + " and " + json.toAbsolutePath()), false);
        } catch (IOException exception) { source.sendFailure(Component.literal("Preview export failed: " + exception.getMessage())); }
    }

    private static LayoutConfig lockedConfig(ServerLevel level) {
        LayoutSavedData saved = level.getDataStorage().computeIfAbsent(LayoutSavedData.factory(), LayoutSavedData.FILE_ID);
        String currentJson;
        try {
            currentJson = Files.readString(loaded.path(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            BoundedNotFree.LOGGER.error("Could not read layout JSON for world-plan locking", exception);
            currentJson = "";
        }
        if (!saved.initialized()) saved.initialize(loaded.hash(), currentJson);
        if (!saved.configHash().equals(loaded.hash())) {
            BoundedNotFree.LOGGER.warn("Layout config changed for {}: stored {}, current {}", level.dimension().location(), saved.configHash(), loaded.hash());
            LayoutConfig.Dimension current = loaded.config().dimensions.get(level.dimension().location().toString());
            if (current != null && current.lockLayoutAfterWorldCreation && !saved.lockedConfigJson().isBlank()) {
                try { return ConfigLoader.GSON.fromJson(saved.lockedConfigJson(), LayoutConfig.class); }
                catch (JsonParseException exception) { BoundedNotFree.LOGGER.error("Stored locked layout is unreadable; using current config", exception); }
            }
        }
        return loaded.config();
    }

    private static void applyGameplayBorder(ServerLevel level, DimensionPlan plan) {
        if (!"VANILLA_WHERE_POSSIBLE".equalsIgnoreCase(plan.config().gameplayBorder)) return;
        if (!"SQUARE".equals(plan.boundary().type()) && !"ROUNDED_SQUARE".equals(plan.boundary().type())) {
            BoundedNotFree.LOGGER.warn("Vanilla border requested for non-square {}; leaving border unchanged", plan.boundary().type()); return;
        }
        level.getWorldBorder().setCenter(plan.config().centerX, plan.config().centerZ);
        double extent = plan.config().extentX > 0 ? plan.config().extentX : plan.config().radius;
        level.getWorldBorder().setSize(extent * 2);
    }

    private static String previewSvg(DimensionPlan plan) {
        int size = 768, samples = 720;
        double extent = Math.max(plan.config().radius, Math.max(plan.config().extentX, plan.config().extentZ));
        StringBuilder path = new StringBuilder();
        for (int i = 0; i <= samples; i++) {
            double angle = Math.PI * 2 * i / samples;
            double r = plan.boundary().directionalRadius(Math.cos(angle), Math.sin(angle));
            double x = size / 2.0 + Math.cos(angle) * r / extent * size * 0.45;
            double y = size / 2.0 + Math.sin(angle) * r / extent * size * 0.45;
            path.append(i == 0 ? "M" : "L").append(format(x)).append(' ').append(format(y));
        }
        StringBuilder reservations = new StringBuilder();
        for (DimensionPlan.Reservation reservation : plan.biomeReservations()) {
            double x = size / 2.0 + reservation.x() / extent * size * 0.45;
            double y = size / 2.0 + reservation.z() / extent * size * 0.45;
            double r = Math.max(2, reservation.radius() / extent * size * 0.45);
            reservations.append("<circle cx=\"").append(format(x)).append("\" cy=\"").append(format(y)).append("\" r=\"").append(format(r)).append("\"/>");
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"768\" height=\"768\" viewBox=\"0 0 768 768\"><rect width=\"100%\" height=\"100%\" fill=\"#10151b\"/><path d=\"" + path + "Z\" fill=\"#3f6951\" stroke=\"#d8e6cf\" stroke-width=\"3\"/><g fill=\"#f3b562\" fill-opacity=\".65\" stroke=\"#fff\">" + reservations + "</g><text x=\"20\" y=\"32\" fill=\"white\" font-family=\"sans-serif\" font-size=\"18\">" + plan.dimension() + " - " + plan.boundary().type() + "</text></svg>";
    }
    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
}
