# Astral Dice

**English** | [中文](README_ZH.md)

**A Minecraft multiloader mod: 1.21.1 NeoForge / 1.20.1 Forge / 26.1.2 NeoForge**

> This repository is a multiloader monorepo: three self-contained subprojects — `neoforge-1.21.1/` (the 1.21.1 NeoForge mainline), `forge-1.20.1/` (the 1.20.1 Forge port line) and `neoforge-26.1.2/` (the 26.1.2 NeoForge migration line) — sharing one set of gameplay content. See `docs/multiloader-layout.md`.

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

## What's New in 1.3.2

- **New sign "Mechanic" (Legendary)**: active **Orbital Bombardment** — designate one hostile target, then take cards in the order "hotbar → offhand → inventory" (stopping as soon as the total damage reaches the cap; leftover cards are kept) and bombard the target plus a 12-block area around it; passive **Resupply** hands out cards on a timer.
- **First Adventure Gift**: the **first loot chest** you open in a save always contains 1 die (once per save; not repeated on death/respawn or dimension travel).
- **Custom sound effects**: a batch of new sounds for skill activation, glass dice shattering, bombardment impacts and effect-card use.
- **Balance changes and fixes**: the damage-channel convention for Blessing-related bonuses (Attack Power vs. Attack Damage), the Flashlight's Starlight trigger, card tier retuning, and loot-chest probability corrections — see the [release notes](https://github.com/merlin-kitsune/astral_dice/releases) for the full list.

## Supported Versions / Requirements

| Support | Subproject | Minecraft | Loader | Java | Current Version | Patchouli |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.3.2 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.3.2-hotfix | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.3.2-beta.1 | ✅ |

- `neoforge-1.21.1` and `forge-1.20.1` are the **release lines** (feature-parity pair; both jars are published with every GitHub Release).
- `neoforge-26.1.2` is the **migration line** (since 2026-09-19 it belongs to the mainline and is synchronised at the same level as the other two; since 2026-09-22 it is a **fully supported migration line** rather than experimental client-side support): it **never gets its own tag or Release**, but its jar is shipped as a **third attachment on the release lines' Releases** (CI builds all three lines together, see `.github/workflows/build.yml`). This line has **no** Iron's Spells 'n Spellbooks integration (upstream ships no 26.1.x build).

| Dependency | Requirement |
|---|---|
| Required (**bundled — no separate install**) | **StarEngine Lib** (`starengine_lib`) **is bundled inside this mod since 1.3.1** (currently embedded **`1.0.5`**, compatible range **`[1.0.5,2.0)`**): the loader picks up the embedded copy at startup. ⚠️ Do **not** also drop a standalone `starengine_lib-*.jar` into `mods`: the loader de-duplicates by modId and prefers that copy, so an older one would shadow the bundled library |
| Required | Curios API (Curios 5.x on 1.20.1, Curios 9+ on 1.21.1, **Curios 15+ on 26.1.2**; a missing or too-old Curios is rejected during NeoForge's dependency sorting). ⚠️ This prerequisite is declared **by this mod**; StarEngine Lib itself only uses it as a **compile-time** dependency on the Forge side (it is not in the library's `mods.toml`) |
| Required (1.20.1 only) | **Either Mixin runtime** (since 1.3.2-hotfix): **Mixin Booster ≥ 0.1.3** **or** **Sinytra Connector**. If the pack already ships Sinytra Connector, **nothing extra is needed** (Connector bundles the same Mixin runtime and Mixin Booster deliberately steps aside); only when neither is present does the mod refuse to start and explain how to install one. An outdated Mixin Booster is still rejected |
| Optional | Bountiful, Patchouli |

---

## Download

- **Releases page**: [GitHub Releases](https://github.com/merlin-kitsune/astral_dice/releases) (each release carries **three** jars: the two release lines plus 26.1.2's `-beta` jar)
- Supported platforms: Minecraft 1.21.1 / NeoForge, 1.20.1 / Forge (release lines); Minecraft 26.1.2 / NeoForge (migration line — no tag or Release of its own; its jar ships with the release lines' Releases)
- Requirements: Curios API (plus a Mixin runtime on 1.20.1: Mixin Booster ≥ 0.1.3 **or** Sinytra Connector). **StarEngine Lib is bundled inside the mod — no separate install needed**
- Build artefacts: `neoforge-1.21.1/build/libs/astral_dice-<version>+neoforge_1.21.1.jar`, `forge-1.20.1/build/libs/astral_dice-<version>+forge_1.20.1.jar`, `neoforge-26.1.2/build/libs/astral_dice-<version>+neoforge_26.1.2.jar`; GitHub Release tags use the bare base version number (e.g. `1.3.2`)

## Build

```bash
./gradlew build                    # build every subproject (all three lines)
./gradlew :neoforge-1.21.1:build   # 1.21.1 NeoForge only
./gradlew :forge-1.20.1:build      # 1.20.1 Forge only
./gradlew :neoforge-26.1.2:build   # 26.1.2 NeoForge only (migration line)
```

- **Hard rule for mod dependency sources**: every third-party mod (front-ends included) may only come from **Curse Maven** (`curse.maven:`) or **Modrinth Maven** (`maven.modrinth:`), enforced at dependency-resolution time by `exclusiveContent` in `build.gradle` and statically gated by `tools/check_mod_sources.ps1` (details in `AGENTS.md`). Third-party mod jars are **never committed** (the `base-mod-*` directory is excluded in `.gitignore`).
