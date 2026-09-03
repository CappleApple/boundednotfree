package com.cappleapple.boundednotfree.command;

import com.cappleapple.boundednotfree.plan.DimensionPlan;
import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Dimension-plan-aware implementations of the three vanilla {@code /locate} searches. */
public final class LocateSearch {
    private LocateSearch() {}

    public static Pair<BlockPos, Holder<Structure>> findNearestStructure(
            ChunkGenerator generator, ServerLevel level, HolderSet<Structure> structures, BlockPos origin,
            int searchRadius, boolean skipKnownStructures) {
        DimensionPlan plan = LayoutRuntime.plan(generator);
        if (plan == null) {
            return generator.findNearestMapStructure(level, structures, origin, searchRadius, skipKnownStructures);
        }
        if (!plan.measure(origin.getX(), origin.getZ()).inside()) return null;

        var state = level.getChunkSource().getGeneratorState();
        Map<StructurePlacement, Set<Holder<Structure>>> placements = new Object2ObjectArrayMap<>();
        for (Holder<Structure> structure : structures) {
            for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
                placements.computeIfAbsent(placement, ignored -> new ObjectArraySet<>()).add(structure);
            }
        }
        if (placements.isEmpty()) return null;

        StructureManager structureManager = level.structureManager();
        Pair<BlockPos, Holder<Structure>> nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        List<Map.Entry<RandomSpreadStructurePlacement, Set<Holder<Structure>>>> spreadPlacements = new ArrayList<>();

