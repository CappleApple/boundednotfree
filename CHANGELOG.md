# Changelog

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
