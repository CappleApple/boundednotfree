package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.plan.DimensionPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/** Applies block-level boundary features after noise generation releases its section locks. */
public final class TerrainHandler {
    private static final EnumSet<Heightmap.Types> GENERATION_HEIGHTMAPS = EnumSet.of(
            Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG);
    private TerrainHandler() {}

    public static ChunkAccess apply(ChunkGenerator generator, ChunkAccess chunk) {
        DimensionPlan plan = LayoutRuntime.plan(generator);
        if (plan == null) return chunk;
        boolean voidOutside = "VOID".equalsIgnoreCase(plan.config().outsideMode);
        boolean barrierEnabled = "BARRIER".equalsIgnoreCase(plan.config().gameplayBorder);
        boolean stabilizeProviderBlend = plan.climateInfluenceStrategy().startsWith("PROVIDER_SAMPLE");
        if (!voidOutside && !barrierEnabled && !stabilizeProviderBlend) return chunk;

        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        BoundaryBarrier.Membership membership = (x, z) -> plan.measure(x, z).inside();

        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = minX + dx, z = minZ + dz;
            boolean inside = membership.inside(x, z);
            if (voidOutside && !inside) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    if (!chunk.getBlockState(pos).isAir()) chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                }
                changed = true;
                continue;
            }
            if (inside && stabilizeProviderBlend) {
                double factor = plan.effectiveClimateInfluenceFactor(x, z);
                if (factor > 0 && factor < 1) {
                    int baseY = Math.max(minY, generator.getSeaLevel());
                    int topY = highestDryTerrain(chunk, pos, x, z, baseY, maxY);
                    if (topY > baseY + 8) {
                        for (int y = baseY; y < topY; y++) {
                            pos.set(x, y, z);
                            var state = chunk.getBlockState(pos);
                            if (state.isAir() || !state.getFluidState().isEmpty()) {
                                chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                                changed = true;
                            }
                        }
                    }
                }
            }
            if (barrierEnabled && BoundaryBarrier.isBarrierColumn(x, z, inside, membership)) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    if (!chunk.getBlockState(pos).is(Blocks.BARRIER)) {
                        chunk.setBlockState(pos, Blocks.BARRIER.defaultBlockState(), false);
                    }
                }
                changed = true;
            }
        }

        if (changed) Heightmap.primeHeightmaps(chunk, GENERATION_HEIGHTMAPS);
        return chunk;
    }

    private static int highestDryTerrain(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
                                         int x, int z, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            pos.set(x, y, z);
            var state = chunk.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty()) return y;
        }
        return minY - 1;
    }
}
