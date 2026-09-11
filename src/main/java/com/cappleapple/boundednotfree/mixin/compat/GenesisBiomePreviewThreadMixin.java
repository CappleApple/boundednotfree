package com.cappleapple.boundednotfree.mixin.compat;

import com.cappleapple.boundednotfree.compat.GenesisPreviewWorker;
import com.cappleapple.boundednotfree.runtime.PreviewLayout;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.alkeari.genesis.client.preview.BiomePreviewThread", remap = false)
abstract class GenesisBiomePreviewThreadMixin implements GenesisPreviewWorker {
    @Shadow @Final @Mutable private Climate.Sampler sampler;
    @Unique private PreviewLayout boundednotfree$layout;

    @Override
    public void boundednotfree$setLayout(PreviewLayout layout) {
        // Called before Thread.start(), which safely publishes the plan and sampler.
        boundednotfree$layout = layout;
        sampler = layout.sampler();
    }

    @Inject(method = "sampleBiome", at = @At("RETURN"), cancellable = true)
    private void boundednotfree$constrainPreview(int quartX, int quartZ, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (boundednotfree$layout == null || boundednotfree$layout.plan() == null || cir.getReturnValue() == null) return;
        // Genesis 1.1.1 samples the surface map at block Y=320 (quart Y=80).
        // Constrain the final result so Genesis's GeoGradient and TerraBlender fallbacks survive.
        cir.setReturnValue(boundednotfree$layout.plan().selectBiome(cir.getReturnValue(), quartX, 80, quartZ, sampler));
    }
}