# Astral Dice

**English** | [中文](README_ZH.md)

**A Minecraft multiloader mod: 1.21.1 NeoForge / 1.20.1 Forge / 26.1.2 NeoForge / 1.20.1 Fabric (port line)**

> This repository is a multiloader monorepo: four self-contained subprojects — `neoforge-1.21.1/` (the 1.21.1 NeoForge mainline), `forge-1.20.1/` (the 1.20.1 Forge port line), `neoforge-26.1.2/` (the 26.1.2 NeoForge migration line) and `fabric-1.20.1/` (the 1.20.1 **Fabric port line**, merged into `multi-main` as the fourth line on 2026-10-01) — sharing one set of gameplay content. See `docs/multiloader-layout.md`.

Astral Dice is a survival expansion mod built around dice. Equip a dice and every melee attack becomes a roll of fate — trigger the **Dice Blessing** and enter a dice-battle system where attack clashes against defense.

---

## Core Features

- **Dice · 13 kinds**: upgrade step by step from the Basic Dice up to the **Nether Star Dice** (Golden / Glass / Netherrack → Diamond / Emerald / Obsidian / Weird / Amethyst → Netherite / Crimson / Ender → Nether Star). Star upgrades (0–3★) unlock more cost points and card slots (0★=4 / 1★=6 / 2★=8 / 3★=12, split evenly between attack and defense).
- **Battle Cards**: Attack and defense cards are inserted into the dice card inventory. They grant random bonus points during Dice Blessings, and each card has its own durability.
- **Effect Cards**: King's Power, Berserk, Unwavering, Fight Poison with Poison, Monster Laser, Orbital Strike, Living Page, Fate's Guidance and many more — each with its own play-count and cooldown cycle.
- **Signs · 24 characters**: each with a unique passive ability and an active skill — Business, Sweeper, Guardian, Ninja, Vampire, Fengshui Master, Cult Leader, Oasis Queen, Dragon, Mechanic and more.
- **Chips · 61 pieces**: passive curios such as Boxing Gloves, Speed Skates, Moto Helmet, Medkit, Magic Quiver, Star Coin Hammer, Cutter and Flashlight, providing attack, defense, movement speed, health, starlight and other bonuses.
- **Player Resources**: Healing Points, Starlight, Mark stacks and Charge stacks form four player resource systems that work together with signs and chips.

## Highlights

- Dice Blessing attack/defense showdown
- Spell Damage system compatible with bows, crossbows, tridents, magic, and many magic mods
- Card selection GUI with star-based card slots (0★=4 / 1★=6 / 2★=8 / 3★=12, split evenly between attack and defense) and real-time stat previews
- Event system featuring Detective, Investigator, and Secret Detective signs
- Bountiful bounty board integration

## What's New in 1.3.3

