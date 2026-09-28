# Astral Dice 1.3.2 Changelog

## 1.3.2-hotfix (1.20.1 only)

- **Fixed: on 1.20.1, the game refused to start with "mixinbooster is missing" whenever the pack ships Sinytra Connector — even with Mixin Booster installed.**
  The fault was not on your side: Sinytra Connector bundles the same Mixin runtime, so **Mixin Booster steps aside on purpose** (the log shows `Disabling Mixin Booster in favor of Connector`) and the `mixinbooster` mod entry is never registered at all — while the 1.20.1 build treated it as a **mandatory** prerequisite, so Forge hard-refused during dependency checking.
  The rule is now "**either Mixin runtime will do**": with Mixin Booster installed it is used as before; if the pack already ships Sinytra Connector its runtime is used instead (**nothing extra to download in that case**); only when neither is present does the mod refuse to start, telling you which one to install.
  ⇒ **1.20.1 no longer requires Mixin Booster.**
- This hotfix changes **1.20.1 only**; the 1.21.1 and 26.1.2 builds keep their versions and content unchanged.

!!! MAJOR UPDATE !!!

Released: 1.3.2 — this release merges everything from 1.3.0 to 1.3.2 (1.3.0 and 1.3.1 were never published on their own; the entries below are merged and deduplicated, with duplicate and intermediate adjustments omitted).

Note! The prerequisite mod StarEngine Lib is now bundled inside this mod (embedded 1.0.5) — you no longer need to install it separately. If your mods folder still contains a manually downloaded starengine_lib-*.jar, delete it: the old file would override the embedded one.
Note! On 1.20.1 the Mixin runtime can be **Mixin Booster ≥0.1.3** **or** **Sinytra Connector** — pick either one (since 1.3.2-hotfix; previously Mixin Booster was mandatory).

## Major Updates

### New Sign — Game Master Sign (Epic)

- Active "Brat's Privilege": choose a friendly player — or use it on yourself — to grant the target 1 random card and a "Mouse Shield".
- Passive "Mouse Rescue": after 5:00 without a Mouse Shield, automatically gain 1 random card and a Mouse Shield.
- Mouse Shield: 5 yellow hearts (10 absorption) + Resistance + 1 Counterattack charge. As long as any yellow hearts remain, even an overwhelmingly large hit is absorbed in full and never spills over into your red hearts; when attacked, 1 charge is spent to counterattack the attacker. The moment the yellow hearts run out, the shield disappears immediately. The shield itself never expires. A shielded player is enveloped in a pale-blue energy sphere that other players can see too.

### New Sign — Fengshui Master Sign (Legendary)

- Active "Baize's Blessing": choose a player within 16 blocks — or yourself. All healing the target receives that overflows past full health is converted into an equal amount of attack power, lasting 2:00. The effect applies immediately, but the countdown only starts on the target's first melee hit. The caster also gains 1 Blessing Talisman and converts every Misfortune Talisman they hold into the same number of Blessing Talismans.
- Passive "Fortune and Misfortune Intertwined": when the final dice roll of a dice battle comes up 1, gain 1 Misfortune Talisman; on 6, gain 1 Blessing Talisman (cards are bound to the recipient when granted and cannot be used by others).
- Passive "Perfect Helper": when Baize's Blessing is applied to a player wearing the Boss Sign, that player additionally gains 1 Recharged Energy stack.

### New Sign — Cult Leader Sign (Legendary)

