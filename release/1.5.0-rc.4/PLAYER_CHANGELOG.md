# Astral Dice 1.5.0-rc.4 — Player Changelog

Released: **1.5.0-rc.4 (pre-release / beta)** — covers **everything** since 1.3.7. This is a large version jump. Main changes:

- **Shops**: the Card Shop and Chip Shop blocks, the Card Trader and Chip Merchant villager professions, and the Loot Box block;
- **Event system**: 12 events, event-card animation, and the Great Detective's event-choice screen;
- **Content**: **seven new signs** (KAngel, Ame, Sister Sign, Big Fat Fish Sign, Dorothy Sign, Bartender Sign, Lingling Sign; 24 → 31 signs),
  two new effect cards (Barricade, Reinforced Repulsion, the latter also as an item), and **6 new chips** (pool 61 → 67);
- **Balance**: 10 existing signs, 16 existing chips and the Weird Dice changed mechanically or numerically; two “on obtain” paths no longer count crafting;
- **Combat rework**: split player/mob defense formulas, 1:1 defense conversion, mob attacks rolled into dice battles, damage floor at 15% of attack, battle cards now single-shot decreasing dice, defense-card durability 150, vanilla damage modifiers restored, and color-coded damage numbers;
- **UI**: combat stat bars (defense / attack), Jade integration, shop cooldown countdowns;
- **License**: source code is now GPL-3.0-only.

> **This pre-release ships two builds only**: `astral_dice-1.5.0-rc.4+neoforge_1.21.1.jar` (Minecraft 1.21.1 / NeoForge)
> and `astral_dice-1.5.0-rc.4+forge_1.20.1.jar` (Minecraft 1.20.1 / Forge). Other platforms are out of scope for this pre-release.

## Installation and requirements (read first)

- **StarEngine is no longer bundled** (NeoForge 1.21.1 / Forge 1.20.1): the shipped jars no longer contain `META-INF/jarjar/`.
  StarEngine (modId `starengine`, **1.1.0**) is now a **separate dependency** — download the `starengine-<loader>-1.1.0.jar`
  for your loader and drop it into `mods`. The version must match the one the mod was built against; if it is missing
  the game refuses to start with “Missing or unsupported mandatory dependencies” — check the library jar first.
- Mixin runtime for 1.20.1: **Mixin Booster ≥0.1.3** or **Sinytra Connector** — either one, not both.
- **Cards can no longer be crafted**: all 22 card recipes were removed. Cards now come from the Card Shop, the Card Trader,
  dice, and signs.
- Worlds from 1.3.7 and earlier upgrade in place; shops, events and villagers are additive and never rewrite existing items.

## New content

### Card Shop (new block)

- **Recipe**: top row 3 smooth stone slabs, middle row 3 star coins, bottom row 3 logs. Place it and **right-click to open** — free, no cooldown.
- **Stock**: **3 cards** on the shelf by default (no duplicate names in one batch), **base price 3 star coins**;
  a bought slot shows “Sold Out”; **the same batch stays** until the **60-second** restock timer hits zero (closing and reopening does not reroll).
- **Business sign synergy**: if someone on your team wears the Business sign, one extra **battle card** is stocked and each card has a **10%** chance to be discounted to **1 star coin**.
- **Battery cards** only appear when your **dice is 2★ or higher**.
- **UI**: a wooden counter with a glass case (Blockbench model, collision and selection box tightened to it); magenta “Card Shop” title centred;
  6 card frames with the card art filling each frame and the full name shown (font shrinks when long); discounts are marked **bottom-right of the card art**;
  left button = starlight→star coin exchange, middle button = transfer (greyed out below 5 star coins or while cooling down);
  the hourglass shows **cooldown progress**; star-coin balance bottom-left, your inventory bottom-right.
- **Buying is drag-and-drop**: hold a card and drag it into the inventory to buy — the card follows the cursor and the source slot shows the “Sold Out” backing;
  dropping it anywhere else returns it (no charge); **you cannot drag a card you cannot afford** (red text shows the price); an occupied slot asks you to pick another.
  Empty slots all share the “Sold Out” look.
- **A purchase counts as “obtaining”**: Vitamin Pill healing and Till the Fox's foxfire stack both trigger; a wallet sound plays on success.
- **Detailing**: buttons are icon-only (explanations moved to tooltips); the transfer screen renders completely on its own (no shop content behind it);
  transfers use **four amount buttons** (5 / 10 / 15 / 20) with a name search box, a scrolling list, and each row showing head, name and current balance,
  with a pink arrow marking the selection. A successful transfer returns to the shop and **you cannot transfer twice in one visit**.

### Starlight exchange and transfers inside the Card Shop