- **Nine balance and skill adjustments**: the Fengshui Master's "Perfect Helper" now fills the target's Recharged Energy straight to 5 stacks; the "Baize's Blessing" / "Divine Descent" 2:00 countdown now starts on any landed attack (previously melee only); the Cult Leader's Fox Light bonus became a separate "Pursuit" extra damage instance; the Detective's Investigation Phase swaps the vanilla Invisibility for a new "Concealment" effect; Destiny's Guidance gains "your combat dice roll is always 6"; Unwavering and Charge card wording trimmed; the Slime Sign loses the area healing on its active; Big Bowl of Stew no longer heals friendly creatures and its healing points go from 1 to 2.
- **Four "on being hit" effects now require a hostile attack**: Buffer Shield, the Mouse Shield counter, the Slime Sign's "Cell Division" and the Vampire Sign's "Drain" used to trigger on environmental damage (fall / cactus / fire) and self-inflicted damage too, which made them farmable; only a hostile attack counts now.
- **Action bar fixes**: two missing messages restored; the "yellow front half, grey back half" seam caused by 11 messages carrying their own colour codes removed; refusal messages unified to red; the airbag / wallet / card inventory / client-side "out of plays" feedback moved from the vanilla overlay back onto the mod's own action bar.
- Full list: see the [release notes on the Releases page](https://github.com/merlin-kitsune/astral_dice/releases).
## Supported Versions / Requirements

| Support | Subproject | Minecraft | Loader | Java | Current Version | Patchouli |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.3.6 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.3.6 | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.3.6-beta.1 | ✅ |
| ✅ | `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 | 17 | 1.3.6-alpha.1 | ✅ |

- `neoforge-1.21.1` and `forge-1.20.1` are the **release lines** (feature-parity pair; both jars are published with every GitHub Release).
- `neoforge-26.1.2` is the **migration line** (since 2026-09-19 it belongs to the mainline and is synchronised at the same level as the other two; since 2026-09-22 it is a **fully supported migration line** rather than experimental client-side support): it **never gets its own tag or Release**, but its jar is shipped as a **third attachment on the release lines' Releases** (CI builds all three lines together, see `.github/workflows/build.yml`). This line has **no** Iron's Spells 'n Spellbooks integration (upstream ships no 26.1.x build).
- `fabric-1.20.1` is the **Fabric port line** (added 2026-09-29; merged into `multi-main` as the fourth subproject on 2026-10-01, its standalone branch and worktree removed). It keeps an **independent version number carrying an `-alpha.x` pre-release suffix** (currently `1.3.6-alpha.1`) — the same arrangement as 26.1.2's `-beta.x`, i.e. a pre-release line that never occupies the release lines' bare version. It is published as a **separate `fabric-<version>` pre-release** and its jar is **never** bundled into the release lines' Release. ⚠️ Its prerequisites are a **different set** (see below) and it has **no** Curios / Bountiful / Iron's Spells 'n Spellbooks integration.

| Dependency | Requirement |
|---|---|
| Required (**bundled — no separate install**) | **StarEngine Lib** (`starengine_lib`) **is bundled inside this mod since 1.3.1** (currently embedded **`1.0.7`** on the three release/migration lines and **`1.0.6-alpha.2`** on the Fabric line; compatible ranges **`[1.0.7,2.0)`** / **`>=1.0.6-alpha.2 <2.0`**): the loader picks up the embedded copy at startup. ⚠️ Do **not** also drop a standalone `starengine_lib-*.jar` into `mods`: the loader de-duplicates by modId and prefers that copy, so an older one would shadow the bundled library. ⚠️ The library's own repository **publishes no jars at all** (source only, since 2026-10-01) — there is nothing to download there in the first place |
| Required | Curios API (Curios 5.x on 1.20.1, Curios 9+ on 1.21.1, **Curios 15+ on 26.1.2**; a missing or too-old Curios is rejected during NeoForge's dependency sorting). ⚠️ This prerequisite is declared **by this mod**; StarEngine Lib itself only uses it as a **compile-time** dependency on the Forge side (it is not in the library's `mods.toml`) |
| Required (1.20.1 only) | **Either Mixin runtime** (since 1.3.2-hotfix): **Mixin Booster ≥ 0.1.3** **or** **Sinytra Connector**. If the pack already ships Sinytra Connector, **nothing extra is needed** (Connector bundles the same Mixin runtime and Mixin Booster deliberately steps aside); only when neither is present does the mod refuse to start and explain how to install one. An outdated Mixin Booster is still rejected |
| Required (fabric-1.20.1 only) | **A different set** — see **[Fabric (1.20.1) prerequisites](#fabric-1201-prerequisites)** below: **Fabric Loader 0.19.x + Fabric API** (1.20.1 line, 0.92.12) + **Puzzles Lib 8.1.33** (+ its **Forge Config API Port 8.0.3**), plus **Trinkets 3.7.2** _or_ **Accessories 1.0.0-beta.48** (either one). The release lines' Curios / Mixin Booster chain does **not** apply here |
| Optional (fabric-1.20.1) | Patchouli. (**No** Bountiful on this line — no 1.20.1 Fabric build exists) |
| Optional | Bountiful, Patchouli |

### Fabric (1.20.1) prerequisites

The Fabric port line uses a **different prerequisite set** — the release lines' Curios / Mixin Booster chain does **not** apply to it:

| # | Requirement | Version / notes |
|---|---|---|
| 1 | **Fabric Loader** | **0.19.5+** (0.19.x) |
| 2 | **Fabric API** | the **1.20.1** line, **0.92.12** |
| 3 | **Puzzles Lib** (event bridge) | **8.1.33+** — install it together with its own prerequisite below |
| 4 | **Forge Config API Port** | **8.0.3** (required by Puzzles Lib 8.x) |
| 5 | **Accessory provider** — *either one* | **Trinkets 3.7.2+** _or_ **Accessories 1.0.0-beta.48+** |
| 6 | Optional | **Patchouli** (the in-game guide) |
| — | **Not applicable on this line** | Curios API, Mixin Booster / Sinytra Connector, Bountiful, Iron's Spells 'n Spellbooks |

- The accessory provider is an **either-or**: Fabric's `depends` is AND-only and cannot express OR, so Trinkets and Accessories are both declared as `recommends`; the mod verifies at startup itself and refuses to start **only when neither** is present.
- **Store listings**: on **both** Modrinth and CurseForge this line declares **Trinkets** as a *required* dependency and **Accessories** as an *optional* one. That is a store-page declaration only — the mod's own `fabric.mod.json` keeps both in `recommends`, so an Accessories-only setup still starts normally.

---

## Download

- **Releases page**: [GitHub Releases](https://github.com/merlin-kitsune/astral_dice/releases) (each release carries **three** jars: the two release lines plus 26.1.2's `-beta` jar)
- **Fabric port line**: published on its **own pre-release** (`fabric-<version>`, marked as a GitHub pre-release and never "latest") carrying **only** its own jar — deliberately kept out of the release lines' Release so it cannot be mistaken for a stable build
- **Modrinth**: the same four builds are published to the main project (id `5xDtrJ8X`), and the bundled prerequisite library **StarEngine Lib** is published to **its own** project (id `2dIXA5wO`) — the library is never mixed into the mod's project. Modrinth version numbers keep the `+<loader>_<MC>` suffix (e.g. `1.3.6+neoforge_1.21.1`) so the four lines stay distinguishable, and the release channel follows the version suffix (`-alpha` → alpha, `-beta` → beta, otherwise release). ⚠️ The library project's jars are published so that the `embedded` dependency reference is resolvable — **do not install them**: the library is already bundled inside the mod and the loader de-duplicates by modId, so a standalone copy would shadow the bundled one
- Supported platforms: Minecraft 1.21.1 / NeoForge, 1.20.1 / Forge (release lines); Minecraft 26.1.2 / NeoForge (migration line — no tag or Release of its own; its jar ships with the release lines' Releases); Minecraft 1.20.1 / Fabric (port line — separate pre-release, never bundled with the release lines' Release)
- Requirements: Curios API (plus a Mixin runtime on 1.20.1: Mixin Booster ≥ 0.1.3 **or** Sinytra Connector). **StarEngine Lib is bundled inside the mod — no separate install needed**. The library repository itself ships **source only** (no jar downloads). On the **Fabric** line the requirements are a **different set** — **Fabric Loader 0.19.x + Fabric API + Puzzles Lib (+ Forge Config API Port)** plus **either Trinkets or Accessories**; Curios and the Mixin runtime do **not** apply there (see *Fabric (1.20.1) prerequisites* above)
- Build artefacts: `neoforge-1.21.1/build/libs/astral_dice-<version>+neoforge_1.21.1.jar`, `forge-1.20.1/build/libs/astral_dice-<version>+forge_1.20.1.jar`, `neoforge-26.1.2/build/libs/astral_dice-<version>+neoforge_26.1.2.jar`, `fabric-1.20.1/build/libs/astral_dice-<version>+fabric_1.20.1.jar`; GitHub Release tags use the bare base version number (e.g. `1.3.5`), while the Fabric port line uses a `fabric-<version>` pre-release tag

## Build

```bash
./gradlew build                    # build every subproject (all four lines)
./gradlew :neoforge-1.21.1:build   # 1.21.1 NeoForge only
./gradlew :forge-1.20.1:build      # 1.20.1 Forge only
./gradlew :neoforge-26.1.2:build   # 26.1.2 NeoForge only (migration line)
./gradlew :fabric-1.20.1:build     # 1.20.1 Fabric only (port line)
```

- **Hard rule for mod dependency sources**: every third-party mod (front-ends included) may only come from **Curse Maven** (`curse.maven:`) or **Modrinth Maven** (`maven.modrinth:`), enforced at dependency-resolution time by `exclusiveContent` in `build.gradle` and statically gated by `tools/check_mod_sources.ps1` (details in `AGENTS.md`). Third-party mod jars are **never committed** (the `base-mod-*` directory is excluded in `.gitignore`).
