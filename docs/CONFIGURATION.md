# Complete configuration reference

This document describes every world-layout field implemented by Bounded Not Free 1.0. The authoritative file is `config/boundednotfree/world-layout.json`.

The file is ordinary JSON: comments and trailing commas are invalid. String options are case-insensitive, but the uppercase values shown here are recommended. Unknown fields are ignored, so a misspelled field silently retains its default. Changes affect newly generated chunks and do not rebuild existing terrain.

## Recommended mountain-rim configuration

```json
{
  "schemaVersion": 1,
  "requiredContentFailurePolicy": "WARN",
  "biomeGroups": {
    "mountains": ["#c:is_mountain"],
    "oceans": ["#minecraft:is_ocean"]
  },
  "structureGroups": {},
  "continentProfiles": {},
  "dimensions": {
    "minecraft:overworld": {
      "enabled": true,
      "centerX": 0,
      "centerZ": 0,
      "radius": 2500,
      "boundaryShape": "CIRCLE",
      "rimEnabled": true,
      "rimWidth": 256,
      "rimPlacementMode": "PREFER",
      "rimSelectors": ["group:mountains"],
      "rimInfluenceStrength": 1.0,
      "rimBlendWidth": 128,
      "outsideMode": "VOID",
      "gameplayBorder": "BARRIER",
      "biomeFilterMode": "BLACKLIST",
      "biomeFilter": [],
      "invalidBiomeBehavior": "NEAREST_ALLOWED",
      "fallbackBiome": "minecraft:plains",
      "biomeRules": [],
      "requiredBiomes": [],
      "structureFilterMode": "BLACKLIST",
      "structureFilter": [],
      "structureRules": [],
      "requiredStructures": [],
      "biomeLayout": "VANILLA",
      "progressionZonesEnabled": false,
      "progressionZones": {},
      "maxPlannerAttempts": 20000,
      "lockLayoutAfterWorldCreation": true
    },
    "minecraft:the_nether": { "enabled": false },
    "minecraft:the_end": { "enabled": false }
  }
}
```

## Selector syntax

| Form | Example | Meaning |
| --- | --- | --- |
| Exact ID | `minecraft:stony_peaks` | One exact biome or structure. |
| Registry tag | `#minecraft:is_ocean` | Every registered entry in that tag, including mod/datapack additions. |
| Named group | `group:mountains` | Entries in the matching root group. Groups may contain IDs, tags, or other groups. |

Biome selectors use `biomeGroups`; structure selectors use `structureGroups`. Missing content resolves to an empty set. Recursive groups are validation errors.

For overlapping rules, higher `priority` wins. Ties prefer exact ID, then tag, then group; equal-priority/equal-specificity rules retain JSON order.

## Root parameters

| Parameter | Type | Default | Options and behavior |
| --- | --- | --- | --- |
| `schemaVersion` | integer | `1` | Must be exactly `1`; another value stops loading. |
| `requiredContentFailurePolicy` | string | `WARN` | `WARN` logs validation problems and continues. `FAIL_WORLD_CREATION` makes any collected layout validation problem fatal. |
| `biomeGroups` | object of string arrays | `{}` | Reusable biome selector groups. |
| `structureGroups` | object of string arrays | `{}` | Reusable structure selector groups. |
| `continentProfiles` | object of profile objects | `{}` | Named selector pools used by macro layouts and bands. |
| `dimensions` | object of dimension objects | `{}` | Keys are IDs such as `minecraft:overworld` or `modid:dimension_name`. Omitted dimensions are untouched. |

## Dimension center, size, and seed

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `enabled` | boolean | `false` | Enables the plan for this dimension. |
| `centerX` | integer | `0` | World X coordinate of the layout center. |
| `centerZ` | integer | `0` | World Z coordinate of the layout center. |
| `radius` | number | `5000` | Fallback half-extent and planner search radius; also the base radius for `ORGANIC`. Keep it large enough to contain custom polygon/star layouts. |
| `extentX` | number | `0` | Positive values replace `radius` as the X half-extent for basic/rounded shapes and normalized polygon points. `0` uses `radius`. |
| `extentZ` | number | `0` | Positive values replace `radius` as the Z half-extent. Unequal circle extents create an ellipse. |
| `layoutSalt` | integer | `1112425985` | XORed with the Minecraft world seed for deterministic planning; also affects macro-band distortion. |
| `customLayoutSeed` | integer or `null` | `null` | If set, replaces `worldSeed XOR layoutSalt` as the layout seed. It does not replace Minecraft's terrain seed. |

