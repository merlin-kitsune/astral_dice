# StarEngine

Shared engine library for the Astral Dice mod family.

**Version 1.1.0-pre.2** ships four platform builds: NeoForge 1.21.1, Forge 1.20.1, NeoForge 26.1.2 and Fabric 1.20.1.

## Do you need to install this?

| Astral Dice build | Install StarEngine separately? |
|---|---|
| NeoForge 1.21.1 | Yes. Since 2026-10-20 this build no longer embeds the library. |
| Forge 1.20.1 | Yes, same rule. |
| NeoForge 26.1.2 | No. The library is still embedded. |
| Fabric 1.20.1 | No. The library is still embedded. |

For the two builds that need it, drop `starengine-neoforge-1.21.1-1.1.0-pre.2.jar` or `starengine-forge-1.20.1-1.1.0-pre.2.jar` into `mods` next to Astral Dice. The version has to match the one the mod was compiled against. A missing library stops the game with "Missing or unsupported mandatory dependencies".

## What is inside

- Dice battle resolution and spell damage classification.
- Sign hook dispatch and active-skill locking.
- Chip effect helpers.
- Star coin wallet storage, with NBT migration from the old `starengine_lib` key.
- Team relations for FTB Teams and OpenPAC, elite and boss detection, hold-to-select targeting.
- A timer seam that the consuming mod implements.

The library registers no items, blocks or effects. Every registry entry stays in the consuming mod, so upgrading the library never changes the owner of a `ResourceLocation` and cannot break saves or data packs.

## Compatibility

| Build | Minecraft | Loader | Java |
|---|---|---|---|
| `starengine-neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 |
| `starengine-forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 |
| `starengine-neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 |
| `starengine-fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 | 17 |

Curios and Trinkets are compile-time dependencies of the library only. They are not declared as library requirements, because the consuming mod owns that decision.

## Where to get builds

- CurseForge carries the platform jars.
- The GitHub repository serves source only. Its Releases hold source archives and no jars.

## Building from source

```bash
./gradlew build                                  # all four platforms
./gradlew :neoforge-1.21.1:publishToMavenLocal   # local maven for a consumer build
```

The shared source lives in `common/src/main/java` and is compiled once per platform. Each platform subproject adds that directory to its own source set.

## Licence

Source is published in the repository. Third-party assets keep their own licences and are listed in the consuming mod.
