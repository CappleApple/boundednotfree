# Compatibility notes

## Design boundary

Bounded Not Free wraps biome candidates supplied to the active `ChunkGenerator`, filters standard structure starts, and changes blocks only for `outsideMode: "VOID"` or `gameplayBorder: "BARRIER"`. Rim terrain influence is installed into the active `RandomState` before chunks generate; it does not replace the generator codec, surface rules, carvers, features, or C2ME's chunk scheduler.

Two automatic terrain strategies are used:

- `CLIMATE_GRAPH` biases continentalness, erosion, and weirdness wherever the provider reuses those fields in terrain density. Temperature and humidity remain local so the biome source can select associated mountain variants.
- `PROVIDER_SAMPLE` handles density-decoupled providers such as Tectonic. It redirects only provider terrain-noise leaves to a matching native region at one-block scale, builds independent local and sampled branches before interpolation/caching, keeps caves and other subsurface systems local, and blends the resulting solid terrain through the rim transition.

The native sample coordinates use a continuous folded mapping instead of coordinate compression. This avoids magnifying a small native feature into a giant shelf or cave wall. A post-noise transition pass closes only large below-mountain air/fluid undercuts above sea level; normal carvers still run afterward.

The parameter scan understands transparent biome-source wrappers through optional delegate discovery. Current Lithostitched injection is therefore read after Regions Unexplored adds its climate points. There is no compile-time dependency on Tectonic, Regions Unexplored, Lithostitched, C2ME, or Chunky.

## C2ME

C2ME's normal threaded chunk scheduling works with both strategies. With C2ME's default configuration, Tectonic uses the full `PROVIDER_SAMPLE` path and preserves threaded pre-generation.

C2ME's optional `useDensityFunctionCompiler` setting is experimental and disabled by default. When that compiler makes a density-decoupled provider graph opaque, Bounded Not Free deliberately selects `CLIMATE_ONLY+C2ME_DFC`: the compiled provider terrain is preserved, rim biomes are influenced, and provider-native rim terrain shaping is skipped. Disable that C2ME option (use its default) to get Tectonic-shaped rim mountains. Vanilla-like climate-coupled graphs can still use `CLIMATE_GRAPH+C2ME_DFC` with full terrain influence.

## Test matrix for 1.0

| Environment | Result |
| --- | --- |
| NeoForge 21.1.244, vanilla Overworld | `CLIMATE_GRAPH`; fresh dedicated server started and generated the boundary test strip; barrier bottom/top, non-barrier interior, outside void, mountain terrain, and mountain biome assertions passed; clean save and shutdown |
| C2ME 0.3.0+alpha.0.93 density compiler forced on, vanilla Overworld + Chunky 1.4.16 | `CLIMATE_GRAPH+C2ME_DFC`; 1,089 requested chunks completed in 6 seconds; barrier/void and terrain assertions passed; clean save and shutdown |
| Tectonic 3.0.26 + Lithostitched 1.7.13 + Chunky 1.4.16 | `PROVIDER_SAMPLE`; 1,089 requested chunks completed in 29 seconds; continuous transition, mountain terrain/biome, barrier, and void assertions passed; clean save and shutdown |
| Tectonic + Lithostitched + default C2ME + Chunky | `PROVIDER_SAMPLE`; 1,089 requested chunks completed in 18 seconds (about 60.5 chunks/second); the same seven assertions and vertical continuity probes passed; clean save and shutdown |
| Tectonic + Lithostitched + C2ME density compiler forced on + Chunky | `CLIMATE_ONLY+C2ME_DFC`; 1,089 requested chunks completed in 6 seconds; provider terrain remained continuous, rim biome/barrier/void assertions passed, and the intentional biome-only guardrail warning was emitted; clean save and shutdown |
| Regions Unexplored 0.6.2 + Lithostitched | Uses the climate-graph path; all 1,080 injected parameter points were discovered in the retained compatibility regression, and the user-confirmed 1.0 modpack test generated normally |

The 1,089-chunk tests used a fresh 512 by 512 block square centered on the eastern rim. Third-party test JARs are not bundled.

Use `/worldlayout compat` and `/worldlayout validate` in the final modpack. Unsupported custom generators, missing selected climate points, or unwrappable non-multi-noise biome sources are reported and left unchanged. Existing chunks are never regenerated.
