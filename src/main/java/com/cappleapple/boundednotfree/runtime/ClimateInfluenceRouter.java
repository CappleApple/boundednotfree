package com.cappleapple.boundednotfree.runtime;

import com.cappleapple.boundednotfree.BoundedNotFree;
import com.cappleapple.boundednotfree.mixin.RandomStateAccessor;
import com.cappleapple.boundednotfree.plan.ClimateChannel;
import com.cappleapple.boundednotfree.plan.DimensionPlan;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Wraps the active provider's climate fields in-place so terrain density and biome resolution see the same influence. */
public final class ClimateInfluenceRouter {
    public record Result(NoiseRouter router, Map<ClimateChannel, Integer> replacements, String strategy) {}

    private ClimateInfluenceRouter() {}

    public static Result install(RandomState state, DimensionPlan plan, List<Climate.ParameterPoint> spawnTargets) {
        NoiseRouter activeRouter = state.router();
        PreparedRouter prepared = prepare(activeRouter);
        Result graphResult = wrap(prepared.router(), plan);
        if (prepared.c2meCompiled() && terrainCoupled(graphResult.replacements())) {
            NoiseRouter compiled = compileWithC2me(graphResult.router());
            graphResult = new Result(compiled, graphResult.replacements(),
                    compiled == graphResult.router() ? "CLIMATE_GRAPH" : "CLIMATE_GRAPH+C2ME_DFC");
        }
        Result result;
        if (terrainCoupled(graphResult.replacements())) {
            result = graphResult;
        } else if (prepared.c2meCompiled()) {
            // C2ME's optional density-function compiler makes third-party provider graphs opaque.
            // Sampling its recovered fallback graph while DFC's NoiseChunk mixins remain active can
            // change bulk-evaluation semantics and create missing terrain. Keep the provider's
            // compiled terrain intact and apply the rim to its exposed climate roots only. C2ME's
            // normal threaded chunk scheduling is independent of this experimental DFC option.
            NoiseRouter compiled = compileWithC2me(graphResult.router());
            if (compiled == graphResult.router()) {
                result = new Result(activeRouter, Map.of(), "PRESERVED+C2ME_DFC");
                BoundedNotFree.LOGGER.warn("C2ME density-function compilation hides this provider's terrain climate graph, "
                        + "and compatible climate-only recompilation was unavailable. Preserving the compiled provider "
                        + "terrain without rim influence; disable C2ME's experimental useDensityFunctionCompiler option "
                        + "to enable provider-native rim terrain.");
            } else {
                result = new Result(compiled, graphResult.replacements(), "CLIMATE_ONLY+C2ME_DFC");
                BoundedNotFree.LOGGER.warn("C2ME density-function compilation hides this provider's terrain climate graph; "
                        + "using biome-only rim influence to preserve terrain integrity. Disable C2ME's experimental "
                        + "useDensityFunctionCompiler option to enable provider-native rim terrain.");
            }
        } else {
            result = providerSampled(prepared.router(), plan);
        }
        RandomStateAccessor accessor = (RandomStateAccessor)(Object)state;
        accessor.boundednotfree$setRouter(result.router());
        NoiseRouter router = result.router();
        accessor.boundednotfree$setSampler(new Climate.Sampler(router.temperature(), router.vegetation(), router.continents(),
                router.erosion(), router.depth(), router.ridges(), spawnTargets));
        plan.recordClimateRouter(result.replacements(), result.strategy());
        return result;
    }

    private record PreparedRouter(NoiseRouter router, boolean c2meCompiled) {}

    private static PreparedRouter prepare(NoiseRouter router) {
        boolean compiled = isC2meCompiled(router.finalDensity());
        if (!compiled) return new PreparedRouter(router, false);
        NoiseRouter restored = mapRouter(router, ClimateInfluenceRouter::unwrapC2me);
        BoundedNotFree.LOGGER.info("Recovered C2ME density compiler fallback graph before installing rim influence");
        return new PreparedRouter(restored, true);
    }

    private static boolean isC2meCompiled(DensityFunction function) {
        return function.getClass().getName().startsWith("com.ishland.c2me.opts.dfc.common.gen.");
    }

