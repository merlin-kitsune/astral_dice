# Astral Dice

**English** | [中文](README_ZH.md)

**A Minecraft multiloader mod: 1.21.1 NeoForge, 1.20.1 Forge, 26.1.2 NeoForge, 1.20.1 Fabric**

> This repository is a multiloader monorepo. Four self-contained subprojects share one set of gameplay content: `neoforge-1.21.1/` (1.21.1 NeoForge mainline), `forge-1.20.1/` (1.20.1 Forge port), `neoforge-26.1.2/` (26.1.2 NeoForge migration) and `fabric-1.20.1/` (1.20.1 Fabric port). Layout details in `docs/multiloader-layout.md`.

Astral Dice is a survival expansion built around dice. Equip a dice and every melee attack rolls for a **Dice Blessing**, which starts a dice battle: your attack roll is checked against the target's defense roll.

---

## Core features

- **Dice, 13 kinds.** Upgrade from the Basic Dice up to the Nether Star Dice: Golden / Glass / Netherrack, then Diamond / Emerald / Obsidian / Weird / Amethyst, then Netherite / Crimson / Ender, and finally Nether Star. Star upgrades (0 to 3 stars) raise the cost pool and card slots (0★=4, 1★=6, 2★=8, 3★=12, split evenly between attack and defense).
- **Battle cards, 12 kinds.** Attack and defense cards sit in the dice card inventory. They add random points during a Dice Blessing and each one carries its own durability.
- **Effect cards, 19 kinds.** King's Power, Berserk, Unwavering, Fight Poison with Poison, Monster Laser, Orbital Strike, Living Page, Fate's Guidance and others. Each card tracks its own play count and cooldown cycle.
- **Signs, 28 characters.** Every sign has one passive and one active skill. Business, Sweeper, Guardian, Ninja, Vampire, Fengshui Master, Cult Leader, Oasis Queen, Mamushi, Mechanic, Bartender, Dorothy, Sister, Big Fat Fish and more.
- **Chips, 67 pieces.** Passive curios such as Boxing Gloves, Speed Skates, Moto Helmet, Medkit, Magic Quiver, Star Coin Hammer, Cutter and Flashlight. They grant attack, defense, movement speed, health, starlight and other bonuses.
- **Player resources.** Healing points, starlight, mark stacks and charge stacks. All four feed into sign and chip behaviour.

## Highlights

- Dice Blessing attack and defense showdown.
- Spell damage that works with bows, crossbows, tridents and magic mods.
- Card selection GUI with star-based slots (0★=4, 1★=6, 2★=8, 3★=12, split evenly) and live stat previews.
- Sign-hook dispatch: signs register their own hooks instead of hard-coded branches in the combat event handler.
- Bountiful bounty board integration.
- Jade shows defense instead of armor on all four lines.

## What's new in 1.5.0

This section covers everything that shipped across the 1.5.0 pre-release and release-candidate builds. Per-build detail lives in [CHANGELOG.md](CHANGELOG.md).

**New content**

- **Six new signs.** KAngel and Ame (Bizarre quality, with their shared "One Body, One Soul" team note), plus Bartender, Dorothy, Sister and Big Fat Fish.
- **Two new effect cards.** Barricade (a 30-durability visible entity with a fence-height collision block that pulls aggro onto itself) and Reinforced Repulsion (exclusive, 100 durability, clamps hostile targets to a 8-block aura with Slowness and Weakness).
- **New chips.** Flashlight - Burst, Fortune Stick, Reagent - Abyss, Wireless Transmission Pole and Sensor Device.
- **Card Shop and Chip Shop blocks**, each acting as a villager job site, so villagers can become Card Traders and Chip Traders and sell cards or chips by level.
- **Event system rewritten**, with a unified name, icon, type, tier and scope model, per-event timers, and twelve new events (Red-Heat Warning, Swift Stride, Storm Approaching, Learn from Others, Shop Bonanza, Hand Erasure, World Collapse, Gift from the Sky, Angel Descent, Problem Delivery, Crowded Passage, Battle Frenzy). The Strange Question Mark item and the Lottery Box block draw events on demand.
- **New combat stat bars** for Defense and Attack Power, drawn on the vanilla armor bar row.

**Platform and integration**

