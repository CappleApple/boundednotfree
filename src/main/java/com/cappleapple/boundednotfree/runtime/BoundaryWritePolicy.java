package com.cappleapple.boundednotfree.runtime;

/** Pure decision logic for writes attempted after the initial boundary terrain pass. */
final class BoundaryWritePolicy {
    private BoundaryWritePolicy() {}

    static boolean allows(boolean voidOutside, boolean inside, boolean barrierColumn,
                          boolean air, boolean barrier) {
        if (barrierColumn) return barrier;
        return !voidOutside || inside || air;
    }
}
