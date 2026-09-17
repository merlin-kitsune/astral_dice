# Astral Dice 1.2.0 — Player Changelog

> Applies to **1.1.3 → 1.2.0**, for both 1.21.1 (NeoForge) and 1.20.1 (Forge). Build suffixes: `+neoforge_1.21.1` / `+forge_1.20.1`.

**In one line:** 9 new dice, 2 new signs, 16 new chips, 4 new materials and a brand-new **Charge** playstyle — plus reworked card slots, dice upgrades, defence conversion, and a full text/colour pass.

---

## ✨ New Content

### Dice (9)

| Dice | Effect |
|---|---|
| **Nether Star Die** | The new top tier (T4). Card slots and costs are **always at the highest tier**; each star grants **+2 attack/defence**; comes with an enchantment glint |
| **Ender Die** | On lethal damage, a pseudo-Totem of Undying saves you (revive at 1 HP) and **teleports you to safety**, then a 5:00 cooldown; but damage taken in rain/underwater is **+40%** |
| **Crimson Die** | **+50% chance of high rolls (4-6)**; the price is 6 damage to yourself when you roll a 1 |
| **Weird Die** | **Sign active cooldowns -50%**; the price is +50% chance of low rolls (1-3) |
| **Amethyst Die** | Ranged/magic attacks also roll the combat die and add the roll as damage (no Dice Blessing, no card durability consumed) |
| **Netherrack Die** | Mining in the Nether: 30% extra Star Coin, 5% Star Plate; **piglins stay neutral** |
| **Obsidian Die** | +3 defence (= +6 armour); fire damage taken **-70%** |
| **Emerald Die** | Pay villagers with **Star Coins** at a 20% discount |
| **Glass Die** | Combat card values are **always maxed**; dying loses this die and all cards equipped in it |

### Signs (2)

- **Gunsmith**: passive "Weakness Insight" — dodge/counter or hit a Broken target to gain a layer (max 4; each layer +1 attack/defence and +1 minimum dice roll); active "Weak Counter" — **wait 30 s**, then hitting a normal hostile applies Broken (2:00); missing consumes no cooldown. A Broken target's dice can only roll 0 and it gets dodged — after dodging, the Gunsmith counterattacks.
- **Pandaman**: active "Devour" — gain a random healing card and apply Taunt (1:00) to hostiles within 16 blocks (taunted mobs can only attack you, and attacking you triggers a counterattack); passive "Good and Bad" — Hamburgers grant +2 max health (up to 100, removed on unequip) and burgers/cakes heal allies within 8 blocks.

### Chips (16)