    private static DensityFunction unwrapC2me(DensityFunction function) {
        if (!isC2meCompiled(function)) return function;
        for (Class<?> type = function.getClass(); type != null; type = type.getSuperclass()) {
            try {
                java.lang.reflect.Method method = type.getDeclaredMethod("getFallback");
                if (!method.trySetAccessible()) break;
                Object fallback = method.invoke(function);
                if (fallback instanceof DensityFunction density) return density;
                break;
            } catch (NoSuchMethodException ignored) {
                // The protected fallback accessor is declared on a superclass.
            } catch (ReflectiveOperationException | RuntimeException exception) {
                BoundedNotFree.LOGGER.warn("Could not recover C2ME density compiler fallback from {}", function.getClass().getName(), exception);
                break;
            }
        }
        return function;
    }

    private static NoiseRouter compileWithC2me(NoiseRouter router) {
        try {
            Class<?> compiler = Class.forName("com.ishland.c2me.opts.dfc.common.gen.BytecodeGen");
            Class<?> cacheType = Class.forName("it.unimi.dsi.fastutil.objects.Reference2ReferenceMap");
            Object cache = Class.forName("it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap")
                    .getConstructor().newInstance();
            java.lang.reflect.Method compile = compiler.getMethod("compile", DensityFunction.class, cacheType);
            return mapRouter(router, function -> {
                try { return (DensityFunction)compile.invoke(null, function, cache); }
                catch (ReflectiveOperationException exception) { throw new C2meCompilationException(exception); }
            });
        } catch (ClassNotFoundException exception) {
            return router;
        } catch (ReflectiveOperationException | C2meCompilationException | LinkageError exception) {
            BoundedNotFree.LOGGER.warn("C2ME density graph recompilation was unavailable; using the correct uncompiled graph", exception);
            return router;
        }
    }

    private static NoiseRouter mapRouter(NoiseRouter router, java.util.function.UnaryOperator<DensityFunction> mapper) {
        return new NoiseRouter(
                mapper.apply(router.barrierNoise()),
                mapper.apply(router.fluidLevelFloodednessNoise()),
                mapper.apply(router.fluidLevelSpreadNoise()),
                mapper.apply(router.lavaNoise()),
                mapper.apply(router.temperature()),
                mapper.apply(router.vegetation()),
                mapper.apply(router.continents()),
                mapper.apply(router.erosion()),
                mapper.apply(router.depth()),
                mapper.apply(router.ridges()),
                mapper.apply(router.initialDensityWithoutJaggedness()),
                mapper.apply(router.finalDensity()),
                mapper.apply(router.veinToggle()),
                mapper.apply(router.veinRidged()),
                mapper.apply(router.veinGap()));
    }

    private static final class C2meCompilationException extends RuntimeException {
        private C2meCompilationException(Throwable cause) { super(cause); }
    }

    static Result wrap(NoiseRouter original, DimensionPlan plan) {
        EnumMap<ClimateChannel, List<DensityFunction>> roots = new EnumMap<>(ClimateChannel.class);
        add(roots, ClimateChannel.CONTINENTALNESS, original.continents());
        add(roots, ClimateChannel.EROSION, original.erosion());
        add(roots, ClimateChannel.WEIRDNESS, original.ridges());
        EnumMap<ClimateChannel, Integer> replacements = new EnumMap<>(ClimateChannel.class);
        DensityFunction factor = DensityFunctions.cache2d(new InfluenceFactor(plan));

        DensityFunction.Visitor visitor = function -> {
            if (function instanceof InfluenceFactor) return function;
            for (var entry : roots.entrySet()) {
                for (DensityFunction root : entry.getValue()) {
                    if (function == root || function.equals(root)) {
                        replacements.merge(entry.getKey(), 1, Integer::sum);
                        // Keep the graph visible to optimizers such as C2ME's density compiler.
                        // Only the shared 2D rim factor remains a custom leaf; all channel
                        // blending is represented by vanilla density arithmetic.
                        DensityFunction blended = DensityFunctions.lerp(factor, function,
                                DensityFunctions.constant(plan.climateTargetValue(entry.getKey())));
                        return DensityFunctions.cache2d(blended);
                    }
                }
            }
            return function;
        };
        return new Result(original.mapAll(visitor), Map.copyOf(replacements), "CLIMATE_GRAPH");
    }

