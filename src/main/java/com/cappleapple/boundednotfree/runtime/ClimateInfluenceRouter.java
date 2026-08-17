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
import java.util.concurrent.atomic.AtomicInteger;

/** Wraps the active provider's climate fields in-place so terrain density and biome resolution see the same influence. */
public final class ClimateInfluenceRouter {
    public record Result(NoiseRouter router, Map<ClimateChannel, Integer> replacements, String strategy) {}

    private ClimateInfluenceRouter() {}

    public static Result install(RandomState state, DimensionPlan plan, List<Climate.ParameterPoint> spawnTargets) {
        NoiseRouter activeRouter = state.router();
        Climate.Sampler activeSampler = state.sampler();
        Result result;
        if (!plan.terrainInfluenceReady()) {
            result = new Result(activeRouter, Map.of(), "NATIVE");
        } else {
            PreparedRouter prepared = prepare(activeRouter);
            Result graphResult = wrap(prepared.router(), plan);
            if (prepared.c2meCompiled() && terrainCoupled(graphResult.replacements())) {
                NoiseRouter compiled = compileWithC2me(graphResult.router());
                graphResult = new Result(compiled, graphResult.replacements(),
                        compiled == graphResult.router() ? "CLIMATE_GRAPH" : "CLIMATE_GRAPH+C2ME_DFC");
            }
            if (terrainCoupled(graphResult.replacements())) {
                result = graphResult;
            } else if (prepared.c2meCompiled()) {
                // Density-decoupled providers such as Tectonic need provider-coordinate sampling.
                // Recompile that sampled fallback graph through the active C2ME generation API so its
                // bulk-evaluation contract remains intact. C2ME 0.4 moved the API into gen.jvm and
                // compiles all router roots in one shared context; older releases use BytecodeGen.compile.
                plan.prepareProviderSamples();
                Result sampled = providerSampled(prepared.router(), plan);
                NoiseRouter compiled = compileWithC2me(sampled.router());
                if (!sampled.strategy().startsWith("PROVIDER_") || compiled == sampled.router()) {
                    result = new Result(activeRouter, Map.of(), "PRESERVED+C2ME_DFC");
                    BoundedNotFree.LOGGER.warn("C2ME density-function compilation hides this provider's terrain climate graph, "
                            + "and compatible provider-sample recompilation was unavailable. Preserving the compiled provider "
                            + "terrain without layout influence; disable C2ME's experimental useDensityFunctionCompiler option "
                            + "to allow the uncompiled compatibility path.");
                } else {
                    result = new Result(compiled, sampled.replacements(), sampled.strategy() + "+C2ME_DFC");
                }
            } else {
                plan.prepareProviderSamples();
                result = providerSampled(prepared.router(), plan);
            }
        }
        result = applyRimTerrainStyle(result, plan);
        RandomStateAccessor accessor = (RandomStateAccessor)(Object)state;
        NoiseRouter router = result.router();
        Climate.Sampler replacementSampler = new Climate.Sampler(router.temperature(), router.vegetation(), router.continents(),
                router.erosion(), router.depth(), router.ridges(), spawnTargets);
        ClimateSamplerCompat.Result samplerCompat = ClimateSamplerCompat.copyFabricSeed(activeSampler, replacementSampler);
        if (samplerCompat == ClimateSamplerCompat.Result.FAILED) {
            BoundedNotFree.LOGGER.warn("Fabric biome sampler hooks were present, but their world seed could not be copied. "
                    + "Preserving the provider router and sampler instead of installing an unseeded replacement.");
            result = new Result(activeRouter, Map.of(), "PRESERVED+FABRIC_SEED");
            router = activeRouter;
            replacementSampler = activeSampler;
        } else if (samplerCompat == ClimateSamplerCompat.Result.COPIED) {
            BoundedNotFree.LOGGER.info("Preserved the Fabric biome sampler world seed on the influenced climate sampler");
        }
        accessor.boundednotfree$setRouter(router);
        accessor.boundednotfree$setSampler(replacementSampler);
        plan.recordClimateRouter(result.replacements(), result.strategy());
        return result;
    }

