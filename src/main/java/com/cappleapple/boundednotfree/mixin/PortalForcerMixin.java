package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.runtime.DimensionTravelBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PortalForcer.class)
abstract class PortalForcerMixin {
    @Shadow @Final protected ServerLevel level;

    @Redirect(method = "createPortal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean boundednotfree$filterPortalCandidates(WorldBorder border, BlockPos pos) {
        return border.isWithinBounds(pos) && DimensionTravelBounds.portalPositionInside(level, pos);
    }

    @Redirect(method = "createPortal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/border/WorldBorder;clampToBounds(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos boundednotfree$clampFallbackPortal(WorldBorder border, BlockPos pos,
                                                        BlockPos requested, Direction.Axis axis) {
        return DimensionTravelBounds.clampPortalOrigin(level, border.clampToBounds(pos), axis);
    }

    @Inject(method = "createPortal", at = @At("HEAD"), cancellable = true)
    private void boundednotfree$rejectTooSmallBoundary(BlockPos pos, Direction.Axis axis,
                                                       CallbackInfoReturnable<Optional<?>> cir) {
        if (!DimensionTravelBounds.portalCanFit(level, axis)) cir.setReturnValue(Optional.empty());
    }

    @Inject(method = "canHostFrame", at = @At("HEAD"), cancellable = true)
    private void boundednotfree$keepPortalFrameInside(BlockPos originalPos, BlockPos.MutableBlockPos offsetPos,
                                                      Direction direction, int offsetScale,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (!DimensionTravelBounds.portalFrameInside(level, originalPos, direction, offsetScale)) {
            cir.setReturnValue(false);
        }
    }
}
