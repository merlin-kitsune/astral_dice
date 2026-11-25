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

## What's new in 1.6.0-rc.1

This release moves straight from pre.5 to a release candidate: every line's mod_version becomes 1.6.0-rc.1+<loader>, the StarEngine prerequisite is pinned to the **1.2.0 final release (no suffix)**, and the two Fabric lines declare >=1.2.0 <2.0.0 for depends.starengine.

- **Astral Anvil use sound restored.** AstralAnvilMenu#onTake now calls this.access.execute((level, pos) -> level.levelEvent(1030, pos, 0)) after taking the result, matching vanilla AnvilMenu#onTake. fabric-1.21.1 already had it; the other four lines were missing it. Place, land and break sounds were already covered by SoundType.ANVIL.
- **Abyss Larva sign text aligned with tentacle v2.** tooltip.astral_dice.sign.sykes_note drops the 1-point skill damage on attackers and now reads: each Abyss Tentacle draws hostile mobs within 4 blocks and keeps their aggro; elites and bosses that attack it are marked (or gain a stack of the Abyss Mark) and then retarget the mark's caster. Applied to 15 language files (five lines x zh_cn/en_us/ja_jp).
- **Ender portal sounds 30% quieter.** On all five lines InkShadowPortalEntity uses 0.5 to 0.35 for the ambient sound, 0.6 to 0.42 for the trigger and 0.8 to 0.56 for travel (both entering and leaving).

## What's new in 1.6.0-pre.5

The Abyss Tentacle attack animation now plays once; NeoForge 1.21.1 restores item tooltips for both layers of the Astral Anvil screen; the attack-power readout no longer counts the old weapon after moving a tool out of the hotbar (including the fabric-1.21.1 modifier enumeration fix); the tentacle second pass removes retaliation damage and redirects elite/boss aggro to the mark's owner; NeoForge 26.1.2 tentacles are selectable by hostile mobs again. StarEngine stays at 1.2.0-pre.2.

## What's new in 1.6.0-pre.4

The Abyss Larva attack-bonus counter now uses an owner→target UUID index: `countMarkedFor` walks only the wearer's marked targets instead of scanning every entity in the level, rebuilding once on the first refresh or a dimension change. P0 `neoforge-1.21.1` live tests passed for cache counting, two-owner isolation and a 125-second single-layer mark expiry; the other four lines (`forge-1.20.1`, `neoforge-26.1.2`, `fabric-1.20.1`, `fabric-1.21.1`) also passed two-owner isolation, cache counting and death-cleanup smoke tests. StarEngine stays at 1.2.0-pre.2.

## What's new in 1.6.0-pre.3

Recipe fixes on top of 1.6.0-pre.2. Three sign recipes (`al_sign`, `inkshadow_sign`, `souri_sign`) now use a blank sign in the centre slot instead of a blank chip, and `lingling_sign` now uses a blank sign instead of a redstone block; after auditing all 36 sign recipes, 35 have a blank sign in the centre and no other mismatch remains. StarEngine stays at 1.2.0-pre.2.

The Abyss Larva sign passive is also reworked: entering the Abyss zone or being hit by a tentacle adds Abyss Mark stacks (max 20), and each stack increases damage taken from the Abyss Larva sign and its tentacles by 1. Every living target you marked grants you +1 attack, removed when the target dies. Marks are bound to the applier until the target dies — other players cannot apply them, though zone entry and tentacle hits still add stacks.

## What's new in 1.6.0-pre.2

This pre-release aligns the mod with StarEngine 1.2.0-pre.2 and the complete `api.astral` surface. The 1.6.0-pre.1 feature list below remains the baseline.

- **Public add-on API.** `AstralDiceApi` now exposes 103 `public static` methods through `com.merlinkitsune.starengine.api.astral`; StarEngine 1.2.0-pre.2 supplies 37 logical API classes (38 physical files) and the shared content, economy, damage, event and player-like APIs on five platforms. The mainline's old 39-file API surface is gone.
- **KubeJS companion.** The separate `astral_dice_kubejs` addon (`0.0.2`) binds content registration/query, wallet, damage hooks and combat-stat hooks to server scripts. Hooks are cleared on reload; client scripts are rejected.
- **Damage categories and stat hooks.** Damage is classified as dice/melee, ranged, magic, gun or skill. Script hooks can adjust attack and defense at `BASE`/`ADDITIVE`/`MULTIPLICATIVE`/`FINAL`; NeoForge 1.21.1 and Forge 1.20.1 install them.
- **Content registration.** The built-in 36 signs and 70 chips are registered into StarEngine's content registries; content freezes after server start so server scripts can register before the freeze.
- **Five-platform library.** StarEngine adds a Fabric 1.21.1 library line, but the mod's Fabric 1.21.1 line remains unreleased scaffolding.

