# Compatibility notes

Bounded Not Free sits around the active world generator rather than replacing it. That makes it work with a fairly broad range of biome/terrain mods, but worldgen mods differ enough internally that the exact terrain-influence path matters.

Use `/worldlayout compat` in the target modpack to see which strategy was selected, and `/worldlayout validate` to catch unsupported selectors or generator layouts before pregenerating a world.

## Where Bounded Not Free hooks worldgen

The mod does four main things:

1. constrains biome candidates returned through the active biome source;
2. filters normal structure starts against the configured layout;
3. influences terrain/climate inputs where the active generator exposes a usable graph; and
4. enforces `VOID`/barrier boundaries before generated chunks become live.

It does **not** replace the generator codec, surface rules, carvers, features, or an installed chunk scheduler.

## Terrain influence strategies

The active strategy is reported by `/worldlayout compat`.

### `CLIMATE_GRAPH`

Used for vanilla-like generators where continentalness, erosion, and weirdness are still meaningful terrain inputs. Bounded Not Free biases those fields in the affected region while leaving local temperature/humidity available for biome variants.

### `PROVIDER_PARAMETERS`

Used for Tectonic-style generation where the provider's parameter noises can be redirected without replacing its final density function. Bounded Not Free chooses a native terrain patch for the requested profile and feeds those provider parameters through the original graph.

This keeps Tectonic's own nonlinear terrain, cave, river, and tunnel relationships instead of blending two unrelated final-density fields.

### `PROVIDER_SAMPLE`

Fallback for density-decoupled providers that do not expose the cleaner parameter path. It redirects the provider terrain-noise leaves that can be identified safely and leaves unrelated subsurface systems local.

If a generator cannot be influenced safely, Bounded Not Free reports the limitation rather than trying to patch an unknown graph blindly.

## CAVE_WALL

`CAVE_WALL` is an extra rim style layered on top of the selected provider strategy. It creates an exposed rock cross-section with coherent cave openings at the world edge.

The compatibility report shows it as a suffix, for example:

```text
CLIMATE_GRAPH+CAVE_WALL
PROVIDER_PARAMETERS+C2ME_DFC+CAVE_WALL
```

## C2ME

C2ME's normal threaded scheduling is supported.

When C2ME's density-function compiler is enabled, the provider graph may be hidden behind generated wrappers. Bounded Not Free uses C2ME's retained fallback graph, applies the layout influence there, and recompiles through the compiler API supported by the installed C2ME version.

The reported strategy gains a `+C2ME_DFC` suffix when that path is active.

If a future C2ME build changes the compiler API in a way the mod cannot safely use, Bounded Not Free keeps the provider's compiled terrain and reports that terrain influence is unavailable rather than installing a partial graph.

## Tectonic

Tectonic uses `PROVIDER_PARAMETERS` when its parameter noises can be identified. Bounded Not Free influences continentalness, erosion, and ridge inputs inside Tectonic's own density graph rather than replacing the graph.

This is the preferred path with or without C2ME.

## Regions Unexplored / Lithostitched / Biolith

Biome-source wrappers are unwrapped when they expose a usable multi-noise delegate. This allows Bounded Not Free to work from the final injected climate points rather than a hardcoded vanilla list.

That is important for stacks such as Regions Unexplored + Lithostitched or Biolith where biome parameters are added after vanilla registration.

If the active biome source cannot be unwrapped to a supported multi-noise source, validation reports it and leaves that source unchanged.

## Supplementaries / Moonlight

Supplementaries adds worldgen features and structures but does not replace the base terrain generator.

Some of its features can place blocks late in decoration, so Bounded Not Free applies its outside-void/barrier policy again before the generated `ProtoChunk` is promoted. This prevents late decorations from leaking across a configured edge.

## Sable

Sable can react to live `LevelChunk` block updates and query neighbors for physics. Boundary cleanup therefore stays on generation-time `ProtoChunk`s instead of mutating live chunks during promotion.

If an alternate scheduler presents a live chunk in a path where cleanup would be unsafe, that pass is skipped rather than forcing synchronous neighbor loads.

## Forgified Fabric API

Fabric Biome API can attach world-seed state to Minecraft's climate sampler after `RandomState` construction. When Bounded Not Free wraps that sampler, it copies the optional seed state so downstream biome hooks continue to receive the expected seed.

Forgified Fabric API remains optional and is not bundled.

## Genesis world preview

Genesis preview integration is client-only and optional.

When available, the preview uses the current Overworld layout config and preview seed to show biome/layout constraints such as:

- outside/void biome selection;
- rim biomes;
- required-biome reservations;
- biome filters; and
- macro layouts.

Genesis displays biome colors rather than terrain geometry, so it does not preview terrain height, cave-wall cross sections, barriers, block-level dissolve, or structures.

Changing the preview seed or reopening the preview rebuilds its plan. Existing saved-world layout locking is not reused for a new-world preview.

## Structure mods

Structure filtering works with generators that go through Minecraft's standard structure-start path. Mods that create structures through a completely separate placement system may need a dedicated integration.

Reservations constrain eligible candidates; they cannot guarantee a final start for every custom/weighted structure system that bypasses vanilla placement.

## Testing a modpack

Worldgen compatibility is best checked with the actual target stack rather than by assuming that two mods which work separately will behave identically together.

For a new pack:

1. Create a fresh disposable world with the intended worldgen mods.
2. Run `/worldlayout compat` and `/worldlayout validate`.
3. Generate a targeted strip across the rim and at least one macro/required-biome region.
4. Check the outside boundary for leaked blocks/structures.
5. If using a pregenerator such as Chunky, run a small test radius before committing to the full map.
6. Save/restart and confirm the same layout seed/plan is retained.

Existing chunks are never regenerated by Bounded Not Free, so worldgen-layout changes should be tested on a fresh world or unexplored area.

Third-party mod JARs are development/runtime dependencies only and are not bundled with Bounded Not Free.
