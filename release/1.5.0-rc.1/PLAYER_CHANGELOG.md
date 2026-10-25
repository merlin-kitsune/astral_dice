# Astral Dice 1.5.0-rc.1 Changelog

Released: 1.5.0-rc.1 (**pre-release / Beta**) — covers everything since 1.3.7. This is a large version jump:
a full **shop system** (Card Shop and Chip Shop blocks plus the Card Trader and Chip Merchant villager professions),
a new **event system** (12 events, event-card animation, event selection screen), **two bizarre-grade signs**
(Kangel / Jirai-ko), **two new effect cards** (Barricade / Repulsion, the latter also has an item form),
**5 new chips**, a **combat rework** (split defense formula with 1:1 conversion, mobs included in dice combat,
single declining dice roll for combat cards, defense cards 150 durability, vanilla damage modifiers restored),
a **combat stat HUD**, and a license change to GPL-3.0-only.

## Installation and requirements (read first)

- **StarEngine is no longer bundled (NeoForge 1.21.1 / Forge 1.20.1)**: the embedded library has been removed from the
  shipped jar. StarEngine (`starengine`, **1.1.0**) is now a **separate requirement** — download the matching
  `starengine-<loader>-1.1.0.jar` and put it in `mods`. The version **must match** the one this build was compiled against (1.1.0);
  a missing library fails startup with "Missing or unsupported mandatory dependencies".
  The NeoForge 26.1.2 and Fabric 1.20.1 lines **still bundle** it.
- Mixin runtime on 1.20.1: **Mixin Booster ≥0.1.3** or **Sinytra Connector** — either one is enough.
- Fabric 1.20.1 requires **Fabric API** and **Puzzles Lib** (which ships Forge Config API Port);
  accessories use **Trinkets** (optionally compatible with **Accessories**) — Trinkets alone or Accessories alone both work,
  only having neither refuses to start.
- **Cards can no longer be crafted**: 22 card recipes were removed; cards come from the Card Shop / Card Trader / dice and signs.
- 1.3.7 and older worlds can be upgraded directly; shops, events and villagers are new content and never rewrite existing items.

## New content

### Shops and villagers

- **New block "Card Shop"**: crafted from 3 smooth stone slabs / 3 star coins / 3 logs; new Blockbench wooden counter model
  (with a glass display window) that faces the player who placed it.
- **The Card Shop UI was rebuilt and buying is now drag-and-drop**: hold a card and drag it to your inventory to buy it —
  the card follows the cursor, releasing it in an invalid slot cancels without charging. Empty slots show the "sold out" style.
- **The shop now polls every 60 seconds**: the stock only changes when the timer hits zero; the hourglass shows 4 stages
  (no more seconds); one transfer and one starlight exchange per round (buying is unlimited); reopening the shop no longer rerolls it.
- **Equipping the Business sign or triggering "Store Sale" no longer rerolls the whole shelf**: missing cards are filled in
  (sign +1 combat card, sale +2), while existing cards, prices, sold state and timer stay untouched; removing the sign
  or ending the sale never shrinks the shelf — that change waits for the next reroll.
- **Starlight exchange and transfers are inside the shop**: **2 starlight = 1 star coin** (no cooldown, with a hint when short);
  transfers send **5–20** star coins to other online players, with a name search box and a scrolling list.
- **New block "Chip Shop"**: draws and sells chips in sets; candidates get quality-colored frames (rare blue / epic purple /
  legendary gold) with 24×24 icons; long chip names scroll left and right; hovering a candidate (or its label) shows its tooltip.
- **5 new chips**: Flashlight-Flashbang, Lucky Slip, Reagent-Abyss, Wireless Power Pole, Sensing Device — all craftable.
- **New villager profession "Card Trader"**: the Card Shop block is now a job site; villagers claim it and trade cards.
- **New villager profession "Chip Merchant"**: the Chip Shop block is a job site; traders sell chips
  (quality tied to level: L1 rare / L2 rare+epic / L3 epic / L4 epic+legendary / L5 legendary, weights 6/3/2);
  journeyman unlocks "1 emerald → 1 star coin" (hidden while an emerald dice is equipped). Each profession now uses its own
  job-site tag, so profession-viewer mods no longer list both as the same block.
- **"Lottery Box"** (formerly Event Lottery Box): right-click to buy a lottery ticket for **5 star coins**, once per **5:00**;
  new texture and recipe (star compass · star coin · star compass / star coin · diamond dice · star coin / star compass · star coin · star compass),
  with a recipe page in the guidebook. "Event Lottery Ticket" became **"Strange Question Mark"** with a new icon.
  Card Shop, Chip Shop and Lottery Box are no longer stackable.

