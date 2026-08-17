package com.cappleapple.boundednotfree.plan;

import com.cappleapple.boundednotfree.api.BoundaryShape;
import com.cappleapple.boundednotfree.api.DistanceMetric;
import com.cappleapple.boundednotfree.config.LayoutConfig;
import com.cappleapple.boundednotfree.mixin.MultiNoiseBiomeSourceAccessor;
import com.cappleapple.boundednotfree.selector.SelectorResolver;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DimensionPlan {
    private static final int PROVIDER_SAMPLE_HALF_SPAN = 96;
    private static final int BIOME_TERRAIN_ALIGNMENT_MARGIN = 8;
    private static final ResourceKey<DensityFunction> TECTONIC_BASE_TERRAIN = ResourceKey.create(
            Registries.DENSITY_FUNCTION, ResourceLocation.fromNamespaceAndPath("tectonic", "base_terrain"));
    public record Reservation(String selector, double x, double z, double radius, int index, Set<Holder<Biome>> biomes) {}
    public record StructureReservation(Holder<Structure> structure, int chunkX, int chunkZ, int index) {}
    private record BiomeRule(LayoutConfig.BiomeRule config, Set<Holder<Biome>> biomes, int specificity) {}
    private record StructureRule(LayoutConfig.StructureRule config, Set<Holder<Structure>> structures, int specificity) {}
    public record ClimateTarget(Holder<Biome> holder, String biome, Climate.ParameterPoint point, double score) {}
    public record ProviderCoordinates(int x, int z) {}
    private record ClimateSelection(Holder<Biome> biome, Climate.ParameterPoint point) {}
    private record ProviderSample(int x, int y, int z, int minimumSurface, int maximumSurface,
                                  double averageSurface, double score) {}
    private record ProviderCandidate(int x, int z, int surface, double score) {}
    private record TerrainDirective(double factor, ClimateSelection climate, ProviderSample sample,
                                    boolean replaceTerrain) {
        private static final TerrainDirective NONE = new TerrainDirective(0, null, null, false);
    }

    private final ResourceLocation dimension;
    private final LayoutConfig.Dimension config;
    private final BoundaryShape boundary;
    private final BoundaryMeasureCache boundaryMeasures;
    private final long seed;
    private final Registry<Biome> biomeRegistry;
    private final Registry<Structure> structureRegistry;
    private final SelectorResolver<Biome> biomeResolver;
    private final SelectorResolver<Structure> structureResolver;
    private final Set<Holder<Biome>> biomeFilter;
    private final Set<Holder<Structure>> structureFilter;
    private final Set<Holder<Biome>> rimBiomes;
    private final Set<Holder<Biome>> outsideBiomes;
    private final Set<Holder<Biome>> fallbackBiomes;
    private final List<BiomeRule> biomeRules;
    private final List<StructureRule> structureRules;
    private final Set<Holder<Structure>> requiredStructureTypes;
    private final List<Reservation> biomeReservations;
    private final MacroLayoutPlan macro;
    private final List<String> validation;
    private volatile List<StructureReservation> structureReservations = List.of();
    private volatile ClimateTarget rimClimateTarget;
    private volatile Map<Holder<Biome>, List<Climate.ParameterPoint>> biomeClimatePoints = Map.of();
    private volatile Set<Holder<Biome>> providerLandBiomes = Set.of();
    private volatile int rimClimatePointCount;
    private volatile boolean climateRouterInstalled;
    private volatile String climateInfluenceStrategy = "NONE";
    private volatile String climateParameterSource = "none";
    private volatile Integer providerSampleX;
    private volatile Integer providerSampleZ;
    private volatile int providerSampleY;
    private volatile Climate.Sampler originalSampler;
    private volatile int climateSampleY;
    private volatile Map<Holder<Biome>, ProviderSample> providerSamples = Map.of();
    private volatile BiomeSource providerSource;
    private volatile DensityFunction providerFinalDensity;
    private volatile DensityFunction providerTerrainRoot;
    private volatile String providerTerrainRootId = "none";
    private volatile int providerSeaLevel;
    private volatile int providerMinBuildHeight;
    private volatile int providerMaxBuildHeight;
    private volatile boolean providerSamplesPrepared;
    private final ThreadLocal<TerrainDirectiveCache> terrainDirectiveCache =
            ThreadLocal.withInitial(TerrainDirectiveCache::new);

    private static final class TerrainDirectiveCache {
        private int x;
        private int z;
        private boolean valid;
        private TerrainDirective directive;
    }

    public DimensionPlan(ResourceLocation dimension, LayoutConfig root, LayoutConfig.Dimension config, BoundaryShape boundary,
                         long seed, Registry<Biome> biomeRegistry, Registry<Structure> structureRegistry) {
        this.dimension = dimension;
        this.config = config;
        this.boundary = boundary;
        this.boundaryMeasures = new BoundaryMeasureCache(boundary, config.centerX, config.centerZ);
        this.seed = seed;
        this.biomeRegistry = biomeRegistry;
        this.structureRegistry = structureRegistry;
        this.biomeResolver = new SelectorResolver<>(biomeRegistry, root.biomeGroups);
        this.structureResolver = new SelectorResolver<>(structureRegistry, root.structureGroups);
        this.biomeFilter = biomeResolver.resolveAll(config.biomeFilter);
        this.structureFilter = structureResolver.resolveAll(config.structureFilter);
        this.rimBiomes = biomeResolver.resolveAll(config.rimSelectors);
        this.outsideBiomes = biomeResolver.resolveAll(config.outsideSelectors);
        this.fallbackBiomes = biomeResolver.resolve(config.fallbackBiome);
        this.biomeRules = resolveBiomeRules(config.biomeRules);
        ArrayList<LayoutConfig.StructureRule> allStructureRules = new ArrayList<>(config.structureRules);
        allStructureRules.addAll(config.requiredStructures);
        this.structureRules = resolveStructureRules(allStructureRules);
        this.requiredStructureTypes = structureResolver.resolveAll(config.requiredStructures.stream().map(rule -> rule.selector).toList());
        this.validation = new ArrayList<>();
        validation.addAll(biomeResolver.validateGroups());
        validation.addAll(structureResolver.validateGroups());
        validateRules(config.requiredBiomes, config.requiredStructures);
        validateInfluence();
        if (config.rimEnabled && rimBiomes.isEmpty()) validation.add("rimEnabled is true, but rimSelectors resolve to zero biomes");
        this.biomeReservations = planBiomeReservations(config.requiredBiomes);
        this.macro = createMacro(root);
    }

    public ResourceLocation dimension() { return dimension; }
    public LayoutConfig.Dimension config() { return config; }
    public BoundaryShape boundary() { return boundary; }
    public long seed() { return seed; }
    public List<Reservation> biomeReservations() { return biomeReservations; }
    public List<String> validation() { return List.copyOf(validation); }
    public List<StructureReservation> structureReservations() { return structureReservations; }
    public void installStructureReservations(List<StructureReservation> reservations) {
        this.structureReservations = List.copyOf(reservations);
        for (LayoutConfig.StructureRule rule : config.requiredStructures) {
            Set<Holder<Structure>> matches = structureResolver.resolve(rule.selector);
            int planned = (int) this.structureReservations.stream().filter(reservation -> matches.contains(reservation.structure())).count();
            int required = rule.exactCount != null ? rule.exactCount : rule.minCount;
            if (planned < required) validation.add("Required structure selector '" + rule.selector + "' requested " + required
                    + " placement(s), but only " + planned + " eligible vanilla placement candidate(s) were found within maxPlannerAttempts.");
        }
    }
    public Set<Holder<Structure>> resolveStructures(String selector) { return structureResolver.resolve(selector); }
    public List<LayoutConfig.StructureRule> requiredStructureRules() { return List.copyOf(config.requiredStructures); }

    public void installClimateTargets(BiomeSource source, Climate.Sampler sampler, NoiseRouter router,
                                      Registry<DensityFunction> densityFunctions,
                                      int seaLevel, int minBuildHeight, int maxBuildHeight) {
        originalSampler = sampler;
        climateSampleY = seaLevel;
        providerSource = source;
        providerFinalDensity = router.finalDensity();
        densityFunctions.getHolder(TECTONIC_BASE_TERRAIN).ifPresent(holder -> {
            providerTerrainRoot = holder.value();
            providerTerrainRootId = TECTONIC_BASE_TERRAIN.location().toString();
        });
        providerSeaLevel = seaLevel;
        providerMinBuildHeight = minBuildHeight;
        providerMaxBuildHeight = maxBuildHeight;
        MultiNoiseBiomeSource multiNoise = findMultiNoiseSource(source);
        if (multiNoise == null) {
            if (config.rimEnabled && !rimBiomes.isEmpty() && config.rimInfluenceStrength > 0) {
                validation.add("Rim climate influence could not find a MultiNoiseBiomeSource inside the active source "
                        + source.getClass().getName());
            }
            return;
        }
        climateParameterSource = source == multiNoise ? multiNoise.getClass().getName()
                : source.getClass().getName() + " -> " + multiNoise.getClass().getName();
        Climate.ParameterList<Holder<Biome>> parameters = ((MultiNoiseBiomeSourceAccessor)(Object)multiNoise).boundednotfree$parameters();
        Map<Holder<Biome>, List<Climate.ParameterPoint>> pointsByBiome = new LinkedHashMap<>();
        for (Pair<Climate.ParameterPoint, Holder<Biome>> pair : parameters.values()) {
            pointsByBiome.computeIfAbsent(pair.getSecond(), ignored -> new ArrayList<>()).add(pair.getFirst());
        }
        LinkedHashMap<Holder<Biome>, List<Climate.ParameterPoint>> immutablePoints = new LinkedHashMap<>();
        pointsByBiome.forEach((biome, points) -> immutablePoints.put(biome, List.copyOf(points)));
        biomeClimatePoints = Collections.unmodifiableMap(immutablePoints);
        LinkedHashSet<Holder<Biome>> landBiomes = immutablePoints.keySet().stream()
                .filter(biome -> !biome.is(BiomeTags.IS_OCEAN))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        providerLandBiomes = Collections.unmodifiableSet(landBiomes);

        if (config.rimEnabled && !rimBiomes.isEmpty() && config.rimInfluenceStrength > 0) {
            List<ClimateTarget> candidates = parameters.values().stream()
                    .filter(pair -> rimBiomes.contains(pair.getSecond()))
                    .map(this::climateTarget)
                    .sorted(Comparator.comparingDouble(ClimateTarget::score).reversed().thenComparing(ClimateTarget::biome))
                    .toList();
            rimClimatePointCount = candidates.size();
            rimClimateTarget = candidates.stream().findFirst().orElse(null);
            if (rimClimateTarget == null) {
                validation.add("No active multi-noise climate parameter points map to rimSelectors; density influence is unavailable");
            }
        }

    }

    /**
     * Finds provider-native terrain anchors only when the active density graph is actually
     * decoupled from its climate roots. Vanilla-compatible graphs never pay this scan cost.
     */
    public synchronized void prepareProviderSamples() {
        if (providerSamplesPrepared) return;
        providerSamplesPrepared = true;
        LinkedHashSet<Holder<Biome>> terrainBiomes = constrainedTerrainBiomes();
        BiomeSource source = providerSource;
        DensityFunction density = providerFinalDensity;
        Climate.Sampler sampler = originalSampler;
        if (terrainBiomes.isEmpty() || source == null || density == null || sampler == null) return;
        findProviderSamples(source, sampler, density, providerSeaLevel, providerMinBuildHeight,
                providerMaxBuildHeight, terrainBiomes);
    }

    public boolean climateInfluenceReady() { return rimClimateTarget != null; }
    public boolean terrainInfluenceReady() {
        return rimClimateTarget != null || constrainedTerrainBiomes().stream()
                .anyMatch(biome -> !biomeClimatePoints.getOrDefault(biome, List.of()).isEmpty());
    }
    public boolean noiseRouterInfluenceReady() { return terrainInfluenceReady() || rimCaveWallEnabled(); }
    public boolean rimCaveWallEnabled() {
        return config.rimEnabled && "CAVE_WALL".equalsIgnoreCase(config.rimTerrainStyle)
                && config.rimCaveWallWidth > 0;
    }
    public double shapeRimDensity(double nativeDensity, int worldX, int worldY, int worldZ) {
        if (!rimCaveWallEnabled()) return nativeDensity;
        DistanceMetric metric = measure(worldX, worldZ);
        return RimCaveWall.apply(nativeDensity, metric.inside(), metric.blocksToEdge(),
                config.rimCaveWallWidth, config.rimCaveWallFloorY, config.rimCaveWallTopY,
                config.rimCaveWallSurfaceNoiseScale, config.rimCaveWallSurfaceNoiseStrength,
                config.rimCaveWallCaveScale, config.rimCaveWallCaveThreshold,
                worldX, worldY, worldZ, seed ^ 0x4341564557414C4CL);
    }
    public boolean climateInfluenceActive() { return climateRouterInstalled; }
    public String climateInfluenceStrategy() { return climateInfluenceStrategy; }
    public String climateParameterSource() { return climateParameterSource; }
    public boolean providerSampleReady() { return !providerSamples.isEmpty(); }
    public boolean providerTerrainRootReady() { return providerTerrainRoot != null; }
    public int rimClimatePointCount() { return rimClimatePointCount; }
    public void addValidation(String message) { validation.add(message); }
    public void recordClimateRouter(Map<ClimateChannel, Integer> replacements, String strategy) {
        if (!terrainInfluenceReady()) {
            climateRouterInstalled = false;
            climateInfluenceStrategy = strategy;
            return;
        }
        EnumSet<ClimateChannel> missing = EnumSet.of(ClimateChannel.CONTINENTALNESS,
                ClimateChannel.EROSION, ClimateChannel.WEIRDNESS);
        missing.removeIf(channel -> replacements.getOrDefault(channel, 0) > 0);
        climateRouterInstalled = missing.isEmpty();
        climateInfluenceStrategy = climateRouterInstalled ? strategy : "INCOMPLETE";
        if (!missing.isEmpty()) validation.add("Active noise router did not expose all climate fields; missing " + missing
                + ". Rim terrain influence is incomplete for this generator.");
    }
    public String climateTargetDescription() {
        ClimateTarget target = rimClimateTarget;
        return target == null ? "none" : target.biome + " C=" + format(mid(target.point.continentalness()))
                + " E=" + format(mid(target.point.erosion())) + " W=" + format(mid(target.point.weirdness()));
    }

    public String providerSampleDescription() {
        if (!providerSampleReady()) return "none";
        String rim = providerSampleX == null ? "dynamic" : providerSampleX + "," + providerSampleY + "," + providerSampleZ;
        ProviderSample sample = rimClimateTarget == null ? null : providerSamples.get(rimClimateTarget.holder);
        String terrain = sample == null ? "" : " surface=" + sample.minimumSurface + ".." + sample.maximumSurface
                + " avg=" + format(sample.averageSurface);
        return rim + terrain + " root=" + providerTerrainRootId
                + " (" + providerSamples.size() + " biome sample(s))";
    }

    public double climateTargetValue(ClimateChannel channel) {
        ClimateTarget target = rimClimateTarget;
        if (target == null) return 0;
        return mid(parameter(target.point, channel));
    }

    public double influencedClimateValue(ClimateChannel channel, double original, int worldX, int worldZ) {
        TerrainDirective directive = terrainDirective(worldX, worldZ);
        if (directive.climate == null) return original;
        Climate.Parameter range = parameter(directive.climate.point, channel);
        if (!directive.replaceTerrain) return mid(range);
        double min = Climate.unquantizeCoord(range.min());
        double max = Climate.unquantizeCoord(range.max());
        return Math.max(min, Math.min(max, original));
    }

    public double rimInfluenceFactor(double worldX, double worldZ) {
        return RimInfluence.factor(config.rimEnabled, config.rimWidth, config.rimBlendWidth, measure(worldX, worldZ));
    }

    public double effectiveClimateInfluenceFactor(double worldX, double worldZ) {
        return terrainDirective((int)Math.floor(worldX), (int)Math.floor(worldZ)).factor;
    }

    public ProviderCoordinates providerCoordinates(int worldX, int worldZ) {
        TerrainDirective directive = terrainDirective(worldX, worldZ);
        ProviderSample sample = directive.sample;
        if (sample == null) return new ProviderCoordinates(worldX, worldZ);
        return new ProviderCoordinates(sample.x + foldProviderOffset(worldX - config.centerX),
                sample.z + foldProviderOffset(worldZ - config.centerZ));
    }

    public double blendProviderTerrain(double local, double sampled, int worldX, int worldZ) {
        TerrainDirective directive = terrainDirective(worldX, worldZ);
        if (directive.factor <= 0) return local;
        if (directive.replaceTerrain) return local + (sampled - local) * directive.factor;
        return com.cappleapple.boundednotfree.runtime.ProviderTerrainBlend.combine(local, sampled, directive.factor);
    }

    /** Keeps native provider sampling inside the verified 192-block patch without magnifying its terrain. */
    static int foldProviderOffset(long localCoordinate) {
        return ProviderCoordinateFold.fold(localCoordinate, PROVIDER_SAMPLE_HALF_SPAN);
    }

    public DistanceMetric measure(double worldX, double worldZ) {
        return boundaryMeasures.measure(worldX, worldZ);
    }

    public boolean terrainInside(int worldX, int worldZ) {
        DistanceMetric metric = measure(worldX, worldZ);
        if (!"VOID".equalsIgnoreCase(config.outsideMode) || config.voidEdgeDitherWidth <= 0) return metric.inside();
        return VoidEdgeDither.inside(metric.inside(), metric.blocksToEdge(), config.voidEdgeDitherWidth,
                worldX, worldZ, seed ^ 0x564F494444495448L);
    }

    /** Applies the optional three-dimensional dissolve after the column-level boundary is accepted. */
    public boolean terrainBlockInside(int worldX, int worldY, int worldZ) {
        DistanceMetric metric = measure(worldX, worldZ);
        if (!terrainInside(worldX, worldZ)) return false;
        if (!"VOID".equalsIgnoreCase(config.outsideMode) || config.voidBlockDissolveWidth <= 0) return true;
        return VoidBlockDissolve.keepsBlock(metric.inside(), metric.blocksToEdge(), config.voidBlockDissolveWidth,
                config.voidBlockDissolveNoiseScale, worldX, worldY, worldZ,
                seed ^ 0x424C4F434B564F49L);
    }

    public String progressionZone(double worldX, double worldZ) {
        if (!config.progressionZonesEnabled) return null;
        DistanceMetric metric = measure(worldX, worldZ);
        for (var entry : config.progressionZones.entrySet()) {
            LayoutConfig.Zone zone = entry.getValue();
            double value = "ABSOLUTE_BLOCK_DISTANCE".equalsIgnoreCase(zone.distanceMode) ? metric.blocksFromCenter() : metric.normalizedFromCenter();
            if (value >= zone.minDistance && value <= zone.maxDistance) return entry.getKey();
        }
        return null;
    }

    public Holder<Biome> selectBiome(Holder<Biome> original, int quartX, int quartY, int quartZ,
                                     Climate.Sampler sampler) {
        double worldX = quartX * 4.0 + 2.0, worldZ = quartZ * 4.0 + 2.0;
        double localX = worldX - config.centerX, localZ = worldZ - config.centerZ;
        DistanceMetric metric = measure(worldX, worldZ);
        boolean outside = !metric.inside() || ("VOID".equalsIgnoreCase(config.outsideMode)
                && !terrainInside((int)Math.floor(worldX), (int)Math.floor(worldZ)));
        if (outside) {
            if ("NORMAL".equalsIgnoreCase(config.outsideMode)) return original;
            Set<Holder<Biome>> pool = outsideBiomes;
            if (pool.isEmpty() && "VOID".equalsIgnoreCase(config.outsideMode)) pool = biomeResolver.resolve("minecraft:the_void");
            if (pool.isEmpty() && "OCEAN".equalsIgnoreCase(config.outsideMode)) pool = biomeResolver.resolve("#minecraft:is_ocean");
            if (pool.isEmpty() && "LAVA_OCEAN".equalsIgnoreCase(config.outsideMode)) pool = biomeResolver.resolve("minecraft:nether_wastes");
            return pool.isEmpty() ? fallback(original) : climatePick(pool, original, quartX, quartY, quartZ, sampler, 11);
        }
        if (config.rimEnabled && metric.blocksToEdge() <= config.rimWidth + BIOME_TERRAIN_ALIGNMENT_MARGIN
                && !rimBiomes.isEmpty()) {
            String mode = config.rimPlacementMode == null ? "PREFER" : config.rimPlacementMode.toUpperCase(java.util.Locale.ROOT);
            if ("REQUIRE".equals(mode) && !rimBiomes.contains(original)) return climatePick(rimBiomes, original, quartX, quartY, quartZ, sampler, 17);
            return original;
        }
        for (Reservation reservation : biomeReservations) {
            if (Math.hypot(localX - reservation.x, localZ - reservation.z) <= reservation.radius) {
                return climatePick(reservation.biomes, original, quartX, quartY, quartZ, sampler, reservation.index);
            }
        }
        MacroLayoutPlan.Result macroResult = macro.at(localX, localZ);
        if (macroResult != null && !macroResult.biomes().isEmpty()) {
            if (macroResult.influenceFactor() >= 1) {
                return climatePick(macroResult.biomes(), original, quartX, quartY, quartZ, sampler, 23);
            }
            if (macroResult.influenceFactor() < 0.75 && original.is(BiomeTags.IS_OCEAN)
                    && macroResult.biomes().stream().allMatch(biome -> biome.is(BiomeTags.IS_OCEAN))
                    && !providerLandBiomes.isEmpty()) {
                return climatePick(providerLandBiomes, original, quartX, quartY, quartZ, sampler, 29);
            }
        }
        if (biomeAllowed(original, metric, progressionZone(worldX, worldZ), macroResult == null ? null : macroResult.profile())) return original;
        return fallback(original);
    }

    public boolean structureAllowed(Holder<Structure> structure, int chunkX, int chunkZ) {
        double x = chunkX * 16.0 + 8, z = chunkZ * 16.0 + 8;
        DistanceMetric metric = measure(x, z);
        if (!metric.inside()) return false;
        boolean base = requiredStructureTypes.contains(structure) || ("WHITELIST".equalsIgnoreCase(config.structureFilterMode) ? structureFilter.contains(structure) : !structureFilter.contains(structure));
        if (!base) return false;
        String zone = progressionZone(x, z);
        StructureRule rule = structureRules.stream().filter(candidate -> candidate.structures.contains(structure)).findFirst().orElse(null);
        return rule == null || locationAllowed(rule.config, metric, zone, null);
    }

    public int maxCount(Holder<Structure> structure) {
        int result = Integer.MAX_VALUE;
        for (StructureRule rule : structureRules) if (rule.structures.contains(structure)) {
            int configured = rule.config.exactCount != null ? rule.config.exactCount : rule.config.maxCount;
            result = Math.min(result, configured);
        }
        return result;
    }

    private boolean biomeAllowed(Holder<Biome> biome, DistanceMetric metric, String zone, String profile) {
        boolean base = "WHITELIST".equalsIgnoreCase(config.biomeFilterMode) ? biomeFilter.contains(biome) : !biomeFilter.contains(biome);
        if (!base) return false;
        BiomeRule rule = biomeRules.stream().filter(candidate -> candidate.biomes.contains(biome)).findFirst().orElse(null);
        return rule == null || locationAllowed(rule.config, metric, zone, profile);
    }

    private static boolean locationAllowed(LayoutConfig.RuleBase rule, DistanceMetric metric, String zone, String profile) {
        if (metric.blocksFromCenter() < rule.minDistance || metric.blocksFromCenter() > rule.maxDistance
                || metric.blocksToEdge() < rule.minEdgeDistance || metric.blocksToEdge() > rule.maxEdgeDistance) return false;
        if (rule.minNormalizedDistance != null && metric.normalizedFromCenter() < rule.minNormalizedDistance) return false;
        if (rule.maxNormalizedDistance != null && metric.normalizedFromCenter() > rule.maxNormalizedDistance) return false;
        if (!rule.allowedZones.isEmpty() && (zone == null || !rule.allowedZones.contains(zone))) return false;
        if (zone != null && rule.forbiddenZones.contains(zone)) return false;
        if (!rule.allowedProfiles.isEmpty() && (profile == null || !rule.allowedProfiles.contains(profile))) return false;
        return profile == null || !rule.forbiddenProfiles.contains(profile);
    }

    private Holder<Biome> fallback(Holder<Biome> original) {
        if ("ERROR".equalsIgnoreCase(config.invalidBiomeBehavior)) throw new IllegalStateException("No allowed biome at constrained coordinate in " + dimension);
        if ("FALLBACK".equalsIgnoreCase(config.invalidBiomeBehavior) && !fallbackBiomes.isEmpty()) return fallbackBiomes.iterator().next();
        LinkedHashSet<Holder<Biome>> allowed = new LinkedHashSet<>();
        for (Holder<Biome> biome : biomeRegistry.holders().toList()) {
            if (("WHITELIST".equalsIgnoreCase(config.biomeFilterMode) && biomeFilter.contains(biome))
                    || (!"WHITELIST".equalsIgnoreCase(config.biomeFilterMode) && !biomeFilter.contains(biome))) allowed.add(biome);
        }
        if (allowed.isEmpty()) return fallbackBiomes.stream().findFirst().orElse(original);
        return pick(allowed, original.hashCode(), 0, 31);
    }

    private Holder<Biome> pick(Set<Holder<Biome>> pool, int x, int z, int salt) {
        List<Holder<Biome>> ordered = pool.stream().sorted(Comparator.comparing(holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse(""))).toList();
        long mixed = mix(seed ^ ((long)x * 341873128712L) ^ ((long)z * 132897987541L) ^ salt);
        return ordered.get(Math.floorMod(mixed, ordered.size()));
    }

    private Holder<Biome> climatePick(Set<Holder<Biome>> pool, Holder<Biome> original,
                                      int quartX, int quartY, int quartZ, Climate.Sampler sampler, int salt) {
        Climate.TargetPoint target = sampler.sample(quartX, quartY, quartZ);
        Holder<Biome> fallback = pick(pool, biomeId(original).hashCode(), 0, salt);
        return ClimateCandidateSelector.nearest(pool, original, target,
                biome -> biomeClimatePoints.getOrDefault(biome, List.of()),
                Comparator.comparing(this::biomeId), fallback);
    }

    private String biomeId(Holder<Biome> holder) {
        return holder.unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    private TerrainDirective terrainDirective(int worldX, int worldZ) {
        TerrainDirectiveCache cache = terrainDirectiveCache.get();
        if (cache.valid && cache.x == worldX && cache.z == worldZ) return cache.directive;
        TerrainDirective directive = computeTerrainDirective(worldX, worldZ);
        cache.x = worldX;
        cache.z = worldZ;
        cache.directive = directive;
        cache.valid = true;
        return directive;
    }

    private TerrainDirective computeTerrainDirective(int worldX, int worldZ) {
        DistanceMetric metric = measure(worldX, worldZ);
        if (!metric.inside()) return TerrainDirective.NONE;
        if (config.rimEnabled && metric.blocksToEdge() <= config.rimWidth) {
            ClimateTarget target = rimClimateTarget;
            if (target == null) return TerrainDirective.NONE;
            double factor = RimInfluence.factor(true, config.rimWidth, config.rimBlendWidth, metric)
                    * clamp01(config.rimInfluenceStrength);
            ClimateSelection climate = new ClimateSelection(target.holder, target.point);
            return new TerrainDirective(factor, climate, providerSamples.get(target.holder), false);
        }

        double localX = worldX - config.centerX;
        double localZ = worldZ - config.centerZ;
        for (Reservation reservation : biomeReservations) {
            if (Math.hypot(localX - reservation.x, localZ - reservation.z) <= reservation.radius) {
                return terrainDirectiveForPool(reservation.biomes, worldX, worldZ);
            }
        }
        MacroLayoutPlan.Result macroResult = macro.at(localX, localZ);
        if (macroResult == null || macroResult.biomes().isEmpty()) return TerrainDirective.NONE;
        return terrainDirectiveForPool(macroResult.biomes(), worldX, worldZ, macroResult.influenceFactor());
    }

    private TerrainDirective terrainDirectiveForPool(Set<Holder<Biome>> pool, int worldX, int worldZ) {
        return terrainDirectiveForPool(pool, worldX, worldZ, 1);
    }

    private TerrainDirective terrainDirectiveForPool(Set<Holder<Biome>> pool, int worldX, int worldZ,
                                                       double influenceFactor) {
        if (influenceFactor <= 0) return TerrainDirective.NONE;
        Climate.Sampler sampler = originalSampler;
        if (sampler == null) return TerrainDirective.NONE;
        Climate.TargetPoint original = sampler.sample(QuartPos.fromBlock(worldX), QuartPos.fromBlock(climateSampleY),
                QuartPos.fromBlock(worldZ));
        ClimateSelection selection = nearestClimateSelection(pool, original);
        if (selection == null) return TerrainDirective.NONE;
        return new TerrainDirective(clamp01(influenceFactor), selection,
                providerSamples.get(selection.biome), true);
    }

    private ClimateSelection nearestClimateSelection(Set<Holder<Biome>> pool, Climate.TargetPoint target) {
        ClimateSelection best = null;
        long bestFitness = Long.MAX_VALUE;
        for (Holder<Biome> biome : pool.stream().sorted(Comparator.comparing(this::biomeId)).toList()) {
            for (Climate.ParameterPoint point : biomeClimatePoints.getOrDefault(biome, List.of())) {
                long fitness = ClimateCandidateSelector.fitness(point, target);
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    best = new ClimateSelection(biome, point);
                }
            }
        }
        return best;
    }

    private LinkedHashSet<Holder<Biome>> constrainedTerrainBiomes() {
        LinkedHashSet<Holder<Biome>> result = new LinkedHashSet<>();
        ClimateTarget rim = rimClimateTarget;
        if (config.rimEnabled && config.rimInfluenceStrength > 0 && rim != null) result.add(rim.holder);
        for (Reservation reservation : biomeReservations) result.addAll(reservation.biomes);
        for (Set<Holder<Biome>> pool : macro.biomePools()) result.addAll(pool);
        return result;
    }

    private List<BiomeRule> resolveBiomeRules(List<LayoutConfig.BiomeRule> rules) {
        return rules.stream().map(rule -> new BiomeRule(rule, biomeResolver.resolve(rule.selector), specificity(rule.selector)))
                .sorted(Comparator.<BiomeRule>comparingInt(rule -> rule.config.priority).reversed().thenComparing(Comparator.comparingInt(BiomeRule::specificity).reversed())).toList();
    }

    private List<StructureRule> resolveStructureRules(List<LayoutConfig.StructureRule> rules) {
        return rules.stream().map(rule -> new StructureRule(rule, structureResolver.resolve(rule.selector), specificity(rule.selector)))
                .sorted(Comparator.<StructureRule>comparingInt(rule -> rule.config.priority).reversed().thenComparing(Comparator.comparingInt(StructureRule::specificity).reversed())).toList();
    }

    private List<Reservation> planBiomeReservations(List<LayoutConfig.BiomeRule> rules) {
        ArrayList<Reservation> result = new ArrayList<>();
        List<LayoutConfig.BiomeRule> ordered = rules.stream().sorted(Comparator.comparingInt((LayoutConfig.BiomeRule r) -> r.priority).reversed()).toList();
        long state = seed;
        for (LayoutConfig.BiomeRule rule : ordered) {
            Set<Holder<Biome>> biomes = biomeResolver.resolve(rule.selector);
            int count = Math.max(rule.minInstances, 1);
            double radius = Math.sqrt(Math.max(256, rule.minArea) / Math.PI);
            for (int instance = 0, attempts = 0; instance < count && attempts++ < config.maxPlannerAttempts;) {
                state = mix(state); double x = (unit(state) * 2 - 1) * config.radius;
                state = mix(state); double z = (unit(state) * 2 - 1) * config.radius;
                DistanceMetric metric = boundary.measure(x, z);
                if (biomes.isEmpty() || !metric.inside() || !locationAllowed(rule, metric, progressionZone(x + config.centerX, z + config.centerZ), null)) continue;
                if (result.stream().anyMatch(other -> Math.hypot(x - other.x, z - other.z) < radius + other.radius)) continue;
                result.add(new Reservation(rule.selector, x, z, radius, instance, biomes));
                instance++;
            }
        }
        return List.copyOf(result);
    }

    private MacroLayoutPlan createMacro(LayoutConfig root) {
        String mode = config.biomeLayout.toUpperCase(java.util.Locale.ROOT);
        List<MacroLayoutPlan.ResolvedBand> radial = config.radialBands.stream().map(b -> new MacroLayoutPlan.ResolvedBand(b.min, b.max, b.profile, bandBiomes(root, b))).toList();
        List<MacroLayoutPlan.ResolvedBand> climate = config.climateBands.stream().map(b -> new MacroLayoutPlan.ResolvedBand(b.min, b.max, b.profile, bandBiomes(root, b))).toList();
        var profileResolver = (java.util.function.Function<String, Set<Holder<Biome>>>) name -> name == null || !root.continentProfiles.containsKey(name)
                ? Set.of() : biomeResolver.resolveAll(root.continentProfiles.get(name).selectors);
        List<MacroLayoutPlan.Region> regions = MacroLayoutPlan.generateRegions(config, boundary, seed ^ 0x4D4143524FL, profileResolver);
        Set<Holder<Biome>> between = biomeResolver.resolveAll(config.betweenRegionSelectors);
        if ("VOID".equalsIgnoreCase(config.betweenRegionsMode)) between = biomeResolver.resolve("minecraft:the_void");
        return new MacroLayoutPlan(mode, config, boundary, radial, climate, regions, between);
    }

    private Set<Holder<Biome>> bandBiomes(LayoutConfig root, LayoutConfig.Band band) {
        if (band.profile != null && root.continentProfiles.containsKey(band.profile)) return biomeResolver.resolveAll(root.continentProfiles.get(band.profile).selectors);
        return biomeResolver.resolveAll(band.selectors);
    }

    private void validateRules(List<LayoutConfig.BiomeRule> biomes, List<LayoutConfig.StructureRule> structures) {
        for (LayoutConfig.RuleBase rule : concat(biomes, structures)) {
            if (rule.minDistance > rule.maxDistance) validation.add("Impossible range for " + rule.selector + ": minDistance > maxDistance");
            if (rule.minNormalizedDistance != null && rule.maxNormalizedDistance != null && rule.minNormalizedDistance > rule.maxNormalizedDistance) validation.add("Impossible normalized range for " + rule.selector);
        }
        for (LayoutConfig.BiomeRule rule : biomes) if (biomeResolver.resolve(rule.selector).isEmpty()) validation.add("Required biome selector resolves to nothing: " + rule.selector);
        for (LayoutConfig.StructureRule rule : structures) if (structureResolver.resolve(rule.selector).isEmpty()) validation.add("Required structure selector resolves to nothing: " + rule.selector);
        if ("WHITELIST".equalsIgnoreCase(config.biomeFilterMode) && biomeFilter.isEmpty()) validation.add("Biome whitelist resolves to zero biomes");
    }

    private void validateInfluence() {
        if (config.rimInfluenceStrength < 0 || config.rimInfluenceStrength > 1) validation.add("rimInfluenceStrength must be from 0 through 1");
        if (config.rimBlendWidth < 0) validation.add("rimBlendWidth cannot be negative");
        if (!Double.isFinite(config.voidEdgeDitherWidth) || config.voidEdgeDitherWidth < 0)
            validation.add("voidEdgeDitherWidth must be a finite non-negative number");
        if (!Double.isFinite(config.voidBlockDissolveWidth) || config.voidBlockDissolveWidth < 0)
            validation.add("voidBlockDissolveWidth must be a finite non-negative number");
        if (!Double.isFinite(config.voidBlockDissolveNoiseScale) || config.voidBlockDissolveNoiseScale < 1)
            validation.add("voidBlockDissolveNoiseScale must be a finite number of at least 1");
        if (!Double.isFinite(config.macroTransitionWidth) || config.macroTransitionWidth < 0)
            validation.add("macroTransitionWidth must be a finite non-negative number");
        if (config.rimTerrainStyle == null || !Set.of("NATIVE", "CAVE_WALL")
                .contains(config.rimTerrainStyle.toUpperCase(java.util.Locale.ROOT)))
            validation.add("rimTerrainStyle must be NATIVE or CAVE_WALL");
        if (!Double.isFinite(config.rimCaveWallWidth) || config.rimCaveWallWidth <= 0)
            validation.add("rimCaveWallWidth must be a finite positive number");
        if (!Double.isFinite(config.rimCaveWallFloorY) || !Double.isFinite(config.rimCaveWallTopY)
                || config.rimCaveWallFloorY >= config.rimCaveWallTopY)
            validation.add("rimCaveWallFloorY must be lower than rimCaveWallTopY");
        if (!Double.isFinite(config.rimCaveWallSurfaceNoiseScale) || config.rimCaveWallSurfaceNoiseScale < 1)
            validation.add("rimCaveWallSurfaceNoiseScale must be a finite number of at least 1");
        if (!Double.isFinite(config.rimCaveWallSurfaceNoiseStrength) || config.rimCaveWallSurfaceNoiseStrength < 0)
            validation.add("rimCaveWallSurfaceNoiseStrength must be a finite non-negative number");
        if (!Double.isFinite(config.rimCaveWallCaveScale) || config.rimCaveWallCaveScale < 1)
            validation.add("rimCaveWallCaveScale must be a finite number of at least 1");
        if (!Double.isFinite(config.rimCaveWallCaveThreshold) || config.rimCaveWallCaveThreshold < 0
                || config.rimCaveWallCaveThreshold > 1)
            validation.add("rimCaveWallCaveThreshold must be from 0 through 1");
        if ("CAVE_WALL".equalsIgnoreCase(config.rimTerrainStyle) && !config.rimEnabled)
            validation.add("rimTerrainStyle CAVE_WALL requires rimEnabled");
        if (config.rimPlacementMode == null || !Set.of("PREFER", "REQUIRE").contains(config.rimPlacementMode.toUpperCase(java.util.Locale.ROOT)))
            validation.add("rimPlacementMode must be PREFER or REQUIRE");
        if (config.gameplayBorder == null || !Set.of("NONE", "VANILLA_WHERE_POSSIBLE", "CUSTOM_SHAPE", "BARRIER")
                .contains(config.gameplayBorder.toUpperCase(java.util.Locale.ROOT)))
            validation.add("gameplayBorder must be NONE, VANILLA_WHERE_POSSIBLE, CUSTOM_SHAPE, or BARRIER");
    }

    private static List<LayoutConfig.RuleBase> concat(List<? extends LayoutConfig.RuleBase> a, List<? extends LayoutConfig.RuleBase> b) {
        ArrayList<LayoutConfig.RuleBase> result = new ArrayList<>(a); result.addAll(b); return result;
    }
    private static int specificity(String selector) { return selector.startsWith("group:") ? 1 : selector.startsWith("#") ? 2 : 3; }
    private static long mix(long z) { z += 0x9E3779B97F4A7C15L; z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L; z = (z ^ z >>> 27) * 0x94D049BB133111EBL; return z ^ z >>> 31; }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private ClimateTarget climateTarget(Pair<Climate.ParameterPoint, Holder<Biome>> pair) {
        Climate.ParameterPoint point = pair.getFirst();
        double continentalness = mid(point.continentalness());
        double erosion = mid(point.erosion());
        double weirdness = mid(point.weirdness());
        double depth = mid(point.depth());
        double score = continentalness * 2.0 - erosion * 2.0 + NoiseRouterData.peaksAndValleys((float)weirdness) * 4.0 - Math.abs(depth) * 8.0;
        String biome = pair.getSecond().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
        return new ClimateTarget(pair.getSecond(), biome, point, score);
    }

    private void findProviderSamples(BiomeSource source, Climate.Sampler sampler, DensityFunction finalDensity,
                                     int seaLevel, int minBuildHeight, int maxBuildHeight,
                                     Set<Holder<Biome>> requestedBiomes) {
        int searchRadius = (int)Math.min(1_000_000, Math.max(20_000, config.radius * 8));
        LinkedHashMap<Holder<Biome>, ProviderSample> best = new LinkedHashMap<>();
        LinkedHashMap<Holder<Biome>, List<ProviderCandidate>> candidates = new LinkedHashMap<>();
        LinkedHashMap<Holder<Biome>, Double> terrainScores = new LinkedHashMap<>();
        for (Holder<Biome> biome : requestedBiomes) {
            double score = biomeClimatePoints.getOrDefault(biome, List.of()).stream()
                    .mapToDouble(point -> climateTarget(Pair.of(point, biome)).score)
                    .max().orElse(0);
            terrainScores.put(biome, score);
        }

        long state = seed ^ 0x50524F5649444552L;
        for (int attempt = 0; attempt < Math.min(config.maxPlannerAttempts, 4096); attempt++) {
            state = mix(state);
            int x = (int)Math.round((unit(state) * 2 - 1) * searchRadius);
            state = mix(state);
            int z = (int)Math.round((unit(state) * 2 - 1) * searchRadius);
            int centerSurface;
            try {
                centerSurface = estimateSurfaceY(finalDensity, x, z, minBuildHeight, maxBuildHeight);
            } catch (RuntimeException exception) {
                validation.add("Could not evaluate the active provider terrain while locating compatible biome samples: "
                        + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                return;
            }
            LinkedHashSet<Holder<Biome>> observed = new LinkedHashSet<>();
            for (int y : new int[]{seaLevel, Math.max(seaLevel, centerSurface),
                    Math.min(maxBuildHeight - 1, Math.max(seaLevel, centerSurface + 32))}) {
                Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
                if (requestedBiomes.contains(biome)) observed.add(biome);
            }
            if (observed.isEmpty()) continue;
            for (Holder<Biome> biome : observed) {
                boolean ocean = biome.is(BiomeTags.IS_OCEAN);
                boolean mountain = rimBiomes.contains(biome);
                double quickScore = ProviderSampleRanker.score(ocean, mountain,
                        terrainScores.getOrDefault(biome, 0.0), centerSurface, centerSurface,
                        centerSurface, seaLevel, maxBuildHeight);
                List<ProviderCandidate> shortlist = candidates.computeIfAbsent(biome, ignored -> new ArrayList<>());
                shortlist.add(new ProviderCandidate(x, z, centerSurface, quickScore));
                shortlist.sort(Comparator.comparingDouble(ProviderCandidate::score).reversed());
                if (shortlist.size() > 8) shortlist.remove(shortlist.size() - 1);
            }
        }

        // Full 7x7 patch evaluation is intentionally limited to the strongest center candidates.
        // Evaluating it for every common ocean hit made C2ME+Tectonic startup take over a minute.
        for (var entry : candidates.entrySet()) for (ProviderCandidate candidate : entry.getValue()) {
            Holder<Biome> biome = entry.getKey();
            int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
            double total = 0;
            int patchSamples = 0;
            for (int dx = -PROVIDER_SAMPLE_HALF_SPAN; dx <= PROVIDER_SAMPLE_HALF_SPAN; dx += 32) {
                for (int dz = -PROVIDER_SAMPLE_HALF_SPAN; dz <= PROVIDER_SAMPLE_HALF_SPAN; dz += 32) {
                    int surface = dx == 0 && dz == 0 ? candidate.surface
                            : estimateSurfaceY(finalDensity, candidate.x + dx, candidate.z + dz,
                            minBuildHeight, maxBuildHeight);
                    minimum = Math.min(minimum, surface);
                    maximum = Math.max(maximum, surface);
                    total += surface;
                    patchSamples++;
                }
            }
            double average = total / patchSamples;
            boolean ocean = biome.is(BiomeTags.IS_OCEAN);
            boolean mountain = rimBiomes.contains(biome);
            double score = ProviderSampleRanker.score(ocean, mountain, terrainScores.getOrDefault(biome, 0.0),
                    minimum, maximum, average, seaLevel, maxBuildHeight);
            score = ProviderSampleRanker.penalizeCeilingClipping(score, maximum, maxBuildHeight);
            ProviderSample current = best.get(biome);
            if (current == null || score > current.score) {
                best.put(biome, new ProviderSample(candidate.x, candidate.surface, candidate.z,
                        minimum, maximum, average, score));
            }
        }
        providerSamples = Collections.unmodifiableMap(best);
        ClimateTarget rim = rimClimateTarget;
        ProviderSample rimSample = rim == null ? null : best.get(rim.holder);
        if (rimSample == null && rim != null) {
            rimSample = rimBiomes.stream().map(best::get).filter(java.util.Objects::nonNull)
                    .max(Comparator.comparingDouble(ProviderSample::score)).orElse(null);
        }
        if (rimSample != null) {
            providerSampleX = rimSample.x;
            providerSampleY = rimSample.y;
            providerSampleZ = rimSample.z;
        }
        if (rim != null && rimSample == null) {
            validation.add("Could not find a provider-native rim terrain sample within " + searchRadius
                    + " blocks; density-decoupled generators cannot produce the configured rim terrain safely");
        }
        long missing = requestedBiomes.stream().filter(biome -> !best.containsKey(biome)).count();
        if (missing > 0) validation.add("Could not locate provider-native terrain samples for " + missing
                + " constrained biome(s); those biomes retain local provider terrain on density-decoupled generators");
    }

    private static int estimateSurfaceY(DensityFunction density, int x, int z, int minY, int maxY) {
        int coarse = minY - 1;
        for (int y = maxY - 1; y >= minY; y -= 8) {
            if (density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) {
                coarse = y;
                break;
            }
        }
        if (coarse < minY) return coarse;
        for (int y = Math.min(maxY - 1, coarse + 7); y >= coarse; y--) {
            if (density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) return y;
        }
        return coarse;
    }

    /**
     * Finds the provider's real multi-noise source without linking against optional wrapper mods.
     * Lithostitched, for example, owns and updates the root parameter list but presents an
     * InjectorBiomeSource to the chunk generator. Public delegate accessors are preferred; the
     * narrow field fallback supports equivalent transparent wrappers.
     */
    private static MultiNoiseBiomeSource findMultiNoiseSource(BiomeSource source) {
        ArrayDeque<BiomeSource> pending = new ArrayDeque<>();
        Set<BiomeSource> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(source);
        while (!pending.isEmpty() && visited.size() < 16) {
            BiomeSource current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof MultiNoiseBiomeSource multiNoise) return multiNoise;

            for (String name : List.of("rootDelegate", "directDelegate", "delegate", "getDelegate",
                    "originalSource", "getOriginalSource")) {
                try {
                    Method method = current.getClass().getMethod(name);
                    if (method.getParameterCount() == 0 && BiomeSource.class.isAssignableFrom(method.getReturnType())) {
                        Object value = method.invoke(current);
                        if (value instanceof BiomeSource delegate && !visited.contains(delegate)) pending.addLast(delegate);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Optional wrappers are discovered best-effort and are never required classes.
                }
            }

            for (Class<?> type = current.getClass(); type != null && type != BiomeSource.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                    if (!BiomeSource.class.isAssignableFrom(field.getType())
                            || !(name.contains("delegate") || name.contains("source") || name.contains("wrapped"))) continue;
                    try {
                        if (field.trySetAccessible()) {
                            Object value = field.get(current);
                            if (value instanceof BiomeSource delegate && !visited.contains(delegate)) pending.addLast(delegate);
                        }
                    } catch (IllegalAccessException | RuntimeException ignored) {
                        // A wrapper that does not expose its delegate simply remains unsupported.
                    }
                }
            }
        }
        return null;
    }
    private static double mid(Climate.Parameter parameter) {
        return Climate.unquantizeCoord((parameter.min() + parameter.max()) / 2L);
    }
    private static Climate.Parameter parameter(Climate.ParameterPoint point, ClimateChannel channel) {
        return switch (channel) {
            case CONTINENTALNESS -> point.continentalness();
            case EROSION -> point.erosion();
            case WEIRDNESS -> point.weirdness();
        };
    }
    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.3f", value); }
}
