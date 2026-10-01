# Astral Dice — Fabric 1.20.1 (port line)

**Version `1.3.5-alpha.1`** · alpha channel (pre-release) · Minecraft 1.20.1 · Fabric

> ⚠️ **This is the Fabric port line, not the stable release.** The stable builds are published for
> NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2. This line carries its own version number
> (`-alpha.N`, mirroring the 26.1.2 line's `-beta.N`) so it never occupies the stable line's version.

## Required dependencies

- **Fabric Loader 0.19.5+**
- **Fabric API** (1.20.1 line, 0.92.12)
- **StarEngine Lib — bundled, do NOT install it separately.** It is embedded through Jar-in-Jar
  (`1.0.6-alpha.1`). If your mods folder still holds a `starengine_lib-*.jar`, delete it — a loose file
  takes priority over the embedded copy, so the old one would be used instead.
- **Puzzles Lib 8.1.33+** (event bridge — install its own dependency **Forge Config API Port 8.0.3** too)
- **An accessory provider — either Trinkets 3.7.2+ or Accessories 1.0.0-beta.48+.** Fabric's `depends`
  is AND-only and cannot express "either one", so both are declared as optional; the mod checks at
  startup and refuses to start **only when neither** is present.

Optional: **Patchouli** (in-game guide). There is no Fabric build of Bountiful for 1.20.1, so the
bounty board is not available on this line.

## What's new in this build

- **Party / team detection now actually works.** The bundled StarEngine Lib had a reflection bug: the
  FTB Teams and Open Parties and Claims integrations pointed at classes and methods that **do not
  exist**, and the failure was swallowed silently — so anyone who had either mod installed was treated
  as "no team" and fell back to "everyone is friendly". Every reflection target has been corrected
  against the mods' real interfaces, and each backend now prints a **startup log line**
  (`back_ftb` / `back_opac` showing `on` or `off`) so that "installed but not working" is visible.

## Known limitations of this line

- **Worlds and player data are not interchangeable with the other lines.** Fabric and Forge/NeoForge
  store the same data differently; this is a platform difference, not something that will be fixed.
- **Not available on this line:** Curios-based accessories (use Trinkets / Accessories instead),
  Bountiful, and Iron's Spells 'n Spellbooks.
- **The 1.3.3 batch of changes has not been ported to this line yet** (for example the concealment
  effect).
- The two recipes that call for a **specific potion** match it **strictly** (NBT included). Some recipe
  viewers only render the base potion; the constraint is enforced on the server and is synced to the
  client's recipe book.
