# Roadmap / known limitations

These are current limitations or ideas that are intentionally outside the implemented feature set.

- A dedicated floating-island / floating-continent terrain mode. `ARCHIPELAGO` currently controls horizontal biome regions rather than replacing the terrain generator with islands.
- Stronger guarantees for weighted multi-entry structure sets and custom structure systems that bypass vanilla placement.
- Persisted historical structure counts for stricter exact-count planning across restarts and already-generated terrain.
- A true proximity-based `NEAREST_ALLOWED` selector. The current fallback is deterministic but does not perform a global nearest-biome search.
- More progression-zone shapes and tools, including polygon zones, named anchors, spawn relocation, and inter-structure relationship rules.
- Replanning tools for unexplored terrain after datapack/config changes.
- More public callbacks/events and an optional client configuration/preview UI.
- PNG/macro-region preview output in addition to the current SVG/JSON preview.
- Stronger biome-diversity / required-profile quota controls.
- Optional direct hooks for named internal density functions in specific terrain mods. The current approach prefers provider-native climate/parameter discovery so it does not hardcode private function names for every supported version.

If one of these becomes part of a release, its behavior should be documented in the normal configuration/compatibility docs rather than treated as an implied promise here.
