package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import com.cappleapple.boundednotfree.runtime.TerrainHandler;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    // The invocation lives in javac's instance lambda body, not in createBiomes itself.
    @ModifyArg(method = "lambda$createBiomes$3", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"), index = 0)
    private BiomeResolver boundednotfree$constrainBiomeSource(BiomeResolver original) {
        ChunkGenerator generator = (ChunkGenerator)(Object)this;
        return LayoutRuntime.constrainedResolver(generator, original);
    }

    /**
     * Make the final block-level boundary pass after every structure and placed feature has run, but while
     * the target is still a ProtoChunk. Running this from the later LevelChunk promotion constructor lets
     * live-world block-change integrations synchronously request neighboring chunks from a C2ME worker.
     */
    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void boundednotfree$finalizeDecoratedTerrain(WorldGenLevel level, ChunkAccess chunk,
                                                          StructureManager structures, CallbackInfo ci) {
        TerrainHandler.finalizeNewChunk((ChunkGenerator)(Object)this, chunk);
    }

    @Inject(method = "tryGenerateStructure", at = @At("HEAD"), cancellable = true)
    private void boundednotfree$filterStructure(StructureSet.StructureSelectionEntry entry,
                                                net.minecraft.world.level.StructureManager structureManager,
                                                net.minecraft.core.RegistryAccess registryAccess,
                                                net.minecraft.world.level.levelgen.RandomState random,
                                                net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                                long seed, net.minecraft.world.level.chunk.ChunkAccess chunk, ChunkPos chunkPos,
                                                net.minecraft.core.SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        Holder<Structure> structure = entry.structure();
        if (!LayoutRuntime.structureAllowed((ChunkGenerator)(Object)this, structure, chunkPos.x, chunkPos.z)) cir.setReturnValue(false);
    }

    @Inject(method = "tryGenerateStructure", at = @At("RETURN"))
    private void boundednotfree$countStructure(StructureSet.StructureSelectionEntry entry,
                                               net.minecraft.world.level.StructureManager structureManager,
                                               net.minecraft.core.RegistryAccess registryAccess,
                                               net.minecraft.world.level.levelgen.RandomState random,
                                               net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                               long seed, net.minecraft.world.level.chunk.ChunkAccess chunk, ChunkPos chunkPos,
                                               net.minecraft.core.SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) LayoutRuntime.recordStructure((ChunkGenerator)(Object)this, entry.structure());
    }
}