- **Starlight exchange**: **2 starlight = 1 star coin**, **no cooldown**, once per visit (a hint appears when you lack starlight).
  This is where the Business sign's old “cash out” ability went.
- **Transfers**: send star coins to **other online players**, **5–20 per transfer**, **60-second** cooldown afterwards
  (shared timer with the shop restock, keeps counting while offline); once per visit. If the target logs off before confirming,
  the list refreshes and you pick again.
  **A Business-sign wearer who completes a transfer** forces the **first unsold card** on their shelf down to 1 star coin.

### Chip Shop (new block)

- **Recipe**: `gold ingot · star plate · gold ingot / polished blackstone bricks · blank chip · polished blackstone bricks / polished blackstone bricks ×3` (shaped).
- **Three chip slots** per visit, drawn by weight from chips **you do not own yet** (no duplicates in a batch); each slot shows its own icon and **all three share one price**.
- **Price**: starts at **10 star coins**, **+5 for every chip you have bought**, capped at **100** (not reset by restocking or reopening).
- **Rarity weighting by dice stars**: 0–1★ = rare 60% / epic 37% / legendary 3%; 2★ = 40% / 45% / 15%; 3★ = 25% / 50% / 25%.
- **School weighting**: chips of a school **you already have equipped** are more likely (**+60% each**); the **category you bought last time** gets **×2** on slot 1 and **×1.25** on slots 2–3.
- **Rerolls**: **3** to start, max **5**; the timer only begins **after your first reroll** and recovers 1 every **30 minutes** (accumulates while away);
  when exhausted the reroll button just flashes red and sends nothing. You **cannot open the shop** with a full chip collection or a full inventory (a hint is shown).
- **Buying**: the chip goes straight into your inventory and the screen closes; **the shop then has a 5:00 cooldown**.
  While cooling down, **right-click a blank chip** into the shop to clear the cooldown and gain **+1 reroll** (once per 2:00).
  With a full inventory the trade still **goes through** — you are charged, the counter advances, the reroll happens —
  and the chip **drops at your feet**; in creative mode it is simply consumed (no drop, no message).
- **UI**: cost row = small star-coin icon plus amount; reroll shows **remaining count only**; icons are **24×24** with a **rarity ring** under each slot
  (rare blue / epic purple / legendary gold); long chip names **scroll horizontally**; the selected slot's name turns **black**;
  hovering a chip (**either the slot or its label**) shows the tooltip. The shop **draws no title** and has **no discount path at all**
  (Membership Letter only discounts the Card Shop).

### Villager professions

- **Card Trader** (Card Shop block is its workstation): novice/apprentice sell **rare** cards (2–6 emeralds), journeyman/expert **epic**,
  expert/master **legendary** (up to 20 emeralds), with prices nudged by card strength; from **journeyman** onward it also offers **1 emerald → 1 star coin**.
  That trade is **hidden while you wear an emerald dice**.
- **Chip Merchant** (Chip Shop block is its workstation): sells chips with rarity tied to level — L1 rare / L2 rare + epic / L3 epic /
  L4 epic + legendary / L5 legendary, appearing with weights 6 / 3 / 2; journeyman also unlocks **1 emerald → 1 star coin** (hidden with an emerald dice).
- Both professions now use **dedicated workstation tags** (the vanilla shared tag is kept, so villagers still claim the blocks),
  and mods that inspect professions no longer show both blocks as the same job site.

### Loot Box (formerly “Event Loot Box”)

- Right-click to buy a loot card for **5 star coins**, limit **one per 5:00** (insufficient balance or cooldown is refused via ActionBar red text);
  new texture and recipe (`star plate · star coin · star plate / star coin · diamond dice · star coin / star plate · star coin · star plate`), with a manual recipe page.
- The item “Event Loot Card” is renamed **Strange Question Mark** with a new icon (using it draws one random event from the pool; consumes 1 in survival).
- The Card Shop, Chip Shop and Loot Box blocks are now **non-stackable**.

### Trading-chip adjustments

- **Membership Letter** gains: while equipped, **every card in your own shop costs 1 star coin less**; it **stacks** with the Business sign's 10% discount and
  **floors at 0** (a 0-cost card is free).
- **ATM rewritten**: every transfer to another player grants **both sides +1 starlight and +2 star coins** (only triggers when the initiator wears it).
  The old “+1 starlight on equip” and “+40% starlight exchange” abilities are **removed**.
- **The starlight “base floor” bug is fixed**: the +4 / +7 starlight from Bank Card-Low Balance / High Balance now **converts normally to star coins**
  (they used to be treated as a floor on your balance, so that amount could never be exchanged).

### Event system (rewritten)

