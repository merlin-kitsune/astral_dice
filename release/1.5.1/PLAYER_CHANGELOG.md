# Astral Dice 1.5.1 — Player Changelog

Released: **1.5.1 (stable)** — a **fix-only** update that repairs exactly one crash: gameplay, content and numbers are unchanged, and saves and multiplayer keep working.

- **Server crash fixed**: when a meito card ran out of durability and its cost was settled, a player who was **not wearing the "Misaki" guardian sign** made the server crash, because the cost calculation had no player context; the save died at `Ticking player`, and one more trigger after a restart killed it again. It no longer crashes, and the cost is settled as usual.
- **Compatibility**: works with 1.5.0 saves — no migration needed. The interop version is still **1.5**, so 1.5.1 and 1.5.0 players can play together.

> **This release ships four builds**: `astral_dice-1.5.1+neoforge_1.21.1.jar` (Minecraft 1.21.1 / NeoForge), `astral_dice-1.5.1+forge_1.20.1.jar` (Minecraft 1.20.1 / Forge),
> `astral_dice-1.5.1+neoforge_26.1.2.jar` (Minecraft 26.1.2 / NeoForge) and `astral_dice-1.5.1+fabric_1.20.1.jar` (Minecraft 1.20.1 / Fabric). All four belong to one Release, tagged `1.5.1`.

## Bug fixes

- **Crash when a meito card ran out of durability (`Ticking player`)**: a meito card losing its last durability point triggers one cost settlement. If the player was **not wearing the "Misaki" guardian sign**, the old code computed the cost without passing the player into the curios lookup, so the server threw and the save died at `Ticking player`; one more trigger after a restart brought it back.
  This version threads the real player through cost settlement and the curios lookup, and guards the lookup with "do not query when there is no player". The 4 → 3 discount while wearing "Misaki" and the charge refund both use the same cost path and produce the same results as before.
- **Nothing else changes**: card costs, durability, discount conditions and charge refunds are numerically untouched.

## Installation and requirements (read first)

- **`StarEngine` is still a separate install** (same on all four lines, version **1.1.0**): the shipped jar does not bundle the library, so put the matching `starengine-<loader>-1.1.0.jar` into `mods`.
- Mixin runtime for 1.20.1: **Mixin Booster ≥0.1.3** or **Sinytra Connector** — either one, not both.
- **No config edits and no world migration**: this version only changes one value lookup in cost settlement; config keys and the save format are untouched, so existing worlds load as they are.

## Feedback

- Report problems in the repository's issue tracker: [merlin-kitsune/astral_dice](https://github.com/merlin-kitsune/astral_dice/issues);
  builds and older versions are on the [Releases](https://github.com/merlin-kitsune/astral_dice/releases) page.
- For crash reports please include the **version** (e.g. `1.5.1`), the **loader + Minecraft version**, the matching file from `crash-reports`, and what you were doing right before it crashed.

---

Chinese version: `PLAYER_CHANGELOG_ZH.md` in this folder (the GitHub Release body shows both side by side).
