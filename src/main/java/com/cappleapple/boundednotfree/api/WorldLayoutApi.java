package com.cappleapple.boundednotfree.api;

import com.cappleapple.boundednotfree.plan.DimensionPlan;
import com.cappleapple.boundednotfree.runtime.LayoutRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/** Read-only integration API for querying the active layout of a server dimension. */
public final class WorldLayoutApi {
    private WorldLayoutApi() {}

    public static Optional<View> get(ServerLevel level) {
        DimensionPlan plan = LayoutRuntime.plan(level.getChunkSource().getGenerator());
        return plan == null ? Optional.empty() : Optional.of(new View(plan));
    }

    public static final class View {
        private final DimensionPlan plan;
        private View(DimensionPlan plan) { this.plan = plan; }

        public ResourceLocation dimension() { return plan.dimension(); }
        public String boundaryType() { return plan.boundary().type(); }
        public long layoutSeed() { return plan.seed(); }
        public boolean contains(double worldX, double worldZ) { return plan.measure(worldX, worldZ).inside(); }
        public DistanceMetric measure(double worldX, double worldZ) { return plan.measure(worldX, worldZ); }
        public Optional<String> progressionZone(double worldX, double worldZ) {
            return Optional.ofNullable(plan.progressionZone(worldX, worldZ));
        }
    }
}