- Every event now has a unified name, icon, type (positive / neutral / negative — **text colour only**), tier, scope and registrable triggers,
  plus its **own timer** (retriggering resets it).
- **12 events** (scope = 64 blocks around the trigger ∪ the whole team):
  - **Red Heat Warning** (attack +5, 3:00, positive)
  - **Haste** (Speed II, 2:00)
  - **Storm Incoming** (1 random card; hostile mobs in range gain +2 attack and +2 defense, 2:00; **at night, hostile natural spawns ×2 near players carrying it**)
  - **Trade Strengths** (lowest-health player +8, highest −8; **heals first, damages 1 second later**)
  - **Shop Sale** (shops stock +2 cards, 5:00)
  - **Hand Wipe** (lose one random card — **it vanishes, it does not drop**)
  - **Earth Shatter** (4 damage immediately)
  - **Gift From Above** (1 random card + 3 star coins, **coins go straight into the wallet**, positive)
  - **Angel Descends** (4 healing)
  - **Questionable Takeout** (2 damage now + Poison / Hunger 0:15 each + **healing disabled for 15 seconds**, negative)
  - **Crowded Corridor** (all players teleported to the trigger, neutral)
  - **Battle Rush** (2 × Attack-Extra Large)
- **Status indicators**: Red Heat Warning / Storm Incoming / Questionable Takeout show their own icons; while Takeout is active a dedicated icon replaces vanilla Poison/Hunger.
  Red Heat's attack bonus and Storm's attack/defense bonuses **run through this mod's combat model** and are **transient** (lost on death or relog).
- **Event card animation**: the card pops up centre-screen — 0.2s fade/scale in → 1.0s hold → 0.2s shrink/fade out;
  upper cell is the art, lower cell the name (coloured by type: positive green / neutral yellow / negative red) plus the timer.
  **The event resolves about 1.9s (38 ticks) after the card appears** (28 ticks visible + 10 ticks buffer); the timer, effects and sound are registered on the same tick.
- **Event sounds**: `event_fade_in`, `event_heal`, `event_damage`, `event_poisoning`, `event_transport`, `event_bonus`.
- **Great Detective sign active is now an event-choice screen**: using it opens a centred panel drawing **2** events from the pool
  (duplicates possible; any mix of positive/negative/neutral); hovering highlights the card, clicking selects it, and **you must confirm** to trigger.
  A **15-second** countdown sits on top; on timeout, death or logout the first option is forced (a confirmed choice is never overridden).
  The active sound plays the moment the skill key is pressed, not on confirm, and confirming **closes the screen instantly** and starts the flow.
- **Secret Detective “Investigation Stage”**: only killing an **Undercover Investigation** target raises the stage (**triggers at the current stage first, then upgrades**),
  wired to 4 stage icons (Investigation I / II / III / Truth Revealed) with **1:00** duration at every stage;
  multiple Secret Detectives each track independently; **a target already carrying the mark cannot be marked again by anyone**;
  apart from Truth Revealed, the event only affects **the detective who applied the mark and the killer**.
  That event can **only be drawn when a Secret Detective is on the team**.
- **Two ways to draw events**: the Strange Question Mark item and the Loot Box block.
- **All star-coin payouts now go to the wallet first** (Haiqing, Bonnie, Hanna, Mimi, Gift From Above, the Great Detective passive, …)
  instead of being stuffed into the inventory (and dropped if full); the item is handed over only when the wallet is closed.

### New signs (7; sign count 24 → 31)

- **KAngel** (quirky; recipe: amethyst dice + 2 golden star plates)
  - Active “Net Angel”: gain (1 + fan count) star coins (max 10); at ≥3 fans heal to full; at ≥9 fans every friendly player gains 3 star coins
    (team scope with a team, otherwise players within 64 blocks).
  - Passive “Little Angel Greeting”: anything you hit or that hits you becomes a permanent **fan**, and **fans deal 1 less damage to you**.
  - Note “Two Hearts As One”: with an Ame on the team this passive applies to her too (her fans count for you). Fans cap at 10, persist through death, and reset when the sign is removed.
- **Ame** (quirky; same recipe)
  - Active “Overdose of Feeling”: heal (Love stacks × 2) and gain 1:00 of +20% movement speed; when it ends, lose 4 Love and raise the Love cap by 1 (up to 32).
  - Passive “Unbearable Love”: 6 base defense; each hostile attack taken grants +1 Love every 30 seconds (starts at 2, cap starts at 4);
    each Love gives +1 attack and −1 defense (never below 0); every Love above 4 adds +1 max health.
  - Note “Two Hearts As One”: with a KAngel on the team she gains 6 base defense, and whenever she is hurt every Ame on the team gains +1 Love.
    Love persists through death and resets when the sign is removed.
