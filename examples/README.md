# Configuration examples

Most files are valid JSON fragments for one entry inside the root `dimensions` object. Merge the chosen fragment under `"minecraft:overworld"` and add referenced groups/profiles at the root. `mountain-terrain-rim.json` includes both its root biome group and dimension fragment so the shared selector is explicit.

- `simple-circle.json`: small compatible vanilla-layout world.
- `mountain-rim-void.json`: stony rim and void exterior.
- `mountain-terrain-rim.json`: provider-native mountain terrain influence on the inner 256 blocks of the rim, with optional common mod tags.
- `continental-profiles.json`: deterministic woodland/grassland continents.
- `star-progression.json`: star boundary with inner/outer zones.
- `custom-polygon.json`: normalized arbitrary polygon.
- `required-content.json`: required deep-dark region and ruined portal.

The same keys can be composed to create the requested frozen-continent, desert-continent, archipelago/floating-layout, biome-blacklist, boss-structure, and climate-band scenarios. Define selectors with mod IDs/tags rather than hardcoding compatibility code.
