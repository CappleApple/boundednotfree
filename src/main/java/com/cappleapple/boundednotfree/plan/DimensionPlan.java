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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DimensionPlan {
    private static final int PROVIDER_SAMPLE_HALF_SPAN = 96;
    public record Reservation(String selector, double x, double z, double radius, int index, Set<Holder<Biome>> biomes) {}
    public record StructureReservation(Holder<Structure> structure, int chunkX, int chunkZ, int index) {}
    private record BiomeRule(LayoutConfig.BiomeRule config, Set<Holder<Biome>> biomes, int specificity) {}
    private record StructureRule(LayoutConfig.StructureRule config, Set<Holder<Structure>> structures, int specificity) {}
    public record ClimateTarget(String biome, Climate.ParameterPoint point, double score) {}

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
    private volatile int rimClimatePointCount;
    private volatile boolean climateRouterInstalled;
    private volatile String climateInfluenceStrategy = "NONE";
    private volatile String climateParameterSource = "none";
    private volatile Integer providerSampleX;
    private volatile Integer providerSampleZ;
    private volatile int providerSampleY;

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

    public void installClimateTargets(BiomeSource source, Climate.Sampler sampler, int seaLevel, int maxBuildHeight) {
        if (!config.rimEnabled || rimBiomes.isEmpty() || config.rimInfluenceStrength <= 0) return;
        MultiNoiseBiomeSource multiNoise = findMultiNoiseSource(source);
        if (multiNoise == null) {
            validation.add("Rim climate influence could not find a MultiNoiseBiomeSource inside the active source "
                    + source.getClass().getName());
            return;
        }
        climateParameterSource = source == multiNoise ? multiNoise.getClass().getName()
                : source.getClass().getName() + " -> " + multiNoise.getClass().getName();
        Climate.ParameterList<Holder<Biome>> parameters = ((MultiNoiseBiomeSourceAccessor)(Object)multiNoise).boundednotfree$parameters();
        List<ClimateTarget> candidates = parameters.values().stream()
                .filter(pair -> rimBiomes.contains(pair.getSecond()))
                .map(this::climateTarget)
                .sorted(Comparator.comparingDouble(ClimateTarget::score).reversed().thenComparing(ClimateTarget::biome))
                .toList();
        rimClimatePointCount = candidates.size();
        rimClimateTarget = candidates.stream().findFirst().orElse(null);
        if (rimClimateTarget == null) validation.add("No active multi-noise climate parameter points map to rimSelectors; density influence is unavailable");
        else findProviderSample(source, sampler, seaLevel, maxBuildHeight);
    }

    public boolean climateInfluenceReady() { return rimClimateTarget != null; }
    public boolean climateInfluenceActive() { return climateRouterInstalled; }
    public String climateInfluenceStrategy() { return climateInfluenceStrategy; }
    public String climateParameterSource() { return climateParameterSource; }
    public boolean providerSampleReady() { return providerSampleX != null && providerSampleZ != null; }
    public int rimClimatePointCount() { return rimClimatePointCount; }
    public void addValidation(String message) { validation.add(message); }
    public void recordClimateRouter(Map<ClimateChannel, Integer> replacements, String strategy) {
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
        return providerSampleReady() ? providerSampleX + "," + providerSampleY + "," + providerSampleZ : "none";
    }

    public double climateTargetValue(ClimateChannel channel) {
        ClimateTarget target = rimClimateTarget;
        if (target == null) return 0;
        Climate.Parameter range = switch (channel) {
            case CONTINENTALNESS -> target.point.continentalness();
            case EROSION -> target.point.erosion();
            case WEIRDNESS -> target.point.weirdness();
        };
        return mid(range);
    }

    public double rimInfluenceFactor(double worldX, double worldZ) {
        return RimInfluence.factor(config.rimEnabled, config.rimWidth, config.rimBlendWidth, measure(worldX, worldZ));
    }

    public double effectiveClimateInfluenceFactor(double worldX, double worldZ) {
        return rimInfluenceFactor(worldX, worldZ) * clamp01(config.rimInfluenceStrength);
    }

    public int providerSampleX(int worldX) {
        return providerSampleX == null ? worldX : providerSampleX + foldProviderOffset(worldX - config.centerX);
    }

    public int providerSampleZ(int worldZ) {
        return providerSampleZ == null ? worldZ : providerSampleZ + foldProviderOffset(worldZ - config.centerZ);
    }

    /** Keeps native provider sampling inside the verified 192-block patch without magnifying its terrain. */
    static int foldProviderOffset(long localCoordinate) {
        int half = PROVIDER_SAMPLE_HALF_SPAN;
        int phase = (int)Math.floorMod(localCoordinate + half, half * 4L);
        return phase <= half * 2 ? phase - half : half * 3 - phase;
    }

    public DistanceMetric measure(double worldX, double worldZ) {
        return boundaryMeasures.measure(worldX, worldZ);
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

    public Holder<Biome> selectBiome(Holder<Biome> original, int quartX, int quartZ) {
        double worldX = quartX * 4.0 + 2.0, worldZ = quartZ * 4.0 + 2.0;
        double localX = worldX - config.centerX, localZ = worldZ - config.centerZ;
        DistanceMetric metric = measure(worldX, worldZ);
        if (!metric.inside()) {
            if ("NORMAL".equalsIgnoreCase(config.outsideMode)) return original;
            Set<Holder<Biome>> pool = outsideBiomes;
            if (pool.isEmpty() && "VOID".equalsIgnoreCase(config.outsideMode)) pool = biomeResolver.resolve("minecraft:the_void");
            if (pool.isEmpty() && "OCEAN".equalsIgnoreCase(config.outsideMode)) pool = biomeResolver.resolve("#minecraft:is_ocean");
            if (pool.isEmpty() && "LAVA_OCEAN".equalsIgnoreCase(config.outsideMode)) pool = biomeResolver.resolve("minecraft:nether_wastes");
            return pool.isEmpty() ? fallback(original) : pick(pool, quartX, quartZ, 11);
        }
        if (config.rimEnabled && metric.blocksToEdge() <= config.rimWidth && !rimBiomes.isEmpty()) {
            String mode = config.rimPlacementMode == null ? "PREFER" : config.rimPlacementMode.toUpperCase(java.util.Locale.ROOT);
            if ("REQUIRE".equals(mode) && !rimBiomes.contains(original)) return pick(rimBiomes, quartX, quartZ, 17);
            return original;
        }
        for (Reservation reservation : biomeReservations) {
            if (Math.hypot(localX - reservation.x, localZ - reservation.z) <= reservation.radius) return pick(reservation.biomes, quartX, quartZ, reservation.index);
        }
        MacroLayoutPlan.Result macroResult = macro.at(localX, localZ);
        if (macroResult != null && !macroResult.biomes().isEmpty()) return pick(macroResult.biomes(), quartX, quartZ, 23);
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
        return new ClimateTarget(biome, point, score);
    }
    private void findProviderSample(BiomeSource source, Climate.Sampler sampler, int seaLevel, int maxBuildHeight) {
        int searchRadius = (int)Math.min(1_000_000, Math.max(20_000, config.radius * 8));
        int top = Math.max(seaLevel, maxBuildHeight - 32);
        int[] sampleY = {seaLevel, Math.min(top, seaLevel + 32), Math.min(top, seaLevel + 64),
                Math.min(top, seaLevel + 96), Math.min(top, seaLevel + 160)};
        int bestCoverage = -1;
        long state = seed ^ 0x50524F5649444552L;
        for (int attempt = 0; attempt < Math.min(config.maxPlannerAttempts, 8192); attempt++) {
            state = mix(state);
            int x = (int)Math.round((unit(state) * 2 - 1) * searchRadius);
            state = mix(state);
            int z = (int)Math.round((unit(state) * 2 - 1) * searchRadius);
            for (int y : sampleY) {
                Holder<Biome> biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
                if (!rimBiomes.contains(biome)) continue;
                int coverage = 0;
                for (int dx : new int[]{-PROVIDER_SAMPLE_HALF_SPAN, 0, PROVIDER_SAMPLE_HALF_SPAN})
                    for (int dz : new int[]{-PROVIDER_SAMPLE_HALF_SPAN, 0, PROVIDER_SAMPLE_HALF_SPAN}) {
                    Holder<Biome> nearby = source.getNoiseBiome(QuartPos.fromBlock(x + dx), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(z + dz), sampler);
                    if (rimBiomes.contains(nearby)) coverage++;
                }
                if (coverage > bestCoverage) {
                    bestCoverage = coverage;
                    providerSampleX = x;
                    providerSampleY = y;
                    providerSampleZ = z;
                }
                if (coverage == 9) return;
            }
        }
        if (!providerSampleReady()) validation.add("Could not find a provider-native rim biome sample within " + searchRadius
                + " blocks; generators whose terrain is decoupled from climate fields cannot be influenced safely");
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
    private static double clamp01(double value) { return Math.max(0, Math.min(1, value)); }
    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.3f", value); }
}