- **Sister Sign** (rare)
  - Active “Iron Maiden”: immune to all damage and harmful effects for **0:30**; if the passive was lost during a Dice Blessing it is restored immediately.
  - Passive “Courage”: each Dice Blessing trigger grants **+5 attack**; taking more than **10 damage** removes the bonus, and it is always removed when the blessing ends.
- **Big Fat Fish Sign** (quirky)
  - Active “Big Feast”: spend a random **1–20 star coins** (capped by your balance), gain attack and defense equal to the actual cost (**2:00**), then gain **3 random cards**; unusable without star coins.
  - Passive “Slacking”: standing still for **10 seconds** grants Fishing — no damage, no harmful effects and mobs ignore you;
    moving for a cumulative **5 seconds** ends it.
- **Dorothy Sign** (quirky)
  - Active “True Me”: teleport to a safe spot beside the target; for **1:00**, passing a friendly player gives **you +1 attack** (once per player, self stacks, cleared when the timer ends);
    that ally gains the status effect **Socialite** (the Dorothy Sign icon): **+1 attack** for **1:00** — timed from when it is gained and **independent** of the active timer,
    so the window ending does not remove it; reapplying only refreshes the duration;
    at **5 Warmth** stacks, consume them all to gain attack equal to your current defense (1:00).
  - Passive “Socialite”: restoring health grants **1 Warmth** (max **5** stacks; Instant Health and Regeneration count by effect level), each stack gives **+1 defense**;
    passing a friendly player heals both for **2 HP** (once every **10s**).
- **Bartender Sign** (quirky)
  - Active “Mix Life”: pick a target player, then choose **3 cards**. Effects apply in slot order, the cards are consumed and **3 random cards** are granted.
    Attack card ⇒ target **+2 attack** (2:00); defense card ⇒ target **+2 defense** (2:00); each effect card ⇒ the target instantly heals **2 HP** (**stacks per card** — three effect cards heal **6**);
    with **three effect cards** only that heal applies — no timer and no icon.
  - Passive “Change Life”: all three of the **same type** (all-effect excluded) ⇒ you and the target gain **Speed II (1:00)**; otherwise both gain **+3 star coins**; all-effect gives neither.
- **Lingling Sign** (epic; recipe: 4 star plates + 4 conductive wire + redstone block + diamond dice)
  - Active “Energy Surge”: gain **1 Reinforced Repulsion** immediately; convert your current charge stacks into attack and movement speed for **1:00**; cooldown **120 s**.
  - Passive “Interceptor”: **2 Reinforced Repulsion** on first equip; Barricades you pick up or buy are converted into Reinforced Repulsion;
    attacking a target clung to by your own field deals **+5 damage** (attacking any clung target grants the bonus, but only your own field grants charge);
    every new target caught by one of your fields grants **+1 charge**, capped at **5 per field** and **20 in total** (a target is never counted twice, deaths do not subtract).
  - Note: a Barricade in your hand is converted into Reinforced Repulsion; a thrown Reinforced Repulsion turns back into a Barricade on landing.
- Only the Lingling Sign's recipe is listed here; the rest are in JEI and the guide.

### New effect cards

- **Barricade** (rare): right-click the face you are looking at (up to 4.5 blocks, vanilla placement rules; a failed placement **does not consume the card**).
  A **visible entity plus an invisible collision block**: **30 durability**, loses a flat **−1** per mob attack (players can break it too);
  **1.5 blocks tall**, blocking players and mobs like a fence; arrows and crossbow bolts are caught at the block layer and also cost −1; explosions cannot move it.
  The server retargets non-elite, non-boss hostiles within **6 blocks** onto the barricade every 4 ticks.
  When durability runs out, a player breaks it, it is `/kill`ed, or it is removed by `/astralparty killbarricade`, it clears aggro, deletes the collision block and plays the wood-break sound;
  **no item drop, no XP**. Added to the random effect-card pool, the creative tab and the bounty reward pool.
- **Reinforced Repulsion** (quirky, exclusive — only the owner can use it): right-click the face you are looking at (up to 4.5 blocks; failed placement is free).
  A collision-free force entity with **50 durability / 20:00 lifetime**; every second it pulls hostile / provoked-neutral mobs that **currently have an aggro target** within **12 blocks**,
  applying **Slowness (0:10)** and **Weakness (0:10)**; each tick any target that escapes 12 blocks is dragged back to **11.5 blocks**; it **generates no aggro** (never rewrites targets);
  caught creepers **cannot detonate**; mob and environmental damage does nothing to it. **Every 10 seconds it loses durability equal to the number of caught targets (max 5 per tick).**
  The placer can **recover it as an item by landing 5 melee hits** (other players just get a hint).
  An exclusive card: **not in the random pool, no recipe, not in bounty pools**, and never offered by shops or villagers.