All effective extents must be positive and satisfy `abs(center) + extent <= 29,999,984`.

## Boundary shapes

`boundaryShape` accepts:

| Value | Shape |
| --- | --- |
| `CIRCLE` | Ellipse using X/Z extents; a circle when they match. |
| `SQUARE` | Axis-aligned rectangle; a square when extents match. |
| `DIAMOND` | Axis-aligned diamond stretched by X/Z extents. |
| `HEXAGON` | Six-sided boundary stretched by X/Z extents. |
| `ROUNDED_SQUARE` | Rounded rectangle using X/Z extents and `cornerRadius`. |
| `POLYGON` | Custom non-self-intersecting polygon from `polygonVertices`. |
| `STAR` | Alternating-radius star from the `star*` fields. |
| `ORGANIC` | Seeded noisy radial boundary from the `organic*` fields. |

Shape parameters:

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `cornerRadius` | number | `256` | For `ROUNDED_SQUARE`; must be `0` through the smaller extent. |
| `starPoints` | integer | `5` | Star tips; minimum `3`. |
| `starInnerRadius` | number | `2500` | Positive valley radius smaller than `starOuterRadius`. |
| `starOuterRadius` | number | `5000` | Tip radius greater than `starInnerRadius`. |
| `starRotation` | number | `0` | Rotation in degrees. |
| `organicNoiseStrength` | number | `500` | Maximum radial variation; at least `0` and smaller than `radius`. |
| `organicNoiseScale` | number | `0.001` | Positive organic-noise scale. |
| `organicNoiseOctaves` | integer | `2` | Detail layers; range `1` through `8`. |
| `polygonVertices` | point array | `[]` | Ordered local vertices for `POLYGON`; requires at least three unique, finite, non-self-intersecting points. |

A point contains:

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `x` | number | `0` | Local X relative to `centerX`. |
| `z` | number | `0` | Local Z relative to `centerZ`. |
| `normalized` | boolean | `false` | If true, X is multiplied by the X extent and Z by the Z extent. |

## Rim parameters

The rim is the inside region whose edge distance is at most `rimWidth`.

| Parameter | Type | Default | Options and behavior |
| --- | --- | --- | --- |
| `rimEnabled` | boolean | `false` | Enables rim biome selection and provider-native climate/density influence. |
| `rimWidth` | number | `256` | Rim thickness in blocks, measured inward from the edge. |
| `rimPlacementMode` | string | `PREFER` | `PREFER` retains the provider's influenced biome. `REQUIRE` additionally replaces a remaining nonmatching biome with a deterministic rim match. |
| `rimSelectors` | string array | `[]` | Biomes whose finalized multi-noise points can supply rim terrain; also the forced pool for `REQUIRE`. |
| `rimInfluenceStrength` | number | `1.0` | Range `0.0` through `1.0`; zero disables density influence and one reaches the selected target in the fully influenced section. |
| `rimBlendWidth` | number | `128` | Inward blend distance, clamped to `rimWidth`. `0` fully influences the entire rim. |

The provider supplies the terrain-bearing climate point. Continentalness, erosion, and weirdness are influenced; temperature and humidity remain local so associated vanilla/modded variants remain possible. Density-decoupled providers such as Tectonic are sampled at native block scale while caves and other subsurface noise stay local. This affects only new chunks and requires compatible noise generation.

C2ME's normal/default configuration supports full provider-native Tectonic rim terrain. If C2ME's optional experimental `useDensityFunctionCompiler` option is manually enabled and makes a provider's terrain graph opaque, the mod preserves that terrain and applies biome-only rim influence. Leave that C2ME option at its default to retain terrain shaping.

## Outside parameters

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `outsideMode` | string | `NORMAL` | Selects the behavior below. |
| `outsideSelectors` | string array | `[]` | Biome pool for `CUSTOM`; when nonempty, it is also preferred by other non-`NORMAL` modes. |

