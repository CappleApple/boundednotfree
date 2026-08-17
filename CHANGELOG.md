# Changelog

## 1.3.1 - 2026-08-17

### Fixed

- Fixed world creation crashing with Forgified Fabric API because the influenced replacement climate sampler did not inherit Fabric Biome API's world seed.

## 1.3 - 2026-08-17

### Added

- Added the opt-in provider-independent `CAVE_WALL` rim terrain style. It constructs a tall rock cross-section, carves deterministic three-dimensional cave pockets and tunnels through the exposed face, and fades the formation back into native terrain.

## 1.2.1 - 2026-08-16

### Fixed

- Fixed world creation deadlocking when Sable observes the final boundary cleanup during C2ME live-chunk promotion; final cleanup now runs after biome decoration while the target is still a generation chunk.
- Removed the post-noise undercut fill that extruded surface fragments and trees into stone pillars; provider transitions now retain the terrain shaped by the blended density graph.
- Fixed Tectonic rim and macro transitions creating enormous hollow cliffs by interpolating unrelated local and remote final-density fields. Tectonic influence now redirects only its native continentalness, erosion, and ridge parameter noises inside one terrain-and-cave graph, while mountain anchors continue to prefer normal relief over floating high ceilings.

## 1.2 - 2026-08-16

### Added

- Added `voidBlockDissolveWidth` and `voidBlockDissolveNoiseScale` for a deterministic three-dimensional noise gradient that dissolves individual blocks toward a `VOID` edge while retaining the existing column dither.
- Added `macroTransitionWidth` so constrained macro terrain fades through a provider-native coast instead of changing biome labels at a hard density boundary.

### Changed

- Recompiled provider-sampled terrain through both the legacy C2ME compiler API and C2ME 0.4's shared `gen.jvm` compilation context.
- Shortlisted provider terrain anchors before full patch scoring, avoiding repeated expensive Tectonic density scans for common ocean candidates.
- Expressed provider terrain blending as optimizer-visible vanilla density arithmetic so C2ME can compile the Tectonic graph instead of delegating the entire blend.

### Fixed

- Fixed Tectonic terrain remaining completely uninfluenced with C2ME 0.4.0 alpha 120 even though rim and continent biomes were reassigned.
- Fixed the inner rim biome boundary lagging behind interpolated rim terrain by one quart cell, which could leave an ocean label on the first mountain columns.
- Fixed a narrow strip of provider land being labeled as an ocean biome at `CONTINENTS` and `ARCHIPELAGO` region transitions.
- Prevented structures and features from refilling block-level dissolve holes before a newly generated chunk becomes live.

## 1.1 - 2026-08-16

### Added

- Added `voidEdgeDitherWidth`, a deterministic maximum inward erosion distance for `outsideMode: "VOID"`; `0` preserves the exact boundary and positive values break the edge into an irregular falloff without ever generating terrain beyond the configured shape.

### Changed

- Changed constrained macro layouts and required-biome reservations to influence the terrain-bearing climate graph as well as biome selection, so ocean profiles produce ocean terrain instead of relabeling existing hills.
- Changed density-decoupled provider sampling to rank verified terrain patches for each selected biome and reject ceiling-clipped mountain samples.

### Fixed

- Fixed `CONTINENTS`, `ARCHIPELAGO`, `VORONOI`, radial-band, and climate-band biome assignments disagreeing with the generated terrain.
- Fixed Tectonic rim selectors producing the requested mountain biome over unrelated local lowland terrain.
- Fixed full-strength Tectonic rim sampling retaining local density instead of replacing it with the verified provider-native mountain density.

## 1.0.3 - 2026-08-16

### Changed

- Changed constrained macro-layout biome selection to retain an accepted provider biome or choose the profile candidate nearest the provider's sampled multi-noise climate point.

### Fixed

- Kept active dimension plans registered through transient level-unload notifications emitted by threaded chunk lifecycles.
- Enforced `outsideMode: "VOID"` and generated barrier columns again when each new `ProtoChunk` becomes a live `LevelChunk`, covering C2ME paths that bypass NeoForge's new-chunk load event and removing deferred Supplementaries generator blocks before they can tick.
- Replaced per-quart coordinate hashing in `CONTINENTS`, `ARCHIPELAGO`, `VORONOI`, band, required-biome, rim-`REQUIRE`, and outside pools, eliminating patchwork biome placement inside a region.

