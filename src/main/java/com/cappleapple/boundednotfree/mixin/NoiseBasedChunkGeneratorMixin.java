package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.runtime.TerrainHandler;
import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @ModifyArg(method = "doCreateBiomes", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"), index = 0)
    private BiomeResolver boundednotfree$constrainNoiseBiomes(BiomeResolver original) {
        return LayoutRuntime.constrainedResolver((ChunkGenerator)(Object)this, original);
    }

    /**
     * Attach to the public future instead of javac's private worker lambda. C2ME redirects the executor and
     * rewrites parts of the chunk-status pipeline, but it still returns this contract. The continuation runs
     * after section locks are released and before downstream statuses observe the chunk.
     */
    @Inject(method = "fillFromNoise", at = @At("RETURN"), cancellable = true)
    private void boundednotfree$applyBoundaryTerrain(Blender blender, RandomState random,
                                                      StructureManager structures, ChunkAccess input,
                                                      CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        ChunkGenerator generator = (ChunkGenerator)(Object)this;
        cir.setReturnValue(cir.getReturnValue().thenApply(chunk -> TerrainHandler.apply(generator, chunk)));
    }
}
