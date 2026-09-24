# Astral Dice 1.3.1 Changelog (Player Edition)

> Scope: **1.3.0 → 1.3.1** (this is an incremental note; see `release/1.3.0/` for the full 1.3.0 content). Applies to both 1.21.1 (NeoForge) and 1.20.1 (Forge). Version suffixes: `+neoforge_1.21.1` / `+forge_1.20.1`.
> The GitHub Release additionally ships a build of the third line, **26.1.2 (NeoForge)**, suffixed `+neoforge_26.1.2` (its version carries `-beta`; feature parity with the release lines).
> This file is the player-facing release note and corresponds one-to-one with the player-visible sections of **1.3.1** in `CHANGELOG.md` (engineering / tooling entries excluded).

**In one sentence**: the "hostile target" criterion is relaxed — a neutral mob no longer has to be provoked first (**an untamed wolf now counts as a hostile target outright**); the Piercing Gun no longer requires "a damage effect card has been used", so plain arrows get the bonus too; and the training dummy can finally be selected and hit; on top of that, **8 custom sound effects** were added (deposit / withdraw, Living Page hits, effect-card use).

> ✅ **From this version on the StarEngine Lib prerequisite is bundled inside the mod — you no longer install it separately.** See "📌 Requirements" at the end.

---

## 🔊 New Sound Effects

This version adds **8 custom sound effects**, converted from the assets you supplied:

- **Star Coin wallet: deposit / withdraw**: one sound when a deposit into the wallet succeeds, plus separate sounds for withdrawing **Star Coins** and withdrawing **Star Coin Bags** (nothing plays when the action does nothing).
  (2026-09-25: the **Star Coin Bag** withdraw sound was too loud, so it is now **40% quieter**.)
- **Living Page hit**: split by **the damage actually dealt** — the regular impact sound below 8, a heavier hit sound at 8 or more.
- **Effect cards**: **Berserk / King Power / Unwavering** get their own cast sound; every other effect card is split by target — one for using it on **another player or target**, one for using it on **yourself**.
- **Who hears it**: deposit / withdraw are private UI feedback and play **only for you**; the rest play in the world and are **audible to nearby players**.

---

## ⚖️ Balance & Quality-of-Life

### Temporary cards: no more surviving inside mod containers

Temporary cards are supposed to live "only in your inventory and hand", but mod containers (AE2 storage terminals, Create vaults and item hatches) use completely different insertion paths, and **they cannot be blocked in code**. So the approach changed: **if we cannot block it, make it destroy itself**.

- **Every card now carries its own self-destruct deadline**: written as "3 minutes from now" when the cards are granted, so it can be judged wherever the card ends up.
- **Put into another container, or dropped on the ground - destroyed at once**: opening any container (chest, ender chest, a mod terminal...) sweeps once, and cards lying near you on the ground are cleared too.
- **Re-casting the skill / relogging realigns the deadline**: the "each cast resets the 3 minutes" rule now covers the cards you already hold.
- **One explicit boundary**: cards already sitting **inside** an AE2 network are out of reach, and we will not dig through another mod's storage - they merely **occupy a slot** there, and the moment they are taken out (into your inventory, a container, or the ground) they are destroyed on the next sweep and can **never be used again**.

