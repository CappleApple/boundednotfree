package com.cappleapple.boundednotfree.runtime;

/** Selects the inside column of each block-wide boundary edge. */
final class BoundaryBarrier {
    @FunctionalInterface
    interface Membership {
        boolean inside(int x, int z);
    }

    private BoundaryBarrier() {}

    static boolean isBarrierColumn(int x, int z, boolean inside, Membership membership) {
        return inside && (!membership.inside(x + 1, z)
                || !membership.inside(x - 1, z)
                || !membership.inside(x, z + 1)
                || !membership.inside(x, z - 1));
    }
}