- **Reinforced Repulsion (item form)**: the recovered item carries **durability (max 50) / ticks survived (remaining life, max 20:00) / charges granted (0–5)**,
  and re-placing restores exactly those values; non-owners can neither place nor recover it. The item form is **not listed in the creative tab or JEI** (it is still obtainable and usable).
- **New `/astralparty` subcommands**: `killbarricade [<radius>|all]` and `killrepulsion [<radius>|all]`
  (permission level 2; radius defaults to 64 blocks, `all` clears the current dimension) — for leftovers that have no entity id and survive `/kill`.

### New chips (6; pool 61 → 67; rare 19 / epic 29 / legendary 19)

- **Flashlight-Burst** (legendary / starlight): attacking a hostile target **that this player has not attacked before** grants +1 starlight
  (dedup per player × target, cap 256); every 2 starlight adds +1 attack damage.
- **Fortune Slip** (epic / starlight): killing your first mob during a dice blessing gives you and friendly players within 16 blocks 1–7 starlight each (once per cycle).
- **Reagent-The Abyss** (epic / healing): +2 healing stacks on equip (−2 on unequip); healing procs grant absorption equal to **twice the effective heal** (yellow hearts);
  +2 when a dice blessing triggers and +2 at each **1:00** healing settlement (same cadence as the Medkit).
- **Wireless Power Pole** (epic / charge): +1 effect card play; whenever you gain charge, other friendly players within 8 blocks each gain 1 charge.
- **Sensor Device** (epic / mark): every 3 marks on a target gives it −1 battle dice roll (max −4); a successful dodge puts 1 mark on the attacker.
- **Extra Battery** (epic / none): sign active cooldowns are **−50%** while equipped (stacks with other cooldown reductions).
- Crafting for these 6 chips is governed by the **chip recipe master switch** (`enable_chip_recipes`), which is **off by default**;
  but the four chip-crafting materials (Regeneration Reagent / Conductive Wire / Star Coin Dust / Mark Paint) are not affected by it — they always show in the creative tab and JEI (Blank Chip and every chip itself stay visible too).

### UI and presentation

- **Combat stat bars (new HUD)**:
  - the **defense bar** takes over the armour row, the **attack bar** sits above the hunger bar; each icon is **2 points**, above 20 it collapses to “one icon + number”.
  - **F1** hides them together with the rest of the HUD; **held weapons are excluded** (weapon damage never enters the dice comparison).
  - the **attack bar now runs the whole attack chain** (previously only about a third of effects showed up):
    every bonus that actually competes in a dice battle (bank cards, electric sword, flashlight, star-coin hammer, King's Power, Berserk, protection, target mark, boxing gloves,
    cursed sword, Big Boss rage, investigation stage, collected attack cards, …) is now included; **battle dice rolls do not enter the bar**
    (the bar shows the stable range, the roll only happens during the clash).
- **Jade integration**: hovering a player shows **this mod's defense** (not just vanilla armour); hovering either shop block shows the **cooldown countdown (M:SS)**.
- **Chip effect icons**: 11 chips switched to **borderless** art, centred and enlarged (the old frames made them look oversized next to other icons);
  the Chip Shop restores **three-tier rarity frames** (rare blue / epic purple / legendary gold), matching the Card Shop and JEI.
- **Indicator changes**: the Railgun's “Ready” indicator is **removed** (it lit every cycle, so players could not tell when it was actually usable);
  Whetstone and Airbag gain **cooldown countdown indicators** (icon + remaining seconds, disappearing when ready).
- **Generic card-choice screen**: two layouts (1 or 3 slots) — danger left, resource in the middle, confirm right — unifying every “pick one of several cards” interaction;
  the logic is wired but **no skill uses it yet**, so it is not visible in game.
- **Audio**: 5 new sounds (`damage_effect_card_cast`, `monster_laser_hit`, `monster_brick_hit`, `orbital_strike_hit`, `directional_blast_hit`) and 1 rewritten (`damage_effect_card_hit`);
  Electric Gloves play `lightning_bolt_impact` on extra targets (volume 0.5 / pitch 1.5, once).
- **Particles**: the bundled **179-texture** Iron / Elden Ring pixel particle library (MIT) is exposed as the particle type `astral_dice:particle_library`.

## Combat rework

- **Separate defense formulas for players and mobs** (both floor the result):
  - Player: `2 + armor × 0.30 + 0.85 × toughness` (was `4 + armor × 0.30 + 0.85 × toughness`) — **base defense 4 → 2**.
  - Mob: `base defense + armor × 0.40 + 1.0 × toughness` (was `armor × 0.40`).
  - Mob base defense: hostile **4** / neutral **3** / passive **0** (passive mobs no longer benefit from hostile-grade armor efficiency).
