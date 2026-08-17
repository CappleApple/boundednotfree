package com.cappleapple.boundednotfree.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Preserves optional loader/API state when Bounded Not Free replaces Minecraft's climate sampler. */
final class ClimateSamplerCompat {
    private static final String FABRIC_HOOKS = "net.fabricmc.fabric.impl.biome.MultiNoiseSamplerHooks";

    enum Result {
        NOT_PRESENT,
        COPIED,
        FAILED
    }

    private ClimateSamplerCompat() {}

    static Result copyFabricSeed(Object source, Object target) {
        Class<?> hooks;
        try {
            hooks = Class.forName(FABRIC_HOOKS, false, source.getClass().getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return Result.NOT_PRESENT;
        } catch (LinkageError exception) {
            return Result.FAILED;
        }

        try {
            copySeed(source, target, hooks);
            return Result.COPIED;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return Result.FAILED;
        }
    }

    static void copySeed(Object source, Object target, Class<?> hooks) throws ReflectiveOperationException {
        if (!hooks.isInstance(source) || !hooks.isInstance(target)) {
            throw new IllegalArgumentException("Climate sampler does not implement " + hooks.getName());
        }

        Method getSeed = hooks.getMethod("fabric_getSeed");
        Method setSeed = hooks.getMethod("fabric_setSeed", long.class);
        try {
            Object value = getSeed.invoke(source);
            if (!(value instanceof Number seed)) {
                throw new IllegalStateException("Fabric climate sampler seed was not numeric");
            }
            setSeed.invoke(target, seed.longValue());
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw exception;
        }
    }
}
