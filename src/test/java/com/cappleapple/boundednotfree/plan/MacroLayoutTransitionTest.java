package com.cappleapple.boundednotfree.plan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MacroLayoutTransitionTest {
    @Test
    void zeroWidthKeepsLegacyHardTransitions() {
        assertEquals(1, MacroLayoutPlan.smoothTransitionFactor(0, 0));
    }

    @Test
    void transitionMovesSmoothlyFromProviderTerrainToMacroTerrain() {
        assertEquals(0, MacroLayoutPlan.smoothTransitionFactor(0, 32));
        assertEquals(0.5, MacroLayoutPlan.smoothTransitionFactor(16, 32), 1.0e-9);
        assertEquals(1, MacroLayoutPlan.smoothTransitionFactor(32, 32));
        assertTrue(MacroLayoutPlan.smoothTransitionFactor(8, 32)
                < MacroLayoutPlan.smoothTransitionFactor(24, 32));
    }
}
