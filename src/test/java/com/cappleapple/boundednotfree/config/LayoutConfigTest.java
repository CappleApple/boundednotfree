package com.cappleapple.boundednotfree.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutConfigTest {
    private static final Gson GSON = new Gson();
    @Test void preservesDefaultsForOmittedFields() {
        LayoutConfig config = GSON.fromJson("{\"dimensions\":{\"minecraft:overworld\":{\"enabled\":true}}}", LayoutConfig.class);
        LayoutConfig.Dimension dimension = config.dimensions.get("minecraft:overworld");
        assertTrue(dimension.enabled);
        assertEquals(5000, dimension.radius);
        assertEquals("CIRCLE", dimension.boundaryShape);
        assertFalse(dimension.progressionZonesEnabled);
        assertEquals("PREFER", dimension.rimPlacementMode);
        assertEquals(1.0, dimension.rimInfluenceStrength);
        assertEquals(128.0, dimension.rimBlendWidth);
        assertEquals("NATIVE", dimension.rimTerrainStyle);
        assertEquals(96.0, dimension.rimCaveWallWidth);
        assertEquals(-48.0, dimension.rimCaveWallFloorY);
        assertEquals(192.0, dimension.rimCaveWallTopY);
        assertEquals(48.0, dimension.rimCaveWallSurfaceNoiseScale);
        assertEquals(12.0, dimension.rimCaveWallSurfaceNoiseStrength);
        assertEquals(32.0, dimension.rimCaveWallCaveScale);
        assertEquals(0.62, dimension.rimCaveWallCaveThreshold);
        assertEquals(0.0, dimension.voidEdgeDitherWidth);
        assertEquals(0.0, dimension.voidBlockDissolveWidth);
        assertEquals(8.0, dimension.voidBlockDissolveNoiseScale);
        assertEquals(32.0, dimension.macroTransitionWidth);
        assertEquals("NONE", dimension.gameplayBorder);
    }

    @Test void customSeedIsDistinctFromWorldSeedSetting() {
        LayoutConfig.Dimension dimension = GSON.fromJson("{\"customLayoutSeed\":123}", LayoutConfig.Dimension.class);
        assertEquals(123L, dimension.customLayoutSeed);
        assertEquals(0x424E4601L, dimension.layoutSalt);
    }
}
