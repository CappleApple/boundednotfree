package com.cappleapple.boundednotfree.plan;

import com.cappleapple.boundednotfree.api.BoundaryShape;
import com.cappleapple.boundednotfree.config.LayoutConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class MacroLayoutPlan {
    record Region(double x, double z, double radius, String profile, Set<Holder<Biome>> biomes, long salt) {}
    record Result(Set<Holder<Biome>> biomes, String profile) {}

    private final String mode;
    private final LayoutConfig.Dimension config;
    private final BoundaryShape boundary;
    private final List<ResolvedBand> radialBands;
    private final List<ResolvedBand> climateBands;
    private final List<Region> regions;
    private final Set<Holder<Biome>> between;

    record ResolvedBand(double min, double max, String profile, Set<Holder<Biome>> biomes) {}

    MacroLayoutPlan(String mode, LayoutConfig.Dimension config, BoundaryShape boundary,
                    List<ResolvedBand> radialBands, List<ResolvedBand> climateBands,
                    List<Region> regions, Set<Holder<Biome>> between) {
        this.mode = mode;
        this.config = config;
        this.boundary = boundary;
        this.radialBands = List.copyOf(radialBands);
        this.climateBands = List.copyOf(climateBands);
        this.regions = List.copyOf(regions);
        this.between = Set.copyOf(between);
    }

    Result at(double x, double z) {
        return switch (mode) {
            case "RADIAL" -> band(radialBands, distorted(boundary.normalizedDistance(x, z), x, z));
            case "CLIMATE_BANDS" -> band(climateBands, climateCoordinate(x, z));
            case "VORONOI" -> nearest(x, z, false);
            case "CONTINENTS", "ARCHIPELAGO" -> nearest(x, z, true);
            default -> null;
        };
    }

    private Result band(List<ResolvedBand> bands, double coordinate) {
        for (ResolvedBand band : bands) if (coordinate >= band.min && coordinate <= band.max) return new Result(band.biomes, band.profile);
        return null;
    }

    private Result nearest(double x, double z, boolean requireInside) {
        Region nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Region region : regions) {
            double distance = Math.hypot(x - region.x, z - region.z);
            double warpedRadius = region.radius * (1.0 + 0.18 * noise(x * 0.0015, z * 0.0015, region.salt));
            if (requireInside && distance > warpedRadius) continue;
            double score = requireInside ? distance / warpedRadius : distance;
            if (score < best) { best = score; nearest = region; }
        }
        return nearest == null ? (between.isEmpty() ? null : new Result(between, null)) : new Result(nearest.biomes, nearest.profile);
    }

    private double climateCoordinate(double x, double z) {
        double extent = Math.max(config.extentX > 0 ? config.extentX : config.radius, config.extentZ > 0 ? config.extentZ : config.radius);
        double value = switch (config.climateAxis.toUpperCase(java.util.Locale.ROOT)) {
            case "EAST_WEST" -> (x / extent + 1) * 0.5;
            case "RADIAL" -> boundary.normalizedDistance(x, z);
            case "ANGLE" -> ((x * Math.cos(Math.toRadians(config.climateAngle)) + z * Math.sin(Math.toRadians(config.climateAngle))) / extent + 1) * 0.5;
            default -> (z / extent + 1) * 0.5;
        };
        return distorted(value, x, z);
    }

    private double distorted(double value, double x, double z) {
        double extent = Math.max(1, config.radius);
        return value + noise(x * 0.001, z * 0.001, config.layoutSalt) * config.transitionNoiseStrength / extent;
    }

    private static double noise(double x, double z, long seed) {
        long ix = (long)Math.floor(x), iz = (long)Math.floor(z);
        long n = seed ^ ix * 0x9E3779B97F4A7C15L ^ iz * 0xD1B54A32D192ED03L;
        n ^= n >>> 30; n *= 0xBF58476D1CE4E5B9L; n ^= n >>> 27; n *= 0x94D049BB133111EBL; n ^= n >>> 31;
        return (n >>> 11) * 0x1.0p-52 * 2 - 1;
    }

    static List<Region> generateRegions(LayoutConfig.Dimension config, BoundaryShape boundary, long seed,
                                        java.util.function.Function<String, Set<Holder<Biome>>> profileResolver) {
        int count = switch (config.biomeLayout.toUpperCase(java.util.Locale.ROOT)) {
            case "CONTINENTS" -> config.continentCount;
            case "ARCHIPELAGO" -> config.islandCount;
            default -> config.regionCount;
        };
        ArrayList<Region> result = new ArrayList<>();
        long state = seed;
        int attempts = 0;
        while (result.size() < count && attempts++ < config.maxPlannerAttempts) {
            state = mix(state); double x = (unit(state) * 2 - 1) * config.radius;
            state = mix(state); double z = (unit(state) * 2 - 1) * config.radius;
            if (!boundary.contains(x, z)) continue;
            state = mix(state); double radius = config.minRegionRadius + unit(state) * Math.max(0, config.maxRegionRadius - config.minRegionRadius);
            boolean spaced = result.stream().allMatch(region -> Math.hypot(x - region.x, z - region.z) >= config.minRegionSpacing);
            if (!spaced) continue;
            String profile = profileFor(config, result.size());
            result.add(new Region(x, z, radius, profile, profileResolver.apply(profile), state));
        }
        return result;
    }

    private static String profileFor(LayoutConfig.Dimension config, int index) {
        return config.profileSequence.isEmpty() ? null : config.profileSequence.get(index % config.profileSequence.size());
    }
    private static long mix(long z) { z += 0x9E3779B97F4A7C15L; z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L; z = (z ^ z >>> 27) * 0x94D049BB133111EBL; return z ^ z >>> 31; }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
}
