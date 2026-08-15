# Intentionally deferred work

These items are explicit follow-up work, not silent promises of version 1.0:

- A compatibility-safe floating-island/floating-continent terrain module. `ARCHIPELAGO` currently controls horizontal biome regions only.
- Guaranteed final starts for weighted multi-entry structure sets and custom generators that do not use vanilla structure placement. Reservations currently constrain eligible standard candidates.
- Persisted historical structure counts and stronger exact-count enforcement across restarts/existing chunks.
- True proximity-based `NEAREST_ALLOWED`; the current deterministic allowed-candidate fallback prioritizes safety and reproducibility.
- Polygon progression zones, named anchors, spawn relocation, inter-structure relationship constraints, and unexplored-chunk replan commands.
- Datapack-reload revalidation/hot replanning, richer public callbacks/events, and a client GUI.
- PNG preview and macro-region rendering; the implemented SVG/JSON preview shows the boundary and required-biome reservations.
- Required-profile-count enforcement and full biome diversity quotas.
- Direct configuration of named internal terrain-mod density functions. The current implementation instead discovers climate points and uses provider-native graph/sample influence without version-specific links.
- Representative structure-mod compatibility runs and an older Regions Unexplored/TerraBlender compatibility gate.