| `outsideMode` | Biome outside | Blocks outside |
| --- | --- | --- |
| `NORMAL` | Original provider biome. | Original terrain. |
| `VOID` | `outsideSelectors`, otherwise `minecraft:the_void`. | Every non-air block in outside columns is removed during generation. |
| `OCEAN` | `outsideSelectors`, otherwise `#minecraft:is_ocean`. | Original terrain; no forced basin or water fill. |
| `LAVA_OCEAN` | `outsideSelectors`, otherwise `minecraft:nether_wastes`. | Original terrain; no forced lava fill. |
| `CUSTOM` | Deterministic match from `outsideSelectors`; empty uses fallback handling. | Original terrain. |

Only `VOID` changes blocks. Other modes constrain biome selection without reshaping or filling terrain.

## `gameplayBorder`

| Value | Behavior |
| --- | --- |
| `NONE` | No movement or physical boundary. `outsideMode` still affects new terrain. |
| `VANILLA_WHERE_POSSIBLE` | Installs Minecraft's standard border for `SQUARE` and `ROUNDED_SQUARE`, centered on the configured center and sized from `extentX` or `radius`. Rounded corners are not enforced. Other shapes warn and leave it unchanged. |
| `CUSTOM_SHAPE` | Every five ticks, moves outside players to one block inside the exact shape at the same Y. It has no vanilla border fog/warning/damage and does not constrain non-player entities. |
| `BARRIER` | During new chunk generation, fills the one-block-thick inside perimeter with `minecraft:barrier` from the dimension's minimum build height through its top buildable block. It follows every supported boundary shape and physically blocks players and other colliding entities. |

`BARRIER` is independent of `rimEnabled`; the rim controls biomes/terrain while the barrier follows the outer boundary. Barrier blocks are invisible unless a player holds a barrier item. Existing chunks are not retrofitted. With `outsideMode: "VOID"`, the wall remains on the inside edge and the next outside column is cleared.

## Biome filtering

| Parameter | Type | Default | Options and behavior |
| --- | --- | --- | --- |
| `biomeFilterMode` | string | `BLACKLIST` | `BLACKLIST` permits everything except `biomeFilter`; `WHITELIST` permits only `biomeFilter`. |
| `biomeFilter` | string array | `[]` | Empty blacklist permits all; empty whitelist is a validation problem. |
| `invalidBiomeBehavior` | string | `NEAREST_ALLOWED` | `FALLBACK`, `NEAREST_ALLOWED`, or `ERROR`. |
| `fallbackBiome` | string | `minecraft:plains` | Used by `FALLBACK` and as the last safety candidate. |
| `biomeRules` | biome-rule array | `[]` | Location restrictions on matching original-provider biomes after outside/rim/reservation/macro precedence. |
| `requiredBiomes` | biome-rule array | `[]` | Requests deterministic circular biome reservations; reservations precede macro layout. |

`invalidBiomeBehavior`:

| Value | Behavior |
| --- | --- |
| `FALLBACK` | Uses `fallbackBiome` when it resolves, then general allowed-pool handling. |
| `NEAREST_ALLOWED` | Currently chooses a deterministic member of the globally allowed pool; it is not yet a geographic nearest-biome search. |
| `ERROR` | Throws when generation encounters a disallowed biome. |

## Common rule fields

These fields exist on biome, required-biome, structure, and required-structure rules.

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `selector` | string | `minecraft:plains` | Biome or structure selector for the containing list. |
| `minDistance` | number | `0` | Minimum Euclidean blocks from center, inclusive. |
| `maxDistance` | number | unbounded | Maximum blocks from center. Omit for no maximum. |
| `minNormalizedDistance` | number or `null` | `null` | Optional minimum shape-normalized distance (`0` center, `1` edge). |
| `maxNormalizedDistance` | number or `null` | `null` | Optional maximum normalized distance. |
| `minEdgeDistance` | number | `0` | Minimum blocks inward from the edge. |
| `maxEdgeDistance` | number | unbounded | Maximum blocks inward from the edge. Omit for no maximum. |
| `priority` | integer | `0` | Higher values win; ties use selector specificity and JSON order. |
| `allowedZones` | string array | `[]` | If nonempty, active zone must be listed. |
| `forbiddenZones` | string array | `[]` | Rejects listed active zones. |
| `allowedProfiles` | string array | `[]` | If nonempty, evaluated macro-profile context must match when rule filtering is reached. |
| `forbiddenProfiles` | string array | `[]` | Rejects matching profile context. |