- **Defense bonuses are now converted 1:1**: every point of “defense +X” from bank cards, signs and chips is worth exactly **1 defense**.
  The old implementation multiplied by 2.0 armor and then applied the 0.30 / 0.40 coefficient, so only 0.6X landed for players and 0.8X for mobs —
  **the effective strength is ×1.667 for players and ×1.25 for mobs** compared to the previous build, which is why this pre-release feels squishier. That is intended.
- **No lost fractions**: defense enters combat as a double, so the 0.6 in `2 + armor×0.30` is no longer rounded away.
- **Zero dice still uses the formula**: if either side has no dice equipped the roll counts as 0, but the attack/defense formulas still apply (no longer skipped).
- **Damage floor**: when defense ≥ attack, final damage becomes `max(1, attack × 15%)` (previously a flat 1);
  **reduction effects (Whetstone, Muscle Detective, …) apply after the final damage and may reduce it to 0**.
- **Battle cards now roll decreasing dice** (consecutive hits on the same target lower the die face by 1; a skill or a new target resets it):
  full-value chance 1–3 faces 55.6% → 25%, 1–6 faces 30.6% → 11.8%, 1–10 faces 19% → 6.9%, 1–20 faces 9.8% → 3.4% —
  **expected damage drops 25–32%**, flattening the “snowball in long fights” advantage.
- **Defense-card durability 10 → 150** (together with the 1:1 conversion, defense cards go from a one-shot shield to a real armor layer).
- **Dice blessing only triggers on a player's own melee attack**: mob attacks on players and the defender's roll no longer trigger it.
- **Vanilla damage modifiers restored**: Sharpness V **+42.9%**, critical **+50%**, Protection IV **−64%**, Resistance II **−40%** —
  the earlier rework had dropped them; vanilla is now applied first and the dice battle result stacks on top.
- **Damage numbers reworked**: the value is **the HP actually removed** (pipeline result), not the theoretical damage;
  **red** = attack power and damage bonuses (including splash / chain / counter), **green** = spell and skill damage;
  multiple hits in the same tick on the same target in the same colour **merge into one number**;
  **only the player who dealt the damage sees them** (the victim sees them when there is no player owner); the position is fixed by the server at settlement and **does not follow the target**,
  with **±30°** random spread so stacked numbers no longer overlap exactly.
- **Other flow changes**: card stack size **64 → 16**; effect-card cooldown **starts when the card is played** (each play in one cycle resets the 45s, charge caps at 30s);
  the healing timer now settles **every 1:00 independently** (no longer tied to the blessing cycle).

## Balance and numbers

- **Signs (the 24 existing ones, vs 1.3.7)**:
  - Business sign: active and passive rewritten (see “Card Shop” and “Trading-chip adjustments”);
  - Gunsmith sign: the active now marks one target with **Opening (2:00)** (their battle dice roll is locked to 0; cannot be applied to an already-marked target);
    the passive and the Muscle Detective's “Reasoning Time” each use their **own 1-minute timer**, shown as remaining time in the UI;
  - Great Detective: the 11 random events are gone, replaced by **event choice** (see “Event system”);
  - Secret Detective: the active now applies **1 mark** first, then Covert Investigation, and pays star coins = marks × 2 (at least 2 per use);
  - Hacker: the passive radius is **6 → 3 blocks**; the active's invisibility bonus is now a **flat +6 attack (2:00)** (no longer card cost ×2, no cards consumed);
  - Mechanic: active cooldown **2:00 → 3:00**;
  - Poster Girl: swap threshold **25 → 100**, and only **returns** count (crafting no longer does);
  - Slime: “Cell Division” now triggers at most once every **15 seconds** (still +1 healing stack and −10s active cooldown);
  - Mamushi: the batch of cards it throws lives **5s → 10s**; “Dragon Roar” now **requires the Dragon Roar card equipped**;
  - Cult Leader: “Fox Light” no longer counts **crafting** (only obtaining or equipping an attack card);
  - Game Master: shield wording unified to “5 yellow hearts + resistance + 1 counter” (numbers unchanged);
  - Padman Sign: “a roll of 6 ignores target defense (armour included)” is now explicit (mechanic unchanged);
  - Investigator / Ninja / Protector: wording unified (range → distance, “every 3 cards” → “after 3 cards”, burst noted as active-only).
