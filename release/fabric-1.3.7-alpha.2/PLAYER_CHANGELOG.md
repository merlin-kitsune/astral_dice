# Astral Dice — Fabric 1.20.1 (port line)

**Version `1.3.7-alpha.2`** · alpha channel (pre-release) · Minecraft 1.20.1 · Fabric

> ⚠️ **This is the Fabric port line, not a stable release.** Stable releases target NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2.
> This line uses its **own version numbers** (`-alpha.N`, mirroring the 26.1.2 line's `-beta.N`) so it never occupies a stable version number.

## Required dependencies

- **Fabric Loader 0.19.5+**
- **Fabric API** (1.20.1 line, 0.92.12)
- **StarEngine Lib — embedded, do NOT install it separately.** It is embedded via Jar-in-Jar (`1.0.12`).
  If your mods folder still has a `starengine_lib-*.jar`, delete it: a loose file takes priority over the embedded copy and will override it.
- **Puzzles Lib 8.1.33+** (event bridge; install its own prerequisite **Forge Config API Port 8.0.3** alongside it)
- **Trinket provider — Trinkets 3.7.2+ **or** Accessories 1.0.0-beta.48+ (either one).** Fabric's `depends`
  has AND semantics and cannot express "either one", so both are declared optional only; the mod checks at
  startup and refuses to launch **only if neither is present**.

Optional: **Patchouli** (in-game manual). There is no Fabric build of Bountiful for 1.20.1, so this line has no bounty board.

## 1.3.7-hotfix

- **You can now use your active skill while holding a "target selection" effect card.** Previously, as long
  as the selection prompt was open, pressing the active-skill key was treated as "collapse the selection"
  and the skill never fired. The key now **only casts the skill** - it no longer steals it.
  To cancel a selection: **right click** (cards that can only target others), **sneak + right click**, or
  press **ESC** to open the menu; for hold-to-select cards, simply **take the card off your main hand**.
  (Tip: if a sign skill is currently "waiting for you to pick a target", pressing the key again does
  nothing - that is normal, it is waiting for your target.)
- **Embedded prerequisite library bumped to `1.0.12`** (embedded - **do NOT** install it separately).

- **Cleaned up three spots of text on the Game Master Sign**: the Mouse Shield status note said "(no icon)"
  even though the effect does show an icon in the status bar (self-contradictory) - removed; the sign's
  item tooltip also dropped its "(absorption)" and trailing "(your red hearts are unaffected)" notes, and the
  in-game guide entry lost its trailing "(your red hearts are unaffected)" as well (the "5 yellow hearts =
  10 absorption" conversion stays). Text only - nothing about the effect changed.

- **The English "Nancy Lu Sign" tooltip was missing a number**: its active skill grants **twice the consumed
  card's cost** as attack (minimum +2) for 2:00 - the Chinese and Japanese texts both say so, but the English
  text read "equal to that card's cost", **half the real value**. Fixed. Text only - nothing about the effect
  changed.
## Changes in this version

This is a **catch-up** release for this line: the decisions and fixes that were previously live only on the stable
lines are now **ported over in one go** (this line now matches the content surface of the stable 1.3.7), together
with a few items specific to this line.

### Caught up with the stable lines (ported in this release)

- **Non-weapon tools no longer count as a "melee weapon attack"**: hitting someone with **shears / a fishing rod / flint and steel / a brush** no longer triggers the Dice Blessing (dice combat).
- **The dice-blessing melee-weapon check reverted to the blacklist**: only **bare hands / shields / ranged-only items (bow / crossbow / slingshot) / blocks / guns themselves / non-weapon tools** are excluded - everything else counts, **including every digging tool (pickaxe / shovel / hoe / axe) and weapons from other mods**. NOTE: weapons that are both melee and ranged are not excluded (e.g. trident).
- **Guns themselves can no longer be used to melee-smack** (detection is driven by per-item evidence taken from the actual mods).
- **Luck-blessing / Divine Descent countdowns are now started by *any* hit**: a **ranged hit** (bow / crossbow / thrown projectile) also starts the 2-minute countdown (previously melee only).
- **Effect cards can now target "untamed tamable" creatures**: on top of the existing hostile set, living pages and damage effect cards (including the spell card), may pick **untamed** tamable creatures - wolves / cats / parrots, plus unowned horses / donkeys / mules / camels / llamas. NOTE: tamed pets and claimed mounts remain unselectable; villagers / wandering traders are always excluded.
- **Skyward Satellite reworked: "kill refunds a random effect card" -> "effect card target-selection distance +50%"**: an always-on bonus while worn - holding any **effect card** widens its target selector by **+50%** (16 -> 24 blocks). NOTE: effect cards only; it **adds up** with the Investigator sign's "Page Range" for a **64-block** cap.
- **Investigator sign active skill also grants "Page Range"**: besides "gain 1-2 living pages", the active skill now also grants a **2:00** "Page Range" status that gives living pages **+50%** range (32 -> 48 blocks; with the Skyward Satellite chip also worn, 32 -> **64** blocks). NOTE: the status is visible (HUD icon + hover text); recasting only **refreshes** the duration, it does not stack the multiplier.
- **Event effects now broadcast over "party + 64 blocks"**: if the trigger is **in a party** the effect goes to all party members union other players within 64 blocks; with **no party** it goes to all party-less players union every player within 64 blocks. Sign passives fired by the event follow the same set, and negative outcomes broadcast over the same range.
- **The charge status icon is hidden while no charge chip is equipped**: with no charge-type chip in the trinket slots the HUD no longer shows the "charge" icon; equipping any charge chip brings it straight back.
- **Permanent (infinite) statuses no longer show an absurd countdown**: legacy ultra-long durations in older saves are now normalised to **∞** after re-entering the world (they previously displayed as tens of thousands of hours).
- **Effect-card sessions now only take over "release + collapse" keys**: **left click = release**, **right click = use on self / collapse**, **sneak + right click = collapse**, **active-skill key J = collapse**; **middle / side mouse buttons** and the **card-slot key H** are always passed through. NOTE: collapsing is not disabling - the selector will not pop back on its own; move the card out of your main hand and back to reopen it.
- **ActionBar text no longer overlaps the item-name toast**, and the fade time is shortened to **0.5 s**; both actionbar options moved out of the config file (enforced for every player).
- The remaining fixes from the stable 1.3.7 (firearm damage detection, living-page stacking, config version number, the crystal left at the death site, effect-card sessions, ...) are live on this line as well.

### Specific to this line

- **Rarity border colours no longer break when "Modern UI" is installed with its "modern tooltip" enabled**: the Fabric build hooks a **different** render entry point than the stable lines, so a dedicated compatibility layer writes the tier colour into that UI mod's stroke slots and restores the player's own settings **immediately** after that draw, **changing none of its settings**. The rainbow tier also gains a **full flowing rainbow ring**. Config: **`modernui_tooltip_frame_compat`** (on by default); when that UI mod is absent the layer is a complete no-op.
- **Content for link mods that do not exist in this environment was cleaned up**: this line has none of those link mods, so the related manual pages, item tooltips and config entries were removed to avoid pointing at things that cannot be installed here.
- **The embedded prerequisite library was upgraded to `1.0.12`** (embedded - **do not** install it separately).
- **Tests and gates**: this line's static checks (manual keys, actionbar channel, injection wiring, language-key sync, ...) are now part of the same gate list as the stable lines, so later regressions get caught automatically.

### Bug Fixes

- **Fixed medkit chips letting you farm healing by repeatedly re-logging or walking through portals**: besides "when the chip is actually equipped" and "after respawning from death", the two medkits also treated **every re-login** and **every dimension change** as a full settlement (add points -> heal for the current stacks x2 -> restart the 1-minute timer), so re-logging repeatedly gave **unlimited healing** - with 32 stacks a single pass healed 64 HP. Per that decision those two moments are now **removed**: healing triggers only "**when the chip is actually equipped**" and "**after respawning from death**". NOTE: this does not depend on the chip still being worn - re-logging or changing dimension while wearing it no longer triggers anything either.
- **Fixed the tier wording for two signs in the in-game manual**: the **Hanna Sign** and the **Sherry Sign** have **always** been the "Bizarre" tier, but the manual text called them "Rare" and "Epic". Following the "items are the source of truth" rule, the manual text is now unified to "**Bizarre tier**" (Chinese / English / Japanese in sync). NOTE: text only - item rarities, drops and recipes are unchanged.
- **Fixed: clearing status effects through the raw API used to throw on this line**: clearing this mod's own status effects went through an internal path that raised an exception. It now checks whether the target still has the effect at all, so an ineffective clear is simply let through without an error, while the block on **external** clears (milk / clear-effect commands) is **kept**.
- **Fixed: the extra 6th manual page** has been removed, so this line's manual has exactly the same page and entry count as the stable lines.