The 1.5.0 feature list below is retained as the previous stable baseline.

## What's new in 1.5.0 (previous stable)

This section covers everything in the 1.5.0 stable release, including what the earlier 1.5.0 pre-release and release-candidate builds shipped. Entry-by-entry detail lives in [CHANGELOG.md](CHANGELOG.md).

**New content**

- **Six new signs.** KAngel and Ame (Bizarre quality, with their shared "One Body, One Soul" team note), plus Bartender, Dorothy, Sister and Big Fat Fish.
- **Two new effect cards.** Barricade (a 30-durability visible entity with a fence-height collision block that pulls aggro onto itself) and Reinforced Repulsion (exclusive, 100 durability, clamps hostile targets to a 8-block aura with Slowness and Weakness).
- **New chips.** Flashlight - Burst, Fortune Stick, Abyss Reagent, Wireless Transmission Pole and Sensor Device.
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
The Abyss Reagent chip is named consistently now: display name, tooltip and every guide/handbook reference use Abyss Reagent (ja_jp: 深淵試薬).
- Forge dev runs need `-Dmixin.config=astral_dice.jade.mixins.json`, because dev does not read `mods.toml`.

## Supported versions

| Support | Subproject | Minecraft | Loader | Java | Version | Patchouli |
|---|---|---|---|---|---|---|
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.6.0-rc.1 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.1.47 | 17 | 1.6.0-rc.1 | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.6.0-rc.1 | ✅ |
| ✅ | `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 | 17 | 1.6.0-rc.1 | ✅ |

- **All four lines are release lines.** `neoforge-1.21.1`, `forge-1.20.1`, `neoforge-26.1.2` and `fabric-1.20.1` stay at feature parity; every GitHub Release carries all four jars, tagged with the bare base version (this release: `1.6.0-rc.1`) and published as a pre-release. CI builds every line in one run (`.github/workflows/build.yml`).
- `neoforge-26.1.2` is the 26.1.x migration line. It ships in the same batch as the other three, as an attachment on the same Release, and never gets its own tag. There is no Iron's Spells 'n Spellbooks integration here, since upstream ships no 26.1.x build.
- `fabric-1.20.1` is the Fabric port line (added 2026-09-29, merged into `multi-main` on 2026-10-01) and has been a stable release line since 1.5.0: same Release and same tag as the other three. It has its own prerequisite set (see [Fabric prerequisites](#fabric-1201-prerequisites)), no Curios, Bountiful or Iron's Spells integration, and the four recipe-mechanism differences listed under "Platform & Compatibility" in [CHANGELOG.md](CHANGELOG.md).
- `fabric-1.21.1` is a fifth subproject that builds from this worktree but is **not released**. It is the P1 scaffolding line (see [the port feasibility plan](docs/features/fabric-1211-port-feasibility-plan.md)): it stays out of this support matrix, out of every GitHub Release and off CurseForge until its acceptance gates are met. Its jar appears in the provenance table below only so the build output stays traceable.

## Dependencies

| Dependency | Requirement |
|---|---|
| Required, install separately | **StarEngine** (mod id `starengine`). **None of the four release lines bundles the library any more**; install the jar that matches your loader, at the exact version the build was compiled against. This release pins **1.2.0** on all five lines: `starengine-neoforge-1.21.1-1.2.0.jar`, `starengine-forge-1.20.1-1.2.0.jar`, `starengine-neoforge-26.1.2-1.2.0.jar`, `starengine-fabric-1.20.1-1.2.0.jar`, and `starengine-fabric-1.21.1-1.2.0.jar` (non-release line). Download from the StarEngine project on CurseForge (id `1755415`). The declared range is template-expanded from that pin: `versionRange="[${starengine_version},2.0.0)"` in `neoforge.mods.toml` / `mods.toml`, and `>=1.2.0 <2.0.0` under `depends.starengine` in `fabric.mod.json`. A missing or out-of-range library fails startup with "Missing or unsupported mandatory dependencies". |
| Required | Curios API: 5.x on 1.20.1, 9+ on 1.21.1, 15+ on 26.1.2. A missing or too-old Curios is rejected during dependency sorting. This requirement is declared by this mod; StarEngine only uses Curios as a compile-time dependency on the Forge side. |
| Required | **GeckoLib**: `[4.9,5)` on NeoForge 1.21.1, `[4.8,5)` on Forge 1.20.1, `4.8.4+` on Fabric 1.20.1, `[5,6)` on 26.1.2. This mod ships GeckoLib-animated entities; a missing or out-of-range GeckoLib is rejected during dependency sorting. GeckoLib is never embedded in the jar. |
| Required, 1.20.1 only | A Mixin runtime: either **Mixin Booster 0.1.3 or newer**, or **Sinytra Connector**. If the pack already ships Connector, nothing else is needed, because Connector bundles the same runtime and Mixin Booster steps aside. When neither is present the mod refuses to start and explains how to install one. An outdated Mixin Booster is rejected. |
| Required, fabric-1.20.1 only | A different set, listed under [Fabric prerequisites](#fabric-1201-prerequisites). The Curios and Mixin Booster chain does not apply on this line. |
| Optional | Bountiful, Patchouli. |
| Optional, fabric-1.20.1 | Patchouli. There is no 1.20.1 Fabric build of Bountiful. |

### Fabric (1.20.1) prerequisites

| # | Requirement | Version | Declaration in `fabric.mod.json` |
|---|---|---|---|
| 1 | Fabric Loader | 0.19.5+ (0.19.x) |
| 2 | Fabric API | 0.92.12 for 1.20.1 |
| 3 | Fabric Data Attachment API | shipped with Fabric API | `depends`: `fabric-data-attachment-api-v1` (any version) |
| 4 | StarEngine | 1.2.0 | `depends`: `starengine >=1.2.0 <2.0.0`; **no longer bundled** on this line (the Loom `include` was removed), so `starengine-fabric-1.20.1-1.2.0.jar` must be installed separately (CurseForge project id `1755415`) |
| 5 | GeckoLib (animated entity) | 4.8.4+ | `depends`: `geckolib >=4.8.4` |
| 6 | Accessory provider, either one | Trinkets 3.7.2+ or Accessories 1.0.0-beta.48+ | both under `recommends`, checked at startup |
| 7 | Optional | Patchouli (in-game guide), JEI 15.62.0+ | both under `recommends` |
| n/a | Not applicable on this line | - | Curios API, Mixin Booster, Sinytra Connector, Bountiful, Iron's Spells 'n Spellbooks |

- StarEngine supplies the Fabric event bridges (13 on 1.21.1, 12 on 1.20.1); Puzzles Lib and Forge Config API Port are no longer required.
- The accessory provider is either-or. Fabric's `depends` block is AND-only, so Trinkets and Accessories are both declared under `recommends`, and the mod checks at startup, refusing to launch only when neither is present.
- The CurseForge store listing declares Trinkets as required and Accessories as optional. That is a store-page declaration only; `fabric.mod.json` keeps both as recommendations, so an Accessories-only setup still starts.

---

## Download

- **GitHub Releases**: [merlin-kitsune/astral_dice/releases](https://github.com/merlin-kitsune/astral_dice/releases). Every Release carries all four jars: one each for `neoforge_1.21.1`, `forge_1.20.1`, `neoforge_26.1.2` and `fabric_1.20.1`.
- **Tag rule**: the tag is always the bare base version (this release: `1.6.0-rc.1`) - no `v` prefix, no platform prefix, no loader suffix. All four jars belong to that one Release. This 1.6.0-rc.1 release is published as a pre-release; the later stable 1.6.0 line will use the bare version. The Fabric line does not publish a separate pre-release or take its own tag.
- **CurseForge**: the builds go to the main project (`astral-dice`, project id `1662159`). StarEngine has its own project (`starengine`, id `1755415`); **none of the four lines bundles it any more**, so players must install that separate jar themselves: `starengine-neoforge-1.21.1-1.2.0.jar` / `starengine-forge-1.20.1-1.2.0.jar` / `starengine-neoforge-26.1.2-1.2.0.jar` / `starengine-fabric-1.20.1-1.2.0.jar`. Version numbers keep the `+<loader>_<MC>` suffix (for example `1.6.0-pre.4+neoforge_1.21.1`), and the release channel follows the version suffix. Modrinth publishing was permanently dropped on 2026-10-23; CurseForge is the only store channel.
- **Build artefacts**: `astral_dice-1.6.0-rc.1+neoforge_1.21.1.jar`, `astral_dice-1.6.0-rc.1+forge_1.20.1.jar`, `astral_dice-1.6.0-rc.1+neoforge_26.1.2.jar` and `astral_dice-1.6.0-rc.1+fabric_1.20.1.jar`, each under its subproject's `build/libs/`; the version inside each jar matches the file name (1.6.0-rc.1).

## Build provenance (1.6.0-pre.4)

> All five lines were built from **`4293b053`** on 2026-11-22. This is a local pre-release candidate; not pushed or published.

The Forge and NeoForge 1.21.1 jars carry `META-INF/astral_dice/build-info.json` with `{version, commit, builtAt,
classCount}`; all five 1.6.0-pre.4 jars ship a sibling `<jar>.sha256`. `build-info.commit` names the **code commit** the
jar was built from; the README update that records the hash does not change the artifact.

| Line | Version | Built from | Built at | Size (B) | SHA-256 |
|---|---|---|---|---|---|
| neoforge-1.21.1 | 1.6.0-pre.4+neoforge_1.21.1 | `4293b053`, classCount 657 | 2026-11-22 | 3,075,524 | `2b886dfa4e2c1a1ee1b00e40e7cfda3cbd746c1ae2ec20be9e50c10c5e343ebf` |
| forge-1.20.1 | 1.6.0-pre.4+forge_1.20.1 | `4293b053`, classCount 681 | 2026-11-22 | 3,125,891 | `a96d882512136541281521193881625f17aca3a615f09b01547b8f9b34a231ba` |
| neoforge-26.1.2 | 1.6.0-pre.4+neoforge_26.1.2 | no build-info, same `4293b053` batch | 2026-11-22 | 3,294,353 | `b4df7c1839f873178d10b572501f0db89a9651466d5684a3bb340374986b3bc7` |
| fabric-1.20.1 | 1.6.0-pre.4+fabric_1.20.1 | no build-info, same `4293b053` batch | 2026-11-22 | 3,367,317 | `f18a4fbaca9a1d3b7c18b6e4151fa4f87543d9ee56eefa7a423a8e9ed14ebd40` |
| fabric-1.21.1 | 1.6.0-pre.4+fabric_1.21.1 | no build-info, same `4293b053` batch | 2026-11-22 | 3,403,901 | `c75c818977cf511c3340234e1a9427782e8693107db04ab9fb65db12214746e0` |

The final `assemble` run was `BUILD SUCCESSFUL` (`assemble --offline --no-daemon --no-configuration-cache`, exit 0).
Only `neoforge-1.21.1` and `forge-1.20.1` embed `build-info.json`; the other three lines are identified by size +
hash only and are from the same `4293b053` commit. Local candidate; not pushed or published.

Earlier `26.4-SNAPSHOT` generations are superseded by this release; their history remains in git and in
`CHANGELOG.md`. The `build/libs` bytes above are the release artefacts; `pushToDevRun` copies under `run/*/mods/`
are dev-runtime repacks and are not release attachments.

`fabric-1.21.1` is tracked here because it is built from this worktree, but it is **not** a release line yet: it is the P1
scaffolding line, has no row in the support matrix above, and gets no Release attachment
(see `docs/features/fabric-1211-port-feasibility-plan.md`).

Live smoke is recorded per release in the release checklist. The historical 2026-11-10 session used the `.10` P0
jars built from `3a6df49c`; it is kept only as prior evidence and is not a test of the 1.6.0-pre.1 artefacts.

Earlier dev work on the 少主 (`al`) sign and the P1 lines is documented in
[docs/features/al-sign-spec.md](docs/features/al-sign-spec.md) §7; those readings predate 1.6.0-pre.1 and are not
release smoke evidence.

Both fabric lines were rebuilt afterwards. The stall was a throughput problem, not a dead end: after an interrupted
build Loom marked its cache lock `ACQUIRED_PREVIOUS_OWNER_DISOWNED` and re-downloaded `minecraft-server.jar` (47.8 MB)
through the local HTTPS proxy at ~73 KB/s, so the console looked frozen. Route Mojang CDNs direct
(`systemProp.http.nonProxyHosts` gain `piston-data.mojang.com`, `piston-meta.mojang.com`, `libraries.minecraft.net`,
`resources.download.minecraft.net`; keep `maven.fabricmc.net` proxied) and judge a stall by watching `*.part` grow.

`build/libs` keeps only the current version; older `26.4-SNAPSHOT.{6,7}` and `2.0.0-SNAPSHOT.*` jars were moved to
`temp/quarantine-build-libs/`. Purge stale local m2 coordinates that were overwritten in place before this release:

```bash
rm -rf ~/.m2/repository/com/merlinkitsune/astral_dice/neoforge-1.21.1/26.4-SNAPSHOT.7+neoforge_1.21.1
rm -rf ~/.m2/repository/com/merlinkitsune/astral_dice/astral_dice-forge-1.20.1/26.4-SNAPSHOT.7
```

Then reinstall from the packaged jars (`bash tools/install-astra-maven-local.sh --from <dist dir>` in the consumer repo).

## Build

```bash
./gradlew build                      # every subproject
./gradlew :neoforge-1.21.1:build     # 1.21.1 NeoForge
./gradlew :forge-1.20.1:build        # 1.20.1 Forge
./gradlew :neoforge-26.1.2:build     # 26.1.2 NeoForge
./gradlew :fabric-1.20.1:build       # 1.20.1 Fabric
```

- Third-party mods (front-ends included) come only from **Curse Maven** (`curse.maven:`) or **Modrinth Maven** (`maven.modrinth:`). `exclusiveContent` in `build.gradle` enforces this during resolution, and `tools/check_mod_sources.py` checks it statically. Details in `AGENTS.md`. Mod jars are never committed.
- The library resolves from mavenLocal. Publish it first when working on a new version: `./gradlew :<platform>:publishToMavenLocal` inside the StarEngine repository.

## License

- **Source code** — Java sources, Gradle scripts, everything under `tools/` and `scripts/`, and the documentation in `docs/` are free software under the **GNU General Public License v3.0 only** (`GPL-3.0-only`); the full text is in [LICENSE](LICENSE). The source for every published jar is this repository at the matching tag.
- **Releases up to 1.3.7** — every release up to and including **1.3.7** (all loaders, including the `1.3.7-hotfix` refresh and the `1.3.7-alpha` / `1.3.7-beta` pre-releases) stays under the **MIT License**. That grant cannot be withdrawn; the MIT text for those releases is the `LICENSE` file at the corresponding tag (e.g. `1.3.7`), and this working tree no longer ships a separate MIT file.
- **Game assets** — every texture, block/item/entity model, animation, particle sprite, GUI sprite, font, sound effect and music track shipped by this mod, plus the Patchouli guidebook text and illustrations (that is, the contents of each subproject's `src/main/resources/assets/astral_dice/` **except `lang/`**, together with the source material in `models/`, `images/` and `sound/`), are licensed under **CC BY-NC-SA 4.0** — [Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-nc-sa/4.0/). You may share and adapt them for non-commercial purposes as long as you credit *Astral Dice* / MerlinKitsune and release your derivative work under the same terms. The GPL above does not extend to these files.
- **Data and language files** — recipe, loot, tag and worldgen JSON plus `lang/*.json` follow the code license (`GPL-3.0-only`).
- **Commercial use** — paid modpacks, paid server access, paid redistribution or resale of the assets need prior permission from the author; free modpacks, videos and streams need none and are welcome.
- **Exceptions** — assets owned by third parties keep their original licenses; see [Third-party assets](#third-party-assets).

## Third-party assets

- **Iron Elden Ring Pixel Particle Library**: 179 hand-drawn 32x32 particle sprites by shuimo0413 ([repository](https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library), MIT licence). They ship on all four lines under `assets/astral_dice/textures/particle/ier/` and are enumerated by the generated `assets/astral_dice/particles/particle_library.json`. The full licence text and the attribution live in [docs/third-party/Iron-Elden-Ring-Particle-library-LICENSE.txt](docs/third-party/Iron-Elden-Ring-Particle-library-LICENSE.txt).
- In game the sprites are exposed as the particle type `astral_dice:particle_library` with the options `variant`, `scale` and `rgb`:
  - 1.21.1 and 26.1.2: `/particle astral_dice:particle_library{variant:12,scale:1.5,rgb:16755200}`
  - 1.20.1 takes positional arguments, all three required: `/particle astral_dice:particle_library 12 1.5 16755200`
- Regenerate the wiring after adding or removing sprites with `python tools/gen_particle_library.py`; add `--check` to verify without writing. New effects should use these sprites rather than ad-hoc or vanilla particles.
