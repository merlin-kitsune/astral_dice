# Astral Dice 1.5.0-rc.7 — Player Changelog

Released: **1.5.0-rc.7 (pre-release / beta)** — a **fix-only** update on top of the previous pre-release: gameplay and content are unchanged, and exactly one server-killing bug is fixed:

- **Server crash fixed**: mobs whose attribute table has no attack-damage attribute at all (the Twilight Forest Hydra being the classic case) crashed the whole server the moment they landed a melee hit on a player, throwing `IllegalArgumentException: Can't find attribute minecraft:generic.attack_damage` followed by a `Ticking entity` crash; after a restart one more hit brought it back, so the world became a crash loop you could not log into. They no longer crash, and their attacks still take part in dice-battle settlement.
- **Ordinary mobs unchanged**: zombies and the like deal exactly the damage earlier pre-releases did, so nothing about the feel has to be relearned.
- **Verified on a real game (NeoForge 1.21.1 only)**: the original crash was reproduced first and the fix verified after; the control readings on ordinary mobs match the pre-fix build.

> **This pre-release ships two builds only**: `astral_dice-1.5.0-rc.7+neoforge_1.21.1.jar` (Minecraft 1.21.1 / NeoForge)
> and `astral_dice-1.5.0-rc.7+forge_1.20.1.jar` (Minecraft 1.20.1 / Forge). Other platform lines get no build in this version.

## Bug fixes

- **A mob's melee hit on a player took the whole server down (`Ticking entity`)**: some mobs have **no attack-damage attribute at all** in their attribute table (`minecraft:generic.attack_damage`) — their damage is hard-wired in script and never reads that table, which is how the Twilight Forest Hydra (`twilightforest:hydra`) strikes.
  The moment such a mob landed a melee hit on a player, the old code read that missing attribute unconditionally and threw
  `IllegalArgumentException: Can't find attribute minecraft:generic.attack_damage`; the server died with `Ticking entity`,
  and **one more hit after a restart killed it again**, so the world became a crash loop (`Description: Ticking entity`, no way in).
  Now the attribute is simply not read when it is absent: **the damage that hit actually dealt** is used as the mob's attack value
  for dice-battle settlement instead — the crash is gone, and its damage still counts at the amount really dealt, never swallowed to 0.
- **Ordinary mobs are point-for-point unchanged**: mobs that do have the attribute go through the old path, so dice-battle results match the previous build (zombie-class damage readings are identical).
- **Verified on a real game (difficulty pinned to normal, `neoforge-1.21.1` only)**: under the same conditions a hydra hit took **46** HP, while the control zombie in the same batch still took **5** — so the fix did not also buff or nerf ordinary mobs.

## Known issues and design notes

- **The fix has only been verified on a real game for `neoforge-1.21.1`**: `forge-1.20.1`, `neoforge-26.1.2` and `fabric-1.20.1` have **no in-game verification** (the source is synced to the same rule and compiles, but there is no reproduced crash and no control reading).
- **The two P1 lines are not updated in this version**: `neoforge-26.1.2` and `fabric-1.20.1` get no build here and stay at their previous state for players; this version delivers the two P0 lines only (`neoforge-1.21.1` / `forge-1.20.1`).
- **Known deviation on 1.20.1**: against a player carrying **damage absorption (yellow hearts)**, these attribute-less mobs' attack value is **underestimated by one step** — the 1.20.1 settlement hook cannot reach the pre-armor original damage, and the value available has already paid absorption once. That is a platform limit (recorded), not a choice of reading we can fix. The ordinary case (no absorption) is correct.
- **This is still a pre-release (beta)**: features and numbers match the previous pre-release; numbers are still moving, and feedback is welcome.

## Installation and requirements (read first)

- **`StarEngine` must be installed alongside this mod** (NeoForge 1.21.1 / Forge 1.20.1): the shipped jar does **not** bundle the library (no `META-INF/jarjar/` inside),
  so `mods` needs the matching `starengine-<loader>-1.1.0.jar`. The mod's **hard dependency range is `[1.1.0, 2.0.0)`**:
  anything below 1.1.0 or reaching 2.0.0 is rejected. A missing or mismatched library refuses to start with “Missing or unsupported mandatory dependencies” — if you see that line, check whether the library jar is missing or the version does not match.
- Mixin runtime for 1.20.1: **Mixin Booster ≥0.1.3** or **Sinytra Connector** — either one, not both.
- **No config edits and no world migration**: this version only changes one value lookup in combat settlement; config keys and the save format are untouched, so existing worlds load as they are.
- **If the crash loop already locked you out of a world**: just upgrade — no entity deletion and no new world needed, and that mob can no longer take the server down.
- Two P0 delivery lines: **`neoforge-1.21.1`** and **`forge-1.20.1`**; the two P1 lines get no build in this version.

## Feedback

- Report problems in the repository's issue tracker: [merlin-kitsune/astral_dice](https://github.com/merlin-kitsune/astral_dice/issues);
  builds and older versions are on the [Releases](https://github.com/merlin-kitsune/astral_dice/releases) page.
- For crash reports please include the **version** (e.g. `1.5.0-rc.7`), the **loader + Minecraft version**, the matching file from `crash-reports`,
  and what you were doing right before it crashed; if `Can't find attribute` appears anywhere in the log, paste that too.
- Balance and feel feedback is easiest to act on with a concrete scene (which mob, which gear, what number you expected) rather than just “too strong / too weak”.

---

Chinese version: `PLAYER_CHANGELOG_ZH.md` in this folder (the GitHub Release body shows both side by side).
