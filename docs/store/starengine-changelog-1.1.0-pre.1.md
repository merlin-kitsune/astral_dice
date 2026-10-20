First 1.1.0 pre-release.

- Renamed the library to StarEngine: mod id `starengine_lib` became `starengine`, the package became `com.merlinkitsune.starengine`, and the maven artifacts are now `starengine-<platform>`.
- Four platform builds: NeoForge 1.21.1, Forge 1.20.1, NeoForge 26.1.2 and Fabric 1.20.1.
- Moved five utilities in from the consumer: party relations, elite and boss detection, hold-to-select targeting, self-targetable, and the effect timer guard with a `TimerSource` seam.
- Wallet storage keeps reading the old `starengine_lib` NBT key as a fallback, so existing star coin balances survive the rename.