### Event system

- **The event system was rewritten**: each event has a name, icon, type (positive / neutral / negative — text color only),
  tier, scope and registerable trigger, with per-event timers (re-triggering resets them).
- **12 events implemented** (scope is "trigger within 64 blocks ∪ whole team"):
  **Overheat Warning** (+5 attack, 3:00) | **Haste** (Speed II, 2:00) | **Storm Approaching** (1 random card; nearby hostile mobs +2 attack, +2 defense, 2:00) |
  **Learn From Each Other** (lowest-health player +8, highest −8) | **Store Sale** (+2 cards on sale, 5:00) |
  **Hand Erasure** (randomly discard a card) | **World Shatters** (instant 4 damage) | **Gift From Above** (1 random card + 3 star coins) |
  **Angel Descent** (4 healing) | **Questionable Takeout** (instant 2 damage + Poison/Hunger 0:15 each + no healing for 15 s) |
  **Crowded Corridor** (all players teleported to the trigger) | **Combat Rush** (2 "Attack - Extra Large" cards).
  Storm Approaching also doubles hostile mob spawns at night near affected players.
- **Event card animation**: a card pops up in the center of the screen (0.2 s fade/scale in → 1.0 s hold → 0.2 s fade out),
  with the event icon on top and the name (colored by type) plus timer below; the event resolves ~1.9 s later together with its sound.
  Card text now fades out with the card instead of lingering.
- **The Great Detective sign's active skill became an "event selection screen"**: 2 random events are drawn,
  hovering highlights, clicking selects, confirming triggers. A **15-second countdown** sits at the top;
  timing out, dying or leaving triggers the first option; confirming closes the screen and starts the event flow immediately (no extra second).
- **Secret Detective "Investigation Stage"**: only killing the "Hidden Investigation" target raises a stage
  (the current stage resolves first, then it upgrades); 4 stage icons are wired in (I / II / III / Revelation),
  and multiple secret detectives in a party track their stages independently.
- **Two ways to draw events**: the "Event Voucher" item and the "Event Drawer" block (now renamed "Strange Question Mark" and "Lottery Box").

### Signs and effect cards

- **Two new bizarre-grade signs, Kangel and Jirai-ko** (both crafted from amethyst dice + 2 golden star compasses):
  - **Kangel**: active "Cyber Angel" instantly grants (1 + fans) star coins (up to 10; at ≥3 fans also fully heals,
    at ≥9 nearby friendly players each get 3 star coins); passive "Little Angel's Greeting" turns targets you attack
    or that attack you into "fans" (permanent), and fans deal 1 less damage to you; a party Jirai-ko also counts.
    Fan cap 10, kept across death, cleared when the sign is unequipped.
  - **Jirai-ko**: active "Emotional Overindulgence" heals 2× your "Love" stacks and grants 1:00 of +20% movement speed,
    then removes 4 stacks and raises the cap by 1 (max 32); passive "Unbearable Love" gives 6 base defense and
    +1 "Love" every 30 s when hit by hostiles (starts at 2 stacks, cap 4); each stack gives +1 attack and −1 defense
    (never below 0), and each stack above 4 gives +1 max health; a party Kangel gains 6 base defense and each Jirai-ko
    gains +1 "Love" whenever she is hurt.
- **New effect card "Barricade"** (rare): right-click a block face to place it (up to 4.5 blocks, no card consumed on failure).
  A visible entity plus invisible collision blocks: 30 durability, −1 per mob hit, 1.5 blocks tall, blocks players and mobs
  like a fence, catches arrows and bolts in the block layer (also −1), and cannot be pushed by explosions.
  Every 4 ticks it pulls aggro from non-elite, non-boss hostiles within 6 blocks. When destroyed (durability, `/kill`,
  or `/astralparty killbarricade`) it releases aggro, removes its collision blocks and plays a wood-break sound.
- **New exclusive effect card "Repulsion"** (bizarre; owner only): place on a block face (up to 4.5 blocks, not consumed on failure).
  A collision-less field entity with **50 durability / 20:00 lifetime** that every second pulls in hostile / angered-neutral mobs
  **that currently have an aggro target** within 12 blocks and applies Slowness (0:10) and Weakness (0:10),
  yanks anything past 12 blocks back to 11.5 blocks, and **never draws aggro**; grabbed creepers cannot detonate.
  Mobs and environmental damage do nothing to it; the placer's **5th melee hit recycles it into item form**
  (anyone else just gets a message). Exclusive cards are excluded from the random pool, recipes and bounty pools.
  The item form is identical to the card form and carries **durability / remaining lifetime / charge already granted**,
  restored on re-placement. The item is **hidden from the creative tab and JEI** (still obtainable and usable).
