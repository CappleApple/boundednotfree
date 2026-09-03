package com.cappleapple.boundednotfree.mixin;

import com.cappleapple.boundednotfree.runtime.DimensionTravelBounds;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Optional;

@Mixin(NetherPortalBlock.class)
abstract class NetherPortalBlockMixin {
    @ModifyArgs(method = "getPortalDestination", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/NetherPortalBlock;getExitPortal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/border/WorldBorder;)Lnet/minecraft/world/level/portal/DimensionTransition;"))
    private void boundednotfree$clampScaledPortalTarget(Args args) {
        ServerLevel destination = args.get(0);
        Entity entity = args.get(1);
        BlockPos entryPos = args.get(2);
        BlockPos vanillaTarget = args.get(3);
        Direction.Axis axis = entity.level().getBlockState(entryPos)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        args.set(3, DimensionTravelBounds.clampPortalTarget(destination, vanillaTarget, axis));
    }

    @ModifyExpressionValue(method = "getExitPortal", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/portal/PortalForcer;findClosestPortalPosition(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/level/border/WorldBorder;)Ljava/util/Optional;"))
    private Optional<BlockPos> boundednotfree$filterExistingPortals(Optional<BlockPos> found,
                                                                    ServerLevel destination) {
        return found.filter(pos -> DimensionTravelBounds.portalPositionInside(destination, pos));
    }
}