        for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> entry : placements.entrySet()) {
            if (entry.getKey() instanceof ConcentricRingsStructurePlacement rings) {
                Pair<BlockPos, Holder<Structure>> candidate = findNearestRingStructure(
                        entry.getValue(), level, structureManager, origin, skipKnownStructures, rings, plan);
                if (candidate != null) {
                    double distance = origin.distSqr(candidate.getFirst());
                    if (distance < nearestDistance) {
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }
            } else if (entry.getKey() instanceof RandomSpreadStructurePlacement spread) {
                spreadPlacements.add(Map.entry(spread, entry.getValue()));
            }
        }

        if (spreadPlacements.isEmpty()) return nearest;
        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

        for (int ring = 0; ring <= searchRadius; ring++) {
            boolean reachedEdge = false;
            Pair<BlockPos, Holder<Structure>> nearestOnRing = null;
            double nearestOnRingDistance = Double.MAX_VALUE;

            for (Map.Entry<RandomSpreadStructurePlacement, Set<Holder<Structure>>> entry : spreadPlacements) {
                RandomSpreadStructurePlacement placement = entry.getKey();
                int spacing = placement.spacing();
                for (int offsetZ = -ring; offsetZ <= ring; offsetZ++) {
                    boolean zEdge = offsetZ == -ring || offsetZ == ring;
                    for (int offsetX = -ring; offsetX <= ring; offsetX++) {
                        if (!zEdge && offsetX != -ring && offsetX != ring) continue;
                        ChunkPos chunk = placement.getPotentialStructureChunk(
                                state.getLevelSeed(), originChunkX + spacing * offsetZ, originChunkZ + spacing * offsetX);
                        int blockX = SectionPos.sectionToBlockCoord(chunk.x, 8);
                        int blockZ = SectionPos.sectionToBlockCoord(chunk.z, 8);
                        if (!plan.measure(blockX, blockZ).inside()) {
                            reachedEdge = true;
                            continue;
                        }
                        Pair<BlockPos, Holder<Structure>> candidate = structureGeneratingAt(
                                entry.getValue(), level, structureManager, skipKnownStructures, placement, chunk);
                        if (candidate != null) {
                            double distance = origin.distSqr(candidate.getFirst());
                            if (distance < nearestOnRingDistance) {
                                nearestOnRing = candidate;
                                nearestOnRingDistance = distance;
                            }
                        }
                    }
                }
            }

            if (nearestOnRing != null) {
                return nearest == null || nearestOnRingDistance < nearestDistance ? nearestOnRing : nearest;
            }
            if (reachedEdge) return nearest;
        }
        return nearest;
    }

    public static Pair<BlockPos, Holder<Biome>> findClosestBiome(
            ServerLevel level, Predicate<Holder<Biome>> biomePredicate, BlockPos origin,
            int radius, int horizontalStep, int verticalStep) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        DimensionPlan plan = LayoutRuntime.plan(generator);
        if (plan == null) return level.findClosestBiome3d(biomePredicate, origin, radius, horizontalStep, verticalStep);
        if (!plan.measure(origin.getX(), origin.getZ()).inside()) return null;

        BiomeSource source = generator.getBiomeSource();
        Set<Holder<Biome>> matches = source.possibleBiomes().stream()
                .filter(biomePredicate)
                .collect(Collectors.toUnmodifiableSet());
        if (matches.isEmpty()) return null;

        int horizontalRadius = Math.floorDiv(radius, horizontalStep);
        int[] ySamples = Mth.outFromOrigin(origin.getY(), level.getMinBuildHeight() + 1,
                level.getMaxBuildHeight(), verticalStep).toArray();
        for (BlockPos.MutableBlockPos offset : BlockPos.spiralAround(BlockPos.ZERO, horizontalRadius,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH)) {
            int x = origin.getX() + offset.getX() * horizontalStep;
            int z = origin.getZ() + offset.getZ() * horizontalStep;
            if (!plan.measure(x, z).inside()) return null;
            int quartX = QuartPos.fromBlock(x);
            int quartZ = QuartPos.fromBlock(z);
            for (int y : ySamples) {
                Holder<Biome> biome = source.getNoiseBiome(quartX, QuartPos.fromBlock(y), quartZ,
                        level.getChunkSource().randomState().sampler());
                if (matches.contains(biome)) return Pair.of(new BlockPos(x, y, z), biome);
            }
        }
        return null;
    }

    public static Optional<Pair<Holder<PoiType>, BlockPos>> findClosestPoi(
            ServerLevel level, PoiManager poiManager, Predicate<Holder<PoiType>> poiPredicate,
            BlockPos origin, int radius, PoiManager.Occupancy occupancy) {
        DimensionPlan plan = LayoutRuntime.plan(level.getChunkSource().getGenerator());
        if (plan == null) return poiManager.findClosestWithType(poiPredicate, origin, radius, occupancy);
        var originMetric = plan.measure(origin.getX(), origin.getZ());
        if (!originMetric.inside()) return Optional.empty();

        int boundedRadius = LocateSearchBounds.blockRadius(originMetric.blocksToEdge(), radius);
        return poiManager.findAllWithType(poiPredicate,
                        pos -> plan.measure(pos.getX(), pos.getZ()).inside(), origin, boundedRadius, occupancy)
                .min(Comparator.comparingDouble(candidate -> candidate.getSecond().distSqr(origin)));
    }

    private static Pair<BlockPos, Holder<Structure>> findNearestRingStructure(
            Set<Holder<Structure>> structures, ServerLevel level, StructureManager structureManager,
            BlockPos origin, boolean skipKnownStructures, ConcentricRingsStructurePlacement placement,
            DimensionPlan plan) {
        List<ChunkPos> positions = level.getChunkSource().getGeneratorState().getRingPositionsFor(placement);
        if (positions == null) {
            throw new IllegalStateException("Tried to locate a structure for an unavailable concentric-ring placement");
        }
        Pair<BlockPos, Holder<Structure>> nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ChunkPos chunk : positions) {
            int blockX = SectionPos.sectionToBlockCoord(chunk.x, 8);
            int blockZ = SectionPos.sectionToBlockCoord(chunk.z, 8);
            if (!plan.measure(blockX, blockZ).inside()) continue;
            double distance = origin.distSqr(new BlockPos(blockX, 32, blockZ));
            if (distance >= nearestDistance) continue;
            Pair<BlockPos, Holder<Structure>> candidate = structureGeneratingAt(
                    structures, level, structureManager, skipKnownStructures, placement, chunk);
            if (candidate != null) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static Pair<BlockPos, Holder<Structure>> structureGeneratingAt(
            Set<Holder<Structure>> structures, ServerLevel level, StructureManager structureManager,
            boolean skipKnownStructures, StructurePlacement placement, ChunkPos chunkPos) {
        for (Holder<Structure> structure : structures) {
            StructureCheckResult result = structureManager.checkStructurePresence(
                    chunkPos, structure.value(), placement, skipKnownStructures);
            if (result == StructureCheckResult.START_NOT_PRESENT) continue;
            if (!skipKnownStructures && result == StructureCheckResult.START_PRESENT) {
                return Pair.of(placement.getLocatePos(chunkPos), structure);
            }

            ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = structureManager.getStartForStructure(
                    SectionPos.bottomOf(chunk), structure.value(), chunk);
            if (start != null && start.isValid() && (!skipKnownStructures || addReference(structureManager, start))) {
                return Pair.of(placement.getLocatePos(start.getChunkPos()), structure);
            }
        }
        return null;
    }

    private static boolean addReference(StructureManager structureManager, StructureStart start) {
        if (!start.canBeReferenced()) return false;
        structureManager.addReference(start);
        return true;
    }
}
