package com.cappleapple.boundednotfree.mixin.compat;

import com.cappleapple.boundednotfree.compat.GenesisPreviewWorker;
import com.cappleapple.boundednotfree.runtime.PreviewLayout;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Pseudo
@Mixin(targets = "net.alkeari.genesis.client.screen.GenesisScreen", remap = false)
abstract class GenesisScreenMixin {
    @Shadow @Final private WorldCreationContext context;
    @Shadow private long currentSeed;
    @Unique private RandomState boundednotfree$random;
    @Unique private PreviewLayout boundednotfree$layout;

    @Redirect(method = "startPreviewThread", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;sampler()Lnet/minecraft/world/level/biome/Climate$Sampler;"))
    private Climate.Sampler boundednotfree$capturePreviewState(RandomState random) {
        boundednotfree$random = random;
        boundednotfree$layout = null;
        return random.sampler();
    }

    @Redirect(method = "startPreviewThread", at = @At(value = "INVOKE",
            target = "Lnet/alkeari/genesis/client/preview/BiomePreviewThread;start()V"))
    private void boundednotfree$startWithLayout(@Coerce Thread worker) throws IOException {
        // Genesis has now initialized its provider bridges. Prepare once, before publishing
        // the session to any worker; seed changes construct a fresh RandomState.
        if (boundednotfree$layout == null) {
            var generator = context.selectedDimensions().get(LevelStem.OVERWORLD).orElseThrow().generator();
            boundednotfree$layout = PreviewLayout.load(Level.OVERWORLD.location(), currentSeed,
                    context.worldgenRegistries().compositeAccess(), generator, boundednotfree$random);
        }
        ((GenesisPreviewWorker)worker).boundednotfree$setLayout(boundednotfree$layout);
        worker.start();
    }

    @Inject(method = "stopPreviewThread", at = @At("RETURN"))
    private void boundednotfree$releasePreview(CallbackInfo ci) {
        boundednotfree$layout = null;
        boundednotfree$random = null;
    }
}