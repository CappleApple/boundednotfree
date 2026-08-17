package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.plan.DimensionPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/** Applies block-level boundary features during generation, before a ProtoChunk is promoted to a live chunk. */
public final class TerrainHandler {
    private static final EnumSet<Heightmap.Types> GENERATION_HEIGHTMAPS = EnumSet.of(
            Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG);
    private static final EnumSet<Heightmap.Types> FINAL_HEIGHTMAPS = EnumSet.allOf(Heightmap.Types.class);
    private TerrainHandler() {}

    /**
     * Prevent later decoration stages from undoing block-level boundary behavior applied after noise generation.
     * Features and structure pieces write through WorldGenRegion after this class has already cleared the terrain.
     */
    public static boolean allowsWorldgenWrite(DimensionPlan plan, BlockPos pos, BlockState state) {
        if (plan == null) return true;
        boolean voidOutside = "VOID".equalsIgnoreCase(plan.config().outsideMode);
        boolean barrierEnabled = "BARRIER".equalsIgnoreCase(plan.config().gameplayBorder);
        if (!voidOutside && !barrierEnabled) return true;

        BoundaryBarrier.Membership membership = (x, z) -> plan.measure(x, z).inside();
        boolean boundaryInside = membership.inside(pos.getX(), pos.getZ());
        boolean inside = voidOutside ? plan.terrainBlockInside(pos.getX(), pos.getY(), pos.getZ())
                : boundaryInside;
        boolean barrierColumn = barrierEnabled
                && BoundaryBarrier.isBarrierColumn(pos.getX(), pos.getZ(), boundaryInside, membership);
        return BoundaryWritePolicy.allows(voidOutside, inside, barrierColumn,
                state.isAir(), state.is(Blocks.BARRIER));
    }

    public static ChunkAccess apply(ChunkGenerator generator, ChunkAccess chunk) {
        return apply(generator, chunk, GENERATION_HEIGHTMAPS);
    }

    /** Final authority for a fresh ProtoChunk, after structures, carvers, surfaces, and placed features. */
    public static ChunkAccess finalizeNewChunk(ChunkGenerator generator, ChunkAccess chunk) {
        // Never emit live-world block changes from a chunk-scheduler promotion worker.
        if (chunk instanceof LevelChunk) return chunk;
        return apply(generator, chunk, FINAL_HEIGHTMAPS);
    }

    private static ChunkAccess apply(ChunkGenerator generator, ChunkAccess chunk,
                                     EnumSet<Heightmap.Types> heightmaps) {
        DimensionPlan plan = LayoutRuntime.plan(generator);
        if (plan == null) return chunk;
        boolean voidOutside = "VOID".equalsIgnoreCase(plan.config().outsideMode);
        boolean barrierEnabled = "BARRIER".equalsIgnoreCase(plan.config().gameplayBorder);
        // Provider transitions are already shaped by the density graph. Filling post-noise gaps here
        // extrudes Tectonic overhangs and surface fragments into solid walls and pillars.
        if (!voidOutside && !barrierEnabled) return chunk;

        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        BoundaryBarrier.Membership membership = (x, z) -> plan.measure(x, z).inside();

        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = minX + dx, z = minZ + dz;
            boolean inside = membership.inside(x, z);
            boolean barrierColumn = barrierEnabled && BoundaryBarrier.isBarrierColumn(x, z, inside, membership);
            boolean terrainInside = !voidOutside || plan.terrainInside(x, z);
            if (voidOutside && !terrainInside && !barrierColumn) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    if (!chunk.getBlockState(pos).isAir()) {
                        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                        changed = true;
                    }
                }
                continue;
            }
            boolean blockDissolveColumn = voidOutside && !barrierColumn
                    && plan.config().voidBlockDissolveWidth > 0
                    && plan.measure(x, z).blocksToEdge() < plan.config().voidBlockDissolveWidth;
            if (blockDissolveColumn) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    if (!plan.terrainBlockInside(x, y, z) && !chunk.getBlockState(pos).isAir()) {
                        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                        changed = true;
                    }
                }
            }
            if (barrierColumn) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    if (!chunk.getBlockState(pos).is(Blocks.BARRIER)) {
                        chunk.setBlockState(pos, Blocks.BARRIER.defaultBlockState(), false);
                    }
                }
                changed = true;
            }
        }

        if (changed) Heightmap.primeHeightmaps(chunk, heightmaps);
        return chunk;
    }
}