- **New `/astralparty` cleanup subcommands**: `/astralparty killbarricade [<radius>|all]` and
  `/astralparty killrepulsion [<radius>|all]` (permission 2; 64 blocks by default, `all` for the whole dimension)
  for leftover entities you cannot get an id for and `/kill` cannot remove.
- **The Business sign was rewritten**: the active became **"Online Shopping"** — remotely open the Card Shop
  (buying, starlight exchange and transfers all inside), then a 120-second cooldown. The "Legendary Merchant" passive became five lines:
  +1 star coin on entering the shop; +1 combat card for your own and teammates' shops; each card has a 10% chance to drop to
  1 star coin; after a transfer the first non-discounted card drops to 1 star coin; starlight exchange gains +50%.
- **Slime sign's "Cell Division" passive now triggers at most once every 15 seconds** (still +1 healing and −10 s active cooldown).
- **Gunsmith sign active/passive reworked**: the active now marks a target with "Opening (2:00)";
  the decay of "Weakness Insight" and the Weird Detective's "Reasoning Time" now uses its own 1-minute timer
  instead of hanging off Dice Blessing.

### Interface and presentation

- **New combat stat bars (defense and attack)**: the vanilla armor row becomes the mod's defense bar (the vanilla armor bar and
  Overloaded Armor Bar's armor bar are hidden), and the attack bar sits right above hunger; 10 icons per row, 2 points per icon,
  with full and half icons; above 20 points it collapses to "1 full icon + number". Values come straight from the combat formula,
  and the vanilla armor number is no longer shown. **The attack bar excludes weapons in hand** (combat still uses the full value).
- **The attack bar now shows every attack bonus**: client evaluation used to be short-circuited by per-site client guards,
  so the HUD only reflected some modifiers (chips like the Electric Sword worked server-side but never moved the number);
  the client now runs the whole chain in "display mode", excluding only **weapons in hand** and **dice-combat random rolls**.
- **Jade shows the mod's defense**: hovering a player shows the mod's defense value with full/half/empty shield icons;
  mobs still show their vanilla armor.
- **Shop blocks gained Jade cooldown readouts**: "restock in M:SS" on the Card Shop and "enter in M:SS" on the Chip Shop / Lottery Box.
- **Chip status icons switched to borderless chip art** (11 effects), and the chip shop's three quality frames were restored.
- **The Railgun "ready" indicator was removed**, and **Whetstone / Airbag gained "cooling down" indicators**
  whose duration equals the remaining cooldown.
- **New generic "card picker" UI component**: server menu plus client screen, 1 or 3 slots, optional card type filter,
  double-click or drag to fill, confirm only when full, Esc or closing consumes nothing.
- **5 new custom sounds and 1 rewritten**: the four damage effect cards have dedicated cast/hit sounds, events have their own
  trigger sounds, a wallet sound plays on a successful shop purchase, and the Shock Gloves gain spark FX and a shock sound.
- **Pixel particle library imported**: 179 hand-drawn 32×32 particle textures by shuimo0413 (MIT).

## Combat rework

- **The defense formula is split between players and mobs, and the 20-armor hard cap is gone**:
  player `defense = 2 + armor×0.30 + 0.85×toughness`; mob `defense = base defense + armor×0.40 + 1.0×toughness` (floored).
  Armor always uses the final attribute value, so status effects and other mods' armor modifiers apply first and are never re-converted.
- **Player base defense is now 2 (one shield), down from 4**; base attack is unchanged (1 bare-handed).
- **Defense points and armor are back to a 1:1 conversion**: every "defense +X" source now really grants X defense —
  the old code added `X × 2.0` armor which, after the coefficient, paid out only **0.6X** (0.8X for mobs).
  After the fix, Moto Helmet +2/+4/+8, Obsidian Dice +10 armor (= +3 defense), Dorothy's warmth +1 defense per stack,
  Jill's +2 defense per defense card and Storm Approaching's +2 mob defense all match their text.
  **Player-side defense bonuses are ×1.667 as strong as in the previous build (×1.25 for mobs)**.
- **Defense no longer loses a point to rounding**: armor is read as the double final value instead of the floored
  `getArmorValue()` — 26.6667 armor used to be truncated to 26, so "defense +8" only delivered +7.
  Dice combat and the piercing gun use the same single entry point, keeping "what you see = what is resolved".
