package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.plan.DimensionPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/** Applies active destination-dimension boundaries to Overworld/Nether travel. */
public final class DimensionTravelBounds {
    private static final double TELEPORT_INSET = 1.0;
    private static final double PORTAL_TARGET_INSET = 20.0;

    private DimensionTravelBounds() {}

    public static DimensionTransition clampTransition(Entity entity, DimensionTransition transition) {
        if (!(entity.level() instanceof ServerLevel source)
                || !isOverworldNetherPair(source, transition.newLevel())) return transition;
        Vec3 clamped = clamp(transition.newLevel(), transition.pos(), TELEPORT_INSET);
        if (clamped.equals(transition.pos())) return transition;
        return new DimensionTransition(transition.newLevel(), clamped, transition.speed(), transition.yRot(),
                transition.xRot(), transition.missingRespawnBlock(), transition.postDimensionTransition());
    }

    public static Vec3 clampTeleport(Entity entity, ServerLevel destination, double x, double y, double z) {
        if (!(entity.level() instanceof ServerLevel source) || !isOverworldNetherPair(source, destination)) {
            return new Vec3(x, y, z);
        }
        return clamp(destination, new Vec3(x, y, z), TELEPORT_INSET);
    }

    public static BlockPos clampPortalTarget(ServerLevel destination, BlockPos target, Direction.Axis axis) {
        DimensionPlan plan = plan(destination);
        if (plan == null) return target;
        BoundaryClamp.Position clamped = BoundaryClamp.clamp(plan.boundary(), plan.config().centerX,
                plan.config().centerZ, target.getX(), target.getZ(), PORTAL_TARGET_INSET);
        BlockPos safeTarget = BlockPos.containing(clamped.x(), target.getY(), clamped.z());
        Direction portalDirection = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        BlockPos safeOrigin = clampPortalOrigin(destination, safeTarget.relative(portalDirection.getOpposite()), axis);
        return safeOrigin.relative(portalDirection);
    }

    public static BlockPos clampPortalOrigin(ServerLevel level, BlockPos preferred, Direction.Axis axis) {
        DimensionPlan plan = plan(level);
        if (plan == null) return preferred;
        BoundaryClamp.Position clamped = BoundaryClamp.clamp(plan.boundary(), plan.config().centerX,
                plan.config().centerZ, preferred.getX(), preferred.getZ(), 4.0);
        BlockPos start = BlockPos.containing(clamped.x(), preferred.getY(), clamped.z());
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        for (int step = 0; step <= 64; step++) {
            double progress = step / 64.0;
            int x = (int)Math.floor(start.getX() + (plan.config().centerX - start.getX()) * progress);
            int z = (int)Math.floor(start.getZ() + (plan.config().centerZ - start.getZ()) * progress);
            BlockPos candidate = new BlockPos(x, preferred.getY(), z);
            if (portalAreaInside(plan, candidate, direction)) return candidate;
        }
        BlockPos centered = findPortalOriginNearCenter(plan, preferred.getY(), direction);
        if (centered != null) return centered;
        return start;
    }

    public static boolean portalPositionInside(ServerLevel level, BlockPos pos) {
        DimensionPlan plan = plan(level);
        if (plan == null) return true;
        Direction.Axis axis = level.getBlockState(pos).getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        for (int offset = -2; offset <= 2; offset++) {
            BlockPos sample = pos.relative(direction, offset);
            if (!plan.measure(sample.getX(), sample.getZ()).inside()) return false;
        }
        return true;
    }

    public static boolean portalFrameInside(ServerLevel level, BlockPos origin,
                                            Direction direction, int perpendicularOffset) {
        DimensionPlan plan = plan(level);
        return plan == null || portalFrameInside(plan, origin, direction, perpendicularOffset);
    }

    public static boolean portalCanFit(ServerLevel level, Direction.Axis axis) {
        DimensionPlan plan = plan(level);
        if (plan == null) return true;
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        return findPortalOriginNearCenter(plan, 0, direction) != null;
    }

    private static BlockPos findPortalOriginNearCenter(DimensionPlan plan, int y, Direction direction) {
        int centerX = (int)Math.floor(plan.config().centerX);
        int centerZ = (int)Math.floor(plan.config().centerZ);
        for (int xOffset = -4; xOffset <= 4; xOffset++) {
            for (int zOffset = -4; zOffset <= 4; zOffset++) {
                BlockPos candidate = new BlockPos(centerX + xOffset, y, centerZ + zOffset);
                if (portalAreaInside(plan, candidate, direction)) return candidate;
            }
        }
        return null;
    }

    private static Vec3 clamp(ServerLevel destination, Vec3 target, double inset) {
        DimensionPlan plan = plan(destination);
        if (plan == null) return target;
        BoundaryClamp.Position clamped = BoundaryClamp.clamp(plan.boundary(), plan.config().centerX,
                plan.config().centerZ, target.x, target.z, inset);
        return new Vec3(clamped.x(), target.y, clamped.z());
    }

    private static boolean isOverworldNetherPair(ServerLevel source, ServerLevel destination) {
        return source.dimension() == Level.OVERWORLD && destination.dimension() == Level.NETHER
                || source.dimension() == Level.NETHER && destination.dimension() == Level.OVERWORLD;
    }

    private static DimensionPlan plan(ServerLevel level) {
        return LayoutRuntime.plan(level.getChunkSource().getGenerator());
    }

    private static boolean portalAreaInside(DimensionPlan plan, BlockPos origin, Direction direction) {
        for (int offset = -1; offset <= 1; offset++) {
            if (!portalFrameInside(plan, origin, direction, offset)) return false;
        }
        return true;
    }

    private static boolean portalFrameInside(DimensionPlan plan, BlockPos origin,
                                             Direction direction, int perpendicularOffset) {
        Direction perpendicular = direction.getClockWise();
        for (int along = -1; along < 3; along++) {
            int x = origin.getX() + direction.getStepX() * along
                    + perpendicular.getStepX() * perpendicularOffset;
            int z = origin.getZ() + direction.getStepZ() * along
                    + perpendicular.getStepZ() * perpendicularOffset;
            if (!plan.measure(x, z).inside()) return false;
        }
        return true;
    }
}
