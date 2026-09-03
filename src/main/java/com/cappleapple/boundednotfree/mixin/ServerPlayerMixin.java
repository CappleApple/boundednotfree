package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.runtime.DimensionTravelBounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @ModifyVariable(method = "changeDimension", at = @At("HEAD"), argsOnly = true)
    private DimensionTransition boundednotfree$clampDimensionTransition(DimensionTransition transition) {
        return DimensionTravelBounds.clampTransition((ServerPlayer)(Object)this, transition);
    }
}