- Active "Divine Descent": choose another player and lock in 50% of their attack and defense power for yourself (locked at the moment of casting), lasting 2:00 (the countdown likewise starts on the target's first melee hit). During Divine Descent, each new target the victim attacks spends 1 stack of the caster's "Fox Light", and the attack against that target gains extra attack power equal to the caster's own attack power plus the current Fox Light stacks; subsequent attacks against the same target keep the bonus. Divine Descent cannot be recast while active.
- Passive "Fox Light": gain 1 stack each time you craft, obtain, or equip attack cards, and +3 stacks immediately when Divine Descent is cast. Cap 20 stacks, kept through death; cleared when the sign is unequipped.

### New Sign — Oasis Queen's Sign (Rare)

- Active "Queen's Privilege": immediately gain 2 random battle cards and 1 random effect card (never exclusive cards), plus a 3:00 "Queen's Privilege" effect. The temporary cards issued come with an enchantment glint and can only be equipped and used — they cannot be dropped or moved into any other container (doing so destroys them). When the effect ends (or the sign is unequipped, or you die), every temporary card is removed, including ones assembled into the die. Requires 3 free inventory slots to cast; you may hold at most 9 temporary cards; the ability can be cast again while active (new cards stack on top and the duration resets to 3:00).
- Passive "Intimidation": each attack card assembled into the die grants +2 attack, each defense card grants +2 defense.

### New Sign — Mamushi Sign (Legendary)

- Active "Chain Reaction": gain 1 "Bite" yourself, and grant every friendly teammate within 12 blocks 1 random card (anyone who currently holds no cards gets one extra). Without a team, the targets become all other players in the dimension instead, up to 32 people in total, nearest first. Hard cooldown 1:00 — it cannot be shortened by any means.
- Passive "King of the Marsh": each time you cause another character to gain cards, gain 1 stack of "Awakening" per recipient (max 3 stacks per trigger; cards granted to yourself don't count). At 8 stacks you automatically enter "True Dragon Form": +5 attack power, the active loses its range limit (whole dimension), and every "Bite" in your hand — plus those gained from later casts — is converted into "Dragon's Roar". Awakening and True Dragon Form are kept through death and only reset when the sign is unequipped.

### New Sign — Sherry Sign (Bizarre)

- Active "Strong-arm Throw": hurl every hostile target within 12 blocks that is in your line of sight to a spot 1–6 blocks in front of you. On landing they take 2 points of spell damage and gain 1 "Marked" stack; at 4 stacks of "Reasoning Time" they take 5 extra damage. If the landing spot is blocked, the throw automatically looks further inward; if there is no valid spot at all, the cast is free (no cooldown).
- Passive "Detective's Strike": each time you attack a new hostile target with at least 20 max health, gain 1 stack of "Reasoning Time" (each target grants at most 1 stack, cap 4). Each stack gives +1 attack power and -1 damage taken; exactly 1 stack is lost when a Dice Blessing ends. Kept through death; reset when the sign is unequipped.
- Passive "Friend's Guard": while a teammate wears the Hanna Sign, that player takes -1 damage.

### New Sign — Hanna Sign (Bizarre)

- Active "Float Magic": gain "Witch's Float" for 1:00: movement speed +20%, fall damage -100%, every melee attack against you is dodged, and you cannot teleport with an Ender Pearl.
- Passive "Fantasy Heiress": when your battle dice roll comes up 6, gain 1 Star Coin; when passing a friendly player, gain 1 Star Coin and 1 stack of "Doll Crafting" (while you are under Witch's Float, that player gains 3 Star Coins instead), at most once per 1:00. At 7 stacks, "Doll Crafting" turns into "Doll Complete": afterwards, passing a friendly player additionally grants them Swiftness II (1:00) and 3 Star Coins.
- Passive "Friend's Blessing": when passing a player wearing the Sherry Sign, grant that player Strength II (1:00) + Resistance (1:00) and 1 stack of "Reasoning Time", at most once per 1:00.

### New Sign — Megas Sign (Legendary)

- Active "Orbital Bombardment": designate a hostile target, then consume cards from your inventory to call down an orbital bombardment on the target and a 12-block area around it. Every 2 cards consumed adds 1 bombardment strike (max 10); each strike hits 1 random monster in range for 2 base damage; for every 1 point of cost among the consumed cards, the bombardment deals 1 more damage. Total output per cast is capped at 800 damage (and 80 per individual strike); once the cap is reached, no more cards are consumed. Cards are taken in hotbar → off-hand → backpack order and any surplus is left untouched. Consuming 6 or more cards with a single cast triggers "Precision Strike": each stack makes the struck target take +1 damage from orbital bombardment, permanently until it dies — this bonus is independent of the caps above.
- Passive "Resupply": whenever you hold fewer than 6 cards, automatically gain 1 random card every 1:00.

### Star Coin Wallet

- Star Coins and Star Coin Bags can now be deposited into a wallet. The balance is stored on your player data and is not lost on death. A deposit button at the top-left of the inventory sweeps every Star Coin and Star Coin Bag from your 36 inventory slots and off-hand into the wallet at face value (1 coin = 1, 1 bag = 9); next to it are withdrawal buttons for coins and bags (left-click takes 1, Shift + left-click takes as many as possible).
- Newly obtained Star Coins go straight into the wallet by default (pickup, grants and the Business Sign's exchanges all count). Mechanics that consume Star Coins from the inventory — like the Star Coin Hammer — are unchanged and still only see physical coins.
- The balance bar shows your wallet total in real time, with this mod's own transactions pushed instantly; in Creative mode the balance bar and wallet button stay visible on every tab.
- Incompatible with third-party currency mods such as Magic Coins / SG-Economy: while the wallet is enabled the game refuses to start and tells you why; disable the wallet in the config to coexist.

### Target Selector Overhauled

- Now click-based: left-click = confirm; right-click = shows that the skill cannot be used on yourself (the Game Master Sign is the exception — right-click casts it on yourself); right-click + sneak = cancel; Esc (pause menu) cancels; the J key (active skill) cancels at any time. The selector no longer swallows your keyboard — movement, inventory, chat, hotbar scrolling and other mods' keybinds all keep working.
- The selection outline is redrawn as a translucent prism that hugs the mob's actually visible shape — a zombie's arms, a spider's legs, a horse's head and neck all fit inside the frame. Bringing the crosshair anywhere inside the outline counts as pointing at that target.
- The screen center shows the target's name, distance and type (color-coded green/red/yellow for friendly/hostile/neutral), and the action-bar hints change color by state and show the remaining time.

### Living Page Reworked

- The Living Page is no longer a passive buff card: holding it in your main hand automatically opens the target selector. It can only be used on hostile targets within 32 blocks, including vertical distance. On confirmation the page flies at high speed toward the target — it always hits and passes through blocks — dealing 2 + the Investigator Sign's used page count points of spell damage and applying 1 "Marked" stack.
- Hitting a target that already has 3 or more "Marked" stacks grants +1 card play this round. Damage numbers now use the spell-damage green, the tooltip value updates in real time, and the flight VFX no longer blocks your first-person view.

## New Content

### New Chips — Shooting Star Series

- Purple Shooting Star (Epic) / Golden Shooting Star (Legendary): when you pass a hostile target, deal 1 point (2 for golden) of true damage to it and gain 1 stack of "Starlight"; the Golden Shooting Star additionally deals damage equal to your current Starlight stacks against elite monsters and bosses. Triggers at most once every 10 seconds (both chips share the timer) and only against hostile targets. The particle falls from above the target's head; when both are equipped, purple lands first and golden follows 0.5 seconds later.

### New Cards (Exclusive — only the recipient can use them)

- Blessing Talisman (Fengshui Master Sign exclusive): hold to select a target — any player within 16 blocks or yourself — restoring 2 health and granting +1 card play this round.
- Misfortune Talisman (Fengshui Master Sign exclusive): hold to select a target — hostile targets only — dealing 1 damage. Holding several invites "Misfortune": every 2:00 you take damage equal to the number held; cards placed in containers no longer count.
- Bite (Mamushi Sign exclusive): cost 2, +3 attack; when equipped and a Dice Blessing triggers, gain 1 stack of "Awakening" and bonus attack power equal to your Awakening stacks.
- Dragon's Roar (Mamushi Sign exclusive): cost 3, +3 attack; hits apply Slowness III and -4 defense (1:00 each). Only obtainable by Mamushi players in True Dragon Form; your "Bite" cards convert automatically when you enter it.

### New Rarities: Pinnacle and Bizarre

- Five custom rarity tiers are introduced: Rare (aqua) / Epic (pink-purple) / Legendary (gold) / Pinnacle (bright red) / Bizarre (rainbow); Common stays vanilla. The Nether Star Dice is the first Pinnacle item.
- The Bizarre tier has no single color: its tooltip border is a flowing rainbow (currently 8 items: the 6 exclusive cards + Sherry Sign + Hanna Sign). Legendary / Pinnacle tooltips use the tier color for the border; Rare / Epic keep the vanilla look. Enchanting no longer bumps an item's tier.

### New Sounds

- Numerous custom sound effects: wallet deposit / coin withdrawal / bag withdrawal, using effect cards (Berserk / King's Power / Unwavering have their own cast sounds; the rest differ for other targets vs. self), active skills firing, glass dice shattering, orbital bombardment impacts, Living Page and Misfortune Talisman hits (tiered by the damage dealt), and more.

### New Loot

- First adventure gift: the very first loot chest a player opens in a save is guaranteed to contain 1 die — once per player per save (a new save resets it; dying does not re-trigger it). Only loot chests count; chests you place yourself never trigger it.
- Glass Dice now drop from loot chests: 2% in ordinary chests, 5% in End City chests.
- Blank Chips now drop from loot chests at 3% (Buried Treasure remains a guaranteed 100%).

### Commands

- /starcoin add|set|remove|get|rank: wallet ledger debug commands.
- /astralparty gains finishsigncooldown: instantly ends the active skill cooldown, for debugging.

## Balance & Quality Updates

- Card tier re-rank: Attack Card (M)/(L)/(XL), Defense Card (M)/(L)/(XL), Berserk, Fight Poison with Poison and You Have, I Have are all Rare (blue) now; King's Power drops to Epic (purple).
- Flashlight Chip - High Beam: Starlight is now gained by killing a hostile target with at least 20 max health (+1 per kill); the "every 4 stacks of Starlight = +1 attack damage" mechanic is unchanged. The Cutter Chip and Flashlight Chip status icons now only show during a Dice Blessing.
- The Starlight / Star Coin / healing-point bonuses are now settled as independent "extra damage": they ignore armor and armor toughness, punch through damage invulnerability frames, and are no longer inflated by skills that scale off your attack stat. The Cutter Chip's flat +2 / +4 remains attack power — only its healing-point bonus goes through this channel; the two damage types are kept strictly apart.
- Star Coin Hammer: now requires at least 32 Star Coins to run, consumes 18 coins per Dice Blessing, and its attack damage bonus is capped at 100 (still 30% of the coins you hold).
- Obsidian Dice: its mitigation changes from "fire damage -70%" to "explosion damage -50%".
- Whetstone: the "while health is above 1, a single hit can at most reduce your health to 1" protection gains a 1:00 cooldown; the survival priority is now explicitly "Airbag > Whetstone".
- "Hostile target" rules relaxed: neutral mobs now always count (tamed pets excluded) — untamed wolves, llamas and trader llamas are hostile targets without needing to be angered first; angered neutral mobs can be picked by the target selector; training dummies (dummmmy) always count as hostile. The Gunsmith Sign's passive and the splash targeting of Directional Blast / Electric Glove / the Boss Sign's spillover are relaxed accordingly, and splash damage can no longer hit yourself.
- Piercing Gun: no longer requires "a damage effect card has been used" — plain arrows, thrown items and spells benefit too; its target rule now matches the Ninja Star.
- Ninja Star: the bonus now only requires "dealing ranged / magic damage to the target".
- Magic Quiver: now works as "after using a damage effect card and dealing ranged or spell damage to a marked target, refund the first effect card you used and apply 1 Marked stack", at most once per 30 seconds.
- Target: the Marked stack is now applied to the nearest hostile target other than the one you are attacking.
- Smart Watch: the inventory card refill threshold drops from 10 cards to 6.
- Charge path fixed-effect rework: while you have Charge, sign active skill cooldowns are at most 160 seconds and effect card cooldowns at most 20 seconds (previously a flat -20%).
- Slime Sign's active "Healing Slime" is now a target-selection skill, usable on any player (right-click for yourself); the healing and area effects follow the target.
- Express Delivery, Luxury Feast, You Have, I Have and Berserk are now "hold to select a target": holding the card in your main hand opens the selector, and it only takes effect and is consumed on confirmation. Berserk can now only target players or yourself.
- The Bounty reward pool now includes the Legendary tier (Legendary dice, signs and chips); Pinnacle and Bizarre never enter any pool. The Living Page and Fate's Guide no longer appear in bounty pools, and no exclusive card ever enters a pool.
- Tooltip and manual text has been thoroughly cleaned up and unified: the card cost line is now "Cost: ◆◆◆", the five sign actives share a common "Designate ..." opening, and a mass of implementation-detail annotations has been removed.
- The kanban sign is renamed to Kanban Musume Sign (display name only; items and saves are unaffected).
- New common config allow_firearm_damage (off by default): when enabled, bullet / shell type damage can also benefit from spell damage.
- Plus a great many description and behavior refinements.

## Bug Fixes

- Fixed the Whetstone computing against pre-absorption damage on 1.21.1 / 26.1.2 and over-mitigating while absorption hearts were present; it is now identical across all three lines.
- Fixed the "gain Starlight on equip" chips (bank card family, ATM Machine, Star Coin Hammer) never granting any Starlight. Equipping now works and unequipping revokes exactly what was granted — repeatedly swapping them can no longer print Starlight. The same class of bug on the Cursed Sword's inscription and the Boss Sign's timer start is fixed too.
- Fixed "lose 1 stack when a Dice Blessing ends" firing over and over and wiping every stack at once (Gunsmith Sign's "Weakness Insight" etc.); one blessing end now removes exactly 1 stack.
- Fixed the effect card tooltip's cooldown line never refreshing (it now shows the live remaining seconds).
- Fixed block-shaped garbled characters appearing inside multi-line card descriptions.
- Fixed the sign-specific feedback of target-selection signs being overwritten by the generic message (Gunsmith / Astrologer / Secret Detective).
- Fixed several signs' actives ending their "in effect" lock early after logging off on multiplayer servers (Business, Guardian, Sweeper, Vampire, Boss, Nancy Lu, Great Detective etc.), including three Nancy Lu Sign judgments across relogins.
- Fixed the % sign losing its color in the Speed Skates tooltips.
- Fixed this mod's loot chest items being rolled twice — Star Coins, Blank Chips, Glass Dice and Star Plates are back to their nominal probabilities.
- Plus a great many fixes to descriptions that did not match the actual behavior.
