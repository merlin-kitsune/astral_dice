# Changelog (English)

> This file contains the English changelog only. Chinese version: [`CHANGELOG_ZH.md`](CHANGELOG_ZH.md).
> The two files correspond one-to-one by version number: each version appears once in both files, and every change must update both together — never only one side.
> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no “updated again” follow-ups.

## 26.4-SNAPSHOT.95

### This line

- P0 ownership fix: the platform abstraction that only the mod uses is back in the mod. `com.merlinkitsune.starengine.api.astral.platform.AstralDicePlatform` (207 lines, 29 default methods) moved to `com.merlinkitsune.astral_dice.platform.AstralDicePlatform`; the library no longer carries any `api/astral/platform` type or the two unused imports that pointed at it. This was never a facade contract: addons keep calling `AstralDiceApi`, not the platform type.
- P1 de-duplication: deleted 13 same-name local copies on the three lines that still had them. `fabric-1.20.1` loses `combat/PartyRelations`, `event/EffectTimerGuard`, `combat/EliteTargets`, `target/HoldToSelect` and `target/SelfTargetable`; `fabric-1.21.1` loses the latter four; `neoforge-26.1.2` loses `combat/PartyRelations`, `combat/EliteTargets` and the two target types. Every reference now resolves to `com.merlinkitsune.starengine.{combat,event,target}`; 134 files were rewritten (imports / FQNs and same-package imports added where the deleted class used to sit in the same package).
- Both Fabric lines now inject the timer seam during mod construction: `EffectTimerGuard.setTimerSource(new SignTimerSourceAdapter())`, immediately after the facade install, so the library guard reads and writes the existing `ModAttachments.EFFECT_TIMER_ENDS` table. The adapter is a direct typed copy over the library `TimerEntry`; the old dual-`TimerEntry` conversion is gone.
- Fabric-1.20.1 now calls the library `PartyRelations`, so its diagnostic prefix changes from `AP_FAB_PARTY:` to `AP_PARTY:` (same `sw_* / back_* / why_*` fields). The live fixtures `PARTY-BACKENDS-fabric-1.20.1.json` and `FAB-PARTY-BACKENDS.json`, the fabric test README and the rule note in `docs/agent/AGENTS-full.md` were updated.
- Dependency pin: all five lines require StarEngine `26.4-SNAPSHOT.16`; the NeoForge/Forge range stays `[${starengine_version},27.0.0)` and both Fabric lines use `">=26.4-SNAPSHOT.16 <27.0.0"`.
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL (37 s, EXIT=0); each jar was checked and contains none of `astral_dice/combat/PartyRelations.class`, `event/EffectTimerGuard.class`, `combat/EliteTargets.class`, `target/HoldToSelect.class` or `target/SelfTargetable.class` (no stale incremental output); the residual-FQN grep over the three edited source roots is empty; the NeoForge jar lists 636 class entries, the Forge jar 660.
- Honest boundary: no live-game run in this batch. The Fabric-1.20.1 party switch replaces a local 572-line FTB/OPAC reflection implementation with the library single-source version — that contract is the intended one, but only a live FTB/OPAC run can confirm the backend reports `on`. The timer seam is injected at construction; a future line that skips the install silently keeps the library's no-op guard.

## 26.4-SNAPSHOT.94

### This line

- API ownership flip (user rule 2026-11-19: main line follows the main library, dev-next follows the next library, no cross-region calls; APIs live in StarEngine, the mod only implements them). Every addon-facing contract left the mod jar: 36 interfaces/enums moved from `com.merlinkitsune.astral_dice.api.*` to `com.merlinkitsune.starengine.api.astral.{client,combat,data,event,choice,platform,playerlike}`, `AstralDiceApi` became a library-side facade over a new `AstralDiceService` SPI (100 methods; `platform()` and the deprecated item-bound `registerDiceTier(String,Supplier,int,IntUnaryOperator)` dropped), and the mod now ships `apiimpl.AstralDiceServiceImpl`, installed from all five `AstralDiceMod` constructors right after the platform install. The mod jar keeps no `api/` package at all.
- Two code couplings removed from the shared contracts: `CardChoiceType.matches(ItemStack)` now delegates to the new facade method `matchesCardChoice(type, stack)` (implemented over `ModItems`), and the `AstralEvent` javadoc no longer links the mod's `AstralEventSystem`.
- `AstralEvent` carries `ResourceLocation` on four lines but `Identifier` on 26.1.2, so the library keeps it in `common/src/main/java-rl` for the ResourceLocation lines and ships one 26.1.2-only copy (identical except those two field types) from that platform's source set.
- Dependency pin: all five lines require StarEngine `26.4-SNAPSHOT.15` (the release that adds `api/astral`); the NeoForge/Forge range stays `[${starengine_version},27.0.0)` and both Fabric lines use `">=26.4-SNAPSHOT.15 <27.0.0"`.
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL; artifacts `astral_dice-26.4-SNAPSHOT.94+<loader>.jar` in every `build/libs`; `unzip -l` finds no `com/merlinkitsune/astral_dice/api/` classes (only `apiimpl/`), the packaged `neoforge.mods.toml` carries `versionRange="[26.4-SNAPSHOT.15,27.0.0)"` and `fabric.mod.json` carries `">=26.4-SNAPSHOT.15 <27.0.0"`. The library compiles all five lines with `api/astral` (neoforge-26.1.2 uses its Identifier copy; the other four include `common/src/main/java-rl`).
- Also fixed a real build-metadata defect found while re-verifying: `generateBuildInfo` did not depend on `compileJava`, so a `clean` build ran it in parallel with `jar` and recorded `classCount: 0` (incremental builds looked fine only because they counted stale classes). It now declares `dependsOn tasks.named("compileJava")` in `neoforge-1.21.1/build.gradle` and `forge-1.20.1/build.gradle`; the two P0 artifacts now report 635 and 659, matching their jars. Local toolchain paths in `gradle.properties` were also corrected to installed JDKs (Zulu 21 had lost its `bin/`).
- Honest boundary: source- and binary-breaking for addons (imports change, `AstralDiceApi.platform()` is gone); every facade call throws `IllegalStateException` if the service was not installed, and install happens in mod construction, so addons must not call the facade earlier. No live-game run in this batch, and the main line (`multi-main` + StarEngine main `1.2.0-pre.1`) is deliberately untouched until the dev-next line is validated in game.

## 26.4-SNAPSHOT.93

### This line