- **Chips (existing)**:
  - Electric Gloves reworked: attacking with a damage effect card hits every target within 3 blocks (main target included) for **2 magic damage**, 1 second later;
    each target hit grants **+1 charge**; cooldown **1:00**;
  - Airbag: gains “+1 charge when taking damage (15s cooldown)”; void damage still cannot be cancelled;
  - Motor Helmet-Advanced: defense +6 / toughness +2 → **defense +8**;
  - Railgun: the lightning strike changed from “separate true damage, floor 5” to **“ignores target defense”**;
  - Member's Reference Letter: gains “all cards in your own shop cost −1 star coin (stacks with the Business sign discount, floor 0)”;
    the random card now only triggers while you hold **fewer than 5** cards;
  - Bank Card-Unlimited (out-of-team range tightened to **32 blocks**), Big Bowl of Stew (out-of-team **16 blocks**),
    Magic Quiver (returns **the card actually used**), Eagle Eye Sight (now extra attack damage, stacks ×2),
    Warp Engine (shared cooldown **5:00 → 3:00**), Perpetual Motion (refill tier **6 → 8** plus **−50%** vanilla melee cooldown while equipped, ≈ ×2 attack speed; dice battles do not gain attack),
    Bookmark (**+1 → +2**);
  - Bank Card-Low / -High / -Unlimited and Star Coin Hammer: starlight is now **granted once on equip and topped up after every successful exchange**
    (4 / 7 / 3 / 5, stacking, cap 32);
  - Vitamin Pills: crafting no longer counts (only obtaining any card grants +1 healing); Ninja Shuriken / Armour-Piercing Gun: wording only, numbers unchanged.
- **Dice and card notes**:
  - Weird Dice **no longer shortens sign cooldowns** (keeps the 1–3 weight 50% → 75% and +5 healing points when the active ends);
  - attack and defense cards drop “roll twice, take the max” for a single, decreasing roll (see “Combat rework”);
  - the four damage effect cards (Monster Laser 4 / Monster Brick 6 / Orbital Strike 8 / Directional Blast 5) changed from a 1:00 ranged/magic buff
    to “aim at a target, 0.5s delay, flat magic damage”, each with its own FX.
- **Drops**: star-coin kills — Zombie / Zombified Piglin **1%**, Enderman / Piglin **3%**, Piglin Brute **20%**;
  star plates by target — Wither **5**, Warden **3–5**, Elder Guardian **1–3**, Ghast **1**, Wither Skeleton / Guardian **5%**, everything else **0.3%**.
- **Acquisition**: golden star plate now has **two recipes**; **16 items changed recipe ingredients** (5 cards — Office Worker, Ninja, Hacker, Secret Detective, Mechanic — and 11 chips);
  blank chip buried chance **3% → 1%** (Buried Treasure itself stays 100%).
- **Creative tab / JEI ordering**: chips sort by **ascending rarity** (school order 星光 → 治愈 → 标记 → 充能 → none unchanged, rare → epic → legendary inside each);
  the Reinforced Repulsion item form is **hidden from the creative tab and JEI** (still registered, still obtainable; only visible with hidden items shown).
- **Chip recipe master switch**: `enable_chip_recipes` defaults to **off**; while off only the chip crafting recipes are affected; Regeneration Reagent / Conductive Wire / Star Coin Dust / Mark Paint and all chips (Blank Chip included) still always show in the creative tab and JEI (decoupled from the switch on 2026-10-18 — sign recipes need those materials).

## Bug fixes

- **1.20.1 refused to start (Forge)**: the StarEngine version range was written as a star, which Maven parses as an empty range ⇒
  `containsVersion()` was always false and every 1.20.1 player hit “Missing or unsupported mandatory dependencies”. Now an explicit range.
- **Jade crash on 1.20.1**: Jade's mixin handlers cannot be `static` under Mixin 0.8.5 (validation throws), changed back to instance methods.
- **Architectury tooltip constant-injection crash**: replaced the constant injection with a wrapped draw call, so toolchains stop fighting.
- **Unified team / hostile checks**: every check now goes through one path (scoreboard teams ∪ FTB Teams ∪ OPAC), fixing
  **about 30 hostile checks and 5 friendly checks** (previously each was hand-rolled, so teammates were still hit or counted as enemies).