- **Fabric 1.20.1 build added**, so the mod ships four lines with feature parity.
- **Jade shows this mod's Defense** instead of vanilla armor on all four lines, and shop blocks show a Jade cooldown countdown.
- **Library renamed to StarEngine.** Mod id `starengine_lib` became `starengine`, the package became `com.merlinkitsune.starengine`, and the maven artifacts became `starengine-<platform>`. The repository moved to [merlin-kitsune/StarEngine](https://github.com/merlin-kitsune/StarEngine).
- **Wallet data survives the rename.** Star coins are read from the new `starengine` NBT key with the old `starengine_lib` key as a fallback on every platform. Removing that fallback would reset every player's balance.
- **Version gate.** Builds interoperate when `major.minor` matches. Any build whose version contains `SNAPSHOT` refuses to interoperate with any other build.

**Balance and rules**

- **Cards are traded, not crafted.** All 22 card crafting recipes were removed; cards come from the shops and traders. Exclusive and Pinnacle/Bizarre cards never enter shops or trades.
- **Every star coin payout goes through the wallet**, including Astrologer, Secret Detective, Hanna, Kanban Musume, Gift from the Sky and the detective signpost passive.
- **Shop rules changed.** A 60-second cycle with a fresh batch only at zero, one transfer and one starlight exchange per cycle, and reopening no longer rerolls the shelf. Equipping the Merchant sign or triggering Shop Bonanza fills missing slots instead of rebuilding the shelf.
- **Cooldown rules.** 120 seconds for Business, Gunsmith, Dorothy and Sherry. 180 seconds for the rest. Mamushi locks for 60 seconds and skips the extra cooldown after a lock.
- **Sign internals consolidated.** `SignHookDispatcher` replaced the hard-coded branches in `DiceCombatEvents` (only the Zhao and Teru hooks remain explicit). The 22 separate equip checks collapsed into `SignEquipCheck`, and attribute and effect application now goes through `SignAttributeUtil` and `EffectTimerGuard`.
- **Ten sign attachments merged into one.** `component/SignState` holds a single `sign_state` attachment; the ten old keys stay registered and migrate on login.
- **Five utilities moved into StarEngine.** `PartyRelations`, `EliteTargets`, `HoldToSelect`, `SelfTargetable` and `EffectTimerGuard`; the mod injects a `TimerSource` implementation.

**Presentation and fixes**

- Five new custom sound effects plus one rewritten, and the 179-sprite Iron Elden Ring particle library.
- Event cards animate in, hold and fade out over a single server-timed sequence, and the detective signpost opens an event choice screen with a 15-second countdown.
- Firearm damage is no longer misread as ranged or magic damage. Laser and explosion damage are in the damage table. Living Page stacking is fixed. The Modern UI rarity frame uses the right colour. The Medkit chip no longer refills health on relog or dimension change. Two sign rarities in the guidebook are corrected.
- Forge dev runs need `-Dmixin.config=astral_dice.jade.mixins.json`, because dev does not read `mods.toml`.

## Supported versions

| Support | Subproject | Minecraft | Loader | Java | Version | Patchouli |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.5.0-pre.3 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.5.0-pre.3 | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.5.0-pre.3 | ✅ |
| ✅ | `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 | 17 | 1.5.0-pre.3 | ✅ |

- `neoforge-1.21.1` and `forge-1.20.1` are the release lines. They stay at feature parity, and both jars ship with every GitHub Release.
- `neoforge-26.1.2` is the migration line. It never gets its own tag or Release; its jar ships as a third attachment on the release lines' Release, because CI builds all lines together (`.github/workflows/build.yml`). There is no Iron's Spells 'n Spellbooks integration here, since upstream ships no 26.1.x build.
- `fabric-1.20.1` is the Fabric port line (added 2026-09-29, merged into `multi-main` on 2026-10-01). It publishes as its own pre-release and is never bundled into the release lines' Release. It has its own prerequisite set and no Curios, Bountiful or Iron's Spells integration.

## Dependencies

| Dependency | Requirement |
|---|---|
| Required, install separately | **StarEngine** (mod id `starengine`, currently **1.1.0-pre.2**). On **NeoForge 1.21.1** and **Forge 1.20.1** the library is **no longer bundled**: put `starengine-neoforge-1.21.1-<version>.jar` / `starengine-forge-1.20.1-<version>.jar` into `mods` next to this mod, at the same version this build was compiled against. A missing library fails startup with "Missing or unsupported mandatory dependencies". (The NeoForge 26.1.2 and Fabric 1.20.1 builds still embed it for now.) |
| Required | Curios API: 5.x on 1.20.1, 9+ on 1.21.1, 15+ on 26.1.2. A missing or too-old Curios is rejected during dependency sorting. This requirement is declared by this mod; StarEngine only uses Curios as a compile-time dependency on the Forge side. |
| Required, 1.20.1 only | A Mixin runtime: either **Mixin Booster 0.1.3 or newer**, or **Sinytra Connector**. If the pack already ships Connector, nothing else is needed, because Connector bundles the same runtime and Mixin Booster steps aside. When neither is present the mod refuses to start and explains how to install one. An outdated Mixin Booster is rejected. |
| Required, fabric-1.20.1 only | A different set, listed under [Fabric prerequisites](#fabric-1201-prerequisites). The Curios and Mixin Booster chain does not apply on this line. |
| Optional | Bountiful, Patchouli. |
| Optional, fabric-1.20.1 | Patchouli. There is no 1.20.1 Fabric build of Bountiful. |

### Fabric (1.20.1) prerequisites

| # | Requirement | Version |
|---|---|---|
| 1 | Fabric Loader | 0.19.5+ (0.19.x) |
| 2 | Fabric API | 0.92.12 for 1.20.1 |
| 3 | Puzzles Lib (event bridge) | 8.1.33+, installed together with item 4 |
| 4 | Forge Config API Port | 8.0.3, required by Puzzles Lib 8.x |
| 5 | Accessory provider, either one | Trinkets 3.7.2+ or Accessories 1.0.0-beta.48+ |
| 6 | Optional | Patchouli (in-game guide) |
| n/a | Not applicable on this line | Curios API, Mixin Booster, Sinytra Connector, Bountiful, Iron's Spells 'n Spellbooks |

- The accessory provider is either-or. Fabric's `depends` block is AND-only, so Trinkets and Accessories are both declared under `recommends`, and the mod checks at startup, refusing to launch only when neither is present.
- Store listings on Modrinth and CurseForge declare Trinkets as required and Accessories as optional. That is a store-page declaration only; `fabric.mod.json` keeps both as recommendations, so an Accessories-only setup still starts.

---

## Download

- **GitHub Releases**: [merlin-kitsune/astral_dice/releases](https://github.com/merlin-kitsune/astral_dice/releases). Each release carries three jars: the two release lines plus the 26.1.2 jar.
- **Fabric port line**: published as its own `fabric-<version>` pre-release, marked as a pre-release and never as "latest", carrying only its own jar.
- **Modrinth**: the four builds go to the main project (`5xDtrJ8X`). StarEngine has its own project (`2dIXA5wO`); on **NeoForge 1.21.1** and **Forge 1.20.1** it is no longer bundled, so players must install that separate jar themselves (the 26.1.2 and Fabric builds still embed it). Version numbers keep the `+<loader>_<MC>` suffix (for example `1.5.0-pre.3+neoforge_1.21.1`), and the release channel follows the version suffix.
- **Build artefacts**: `astral_dice-<version>+neoforge_1.21.1.jar`, `+forge_1.20.1.jar`, `+neoforge_26.1.2.jar` and `+fabric_1.20.1.jar`, each under its subproject's `build/libs/`. GitHub Release tags use the bare base version, while the Fabric line uses a `fabric-<version>` pre-release tag.

## Build

```bash
./gradlew build                      # every subproject
./gradlew :neoforge-1.21.1:build     # 1.21.1 NeoForge
./gradlew :forge-1.20.1:build        # 1.20.1 Forge
./gradlew :neoforge-26.1.2:build     # 26.1.2 NeoForge
./gradlew :fabric-1.20.1:build       # 1.20.1 Fabric
```

- Third-party mods (front-ends included) come only from **Curse Maven** (`curse.maven:`) or **Modrinth Maven** (`maven.modrinth:`). `exclusiveContent` in `build.gradle` enforces this during resolution, and `tools/check_mod_sources.ps1` checks it statically. Details in `AGENTS.md`. Mod jars are never committed.
- The library resolves from mavenLocal. Publish it first when working on a new version: `./gradlew :<platform>:publishToMavenLocal` inside the StarEngine repository.

## Third-party assets

- **Iron Elden Ring Pixel Particle Library**: 179 hand-drawn 32x32 particle sprites by shuimo0413 ([repository](https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library), MIT licence). They ship on all four lines under `assets/astral_dice/textures/particle/ier/` and are enumerated by the generated `assets/astral_dice/particles/particle_library.json`. The full licence text and the attribution live in [docs/third-party/Iron-Elden-Ring-Particle-library-LICENSE.txt](docs/third-party/Iron-Elden-Ring-Particle-library-LICENSE.txt).
- In game the sprites are exposed as the particle type `astral_dice:particle_library` with the options `variant`, `scale` and `rgb`:
  - 1.21.1 and 26.1.2: `/particle astral_dice:particle_library{variant:12,scale:1.5,rgb:16755200}`
  - 1.20.1 takes positional arguments, all three required: `/particle astral_dice:particle_library 12 1.5 16755200`
- Regenerate the wiring after adding or removing sprites with `python tools/gen_particle_library.py`; add `--check` to verify without writing. New effects should use these sprites rather than ad-hoc or vanilla particles.