- **Mobs now have base attack and their melee attacks join dice combat**: hostile base attack 4, neutral 3, passive/friendly 0;
  mob attack = base attack + attack attribute + weapon enchantment bonus (the already-mitigated damage event value is no longer counted).
- **Dice combat requires both sides to have dice equipped**: if either side has no dice, both rolls count as 0,
  but **the dice formula still applies** (players without dice no longer fall back to vanilla combat).
- **Combat cards now roll once with declining weights**: "roll twice, take the max" is gone; the lowest value is about twice as
  likely as the highest. Max-roll chances drop sharply: 1~3 from 55.6% → 25%, 1~6 from 30.6% → 11.8%,
  1~10 from 19% → 6.9%, 1~20 from 9.8% → 3.4%; expected values drop 25%–32%.
  The Meito's raised floor still applies; Glass Dice and the Office Worker's "take the max" are unaffected.
- **Defense card durability 10 → 150**: every dice-combat hit costs all equipped defense cards 1 point each; a card at 0
  is removed and refunds its cost. Ranged, magic and skill damage never consume defense cards.
- **Vanilla damage modifiers apply inside dice combat**: Sharpness, crits (vanilla conditions, ×1.5 on the base segment only),
  attack cooldown (`0.2 + charge²×0.8`), Protection and Resistance (post-resolution multipliers on both the player→mob and
  mob→player paths) are all rebuilt and match vanilla (Sharpness V +42.9%, crit +50%, full Protection IV −64%, Resistance II −40%).
- **Damage numbers show the final value and are colored by damage type**, anchored where the hit resolved instead of following the target.
- **Effect card cooldowns are fully separated from effect durations**: playing a card starts the cooldown immediately.
- **The healing timer is decoupled from Dice Blessing**: it now resolves automatically every 1:00.
- **The Curse charm card's damage type became magic damage** (it used to be true damage).

## Balance and numbers

- **Card stack size 64 → 16** (combat cards included).
- **Chip economy tuning**: new `enable_chip_recipes` switch (off by default; when off the creative tab hides the four crafting
  materials); blank chip loot chance 3% → 1% (buried treasure stays 100%); buying from the chip shop starts a 5:00 cooldown,
  and right-clicking a blank chip into the shop during that cooldown clears it and grants +1 reroll (once per 2:00).
- **The four damage effect cards (Monster Laser, Monster Brick, Rail Cannon, Directional Blast) were rebuilt**:
  the "remote/magic damage +N for 1:00" buff is gone; holding the card enters target selection (hostiles ∪ neutrals
  (pets excluded) ∪ untamed tamables, never players) and confirming deals **magic damage** (fixed 4 / 6 / 8 / 5 plus
  effect-card bonuses). Directional Blast also hits everything within 6 blocks for the same damage.
  New FX delay the damage to the moment of impact (a red laser beam 0.5 s after casting, a red brick dropped on the target's head,
  a golden beacon beam, an explosion for the blast).
- **Each effect card now declares its own lock-on range**: Monster Laser 32, Monster Brick 16, Rail Cannon 24, Directional Blast 24,
  Living Page 32, Curse charm 32; supportive effect cards share 16.
- **Shock Gloves reworked**: using a damage effect card spends 4 charge so that card's remote/magic damage also hits targets within
  3 blocks; the spread is bound to that single use and does not chain with Directional Blast's 6-block blast.
- **Airbag gained abilities**: after negating a lethal hit it counters the attacker once (through the shared counter-damage path);
  taking damage now grants +1 charge (15-second cooldown, tracked independently).
- **7 existing chips adjusted**: Bank Card-Restless (32-block radius when not in a party), Big Bowl of Stew (16-block players when
  not in a party), Magic Quiver (returns the card actually used), Eagle Eye Sight (bonus becomes extra attack damage),
  Warp Engine (shared cooldown 5:00 → 3:00), Perpetual Motion (blessing top-up 6 → 8, vanilla melee cooldown −50% while equipped,
  without inflating dice-combat attack), Bookmark (effect card damage +1 → +2).
- **ATM rewritten**: every transfer to another player gives both sides 1 starlight and 2 star coins.
- **Membership Letter gains an ability**: all cards in your own shop cost 1 star coin less while equipped.
- **Weird Dice no longer shortens sign active cooldowns** (still keeps the low-roll weighting raised from 50% to 75% and heals 5 on active end).
- **Recipe changes**: the golden star compass keeps two recipes (1 compass + 1 nether star shapeless / 3 compasses + 6 star coins shaped);
  star coins and star compasses now drop from kills; 16 sign/chip recipes had their materials adjusted; the chip shop recipe is
  "gold ingot · star compass · gold ingot / polished blackstone bricks · blank chip · polished blackstone bricks / 3 polished blackstone bricks".
