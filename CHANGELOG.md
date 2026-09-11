# Changelog (English)

> This file contains the English changelog only. Chinese version: [`CHANGELOG_ZH.md`](CHANGELOG_ZH.md).
> The two files correspond one-to-one by version number: each version appears once in both files, and every change must update both together — never only one side.

## Unreleased (1.2.0)

> Convention: later edits to an entry already recorded for this version are merged into that entry — only the final version is kept, no “updated again” follow-ups.

### New Content

- Added the Charge playstyle infrastructure: icon copied from `images/充能.png` (effect texture `mob_effect/charge.png`); max stacks stored in `GameplayConstants.CHARGE_MAX_STACKS = 20`; with at least 1 Charge stack, sign-active/effect-card cooldowns are reduced by 20%% (via `ChargeManager`, wired into sign cooldown and effect-card cooldown entries; **no defense/armor bonus**); Charge stacks are kept on death (`ChargeManager.preserveOnDeath` before death, restored on respawn); all gain/spend sources go through `ChargeManager.addStacks/consumeOne/removeAll` (both loaders). All 5 charge chips (Warp Engine / Energy Recycler / Electric Sword / Advanced Peripherals / Perpetual Motion) now append a unified "Current Charge" counter to their tooltips (`current stacks / CHARGE_MAX_STACKS`, via `ModTooltipHandler.addChargeCounter` → lang key `tooltip.astral_dice.chip.charge`) (both loaders).
- Sign-active ActionBar feedback refactored: a dedicated response event `SignActiveTriggeredEvent` was added, and signs register their own prompts in their sign classes — Komachi (play count +1 and remaining), Mimi (new cards and Star Coins) and Nancy Lu (invisibility duration) now have custom texts; Haiqing/Bonnie prompt texts were updated; unregistered signs (Fen, Jasmine, Lulu, Misaki, Padman, Papara, Parunan, Rin) show the default "<Sign>: Active skill started!".
- Card inventory screen: the red locked warning during a Dice Blessing moved from the top to the bottom of the screen, outside the selection area.
- Card inventory screen: background texture now switches by die star level (0-3), with `getStarLevel()` exposed on the menu (synced to 1.20.1, feature parity across loaders).
- New "Nether Star Die": a brand-new exotic T4 tier (`astral_dice:dice_t4`), upgradeable from any T3 die (Netherite/Crimson/Ender via the `dice_t3` tag) — 4 Nether Stars in a cross around it (`" N " / "NDN" / " N "`, N=Nether Star, D=T3 die); chip slots = 4+s (0★4/1★5/2★6/3★7, one more than T3); card slots and cost points are always configured as the top 3★ tier (12 card slots / cost cap 6 per side, via `DiceCurioItem.configStarLevel`, while the menu persists the actual star); each star level grants +2 attack and +2 defense (+4 armor via transient attribute modifiers refreshed by `NetherStarDiceItem`); the item has a permanent enchanted glint (`isFoil` always true) (both loaders).
- New "Ender Die": same tier as the Netherite Die (`astral_dice:dice_t3`), upgradeable from it (ZYZ/YDY/ZYZ, Z=Ender Pearl, Y=Eye of Ender, D=Netherite Die); lethal damage triggers one totem-of-undying effect (revive to 1 HP, clear all effects, Regeneration II 0:45, Absorption II 0:05, Fire Resistance 0:40 — matching the vanilla totem, with the totem particles/sound/held-up animation using the Ender Die icon), followed by a 5:00 cooldown (via `EnderDiceHandler`, cancelling `LivingDeathEvent` and attached to the player); after the Ender Die's own totem effect or a vanilla Totem of Undying triggers, it attempts to teleport to safe ground within 16 blocks (no air/water-surface/boat destinations, and no teleport while the player is in water), with ender particles and a teleport sound; the vanilla-totem path does not start the Ender Die cooldown; while equipped, damage taken in rain/underwater (`isInWaterRainOrBubble`) is increased by 40%% (applied after final reductions in `LivingDamageEvent.Pre`) (both loaders).
- New "Amethyst Die": same tier as the Diamond Die (`astral_dice:dice_t2`), upgradeable from it (AAA/ADA/AAA, A=Block of Amethyst, D=Diamond Die); ranged/magic attacks also roll the combat die (1-6) and add its roll as damage (settled through the `SpellDamageRegistry` spell-damage modifier chain, sharing the whitelisted scope — arrows/throwables/vanilla and linked mod magic — with the spell-damage effect cards), without triggering a Dice Blessing or consuming card durability (both loaders).
- New "Crimson Die": same tier as the Netherite Die (`astral_dice:dice_t3`), upgradeable from it (YJY/JDJ/YJY, Y=Nether Wart Block, J=Crimson Fungus, D=Netherite Die); high combat-die rolls (4-6) are 50%% more likely (weighted: faces 4-6 ×1.5, P(high) 50%%→60%%, applies to attack rolls and player defense rolls), but rolling a natural 1 immediately deals 6 damage to you (dice-damage type, the same source as King Power's self-damage) (both loaders).
- New "Weird Die": same tier as the Diamond Die (`astral_dice:dice_t2`), upgradeable from it (CJC/JDJ/CJC, C=Twisting Vines, J=Warped Fungus, D=Diamond Die); sign active-skill cooldowns are halved, but low combat-die rolls (1-3) are 50%% more likely (weighted: faces 1-3 ×1.5, P(low) 50%%→60%%, applies to attack rolls and player defense rolls) (both loaders).
- New "Netherrack Die": same tier as the Golden Die (`astral_dice:dice_t1`), upgradeable from it (NNN/NTN/NNN, N=Nether Brick, T=Golden Die); mining Nether ores (quartz/gold/ancient debris) has a 30% chance to drop a Star Coin and 5% to drop a Star Plate (at most one per block, plate checked first), and piglins stay neutral to the wearer (via `PiglinAiMixin`, treated as wearing gold armor) (both loaders).
- New "Obsidian Die": same tier as the Diamond Die (`astral_dice:dice_t2`), upgradeable from it; grants 3 base defense (+6 armor via attribute modifier, 1 defense = 2 armor) and reduces fire damage taken (vanilla `is_fire` tag) by 70%; being in the `dice_t2` tag it can craft the higher-tier Netherite Die (both loaders).
- New "Emerald Die": same tier as the Diamond Die (`astral_dice:dice_t2`); while equipped, villager trades are paid with Star Coins instead of Emeralds at a 20% discount (rounded down, minimum 1); recipe = GPG/PDP/GPG (G=Emerald Block, P=Star Plate, D=Diamond Die) (both loaders).
- New "Glass Die": same tier as the Golden Die (`astral_dice:dice_t1`); combat cards always roll their maximum (random cards only: Medium/Large/Epic/Meito/Defense; fixed cards Shadow Strike/Charge/Full Power keep their side effects), but dying destroys this die and all of its equipped cards; recipe = GGG/GTG/GGG (G=Glass, T=Golden Die) (both loaders).
- New "Gunsmith Sign" (Moses, epic): Weakness Insight — gain 1 layer (max 4) after any source of dodging/counterattacking (at most once per target) or after attacking a Broken target; each layer gives +1 attack/defense and raises the minimum dice roll by +1; one layer is removed when a Dice Blessing ends. Active "Weak Counter" — waits 30s, applying Broken (2:00) when you attack a normal hostile; no cooldown is consumed if no target is hit, and Passive "Precision" reduces the active cooldown to 120s. A Broken target's dice can only be 0 and it is dodged by the Gunsmith; after dodging the Gunsmith automatically counterattacks (including Weakness Insight attack). The tooltip merges the two passive sections (Weakness Insight/Precision), moves the Broken effect description into the active description, and places the counter at the very bottom. Recipe = GPG/TCT/PZP (G=Crossbow, P=Star Plate, Z=Diamond Die, C=Blank Sign, T=Block of Redstone); icon taken from images/枪匠立牌.png (both loaders).
- New "Pandaman Sign" (rare): Passive "Good and Bad" — using Hamburger grants 2 max health (up to 100, cleared on unequip); using Hamburger or Chocolate Cake gives friendly players within 8 blocks 2 healing and 1 Healing Point; while equipped, counterattacks add damage equal to missing health when not at full health. Active "Devour" — gain one random healing card (Hamburger/Chocolate Cake/Luxury Feast) and apply Taunt (1:00) to all hostile targets within 16 blocks; taunted targets trigger a counterattack when they attack the taunter (using the counterattack damage formula, no Counterattack layers consumed). New Taunt effect: the target can only attack the player who taunted it. Recipe = WHW/HCH/BDB (W=White Concrete, H=Black Concrete, C=Blank Sign, B=Star Coin, D=Die); the tooltip shows Health Gained and Healing Point counters at the bottom; icon from images/肉弹战车立牌.png (both loaders).
- Added first-join rulebook option: new common-config entry `give_guide_book_on_first_join` (default `true`) backed by a GameplayConstants field; when enabled, players receive the Koi's Rulebook (Patchouli guide) on first join, once per player per world; config version remains 1 (both loaders).
- New Charge chip "Warp Engine" (`warp_engine_chip`, rare): charge +2 and Speed (0:10) after select teleports (ender pearl, the Ender Die's safe blink after its totem, dimension portals and Waystones; portals and Waystones share a 5:00 cooldown, while ender pearl and Ender Die blinks ignore it); recipe = `XXX/XCX/BBB` (X=Ender Pearl, C=Blank Chip, B=Star Coin) (both loaders).
- New Charge chip "Energy Recycler" (`energy_recycler_chip`, rare): charge +1 per 50 m of **horizontal** travel (a single-tick displacement above 10 blocks counts as a teleport/dimension change and is ignored; unequipping clears progress); **movement speed +5%% while you have at least 5 Charge stacks** (a conditional attribute, applied and removed live as Charge rises and falls, and cleared immediately on unequip); recipe = `XXX/XCX/BBB` (X=Piston, C=Blank Chip, B=Star Coin) (both loaders).
- New Charge chip "Electric Sword" (`electric_sword_chip`, rare): +1 attack per 4 Charge stacks (rounded down); charge +2 after 10 hostile kills (counter cleared on unequip); recipe = `XXX/XCX/BBB` (X=Iron Sword, C=Blank Chip, B=Star Coin) (both loaders).
- New Charge chip "Advanced Peripherals" (`advanced_peripherals_chip`, epic): +4 attack while you have at least 4 Charge stacks; each Dice Blessing removes 1 Charge stack; recipe = `XXX/XCX/PPP` (X=Echo Shard, C=Blank Chip, P=Star Plate) (both loaders).
- New Charge chip "Perpetual Motion" (`perpetual_motion_chip`, legendary): charge +6 on every Dice Blessing trigger (legendary chip, excluded from the Bountiful reward pool); recipe = `XXX/XCX/GGG` (X=Gold Ingot, C=Blank Chip, G=Golden Star Plate) (both loaders).
- New Healing chip "Big Bowl Stew" (`big_bowl_stew_chip`, legendary): after a Dice Blessing ends, all friendly targets within 16 blocks gain the effect — players get +1 Healing Point and restore 2 health; tamed/rideable friendly mobs (pets, horses, pigs, striders, camels) only restore 2 health (Healing Points are a player-level resource); icon from images/大碗炖肉.png (both loaders).
- New Other chip "Member Recommendation" (`member_recommendation_chip`, rare): gain one random card every time a Dice Blessing triggers (the normal random pool, exclusive cards excluded); icon from images/会员推荐信.png (both loaders).
- New Other chip "Bookmark" (`bookmark_chip`, rare): damage effect cards gain +1 damage bonus (uncapped, uncounted; it feeds the same single aggregate exit `SpellDamageRegistry.effectCardDamageBonus` as the Ninja sign's "effect-card damage bonus", applying to Laser/Brick/Orbital Strike/Directional Blast/Living Page spell damage, and tooltips show the boosted values); icon from images/书签.png (both loaders).
- New Other chip "Piggy Bank" (`piggy_bank_chip`, rare): gain 3 Star Coins for every 2 effect cards used (its own counter, independent of the Magic Tome/Ninja sign, cleared on unequip); icon from images/小猪存钱罐.png (both loaders).
- New Other chip "Smart Watch" (`smart_watch_chip`, epic): while you carry fewer than 10 cards, each hostile kill grants one random card (no cooldown and no counter; nothing is granted once you are at or above the threshold); icon from images/智能手表.png (both loaders).
- New Charge chip "Current Core" (`current_core_chip`, epic): (1) gain +1 Charge when you use an active skill (target-waiting signs count it when the wait resolves successfully, not when it times out); (2) while the active skill is on cooldown, pressing the active-skill key consumes Charge based on the remaining cooldown's share of 180 s (one tier per 1/6, rounded up, min 1 and max 6) and **instantly finishes the cooldown** — the skill itself is not cast, press again to use it; if Charge is insufficient nothing happens and a warning is shown. Its tooltip carries the unified "Current Charge" counter; icon from images/电流核心.png. No recipe yet (both loaders).
- New 4 crafting materials (all added to the `astral_dice:materials` item tag and the guidebook "Materials" category, both loaders): "Regeneration Reagent" (`regeneration_reagent`) and "Star Coin Dust" (`star_coin_dust`) use animated textures (Regeneration Reagent 2 frames, Star Coin Dust 7 frames), "Conductive Wire" (`conductive_wire`) is a 10-frame animated texture (the source image's last frame was a loop marker duplicating the first, now trimmed), and all three generate matching `.png.mcmeta` animation files; "Mark Paint" (`mark_paint`) uses a static texture. Icons taken from images/再生试剂.png, images/导电线材.png, images/星币尘.png and images/标记涂料.png respectively. Each has a crafting recipe (both loaders): Regeneration Reagent = Redstone + Slime Ball + Glistering Melon Slice + Awkward Potion (shapeless); Conductive Wire = DLD/DLD/RRR (D=Diamond, L=String, R=Redstone); Star Coin Dust = Star Coin + Nether Quartz + Glowstone Dust (shapeless); Mark Paint = empty/Y/empty, IRI, CGC (Y=Magma Cream, I=Iron Ingot, R=Redstone, C=Copper Ingot, G=Gold Ingot).

### Content & Balance

- Effect-card play system: the play window is kept (base 1 play + fixed/temporary +1 sources + Komachi's banked plays), and the per-round play cap is restored as a **fixed constant of 9 cards** (`GameplayConstants.MAX_EFFECT_CARD_PLAYS = 9`; `getMaxAllowed` returns `min(1 + bonuses, 9)`) — it is a fixed constant, **not a config option**, so the former `max_effect_card_plays` entry is no longer provided; bonus sources stack but cannot exceed the cap; the effect-card round follows the new definition (a round ends only when all effect progress AND the play cooldown are done).
- Mimi passive rework: +1 Star Coin per battle card gained via crafting or the active skill's returns (rewards/copies no longer trigger); every 25 battle cards returned by the active skill grants a random chip (was: every 25 Star Coins).
- Jasmine passive addition: using an Express Delivery card immediately reduces the active skill's cooldown by 50%% of its maximum.
- Rin active: grants one Living Page, or two if you had none before.
- Bonnie: while hidden during an Investigation stage (Invisibility + Investigation Stage), mobs can no longer target you.
- Nancy Lu active: the consumed battle card is now taken from the main inventory only (ender chest / backpack-like containers no longer count); the attack bonus is at least +2, and a guaranteed +2 applies even when no battle card is available in the main inventory.
- Unwavering: defense +2 → armor +8 (equal to +4 defense in dice battles), moved to an attribute modifier on the effect so both real armor and dice combat apply without double counting.
- Full Power: durability 2 → 5.
- Cursed Sword recipe: the middle ingredient is now Crying Obsidian instead of the Suspicious Stew item tag; the unused `suspicious_stews` item tag was removed.
- Blue Curse description: armor toughness "0" → "-100%%" (values unchanged).
- Healing: the healing point cap is now fixed at 32 (was max(10, max HP ÷ 2)); Medkit chips no longer restore 2/6 HP on equip (blessing points kept).
- Vitamin Pill: triggers on any crafted/obtained card (was crafting/reward sources only); the Satellite chip's Orbital Strike replenishment now uses the unified card-grant path.
- Satellite third ability: the "play count +1 after using an Orbital Strike" is now once per 1:00 (was once per round).
- Naming unification: Living Page Java identifiers and the effect registry id are unified to `LIVING_PAGE`/`living_page` (item id `effect_card_living_page` unchanged).
- Defense conversion: defense from effect cards/signs/chips is now converted to real armor (1 defense = 2 armor via ARMOR attribute modifiers) and no longer participates in dice-combat defense modifiers — only battle defense cards (range-varying values) remain there. Affected sources: Jasmine (+2 armor per stack, armor cap 40), Padman (-4 to +8 armor), Papara (+6 armor at half HP), Revenge Halberd (+12 armor), Nancy Lu (+6 armor on defensive passive), Fen (+4 armor with Recharged Energy); Resistance no longer adds dice-defense points (its vanilla damage reduction still applies).
- Monster armor-to-defense conversion synced with players: monster defense now uses armor÷2 like players (1 defense = 2 armor); the Piercing Gun chip's target-defense calculation was synced too.
- Piercing Gun recipe: Netherite Scrap → Netherite Ingot (both versions).
- Restored the "Adrenaline - Common" chip (epic) and rolled back the "Adrenaline - High-Grade" (legendary) recipe to an upgrade chain: Common = attack/defense +3 while below half max HP; High-Grade = +8 with a 20%% dodge chance against a hostile attack while the bonus is active. Recipes: Common = ZXZ/DCD/PPP (Z = Potion of Regeneration, X = Nether Star, D = Wither Rose, C = Blank Chip, P = Star Plate); High-Grade = RZR/ZOZ/PPP (R = Redstone, Z = Diamond, O = Adrenaline - Common, P = Golden Star Plate) (both versions).
- Target chip: after triggering the Dice Blessing, apply 1 Mark layer to the **nearest hostile target** (the old "random hostile within target-chip range" logic and the `target_chip_range` range config were removed; no distance cap).
- Sandwich (Gourmet): max health +12 → +8; removed the "gain 1 Counterattack layer per 1:00 while below half max HP" passive; new: while max HP exceeds 20, gain +1 attack per 4 HP above 20.
- Creative tab dice order adjusted: the Nether Star die now appears immediately after the Netherite die; Glass and Netherrack dice also appear after the Netherite die (both loaders).
- Sign display order adjusted: creative tab and Patchouli handbook now sort by rarity low→high (RARE → EPIC → UNCOMMON in this mod's quality mapping; same-rarity signs keep their existing relative order) (both loaders).
- Text/handbook cleanup merged to both loaders: Moses handbook passive pages merged, integration page 4 (Curse Mark) removed, Pandaman tooltip Taunt recolored blue, the extra blank line between the two Pandaman tooltip counters was removed, and the Gunsmith sign tooltip passive title now shows only “Precision” (removing “Weakness Insight”).
- Fixed the dice tooltip showing attack cards' bonus mislabeled as "dice" and a coloring issue: attack cards (Medium/Large/Epic/Meito) now uniformly read "attack", the range no longer carries a "+" prefix, matching the defense-card and standalone-card tooltip format.
- Dice card-slot balance: the total card slots are now determined by star level only (independent of dice tier) — 0★ = 4 (2 attack + 2 defense), 1★ = 6 (3+3), 2★ = 8 (4+4), 3★ = 12 (6+6); usable slots strictly follow the star level, with no extra hidden usable slots (both versions).
- Dice upgrade recipes now use tier tags for the upgrade base: added four item tags — `astral_dice:dice_t0` (base) / `dice_t1` (golden) / `dice_t2` (diamond) / `dice_t3` (netherite); the golden / diamond / netherite dice upgrade recipe inputs now reference these tags instead of specific items (both versions).
- Removed the Counterattack playstyle/effect: deleted the `counterattack` effect, layers, and the continuous retaliation-target cycle; direct triggers (Gunsmith Broken dodge, Pandaman Taunt attacks) still perform a **single counterattack damage injection** via `DiceCombatEvents.injectCounterDamage`; the Patchouli playstyle page and effect entry were removed (both loaders).
- Effect level badge now uses Arabic numerals and has a raised cap: vanilla rendered Roman numerals (II–X) up to level 10; it now shows an Arabic-numeral badge (e.g., "Healing 3", "Healing 32") up to level 100 (new `EffectRenderingInventoryScreenMixin`) (both versions).
- Config rewritten and trimmed: common config version reset to 1; the client config was removed; the damage-bonus cap, Living Page cap, sign active cooldown, sign waiting time, all sign numeric tuning, Dice Blessing duration, and Cursed Sword cap entries were removed and are now fixed GameplayConstants values (values unchanged); target/wait signs (Astrologer, Undercover Detective, Gunsmith) use the fixed 30-second wait constant.
- Removed the spell-damage bonus caps: both the Living Page per-use stacking damage and the Ninja sign “Effect Card Damage Bonus” are now uncapped (still reset on unequip); the unused `MAX_DAMAGE_EFFECT_BONUS` and the fixed constants `LIVING_PAGE_BONUS_CAP`/`KOMACHI_DAMAGE_BONUS_MAX` were deleted, and handbook/tooltip text no longer mentions “cap 20/10” (both loaders).
- Friendly/team reward rule: when a player is in a team, rewards/buffs to friendly players still affect only teammates (collected centrally via MC/FTB/OPAC); when the player is not in any team, they affect all online players instead. This is now unified for random event buffs, the Unlimited Bank Card Star Coins, the Investigator sign’s Living Page, and the Truth-Revealed Investigation effect; handbook/tooltip text now notes “all players if not in a team” (both loaders).
- Hostile HP threshold unified: the kill checks of the Undercover Detective sign's "Key Clue" and the Cursed Sword chip changed from "more than 20 HP" to "at least 20 HP" — hostiles with exactly 20 HP (10 hearts) now count (tooltip/handbook text updated to "at least 20 HP" as well) (both loaders).
- Chip rarity standard: a new chip's rarity is now determined by its **icon border colour** (blue = rare / purple = epic / gold = legendary; added as a mandatory rule in AGENTS.md); four chips were corrected accordingly — Member Recommendation and Piggy Bank (epic → rare), Smart Watch (rare → epic), Big Bowl Stew (epic → legendary) (both loaders).

### Bug Fixes

- Fixed the `PiglinAiMixin` injection signature: vanilla `PiglinAi.isWearingGold` takes a `LivingEntity` in 1.21.1; it was written against `Player`, causing a runtime Mixin apply failure (invalid descriptor) in the modpack — it now accepts `LivingEntity` and applies the Netherrack-die check only to players.
- Fixed missing tooltips on newer dice: Obsidian, Netherrack, Weird, Crimson, Amethyst, Ender, and Nether Star dice were not entering the dice tooltip branch, so their unique descriptions were absent — they are now handled as dice (both loaders).
- Fixed dice-tooltip coloring breaking around translated `%%`/placeholder tokens: dice tooltips containing `%%` or `%s` (e.g. the Emerald die's 20%% discount) are now expanded via `translationString` into a single `Component.literal`, so Minecraft no longer splits the format token into an unstyled fragment and the `%` sign and following text keep the intended colors (both loaders).
- Fixed the broken coloring of “Dice Blessing + duration” in every die tooltip: the shared `dice_desc` line no longer places the duration inside a legacy-color range that translation/placeholder handling can split; it is now assembled from independently styled fragments (prefix / blessing+duration / middle / key / suffix), so “Dice Blessing (60s)” stays fully blue (both loaders).
- Fixed text after numeric highlights in die tooltips being wrongly colored gray (including newer die unique descriptions and the anvil star-upgrade hint): they used `§7` (gray) as the “restore” marker, so gold/red/green/dark-green lines turned gray after the highlighted number; changed to `§r` (true reset back to the line’s own color), keeping the following text consistent with each tooltip’s intended color (both loaders).
- Removed the potentially misleading “Does not trigger a Dice Blessing” line from the Amethyst die tooltip, keeping only the ranged/magic combat-die description (both loaders).
- Fixed durability numbers on some combat-card tooltips not being colored yellow: medium/large/epic/Shadow Strike/Meito/Charge/defense medium/large/epic still used `Component.translatable`, so the `%s` durability value became an unstyled fragment; they now use `tt()` to expand first and render as one literal, keeping the remaining-uses number yellow (both loaders).
- Added the missing `curios:dice` curios-slot tag for the Emerald Die (previously it could not be equipped into the dice slot), and synced it into the `astral_dice:dices` summary tag (both loaders).
- Death-cleanup adjustments: totem-canceled deaths no longer trigger any cleanup; Misaki loses all Sword Qi stacks on death (tooltip note added); Papara's active effect is cleared on death; Bonnie keeps investigation progress on death (unequip only); Komachi/Rin effect-card damage bonuses survive death; removed the leftover no-reader DamageEffectBonus reset in the death handler.
- Fixed the Komachi sign's active failing when play progress existed or the cooldown was running: the play-count +1 is now a banked extra-play token (consumed per actual play, persists across windows, unaffected by burst-full or cooldown), and the old boolean flag plus its leftover calls were removed.
- 1.20.1 recipe fixes: the "Koi's Rulebook" (patchouli:guide_book) recipe was in the wrong folder (singular "recipe") and thus never loaded — moved to "recipes"; the Cursed Sword chip's generated recipe used the 1.21 result.id format and failed to parse on 1.20.1 — regenerated in the 1.20.1 item format (generation sources fixed in both versions).
- Fixed chip tooltips rendering embedded newlines as box glyphs (Sandwich - Gourmet / Adrenaline - High-Grade / Satellite / Revenge Halberd / Cursed Sword Enigmatic Legacy+ link, etc.): multi-line lang values are now split into separate tooltip lines instead of keeping real `\n` inside a single component.
- Completed item aggregate tags: newly added chips/signs were missing from the `astral_dice:chips` / `astral_dice:signs` aggregate tags — 12 chips (Adrenaline - Regular/High-Grade, Warp Engine / Energy Recycler / Electric Sword / Advanced Peripherals / Perpetual Motion / Current Core, Big Bowl Stew / Member Recommendation / Bookmark / Piggy Bank / Smart Watch) and 2 signs (Gunsmith / Pandaman); both loaders' aggregate tags now contain every chip (55) and sign (17) (both loaders).
- Fixed the `tooltip.astral_dice.chip.smart_watch` color-code count mismatch between zh_cn and en_us (the English string omitted the highlight on "1"); both languages now use aligned coloring (both loaders).

### Project

- Integrated JEI into the dev test environments for recipe checking (forge-1.20.1 15.56.0.205 / neoforge-1.21.1 19.39.0.372); the 1.20.1 dev pack mods now come from curse maven via modImplementation so production mixin mods load in the dev environment.
- 1.20.1 now depends on Mixin Booster (`mixinbooster` 0.1.3, mandatory): Sponge Mixin takes over mixin execution at runtime with automatic Mojmap→SRG remapping, replacing the MDG LegacyForge `mixin` extension/refmap/annotation-processor setup (the villager-trade mixins no longer need a build-time refmap).
- Ported the newer dice (Obsidian/Netherrack/Weird/Crimson/Amethyst/Ender/Nether Star), creative-tab ordering, tooltip coloring, and the Ender Die teleport changes from 1.21.1 to forge-1.20.1; the 1.20.1 Netherrack-die piglin-neutral `PiglinAiMixin` follows the Mixin Booster / Sponge Mixin convention (consistent with NeoForge/Fabric) and does not use Forge-native or other third-party Mixin APIs (1.20.1 sync).
- Integrated ModernFix into the test environment: 1.21.1 copies `modernfix-neoforge-5.27.24+mc1.21.1.jar` from the modpack to `run/1.21.1/mods` (`install_test_mods.ps1`); 1.20.1 injects it into the dev run classpath via `modImplementation "maven.modrinth:modernfix:OvpPdk44"`. Both launch verifications now detect ModernFix's `Total time to load game and open world was` loading-complete log (base wait 30s, re-check every 15s if absent).
- Builds now push to integration packs by default: `gradlew build` deploys to run/mods, the root build/libs, and both pack mods directories automatically (previously required `-PdeployToPack`; the flag remains accepted for compatibility, and missing pack roots are skipped).
- Completed the Patchouli handbook: added all 11 previously undocumented chips (6 Charge / 1 Healing / 4 General) to the Ren's Rulebook and a new "Charge Chips" category (`chips_charge`, parent "Chips", ordered between Mark and General with `chips_other` shifted down); every chip (55) now has a handbook entry (both loaders).

## 1.1.3

### New Content

- During the Dice Blessing, cards can no longer be inserted into or removed from the dice card interface (server-authoritative guards + client-side blocking), and the screen shows a red warning at the top (both versions).
- Effect-card copy/refund now covers every effect card — the Komachi sign, Magic Tome and Magic Quiver no longer exclude any card (healing/damage/exclusive cards included; exclusive copies are bound to their new owner) (both versions).
- Patchouli handbook: already present on 1.21.1; newly ported to 1.20.1 (109 pages + 175×2 guide keys, new Patchouli 1.20.1-85-forge dependency, Patchouli added to the 1.20.1 modpack; the 4 Enigmatic-Legacy+ link texts rewritten for Enigmatic Legacy).
- Handbook category icons (both versions): Getting Started = No.1 Player Sign, Materials = Star Coin, Cards = Epic Attack Card.

### Bug Fixes

- **1.20.1 handbook registration completed**: the earlier port copied only the book pages (assets) but missed the book definition (book.json), crafting recipe, creative-tab book item and book model, so the book item was missing and no page text (including the integration texts) was reachable; all are now in place (the recipe uses the 1.20.1 NBT format) and republished.
- The effect-removal guard no longer swallows the mod's own removals — a new internal-removal channel (ModEffectRemoval) keeps ready/count/healing indicators from lingering forever (both versions).
- 以毒攻毒 removed effects while iterating the live view, risking a ConcurrentModificationException on the server tick — now iterates a snapshot (both versions).
- Copy/refund mappings missing card ids silently granted King Power — unified into BaseEffectCardItem.cardByTypeId covering every effect card (both versions).
- Random card pools diverged: ALL now includes 以毒攻毒 and BATTLE includes defense cards per its contract (both versions).
- Parunan's in-hand right-click bypassed cooldown/wait checks and could fire the active unexpectedly — right-click now only equips/replaces, actives are triggered via the keybind/equipped path only (both versions).
- The FTB Teams UUID-member branch never added anyone — aligned with the OPAC branch (both versions).
- AoE victims (cleave / directional blast) no longer take a second full dice-combat hit (both versions).
- Nancy Lu unequip wiped invulnerability/invisibility it never granted, and its onCurioTick expiry check was always true at the default value, stripping other sources' invisibility every tick — now only self-granted state is cleared (both versions).
- The mark's glow shares the mark's lifetime (milk/effect clear no longer removes it alone) and no longer shows a HUD effect icon (both versions).
- Patchouli "Format error" entries: literal `%` in guide texts (18 entries, zh+en) escaped to `%%`; the 4 tooltip keys with the same latent issue fixed too (1.21.1; the 1.20.1 handbook was ported already-escaped and the same 4 tooltip keys were fixed).
- Client damage numbers never expired due to a wrong-bus subscription plus a network-thread race (1.21.1; 1.20.1 was already correct).
- Skill renames: Fen's active 运攻 → 运功 and Nancy Lu's active 远程骇入 → 远程侵入 (both versions).

### Project

- Versions: 1.21.1 = `1.1.3-rc1+neoforge_1.21.1`, 1.20.1 = `1.1.3-pre1+forge_1.20.1`; rebuilt and published to each modpack (old artifacts auto-cleaned).
- ModEventHandlers subscribers moved to their feature homes (DiceCombatEvents, ModTooltipHandler, LootInjectionHandler, AnvilUpgradeHandler, PlayerLifecycleHandler, ModEffectEvents, PlayerTickEvents, plus sign/chip/card/manager classes).
- Removed the charge-deferral timing code for Charge cards (the charge_defer attachment and branches) — impossible now that the card slots lock; Full Power is always refunded when the Blessing ends (both versions).
- Removed the write-only PlayerResourceRegistry (PlayerResource/ResourceType and the healing/starlight registration stubs) (both versions).
- Full code audit and cleanup (led by 1.21.1, synced to 1.20.1): removed per-tick effect re-apply sync packets (cutter/revenge-halberd/healing), per-tick enchantment ResourceKey allocation, per-tick full recompute while a menu is open (20-tick throttle) and double isActive evaluation; consolidated duplicated damage-number senders and card-type mappings; removed 8 unused data components, the isChipItem chain, redundant dice ctor args, empty overrides, unused methods/imports and dead branches.
- Repository restructured as a multiloader monorepo (1.21.1-main → neoforge-1.21.1/, 1.20.1-forge → forge-1.20.1/; no shared common source set across MC major versions).
- Formerly git-excluded content (AGENTS.md, docs/, temp/, scripts/, run/, deploy.ps1, etc.) migrated; tools/check_lang_sync.py moved to the repo root tools/.
- CI (build.yml) builds both subprojects with dual JDKs (17/21) and runs the lang sync check for both.

## 1.1.2-rc1

### New Content

- **Revenge Halberd**: tooltip now shows the current attack/defense bonus; a status effect with the halberd's own icon appears while either bonus is active and is removed when the bonus ends.
- Added a timer guard: all timed effects of this mod now strictly tick at 20 t/s, immune to buffs that speed up or slow down effect durations (e.g. the Blazing Core from Enigmatic Legacy+).
- The card selector now displays **3 columns**, showing more cards at once.
- Synced local zh_cn.json text updates (Mimi sign tooltip wording, Fate Guidance description).
- Removed the separate CurseForge and Modrinth changelog files and their references.

### Balance Changes

- Unwavering and Berserk durations adjusted to **3:00**.

### Bug Fixes

- Fixed Charge converting to Full Power immediately when placed during an active Dice Blessing: a Charge placed mid-blessing is inactive for the current blessing and now only takes effect on the next blessing, converting to Full Power when that blessing ends.
- Fixed the glowing effect lingering after the mark disappears: glowing now shares the mark's lifetime (no more infinite duration), is refreshed on layer transitions, and ends together with the mark.

### Project

- Version updated to `1.1.2-rc1`.
## 1.1.1-rc1

### New Content

- Mimi sign reworked: passive now grants +1 Star Coin per battle card gained from crafting, rewards, or card returns, +1 chip slot while equipped, and a random chip every 25 Star Coins (Blue 60% / Purple 35% / Gold 5%); active now recycles all cards in the inventory (including exclusive cards) and returns N+1 random cards (exclusive cards excluded).
- Added a generic Suspicious Stew recipe; Cursed Sword now uses the `astral_dice:suspicious_stews` tag.

### UI & Display

- Added Nancy Lu "Remote Hack" effect icon and description; sign active-skill ActionBar now uses localized names; fixed Fate Guidance note color.

### Bug Fixes

- Fixed the healing icon not being removed when its timer ends without re-triggering.

### Project

- Version updated to `1.1.1-rc1`.

## 1.1.0-rc1

### New Content

- Added Fight Poison with Poison: removes up to 3 vanilla negative effects and grants Regeneration.
- Added Blue Curse: -20% armor and zero armor toughness.
- Added chips: Vitamin Pill, Cursed Sword, Revenge Halberd, Piercing Gun, Candy, Friendship Badge, Satellite.
- Added Nancy Lu (Hacker) sign.
- Added Hand Fan Small and Hand Fan Big chips.

### Content & Balance Changes

- Adjusted Hand Fan Small recipe; Hand Fan Big now uses the generic Blue→Purple upgrade recipe.
- Simplified Berserk recipe to 1 Gunpowder + 1 Star Coin.
- Cursed Sword kill bonus now triggers at most once per Dice Blessing; cap default 16, max 32.
- Astrologer and Ninja signs upgraded to Epic and now require Diamond Dice.
- While fully invisible, mobs cannot target the player.
- Creative chip tab regrouped by Starlight, Healing, Mark, and No-school; Cutter chips moved to Healing.

### UI & Display

- Healing chips and Slime sign now show current healing points.
- Fixed sign key, percent sign, and counter symbol coloring in tooltips.
- Fixed an extra blank line around Blue Curse in the Cursed Sword tooltip.
- Moved sign and material tooltip keys out of the card category.

### Bug Fixes

- Fixed Great Detective passive not triggering from active skill or Undercover Investigation kills.
- Fixed missing Vitamin Pill tooltip; picking up cards no longer triggers it.
- Friendship Badge recipe now requires an Instant Healing potion.
- Fixed tooltip color-code issues causing some text to appear white.

## 1.0.3-rc1

### Content & Balance Changes

- Defense card durability is now only consumed in PvP: when attacking another dice-holding player, both gain Dice Blessing and the defender consumes defense card durability once per blessing; monster attacks no longer consume it.
- Added a defense-to-armor conversion: 1 defense = 2 armor.
- Defense bonuses from effect cards, signs, events, and chips always convert to armor at 1:2 regardless of Dice Blessing; only battle defense cards add directly to defense points.
- Reworked Unwavering: grants +2 defense and Resistance II for 1:00.

### Project

- Version updated to `1.0.3-rc1`.

## 1.0.2-rc1

### Content & Balance Changes

- Adjusted dice chip slot counts (0★~3★): Basic 0/1/2/3, Golden 1/2/3/4, Diamond 2/3/4/5, Netherite 3/4/5/6.
- Adjusted per-side attack/defense card slots: Basic 3, Golden 4, Diamond 5, Netherite 6.
- Actively triggering Dice Blessing no longer consumes defense card durability.
- When a player attacks another player with a dice, both dice-holding players automatically gain Dice Blessing, and the defender's defense card durability is consumed at most once per blessing.
- Bonnie sign kill rewards now grant only attack cards, and attacking players grants no card.
- Defense bonuses from effect cards, signs, events, and chips now count as armor value by default, and become defense points while Dice Blessing is active.
- Defense cards only provide defense points during Dice Blessing.

### UI & Display

- Added active/passive skill names to all sign tooltips and moved the key hint to the top.
- The sign key hint is now white, with the key symbol in yellow.
- The Fen sign active skill icon now uses the Fen sign icon.
- Unified tooltip formatting: blue time, yellow values, seconds for durations under 1:00, and parentheses around potion effect times.

### Bug Fixes

- Removed the generic “Skill activated” ActionBar after sign skills trigger, preventing it from overwriting sign-specific messages.
- Fixed missing damage numbers on affected targets for splash/AOE damage (Fen cleave and Directional Blast AOE).
- Custom effects created by this mod can no longer be removed by milk, honey bottles, or `/effect clear`; only player death can remove them.
- Fixed some effect states not being reset on player death; sign-ready, cleave, play-count, Magic Quiver, Fate Guidance, and investigation states are now reset properly.

## 1.0.1-rc1

### Content & Balance Changes

- Reworked the healing system with an independent timer: healing triggers every 30 seconds by default (only during Dice Blessing).
- Healing Points heal ×2 when Dice Blessing triggers, and halve when the timer ends.
- Cutter chips now trigger above 60% of max HP, preventing them from being unable to trigger in most situations.
- Misaki sign: active skill attack bonus increased to +4 and duration increased to 2:00.
- Papara sign: the second active effect now treats the player as both full HP and below half HP regardless of current health.
- Bonnie sign: passive card reward now only applies when killing hostile targets with at least 20 HP.
- Chocolate Cake and Hamburger now heal 20%/40% of max HP; Luxury Feast heals 30% of the user's max HP and also heals teammates and teamless players.
- Buffer Shield trigger cooldown changed to 15 seconds.
- Adjusted battle card durability values: Medium/Large/Epic=10, Shadow Strike=10, Meito=5, Charge=1, Full Power=2.
- Charge now refunds Full Power after blessing, with fallback check and ActionBar message.

### Bug Fixes

- Fixed Dice Blessing target and weapon checks: it now only triggers with melee weapons and no longer triggers on friendly, passive, or non-angered neutral mobs.
- Fixed sneak-right-click auto-equip for dice/chips/signs.
- Fixed battle card durability bar display issues and missing tooltip, restored remaining uses display.
- Fixed an unexpected recursion crash when Fen sign triggered damage cleave.
- Healing effect no longer shows potion particles.

### Quality Improvements

- Added ActionBar feedback for several actions, and players affected by events also receive ActionBar messages.
- Fanny random event messages now show the triggered event name.
- Fine-tuned card display in the dice card selection GUI.
- Replaced the card selection background texture.
- Fixed gradlew permission in CI and added artifact upload.
- Updated README with mod introduction.
- Version updated to `1.0.1-rc1`.

## 1.0-rc1

### Card Selection GUI Rewrite

- Used the new card container texture.
- Right side now shows attack/defense `<min>-<max>` ranges, updating live as cards are added or removed.
- Added a card selector at the bottom: attack on the left, defense on the right, sorted by inventory order, with scrolling support.
- Added support for returning a held card to the inventory by clicking the storage area or right-clicking.
- Shadow Strike, Meito, Charge, and Full Power icons are scaled and aligned to the bottom-right.

### Durability System

- Battle cards now use vanilla Minecraft durability data (damage/maxDamage), recognized by Durability Tooltip mods.
- Removed the custom "remaining uses" tooltip text from battle cards.
- Defense cards now correctly consume durability when the wearer takes damage.

### Recipe Adjustments

- Komachi sign: Iron Ingot → Echo Shard.
- Haiqing sign: Lapis Block → Prismarine Crystals.
- Magic Tome: center Book and Quill → Echo Shard.
- Magic Quiver: center Spectral Arrow → Echo Shard.
- Ninja Star: Lodestone → Redstone Block.

### Effect Card Feedback

- Shows an ActionBar message when play count is exhausted; cooldown uses the longest remaining time among all effect cards.

### Project Configuration

- Version updated to `1.0-rc1`.

## 1.0-SNAPSHOT.23

### Item IDs & Tags

- Effect cards unified to `effect_card_*`.
- Chips unified to `*_chip`.
- Living Book Page renamed to `effect_card_living_page`.
- Added item tags: `dices`, `combat_cards`, `effect_cards`, `is_exclusive`, `signs`, `chips`, `materials`.

### Recipes

- Basic dice recipe changed to Redstone + Quartz Block.
- Golden/Diamond/Netherite dice now use the `dice_upgrade` shaped upgrade; removed the Netherite dice smithing recipe.
- Adjusted many battle card, effect card, sign, and chip recipes.
- Chip generic upgrade templates:
  - Blue→Purple: `LGL/GTG/PPP`
  - Purple→Gold: `RDR/DTD/GGG`
- Directional Blast changed to a shaped symmetrical recipe.

### Loot

- Star Plates can now be found in all vanilla chests.
- Golden Star Plates can be found in `minecraft:chests/trial_chambers/reward_ominous`.

### Creative Tab

- Materials moved to the front of the creative tab.
- Adjusted the order of effect cards and Living Book Page.

### Values & Tooltip

- Boxing Gloves attack: +2/+4/+8.
- Speed Skates movement speed: +5%/+15%/+25%.
- Moto Helmet defense: +2/+4/+6; High tier additionally grants +2 armor toughness.
- Sandwich max health: +4/+8/+12.
- Buffer Shield cooldown changed to 30 seconds.
- Flashlight: +1 attack per 4 Starlight.
- Added effect card tooltip hints for play count and current-cycle damage bonus.
