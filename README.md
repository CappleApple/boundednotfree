# Bounded Not Free

Bounded Not Free gives modpacks control over the overall shape and geography of generated worlds without requiring a hand-built map.

It is made for NeoForge 1.21.1 and can limit where terrain, biomes, and structures are allowed to appear. The main use case is a finite or strongly shaped world that still feels naturally generated: continents, rings, islands, progression zones, biome regions, hard edges, cave walls, and similar layouts.

## What it can control

- Overworld, Nether, End, and custom dimensions independently.
- Circle, square, diamond, hexagon, rounded-square, polygon, star, and organic world boundaries.
- Biome and structure allow/deny rules.
- Named biome/structure groups and tag selectors.
- Macro regions and progression-style zones.
- Several biome layout styles, including radial, continent, Voronoi, climate-band, and archipelago layouts.
- Separate edge behavior for terrain and biome selection.
- Void edges, barrier walls, or cave-wall-style rims.
- World-border shapes that follow the configured playable area.
- Boundary-aware `/locate` behavior.
- Portal and cross-dimension destination clamping so travel does not drop players outside the allowed region.
- Preview export for checking a layout before committing to a world.

Existing chunks are never rewritten, so major layout changes should be treated like other worldgen changes: make a backup and preferably test them on a fresh world first.

## Getting started

1. Install the mod on the server and clients.
2. Start the game/server once to generate:

```text
config/boundednotfree/world-layout.json
```

3. Stop the server and edit the layout.
4. Enable the dimensions you want Bounded Not Free to control.
5. Create a new world.

The generated default configuration is intentionally disabled so installing the mod does not silently change world generation.

## Boundaries

A dimension can have its own boundary shape and outside behavior.

For a normal finite world, the simplest setup is usually a circle, square, or rounded square with void outside. More unusual packs can use polygons, stars, or organic boundaries.

Void edges can dissolve rather than ending in a perfectly vertical chunk wall. A pack can instead use a full-height barrier wall or a generated cave-wall rim if that better fits the setting.

## Biomes and regions

Biome rules can reference exact IDs, tags, or named groups. Those rules can then be combined with distance from the center, distance from an edge, macro regions, or progression zones.

The mod tries to work with the active biome provider rather than replacing it wholesale. This matters with large worldgen mods because the goal is to constrain their output while keeping their terrain character intact.

Rim terrain can also bias the provider toward terrain that fits the selected edge biome instead of merely swapping the biome label after terrain has already been chosen.

## Structures

Structure rules use the same general idea as biome rules: exact IDs, tags, groups, zones, and boundary-aware placement constraints.

Structure filtering stays in Minecraft's normal structure-generation path rather than spawning copies afterward, which helps modded structures behave normally when they are allowed.

## Commands

All commands require permission level 2.

```text
/worldlayout info
/worldlayout validate
/worldlayout compat
/worldlayout preview
```

`info` shows the current layout state for your position. `validate` checks selectors and required reservations. `compat` reports the active generator and detected worldgen integrations. `preview` writes SVG and JSON previews to the world folder.

## Compatibility

Bounded Not Free has specific handling for common modern worldgen stacks, including Tectonic and C2ME, while keeping those mods optional.

The exact strategy depends on the active generator. Vanilla-like density generators can be influenced directly. Providers with their own terrain model are handled through provider-specific adapters where possible, and unknown generators fall back conservatively instead of forcing assumptions that could corrupt terrain.

Custom generators that do not expose the information BNF needs may only receive the parts of the layout that can be applied safely.

More technical compatibility notes live in [COMPATIBILITY.md](COMPATIBILITY.md).

## Configuration and examples

- [Configuration reference](docs/CONFIGURATION.md)
- [Java API](docs/API.md)
- [Compatibility notes](COMPATIBILITY.md)
- [Example layouts](examples/README.md)
- [Known/deferred work](TODO.md)

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.244 or newer compatible 21.1 build
- Java 21

## Building and testing

```bash
./gradlew test build
```

Windows:

```powershell
.\gradlew.bat test build
.\gradlew.bat runServer
```

The test suite covers the boundary math and major world-edge behaviors. For actual modpack use, previewing a layout and testing it on a fresh world is still strongly recommended.
