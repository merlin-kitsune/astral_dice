# StarEngine Lib 1.0.6

Shared prerequisite library embedded (JarJar) in **Astral Dice**. This release fixes the friendly-target
collection on the FTB Teams and OPAC backends, and renumbers the Fabric sub-project to match the others.

> You do **not** need to install this library by hand — every Astral Dice jar already ships it embedded.
> Installing a standalone copy only makes sense if you are building against it or debugging it.

## Fixes

- **Both third-party backends in `event/EventTargetCollector` were permanently disabled and completely
  silent.** The two reflection targets simply did not exist, and the outermost `catch (Exception ignored)`
  swallowed every `NoSuchMethodException` / `ClassNotFoundException`. Players running FTB Teams or OPAC were
  therefore classified as "in no team at all" and fell into the "everyone on the server counts as friendly"
  fallback, so **team detection was entirely broken**. Corrected item by item against the upstream artifacts:
  - FTB Teams: the four manager accessors are declared on the **nested interface** `FTBTeamsAPI$API`, which
    `Class#getMethod` does not reach from the outer class. The real server-side accessor is
    `TeamManager#getTeamForPlayerID(UUID)`; the client side is
    `ClientTeamManager#getKnownPlayer(UUID)` + `KnownClientPlayer#teamId()` (a record accessor, **no `get`
    prefix**).
  - OPAC: the real package path is `xaero.pac.*`. Correct chain: `OpenPACServerAPI.get(MinecraftServer)` ->
    `getPartyManager()` -> `IPartyManagerAPI#getPartyByMember(UUID)`, and the member accessor is
    `IServerPartyAPI#getOnlineMemberStream()`.

## Changes

- **Fabric sub-project renumbered `1.0.5-alpha.2` -> `1.0.6-alpha.1`** so that its major version matches the
  other three platforms, keeping the `-alpha.N` pre-release suffix. **No bytecode change whatsoever** — all
  four platforms build from the same `common` sources.
- The three platforms go `1.0.5` -> `1.0.6`.

## Artifacts

| File | Platform | Minecraft |
| --- | --- | --- |
| `starengine_lib-neoforge-1.21.1-1.0.6.jar` | NeoForge | 1.21.1 |
| `starengine_lib-forge-1.20.1-1.0.6.jar` | Forge | 1.20.1 |
| `starengine_lib-neoforge-26.1.2-1.0.6.jar` | NeoForge | 26.1.2 |
| `starengine_lib-fabric-1.20.1-1.0.6-alpha.1.jar` | Fabric | 1.20.1 |

## Version note

The fabric sub-project advances on its own pre-release number (`1.0.6-alpha.1`), while the three other
platforms use the release number `1.0.6`. The bare `1.0.6` / `1.0.7` / `1.0.8` jars this sub-project once
produced are **local throwaway builds, never an official number**, and are no longer distributed.
