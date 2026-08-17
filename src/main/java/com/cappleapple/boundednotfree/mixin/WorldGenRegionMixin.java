package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.plan.DimensionPlan;
import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import com.cappleapple.boundednotfree.runtime.TerrainHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps structure and placed-feature decoration from writing through a finalized boundary. */
@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionMixin {
    @Shadow @Final private ServerLevel level;

    @Unique private boolean boundednotfree$planResolved;
    @Unique private DimensionPlan boundednotfree$plan;

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void boundednotfree$guardBoundaryWrite(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!boundednotfree$planResolved) {
            boundednotfree$plan = LayoutRuntime.plan(level.getChunkSource().getGenerator());
            boundednotfree$planResolved = true;
        }
        if (!TerrainHandler.allowsWorldgenWrite(boundednotfree$plan, pos, state)) cir.setReturnValue(false);
    }
}
