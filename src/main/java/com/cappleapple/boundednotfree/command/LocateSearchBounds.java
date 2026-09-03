package com.cappleapple.boundednotfree.command;

final class LocateSearchBounds {
    private LocateSearchBounds() {}

    static int blockRadius(double blocksToEdge, int requestedRadius) {
        if (!Double.isFinite(blocksToEdge) || blocksToEdge <= 0 || requestedRadius <= 0) return 0;
        return Math.min(requestedRadius, (int)Math.min(Integer.MAX_VALUE, Math.floor(blocksToEdge)));
    }
}