    private static Result applyRimTerrainStyle(Result base, DimensionPlan plan) {
        if (!plan.rimCaveWallEnabled()) return base;
        NoiseRouter original = base.router();
        NoiseRouter styled = new NoiseRouter(
                original.barrierNoise(),
                original.fluidLevelFloodednessNoise(),
                original.fluidLevelSpreadNoise(),
                original.lavaNoise(),
                original.temperature(),
                original.vegetation(),
                original.continents(),
                original.erosion(),
                original.depth(),
                original.ridges(),
                new RimCaveWallDensity(original.initialDensityWithoutJaggedness(), plan),
                new RimCaveWallDensity(original.finalDensity(), plan),
                original.veinToggle(),
                original.veinRidged(),
                original.veinGap());
        String strategy = "NATIVE".equals(base.strategy())
                ? "CAVE_WALL" : base.strategy() + "+CAVE_WALL";
        BoundedNotFree.LOGGER.info("Installed provider-independent CAVE_WALL rim density profile");
        return new Result(styled, base.replacements(), strategy);
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
        Throwable modernFailure = null;
        try {
            return compileWithModernC2me(router);
        } catch (ClassNotFoundException ignored) {
            // C2ME releases before 0.4 use the legacy compiler below.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            modernFailure = exception;
        }
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
            if (modernFailure != null) {
                BoundedNotFree.LOGGER.warn("C2ME density graph recompilation was unavailable; preserving its active compiled graph",
                        modernFailure);
            }
            return router;
        } catch (ReflectiveOperationException | C2meCompilationException | LinkageError exception) {
            BoundedNotFree.LOGGER.warn("C2ME density graph recompilation was unavailable; preserving its active compiled graph",
                    modernFailure == null ? exception : modernFailure);
            return router;
        }
    }

    private static NoiseRouter compileWithModernC2me(NoiseRouter router) throws ReflectiveOperationException {
        Class<?> compiler = Class.forName("com.ishland.c2me.opts.dfc.common.gen.jvm.BytecodeGen");
        Object context = compiler.getMethod("initContext").invoke(null);
        java.lang.reflect.Method compile = context.getClass().getMethod("compileDelayed", String.class,
                DensityFunction.class);
        String[] names = {"barrier", "fluid_level_floodedness", "fluid_level_spread", "lava", "temperature",
                "vegetation", "continents", "erosion", "depth", "ridges", "initial_density_without_jaggedness",
                "final_density", "vein_toggle", "vein_ridged", "vein_gap"};
        DensityFunction[] original = routerFunctions(router);
        DensityFunction[] compiled = new DensityFunction[original.length];
        for (int index = 0; index < original.length; index++) {
            compiled[index] = (DensityFunction)compile.invoke(context, names[index], original[index]);
        }
        compiler.getMethod("finalizeCompilation", context.getClass()).invoke(null, context);
        return routerFrom(compiled);
    }

    private static DensityFunction[] routerFunctions(NoiseRouter router) {
        return new DensityFunction[]{router.barrierNoise(), router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(), router.lavaNoise(), router.temperature(), router.vegetation(),
                router.continents(), router.erosion(), router.depth(), router.ridges(),
                router.initialDensityWithoutJaggedness(), router.finalDensity(), router.veinToggle(),
                router.veinRidged(), router.veinGap()};
    }

    private static NoiseRouter routerFrom(DensityFunction[] functions) {
        return new NoiseRouter(functions[0], functions[1], functions[2], functions[3], functions[4], functions[5],
                functions[6], functions[7], functions[8], functions[9], functions[10], functions[11], functions[12],
                functions[13], functions[14]);
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
            if (function instanceof InfluenceFactor || function instanceof ClimateTargetFunction) return function;
            for (var entry : roots.entrySet()) {
                for (DensityFunction root : entry.getValue()) {
                    if (function == root || function.equals(root)) {
                        replacements.merge(entry.getKey(), 1, Integer::sum);
                        // Keep the graph visible to optimizers such as C2ME's density compiler.
                        // Only the shared 2D rim factor remains a custom leaf; all channel
                        // blending is represented by vanilla density arithmetic.
                        DensityFunction target = DensityFunctions.cache2d(
                                new ClimateTargetFunction(function, plan, entry.getKey()));
                        DensityFunction blended = DensityFunctions.lerp(factor, function, target);
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
        DensityFunction factor = DensityFunctions.cache2d(new InfluenceFactor(plan));
        if (plan.providerTerrainRootReady()) return tectonicParameters(original, plan, factor);

        DensityFunction.Visitor visitor = providerTerrainVisitor(plan);
        DensityFunction sampledInitial = original.initialDensityWithoutJaggedness().mapAll(visitor);
        DensityFunction sampledFinal = original.finalDensity().mapAll(visitor);

        // Keep aquifers, ore veins, and other subsurface router roots local. Climate roots are sampled
        // coherently so PREFER can still select the provider-native biome associated with the terrain.
        NoiseRouter router = new NoiseRouter(
                original.barrierNoise(),
                original.fluidLevelFloodednessNoise(),
                original.fluidLevelSpreadNoise(),
                original.lavaNoise(),
                providerBlend(original.temperature(), original.temperature().mapAll(visitor), factor),
                providerBlend(original.vegetation(), original.vegetation().mapAll(visitor), factor),
                providerBlend(original.continents(), original.continents().mapAll(visitor), factor),
                providerBlend(original.erosion(), original.erosion().mapAll(visitor), factor),
                providerBlend(original.depth(), original.depth().mapAll(visitor), factor),
                providerBlend(original.ridges(), original.ridges().mapAll(visitor), factor),
                providerBlend(original.initialDensityWithoutJaggedness(), sampledInitial, factor),
                providerBlend(original.finalDensity(), sampledFinal, factor),
                original.veinToggle(),
                original.veinRidged(),
                original.veinGap());
        EnumMap<ClimateChannel, Integer> replacements = new EnumMap<>(ClimateChannel.class);
        for (ClimateChannel channel : ClimateChannel.values()) replacements.put(channel, 1);
        return new Result(router, Map.copyOf(replacements), "PROVIDER_SAMPLE");
    }

    private static Result tectonicParameters(NoiseRouter original, DimensionPlan plan, DensityFunction factor) {
        AtomicInteger terrainReplacements = new AtomicInteger();
        DensityFunction.Visitor terrainVisitor = tectonicParameterVisitor(plan, factor, terrainReplacements);
        DensityFunction influencedInitial = original.initialDensityWithoutJaggedness().mapAll(terrainVisitor);
        DensityFunction influencedFinal = original.finalDensity().mapAll(terrainVisitor);
        if (terrainReplacements.get() == 0) {
            BoundedNotFree.LOGGER.warn("Tectonic's base terrain graph was present, but its continentalness, erosion, and "
                    + "ridge parameter noises were not discoverable. Preserving provider terrain instead of blending "
                    + "unrelated final-density fields.");
            return new Result(original, Map.of(), "UNAVAILABLE");
        }

        // Tectonic builds terrain and caves as one nonlinear graph. Influence its three primary
        // horizontal terrain parameters inside that graph instead of interpolating two complete
        // final-density fields, which can introduce extra zero crossings and enormous overhangs.
        DensityFunction.Visitor climateVisitor = tectonicParameterVisitor(plan, factor, new AtomicInteger());
        NoiseRouter router = new NoiseRouter(
                original.barrierNoise(),
                original.fluidLevelFloodednessNoise(),
                original.fluidLevelSpreadNoise(),
                original.lavaNoise(),
                original.temperature(),
                original.vegetation(),
                original.continents().mapAll(climateVisitor),
                original.erosion().mapAll(climateVisitor),
                original.depth().mapAll(climateVisitor),
                original.ridges().mapAll(climateVisitor),
                influencedInitial,
                influencedFinal,
                original.veinToggle(),
                original.veinRidged(),
                original.veinGap());
        EnumMap<ClimateChannel, Integer> replacements = new EnumMap<>(ClimateChannel.class);
        for (ClimateChannel channel : ClimateChannel.values()) replacements.put(channel, 1);
        BoundedNotFree.LOGGER.info("Installed provider-native Tectonic parameter influence at {} terrain-noise leaves",
                terrainReplacements.get());
        return new Result(router, Map.copyOf(replacements), "PROVIDER_PARAMETERS");
    }

    private static DensityFunction.Visitor providerTerrainVisitor(DimensionPlan plan) {
        return function -> isProviderTerrainNoise(function) ? new ProviderCoordinateSample(function, plan) : function;
    }

    private static DensityFunction.Visitor tectonicParameterVisitor(DimensionPlan plan, DensityFunction factor,
                                                                    AtomicInteger replacements) {
        return function -> {
            if (!ProviderNoiseClassifier.isTectonicTerrainParameter(noiseKey(function))) return function;
            replacements.incrementAndGet();
            DensityFunction sampled = new ProviderCoordinateSample(function, plan);
            return DensityFunctions.cache2d(DensityFunctions.lerp(factor, function, sampled));
        };
    }

    private static DensityFunction providerBlend(DensityFunction local, DensityFunction sampled,
                                                  DensityFunction factor) {
        return DensityFunctions.lerp(factor, local, sampled);
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

    private record ClimateTargetFunction(DensityFunction original, DimensionPlan plan,
                                         ClimateChannel channel) implements DensityFunction.SimpleFunction {
        @Override
        public double compute(FunctionContext context) {
            double value = original.compute(context);
            return plan.influencedClimateValue(channel, value, context.blockX(), context.blockZ());
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new ClimateTargetFunction(original.mapAll(visitor), plan, channel));
        }

        @Override public double minValue() { return original.minValue(); }
        @Override public double maxValue() { return original.maxValue(); }
        @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return original.codec();
        }
    }

    private record ProviderCoordinateSample(DensityFunction input, DimensionPlan plan) implements DensityFunction {
        @Override
        public double compute(FunctionContext context) {
            if (context instanceof RedirectedContext) return input.compute(context);
            DimensionPlan.ProviderCoordinates sample = plan.providerCoordinates(context.blockX(), context.blockZ());
            return input.compute(new RedirectedContext(sample.x(), context.blockY(), sample.z(), context));
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

    private record RimCaveWallDensity(DensityFunction input, DimensionPlan plan) implements DensityFunction {
        @Override
        public double compute(FunctionContext context) {
            return plan.shapeRimDensity(input.compute(context), context.blockX(), context.blockY(), context.blockZ());
        }

        @Override
        public void fillArray(double[] values, ContextProvider contexts) {
            input.fillArray(values, contexts);
            for (int index = 0; index < values.length; index++) {
                FunctionContext context = contexts.forIndex(index);
                values[index] = plan.shapeRimDensity(values[index], context.blockX(), context.blockY(), context.blockZ());
            }
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new RimCaveWallDensity(input.mapAll(visitor), plan));
        }

        @Override public double minValue() { return Math.min(input.minValue(), -1); }
        @Override public double maxValue() { return Math.max(input.maxValue(), 1); }
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
            DimensionPlan.ProviderCoordinates sample = plan.providerCoordinates(context.blockX(), context.blockZ());
            return new RedirectedContext(sample.x(), context.blockY(), sample.z(), context);
        }

        @Override
        public void fillAllDirectly(double[] values, DensityFunction function) {
            for (int index = 0; index < values.length; index++) values[index] = function.compute(forIndex(index));
        }
    }
}