JSON has no infinity literal. Omit an unbounded maximum instead of writing `Infinity`.

## Required-biome fields

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `minInstances` | integer | `0` | Requested reservation count; values below one currently request one. |
| `minArea` | number | `65536` | Reservation radius is `sqrt(max(256, minArea) / pi)`. |

The planner attempts each requested instance up to `maxPlannerAttempts` and avoids overlap with previous reservations. It is a bounded search; inspect `/worldlayout validate` and `/worldlayout preview` before release.

## Structure parameters

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `structureFilterMode` | string | `BLACKLIST` | `BLACKLIST` permits structures except listed matches; `WHITELIST` permits only matches. Required selectors bypass this base filter. |
| `structureFilter` | string array | `[]` | Structure selectors for the filter. |
| `structureRules` | structure-rule array | `[]` | Location and session maximum restrictions for matching new standard starts. |
| `requiredStructures` | structure-rule array | `[]` | Requests reservations at eligible vanilla-style structure-set candidates. |

Structure-rule-only fields:

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `minCount` | integer | `0` | Requested count for `requiredStructures`; below one currently requests one. Not used by ordinary rules. |
| `maxCount` | integer | unbounded | Maximum new matching starts during the current server session. |
| `exactCount` | integer or `null` | `null` | Replaces `minCount` for planning and `maxCount` for the session limit. |
| `minSpacingFromSelf` | number | `512` | Reservation spacing input. Current planning compares the maximum of both spacing values against every earlier reservation. |
| `minSpacingFromAnyStructure` | number | `0` | Second reservation spacing input. |

Reservations identify eligible standard placement candidates but do not bypass Minecraft's final biome/terrain checks or custom generators. Count tracking is session-local and does not recount historical starts after restart.

## Macro biome layouts

`biomeLayout` accepts:

| Value | Behavior |
| --- | --- |
| `VANILLA` | Retains accepted candidates from the active biome source. |
| `RADIAL` | Uses `radialBands` against normalized center distance. |
| `CLIMATE_BANDS` | Uses `climateBands` on a geographic axis; this is not the underlying temperature/humidity sampler. |
| `VORONOI` | Assigns every point to the nearest of `regionCount` centers. |
| `CONTINENTS` | Uses `continentCount` bounded, noise-warped circular regions plus between-region biomes. |
| `ARCHIPELAGO` | Same current region algorithm using `islandCount`; it does not create floating islands. |

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `radialBands` | band array | `[]` | First inclusive matching band in JSON order wins for `RADIAL`. |
| `climateBands` | band array | `[]` | First inclusive matching band wins for `CLIMATE_BANDS`. |
| `climateAxis` | string | `NORTH_SOUTH` | `NORTH_SOUTH`, `EAST_WEST`, `RADIAL`, or `ANGLE`; unknown values act as north/south. |
| `climateAngle` | number | `0` | Degrees when the axis is `ANGLE`. |
| `regionCount` | integer | `12` | Centers for `VORONOI`. |
| `continentCount` | integer | `4` | Regions for `CONTINENTS`. |
| `islandCount` | integer | `24` | Regions for `ARCHIPELAGO`. |
| `minRegionRadius` | number | `450` | Minimum generated region radius. |
| `maxRegionRadius` | number | `1400` | Maximum radius; values below the minimum effectively use the minimum. |
| `minRegionSpacing` | number | `256` | Minimum center spacing. |
| `transitionNoiseStrength` | number | `64` | Deterministic band-edge distortion for `RADIAL` and `CLIMATE_BANDS`. Region edges use a fixed warp. |
| `betweenRegionsMode` | string | `BIOME` | `BIOME` uses `betweenRegionSelectors`; `VOID` selects `minecraft:the_void` as a biome but does not remove blocks. |
| `betweenRegionSelectors` | string array | `[#minecraft:is_ocean]` | Filler biome pool between continent/archipelago regions. |
| `profileSequence` | string array | `[]` | Cycles profile names across generated centers. Empty or unknown names produce empty region pools. |

Band fields:

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `min` | number | `0` | Inclusive lower coordinate, normally normalized `0` through `1`. |
| `max` | number | `1` | Inclusive upper coordinate. |
| `selectors` | string array | `[]` | Biome pool when no existing profile is selected. |
| `profile` | string or omitted | omitted | Existing profile takes precedence; unknown profile falls back to `selectors`. |

