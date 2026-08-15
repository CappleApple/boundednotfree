package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.runtime.TerrainHandler;
import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @ModifyArg(method = "doCreateBiomes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"), index = 0)
    private BiomeResolver boundednotfree$constrainNoiseBiomes(BiomeResolver original) {
        return LayoutRuntime.constrainedResolver((ChunkGenerator)(Object)this, original);
    }

    /**
     * Apply block-level boundary features after vanilla has released every LevelChunkSection lock, but before
     * its original fillFromNoise future completes. This keeps C2ME's future topology unchanged while
     * ensuring downstream generation never observes a half-cleared chunk.
     */
    @Inject(method = "lambda$fillFromNoise$11", at = @At("RETURN"))
    private void boundednotfree$applyBoundaryTerrain(ChunkAccess input, int cellCountY,
                                                 NoiseSettings noiseSettings, int minY,
                                                 Blender blender, StructureManager structures,
                                                 RandomState random, int minCellY,
                                                 CallbackInfoReturnable<ChunkAccess> cir) {
        TerrainHandler.apply((ChunkGenerator)(Object)this, cir.getReturnValue());
    }
}
