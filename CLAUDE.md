# FMSCraft

Fabric 1.21.1 mod (yarn mappings): Seven Deadly Sins boss mod for a group of 7 friends.
Design, rules and roadmap: `docs/DESIGN.md`. Server setup: `docs/SERVER.md`.

## Vanilla feel (applies to everything)
Everything added must feel like it could ship in vanilla Minecraft: 16x16 pixel-art textures in the vanilla palette,
blocky models built from cubes, vanilla-style names, sounds and particles, mechanics that use existing systems
(advancements, loot tables, structures, boss bars). No realistic textures, no modern UI, no out-of-place effects.
Bosses should read like the Wither, Ender Dragon or Warden: telegraphed attacks and fair, learnable patterns.

## Code
- Everything per-sin is keyed off `sin/Sin.java`; items live in `ModItems.FRAGMENTS/KEYS/SOULS` maps.
- Recipes, item models, advancements and `en_us.json` are generated: edit `datagen/*`, then run `gradlew runDatagen`
  (output in `src/main/generated`, committed). Don't hand-edit generated files.
- Must stay compatible with the user's other mods, `ragnarstwilightdimension` (`../Fabric-Twilight-1.21.1`) and
  `ragnarsmagicmod` (`../ragnars-magic-mod-1.21.1`). Reference them by id/tag only, not as compile dependencies.
  Dev runs load their built jars (paths in `gradle.properties`).
- `gradlew prepareServer` collects all server jars into `build/server-mods`.

## Tests
- `gradlew test`: JUnit tests for pure logic (`src/test`).
- `gradlew runGametest`: Fabric game tests in a real server with all three mods loaded (`src/gametest`).
  Add a game test for every new gameplay mechanic.
- Item textures come from `tools/textures/sin_textures.py` (needs Pillow); edit there and re-run instead of editing PNGs.
- `structure/greed_vault.nbt` is built by `tools/structures/greed_vault.py` from the two halves saved in the Builds
  world; re-run it instead of editing the template.
