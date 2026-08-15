# Public API

`WorldLayoutApi` is a read-only, server-side query surface. It never mutates planning state.

```java
WorldLayoutApi.get(serverLevel).ifPresent(layout -> {
    boolean permitted = layout.contains(x, z);
    DistanceMetric distance = layout.measure(x, z);
    double normalized = distance.normalizedFromCenter();
    double edgeBlocks = distance.blocksToEdge();
    Optional<String> zone = layout.progressionZone(x, z);
});
```

`View` also exposes `dimension()`, `boundaryType()`, and `layoutSeed()`. Calls are safe from world-generation workers because active plans are immutable after installation and are held in concurrent maps. Query only a live `ServerLevel`; cache values, not a `View`, across server shutdowns.

There is no event/callback registration API in 1.0. Add-ons should use the query API and standard NeoForge lifecycle/worldgen events. A richer reservation event surface remains listed in `TODO.md` rather than being claimed as implemented.
