# StarEngine Lib 1.0.12

Shared prerequisite library embedded (JarJar) in **Astral Dice**. This release makes the action-bar line
follow the vanilla dynamic position, so it no longer overlaps the item name once the status bars grow taller.

> You do **not** need to install this library by hand - every Astral Dice jar already ships it embedded.
> Installing a standalone copy only makes sense if you are building against it or debugging it.

## Fixes

- **The action bar could overlap the item-name tooltip while the player had absorption (yellow) hearts.**
  `client/ActionBarManager` drew the line at a fixed `guiHeight - 68`, which is only equivalent to the
  vanilla rule while `max(leftHeight, rightHeight) <= 59`. Once the left stack grew taller (three or more
  rows of absorption hearts, or armour + absorption) vanilla's item name moved **up** while the action bar
  stayed put, so the two overlapped. The line now mirrors vanilla `Gui#renderOverlayMessage`:

  - **NeoForge lines** (`neoforge-1.21.1` / `neoforge-26.1.2`): `yShift = max(leftHeight, rightHeight) + (68 - 59)`
    and `y = guiHeight - max(yShift, 68)`. NeoForge patches both `Gui.leftHeight` and `Gui.rightHeight` to
    `public`, so the library can read them directly.
  - **`forge-1.20.1` / `fabric-1.20.1`**: **unchanged**. Plain vanilla 1.20.1 has no such mechanism - both the
    item name and the action bar sit at fixed positions there, so there is nothing to align to.

## Changes

- All four platforms go `1.0.11` -> `1.0.12` (the library version is shared across 1.21.1 / 1.20.1 / 26.1.2 /
  Fabric; only the Fabric sub-project's *mod* version carries the independent `-alpha.N` suffix).
