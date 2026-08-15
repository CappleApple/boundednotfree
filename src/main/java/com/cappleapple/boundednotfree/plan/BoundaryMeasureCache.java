package com.cappleapple.boundednotfree.plan;

import com.cappleapple.boundednotfree.api.BoundaryShape;
import com.cappleapple.boundednotfree.api.DistanceMetric;

/**
 * Small per-thread cache for the two-dimensional boundary measurements reused by
 * biome sampling and density functions at every vertical coordinate.
 */
final class BoundaryMeasureCache {
    private static final int SIZE = 4096;
    private static final int MASK = SIZE - 1;

    private final BoundaryShape boundary;
    private final double centerX;
    private final double centerZ;
    private final ThreadLocal<Entries> entries = ThreadLocal.withInitial(Entries::new);

    BoundaryMeasureCache(BoundaryShape boundary, double centerX, double centerZ) {
        this.boundary = boundary;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    DistanceMetric measure(double worldX, double worldZ) {
        int x = (int)worldX;
        int z = (int)worldZ;
        if ((double)x != worldX || (double)z != worldZ) {
            return boundary.measure(worldX - centerX, worldZ - centerZ);
        }

        long key = (long)x << 32 ^ (z & 0xffffffffL);
        int index = mix(key) & MASK;
        Entries local = entries.get();
        if (local.valid[index] && local.keys[index] == key) return local.values[index];

        DistanceMetric value = boundary.measure(worldX - centerX, worldZ - centerZ);
        local.keys[index] = key;
        local.values[index] = value;
        local.valid[index] = true;
        return value;
    }

    private static int mix(long key) {
        key ^= key >>> 33;
        key *= 0xff51afd7ed558ccdl;
        key ^= key >>> 33;
        return (int)key;
    }

    private static final class Entries {
        private final long[] keys = new long[SIZE];
        private final DistanceMetric[] values = new DistanceMetric[SIZE];
        private final boolean[] valid = new boolean[SIZE];
    }
}