- **The starlight exchange "base value floor" bug is fixed**: the +4 / +7 starlight from Bank Card-Low Balance / High Balance
  **can be exchanged now** (they used to act as a permanent floor that was always subtracted from the exchangeable amount).
- **All star coin payouts now prefer the wallet**: Haiching, Bonnie, Hanna, Mimi, Gift From Above and the Great Detective passive
  no longer stuff the inventory (and drop on the ground when full); they credit the wallet first and only hand out items when the wallet is off.

## Bug fixes

- **A 100% startup crash in production was fixed**; **the world-join / tooltip-hover crash caused by an Architectury conflict was fixed**.
- **Party detection was completely broken and is fixed**: teammates could still damage each other and the railgun still hit allies.
- **Third-party melee weapons could not trigger Dice Blessing and now can**: melee detection is back to a blacklist
  (only bare hands / shields / ranged-only items / blocks / gun bodies / non-weapon tools are excluded).
- **Mobs spamming milk to clear effects (server lag) is fixed**; **"Mark" can now be cleared with milk**.
- **The Ninja sign's active was almost unusable and is fixed** (it now requires fewer than 9 cards played this round).
- **King's Power's 8-point cost now always applies**; **the Sweeper sign's attack/defense bonuses now survive death**.
- **"The guidebook is re-issued every launch" is fixed**; parsing the guidebook recipes without Patchouli installed no longer errors.
- **Accessories slot icons render again**; Fabric's "Concealment" effect gained its HUD icon.
- **Permanent status effects now display ∞**; **status effects now show a description** (values, duration, trigger).
- **Medkit's infinite healing via relog / dimension changes is fixed** (it only resolves when actually equipped and on respawn).
- **Repeated star coin payouts from re-entering the shop are fixed**; **a full inventory no longer blocks chip shop purchases**
  (the chip drops at your feet).
- **Chip Shop's purple-black missing texture, backwards screen, Card Shop not facing the placer, and wrong break particles are fixed**;
  the Chip Shop was added to the axe mineable tag.
- **Damage effect cards occasionally dealing 1 less damage is fixed**; Fabric 1.20.1's halved FX duration is fixed;
  hit sounds no longer stack.
- **Jirai-ko's active resolving multiple times on natural expiry is fixed**; **two Kangel PvP misjudgements are fixed**
  (the fan marker and the wearer's indicator no longer share one status).
- **Card picker**: non-interactive card frames, dropping cards on death, session leaks, swapping with occupied slots when dragging back,
  and permanent sessions after an open failure are all fixed.
- **Misaki discount face**: with the Misaki sign equipped, the shop shelf draws the discounted "Meito Gawu-chan" face (3 points);
  unequipping restores the original face.
- **The Repulsion item is hidden from the creative tab and JEI**; chips in the creative tab / JEI are now sorted by rarity
  (the five archetype groups and their order are unchanged).

## Text and guidebook

- The guidebook gained a **"Blocks"** category (with the Card Shop recipe page) and a pink cover; a new "Combat system changes"
  entry summarizes this development line's combat changes for players.
- Guidebook defense examples now use the real base: `2 + armor×0.30 + 0.85×toughness`, and
  `1 defense = 1 ÷ armor coefficient armor` (≈3.33 for players, 2.5 for mobs).
- Internal-mechanic notes were removed from tooltips and the guidebook, and the three damage effect cards' descriptions were
  trimmed to "how to use + magic damage".

## License changes

- **Source code is now GPL-3.0-only**: from 1.5.0 on, the source, Gradle scripts, `tools/` and `scripts/` utilities and
  `docs/` are released under GPLv3 (version 3 only).
- **1.3.7 and earlier remain MIT** (all four lines' releases and pre-releases); that grant is irrevocable.
- **In-game assets (textures, models, animations, particles, GUI, fonts, sounds, guidebook text) remain CC BY-NC-SA 4.0**;
  recipes / loot / tags / worldgen JSON and `lang/*.json` follow the code into GPL-3.0-only.
- StarEngine switched to GPL-3.0-only as of 1.1.0 (its 1.0.0–1.0.12 and already-distributed 1.1.0-pre.x builds remain MIT).
