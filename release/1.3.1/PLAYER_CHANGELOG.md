# Astral Dice 1.3.1 Changelog (Player Edition)

> Scope: **1.3.0 → 1.3.1** (this is an incremental note; see `release/1.3.0/` for the full 1.3.0 content). Applies to both 1.21.1 (NeoForge) and 1.20.1 (Forge). Version suffixes: `+neoforge_1.21.1` / `+forge_1.20.1`.
> The GitHub Release additionally ships a build of the third line, **26.1.2 (NeoForge)**, suffixed `+neoforge_26.1.2` (its version carries `-beta`; feature parity with the release lines).
> This file is the player-facing release note and corresponds one-to-one with the player-visible sections of **1.3.1** in `CHANGELOG.md` (engineering / tooling entries excluded).

**In one sentence**: the "hostile target" criterion is relaxed — a neutral mob no longer has to be provoked first (**an untamed wolf now counts as a hostile target outright**); the Piercing Gun no longer requires "a damage effect card has been used", so plain arrows get the bonus too; and the training dummy can finally be selected and hit.

> ⚠️ **This release needs StarEngine Lib `1.0.3`+** — see "📌 Requirements" at the end.

---

## ⚖️ Balance & Quality-of-Life

### Hostile targets relaxed: neutral mobs no longer need to be provoked first

- **The "hostile target" criterion is rewritten to "hostile mobs ∪ neutral mobs (pets excluded)"**: previously a neutral mob had to be **angered** to count — so an unangered wolf, iron golem, polar bear or bee simply "was not hostile" in your eyes until you **let it hit you first** and it entered its 20–39 s anger state. It now reads **every neutral mob counts, only tamed pets are excluded**.
- ⚠️ **Measured impact**: the only vanilla mob that is both a neutral mob and tameable is the **wolf**, so this entry is effectively "**an untamed wolf now counts as a hostile target, a tamed one does not**".
- The criterion is **global**: the single entry point for "hostile target" lives in the prerequisite library `StarEngine Lib`, so **every** effect that depends on it follows suit — Dice Blessing triggering, spell-damage bonuses, the target selector's selectability checks (crosshair filter / radius highlight / server-side confirmation), railgun lightning target selection, and more.

### Piercing Gun: no longer requires "a damage effect card has been used"

- **The Piercing Gun drops its "while a damage effect card is active" prerequisite**: it used to additionally require an active damage effect card (one of Monster Laser / Monster Brick / Orbital Strike / Directional Blast, or the hit itself being the Living Page's spell damage), which meant **plain arrows, thrown projectiles and linked-mod spells** never received the bonus. Now it simply reads: **wearing the Piercing Gun and dealing ranged/magic damage to a hostile target grants bonus damage equal to the target's defence points**.
- ⚠️ The one difference from the Ninja Star: the Piercing Gun **keeps** the "the target must be a hostile target" range check — its bonus is taken from the target's defence points, which is meaningless against passive animals, teammates or yourself.
- The item description and handbook entry had that prerequisite removed to match.

## 🐛 Bug Fixes

### The training dummy can now be selected and hit

- **The training dummy (`dummmmmmy`) is now always treated as a hostile target**: it is neither a hostile mob nor an angerable neutral mob, so every "requires a hostile target" effect excluded it — most visibly, the **Living Page could not select it at all, and therefore could not hit it** (you could not even use it to test your damage). This is now fixed at the root: the training dummy counts as hostile in **all** "hostile target" checks — Dice Blessing triggers normally, spell-damage bonuses are applied normally, and the target selector picks it up normally.

## 📌 Requirements

- ⚠️ **StarEngine Lib is a required dependency from 1.3.0 onward**: **releases up to and including 1.2.1-hotfix ran on their own**; from 1.3.0 a large part of this mod's shared implementation lives in that library, and **without it the mod is refused at load time** (the loader reports a missing required dependency instead of letting you into the game and crashing there). Library repository: <https://github.com/merlin-kitsune/starengine_lib>
- **This release requires StarEngine Lib `1.0.3` or any higher `1.x` version** (the dependency range this mod declares is `[1.0.3,2.0)`); **update the library and the mod as a pair** — put the library jar released together with this mod version into the `mods` folder as well. ⚠️ Library versions `1.0.2` and `1.0.1` were **never published** (the content of `1.0.2` was merged into `1.0.3`).
- **Other requirements**: Curios API (**1.20.1 additionally requires Mixin Booster**).
- **26.1.2 line**: requires Curios API **15+**; it has **no Iron's Spells 'n Spellbooks integration**.
- **Optional integrations**: Patchouli (guidebook), Bountiful.
- **Version gate (multiplayer)**: the client and server must share the same **major.minor version** (`1.3.x` ↔ `1.3.y` interoperate). Cross-version connections are **refused with a clear message** rather than failing silently.
