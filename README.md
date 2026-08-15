# Bounded Not Free

Bounded Not Free is a server-authoritative NeoForge 1.21.1 world-layout controller. It constrains where biomes and structures may appear and can influence the active provider's climate and density graph so selected rim terrain is generated authentically.

It is intended for procedural modpack worlds that need a reproducible geography contract: a finite custom shape, a distinct rim, guaranteed biome regions, constrained structures, macro regions, and optional progression zones without shipping a hand-built map.

## Install

1. Install NeoForge 21.1.244 or newer for Minecraft 1.21.1.
2. Put `boundednotfree-1.0.1.jar` in the server's and clients' `mods` directories.
3. Start once to create `config/boundednotfree/world-layout.json`.
4. Stop the server, edit that file, set the desired dimensions to `"enabled": true`, then create a new world.

The generated default is disabled and conservative. Existing chunks are never rewritten. Keep backups before changing any world-generation configuration.

## What it supports

- Independent Overworld, Nether, End, or custom-dimension activation.
- Circle, square, diamond, hexagon, rounded-square, polygon, star, and deterministic organic boundaries.
- Separate biome rim, terrain outside mode, and optional square/custom-shape gameplay border.
- Exact IDs, `#tags`, and named `group:` selectors for biomes and structures.
- Whitelists/blacklists, deterministic rule precedence, distance/edge/profile/zone constraints, and bounded required-content planning.
- Vanilla, radial, continent, Voronoi, climate-band, and archipelago biome layouts.
- Provider-native rim terrain influence with smooth falloff and `PREFER` or `REQUIRE` biome placement.
- Optional full-height, one-block-thick barrier-block walls that follow the exact configured boundary shape.
- Deterministic saved layout seed/hash, optional per-world plan locking, SVG/JSON preview export, and a read-only Java API.

## Commands

All commands require permission level 2.

- `/worldlayout info` reports the current boundary metric, edge distance, zone, influence factor, selected climate target, and layout seed.
- `/worldlayout validate` reports selector and reservation problems.
- `/worldlayout compat` reports the active generator, biome source, registry sizes, and detected Tectonic, Regions Unexplored, Lithostitched, and TerraBlender mods.
- `/worldlayout preview` writes SVG and JSON files to `<world>/boundednotfree-previews/`.

## Architecture and compatibility

Biome selection delegates to the active generator's original resolver, then applies the configured layout. Structure filtering stays inside vanilla's standard structure-start path. For rim influence, the mod reads the finalized multi-noise parameter list after mod/datapack injection and biases terrain-bearing climate fields toward a selected provider-native point. Temperature and humidity remain local so the active biome source can choose associated variants.

Vanilla-like generators use direct climate-graph influence. If C2ME has already compiled that graph, the mod recovers C2ME's retained fallback graph, applies the influence in optimizer-visible vanilla density arithmetic, and recompiles it through the installed C2ME runtime. If a provider such as Tectonic decouples terrain density from those fields, the mod samples only provider terrain noise at native block scale, keeps caves/subsurface systems local, and blends independent terrain branches through the rim. The outside-void pass runs inside vanilla's existing post-unlock terrain completion stage, preserving C2ME's threaded future pipeline.

C2ME's default configuration supports full Tectonic rim terrain. If C2ME's optional experimental density-function compiler is manually enabled for a density-decoupled provider, Bounded Not Free preserves that provider's compiled terrain and falls back to biome-only rim influence; this prevents fragmentation. No C2ME, Chunky, Tectonic, or Regions Unexplored classes are compiled into or bundled with this mod. Custom non-noise generators and biome sources that do not expose a multi-noise delegate are reported by validation and left unchanged.

See [configuration](docs/CONFIGURATION.md), [API](docs/API.md), [compatibility notes](COMPATIBILITY.md), [examples](examples/README.md), and the honest [deferred-work list](TODO.md).

## Build and test

Use Java 21:

```powershell
.\gradlew.bat test build
.\gradlew.bat runServer
```

The test suite covers all boundary shapes, barrier-edge selection, rim falloff behavior, polygon/star validation, and configuration seed/default behavior. Release gates also launch fresh dedicated servers with vanilla, Tectonic, Regions Unexplored/Lithostitched, and C2ME/Chunky forced pre-generation stacks.