- **Charge (10, new playstyle)**: Warp Engine (teleport → +2 Charge), Energy Recycler (+1 per **150 m** travelled; +5% speed at 5+ Charge), Electric Sword (+1 attack per 4 Charge; 10 kills → +2 Charge), Advanced Peripherals (+4 attack at 4+ Charge), Perpetual Motion (on a blessing, **Charge below 6 is topped up to 6**), **Current Core** (gain Charge on skill use; while a sign skill is cooling down, spend Charge to **finish the cooldown instantly**), Electric Glove (damage cards splash to enemies within 3 blocks), **Airbag** (lethal damage costs 6 Charge to negate — saves you *before* a Totem of Undying), **Railgun** (spend 6 Charge to call lightning on enemies around the target 1 second later — **lightning damage = 50% of that hit's final damage, minimum 5** — then a 1:00 cooldown), Primordial Core (spend Charge to gain Empower stacks).
- **Healing (1)**: Big Bowl Stew — when a Dice Blessing ends, heal allies within 16 blocks (players also gain +1 Healing Point).
- **General (5)**: Member Recommendation (random card each blessing), Bookmark (+1 damage effect card damage), Piggy Bank (3 Star Coins every 2 effect cards), Smart Watch (kills drop cards while you carry fewer than 10), Whetstone (below 50% HP: +4 attack, -2 damage taken, and a single hit can never kill you).

### Everything else

- **New "Charge" playstyle**: a player-level stack resource (max 20) gained from movement, teleporting, kills and triggering blessings. **While you have Charge, sign-active and effect-card cooldowns are 20% shorter**; stacks also convert into attack/speed or can be spent by the Current Core to end a cooldown.
- **New effect "Empower"**: +1 attack/defence per stack (**max 10 stacks**), losing 1 stack every 0:30 (with a real countdown in the effect panel); unequipping the chip clears every stack.
- **4 new materials**: Regeneration Reagent, Conductive Wire, Star Coin Dust (animated) and Mark Paint.
- **First-join gift**: you now receive the *Rules of Love* guidebook on your first join (can be disabled in the common config).
- New status effects: **Taunt**, **Broken**, and **Standby** (while the Astrologer / Secret Detective / Gunsmith are waiting).

---

## ⚖️ Balance & Quality-of-Life

- **Card slots now scale with stars**: 0★=4 / 1★=6 / 2★=8 / 3★=12, split evenly between attack and defence (no longer tied to dice rarity).
- **Dice upgrades use tier tags**: Golden/Diamond/Netherite dice can be upgraded from any die of the same tier (and the T4 Nether Star Die from any T3 die).
- **Effect-card rules**: the single-round play limit is a fixed **9 cards** (a **global** cap that no source can exceed); the Ninja's "+1 play" now applies **only to the current card cycle, at most once per cycle** (it is not carried across cycles, and it is refused — **without starting a cooldown** — while on cooldown, at the 9-card cap, or once already used this cycle); the **Living Page is now cumulative** — each use adds +1 for the current cycle and stacks with other sources up to the cap of 9; a round ends only when every effect card's progress *and* cooldown are finished.
- **Sign changes**: Signboard passive reworked (25 recycled battle cards → a random chip); Sweeper's active cooldown is reduced by 50% after playing Express Delivery; Investigator grants 1–2 Living Pages as needed; Secret Detective is no longer targeted by mobs while invisible during the investigation stage; Hacker's guaranteed bonus is now **+2**; the **Hacker's active is now true invisibility** (no potion particles, and your own armour / held item / curio renders are hidden), **any attack** (melee / ranged / projectile / magic) that hits a hostile or player breaks it and pays out the bonus, and the pearl-fall immunity is now **cancelled at the earliest damage check** (no red flash, no screen shake, no hurt sound or knockback); the **Astrologer now awards "Fate's Guidance" to the killer** of a Weakened target.
- **Numbers & formulas**: Unwavering is now **+8 armour**; Full Power durability 2→5; Healing Point cap fixed at **32**; Vitamin Pill now triggers on "craft or gain a card"; Satellite restocks once per 1:00; Cursed Sword cap is a concrete **+16**; friendly/team rewards are unified (**all online players when you have no team**); the "20 HP" threshold is now consistently **at least 20 HP**; movement-distance sources (Energy Recycler and the Sweeper sign) now count **3D movement including vertical**.
- **Unified defence conversion**: defence from cards/signs/chips is converted into **real armour** (1 defence = 2 armour); mob armour now uses the same ÷2 formula as players.
- **Counterattack playstyle removed**: the lingering effect is gone; the Gunsmith's dodge-counter and Pandaman's taunt keep their **single-hit counterattack**.
- **Effect badges**: inventory effect levels are shown in **Arabic numerals** and support up to level 100 (vanilla stopped at X).
- **Slimmer config**: many values are now fixed constants, so the config is much shorter (version 1 → 2; your old file is backed up automatically).
- **Text & colours**: tooltip terminology, measure words and colouring were unified (non-time numbers in yellow, times in blue, "effect name (duration)" fully blue).
- **Charge and Empower no longer emit potion particles** (icons, stacks and countdowns are unaffected).
- **Renamed effect**: "Take a Bite" is now **"Drain"** (the status applied by the Vampire sign's active).
- **Bountiful integration completed**: a new decree, **"Astral Bounties"** — a **global decree that is not bound to any profession** (boards of every profession can offer our bounties); 13 requirement / 92 reward entries (**legendary dice, chips and signs never enter the reward pool**).
- **Guidebook**: the playstyles chapter was rewritten for **four playstyles** (with a new Charge page), a "Charge chips" chapter was added, and every chip now has an entry and a recipe page.
- **Dice & star upgrades**: anvil star upgrades now cover **all 13 dice**; the Nether Star Die tooltip **no longer shows the star-cost line**; the Weird / Crimson dice now roll their high / low values **75%** of the time (was 50%), matching the "+50% chance" wording.
- **Effect stacking**: **Unwavering now stacks up to 3 layers** (+8 armour each; re-use stacks and refreshes the duration); "Fate's Guidance" now reduces an active cooldown by **half of its current maximum**.
- **Other rulings**: **Taunt only affects hostile mobs** (never players); the Eight-Sided Dice **keeps** its accumulated points when Starlight is full; Well-Rested resets to 0 on death; the Flashlight strictly grants **+1 Starlight per target**; the Star Coin Hammer runs a **storability pre-check** before spending coins (coins never drop on the ground just because your inventory is full); Big Bowl Stew's friendly check is now "your own or same-team tamed pets and mounts, plus ownerless pigs / striders".

---

## 🐛 Bug Fixes

- "Empower" no longer shows an absurd duration — **the countdown and stack decay work again**.
- Added the missing curio slot tags on 5 Charge chips (they could not be equipped on 1.21.1 at all).
- Added the missing name keys for the Gunsmith's 3 new effects (they showed raw translation keys).
- Fixed the 1.21.1 piglin-neutrality Mixin signature (Netherrack Die's effect was silently dead).
- Added missing tooltips for 7 new dice.
- Fixed **broken colouring** caused by `%%`/placeholders in tooltips (emerald discount, blessing lines, etc.).
- Fixed text turning grey after a highlighted number.
- Fixed combat-card durability numbers not being highlighted, and several chip tooltips rendering newlines as boxes.
- Added the missing summary tags on 12 chips and 2 signs, plus the Emerald Die's curio slot tag.
- Fixed 1.20.1 issues where the **"investigation stage" event never fired** and **multi-layer Marks all vanished at once**.
- Fixed the 1.20.1 guidebook recipe path and the Cursed Sword recipe format.
- Fixed death cleanup: the Vampire's active effect clears as intended; Ninja/Investigator damage bonuses persist; the Guardian's sword-qi is lost on death (noted in the tooltip).
- Fixed the Ninja's active failing when a play count was already banked or the round was cooling down.
- Added the **15 missing guidebook recipe pages** and fixed the Whetstone entry's unescaped `%`.
- Corrected sign/chip texts that contradicted the actual rules (Vampire, Whetstone, Big Bowl Stew, Signboard, Cursed Sword and more).
- Fixed Charge **partially** never being deducted (which silently disabled every **Railgun / Current Core / Advanced Peripherals** spend).
- Fixed Mark / Healing / Weakness Insight stacks never decreasing (vanilla ignores a lower-level write).
- Fixed decaying effect stacks (Healing / Mark / Weakness Insight / Empower) **flickering** in the HUD.
- Fixed the Emerald Die trade still showing **emerald costs on the client** (it now shows Star Coins, and the villager XP bar / level refresh the moment you trade).
- Fixed **dodges not preventing on-hit effects** (such as a husk's empty-hand hunger) and an **infinite recursion in Broken counterattacks**.
- Fixed the bounty decree missing its name (the board showed "Invalid Decree"); it now reads **"Astral Bounties"**.
- Effect icons: Weakness Insight / Mark / Healing now use their carrier item's icon, and Blue Curse uses its dedicated icon again.
- Text: removed the "Defence X (Armour Y)" wording, deleted the redundant cyan chip line from the Nether Star Die tooltip, fixed tooltip colour fallbacks (`§7` → `§r`), switched blessing durations to `M:SS`, and fixed the Star Coin Hammer tooltip containing `%`.

---

## 📌 Requirements

- **Required**: Curios API (**1.20.1 additionally requires Mixin Booster**).
- **Optional integrations**: Patchouli (guidebook), Bountiful.
- **Version gate (multiplayer)**: the client and server must share the same **major.minor version** (`1.2.x` ↔ `1.2.y` interoperate; a `1.1.x` client **cannot join** a `1.2.0` server, and vice versa). Cross-version connections are **refused with a clear message** instead of failing silently.
- Upgrading from 1.1.3: **just swap the jar**. Some obsolete config options were removed in 1.2.0, so on first launch your old config is backed up to `.bak` and a fresh one is written; removed options no longer apply (those values are now fixed constants).
