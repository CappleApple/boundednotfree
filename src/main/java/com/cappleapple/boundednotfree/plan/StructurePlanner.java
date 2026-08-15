package com.cappleapple.boundednotfree.plan;

import com.cappleapple.boundednotfree.config.LayoutConfig;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class StructurePlanner {
    private StructurePlanner() {}

    public static List<DimensionPlan.StructureReservation> plan(ServerLevel level, DimensionPlan plan) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        ArrayList<DimensionPlan.StructureReservation> result = new ArrayList<>();
        List<LayoutConfig.StructureRule> rules = plan.requiredStructureRules().stream()
                .sorted(Comparator.comparingInt((LayoutConfig.StructureRule rule) -> rule.priority).reversed()).toList();
        long random = plan.seed() ^ 0x5354525543545552L;
        for (LayoutConfig.StructureRule rule : rules) {
            Set<Holder<Structure>> requested = plan.resolveStructures(rule.selector);
            int count = rule.exactCount != null ? rule.exactCount : rule.minCount;
            if (count <= 0) count = 1;
            int placed = 0;
            for (int attempt = 0; attempt < plan.config().maxPlannerAttempts && placed < count; attempt++) {
                random = mix(random); int chunkX = (int)Math.floor((unit(random) * 2 - 1) * plan.config().radius / 16.0);
                random = mix(random); int chunkZ = (int)Math.floor((unit(random) * 2 - 1) * plan.config().radius / 16.0);
                if (!plan.structureAllowed(requested.stream().findFirst().orElse(null), chunkX, chunkZ)) continue;
                Holder<Structure> candidate = candidateAt(state, requested, chunkX, chunkZ);
                if (candidate == null) continue;
                double spacing = Math.max(rule.minSpacingFromSelf, rule.minSpacingFromAnyStructure);
                boolean separated = result.stream().allMatch(other -> Math.hypot((chunkX - other.chunkX()) * 16.0, (chunkZ - other.chunkZ()) * 16.0) >= spacing);
                if (!separated) continue;
                result.add(new DimensionPlan.StructureReservation(candidate, chunkX, chunkZ, placed++));
            }
        }
        return List.copyOf(result);
    }

    private static Holder<Structure> candidateAt(ChunkGeneratorStructureState state, Set<Holder<Structure>> requested, int chunkX, int chunkZ) {
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            boolean contains = set.value().structures().stream().anyMatch(entry -> requested.contains(entry.structure()));
            if (contains && set.value().placement().isStructureChunk(state, chunkX, chunkZ)) {
                return set.value().structures().stream().map(StructureSet.StructureSelectionEntry::structure).filter(requested::contains).findFirst().orElse(null);
            }
        }
        return null;
    }

    private static long mix(long z) { z += 0x9E3779B97F4A7C15L; z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L; z = (z ^ z >>> 27) * 0x94D049BB133111EBL; return z ^ z >>> 31; }
    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
}