    private static boolean terrainCoupled(Map<ClimateChannel, Integer> replacements) {
        return replacements.getOrDefault(ClimateChannel.CONTINENTALNESS, 0) > 1
                && replacements.getOrDefault(ClimateChannel.EROSION, 0) > 1
                && replacements.getOrDefault(ClimateChannel.WEIRDNESS, 0) > 1;
    }

    private static Result providerSampled(NoiseRouter original, DimensionPlan plan) {
        if (!plan.providerSampleReady()) return new Result(original, Map.of(), "UNAVAILABLE");
        DensityFunction.Visitor visitor = function -> isProviderTerrainNoise(function)
                ? new ProviderCoordinateSample(function, plan)
                : function;
        // Build independent local and provider-native branches before NoiseChunk installs interpolation and caches.
        // Each branch consequently owns coherent interpolation state; only their finished density values are blended.
        NoiseRouter router = new NoiseRouter(
                providerBlend(original.barrierNoise(), plan, visitor, false),
                providerBlend(original.fluidLevelFloodednessNoise(), plan, visitor, false),
                providerBlend(original.fluidLevelSpreadNoise(), plan, visitor, false),
                providerBlend(original.lavaNoise(), plan, visitor, false),
                providerBlend(original.temperature(), plan, visitor, false),
                providerBlend(original.vegetation(), plan, visitor, false),
                providerBlend(original.continents(), plan, visitor, false),
                providerBlend(original.erosion(), plan, visitor, false),
                providerBlend(original.depth(), plan, visitor, false),
                providerBlend(original.ridges(), plan, visitor, false),
                providerBlend(original.initialDensityWithoutJaggedness(), plan, visitor, true),
                providerBlend(original.finalDensity(), plan, visitor, true),
                providerBlend(original.veinToggle(), plan, visitor, false),
                providerBlend(original.veinRidged(), plan, visitor, false),
                providerBlend(original.veinGap(), plan, visitor, false));
        EnumMap<ClimateChannel, Integer> replacements = new EnumMap<>(ClimateChannel.class);
        for (ClimateChannel channel : ClimateChannel.values()) replacements.put(channel, 1);
        return new Result(router, Map.copyOf(replacements), "PROVIDER_SAMPLE");
    }

    private static DensityFunction providerBlend(DensityFunction local, DimensionPlan plan,
                                                  DensityFunction.Visitor visitor, boolean terrainDensity) {
        return new ProviderBlend(local, local.mapAll(visitor), plan, terrainDensity);
    }