Temporary cards also have their own hint line now: **"Temporary card: cannot be dropped or transferred; destroyed if put into another container; disappears when Queen's Privilege ends"** (visible on hover, including on a card sitting in someone else's container).

### Card costs have a new look

- The cost line at the top of a battle card's tooltip used to read "Cost: ⨀⨀⨀" - "Cost" was never localised, and the ⨀ glyph does not look great in some fonts.
- It now shows a **localised label plus ◆** (one ◆ per point of cost): "**费用：◆◆◆**" in Chinese, "Cost: ◆◆◆" in English, "コスト：◆◆◆" in Japanese.

### Whetstone: its life-saving effect now has a 1-minute cooldown

- The Whetstone's "a single hit can at most reduce your health to 1" used to have **no cooldown** - as long as you were above 1 HP you were nearly unkillable by any single hit, which made the Airbag (6 charge plus a 1-minute cooldown) feel pointless.
- That guard now **goes on a 1-minute cooldown each time it saves you** (only when it actually blocks a lethal hit; the everyday -2 reduction and +4 Attack Power are unaffected).
- We also spelled out who saves you first: **the Airbag takes priority over the Whetstone** - when a hit would kill you, the Airbag is checked first (if it has charge and is off cooldown it negates the hit), and only when it is unavailable does the Whetstone's "keep 1 HP" step in. With both equipped, the Airbag is always first.

### Hostile targets relaxed: neutral mobs no longer need to be provoked first

- **The "hostile target" criterion is rewritten to "hostile mobs ∪ neutral mobs (pets excluded)"**: previously a neutral mob had to be **angered** to count — so an unangered wolf, iron golem, polar bear or bee simply "was not hostile" in your eyes until you **let it hit you first** and it entered its 20–39 s anger state. It now reads **every neutral mob counts, only tamed pets are excluded**.
- ⚠️ **Measured impact**: the only vanilla mob that is both a neutral mob and tameable is the **wolf**, so this entry is effectively "**an untamed wolf now counts as a hostile target, a tamed one does not**".
- The criterion is **global**: the single entry point for "hostile target" lives in the prerequisite library `StarEngine Lib`, so **every** effect that depends on it follows suit — Dice Blessing triggering, spell-damage bonuses, the target selector's selectability checks (crosshair filter / radius highlight / server-side confirmation), railgun lightning target selection, and more.

### Piercing Gun: its condition now matches the Ninja Star exactly

- **The Piercing Gun drops its "while a damage effect card is active" prerequisite**: it used to additionally require an active damage effect card (one of Monster Laser / Monster Brick / Orbital Strike / Directional Blast, or the hit itself being the Living Page's spell damage), which meant **plain arrows, thrown projectiles and linked-mod spells** never received the bonus. Now it simply reads: **wearing the Piercing Gun and dealing ranged/magic damage to a target grants bonus damage equal to the target's defence points**.
- **Its target criterion now matches the Ninja Star exactly — the "must be a hostile target" restriction is gone**: the old test was narrower than the actual gate, so **non-teammate players who had never attacked you** and **neutral mobs without vanilla's "neutral" marker (goats / llamas / foxes etc.)** received the Ninja Star bonus but not the Piercing Gun one. The two are now fully identical, with the target scope decided by the spell-damage chain's single gate.
- The item description and handbook entry had both the prerequisite and the "hostile" qualifier removed to match.

### The four damage cards and the Electric Glove now read straight

- **Monster Laser / Monster Brick / Orbital Strike / Directional Blast and the Electric Glove** used to say "**all ranged/magic damage to hostiles** +N", but they do **not check the target at all** - with the effect up, the bonus applies to whatever you hit. The text was narrower than the code and made it look as if neutral mobs were excluded. All five now start with "ranged/magic damage", and the area wording says "other targets" to match the code.

### A few numbers

- **Star Coin Hammer**: Star Coins consumed per Dice Blessing **3 -> 6**.
- **Smart Watch**: the card top-up threshold drops from **10 to 6** (easier to keep a hand).
- **Target chip**: the Mark from a Dice Blessing no longer lands on **the very target you are attacking** - it now picks the nearest target **other than that one**, so it finally works as "set up a second target".
- **Magic Quiver**: it now requires **a damage effect card to have been used first** (Monster Laser / Monster Brick / Orbital Strike / Directional Blast / Living Page), and upon dealing **ranged or spell damage to an already-marked target** it returns the first effect card used and applies 1 Mark layer (at most once every 30 seconds). The Living Page itself counts as a damage effect card - but you still need to have used one first.

### The "Starlight / Star Coin / healing point" bonuses are now resolved separately

- **Flashlight (every 4 Starlight +1), Star Coin Hammer (30% of held coins), Cutter (+2 / +4 plus healing points)**: these three used to be folded into **Attack Power** (reduced by defence, and scaled by "attack power snapshot" effects). They are now **resolved as a separate damage instance on hit**, with the new damage type **bonus damage** (`astral_dice:extra_damage`) - the same treatment as true damage and skill damage: **armour is ignored and the listed amount lands as-is**. Their item text now says "**Attack Damage**" (things that add to your base damage still say "Attack Power").

### Mouse Shield: a single hit bigger than its yellow hearts no longer reaches your red hearts

- The **Mouse Shield** still gives **5 yellow hearts (10 absorption)** and still vanishes once they are used up - but now **as long as any
  yellow hearts remain, that one hit is fully carried by the shield**: the excess is discarded, only the remaining yellow hearts are
  consumed, and **your red hearts take nothing at all**.
- That was not true before: a hit harder than the yellow hearts only lost the part the shield could cover, and the rest went straight to
  your red hearts (5 yellow hearts could not stop 190 of a 200-damage hit).
- A knock-on issue was fixed too: a hit fully eaten by the shield no longer wastes the **Airbag** charge (same for the Whetstone and the
  deduction from Detective layers).

### Shooting Stars: higher, faster, and pickier about targets

- **Higher and faster**: the origin now starts **3x** higher and the star falls **6x** faster - what used to take 1 second to reach the target's head now takes **0.5 s** (dropping from 7.3 blocks above it).
- **Tighter back-to-back timing**: with both chips equipped the golden star now follows **0.5 s** after the purple one lands (was 1 s).
- **Hostile targets only**: an **unprovoked** wolf, iron golem, polar bear or bee used to catch a star as you walked by - such neutral mobs must now be **angered** first; real monsters (zombies, skeletons, creepers, endermen, ...) trigger exactly as before.
- **The star trail now glows**: in the dark the old particles were just a string of **dim coloured dots** (the vanilla particle family does not glow), while the Living Page trail has always been bright - the shooting star now uses a **self-luminous** particle that stays bright in the dark, and the purple / gold split is unchanged.
- **"Without attacking it" is no longer required**: that wording was often ineffective in practice (it read a **single-slot** record of who last hit the mob, which any other mob or environmental damage would overwrite), so the whole restriction was **removed** - a star now drops whenever you pass a hostile target.

## 🐛 Bug Fixes

- **The Sherry Sign's "Detective's Strike" now counts new targets**: previously a few hits on the same mob filled up
  Reasoning Time; now **each target only ever grants 1 stack** (you need fresh targets to keep stacking), and the cap was
  lowered from 5 to **4** stacks (sign tooltip and handbook updated).
- **Chips that grant Starlight on equip (bank cards, ATM, Star Coin Hammer) no longer keep the Starlight after unequipping
  (equip/unequip cycling could farm Starlight)**: unequipping now **deducts exactly** what the equip actually granted, so
  cycling grants nothing, and bank cards no longer leave convertible Starlight behind when removed.
### Temporary cards and the Q key: from "vanishing" to "simply not droppable"

The temporary cards the Oasis Queen hands out say "cannot be dropped" - but pressing Q actually made them **vanish from your inventory**, with nothing on the ground. The reason: only the server refused the drop, while the client had already wiped the card from its slot, and the server never sends a correction for that. So all you saw was "the card is gone" until you relogged.

- **Q now does nothing at all**: the client asks "can this be dropped?" first, and if not, nothing happens (no drop, no vanishing, no leftovers).
- **Same inside the dice screen**: pressing Q on a temporary card already equipped in the dice used to **pull it out of the dice and stuff it back into your inventory** - and **destroy it outright** when the inventory was full. It is now refused, and the card in the dice stays put.
- **Effect cards fixed too**: they did not hit the ground, but they **jumped to another inventory slot**, and were destroyed as well when the inventory was full.
- **Dying now always clears every temporary card**: before, dying with the dice screen open - or with a temporary card on the cursor - let that card survive death (and it came back into the dice when the screen closed). Both paths are covered now.

### Bank Cards: the starlight they promised actually arrives now

The four bank cards (Low / High balance, Unlimited) plus the ATM and the Star Coin Hammer all say "On equip, gain N
Starlight" - but **not one of them ever granted anything**: equip it and Starlight was still 0 (these chips print
"Current Starlight: X / cap" at the bottom of their tooltip, so you can check directly).

The cause was a parameter read at the wrong position: the "is this a fresh equip?" check was reading the item just
equipped instead of the slot's previous content - and that is never empty, so the whole grant was skipped every time.

All fixed:

- **Bank Card (Low / High balance)**: equipping raises Starlight to **4 / 7 base starlight** (base starlight cannot be
  converted into Star Coins);
- **Bank Card (Unlimited) / ATM / Star Coin Hammer**: **+3 / +1 / +5 Starlight** on equip;
- Two same-root issues fixed along the way: the Cursed Sword's "Thousand Curses Mark" was never actually applied on
  equip (it only ever worked because it is re-applied every second), and the Big Boss sign's "reset the 1-minute timer
  on equip" never took effect either.

The starlight readout is already **live** (the server syncs it to your client the moment it changes), so the tooltip
shows the new value as soon as you equip the chip.


### 1.21.1: the Whetstone was over-cutting hits while yellow hearts were up

- Only **1.21.1 / 26.1.2** had this problem (1.20.1 was always correct): **with yellow hearts up, the Whetstone cut the damage far too hard**.
- The clearest example: 5 health, 10 absorption, taking 8 - it should be "absorption eats 8, red health untouched", but the hit was cut to 4 (absorption only lost 4).
- The cause is that the damage event fires at a different moment on the two versions, so on 1.21.1 the Whetstone was handed a value that still included the yellow hearts.
- It now matches 1.20.1: **absorption is consumed first, then the Whetstone decides how much red health is lost** - the yellow hearts eat exactly what they should, and only red health is affected. Its -2 reduction and "keep 1 HP" guard are unchanged.

### Sherry Sign "Strength Throw": thrown mobs no longer pile up and shove each other apart

- Previously, throwing a group of mobs in front of you packed them onto **a single line** (the more targets, the tighter - from 4 targets on, neighbouring landing spots were less than 0.6 blocks apart), so after landing they **shoved each other apart**; the AI herded them back, and a few seconds later it happened again.
- Landing spots are now laid out on **a small grid** (centre first, at least 1.1 blocks apart - automatically wider for broad mobs), and each spot resolves its own ground => they land **without overlapping**, so the shoving is gone.

### The training dummy can now be selected and hit

- **The training dummy (`dummmmmmy`) is now always treated as a hostile target**: it is neither a hostile mob nor an angerable neutral mob, so every "requires a hostile target" effect excluded it — most visibly, the **Living Page could not select it at all, and therefore could not hit it** (you could not even use it to test your damage). This is now fixed at the root: the training dummy counts as hostile in **all** "hostile target" checks — Dice Blessing triggers normally, spell-damage bonuses are applied normally, and the target selector picks it up normally.

### Gunsmith sign: neutral targets now get Weakness Insight too

- The 1.21.1 / 1.20.1 lines used to restrict the Gunsmith passive to "**normal hostile mobs**", so targets like **wolves / iron golems / polar bears / bees, goats / llamas** carrying Broken never granted Weakness Insight even when killed (the Dice Blessing from the same swing still triggered). Both lines now match 26.1.2: the trigger condition is exactly the Dice Blessing's.
- For the record, the Gunsmith's **active** always selected "hostiles + neutral mobs (tamed pets excluded) + the training dummy", so neutral targets were **always** selectable.

### Four places where the area/bonus missed identical nearby targets

- The **area splash** of Directional Blast and the Electric Glove, the **Boss sign's splash**, and the dice-battle "Investigation Stage bonus" used a narrower hostility test - so **a player who never attacked you**, a **Boss**, or **a mob that fights back but has no vanilla "neutral" marker** could receive the bonus as the main target while an identical target standing next to it got nothing. All four now use the **same gate** as the Dice Blessing, the Ninja Star and the Piercing Gun.

### The Electric Glove's splash no longer hits the caster

- **Electric Glove and Directional Blast**: the area splash used to **include you as a target** - at close range you would take your own splash damage. Fixed: the splash only hits **other targets**, never the caster.

### The Sherry Sign's throw damage is no longer amplified by spell-damage bonuses

- **Sherry Sign, "Strength Throw"**: the 2 (+5) damage dealt on landing used to resolve as **spell damage** - so it was amplified by the Ninja Star, the Piercing Gun, the Amethyst Dice, the Marker Sprayer, the Magic Quiver and the damage effect card bonuses, and could even trigger the Electric Glove's splash. It now uses a **separate skill-damage type**: only its own flat amount is dealt (still armour-piercing). It also **bypasses the hurt-invulnerability window**, like true damage.

### Mamushi & Hanna: the counters stop once True Dragon Form / Doll Complete is reached

- **Mamushi Sign**: in True Dragon Form, Awakening no longer accumulates (it stays pinned at 8), and the "Awakening: 8 / 8" row is no longer shown in the item description - only the True Dragon Form label remains.
- **Hanna Sign**: once Doll Complete is reached, Doll Crafting no longer accumulates. It used to climb back from 0 to 7, re-trigger the completion and make the Doll Crafting icon reappear; now it stays void after completion.

### Text and display

- **Friendship Badge / Big Bowl Stew**: the parenthesised notes (dedup rule, what counts as a "friendly creature") are gone - implementation details the player does not need.
- **Scope / Eagle Scope**: the text has been trimmed (redundant qualifiers removed); behaviour is unchanged.
- **Sky-Searching Satellite**: the parentheses became a comma, for consistent phrasing.
- **Revenge Halberd**: the "each type triggers once" and "does not stack" clauses are gone.
- **Speed Skates (basic / medium / advanced)**: the **`%` no longer loses its colour** (it used to fall back to the line colour, looking un-highlighted).

### Teru Sign: the Descent bonus now persists, and Fox Light can be cleared

- **Descent bonus**: it used to apply only to the **first hit on each target** - a second hit on the same mob lost it. Now attacking a **new** target spends 1 Fox Light stack and grants the bonus, and **further hits on the same target keep it** (no extra stack spent). With no stacks left, attacking a **new** target adds nothing.
- **Fox Light**: (1) **unequipping the Teru Sign** now clears its stacks and icon (it could not be cleared before); (2) at 0 stacks the icon always disappears; (3) the icon number saturates at **X** (10) - vanilla only draws numerals up to X, so stacks >= 11 show X; **see the item description ("Fox Light: x / 20") for the real count**.

### Holding an effect card no longer swallows the sign active-skill key (J)

- Before: with the **Living Page** (or Express Delivery / Luxury Feast / You Have I Have / Berserk / Talisman Cards)
  in your main hand, the card automatically entered aiming mode - and pressing J to fire your sign active skill was
  read as "cancel selection", so **the skill simply would not fire** (you got a "selection cancelled" message instead).
- Now: these hold-to-aim cards no longer take over the active-skill key: **the sign active skill fires as usual and the
  card stays in aiming mode**. To cancel aiming, keep using **sneak + right-click** or **move the card out of your
  main hand**.
- A knock-on issue was fixed too: firing a skill while holding a card used to leave the skill **without cooldown /
  "in effect" lock** (and without a Current Core charge) because of the same misjudgement.

## 📌 Requirements

- ✅ **From this version on, the StarEngine Lib dependency is bundled inside the mod — you no longer install it separately**: a copy of `starengine_lib` ships inside the artefact (embedded under `META-INF/jarjar/`) and is loaded automatically by the loader at startup.
- ⚠️ **Bundled version = `1.0.3`, compatible range `[1.0.3,2.0)`** (the same range declared in `mods.toml`); **do not** also drop a standalone `starengine_lib-*.jar` into `mods` — the loader de-duplicates by modId and prefers that copy, so an older one would shadow the bundled library. Library source repository: <https://github.com/merlin-kitsune/starengine_lib>
- **Other requirements**: Curios API (**1.20.1 additionally requires Mixin Booster**).
- **26.1.2 line**: requires Curios API **15+**; it has **no Iron's Spells 'n Spellbooks integration**.
- **Optional integrations**: Patchouli (guidebook), Bountiful.
- **Version gate (multiplayer)**: the client and server must share the same **major.minor version** (`1.3.x` ↔ `1.3.y` interoperate). Cross-version connections are **refused with a clear message** rather than failing silently.
