# Astral Dice 1.3.2 Changelog (Player Edition)

> Scope: **1.3.1 → 1.3.2** (this is an incremental note; see `release/1.3.1/` for the full 1.3.1 content). Applies to both 1.21.1 (NeoForge) and 1.20.1 (Forge). Version suffixes: `+neoforge_1.21.1` / `+forge_1.20.1`.
> The GitHub Release additionally ships a build of the third line, **26.1.2 (NeoForge)**, suffixed `+neoforge_26.1.2` (its version carries `-beta.1`; feature parity with the release lines).
> This file is the player-facing release note and corresponds one-to-one with the player-visible sections of **1.3.2** in `CHANGELOG.md` (engineering / tooling entries excluded).

**In one sentence**: a new sign, the **Megas Sign**, arrives with **Orbital Bombardment**, alongside the new-player perk of **a guaranteed die in your very first loot chest**; we also fixed this mod's loot-chest items being **rolled twice**, and relaxed the **"hostile target"** rule once more (untamed llamas / trader llamas now count).

> ⚠️ **As of this version the bundled prerequisite library moves from `1.0.4` to `1.0.5`** - see "📌 Requirements" at the end.

---

## 🎁 New Content

### First adventure gift: your first loot chest in a save is guaranteed to hold 1 die

- When a player enters a new world and opens **their first loot chest**, that chest is **guaranteed** to contain **1 die** - placed into a **random empty slot**, or, if the chest is already full of loot (common on heavily modded packs), it **forcibly replaces a random slot** so the die always lands in your hands.
- Each player gets this **once per save**: the flag lives in the player's saved data, so **a new save counts again**, while **respawning or changing dimension does not re-grant it**.
- Only "loot chests" qualify (chests, trapped chests, barrels, shulker boxes, hoppers and minecart chests; the jungle temple dispenser is excluded) - **chests you place yourself never trigger it**.

### New custom sound effects

