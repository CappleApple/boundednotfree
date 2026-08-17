package com.cappleapple.boundednotfree.plan;

/** Pure native-scale triangular coordinate fold used by provider terrain sampling. */
final class ProviderCoordinateFold {
    private ProviderCoordinateFold() {}

    static int fold(long localCoordinate, int halfSpan) {
        int phase = (int)Math.floorMod(localCoordinate + halfSpan, halfSpan * 4L);
        return phase <= halfSpan * 2 ? phase - halfSpan : halfSpan * 3 - phase;
    }
}