A `continentProfiles` entry has one field:

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `selectors` | string array | `[]` | Biome pool used by bands and generated regions. |

## Progression zones

Zones are static geographic labels consumed by rule allow/deny lists. They do not expand borders, unlock chunks, run commands, or change over time.

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `progressionZonesEnabled` | boolean | `false` | Enables zone lookup. |
| `progressionZones` | ordered object | `{}` | First matching named zone in JSON order wins. |

Zone fields:

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `distanceMode` | string | `NORMALIZED_WORLD_DISTANCE` | `NORMALIZED_WORLD_DISTANCE` uses `0` center/`1` edge; `ABSOLUTE_BLOCK_DISTANCE` uses Euclidean blocks. Unknown values act as normalized. |
| `minDistance` | number | `0` | Inclusive lower bound. |
| `maxDistance` | number | `1` | Inclusive upper bound. |

## Planner and persistence

| Parameter | Type | Default | Behavior |
| --- | --- | --- | --- |
| `maxPlannerAttempts` | integer | `20000` | Attempt cap for required-biome, required-structure, and macro-region planning. |
| `lockLayoutAfterWorldCreation` | boolean | `true` | Stores the first complete root JSON in world data. If the external hash changes and this remains true, the stored snapshot is used for that existing world. |

A locked world does not pick up later group, profile, or dimension edits. Set the lock false before creating a world if later changes should affect unexplored chunks; changing generation settings can create seams.

## Schema-complete example

This disabled reference specimen shows every implemented field and JSON type. Only fields relevant to the selected shape/layout are active at one time.

```json
{
  "schemaVersion": 1,
  "requiredContentFailurePolicy": "WARN",
  "biomeGroups": {
    "mountains": ["#c:is_mountain", "minecraft:stony_peaks"]
  },
  "structureGroups": {
    "villages": ["#minecraft:village"]
  },
  "continentProfiles": {
    "temperate": {
      "selectors": ["minecraft:plains", "minecraft:forest"]
    }
  },
  "dimensions": {
    "minecraft:overworld": {
      "enabled": false,
      "centerX": 0,
      "centerZ": 0,
      "radius": 5000,
      "extentX": 0,
      "extentZ": 0,
      "boundaryShape": "CIRCLE",
      "cornerRadius": 256,
      "starPoints": 5,
      "starInnerRadius": 2500,
      "starOuterRadius": 5000,
      "starRotation": 0,
      "organicNoiseStrength": 500,
      "organicNoiseScale": 0.001,
      "organicNoiseOctaves": 2,
      "polygonVertices": [
        { "x": -1, "z": -1, "normalized": true },
        { "x": 1, "z": -1, "normalized": true },
        { "x": 1, "z": 1, "normalized": true },
        { "x": -1, "z": 1, "normalized": true }
      ],
      "layoutSalt": 1112425985,
      "customLayoutSeed": null,
      "rimEnabled": false,
      "rimWidth": 256,
      "rimPlacementMode": "PREFER",
      "rimSelectors": ["group:mountains"],
      "rimInfluenceStrength": 1.0,
      "rimBlendWidth": 128,
      "outsideMode": "NORMAL",
      "outsideSelectors": [],
      "gameplayBorder": "NONE",
      "biomeFilterMode": "BLACKLIST",
      "biomeFilter": [],
      "invalidBiomeBehavior": "NEAREST_ALLOWED",
      "fallbackBiome": "minecraft:plains",
      "biomeRules": [
        {
          "selector": "minecraft:desert",
          "minDistance": 0,
          "maxDistance": 5000,
          "minNormalizedDistance": null,
          "maxNormalizedDistance": null,
          "minEdgeDistance": 0,
          "maxEdgeDistance": 5000,
          "priority": 0,
          "allowedZones": [],
          "forbiddenZones": [],
          "allowedProfiles": [],
          "forbiddenProfiles": []
        }
      ],
      "requiredBiomes": [
        {
          "selector": "minecraft:badlands",
          "minDistance": 0,
          "maxDistance": 5000,
          "minNormalizedDistance": null,
          "maxNormalizedDistance": null,
          "minEdgeDistance": 0,
          "maxEdgeDistance": 5000,
          "priority": 10,
          "allowedZones": [],
          "forbiddenZones": [],
          "allowedProfiles": [],
          "forbiddenProfiles": [],
          "minInstances": 1,
          "minArea": 65536
        }
      ],
      "structureFilterMode": "BLACKLIST",
      "structureFilter": [],
      "structureRules": [
        {
          "selector": "minecraft:stronghold",
          "minDistance": 0,
          "maxDistance": 5000,
          "minNormalizedDistance": null,
          "maxNormalizedDistance": null,
          "minEdgeDistance": 0,
          "maxEdgeDistance": 5000,
          "priority": 0,
          "allowedZones": [],
          "forbiddenZones": [],
          "allowedProfiles": [],
          "forbiddenProfiles": [],
          "minCount": 0,
          "maxCount": 2147483647,
          "exactCount": null,
          "minSpacingFromSelf": 512,
          "minSpacingFromAnyStructure": 0
        }
      ],
      "requiredStructures": [
        {
          "selector": "group:villages",
          "minDistance": 0,
          "maxDistance": 5000,
          "minNormalizedDistance": null,
          "maxNormalizedDistance": null,
          "minEdgeDistance": 0,
          "maxEdgeDistance": 5000,
          "priority": 10,
          "allowedZones": [],
          "forbiddenZones": [],
          "allowedProfiles": [],
          "forbiddenProfiles": [],
          "minCount": 1,
          "maxCount": 2147483647,
          "exactCount": null,
          "minSpacingFromSelf": 512,
          "minSpacingFromAnyStructure": 0
        }
      ],
      "biomeLayout": "VANILLA",
      "radialBands": [
        { "min": 0, "max": 1, "selectors": ["minecraft:plains"], "profile": "temperate" }
      ],
      "climateBands": [
        { "min": 0, "max": 1, "selectors": ["minecraft:plains"], "profile": "temperate" }
      ],
      "climateAxis": "NORTH_SOUTH",
      "climateAngle": 0,
      "regionCount": 12,
      "continentCount": 4,
      "islandCount": 24,
      "minRegionRadius": 450,
      "maxRegionRadius": 1400,
      "minRegionSpacing": 256,
      "transitionNoiseStrength": 64,
      "betweenRegionsMode": "BIOME",
      "betweenRegionSelectors": ["#minecraft:is_ocean"],
      "profileSequence": ["temperate"],
      "progressionZonesEnabled": false,
      "progressionZones": {
        "inner": {
          "distanceMode": "NORMALIZED_WORLD_DISTANCE",
          "minDistance": 0,
          "maxDistance": 0.5
        }
      },
      "maxPlannerAttempts": 20000,
      "lockLayoutAfterWorldCreation": true
    },
    "minecraft:the_nether": { "enabled": false },
    "minecraft:the_end": { "enabled": false }
  }
}
```