## 1.0.2 - 2026-08-16

### Fixed

- Prevented structures and placed features, including Supplementaries worldgen, from restoring non-air blocks outside an `outsideMode: "VOID"` boundary after the initial terrain pass.
- Prevented later worldgen decoration from replacing generated `gameplayBorder: "BARRIER"` blocks.

## 1.0.1 - 2026-08-14

### Added

- Added the supplied bounded-world artwork as the NeoForge mod icon.

## 1.0 - 2026-08-14

### Added

- Added `gameplayBorder: "BARRIER"`, which generates a one-block-thick barrier-block wall along the inside perimeter of any supported boundary shape from minimum build height through the top buildable block.

### Changed

- Removed shipped Chunky automation, smoke probes, terrain signatures, threading counters, and their runtime system properties; compatibility tests now drive unmodified production code externally.
- Removed legacy or reserved configuration fields that had no implemented behavior, along with unreachable helpers and unused bootstrap settings.
- Reworked the complete configuration reference around the production 1.0 schema.
- Changed density-decoupled provider sampling to native block scale, with independent local/provider branches and provider-local subsurface noise.

### Fixed

- Fixed Tectonic rim terrain fragmenting into giant shelves, cave walls, and disconnected terrain.
- Preserved C2ME threaded generation with Tectonic under C2ME's default settings.
- Added a terrain-integrity guardrail for C2ME's optional experimental density-function compiler: opaque provider graphs keep their compiled terrain and use biome-only rim influence instead of producing corrupted terrain.

## 0.3.2 - 2026-08-14

### Changed

- Expressed climate-channel influence as vanilla density arithmetic around one shared two-dimensional boundary factor so density optimizers can retain the provider's graph structure.
- Cached repeated two-dimensional boundary measurements per generation thread.

### Fixed

- Recovered and recompiled C2ME's retained fallback density graph when its density-function compiler is active, preventing the compiled graph from being misclassified as density-decoupled.
- Eliminated the slow `PROVIDER_SAMPLE` fallback and cave-wall/shelf terrain artifact in the vanilla Overworld with C2ME's density compiler enabled.

## 0.3.1 - 2026-08-14

### Changed

- The outside-void terrain pass now runs in vanilla's existing post-section-unlock completion stage without replacing or extending C2ME's `fillFromNoise` future.
- Added a development-only Chunky pre-generation gate that records terrain-pass worker distribution and stops the dedicated server after completion.

### Fixed

- Preserved C2ME threaded chunk generation during Bounded Not Free terrain processing.
- Avoided section-lock contention while clearing outside-void columns under concurrent generation.

## 0.3.0 - 2026-08-14

### Added

- Provider-native rim climate/density influence derived from the finalized biome parameter list.
- Automatic `CLIMATE_GRAPH` and density-decoupled `PROVIDER_SAMPLE` strategies.
- `rimPlacementMode`, `rimInfluenceStrength`, and `rimBlendWidth` configuration.
- Optional biome-source delegate discovery for current Regions Unexplored/Lithostitched injection.

### Changed

- Temperature and humidity stay provider-local so associated biome variants remain naturally selectable.
- Removed the generic post-generation height profiles in favor of influencing the active terrain provider.

### Fixed

- Mountain rim selectors now produce actual mountain density terrain instead of only changing the biome label.
- Tectonic and Regions Unexplored can be used together without hard dependencies or bundled third-party code.

## 0.2.0 - 2026-08-14

### Added

- Optional biome-aligned terrain rules with mountain, hills, basin, plateau, flat, and custom-offset profiles.
- Smooth distance and edge blending, deterministic profile noise, and a configurable maximum terrain shift.
- Terrain diagnostics in commands, previews, and automated server probes.
- Dedicated-server compatibility gates for Tectonic 3.0.26, Regions Unexplored 0.6.2, and Lithostitched 1.7.13.

### Fixed

- Apply biome constraints through `NoiseBasedChunkGenerator`'s real biome-generation path, fixing rim selectors that previously had no effect in normal Overworld generation.
