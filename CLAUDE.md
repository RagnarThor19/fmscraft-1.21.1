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
- The title screen's "FMS EDITION" (`assets/minecraft/textures/gui/title/edition.png`) comes from
  `tools/textures/edition_texture.py`, which fits `tools/textures/source/fms_edition_logo.png` into vanilla's slot.
- `structure/greed_vault.nbt` is built by `tools/structures/greed_vault.py` from the two halves saved in the Builds
  world; re-run it instead of editing the template.
- `structure/arena/greed.nbt` (RAGGIMEL's island) is built by `tools/structures/greed_arena.py` from
  `greed_arena_source.nbt` (saved in a newer Minecraft; the script swaps blocks 1.21.1 lacks). Positions in `GreedArena`.
  His second arena, `structure/arena/greed_manor.nbt` (the Roswall manor), is built by `tools/structures/greed_manor.py`
  from the litematic in `~/for boss fights/Raggimel` (needs numpy). Positions in `GreedManor`; his sky is `GreedSky`.
- `structure/arena/wrath.nbt` (ORNAMIX's colosseum) is built by `tools/structures/wrath_arena.py` from
  `wrath_arena_source.nbt` (bars the pit's tunnels, adds the fires). Positions in `WrathArena`.
- GUMMSS's arena (Envy) is built in code by `EnvyArena.build`, not a template. Its watching eyes come from
  `tools/textures/envy_eye_textures.py` (from `tools/textures/source/analog_eye.png`), his glowing eyes from
  `tools/textures/gummss_textures.py`.
- LalliLarge's arenas (Gluttony): `structure/arena/gluttony.nbt` (the Earth) and `gluttony_castle.nbt` are built by
  `tools/structures/gluttony_arena.py` from `gluttony_earth_source.nbt` / `gluttony_castle_source.nbt`; the snowfield
  is built in code (`GluttonyArena`). His sky, snowfall and the nodding statue are drawn client-side by `GluttonySky`
  from the song's clock. His glowing eyes come from `tools/textures/lalli_textures.py`.
- MUGEN's arena (Lust): `structure/arena/lust.nbt` (the cherry compound) is built by `tools/structures/lust_arena.py`
  from `lust_arena_source.nbt`. Positions in `LustArena`. His eyes, the kunai/shuriken/orb/blaze item textures, the
  void spike block and the eclipse come from `tools/textures/mugen_textures.py` (his skin is `tools/textures/source/mugen_skin.png`). His sky
  is drawn client-side by `LustSky`.
- LEONARD's arena (Pride): `structure/arena/pride.nbt` (the plaza in the sky and the temple) is built by
  `tools/structures/pride_arena.py` from `pride_arena_source.nbt`. Positions (and Munch's) in `PrideArena`. His skin, the
  shades glint, Munch's skin and eyes, the lightning bolt and the traffic cone's texture come from
  `tools/textures/leonard_textures.py` (sources `leonard_skin.png`, `munch.png`); the cone's cube model is hand-written
  (`models/item/traffic_cone.json`). His sky, the cloud sea and banks, and Munch are drawn client-side by `PrideSky`.
- RICKY's arena (Sloth): `structure/arena/sloth.nbt` (the heart of the Abandoned City) is cut straight out of the world
  save by `tools/structures/sloth_arena.py` (needs numpy; the save is at `~/for boss fights/Ricky/Abandoned_City`). Positions
  in `SlothArena`; the flooded plain round it is the dimension's own flat ground. His skins and the glow come from
  `tools/textures/ricky_textures.py` (sources `ricky_skin.png`, `ricky_saiyan_skin.png`); his songs, power-up and teleport
  from `tools/sounds/sloth_sounds.py`. His sky is drawn client-side by `SlothSky`.
