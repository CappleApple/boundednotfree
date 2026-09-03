package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.runtime.DimensionTravelBounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
abstract class EntityMixin {
    @ModifyVariable(method = "changeDimension", at = @At("HEAD"), argsOnly = true)
    private DimensionTransition boundednotfree$clampDimensionTransition(DimensionTransition transition) {
        return DimensionTravelBounds.clampTransition((Entity)(Object)this, transition);
    }

    @Redirect(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveTo(DDDFF)V"))
    private void boundednotfree$clampCrossDimensionTeleport(Entity movedEntity, double x, double y, double z,
                                                             float yRot, float xRot) {
        Entity source = (Entity)(Object)this;
        Vec3 clamped = DimensionTravelBounds.clampTeleport(source, (ServerLevel)movedEntity.level(), x, y, z);
        movedEntity.moveTo(clamped.x, clamped.y, clamped.z, yRot, xRot);
    }
}