- **Third-party melee weapons use a blacklist**: excludes empty hand / shield / pickaxe-shovel-hoe (**axes kept**) / ranged / blocks, so modded melee weapons work.
- **Mob milk-drinking lag**: non-player entities no longer take the vanilla per-frame recalculation, fixing lag when many mobs drink milk.
- **Marks can be cleared with milk** (the leftover glowing effect fades within 60 seconds instead of sticking forever).
- **Ninja active** now works while under the 9-card cap for the round (previously it required a full hand).
- **King's Power / Sweeper**: King's Power cost fixed at 8 points; the Sweeper's attack/defense bonuses **persist through death**.
- **Manual issues**: upgrading an old world no longer fails to grant the guide book, and Patchouli recipes are back (obtainable again, recipes visible in JEI / the book).
- **Medkit relog healing** no longer double-settles after relogging.
- **Card-choice screen**: lingering screen, accidental close, and lost materials after confirming are all fixed.
- **Protection discount card face** now shows the discount; **KAngel had two PvP misjudgements** (PvP targets counted as fans, fan damage applied in PvP);
- **Ame settled multiple times** from a single hit; **four damage effect cards were 1 point short**.
- **Repeated star-coin payouts from re-entering the shop** are fixed (one payout per batch); **full-inventory chip purchases being swallowed** is fixed (see “the trade still goes through” above).- **Bartender Sign passive "Change Life" behaves as originally designed again**: sharing one card type always grants Speed II (1:00) — including three effect cards; mixed types still grant 3 star coins each. The extra "all-effect cards grant neither Speed nor coins" rule was never requested and has been withdrawn.


- **Card Shop: buying a card no longer locks transfers**: the 60-second shelf cycle and the transfer lock used to share one timer, and that cycle starts as soon as you buy a card - so one purchase locked the transfer button until the cycle ended. They are now independent: the cycle only drives the hourglass and the starlight exchange, while the transfer lock is set only **after a transfer actually succeeds this cycle** and is cleared when the shelf rerolls. So you can still transfer right after buying; only a successful transfer locks the rest of the cycle.
- **Chip tooltips showed the raw key instead of "When worn as chip:"**: the `curios.modifiers.chip` / `.dice` / `.stand` strings were missing, so the tooltip printed the key name (most visible on 1.20.1). All three languages are now filled in.
- **Some tooltips showed a literal `\n`**: the bartender active-skill description had double-escaped line breaks in English / Japanese (1.21.1) and in every language on 1.20.1, so the game printed them as text. Fixed, plus a release check that language files contain no double-escaped lines.
- **Dorothy active-skill description in Simplified Chinese reverted to an older text**: an unrelated change rolled it back; restored.

## Known issues and design notes

- This pre-release covers **only the NeoForge 1.21.1 and Forge 1.20.1 builds**; other platform lines are out of scope.
- The generic card-choice screen has its logic wired but **no skill uses it yet**, so it is not visible.
- Crafting for the 6 new chips follows `enable_chip_recipes` (off by default); enable it in the config if you want recipe access.
- “Reinforced Repulsion” is an **exclusive card**: not in the random pool, no recipe, not in bounty pools. Its item form is deliberately hidden from the creative tab and JEI.
- This is a Beta pre-release; **numbers are still moving** (especially the difficulty curve after the 1:1 defense conversion). Feedback is welcome.

## Text and manual

- The Patchouli guide covers everything new: Card Shop / Chip Shop / Loot Box / events / **the seven new signs** / both new effect cards / **the six new chips**,
  and entries that still described the old defense formula were corrected.
- Battle-card and dice-battle text rewritten: “roll twice, take the max” is gone, the dice roll counts as 0 when no dice are equipped,
  the damage floor is 15% of attack (minimum 1), and both the player and mob defense formulas are spelled out.
- All three languages (zh_cn / en_us / ja_jp) are in sync — same key set, every new or changed entry translated.
- Tooltips and ActionBar hints share one voice (values highlighted, times coloured), and failure messages are no longer silent.- **Tooltips now say *what* a thing does, not *how* it is computed**: no more resolution order, stacking rules, timers/icons, stack caps or totals, and each tooltip stays within 4 lines. First cleanup: the effect-card line of the Bartender Sign active, shortened to "Effect card: target heals 2 HP" (all three languages).
- **Handbooks no longer repeat recipe lists**: the "Recipe: …" sentence is gone from sign handbook entries (each already has its own recipe page); 7 signs were affected and their skill text is unchanged.


## License changes

- **Source code is GPL-3.0-only from this version on** (1.3.7 and earlier were MIT — licenses already granted cannot be revoked).
- **Art and audio assets**: CC BY-NC-SA 4.0.
- **Data files** (recipes / loot tables / tags / worldgen JSON) and language files follow the code: GPL-3.0-only.
- **StarEngine (the dependency library) is GPL-3.0-only from 1.1.0**; 1.0.0–1.0.12 and the already-distributed 1.1.0-pre.x remain MIT.
- The bundled 179 particle textures come from the Iron / Elden Ring pixel particle library (MIT).

---

中文版见同目录 `PLAYER_CHANGELOG_ZH.md`（GitHub Release 正文会把中英两版拼接展示）。