    private static boolean isHorizontalNoise(DensityFunction function) {
        String name = function.getClass().getName();
        return name.equals("net.minecraft.world.level.levelgen.DensityFunctions$Noise")
                || name.equals("net.minecraft.world.level.levelgen.DensityFunctions$Shift")
                || name.equals("net.minecraft.world.level.levelgen.DensityFunctions$ShiftA")
                || name.equals("net.minecraft.world.level.levelgen.DensityFunctions$ShiftB")
                || name.equals("net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
                || name.equals("net.minecraft.world.level.levelgen.DensityFunctions$OldBlendedNoise")
                || name.equals("net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction")
                || name.equals("dev.worldgen.tectonic.worldgen.densityfunction.ConfigNoise");
    }

    private static boolean isProviderTerrainNoise(DensityFunction function) {
        if (!isHorizontalNoise(function)) return false;
        String noise = noiseKey(function);
        return noise == null || !ProviderNoiseClassifier.isSubsurface(noise);
    }

    private static String noiseKey(DensityFunction function) {
        for (String accessor : List.of("noise", "offsetNoise")) {
            try {
                java.lang.reflect.Method method = function.getClass().getDeclaredMethod(accessor);
                if (!method.trySetAccessible()) continue;
                Object value = method.invoke(function);
                if (value instanceof DensityFunction.NoiseHolder holder) {
                    return holder.noiseData().unwrapKey()
                            .map(key -> key.location().toString())
                            .orElse(null);
                }
            } catch (NoSuchMethodException ignored) {
                // Different native noise records expose one of the two accessors.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void add(EnumMap<ClimateChannel, List<DensityFunction>> roots, ClimateChannel channel, DensityFunction function) {
        ArrayList<DensityFunction> candidates = new ArrayList<>();
        candidates.add(function);
        DensityFunction unwrapped = function;
        while (unwrapped instanceof DensityFunctions.HolderHolder holder) {
            unwrapped = holder.function().value();
            candidates.add(unwrapped);
        }
        roots.put(channel, List.copyOf(candidates));
    }

    private record InfluenceFactor(DimensionPlan plan) implements DensityFunction.SimpleFunction {
        @Override
        public double compute(FunctionContext context) {
            return plan.effectiveClimateInfluenceFactor(context.blockX(), context.blockZ());
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(this);
        }

        @Override public double minValue() { return 0; }
        @Override public double maxValue() { return 1; }
        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            // Runtime-only leaf: the router is never serialized after installation.
            return DensityFunctions.constant(0).codec();
        }
    }

    private record ProviderBlend(DensityFunction local, DensityFunction sampled, DimensionPlan plan,
                                 boolean terrainDensity) implements DensityFunction {
        @Override
        public double compute(FunctionContext context) {
            double factor = plan.effectiveClimateInfluenceFactor(context.blockX(), context.blockZ());
            if (factor <= 0) return local.compute(context);
            double desired = sampled.compute(context);
            double original = local.compute(context);
            if (terrainDensity) return ProviderTerrainBlend.combine(original, desired, factor);
            if (factor >= 1) return desired;
            return original + (desired - original) * factor;
        }

        @Override
        public void fillArray(double[] values, ContextProvider contexts) {
            double[] factors = new double[values.length];
            boolean needOriginal = false;
            boolean needSample = false;
            for (int index = 0; index < values.length; index++) {
                FunctionContext context = contexts.forIndex(index);
                double factor = plan.effectiveClimateInfluenceFactor(context.blockX(), context.blockZ());
                factors[index] = factor;
                needOriginal |= factor < 1;
                needSample |= factor > 0;
            }
            if (needOriginal) local.fillArray(values, contexts);
            if (!needSample) return;
            double[] desired = new double[values.length];
            sampled.fillArray(desired, contexts);
            for (int index = 0; index < values.length; index++) {
                double factor = factors[index];
                if (terrainDensity && factor > 0) {
                    values[index] = ProviderTerrainBlend.combine(values[index], desired[index], factor);
                } else if (factor >= 1) values[index] = desired[index];
                else if (factor > 0) values[index] += (desired[index] - values[index]) * factor;
            }
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new ProviderBlend(local.mapAll(visitor), sampled.mapAll(visitor), plan,
                    terrainDensity));
        }

        @Override public double minValue() { return Math.min(local.minValue(), sampled.minValue()); }
        @Override public double maxValue() { return Math.max(local.maxValue(), sampled.maxValue()); }
        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() { return local.codec(); }
    }

    private record ProviderCoordinateSample(DensityFunction input, DimensionPlan plan) implements DensityFunction {
        @Override
        public double compute(FunctionContext context) {
            if (context instanceof RedirectedContext) return input.compute(context);
            return input.compute(new RedirectedContext(plan.providerSampleX(context.blockX()), context.blockY(),
                    plan.providerSampleZ(context.blockZ()), context));
        }

        @Override
        public void fillArray(double[] values, ContextProvider contexts) {
            input.fillArray(values, new RoutedProvider(contexts, plan));
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new ProviderCoordinateSample(input.mapAll(visitor), plan));
        }

        @Override public double minValue() { return input.minValue(); }
        @Override public double maxValue() { return input.maxValue(); }
        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() { return input.codec(); }
    }

    private record RedirectedContext(int blockX, int blockY, int blockZ, DensityFunction.FunctionContext original)
            implements DensityFunction.FunctionContext {
        @Override public net.minecraft.world.level.levelgen.blending.Blender getBlender() { return original.getBlender(); }
    }

    private record RoutedProvider(DensityFunction.ContextProvider original, DimensionPlan plan)
            implements DensityFunction.ContextProvider {
        @Override
        public DensityFunction.FunctionContext forIndex(int arrayIndex) {
            DensityFunction.FunctionContext context = original.forIndex(arrayIndex);
            return new RedirectedContext(plan.providerSampleX(context.blockX()), context.blockY(),
                    plan.providerSampleZ(context.blockZ()), context);
        }

        @Override
        public void fillAllDirectly(double[] values, DensityFunction function) {
            for (int index = 0; index < values.length; index++) values[index] = function.compute(forIndex(index));
        }
    }
}