- **Skill activation**: plays when an active skill takes effect (entering the target selector counts too).
- **Glass dice broken**: plays when you die while wearing the Glass Dice.
- **Orbital bombardment impact**: plays **at the hit position**.
- **Effect-card use** is now a single sound (except **Berserk / King's Power / Immovable**, which keep their own cast sounds).
- **Living Page and Misfortune Card** hits use new samples: split by **the damage actually dealt** - the regular impact sound below 8, the heavier hit sound at 8 or above - played **at the hit position**.

### Megas Sign (Legendary): Orbital Bombardment + Resupply

- Active **"Orbital Bombardment"**: designate one hostile target, then **immediately consume every card in the inventory** and call down a bombardment on that target and a **12-block** area around it. Every **2** cards consumed adds **1** bombardment (**up to 10**); each bombardment hits **1 random monster** in the area for **2** base damage, plus extra damage equal to **the total cost of the battle cards consumed**.
- Passive **"Resupply"**: whenever there are **fewer than 6** cards in the inventory, gain **1** random card every **1:00**.
- Consuming **6 or more** cards in a single cast triggers **"Precision Strike"**, which raises the bombardment damage that target takes by **+1 per stack**, **permanently, until it dies**.

---

## ⚖️ Balance & Quality-of-Life

### Loot chests: a higher Blank Chip chance, and Glass Dice start dropping

- **Blank Chip**: still a **guaranteed 100%** drop from **buried treasure**, and it now additionally has a **3%** chance in every other loot chest - previously **no chest** other than buried treasure could drop one at all.
- **Glass Dice** (the T1 golden tier) can now be found in loot chests: **2%** in ordinary chests and **5%** in **end city** treasure, **1 per roll**. Previously loot chests dropped **no dice at all** (dice came only from crafting and from the bounty reward pool).
- Neither interacts with the "first adventure gift": the first chest still grants **1** extra base die at **100%**, and the Glass Dice is rolled independently, so both **can appear in the same chest**.

### Megas Sign "Orbital Bombardment": numbers retuned

- The battle-card cost bonus is now **1 damage per 1 cost** (was 2 damage per 1 cost), and the target-selector radius is **32** blocks (was 16).
- A single bombardment now deals at most **80** damage, and a single cast deals at most **800** damage in total - bombardments beyond that budget keep **only their visuals and sound and deal no damage**.

### Star Coin Hammer: higher threshold, higher cost, capped bonus

- The trigger threshold is now **holding at least 32 Star Coins** (was "more than 20").
- Each Dice Blessing consumes **18** Star Coins (was 6).
- The Attack Damage bonus is **capped at 100** (still **30%** of the Star Coins held; nothing accrues beyond the cap).

### Oasis Queen: casting the active skill needs 3 free slots again

- The active skill now requires **3** free main-inventory slots (was 2), matching the fixed 2 battle + 1 effect cards granted per cast.
- With fewer slots the cast is **refused**, at **no cost** (no cooldown, no effect); the message now reads "**3 free slots needed**".

### Baize's Blessing / Divine Descent: a fixed 2:00, started by the first melee hit

- Both effects used to run "until the recipient's **next Dice Blessing ends**", which made their duration drift with the Dice Blessing cycle and could end the effect **the instant a Dice Blessing fired**. Both now use a **fixed 2:00** duration, and the timer **starts only once the affected player first lands a qualifying melee attack**.
- The effect itself is applied **immediately** (it takes effect at once), but the **2:00** countdown **does not begin until that first melee hit**.
- The qualifying attack is a **melee weapon attack** (sword / axe / mace / trident) against a target that is **a player, a hostile mob or a boss** - the same gate Dice Blessing uses, so **ranged kills do not start the clock**.
- The timer **starts once and never resets** (later hits do not refresh it).
- When the countdown reaches 0 the effect is removed, together with **everything it granted, exactly as before** (Baize's Blessing removes the converted attack power; Divine Descent removes the caster's attack / defense bonus).
- The effect and its timer are **separate states**, and the "granted immediately, clock not yet running" intermediate state shows as a **permanent-looking icon** by design.

### "Hostile target" relaxed once more: untamed llamas / trader llamas now count

- The rule was "hostile mobs ∪ neutral mobs (pets excluded)", excluding only **tamed** pets. But **untamed** llamas / trader llamas are tameable yet are neutral mobs that retaliate once provoked - so **neither tamed nor angered, the target selector could not pick them**, which contradicts the intuition that "anything that can be angered should count as hostile".
- A class is now added by **capability**: **any mob that can be angered and is not already tamed** counts as a hostile target.
- ⚠️ **Measured impact**: the newly covered mobs are **llamas / trader llamas** (the only vanilla non-pet mobs that are both tameable and angerable).
- The criterion is **global**: it is implemented once in the prerequisite library **StarEngine Lib**, so **every** effect that depends on it follows suit - Dice Blessing triggering, spell-damage bonuses, the target selector's selectability checks, railgun lightning target selection, and more.

### Cutter / Flashlight chips: HUD icons now follow the Dice Blessing

- These three chips' bonuses **already only resolved during a Dice Blessing**, but their HUD status icons did not follow it: **Cutter Chip / Cutter Blade Chip** kept the icon shown for as long as they were equipped with health ≥ 60% (even when no bonus could possibly apply), and **Flashlight Chip** had **no icon at all**. All three are now uniform: **hidden whenever there is no Dice Blessing**.
- **Cutter Chip / Cutter Blade Chip**: shown only when the corresponding chip is equipped **and** health ≥ 60% (or you are under the Sip state) **and** a Dice Blessing is active; hidden otherwise.
- **Flashlight Chip** (**new icon this round**): shown only when the chip is equipped **and** a Dice Blessing is active **and** Starlight has reached **4** (every 4 Starlight gives +1 extra damage; below 4 there is **no bonus and no icon**).
- The icons reuse each chip's own texture, so **no new art assets** were added.

---

## 🐛 Bug Fixes

### Loot chest rates fixed: this mod's items were being rolled twice

- Every vanilla loot chest rolls both the vanilla table and this mod's added "star plate" table, and the four pools - **Star Coin / Blank Chip / Star Plate / Glass Dice** - had been injected into **both** of them, pushing the effective rates up across the board:
  - Star Coin **5% → 9.75%**, Blank Chip **3% → 5.91%**, Glass Dice **2% → 3.96%** (about **double** their stated values); Star Plate **5.95% → 6.89%**.
- The added table is no longer injected a second time, so every pool is back to its stated chance: **Star Coin 5%, Blank Chip 3%, Glass Dice 2%, Star Plate 5.95%** (Star Plate = 5% from the added table + 1% from the main table); **End City** is Star Coin **9%** / Star Plate **9.75%** / Glass Dice **5%**; and **Buried Treasure** still guarantees the Blank Chip at **100%**.

### Unequipping the Oasis Queen sign now clears "Queen's Privilege" and the temporary cards with it

- Previously, unequipping the sign only removed the armor granted by its passive, so the **"Queen's Privilege" effect (the HUD timer) and every temporary card stayed on you** for up to the effect's full **3:00** duration - inconsistent with the "clean up on unequip" convention every other sign follows.
- Unequipping the sign now **removes the "Queen's Privilege" effect** and **purges all temporary cards** (inventory, offhand, those equipped into the dice, and any on an open card-panel or the cursor) at the same instant, matching exactly how natural expiry, external removal and player death already clean up.

### The Megas Sign's "Precision Strike" is visible now

- The effect was previously applied to monsters **without particle display**, making it **entirely invisible** in the world - you could not tell whether a bombardment had triggered Precision Strike or how many stacks a target had accumulated, so it looked as if the effect did not exist (or vanished once the bombardment ended).
- Hit targets now **continuously emit that effect's particles**, making higher stacks easier to identify.
- The effect's duration and its "**permanently retained until the target dies**" behaviour are **unchanged** (the effect was in fact always retained correctly; it simply had no visual indicator at all).

---

## 📌 Requirements

- ✅ **The StarEngine Lib prerequisite is still bundled with this mod - no separate install needed**: the artifact ships a copy of `starengine_lib` (embedded under `META-INF/jarjar/`) that the loader loads automatically at startup.
- ⚠️ **As of this version the bundled library moves from `1.0.4` to `1.0.5`**: the compatible range is now **`[1.0.5,2.0)`**. This version's looser "hostile target" rule lives in that library, so **the library and the mod must be updated together**.
- ⚠️ **Do not keep a standalone `starengine_lib-*.jar` in `mods`**: when the loader de-duplicates by modId it **prefers the copy in `mods` and discards the embedded one** - if that copy is **older** (`1.0.4` or earlier) it shadows the library bundled with this mod (visible as a prerequisite version mismatch, or a hostile-target rule that is still the old one). **Since 1.3.1 there is no need to install it separately**; only 1.3.0 and earlier did.
- Library source repository: <https://github.com/merlin-kitsune/starengine_lib>
- **Other requirements**: Curios API (**1.20.1 additionally requires Mixin Booster**).
- **26.1.2 line**: requires Curios API **15+**; it has **no Iron's Spells 'n Spellbooks integration**.
- **Optional integrations**: Patchouli (guidebook), Bountiful.
- **Version gate (multiplayer)**: the client and server must share the same **major.minor version** (`1.3.x` ↔ `1.3.y` interoperate). Cross-version connections are **refused with a clear message** rather than failing silently.
