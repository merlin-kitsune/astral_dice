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
| ✅ | `neoforge-1.21.1` | 1.21.1 | NeoForge 21.1.235 | 21 | 1.5.0 | ✅ |
| ✅ | `forge-1.20.1` | 1.20.1 | Forge 47.4.10 | 17 | 1.5.0 | ✅ |
| ✅ | `neoforge-26.1.2` | 26.1.2 | NeoForge 26.1.2.109 | 25 | 1.5.0 | ✅ |
| ✅ | `fabric-1.20.1` | 1.20.1 | Fabric Loader 0.19.5 | 17 | 1.5.0 | ✅ |

- **All four lines are release lines.** `neoforge-1.21.1`, `forge-1.20.1`, `neoforge-26.1.2` and `fabric-1.20.1` stay at feature parity; every GitHub Release carries all four jars, tagged with the bare base version (this release: `1.5.0`) and published as a stable release. CI builds every line in one run (`.github/workflows/build.yml`).
- `neoforge-26.1.2` is the 26.1.x migration line. It ships in the same batch as the other three, as an attachment on the same Release, and never gets its own tag. There is no Iron's Spells 'n Spellbooks integration here, since upstream ships no 26.1.x build.
- `fabric-1.20.1` is the Fabric port line (added 2026-09-29, merged into `multi-main` on 2026-10-01) and has been a stable release line since 1.5.0: same Release and same tag as the other three. It has its own prerequisite set (see [Fabric prerequisites](#fabric-1201-prerequisites)), no Curios, Bountiful or Iron's Spells integration, and the four recipe-mechanism differences listed under "Platform & Compatibility" in [CHANGELOG.md](CHANGELOG.md).
- `fabric-1.21.1` is a fifth subproject that builds from this worktree but is **not released**. It is the P1 scaffolding line (see [the port feasibility plan](docs/features/fabric-1211-port-feasibility-plan.md)): it stays out of this support matrix, out of every GitHub Release and off CurseForge until its acceptance gates are met. Its jar appears in the provenance table below only so the build output stays traceable.

## Dependencies

| Dependency | Requirement |
|---|---|
| Required, install separately | **StarEngine** (mod id `starengine`). **None of the four release lines bundles the library any more**; install the jar that matches your loader, at the exact version the build was compiled against. Pins as of the dev tree `multi-dev-next` (`26.4-SNAPSHOT.11`; unchanged from the `.10` builds these were verified against on 2026-11-10): `starengine-neoforge-1.21.1-26.4-SNAPSHOT.2.jar` (NeoForge 1.21.1), `starengine-forge-1.20.1-26.4-SNAPSHOT.2.jar` (Forge 1.20.1), `starengine-neoforge-26.1.2-26.4-SNAPSHOT.2.jar` (NeoForge 26.1.2), `starengine-fabric-1.20.1-26.4-SNAPSHOT.11.jar` (Fabric 1.20.1), `starengine-fabric-1.21.1-26.4-SNAPSHOT.9.jar` (Fabric 1.21.1, non-release line). Download from the StarEngine project on CurseForge (id `1755415`). The declared version range is template-expanded from that same pin, not a separate number: `versionRange="[${starengine_version},27.0.0)"` in `neoforge.mods.toml` / `mods.toml`, and `>=<pin> <27.0.0` under `depends.starengine` in `fabric.mod.json` (equivalent). A missing or out-of-range library fails startup with "Missing or unsupported mandatory dependencies". The published `1.5.0` artifacts still pin `26.4-SNAPSHOT.1`; if you mix builds, read the pin back from that jar's own metadata. |
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
| 4 | Puzzles Lib (event bridge) | 8.1.33+, installed together with item 5 | `depends`: `puzzleslib >=8.1.33` |
| 5 | Forge Config API Port | 8.0.3, required by Puzzles Lib 8.x | not declared directly; pulled in by Puzzles Lib 8.x |
| 6 | StarEngine | 26.4-SNAPSHOT.11 | `depends`: `starengine >=26.4-SNAPSHOT.11 <27.0.0`; **no longer bundled** on this line (the Loom `include` was removed), so `starengine-fabric-1.20.1-26.4-SNAPSHOT.11.jar` must be installed separately (CurseForge project id `1755415`) |
| 7 | GeckoLib (animated entity) | 4.8.4+ | `depends`: `geckolib >=4.8.4` |
| 8 | Accessory provider, either one | Trinkets 3.7.2+ or Accessories 1.0.0-beta.48+ | both under `recommends`, checked at startup |
| 9 | Optional | Patchouli (in-game guide), JEI 15.62.0+ | both under `recommends` |
| n/a | Not applicable on this line | - | Curios API, Mixin Booster, Sinytra Connector, Bountiful, Iron's Spells 'n Spellbooks |

- The accessory provider is either-or. Fabric's `depends` block is AND-only, so Trinkets and Accessories are both declared under `recommends`, and the mod checks at startup, refusing to launch only when neither is present.
- The CurseForge store listing declares Trinkets as required and Accessories as optional. That is a store-page declaration only; `fabric.mod.json` keeps both as recommendations, so an Accessories-only setup still starts.

---

## Download

- **GitHub Releases**: [merlin-kitsune/astral_dice/releases](https://github.com/merlin-kitsune/astral_dice/releases). Every Release carries all four jars: one each for `neoforge_1.21.1`, `forge_1.20.1`, `neoforge_26.1.2` and `fabric_1.20.1`.
- **Tag rule**: the tag is always the bare base version (this release: `1.5.0`) - no `v` prefix, no platform prefix, no loader suffix. All four jars belong to that one Release, published as a stable release (not a pre-release). The Fabric line no longer publishes a separate pre-release and no longer takes its own tag.
- **CurseForge**: the builds go to the main project (`astral-dice`, project id `1662159`). StarEngine has its own project (`starengine`, id `1755415`); **none of the four lines bundles it any more**, so players must install that separate jar themselves: `starengine-neoforge-1.21.1-26.4-SNAPSHOT.1.jar` / `starengine-forge-1.20.1-26.4-SNAPSHOT.1.jar` / `starengine-neoforge-26.1.2-26.4-SNAPSHOT.1.jar` / `starengine-fabric-1.20.1-26.4-SNAPSHOT.1.jar`. Version numbers keep the `+<loader>_<MC>` suffix (for example `1.5.0+neoforge_1.21.1`), and the release channel follows the version suffix. Modrinth publishing was permanently dropped on 2026-10-23; CurseForge is the only store channel.
- **Build artefacts**: `astral_dice-1.5.0+neoforge_1.21.1.jar`, `astral_dice-1.5.0+forge_1.20.1.jar`, `astral_dice-1.5.0+neoforge_26.1.2.jar` and `astral_dice-1.5.0+fabric_1.20.1.jar`, each under its subproject's `build/libs/`; the version inside each jar matches the file name (1.5.0).

## Build provenance (26.4-SNAPSHOT.13)

> All five lines were rebuilt together at **2026-11-12T01:10 from `a248f13e`** (the audit commit that closes the
> P0/P1 reclassification, the 26.1.2 player-like shop port and the two 26.1.2 parity fixes). Earlier artifact
> generations are summarised under the table.

The two NeoForge/Forge release jars carry `META-INF/astral_dice/build-info.json` with `{version, commit, builtAt,
classCount}`; all five `.13` jars ship a sibling `<jar>.sha256`. `build-info.commit` names the **code commit** the
jar was built from; the README update that records the hash does not change the artifact.

| Line | Version | Built from | Built at | Size (B) | SHA-256 |
|---|---|---|---|---|---|
| neoforge-1.21.1 | 26.4-SNAPSHOT.13+neoforge_1.21.1 | `a248f13e`，classCount 588 | 2026-11-12T01:10:45+08:00 | 2,844,066 | `6c109cff4467d4bef6d6793424045c22f33fc14fec6868eba5221eaa7c281c61` |
| forge-1.20.1 | 26.4-SNAPSHOT.13+forge_1.20.1 | `a248f13e`，classCount 613 | 2026-11-12T01:10:45+08:00 | 2,893,989 | `0e69643106b2ce362e805bacde48eacbda8edbe8b3198ba125648cc55cd1f2aa` |
| neoforge-26.1.2 | 26.4-SNAPSHOT.13+neoforge_26.1.2 | 无 build-info，按同一批 `a248f13e` 构建 | 2026-11-12T01:10 | 3,064,168 | `6d0204ce0363eca2f2c645c5ec4afdeeb93d7cf173482afb4b2a99a6b314a39d` |
| fabric-1.20.1 | 26.4-SNAPSHOT.13+fabric_1.20.1 | 无 build-info，按同一批 `a248f13e` 构建 | 2026-11-12T01:10 | 3,152,903 | `7da6d30fad8193fc0df0591d569df86ce00fb089f58d5fd035ea608bd86a3634` |
| fabric-1.21.1 | 26.4-SNAPSHOT.13+fabric_1.21.1 | 无 build-info，按同一批 `a248f13e` 构建 | 2026-11-12T01:10 | 3,179,282 | `c9fdbfaeaeae865bd1d4273ffbe9474b3ae59c5adaf846802d81ad214759ea52` |

The five rows come from one build run (`gradlew --no-daemon --offline` over the five `:build` tasks,
`BUILD SUCCESSFUL in 11s`, 21 executed / 23 up-to-date), which also pumped every `pushToDevRun` copy into
`run/*/mods/`. Only `neoforge-1.21.1` and `forge-1.20.1` embed `build-info.json`; the other three lines are
identified by size + hash only.

Rebuild history for the two P0 rows: `011a6891` at 2026-11-10T15:21 (all five), `51ff5691` at 23:17 (`neoforge-1.21.1`
only), `60deccc7` at 2026-11-11T00:21, `273dc47a` at 00:35 (recipe ingredients: ink sac → ender pearl, gold block →
yellow dye), then **`e1d1e922` at 2026-11-11T14:15** (both P0 lines, after the Huntsman-Sign chunk-pin/cross-dimension fix
`4ee66b81` and the global-cooldown + nether-portal-sound change `37f97669`). Both `.sha256` sidecars were regenerated from
the final bytes next to the jars in `<line>/build/libs/` and in the root `build/libs/` mirror.
`neoforge-1.21.1` is byte-identical in all three places (build dir, root mirror, `run/1.21.1/mods/`). `forge-1.20.1` is not:
`pushToDevRun` puts a re-packed jar in `run/1.20.1/mods/` (2,862,670 B / `sha256 6a5fc2c4…0f8b1f`, same `build-info.json`),
so the `build/libs` bytes above are the release artefacts and the `run/` copy is only the dev-runtime one.

`fabric-1.21.1` is tracked here because it is built from this worktree, but it is **not** a release line yet: it is the P1
scaffolding line, has no row in the support matrix above, and gets no Release attachment
(see `docs/features/fabric-1211-port-feasibility-plan.md`).

Live smoke on 2026-11-10 used the `.10` P0 jars built from `3a6df49c` (the ones in the table above before this
rebuild) — they were launched from `build/libs` into `testworld`.
On `neoforge-1.21.1` the 金鲤 (`astral_dice:souri_sign`) tooltip renders every line, including the 「金鳞：2 / 4」
status line, which is read from the **viewer's** attachment (`ModTooltipHandler.java:1646-1656`), not from the stack.
On `forge-1.20.1` the same commit registers the item (1593 items; `astral_dice:souri_sign` is the only `souri` id),
`SouriSignManager.getGoldScales` reads 2, `SouriSignItem.isEquipped` is true, and the `souri_gold_scale` /
`souri_dragon` effects plus the `souri_drink` entity are registered. Both sessions stopped cleanly and left
no 25565 / 25575 / 25585 listener. The only log errors are Forge's own version-check timeouts (the game JVM has
no proxy), which do not affect loading.

The `.11` jars themselves were not smoke-tested from `build/libs`: the `al` work described next was verified on the dev
`runClient` classpath of the three P1 lines, and the two P0 jars in this table are a rebuild of `011a6891` that has no
separately launched session of its own yet.

Same day, second batch: the 少主 (`al`) sign was ported to the three P1 lines and each was smoke-tested serially —
world-ready in 16.220 s (`fabric-1.20.1`), 18.137 s (`fabric-1.21.1`) and 12.184 s (`neoforge-26.1.2`), each
stopped cleanly with no 25565 / 25575 / 25585 listener left behind and no new crash report. `astral_dice:al_sign`
registers as epic, stacks to 1, and its tooltip renders every section on 1.21.1 and 26.1.2; the 1.20.1 line's
KubeJS Rhino cannot reach `TooltipFlag$Default`, so that line was verified from the lang keys and
`ModTooltipHandler` instead. Readings: [docs/features/al-sign-spec.md](docs/features/al-sign-spec.md) §7.

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