- API surface consolidation, no aliases kept: every addon-facing type now sits under `com.merlinkitsune.astral_dice.api.*`. Moved on all five lines — `event.api.*` → `api.event.*` (5 types), `choice.CardChoiceType` → `api.choice.CardChoiceType`, `platform.AstralDicePlatform` → `api.platform.AstralDicePlatform`. StarEngine made the same move for `target.TargetType` → `api.target.TargetType` and `combat.HostileTargets` → `api.combat.HostileTargets`; 231 astral sources were rewritten, plus every call site inside the library itself. Addons and KubeJS `Java.type` strings must update their imports.
- Dependency pin: all five lines now require StarEngine `26.4-SNAPSHOT.14` (the release carrying the moved packages). The NeoForge/Forge `mods.toml` range was `[\${starengine_version},2.0.0)`, which is already empty once the lower bound is `26.4`; it is now `[\${starengine_version},27.0.0)`. Both Fabric lines keep `lib_version_range` in `fabric.mod.json`.
- Fixed a real inconsistency: `AstralDiceApi.diceTierIds()` read the library-side `DiceTiers` table while `diceTierViews()` read the project-side `DiceTierRegistry`, so the id list could miss registered playable tiers and include deprecated library-only entries. It now reads `DiceTierRegistry`, the same source as the views.
- `AstralDiceApi` class javadoc now states the two-jar requirement (addons must depend on both `astral_dice` and `starengine`) and the new package paths.
- Verification: five-line `assemble --offline` with JDK 21 — BUILD SUCCESSFUL, no `error:` lines; artifacts `astral_dice-26.4-SNAPSHOT.93+<loader>.jar` in all five `build/libs`; `jar tf` shows `astral_dice/api/{event,choice,platform}/` and no leftover `astral_dice/event/api/`, `astral_dice/choice/CardChoiceType.class` or `astral_dice/platform/AstralDicePlatform.class`; the packaged `neoforge.mods.toml` carries `versionRange="[26.4-SNAPSHOT.14,27.0.0)"` for `starengine`.
- Honest boundary: this is a source- and binary-breaking move for addons (class names changed, no deprecated stubs). Only the moved types are affected — `combat.TargetCategory`, `api.playerlike.*` and the facade keep their signatures, so a consumer that only uses `AstralDiceApi` recompiles unchanged.

## 26.4-SNAPSHOT.92

### This line

- Fixed a silent no-op in the B2 combat facade: `AstralDiceApi.registerSpellDamageModifier(...)` forwarded to `AstralDicePlatform.registerSpellDamageModifier`, whose default body is empty, and none of the five platform implementations overrode it. The call still logged `AP_COMBAT_API: registered=spell_damage_modifier`, but `SpellDamageRegistry.registerModifier` was never reached, so any addon modifier was dropped without a word. All five lines now override the method and forward to `CombatApiAdapters.registerSpellDamageModifier`, matching the shape `registerSpellDamageMatcher` already had.
- Found by the LT-4 live assertion: registering a +40 modifier through the facade left `SpellDamageRegistry.modifiers()` at 10 and added no bonus to any spell damage; the direct internal registration path was unaffected, which is why the B2 batch looked healthy from the source side.
- Verification (live, dev client on neoforge-1.21.1, flat superflat save `testworld`, husk with armor 0 and MAX_HEALTH 1000, damage injected with `/damage ... by @s`): baseline `player_attack` 10 → 990; +40 modifier registered through the facade but no matcher → still 990; after registering a matcher for `msgId == player` → 950; `mob_attack` (matcher does not match) → 990. `SpellDamageRegistry.modifiers()` went 10 → 11; before the fix it stayed at 10.
- Real-spell leg, same husk, same cast (`irons_spellbooks:firebolt` level 1): 1000 → 992 with no modifier (8 damage); after registering +40 through the facade a fresh cast went 1000 → 952 (48 = 8 + 40). The built-in `fire_magic` matcher already classifies this damage, so no custom matcher was needed.
- Honest boundary: `SpellDamageModifier.onHit` could not be exercised from the probe — Rhino's `JavaAdapter` does not override interface default methods (a direct `m.onHit(view, 1)` returns `undefined` and runs the empty default). `isActive` / `apply`, both abstract, are overridden normally and were asserted; `onHit` needs a Java-side implementer.

## 26.4-SNAPSHOT.91

### This line

