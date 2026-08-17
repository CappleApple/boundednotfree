# Compatibility notes

## Design boundary

Bounded Not Free wraps biome candidates supplied to the active `ChunkGenerator`, filters standard structure starts, and post-processes blocks only for `outsideMode: "VOID"` or `gameplayBorder: "BARRIER"`. Rim and macro-layout terrain influence is installed into the active `RandomState` before chunks generate; it does not replace the generator codec, surface rules, carvers, features, or C2ME's chunk scheduler. During later decoration, writes through vanilla's `WorldGenRegion` are rejected only when they would restore a non-air block outside a `VOID` boundary or replace a generated barrier column. The final policy pass runs after biome decoration while the target is still a `ProtoChunk`, before lighting and live-chunk promotion.

Three automatic terrain strategies are used:

- `CLIMATE_GRAPH` biases continentalness, erosion, and weirdness wherever the configured rim, reservation, or macro layout applies and the provider reuses those fields in terrain density. Temperature and humidity remain local so the biome source can select associated variants.
- `PROVIDER_PARAMETERS` handles Tectonic. It redirects only Tectonic's primary continentalness, erosion, and ridge parameter noises to a ranked native patch, inside one original Tectonic density graph. Its nonlinear terrain spline, caves, underground rivers, and lava tunnels therefore retain their native relationships; temperature, vegetation, aquifers, fluid levels, and ore veins remain local.
- `PROVIDER_SAMPLE` is the conservative fallback for unknown density-decoupled providers. It redirects classified provider terrain-noise leaves to a ranked native patch and keeps independently identified subsurface systems local.

Both provider-native strategies use a continuous folded coordinate mapping instead of coordinate compression. Tectonic transitions blend the parameter inputs to one density graph; they never interpolate unrelated local and remote final-density values, which can introduce extra solid/air zero crossings and enormous hollow cliffs. Provider transitions are shaped entirely by the density graph; the final boundary pass never fills terrain beneath overhangs or between surface fragments.

The optional `CAVE_WALL` rim style deliberately creates a controlled world-edge cross-section after the safe provider strategy has been selected. It wraps both final density and preliminary surface density with the same provider-independent wall and three-dimensional cave fields, so the exposed rock and cave openings agree without disturbing Tectonic's internal terrain/cave relationship. Runtime reports it as a suffix such as `CLIMATE_GRAPH+CAVE_WALL` or `PROVIDER_PARAMETERS+C2ME_DFC+CAVE_WALL`.

The parameter scan understands transparent biome-source wrappers through optional delegate discovery. Current Lithostitched injection is therefore read after Regions Unexplored adds its climate points. There is no compile-time dependency on Tectonic, Regions Unexplored, Lithostitched, C2ME, or Chunky.

## C2ME

C2ME's normal threaded chunk scheduling works with all three strategies. Tectonic uses `PROVIDER_PARAMETERS` with or without C2ME.

C2ME's density-function compiler can hide the provider graph behind generated wrappers. Bounded Not Free recovers the retained fallback graph, applies the layout, and recompiles it through either the legacy compiler API or C2ME 0.4's shared `gen.jvm` compilation context. Tectonic can therefore use `PROVIDER_PARAMETERS+C2ME_DFC`, other density-decoupled providers can use `PROVIDER_SAMPLE+C2ME_DFC`, and vanilla-like graphs use `CLIMATE_GRAPH+C2ME_DFC`. If a future C2ME compiler API cannot be called safely, the active compiled provider graph is preserved and the incomplete terrain influence is reported instead of installing a fragmented branch.

## Supplementaries

Supplementaries does not replace the terrain generator, but it intentionally participates in world generation. It registers structures and placed features such as road signs, cave urns, barnacles, wild flax, and basalt ash. Its road-sign generator can defer work to a block entity, then search structure-start chunks and place the completed sign through `ServerLevel`. Bounded Not Free therefore guards ordinary `WorldGenRegion` writes and performs a final cleanup immediately after biome decoration, before the generation chunk is promoted and can tick.

## Sable

Sable observes live `LevelChunk.setBlockState` calls and may query neighboring chunks for its physics neighborhood. Bounded Not Free 1.2.1 never performs final boundary cleanup through a live chunk: cleanup runs on the decorated `ProtoChunk`, and an additional runtime guard skips the pass if an alternate scheduler supplies a `LevelChunk`. This avoids synchronous neighbor loads from inside C2ME promotion workers.

## Biolith

Biolith's injected biome source remains visible to Bounded Not Free through its retained `MultiNoiseBiomeSource` delegate. The exact-pack test with Biolith 3.0.14 discovered all 1,080 active climate points and exercised both vanilla-compatible and Tectonic terrain paths. C2ME 0.4.0-alpha.0.120 automatically disabled its own incompatible End-biome cache when Biolith was present; this is C2ME's compatibility guard, not a Bounded Not Free failure.

## Test matrix through 1.3

