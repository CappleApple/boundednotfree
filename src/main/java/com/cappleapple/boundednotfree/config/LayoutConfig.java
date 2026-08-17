package com.cappleapple.boundednotfree.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LayoutConfig {
    public int schemaVersion = 1;
    public String requiredContentFailurePolicy = "WARN";
    public Map<String, List<String>> biomeGroups = new LinkedHashMap<>();
    public Map<String, List<String>> structureGroups = new LinkedHashMap<>();
    public Map<String, Profile> continentProfiles = new LinkedHashMap<>();
    public Map<String, Dimension> dimensions = new LinkedHashMap<>();

    public static final class Dimension {
        public boolean enabled;
        public int centerX;
        public int centerZ;
        public double radius = 5000;
        public double extentX;
        public double extentZ;
        public String boundaryShape = "CIRCLE";
        public double cornerRadius = 256;
        public int starPoints = 5;
        public double starInnerRadius = 2500;
        public double starOuterRadius = 5000;
        public double starRotation;
        public double organicNoiseStrength = 500;
        public double organicNoiseScale = 0.001;
        public int organicNoiseOctaves = 2;
        public List<Point> polygonVertices = new ArrayList<>();
        public long layoutSalt = 0x424E4601L;
        public Long customLayoutSeed;
        public boolean rimEnabled;
        public double rimWidth = 256;
        public String rimPlacementMode = "PREFER";
        public List<String> rimSelectors = new ArrayList<>();
        public double rimInfluenceStrength = 1.0;
        public double rimBlendWidth = 128;
        public String rimTerrainStyle = "NATIVE";
        public double rimCaveWallWidth = 96;
        public double rimCaveWallFloorY = -48;
        public double rimCaveWallTopY = 192;
        public double rimCaveWallSurfaceNoiseScale = 48;
        public double rimCaveWallSurfaceNoiseStrength = 12;
        public double rimCaveWallCaveScale = 32;
        public double rimCaveWallCaveThreshold = 0.62;
        public String outsideMode = "NORMAL";
        public List<String> outsideSelectors = new ArrayList<>();
        public double voidEdgeDitherWidth;
        public double voidBlockDissolveWidth;
        public double voidBlockDissolveNoiseScale = 8;
        public String gameplayBorder = "NONE";
        public String biomeFilterMode = "BLACKLIST";
        public List<String> biomeFilter = new ArrayList<>();
        public String invalidBiomeBehavior = "NEAREST_ALLOWED";
        public String fallbackBiome = "minecraft:plains";
        public List<BiomeRule> biomeRules = new ArrayList<>();
        public List<BiomeRule> requiredBiomes = new ArrayList<>();
        public String structureFilterMode = "BLACKLIST";
        public List<String> structureFilter = new ArrayList<>();
        public List<StructureRule> structureRules = new ArrayList<>();
        public List<StructureRule> requiredStructures = new ArrayList<>();
        public String biomeLayout = "VANILLA";
        public List<Band> radialBands = new ArrayList<>();
        public List<Band> climateBands = new ArrayList<>();
        public String climateAxis = "NORTH_SOUTH";
        public double climateAngle;
        public int regionCount = 12;
        public int continentCount = 4;
        public int islandCount = 24;
        public double minRegionRadius = 450;
        public double maxRegionRadius = 1400;
        public double minRegionSpacing = 256;
        public double transitionNoiseStrength = 64;
        public double macroTransitionWidth = 32;
        public String betweenRegionsMode = "BIOME";
        public List<String> betweenRegionSelectors = new ArrayList<>(List.of("#minecraft:is_ocean"));
        public List<String> profileSequence = new ArrayList<>();
        public boolean progressionZonesEnabled;
        public Map<String, Zone> progressionZones = new LinkedHashMap<>();
        public int maxPlannerAttempts = 20000;
        public boolean lockLayoutAfterWorldCreation = true;
    }

    public static class RuleBase {
        public String selector = "minecraft:plains";
        public double minDistance = 0;
        public double maxDistance = Double.POSITIVE_INFINITY;
        public Double minNormalizedDistance;
        public Double maxNormalizedDistance;
        public double minEdgeDistance;
        public double maxEdgeDistance = Double.POSITIVE_INFINITY;
        public int priority;
        public List<String> allowedZones = new ArrayList<>();
        public List<String> forbiddenZones = new ArrayList<>();
        public List<String> allowedProfiles = new ArrayList<>();
        public List<String> forbiddenProfiles = new ArrayList<>();
    }

    public static final class BiomeRule extends RuleBase {
        public int minInstances;
        public double minArea = 65536;
    }

    public static final class StructureRule extends RuleBase {
        public int minCount;
        public int maxCount = Integer.MAX_VALUE;
        public Integer exactCount;
        public double minSpacingFromSelf = 512;
        public double minSpacingFromAnyStructure;
    }

    public static final class Profile {
        public List<String> selectors = new ArrayList<>();
    }

    public static final class Band {
        public double min;
        public double max = 1;
        public List<String> selectors = new ArrayList<>();
        public String profile;
    }

    public static final class Zone {
        public String distanceMode = "NORMALIZED_WORLD_DISTANCE";
        public double minDistance;
        public double maxDistance = 1;
    }

    public static final class Point {
        public double x;
        public double z;
        public boolean normalized;
    }
}