- Type cleanup (B6 of the API-coverage plan, S2/S3): new `api/data/DiceTierView` (id, card slots, star → chip slots with `chipSlotsAt(int)`), and the facade gains `registerDiceTierView(...)`, `diceTierView(String)` and `diceTierViews()`. The old `registerDiceTier` (both arities), `diceTier` and `diceTiers` stay and are marked `@Deprecated` — source compatible, removal deferred to the next major version.
- Missing-line coverage (S3): `item/chip/ChipEquip` used to exist only in neoforge-1.21.1 and forge-1.20.1; this batch adds fabric-1.20.1, fabric-1.21.1 and neoforge-26.1.2 (fabric uses the shim's static `getStacksHandler`, 26.1.2 uses `getCuriosInventory → getStacksHandler`; both inspect the `chip` slot for `BaseChipItem`).
- Declared out of scope: `item/sign/SignHooks` plus `SignHookDispatcher` belong to the sign-dispatch subsystem (ten nested interfaces and a hard-coded priority list); the two fabric lines have no sign subsystem, so porting it would mean shipping half a sign implementation. `CardUseService` was already filled in during B1, and `OpenCardInventoryPayload` was superseded by the platform seam in B5.
- Honest boundaries: `DiceTierView` is read-only and carries no item stack or persistence semantics, and cannot rename or unregister a tier; `diceTierViews()` covers only playable dice (the item-bound table), not entries registered as bare StarEngine descriptors; `@Deprecated` is a compile-time hint only and changes no runtime behaviour.
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL; `javap` confirmed every artifact carries both the new methods (`registerDiceTierView` / `diceTierView` / `diceTierViews`) and the old signatures (5-arg `registerDiceTier`, `diceTier`, `diceTiers`); `ChipEquip` exists in all five lines.

## 26.4-SNAPSHOT.90

### This line

- Client-side facade (B5 of the API-coverage plan): `AstralDiceApi.openCardInventory(ServerPlayer)` opens the card screen on the server, after the same "dice equipped?" check the keybind path uses, and returns whether the screen opened. New `api/client` package: `AstralHudLayer` (`render(Object graphics, float partialTick)`), `AstralHudLayers` (string ids, duplicate rejection, `pathOf`, ordered snapshot) and `AstralKeyBindings` (id plus default GLFW key code, `translationKey()`).
- All five clients now consume those registries during startup: the HUD loop runs inside the existing GUI-layer/overlay registration and anchors addon layers above the crosshair; the key loop runs inside the existing key-mapping registration and creates one `KeyMapping` per request.
- Server-side policy (P0-10): the facade class javadoc now marks which calls are server-only, and a private `requireServer` guard throws `IllegalStateException` when a client-side entity calls `giveCardTo`, `giveItemTo`, `refreshDiceChipSlots`, `walletDeposit`, `walletWithdraw`, `walletTransfer`, `playerLikeWalletWithdraw`, `playerLikeWalletDeposit` or `openCardInventory`.
- Honest boundaries: the HUD render signature takes `Object` because 1.20.1/1.21.1 use `GuiGraphics` while 26.1.2 uses `GuiGraphicsExtractor` — addons must cast to their line's type; HUD and key registration must happen during mod construction, before client setup, and late registrations are recorded but never shown; 1.20.1 overlay ids accept a path only (the namespace is fixed to this mod), so all lines register by path; addon keys need their own `key.astral_dice.<path>` lang entry; the card shop stays unexposed by decision.
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL; `javap` confirmed `openCardInventory` on the facade and the three `api/client` classes in every built artifact. Dev client: `openCardInventory` returned `true` and opened `CardInventoryMenu` with a dice equipped, `false` after clearing the dice slot; HUD and key registries rejected duplicates and blank ids; client-side calls to `walletDeposit` / `giveItemTo` threw the server-only `IllegalStateException`.

## 26.4-SNAPSHOT.89

### This line

- Economy and config facade (B4 of the API-coverage plan): `AstralDiceApi` gains `isStarCoin`, `starCoinValue` (1 star coin = 1, 1 bag = 9, anything else = 0), `countCarriedStarCoins` (36 inventory slots + offhand, folded into **face value**, not item count), `depositCarriedStarCoins` (same scope, deposits and removes, returns the credited amount) and `giveItemToPlayer` (the mod's central funnel: star coins enter the wallet when both wallet and auto-deposit are enabled, otherwise the inventory, and drop on the ground when full). Config reads: `walletFeatureEnabled`, `autoDepositOnObtain`, `firearmDamageAllowed`, `actionBarDurationTicks` and `actionBarFadeTicks`.
- The five lines already share identical `StarCoinCurrency` / `ResourceConversion` signatures, so B4 is common plus per-line forwarding: eight new `AstralDicePlatform` defaults (`starCoinValue`, `depositCarriedStarCoins`, `giveItemToPlayer`, the three config reads and the two actionbar durations), one `@Override` each on all five lines. `countCarriedStarCoins` stays in `common` because it only needs `starCoinValue` plus the vanilla inventory.
- Honest boundaries: those platform defaults return `0` / `false` when a line is not wired, so a future loader line must implement them or the facade silently degrades; the wallet and deposit calls are server-side (client calls return `0` / `false`); `starCoinValue` only knows star coins and bags, no other currency items; the config reads cannot change values at runtime; the anvil costs, loot rules and actionbar display helper from the same audit item (P2) are not part of this batch.
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL; `javap` confirmed the ten B4 facade methods and the eight platform defaults in every built artifact.

## 26.4-SNAPSHOT.88

### This line

- Selection / card-choice / event / effect facade (B3 of the API-coverage plan): `AstralDiceApi` gains `startTargetSelection`, `isSelectingTarget`, `targetSelectionToken`, `confirmTargetSelection`, `cancelTargetSelection`; `startCardChoice` (slot-count overload and min-slot overload, both returning a session token, `-1` when refused); `registerAstralEvent`, `astralEvent`, `triggerAstralEvent` (with and without a tier owner); `registerCombatResolutionListener`; `effectLevel`, `effectRemainingTicks`, `applyAstralEffect` and `astralEffectIds`. Effect-id resolution, event lookup and event triggering go through six new `AstralDicePlatform` default methods implemented on all five lines, so addons no longer walk `ModEffects.ALL` or call `AstralEventDispatcher` directly.
- New read-only `api/combat/CombatResolutionView` (attacker, target, final damage, landing damage, settled extra damage, dice stack, star level) and `CombatResolutionListener`; both dice-battle paths — player attack and creature attack — fire it after the extra-damage settlement and attack-card durability step, behind a re-entrancy guard so a listener cannot nest-fire itself.
- Honest boundaries: the resolution view is a post-settlement snapshot and cannot rewrite damage; `registerCombatResolutionListener` fires only for dice battles, not arbitrary vanilla damage; the selection/choice API requires a `ServerPlayer` and only exposes session tokens (the UI stays client-side); `triggerAstralEvent` follows the internal dispatcher, so cooldown and eligibility rules remain internal.
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL; `javap` confirmed all 16 B3 facade methods in every built artifact, and each line's `DiceCombatEvents` now has both resolution fire sites.

## 26.4-SNAPSHOT.87

### This line

- Combat registration facade (B2 of the API-coverage plan): new `api/combat` types — the `DiceBattleView` and `SpellDamageView` read-only records plus `AttackPowerModifier`, `DefensePowerModifier`, `ExtraDamageModifier`, `VictimDamageModifier`, `DiceCombatFactor`, `SpellDamageMatcher` and `SpellDamageModifier`. `AstralDiceApi` gains seven registration methods (`registerAttackModifier`, `registerDefenseModifier`, `registerExtraDamageModifier`, `registerVictimDamageModifier`, `registerDiceCombatFactor`, `registerSpellDamageMatcher`, `registerSpellDamageModifier`), each routed through a new `AstralDicePlatform` default method, so addons can tune dice-battle attack/defense power, flat extra damage, any-source victim multipliers, final dice-battle damage, spell-damage scope and spell bonuses without touching internal classes or the loader event objects those classes carry.
- The audit had planned B2 as "common only"; that was wrong. `DiceCombatContext`, `SpellDamageContext`, `DiceCombatModifiers`, `SpellDamageRegistry` and `DiceCombatEvents` exist once **per loader line** (each carries its own `LivingDamageEvent.Pre` / `LivingIncomingDamageEvent`), so `common` can only ship the read-only facade views. Every line therefore gets an identical `combat/CombatApiAdapters` translating those views into its own context types; the platform implementation forwards to it. Registration is init-time only — the internal lists are plain `ArrayList`s walked during combat.
- Honest boundaries: the views are read-only, so addons cannot write the internal context's result fields; `DiceCombatFactor` and the two spell interfaces take a `Player` attacker, so player-like hosts stay outside that channel (same as the internal state today); no entry point that makes a creature *start* a ranged or magic attack exists yet (P0-5 only covers detection and bonuses).
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL; `javap` confirmed the seven facade methods, the seven platform defaults, `CombatApiAdapters` and the nine `api/combat` classes in the built artifacts.

## 26.4-SNAPSHOT.86

### This line

- Card-loadout facade (B1 of the API-coverage plan): new `api/data/InstalledCard` and `api/data/DiceLoadout` read-only records plus a loader-independent `item/card/DiceCardLoadout` service that reproduces the private `CardInventoryMenu#saveToDice` / `#loadFromDice` semantics line by line (half the slots per side, per-side cost accounting, `cardCostForStar(configStarLevel(dice))` cap, persisted star level stays the dice's **actual** star). New facade methods: `diceLoadout`, `canEquipCard`, `equipCard`, `unequipAllCards`. Equipping still **destroys** the card stack (the loadout lives in the dice component) — the facade never deducts it, the caller does; unequipping rebuilds stacks through the original return path (player inventory → drop; player-like hosts go through `giveItemTo` → drop), and temporary-card flags cannot be restored for non-player hosts because the marking API needs a `Player`.
- `useCardOn(LivingEntity, ItemStack)` exposes `CardUseService.useCardFor`: players run the vanilla `ItemStack#use` pipeline; for non-player entities it still reaches the default `useCardForEntity` that returns `false` and logs a WARN — effect cards on player-like hosts remain the upstream gap, so callers must handle `false`. `CardUseService` was missing on three of five lines (fabric-1.20.1, fabric-1.21.1, neoforge-26.1.2) and now exists on all five with identical content.
- `diceChipSlots(ItemStack)` (backed by a new public `DiceCurioItem#chipSlots` on all five lines) exposes the dice-derived chip-slot count, and `hasDiceTier(ItemStack)` answers "is this a dice registered in the tier registry". The dice tooltip judge switched from a hard-coded list of the mod's own 13 dice items to `hasDiceTier`, so addon-registered dice finally get the cost/star/card tooltip lines.
- Verification: five-line `assemble --offline`; facade methods and the loadout service compiled in all five artifacts.

## 26.4-SNAPSHOT.85

### This line

- `PlayerLikeTargets.giveItem` now really delivers to players instead of returning `false`: the player branch routes through the central `ResourceConversion.giveItem` funnel (star-coin auto-deposit → inventory → drop), so player→player, player→maid, maid→player and maid→maid all share one delivery contract. The old early return made `AstralDiceApi.giveItemTo(player, stack)` permanently `false` and forced addons to duplicate inventory/drop logic that silently skipped star-coin absorption.
- Added `AstralDiceApi.isCardItem(ItemStack)` — true for both combat cards (`CardItem`) and effect cards (`BaseEffectCardItem`), which share no common superclass, so addons previously had to reference internal item classes to tell a card from an ordinary item.
- Verification: five-line `assemble --offline` BUILD SUCCESSFUL; compiled bytecode of `AstralDiceApi` / `PlayerLikeTargets` contains the new members.

## 26.4-SNAPSHOT.80

### This line

- Fixed the `giver != null` guard in `AstralDiceApi.giveCardTo`: player-like entities (maids, summons) can never be a `ServerPlayer`, so "maid hands a card to a player" always returned `false`. The giver may now be `null`, treated as an unknown giver — the card is still delivered, only giver-identity passives are skipped (matching the existing `VitaminPillChipItem.giveCard` convention). Found during the TLMDice facade-only rewrite's in-game acceptance (the maid-to-player transfer intent always failed).
- Verification: five-line `assemble --offline` exited 0; the in-game re-check is recorded in the commit message.

## 26.4-SNAPSHOT.79

### This line

- Filled the `AstralDiceApi` facade gaps (2026-11-18 ruling: this branch starts closing the facade). Six new public groups: player-like registration and capability queries, dice/sign read-only queries and sign activation, dice blessing checks, dice-combat participant registration, the player-like wallet registry, and a composite hostile-target probe. Addons no longer need to import the mod's internal implementation classes.
- The `AstralDicePlatform` seam gains three default methods (`registerPlayerLike` / `activateSign` / `hasDiceBlessing`) overridden per loader line, absorbing three five-line differences: the registry type of `ModEffects.DICE_BLESSING`, `BaseSignItem.activateFor` being static on forge-1.20.1, and neoforge-26.1.2 renaming `ResourceLocation` to `Identifier`.
- The five `DiceCombatEvents` static blocks now register their training-dummy hostile probe through the facade; `PlayerLikeWallets.resolve` falls back to the facade wallet table when no internal binding matches (the two fabric lines do not ship that file and are not wired).
- Freeze semantics unchanged: `freezeContent()` still freezes only the four dice-tier/sign/chip tables; the new registries are not frozen.

- Verification: five-line `assemble --offline` exited 0 (`BUILD SUCCESSFUL in 18s`, 28 tasks: 17 executed / 11 up-to-date; the facade was compiled green on the old version first, then re-verified after the bump to `.79`). No in-game check in this batch.

## 26.4-SNAPSHOT.78

### This line

- Merged `multi-main` 1.6.0-pre.1 into the development line. The five lines now pin StarEngine `1.2.0-pre.1` (the release cut of the same `next` commit that shipped as `26.4-SNAPSHOT.13`), and the release line's language fixes, tool gates, AutoJS test cases and docs come along. Version numbering stays on the `26.4-SNAPSHOT.N` development series.
- Adopted the release line's final transfer-panel geometry: player-name column 42 -> 52 px (x=24..76) and the selected-target arrow 66 -> 78 (right edge 91, 2 px clear of the scrollbar thumb at 93), replacing the 50/77 values built in `26.4-SNAPSHOT.77`.
- Branch policy: from this version on, `multi-dev-next` forks from `multi-main` as an independent development line for the next major release. Mainline bug fixes are made here directly instead of being merged back from the mainline.

- Validation: five-line `assemble` exit 0 (`BUILD SUCCESSFUL in 11s` with `--offline`; 28 tasks: 18 executed / 5 from cache / 5 up-to-date; five `.78` jars, neo `classCount` 643 / forge 668, no `META-INF/jarjar/`). The first online run could not resolve `maven.neoforged.net` from this Windows host, so the check ran against the local Gradle cache and `mavenLocal()`; no live-client check in this batch.

## 26.4-SNAPSHOT.77

### This line

- Widened the card-shop transfer player-name column from 42px to 50px (x=24..74) and moved the pink selected-target arrow from x=66 to x=77. The arrow's right edge is x=90, leaving a 3px gap to the scrollbar control at x=93; the row-hover fill doc now matches the actual (2,+0)-(93,+18) range.
- Validation: five-line `assemble` exit 0 (`BUILD SUCCESSFUL in 28s`, 28 tasks: 23 executed / 5 up-to-date; five `.77` jars). No live-client visual check in this batch.

## 1.6.0-pre.1 (2026-11-16)

### This release

- First pre-release of 1.6.0, folding the `26.4-SNAPSHOT.1`–`.76` development line into `multi-main`. The four release lines remain NeoForge 1.21.1, Forge 1.20.1, NeoForge 26.1.2 and Fabric 1.20.1; Fabric 1.21.1 stays on the P1 scaffolding line.
- StarEngine 1.2.0-pre.1 on five platforms: content registries, wallet API, four-phase damage hooks, damage categories, attack/defense stat hooks, vendored GeckoLib and the new fabric-1.21.1 library line. All five main lines pin `1.2.0-pre.1`; the declared upper bound is `2.0.0`.
- `AstralDiceApi` exposes content queries, wallet read/write/transfer, category-filtered damage hooks and `BASE`/`ADDITIVE`/`MULTIPLICATIVE`/`FINAL` attack/defense hooks. NeoForge 1.21.1 and Forge 1.20.1 install the damage classifiers and stat support; the other three lines compile without installing them.
- Companion KubeJS addon `0.0.2` (separate repository): server-script bindings for content, wallet, damage and stat hooks, with the repository-root logo and a required `astral_dice` dependency `[1.6.0-pre.1,)`. Hooks are cleared on script reload and client scripts are rejected.
- Brought the latest development assets and docs into `multi-main`: `images/`, six new `.bbmodel` sources, refreshed AGENTS/DSH rules, and the 1.5.1 release fixes (meito discount NPE, Forge 47.1.47 compatibility, four-line CurseForge upload).
- Validation: five-line release build and the addon's two-line build passed; the NeoForge 1.21.1 and Forge 1.20.1 live KubeJS probes from `26.4-SNAPSHOT.76` cover content counts, freeze state, damage categories, wallet and damage/stat hook registration. That code path is unchanged in this release except for the version pins and the companion addon metadata.
- Sign display names now follow the official wiki: the Z3000 sign reads `Z3000 Sign` / `ゼット3000のスタンド` (English wiki page name and `CharPlayable.nameJapanese`), while the Chinese name stays the community's `垃圾桶立牌` rather than the wiki character title, and the `mamushi_note` tooltips now name the Souri sign by its item names (`Amakawa Souri Sign` / `天川蒼鯉のスタンド`). `tools/sign_display_names.json` now lists 37 signs and `verify_sign_display_names` passes.

- Transfer panel layout: the selected-row arrow moved right (TP_SELECT_X 66 -> 78, keeping 2px clear of the scrollbar thumb) and the player-name column widened from 42px to 52px; synchronized across all five lines.

## 26.4-SNAPSHOT.76

### This line

- Added the public combat API under `com.merlinkitsune.astral_dice.api.combat`: `DamageCategory` (`DICE_MELEE`, `RANGED`, `MAGIC`, `GUN`, `SKILL`, `OTHER`), `DamageCategoryResolver`, `CombatStat` (`ATTACK`, `DEFENSE`), `CombatStatStage` (`BASE`, `ADDITIVE`, `MULTIPLICATIVE`, `FINAL`), `CombatStatContext`, `CombatStatHook`, `CombatStatHandle` and `CombatStatHooks`. Hooks run in descending priority order, exceptions are isolated and logged, display-only contexts bypass hooks, and registration is rejected until the line installs support.
- Extended `AstralDiceApi` with `signs()`, `chips()`, `signIds()`, `chipIds()`, `diceTierIds()`, `isContentFrozen()`; wallet `walletAvailable`/`walletBalance`/`walletHasBalance`/`walletDeposit`/`walletWithdraw`/`walletTransfer` (best-effort transfer with refund); damage-category `installDamageCategoryResolver`/`damageCategory`/`isDamageCategorySupported`; category-filtered `registerDamageHook`; and `installCombatStatSupport`/`isCombatStatSupported`/`registerCombatStatHook`. The existing phase-only `registerDamageHook` and `freezeContent` remain.
- NeoForge 1.21.1 and Forge 1.20.1 install `SpellDamageRegistry::damageCategory` and combat-stat support in common setup; the other three lines compile without installing them. The served lines classify damage as `SKILL` → `GUN` → `MAGIC` → `RANGED` → `DICE_MELEE` → `OTHER`, reusing the existing firearm/spell evidence tables. The gun classification is independent of `GameplayConstants.ALLOW_FIREARM_DAMAGE`, which only affects the spell whitelist.
- Wired combat-stat hooks into `DiceCombatEvents` on both served lines: attack `BASE` after the base attack power and before built-in attack modifiers; attack `ADDITIVE`/`MULTIPLICATIVE` after the built-in loop; attack `FINAL` immediately before `DiceBattleResolver.resolve`; defense `BASE` after the formula and before `defenseBaseDice`/`defenseCardSum`; defense `ADDITIVE`/`MULTIPLICATIVE`/`FINAL` after the padman bypass and before resolution. The combat context uses `display=false`; HUD and tooltip values do not include script hooks.
- Added the standalone KubeJS addon `0.0.1` (separate repository) with a shared two-line reflection binding: content registration/query, wallet read/write, category-filtered damage hooks, attack/defense hooks, the repository-root `logo.png`, and metadata requiring `[26.4-SNAPSHOT.76,)`. The addon is server-script-only; hooks are cleared on reload.
- Validation: five-line `.76` build exit 0 (`BUILD SUCCESSFUL in 8s`, 44 tasks: 32 executed / 12 up-to-date; five `.76` jars), plus the addon's two-line build. NeoForge 1.21.1 live (`scripts/test/win/logs/1.21.1-20261115-202859.log`): `Astral Dice KubeJS 0.0.1 loaded (KubeJS 7, NeoForge 1.21.1)`; probe `signs=36;chips=70;diceTiers=13;frozen=true;categorySupported=true;statSupported=true;magicCategory=magic;diceCategory=dice_melee;walletBalance=166`; behavior probe `cancelHealth=20->20`, `plainHealth=20->16`, attack `BASE` hook `runs=1`, defense `BASE` hook `runs=1`. Forge 1.20.1 live (`scripts/test/win/logs/1.20.1-20261115-203634.log`, with the unrelated Carpet test dependency temporarily excluded via a test-only Gradle init script): `Astral Dice KubeJS 0.0.1 loaded (KubeJS 6, Forge 1.20.1)`; probe `signs=36;chips=70;diceTiers=13;frozen=true;categorySupported=true;statSupported=true`; behavior `cancelSeen=magic/incoming/4/4;cancelHealth=20->20;plainHealth=20->16;statSeen=attack/base/0.20800000056624413;statTargetHealth=0;defenseSeen=defense/base/0;defenseTargetHealth=19`.

## 26.4-SNAPSHOT.75

### This line

- Wired the main mod's built-in signs and chips into the StarEngine content registries. `BuiltinContent` registers 36 signs and 70 chips in common setup on all five lines, before the server-start freeze; `blank_sign` and `blank_chip` are excluded. Sign descriptors use school `generic` and `GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS`; chip descriptors use the shop data's school/category, base price 10 and tier weight 6/3/2 (rare/epic/legendary). The tables are static and must be kept in sync with the item/shop data.
- Validation: five-line `.75` `build` exit 0 (`BUILD SUCCESSFUL in 12s`, 44 tasks: 32 executed / 12 up-to-date; five `.75` jars in `build/libs`). NeoForge 1.21.1 live (`scripts/test/win/logs/1.21.1-20261115-191546.log`): `Signs.size()=36`, `Chips.size()=70`, both frozen after server start; sample descriptors `parunan_sign/generic/3600` and `atm_chip/STARLIGHT/ECONOMY/10/6`; `blank_sign` and `blank_chip` resolve to null; all 70 chip descriptors are non-null. The freeze log is at `:6421`, mod-loaded at `:4795`. The game was stopped after the probe.

## 26.4-SNAPSHOT.74

### This line

- Content registries (`DiceTierRegistry`, `DiceTiers`, `Signs`, `Chips`) now freeze after server start (`ServerStartedEvent` on NeoForge/Forge, `ServerLifecycleEvents.SERVER_STARTED` on Fabric) instead of during common setup. KubeJS server scripts load after common setup; freezing there would reject `AstralDice.registerSign/registerChip` from scripts. Late registration after freeze still throws `IllegalStateException`.
- Documented DEATH-phase cancellation semantics in `DamageBridge`: cancelling only suppresses platform death processing (drops, death message); it does not restore health or keep the entity alive. Use POST to restore health or rewrite the damage in INCOMING/AFTER_ARMOR if survival is intended.
- Validation: five-line `.74` build exit 0 (`BUILD SUCCESSFUL in 24s`, 44 tasks: 37 executed / 7 up-to-date; five `.74` jars in `build/libs`). NeoForge 1.21.1 live (`scripts/test/win/logs/1.21.1-20261115-185541.log`): the KubeJS pre-freeze script registered `kubejs_prefreeze_sign` at :5964, the freeze log appeared at :6422, and the probe returned `sign=true;signCount=1;frozenSign=true;frozenChip=true;frozenTiers=true;frozenRegistry=true;lateSign=...IllegalStateException: content registry sign is frozen`. No client visual check in this batch.

## 26.4-SNAPSHOT.73

### This line

- Fixed nine shared shop card entries whose item IDs did not match the registered items (`monster_laser_card` → `effect_card_monster_laser`, `chocolate_cake` → `effect_card_chocolate_cake`, `you_have_i_have` → `effect_card_you_have_i_have`, `monster_brick_card` → `effect_card_monster_brick`, `hamburger` → `effect_card_hamburger`, `luxury_feast` → `effect_card_luxury_feast`, `express_delivery` → `effect_card_express_delivery`, `orbital_strike_card` → `effect_card_orbital_strike`, `directional_blast_card` → `effect_card_directional_blast`). All nine resolved to `minecraft:air`, so the card-trade validator logged nine “excluded item: <air>” errors and the shop pool contained empty entries.
- `CardShopCatalog.applyData` now skips unparsable or unregistered card item IDs and logs an error instead of adding an air `PoolEntry`; a bad shop data pack can no longer put empty cards on a shelf.
- Validation: five-line `.73` build exit 0 (`BUILD SUCCESSFUL in 14s`, 44 tasks: 32 executed / 12 up-to-date; five `.73` jars in `build/libs`). NeoForge 1.21.1 live (`scripts/test/win/logs/1.21.1-20261115-184222.log`): all 23 shop cards resolve to registered items, `CardShopCatalog.entries()` has 23 entries and `CardTradeFilter.validatePool` reports 0 bad; log lines `:6137` (shop pool 23, abnormal 0) and `:7731` (smoke_reload 23, abnormal 0). The three Curios `scroll/feet/spellbook is not a registered slot type!` errors are unrelated to this change.

## 26.4-SNAPSHOT.72

### This line

- Forge 1.20.1 and both Fabric lines now expose all four StarEngine damage phases. Forge still has no native post-health event, so POST is derived from the health delta around `LivingEntity#actuallyHurt`: HEAD records the entry health, RETURN fires POST only when health actually dropped.
- Fabric 1.20.1 / 1.21.1 add AFTER_ARMOR and POST through `LivingDamageBridgeMixin`: it wraps the `setHealth` call inside `LivingEntity#actuallyHurt`, dispatches AFTER_ARMOR with the post-armor/absorption loss (cancel or rewrite), then dispatches POST with the real health loss. This also covers lethal damage, which Fabric 1.21.1's `AFTER_DAMAGE` skips.
- Removed the Fabric 1.21.1 `AFTER_DAMAGE` registration to avoid duplicate POST and the lethal-damage blind spot. `ALLOW_DAMAGE` remains cancel-only and is still mapped to INCOMING.
- Mixin registration: `bridge.LivingDamagePostBridgeMixin` (Forge) and `bridge.LivingDamageBridgeMixin` (both Fabric lines). Damage sources are carried through ThreadLocal stacks so nested damage stays paired.
- Validation: five-line `build` exit 0 (`BUILD SUCCESSFUL in 15s`, 44 tasks: 27 executed / 17 up-to-date; `.72` jars in `build/libs`). The Forge refmap maps the mixin to `m_6475_`; the Fabric final jars contain the remapped `method_6074` / `method_6033` targets. No live-client testing in this batch.

## 26.4-SNAPSHOT.71

### This line

- Wired the StarEngine damage-hook chain into the main damage events. NeoForge 1.21.1 and 26.1.2 dispatch all four phases (INCOMING, AFTER_ARMOR, POST, DEATH); Forge 1.20.1 dispatches INCOMING, AFTER_ARMOR and DEATH (no native post event); Fabric 1.21.1 dispatches INCOMING, POST and DEATH; Fabric 1.20.1 dispatches INCOMING and DEATH.
- Fabric's `ALLOW_DAMAGE` can cancel but cannot rewrite the incoming amount; its bridge treats amount changes as advisory. Forge lacks a native post-damage event, and Fabric 1.20.1 lacks a post/after-armor event; these gaps are documented in the bridge classes.
- No hooks are registered by default, so the event wiring is behavior-neutral until a mod registers a hook.
- Validation: five-line `build` exit 0 (`BUILD SUCCESSFUL in 15s`, 44 tasks: 32 executed / 12 up-to-date; `.71` jars in `build/libs`). No live-client testing in this batch.

## 26.4-SNAPSHOT.70

### This line

- The card shop and chip shop pools are now data-pack driven on all five lines. `data/<namespace>/astral_dice/shop/*.json` declares the 23-card pool and the 70-chip pool (id, tier, school, category, weight, combat flag and star gate); the built-in pools remain the fallback when no valid file is present.
- Added `ShopData` and a shop snapshot in `AstralDiceDataStore`. Each line registers a shop reload listener; `ChipShopCatalog` and `CardShopCatalog` swap their active pool/index after a reload without restarting the server.
- Fabric 1.20.1/1.21.1 use the same default data; the barricade card continues to resolve through the Fabric `byItemId` helper, so the Fabric pool is semantically identical to the Neo/Forge pool.
- Validation: five-line `build` exit 0 (`BUILD SUCCESSFUL in 15s`, 44 tasks: 32 executed / 12 up-to-date; `.70` jars in `build/libs`). No live-client testing in this batch.

## 26.4-SNAPSHOT.69

### This line

- The Astral Anvil and loot injection are now data-pack driven on all five lines. `data/<namespace>/astral_dice/anvil/*.json` controls star-coin costs, XP costs, max star, the material tag and per-item denominations; `data/<namespace>/astral_dice/loot/*.json` controls chest and entity injection through native loot tables.
- Removed the old hardcoded anvil values and the NeoForge GLM / `onLivingDrops` / four-pool paths. The default data lives in `common/src/main/resources`; NeoForge and Forge use deferred reload queues, while Fabric parses the pack from `LootTableEvents.MODIFY` (Fabric 1.21.1 falls back to the packaged default until the server starts).
- Native tables now cover the common/end-city/buried-treasure chests, star coins and the special star-plate tiers (wither, warden, elder guardian, ghast, wither skeleton, guardian, generic monster). Ordinary mob star-plate odds are unified at 1%; wither 5; ender dragon 100%.
- Validation: five-line `build` exit 0 (`BUILD SUCCESSFUL in 14s`, 44 tasks: 32 executed / 12 up-to-date; `.69` jars in `build/libs`). No live-client testing in this batch.

## 26.4-SNAPSHOT.68

### This line

- The Astral Anvil GUI no longer draws the gray `playerInventoryTitle` (“Inventory”) above the player inventory on all five lines. The NeoForge 1.21.1 screen no longer calls `super.renderLabels`; the other four lines drop their explicit inventory-label draw.
- The anvil title is now rendered from a fixed `container.astral_dice.astral_anvil` component with a Chinese fallback, so the screen no longer depends on the menu-provided title (which could be blank or a raw ID on some loaders).
- Validation: five-line `:build` exit 0 (`BUILD SUCCESSFUL in 18s`, 44 tasks: 37 executed / 7 up-to-date; log `temp/build-68.log`). Live clients on NeoForge 1.21.1, Forge 1.20.1, NeoForge 26.1.2, Fabric 1.20.1 and Fabric 1.21.1 all opened the Astral Anvil GUI with the title “星愿铁砧” and no gray inventory label: `run/neoforge-1.21.1/screenshots/2026-11-15_14.26.40.png`, `run/forge-1.20.1/screenshots/2026-11-15_14.28.57.png`, `run/neoforge-26.1.2/screenshots/2026-11-15_14.29.25.png`, `fabric-1.20.1/run/client/screenshots/2026-11-15_14.30.00.png`, `fabric-1.21.1/run/client/screenshots/2026-11-15_14.30.28.png`.

## 26.4-SNAPSHOT.67

### This line

- The Astral Anvil no longer resets star level or returns cards under `experimental_star_upgrade`; both the Astral Anvil and the vanilla anvil now always add exactly one star and keep the input dice's applied cards.
- Moved that reset to dice-tier crafting (plain → golden → diamond). With `experimental_star_upgrade` on, the crafted dice is reset to 0★ with no applied cards and zero used attack/defense cost; its card capacity is reset to the 0★ value. The parent dice's applied cards are returned to the player. Crafting inputs and card costs are not refunded (option B).
- Added `DiceUpgradeCraftHandler` on all five lines (server-side, experimental only); both Fabric lines register it explicitly. Removed the anvil menu/handler reset branch and the `ANVIL_STAR_RESET_CARDS_RETURNED` log.
- The JEI `astral_dice:astral_anvil` page no longer shows `Reset X★ → 0★`; the `star_reset` key was removed from all 15 language files, and the config comments now describe the crafting reset instead of an anvil reset.
- Validation: five-line `compileJava` exit 0 (`BUILD SUCCESSFUL in 17s`, 8 tasks; log `temp/compile-b-all-2.log`). Five-line `.67` `:build` exit 0 (`BUILD SUCCESSFUL in 21s`, 44 tasks; 37 executed, 7 up-to-date; log `temp/build-67.log`); all five `.67` jars contain `DiceUpgradeCraftHandler` and were deployed to `run/<line>/mods`. NeoForge 1.21.1 live with `experimental_star_upgrade=true` (`scripts/test/win/logs/1.21.1-20261115-134430.log`): crafted a `golden_dice` in a crafting table from a carded 2★ `astral_dice:dice` (medium card) + 4 gold ingots + 4 star coins; the result was 0★ with empty `appliedStones`, used attack/defense cost 0/0 and capacity 3/3. Taking it returned one `astral_dice:attack_card_medium` (log `:9453`: `CRAFT_STAR_RESET_CARDS_RETURNED player=Dev source=astral_dice:dice star=2->0 cards=1`), and neither the gold ingots nor the star coins were refunded. A second carded 2★ dice in the Astral Anvil upgraded to 3★ with its medium card retained and 25 star coins consumed (64→39), confirming the anvil no longer resets. Clicking the anvil arrow opened the JEI `astral_dice:astral_anvil` page; it reads `星阶 0→1 · 15 星币 / 升级花费：5` with no `重置` text; screenshot `temp/astral-anvil-evidence/neoforge-1.21.1/jei-67-no-reset.png` (sha256 `1f58b4ca2bb801358fd1539dc6873b79661d2d89bdb23d2172d89a0d9c639a92`). The other four lines were built but not live-tested in this batch.

## 26.4-SNAPSHOT.66

### This line

- Fixed the Astral Anvil JEI entry with empty slots: left-clicking the arrow with no items now opens the `astral_dice:astral_anvil` category and lists all 39 upgrade recipes (13 dice × 0/1/2 stars) instead of requiring the input or result slot to contain an item; the 'Show Recipes' tooltip also appears when the GUI is empty. `AstralAnvilJeiBridge` now accepts an empty stack, and the plugin passes the complete recipe list to `RecipesGui` for that case.
- Validation: five-line `.66` `:build` exit 0 (`BUILD SUCCESSFUL in 15s`, 44 tasks; 32 executed, 12 up-to-date; log `temp/build-66.log`). NeoForge 1.21.1 live (`scripts/test/win/logs/1.21.1-20261115-132255.log`, loading `astral_dice-26.4-SNAPSHOT.66+neoforge_1.21.1.jar`, `experimental_star_upgrade=false`): cleared the inventory, placed an Astral Anvil at `(8,-60,5)`, opened the empty GUI, then left-clicked the arrow; `mc.screen` changed from `AstralAnvilScreen` to JEI `RecipesGui`, the category contained 39 recipes on page `1/14`, all upgrade entries (star 0→1 / 1→2 / 2→3, Upgrade Cost 10); screenshot `run/neoforge-1.21.1/screenshots/2026-11-15_13.23.52.png`. The other four lines were built but not live-tested in this batch.

## 26.4-SNAPSHOT.65

### This line

- Fixed the Astral Anvil JEI page layout on all five lines: the category is now 128 px wide instead of 180 px, so JEI centers the recipe panel instead of leaving a wide empty margin on the right.
- Clicking the anvil arrow now sends all 39 upgrade pages (13 dice × 0/1/2 stars) to JEI, with the page matching the input dice and current star level moved to the front. Previously only that single recipe was sent, so the GUI always showed page `1/1` and only the plain dice cost.
- Each page now reads its own dice and star level: with `experimental_star_upgrade` off every die shows the 10-level cost; with it on, dice in `dice_t0`…`dice_t4` show 5/10/15/20/30 and untiered dice show 5, while star-coin costs remain 15/20/25 by star level.
- Renamed the JEI cost line from `Enchant Cost` to `Upgrade Cost` (zh: 升级花费, ja: アップグレード費用) in all 15 language files.
- Validation: five-line `.65` `:build` exit 0 (`BUILD SUCCESSFUL in 15s`, 44 tasks; 32 executed, 12 up-to-date; log `temp/build-65.log`); all five `.65` jars contain `AstralAnvilJeiCategory.recipesStartingWith` and the renamed `Upgrade Cost` strings. NeoForge 1.21.1 live normal mode (`scripts/test/win/logs/1.21.1-20261115-123420.log`, `experimental_star_upgrade=false`): a `diamond_dice` + 64 star coins showed `cost=10`; clicking the arrow opened `astral_dice:astral_anvil` with the panel centered, page `1/14`, and all 39 entries (13 dice × 0/1/2 stars) read `Upgrade Cost: 10`; screenshot `temp/astral-anvil-evidence/neoforge-1.21.1/jei-65-normal.png`. Experimental mode (`scripts/test/win/logs/1.21.1-20261115-123622.log`, option on): a `nether_star_dice` (t4) + 64 star coins showed `cost=30`; the JEI page starts with `Reset 0★ → 0★` and `Upgrade Cost: 30`, and the 39-entry list splits 3/9/15/9/3 across XP 5/10/15/20/30; screenshot `.../jei-65-experimental.png`. The other four lines were built but not live-tested in this batch.

## 26.4-SNAPSHOT.64

### This line

- Added a JEI recipe shortcut to the Astral Anvil screen on all five lines. Left-clicking the gold arrow now opens the dedicated `astral_dice:astral_anvil` JEI category, which lists the 13 dice × 0/1/2 stars (39 upgrade rules) with the astral anvil as catalyst, star coin cost, and XP cost. The exact page is chosen from the input dice's item and current star level, so it shows the anvil star-upgrade rule rather than the dice's crafting recipe. Hovering the arrow shows a 'Show Recipes' tooltip while JEI is available; the screen talks only to the JEI-free `AstralAnvilJeiBridge`, so the mod still loads without JEI. `AstralAnvilJeiPlugin` is discovered by `@JeiPlugin` on NeoForge/Forge and registered under `jei_mod_plugin` in both Fabric `fabric.mod.json` files; all 15 language files carry the new category and tooltip keys.
- Added `DiceStarUpgrade.starLevel(ItemStack)` (non-dice → -1) so the JEI category and arrow match the exact upgrade page without duplicating component-reading logic.
- Made that JEI page mode-aware. With `experimental_star_upgrade` on, every page now reads `Reset X★ → 0★` and keeps the same 15/20/25 star-coin and tiered XP costs as the in-game anvil; with the option off it still reads `Star X→Y · N Star Coins`. All 15 language files carry the new reset key.
- Hid the leftover `astral_dice:vacuum_piece` 'Pipe Prototype' item from gameplay: removed it from all five creative tabs and removed its three NeoForge 1.21.1 language names. The item registration and item model stay because `GrapplingHookRenderer` uses `vacuum_piece` as the internal model carrier for the Z3000 grappling hook; deleting it would break that renderer.
- Validation: five-line `.64` `:build` exit 0 (`BUILD SUCCESSFUL in 3s`, 44 tasks; 21 executed, 23 up-to-date; log `temp/build-64.log`). All five jars contain `AstralAnvilJeiCategory.class`, and every `zh_cn.json` carries `重置 %s★ → 0★`. NeoForge 1.21.1 live (`scripts/test/win/logs/1.21.1-20261115-114726.log`): opened the Astral Anvil GUI, put one `astral_dice:dice` and 64 `astral_dice:star_coin` into the input slots, then left-clicked the arrow. JEI opened `astral_dice:astral_anvil` with page `1/1`: anvil + dice + 15 Star Coins → dice, `重置 0★ → 0★`, `附魔花费: 5` (the test instance has `experimental_star_upgrade=true`; the recipe's own `coinCost` is 15 and `xpCost` is 5). JEI's runtime ingredient inventory returned 1928 item stacks with zero `vacuum_piece` hits, and the creative tab has no Pipe Prototype entry. The other four lines were built but not live-tested in this batch. Screenshot evidence: `temp/astral-anvil-evidence/neoforge-1.21.1/jei-astral-anvil.png` and `...-crop.png` (not committed).


## 26.4-SNAPSHOT.59

### This line

- Fixed the Astral Anvil inventory icon on all five lines. The block model had no `minecraft:block/block` parent, so its item inherited no GUI display transform and rendered as a flat, oversized face instead of a normal block icon. It now inherits the vanilla block GUI transform (30°/225° rotation, 0.625 scale), matching the vanilla anvil item icon. The in-world model, UVs, textures, recipe, and item model are unchanged.
- Validation: five-line `:build` exit 0 (BUILD SUCCESSFUL in 10s, 44 tasks; 32 executed, 12 up-to-date); all five `.59` jars carry the parented block model, and `check_changelog_parity.py` reports `CHANGELOG_PARITY: OK sections=81 entries=1080/1080`. No live run in this batch; this is the standard block GUI display transform, not new rendering code.

## 26.4-SNAPSHOT.58

### This line

- Added the **Astral Anvil** (`astral_dice:astral_anvil`) on all five loader lines: a new anvil-family block that keeps vanilla falling, landing/break sounds, horizontal facing, and never takes durability damage. Its 176×166 anvil-like UI has three input slots (one dice plus two materials) and one result; the material slots are gated by the `astral_dice:astral_anvil_materials` item tag, which starts with only `astral_dice:star_coin`. The block is crafted shapeless from `minecraft:anvil` plus two `astral_dice:star_coin_bag`. It is deliberately not added to the `minecraft:anvil` block tag and its menu never calls `AnvilBlock.damage`, so vanilla anvil damage/removal behavior cannot touch it.
- Vanilla anvil star upgrades remain available. A new common config option, `experimental_star_upgrade` (default `false`, config version 8), controls an experimental path on **both** anvils: when enabled, a successful star upgrade produces a 0★ dice, returns the equipped cards to the player's inventory (survival drops any overflow; creative discards it), and uses tiered XP costs based on the input dice's `astral_dice:dice_t0`…`dice_t4` tags (5/10/15/20/30, fallback 5). With the option off, both anvils keep the existing 10-level cap, star +1, card retention, and 15/20/25 star-coin material costs unchanged.
- Added the five-line assets and wiring: 176×166 GUI texture, 32×32 block texture, block model/blockstate/item model, shapeless recipe, loot table, three-language names/tooltips, pickaxe/needs-iron-tool tags, creative-tab entry, menu type, screen registration, and the 26.1.2 generated blockstate/item-model datagen registrations.