## Separate NeoForge common configuration

NeoForge also creates `config/boundednotfree-common.toml`:

| Parameter | Default | Behavior |
| --- | --- | --- |
| `strictMissingSelectors` | `false` | If true, any collected layout validation problem is fatal. Despite the name, this currently applies to every validation message. |

## 0.x migration cleanup

Version 1.0 removed parsed placeholders that never had production behavior: `rimMode`, `preferredDistance`, `distanceFalloff`, rule `weight`, `maxInstances`, `maxArea`, `minDistinctMatches`, structure `allowedBiomes`/`forbiddenBiomes`, profile diversity/weight fields, `requiredProfileCounts`, zone `strategy`/`polygonVertices`, `plannerGridSize`, and the legacy `terrainControlEnabled`/`terrainRules`/`maxTerrainShift` block. Old JSON containing them still loads because unknown fields are ignored, but remove them when updating the file.

The old common-TOML placeholders `lockLayoutAfterWorldCreation` and `writeExampleConfig` were also removed; layout locking is controlled per JSON dimension and the default JSON is always created when missing.

## Validation and inspection

- `/worldlayout validate` reports unresolved selectors, impossible ranges, unavailable rim influence, and required-structure shortfalls.
- `/worldlayout info` reports the current boundary metric, zone, influence factor, and climate target.
- `/worldlayout compat` reports the generator, biome source, climate strategy, provider sample, and detected compatibility mods.
- `/worldlayout preview` writes SVG and JSON planning output under `<world>/boundednotfree-previews/`.

For strict worlds, validate, inspect the preview, and pregenerate a representative boundary section with Chunky before opening the server.
