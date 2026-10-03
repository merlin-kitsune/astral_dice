# Astral Dice - Fabric 1.20.1 (port line)

**Version `1.3.6-alpha.1`** - alpha channel (pre-release) - Minecraft 1.20.1 - Fabric

> Note: **this is the Fabric port line, not a stable release.** Stable releases target NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2.
> This line uses an **independent version number** (`-alpha.N`, mirroring the `-beta.N` of the 26.1.2 line), so it never occupies a stable version number.

## Required dependencies

- **Fabric Loader 0.19.5+**
- **Fabric API** (1.20.1 line, 0.92.12)
- **StarEngine Lib - embedded, do NOT install it separately.** Bundled via Jar-in-Jar (`1.0.6-alpha.2`).
  If your mods folder still holds `starengine_lib-*.jar`, delete it: a loose file outranks the embedded copy and will shadow it.
- **Puzzles Lib 8.1.33+** (event bridge; install it together with its own prerequisite **Forge Config API Port 8.0.3**)
- **Accessory provider - Trinkets 3.7.2+ or Accessories 1.0.0-beta.48+, either one.** Fabric's `depends`
  is AND-only and cannot express "one of two", so both are declared optional; the mod checks at startup and
  **only refuses to start when neither is present**.

Optional: **Patchouli** (in-game guide). There is no Fabric build of Bountiful for 1.20.1, so this line has no bounty board.

## Changes in this version

The following matches 1.3.6 on the three stable lines (this line carries the same batch of changes).

### Balance & Quality Updates

- **The "Thousand-Curse Mark" enchantment is renamed "Blue Curse" and hidden from JEI and the creative menu**: this enchantment is an internal marker (its only purpose is to make the Thousand-Curse Scroll count it as one curse), but vanilla automatically generates an "enchanted book" entry in the creative menu for every enchantment, and JEI indexes it too. It no longer appears there, and the enchantment line appended to the item tooltip is removed as well. **Only "visible / obtainable" changes - the function is untouched** (curse counting still works).
- **Blue Curse's armour-toughness penalty changed from -100% to -50%**: the -20% armour value is unchanged, and the effect description and item tooltip were updated to match.
- **The Cursed Sword's stacked attack-power cap is raised from 16 to 32**: each hostile with 20+ HP killed during a Dice Blessing stacks 1 point; it used to cap at 16 and now caps at 32. (Tooltips and the in-game guide are updated to match.)
- **Kanban Musume Sign's random chips are now capped at one per active use**: the passive is "the active skill returns 25 accumulated battle cards -> 1 random chip", and the active skill first collects every card in your inventory - with many cards one use could add up to several sets of 25 and **spawn a pile of chips at once**. It is now capped at **one chip per active use**; anything beyond 25 is still kept and carried into the next count, so nothing is deducted for nothing.
### Bug Fixes

- **Fixed: signs and chips kept working while no dice was equipped (every bonus was free)**: previously, removing the dice only stopped refreshing the chip slots, while sign and chip bonuses kept applying - a defect, not a balance change. Three things are now fixed:
  - (1) **Without dice equipped, every sign and chip feature is disabled** (passives, active skills, target-selecting skills, combat values and per-tick state machines all stop), but **already accumulated values are not cleared** - attack power stacked by **Cursed Sword** and the layer counters tracked by signs are all kept.
  - (2) **Switching to a dice with fewer usable chip slots shrinks them on the spot**: chips beyond the new limit are pulled out and **returned to your inventory** (no longer waiting for the next reconciliation pass).
  - (3) **Respawning - or rejoining while still wearing no dice** forces the "no chip slots" initial state and returns the chips to your inventory.
  - Note: panel bonuses disappear together with "disabled" and come back when you equip dice again.

- **Fixed Medkit chips not firing healing "on equip" and "after respawn"**: the two Medkits (Emergency / Complete) only added healing layers when **a Dice Blessing started**, so equipping them did nothing and respawn / relog / dimension change did nothing either. Now all four moments fire a healing trigger immediately: on equip, and after respawn / relog / dimension change. Unequipping deducts "the layers granted by this equip" using an internal ledger, which also closes the loophole of farming layers by repeatedly relogging.
- **Fixed Epic items' tooltip frames showing "the previously hovered item" colour**: when a tooltip-rewriting mod is installed alongside, an Epic item's frame kept the previous item's colour (observed as gold). Epics now **explicitly write their own tier colour**; on top of that, a mitigation was added for the third-party tooltip mod's colour cache, and the Epic frame used with that mod is corrected to the vanilla Epic colour too.
- **Fixed the Cursed Sword's stacked attack bonus being wiped on death**: the bonus lives on the player, and dropping the chip on death cleared it - one death wasted all of it. It is now **kept through death**, and only a **genuine unequip** clears it.

- **Chip status icons completed, and all damage-boost chip icons no longer count down**:
  - (1) **Five chips that had no icons at all are now covered** - **Whetstone**, **Adrenaline** (one icon each for Common / High-Grade), **Cursed Sword**, **Electric Sword** and **Railgun**.
  - (2) **Damage-boost chip statuses are now "shown as long as they can take effect"** (no more short countdowns): **Flashlight Chip** and **Revenge Halberd** changed from a 5-second countdown to permanent (**Cutter Chip** already was).
  - (3) An icon lights up only when **the bonus is actually active**: Whetstone and Adrenaline at low health (<=50%), Cursed Sword once a bonus has been stacked, Electric Sword at charge >= 4, and Railgun at charge >= 6.
  - (4) A related hazard was fixed along the way: after removing the dice these icons **now go dark properly** (they previously stayed as a stale "feature off, icon still on" state).
- **The Railgun's lightning now only triggers on "your own melee attack"**: previously any damage whose **direct source** was you registered a strike, so three cases misfired: **Thorns and other damage reflection** (reflection records you as the attacker), **your pets' attacks** (wolves and maids), and **spells / skills / ranged** non-melee damage. It is now tightened to **only an attack you personally land while holding a melee weapon**. The line "On attacking a hostile target" in the item tooltip and the guide was changed to "On a **melee** attack against a hostile target".
- **The Gunsmith sign's "Weakness Insight" now stacks on every attack**: it used to count only the first hit on a given target (one stack per Broken segment). Now **each hit grants a stack**, and each incoming hit from a Broken enemy does too; **once capped at 4, further attacks refresh the duration** (nothing is wasted).
- **King Power's cost now really costs 8 HP**: the 8 damage used to be shaved down to almost nothing by armour, Protection and Resistance - a free cost. It now **ignores armour / Protection / Resistance entirely**, and must be paid in full; **life-saving effects still work** (airbag, totem, ender dice, and the whetstone's "cannot be one-shot").
- **Berserk's "take +1 damage per stack" is now settled as a separate true-damage hit**: it used to be shaved by armour and then cancelled out by flat reductions such as the whetstone. It is now **settled on its own** - unaffected by armour / Protection / Resistance and by this mod's flat reductions.
- **Fixed the Kanban Musume Sign's random-chip pool being incomplete**: it used to be a **hand-written list** covering only the first 41 chips, so the 20 chips added later (including the purple / golden Shooting Stars and the charge chips) **could never be obtained**. It is now **generated from all current chips**, so future chips join the pool automatically and nothing is missed.
- Plus a large number of description-vs-behaviour mismatches were fixed.