| Environment | Result |
| --- | --- |
| NeoForge 21.1.244, vanilla Overworld | `CLIMATE_GRAPH`; fresh dedicated server started and generated the boundary test strip; barrier bottom/top, non-barrier interior, outside void, mountain terrain, and mountain biome assertions passed; clean save and shutdown |
| Supplementaries 3.8.9 + Moonlight 3.3.4 + C2ME 0.3.0+alpha.0.93 + Chunky 1.4.16 | A fresh radius-384 ocean-only `CONTINENTS` world generated 2,601 chunks in 10 seconds. All 425,385 measured interior columns had an ocean surface at or below Y=68 (95th percentile Y=62; no dry terrain above Y=80), and all 186 wholly outside full chunks contained zero non-air blocks; clean save and shutdown |
| C2ME 0.3.0+alpha.0.93 density compiler forced on, vanilla Overworld + Chunky 1.4.16 | `CLIMATE_GRAPH+C2ME_DFC`; 1,089 requested chunks completed in 6 seconds; barrier/void and terrain assertions passed; clean save and shutdown |
| Tectonic 3.0.26 + Lithostitched 1.7.13 + Chunky 1.4.16 | `PROVIDER_SAMPLE`; 1,089 requested chunks completed in 29 seconds; continuous transition, mountain terrain/biome, barrier, and void assertions passed; clean save and shutdown |
| Tectonic 3.0.26 + Lithostitched 1.7.13 + default C2ME + Chunky 1.4.16 | `PROVIDER_SAMPLE`; the ranked frozen-peaks source patch measured Y=197..280. A fresh 2,601-chunk circle completed in 40 seconds; the full-strength outer rim had median dry terrain Y=272 and 95th percentile Y=300, while all 167 wholly outside full chunks contained zero non-air blocks; clean save and shutdown |
| Supplementaries + Moonlight + default C2ME + Chunky, `VOID` dither width 64 | A fresh 2,601-chunk circle completed in 8 seconds. The analysis found 35,952 erased columns inside the configured 64-block band and zero non-air blocks in all 167 wholly outside full chunks; clean save and shutdown |
| Regions Unexplored 0.6.2 + Lithostitched | Uses the climate-graph path; all 1,080 injected parameter points were discovered in the retained compatibility regression, and the user-confirmed 1.0 modpack test generated normally |
| C2ME 0.4.0-alpha.0.120 + Tectonic 3.0.26 + Lithostitched 1.8.0+beta4 + Biolith 3.0.14 + Regions Unexplored 0.6.2 + Supplementaries 3.8.10 + Moonlight 3.3.4 + Chunky 1.4.23 | `PROVIDER_SAMPLE+C2ME_DFC`; a fresh exact-pack boundary strip completed 169 chunks in 15 seconds. All 5,488 ocean-tagged columns had no dry terrain above Y=80, the selected rim produced mountain terrain, and all 36 wholly outside full chunks contained zero non-air blocks; clean save and shutdown |
| Same exact stack without Tectonic | `CLIMATE_GRAPH+C2ME_DFC`; a targeted fresh continent/coast strip completed 169 chunks in 1 second. All 3,680 ocean columns had dry terrain at or below Y=61 with no elevated ocean artifact; clean save and shutdown |
| Exact Tectonic stack, column dither 32 + block dissolve 64 | At Y=64, occupancy was 100% before the dissolve band, approximately 50% through its middle, then 18.5%, 3.0%, 0.1%, and 0% in successive outer eight-block bands. No block was retained outside the nominal boundary. |
| Exact Tectonic stack, C2ME 0.4.0-alpha.0.120, `VANILLA` layout, both dither widths `0` | `PROVIDER_PARAMETERS+C2ME_DFC`; six Tectonic parameter-noise leaves were influenced inside one provider graph. The ranked frozen-peaks patch measured Y=110..257 (average Y=177.980). A fresh 289-chunk square centered on the eastern rim completed in 3 seconds; layout and compatibility validation passed, all dimensions saved, and the server shut down cleanly. |
| Same exact Tectonic stack without C2ME | `PROVIDER_PARAMETERS`; the same six parameter-noise leaves and provider patch were selected. A fresh targeted 49-chunk rim square completed in 2 seconds; both validators passed, all dimensions saved, and the server shut down cleanly. |
| Exact Tectonic + C2ME stack, `CAVE_WALL`, `VANILLA` layout, both dither widths `0` | `PROVIDER_PARAMETERS+C2ME_DFC+CAVE_WALL`; a fresh 289-chunk edge square completed in 4 seconds. At Y=72..176, a loaded cross-section sampled 32 air openings and 206 occupied points on the exposed face, then 41 air openings and 197 occupied points 48 blocks inward. The outside probe was void, both validators passed, and shutdown saved every dimension. |
| Same `CAVE_WALL` profile with both Tectonic and C2ME disabled | `CLIMATE_GRAPH+CAVE_WALL`; a fresh 289-chunk edge square completed in 6 seconds. The same deterministic cross-section produced the same opening/rock partition counts, the outside probe was void, both validators passed, and shutdown saved every dimension. |

The 1,089-chunk tests used a fresh 512 by 512 block square centered on the eastern rim. Third-party test JARs are not bundled.

Use `/worldlayout compat` and `/worldlayout validate` in the final modpack. Unsupported custom generators, missing selected climate points, or unwrappable non-multi-noise biome sources are reported and left unchanged. Existing chunks are never regenerated.
