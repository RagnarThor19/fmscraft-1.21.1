# FMSCraft — The Seven Deadly Sins

Seven friends, seven sins, seven boss fights. The game is finished when all seven sins are defeated
and their souls are placed in the True Ending structure.

## Golden rule: it should feel like vanilla Minecraft

Everything added to this mod (items, blocks, structures, bosses, arenas, sounds, UI) should feel like it could be
part of vanilla Minecraft:
- **Textures**: 16x16 pixel art, vanilla color palette and shading style. Keys look like trial keys, souls look
  like something next to the nether star or echo shard.
- **Models**: blocky, made of cubes like vanilla mobs. No smooth or high-poly shapes.
- **Structures**: built from vanilla blocks (plus the twilight mod's blocks where it fits), styled like ancient
  cities, bastions, trial chambers.
- **Mechanics**: reuse vanilla systems: advancements, loot tables, boss bars, structure generation, vanilla-style
  particles and sounds. Names in plain vanilla style ("Wrath Key", "Soul of Greed").
- **Bosses**: like the Wither, Ender Dragon and Warden: telegraphed attacks, readable patterns, fair to learn.
  Custom music is the one intentional step beyond vanilla, and it should still sit well next to C418 / Lena Raine.

## Core loop

1. Do the sin's challenge to get **fragments** (5 needed per key).
2. Put 5 fragments in a **plus shape** in a crafting table to get a **key** (e.g. Wrath Key).
3. Find that sin's **portal structure** and use the key on it. Each key opens the portal **once**.
4. The portal leads to that sin's **boss arena dimension**.
5. Beat the boss with **nobody dying** to get XP and the **Soul of <Sin>**.
6. Place all 7 souls in the overworld **True Ending** structure → music + credits + "True Ending" advancement for everyone.

## The sins

| Sin | Boss (friend) | How to get fragments | Fragments per success | Portal structure dimension | Status |
|---|---|---|---|---|---|
| Greed | Ragnar Thor | Bought from a villager: any villager reaching Expert has a 20% chance to add one extra trade, a Greed Fragment for **one** of 32 diamonds, 8 emerald blocks or 16 gold blocks (picked at random; 3 uses before it restocks; never replaces a vanilla trade) | 1 | Twilight (`ragnarstwilightdimension:twilight`) | Done |
| Wrath | Aron Eggert | Kill 3 wardens within **4 minutes** (any players). A red boss bar counts down for everyone online; the 3rd warden drops the fragment | 1 | Nether | Done |
| Pride | Leo Gunnlaugsson | Put full netherite armor on an armor stand. The armor crumbles into 5 fragments (so one set can't be reused forever) | 5 | Overworld | Done |
| Gluttony | Larus Armann | Eat an enchanted golden apple | 1 | Nether | Done |
| Envy | Gudmundur Sigurdsson | Rare chest loot: end city 5%, bastion treasure 8%, other bastion chests 3%, woodland mansion 8% | 1 | Overworld | Done |
| Lust | Karl Phillip | The same player breeds all 25 "Two by Two" animals (tag `fmscraft:lust_breedables`). Progress shows in the action bar and resets after the reward, so it can be repeated | 5 | Twilight | Done |
| Sloth | Patrik Atanaia | A tamed wolf (or tome summon from Ragnars Magic Mod) kills a mob: 5% chance | 1 | End | Done |

Tuning knobs: chances in `fragment/LootFragments.java`, `GreedTrade` (`CHANCE`, `LEVEL`, prices, `MAX_USES`), `SlothTrigger.CHANCE`, `WrathTracker.WINDOW_TICKS`.

Open questions:
- Wrath: the timer currently shows for everyone online. Change to nearby players only?

## Advancements

- One per sin, titled just the sin name ("Greed"), granted the first time you hold that sin's fragment. Done (data-driven, `inventory_changed`).
- "True Ending", granted from code when all 7 souls are placed. Advancement exists; the trigger is TODO.

## Boss fight rules (built: `fight/BossFights.java`)

- Using the key opens a 2x3 **sin portal** (tinted in the sin's color) for **30 seconds**. Everyone who steps
  through waits in the arena; a bar shows "The fight begins in N". Then the portal closes behind them and the
  boss spawns. If nobody went through, the key is wasted.
- Only one fight per sin at a time. Each sin has its own arena dimension (`fmscraft:<sin>_arena`), wiped and
  rebuilt before every fight, so the boss always starts fresh.
- **If any participant dies or disconnects**: the attempt ends for everyone, the boss is removed, the others are
  sent back to the portal, and the fallen player's items go into chests next to the portal.
  (A totem of undying still saves you. A server shutdown doesn't count as a loss.)
- **5 seconds before the portal closes**: a bell tolls at the portal ("The portal is closing!" for anyone nearby),
  then rising chimes on 3, 2, 1, and it closes with a beacon-off sound and a puff of particles. Players waiting
  inside hear the bell and chimes too.
- **On a win**: XP + Soul of <Sin>, the sin is marked defeated (`FmsWorldState`), a server-wide message, and an
  exit portal opens in the arena to go home.
- **Tome wheel** (`reward/TomeWheel.java`): everyone in the fight spins their own wheel, 50% Beginner / 30%
  Advanced / 20% Master, then gets a random Ragnars Magic Mod tome of that tier. 10 slices (5/3/2), drawn as pixel art, clicks as
  slices pass, tier sound on landing. The server rolls the result first and gives the tome itself when the 10-second spin ends;
  the wheel only animates toward it, so lag or quitting can't change or lose the prize. Tomes are found by reading
  the magic mod's `TomeItem` tiers at runtime (`compat/MagicTomes.java`).
- Defeated bosses can be fought again with another key.
- Only one boss per arena: bosses are never saved with the world, and any boss in an arena that isn't the running
  fight's boss removes itself (so one left over from a crash can't become a second boss).
- Players left in an arena after a restart are sent back to their portal when they log in.
- Test without structures: `/fmscraft fight <sin>`, `/fmscraft end <sin>`, `/fmscraft status`, `/fmscraft undefeat <sin>`.

**How each structure hooks in (decided per structure, together):** whatever the structure's lock mechanic is,
it consumes the key and calls `BossFights.open(sin, world, portalBasePos, axis)`.

**Arenas:** build an arena in-game, save it with a structure block as `fmscraft:arena/<sin>` (centered on x/z 0,
floor at y 63) and put the `.nbt` in `data/fmscraft/structure/arena/`. Until then a plain deepslate platform is used.

**Music:** boss themes play on the **Jukebox/Note Blocks** volume slider (so players who turned background music
off still hear them), loop, and pause vanilla music. The test boss uses Pigstep, then Otherside in phase 2.

## Boss template

Every boss extends `boss/SinBossEntity` and is swapped in for its sin in `boss/ModBossEntities.forSin`
(Greed is RAGGIMEL; the other sins still use the stand-in `TestBossEntity`):
- Boss bar named after the friend (`bossBarName` to call it something else).
- **Phase 2 at half health** (`onPhaseTwo`; `phaseTwoHealth` to move it) — switch songs and behaviour here. A boss with
  a transition returns false from `startsPhaseTwoSongAtOnce` and calls `playSong(song, fadeTicks)` when it's ready.
- **Beat clock**: each phase has a `BossSong(soundId, bpm, firstBeatTick)`; `onBeat(phase, beat)` fires on each beat so attacks can land on the music.
- **Intro** (optional, `ModBossEntities.introFor`): a `fight/FightIntro` cutscene that plays in the arena while the
  portal is open, timed from the portal opening so late arrivals join it mid-way. A song it starts keeps playing into
  the fight. `fight/Cinematics` freezes players (no moving, hitting, using or being hurt; the void still kills) and
  covers their screens (`BossScreenPayload`: an intro picture, black, or a flash).
- **Arena layout** (optional, `Arenas.layout`): where the template goes, the spawns and the exit portal, for arenas
  that aren't a 36-block disc centered on 0,0.

Per boss, fill in:

```
Boss: <friend> — Sin of <sin>
Song (phase 1): <file>, BPM <n>, first beat at <seconds>
Song (phase 2): <file or same>, BPM <n>
Arena: <description>
Phase 1 attacks: <attack> on every <n> beats / at beat <n> ...
Phase 2 attacks: ...
Health / armor:
Special mechanics:
```

## RAGGIMEL (Greed) (built: `boss/greed/`)

```
Boss: Ragnar Thor - Sin of Greed. Boss bar: RAGGIMEL (light blue). His own skin on a player model.
Song (phase 1): Milky - Just The Way You Are, 130 BPM, first beat 0.75 s (music.greed.phase_one)
Song (phase 2): Lennsko - Panzerknacker (slowed), measured ~172 BPM, attacks on every other beat (86) (music.greed.phase_two)
Arena: the floating island (structure/arena/greed.nbt, tools/structures/greed_arena.py). Players arrive in the
       ring around the amethyst shrine at its east end; he comes down on the flat plain just west of it (x -30, z 0),
       where an empty deepslate-and-gold frame on the north edge fills with the exit portal when he's beaten.
       Pillars and trees block lasers. Falling off kills you. Own dimension type (greed_arena): always noon.
Health / armor: 1000 HP, 10 armor, 6 toughness; takes x0.75 damage in phase 1, x0.85 in phase 2, x1.5 while
       stunned, at most 30 per hit, and phase 1 can't be skipped by one big hit. Aimed at 3-4 minutes for 5-7 players.
```

- **Intro (the 30 s the portal is open):** everyone who comes through is frozen behind the Seven Deadly Sins wheel
  (`textures/gui/seven_sins.png`). From ~16 s it slowly zooms in on Greed; at 25 s Just The Way You Are fades in;
  at 30 s the wheel fades and RAGGIMEL floats down in his bubble (shoot the bubble and he drops, stunned).
- **Phase 1 (100-70%), light:** pale blue noon sky, white specks drifting. He walks after people and punches, and
  on the beat picks one of: *Bubble Ride* (floats over everyone in a bubble dropping sleep darts; any hit or arrow
  pops it, he falls and is stunned for 3.5 s taking extra damage), *Bubble Volley* (two slow bubbles that lift who
  they touch for 3.5 s; a teammate's hit pops them early), *Sleep Darts* (three, at three players; Ragnars Magic
  Mod's dart: drowsy, then asleep until hurt), *Dash Punch* (crouches with the goat's ram sound, charges), *Splash*
  (hops and lands with a ring of water to jump over).
- **Phase change (at 70%):** everything goes black, everyone freezes, music cuts; heartbeat and cave sounds for
  5 s, then `greed_switch_voiceline`. Under the black he grows to 3 blocks, moves to a perch over the island's
  middle shrine 50 blocks west and night falls (dark blue sky, stars, no sun, dimmer light, closer fog). As the
  voice line ends the black lifts, lightning strikes, Panzerknacker fades in, and he fires lasers from the perch
  for 12 s (break line of sight) before leaping back into the ring with a three-ring slam. Timings: `GreedTimeline`.
- **Phase 2 (70-0%), scary:** faster, punches for 9, rests less. Picks from *Laser* (a thin beam follows you for
  1.5 s, freezes white, fires; prefers anyone stuck in a bubble), *Laser Sweep* (a beam along the ground spins all
  the way round him: jump it), *Bubble Prison* (one bubble per player, lifting 7.5 blocks for 5.5 s: pop each other
  out), *Greed's Due* (a gold circle under every player, coins crash into it 1.6 s later), *Blink Strike* (portal
  particles show where he'll appear behind three players in turn), *Tidal Slam* (three soul-fire rings to jump),
  *Dart Fan* (seven darts) and a double *Dash Punch*. Below 30% he goes into a frenzy: a roar and a flash, two
  lasers at once, a second wave of coins, four blink strikes, shorter rests, more lightning.
- **The night:** snowflakes, glow specks and souls drifting around everyone, sparks off him, the shrine pulsing a
  ring of light every 8 beats, cosmetic lightning striking the island and the void (with a flash on screen).
  When he dies the night lifts again.
- **Magic mod:** he holds its diamond staff (netherite in phase 2), can't be put to sleep or carried off in a
  player's bubble (he wakes / bursts it at once), and status effects don't stick to him. Without the mod his darts
  are plain arrows. The mod's built jar has to include Sleep Darts for those to work.
- Tuning knobs: `RaggimelEntity` (health, damage taken, `MAX_HIT`, rests, attack weights and gaps in
  `chooseAttack`), `GreedTimeline` (cutscene timings), `GreedArena` (positions).
- Tests: `RaggimelGameTests` (the whole fight flow in the real arena, the phase 1 clamp, bubbles, magic bubbles),
  `GreedTimelineTest`.

## Structures

- 7 portal structures, each different, in the dimensions listed above.
- **Every structure uses the same lock, the Sin Portal Frame** (`block/SinPortalFrameBlock`, textures from
  `tools/textures/frame_textures.py`). It's the vanilla end portal frame recolored per sin (`sin` block state):
  every pixel keeps its brightness and is pulled partway toward the sin's color, then dulled a little. The creative
  tab has one frame per sin ("Envy Portal Frame"...): one item, the sin carried in its block state component. Use that sin's key on it: the key is used up, slots in and stands upright out
  of the keyhole (the key's own item texture), and a 3x3 portal fills the frame 4 blocks behind the lock
  (`BossFights.open(...)`). Other keys don't fit. When the fight ends the key crumbles away.
  Place one with `SinPortalFrameBlock.of(sin, facing)`, facing the way players stand to use it.
  A few specks of dust in the sin's color drift up and out of the keyhole (more while a key is in).
- **Atmosphere** (`structure/StructureAmbience.java`): sparse particles sent only to players inside a structure.
  Envy maze: moss dust sifting from the ceiling, now and then a faint green mote. Tower of Pride: gold glints
  (wax-on sparkle) in the air, and wind-blown white ash above half height. Sphere of Sloth: dark ash hanging in the
  air between the shells. Greed Vault (down in the rooms): thick with dust, dark specks hanging everywhere and grey
  dust sifting from the ceiling, and the odd flake of gold leaf.
  Lust's Garden: cherry blossom drifting down through the fog, spores hanging in the air.
  Den of Wrath (the hall): crimson spores drifting, and sculk soul specks rising off the floor.
  Hall of Gluttony (the hall): white ash drifting down from the vault, and kitchen smoke hanging low.
- **Sealed rooms** (`structure/SealedRooms.java`): nothing teleports at random (chorus fruit, endermen) into a room a
  challenge still has shut: behind the Greed Vault's door, the Lust crypt, the Sloth core, behind the Gluttony gate.
  Chorus fruit is food, so the barriers allow it, and it jumps 8 blocks through walls; without this one player could
  eat past a challenge and open it for everyone. A chorus fruit that would land in one just lands somewhere else.
- Statues, the Mother Warden and the Gorged Brute are only put in once their chunk's entities have loaded
  (`shouldTickEntity`), or one put in too early is lost track of and a second is put in beside it.
- 1 overworld True Ending structure with 7 end-gateway-like soul pedestals.

### Envy: the buried maze (built)

`structure/envy/` + `entity/EnvyStatueEntity`. Map of one maze: `docs/envy-maze-example.png`
(gold = chests, red = statue starts, teal = safe room, yellow = tunnel, green = the lock).

- **Where:** overworld only, fully buried (floor at y = -40, no entrance: dig in). Caves are carved before
  structures, and the maze is generated after mineshafts, trial chambers, strongholds and ores, replacing every block
  in its box; a 2-block deepslate tile shell above and below seals it. Nothing generates into it. Rare: one per roughly
  100x100 chunks, about 1600 blocks apart (`worldgen/structure_set/envy_maze.json`), kept away from ancient cities.
  The maze is centered on the chunk it starts in: the game only builds a structure up to 8 chunks from its start,
  and the maze is 10 chunks across (mazes from before this fix are missing their east and south edges; statues
  there spawn on the nearest built cell instead). `/locate structure fmscraft:envy_maze`.
- **Size:** 31x31 grid of 5-block units, about 155x155 blocks. Every walkway is 5 wide and 5 high; walls are
  5 thick. Generated from a seed (recursive backtracker, then about a third of dead ends knocked through so there
  are loops to get cut off in).
- **Look:** mossy/cracked stone bricks, oxidized copper baseboards, oxidized chiseled copper "watching eyes" in
  the walls, moss and mossy cobble floors, vines and glow lichen along the walls and ceiling only (walkways stay
  clear), verdant froglights.
- **Loot:** up to 10 chests in dead ends (`loot_table/chests/envy_maze.json`): emeralds, gold, diamonds,
  golden apples, enchanted books, XP, a rare god apple, and a 15% Envy Fragment.
- **Safe room:** 3x3 units in the middle, double-walled, reached only through a 1-wide, 2-high, 10-block tunnel
  the statue can't fit through. The room is built in-game and saved as the template
  `data/fmscraft/structure/envy_portal_room.nbt` (17x7x17: the 15x15 room plus its wall ring, doorway in the middle
  of the south wall, rotated to face the tunnel). The Envy **Sin Portal Frame** sits in the middle facing the tunnel
  (whatever frame is in the template is swapped for it): use the Envy Key on it and a 3x3 portal fills the shrine
  4 blocks behind it (the shrine's opening must stay empty until then).
  - Chests: average loot (`chests/envy_safe_room_plain`), except one half of one double chest (the left half
    with the lowest template position) with a 10% Advanced and a 2% Master tome (`chests/envy_safe_room`).
  - Barrels: junk loot with a 3% Beginner and 1% Advanced tome each (`chests/envy_barrel`).
  - To change the room: edit it in the Builds world, save it with a structure block as `envy_portal_room`, and copy
    `run/saves/Builds/generated/minecraft/structures/envy_portal_room.nbt` over the one in `data/fmscraft/structure/`.
  - Tomes come from the `fmscraft:random_tome` loot function (`"tier": "beginner" | "advanced" | "master"`),
    which picks a random Ragnars Magic Mod tome of that tier.
- **The Statue of Envy** (always exactly 4 per maze, kept up by `structure/envy/EnvyMazes.java`: when a player comes
  within 50 blocks (measured flat) of a maze that has fewer, new ones appear in its four corners, one per corner, at
  least 24 blocks from any player; extras from old saves crumble when they load), after Giotto's *Invidia* (Scrovegni Chapel, c. 1306): huge ears, a serpent
  from her mouth biting back at her own glowing green eyes, a purse clutched in one hand, the other clawing
  upward, green copper flames at her feet, all carved in mossy stone on a plinth.
  - Stands still and silent until a player is within 50 blocks (flat) of its maze, then starts roaming.
  - No animation, never stops while awake. Slides exactly 5 blocks (one unit) in 12 ticks (0.6 s), always down the middle of
    the walkway and always facing squarely along it, with a deep grinding rumble and stone dust. At junctions it picks a way at random (55% straight on);
    at turns and dead ends it grinds slowly round (1.5 s per quarter turn) and carries on.
  - Touching it deals 20 damage (10 hearts) and throws you aside. It can't be hurt, burned, blown up or pushed.
  - Each statue remembers its maze (origin + seed) and only ever moves into that maze's corridors, so it can never
    leave the maze or pass through a wall, even one that has been dug out. Any gap with 3+ solid blocks also stops
    it; a block or two that players put in its way is smashed. Statues from before this rule find their maze on
    load and, if they've strayed, are put back on the nearest corridor.

### Greed: the Greed Vault (built)

`structure/greed/` + `entity/GreedStatueEntity`. Built in-game in the Builds world.
- **Where:** the twilight dimension only (`tags/worldgen/biome/has_structure/greed_vault.json`), rare: one chance per
  80x80 chunks, about 1280 blocks apart (`worldgen/structure_set/greed_vault.json`). Buried just under the surface:
  only the **pipe** sticks up out of the ground (3-4 blocks), and the rooms stay at least a block under the ground
  everywhere above them. Turned to a random side, centered on its start chunk. It generates after all the twilight
  features (trees, graves...) so nothing grows into it, and anything over the pipe's mouth is cleared. No mobs spawn
  inside. `/locate structure fmscraft:greed_vault` (in the twilight).
- **The template:** a structure block only saves 48 blocks across, so the vault was saved in two halves,
  `greed_vault2` (west, x 0-28) and `greed_vault` (east, x 29-76). `tools/structures/greed_vault.py` joins them into
  one 77x15x35 template, `data/fmscraft/structure/greed_vault.nbt`, dropping the air saved around the building (so
  it doesn't carve a cave into the terrain), adding the ladder up the pipe, and writing the second 7 clue into the
  empty book. To change the vault: edit it in the Builds world, re-save both halves with their structure blocks and
  re-run the script. Positions the code relies on are in `GreedVaultLayout` and checked by a game test.
- **Getting in and out:** drop down the pipe (about 13 blocks: it hurts) and climb back out by the ladder in it.
  Anyone down in the rooms (not up the pipe or on the surface, so you can build up to a pipe that stands tall) has
  **Mining Fatigue X**, **Slowness II** and **Block Barrier** (a new effect: no placing or breaking blocks or emptying
  buckets, like adventure mode), so there's no digging, pillaring or walling off. Down there the fog turns nearly black and closes in
  to about 12 blocks, and the light itself dims like the Darkness effect but without the pulsing (`VaultFog`, told
  by the server with `VaultFogPayload`), fading in and out over two seconds.
- **The puzzle:** clue books hidden in the vault spell out 1, 5 and 7 (5: a chest and a decorated pot, Icelandic;
  7: a chest, German, and the chiseled bookshelf, French; 1: a chest, "One piece lol", and the "One Piece" sign).
  The clue chests have no loot table, so the books are always there; if one is taken, it's put back when the vault
  resets. Eight levers in two rows, numbered like a book from in front of the wall (top row 1-4 left to right,
  bottom row 5-8). **Exactly 1, 5 and 7 on** and the vault is solved: everyone in it hears the challenge-complete
  fanfare, the effects lift, the statues crumble away, and the 4x4 iron block door sinks into the floor a row at a
  time. Any other combination does nothing. It stays solved (no effects, no statues, door open) until it resets. Anyone behind the door while
  it's shut (back from the fight after a reset, logged out in there) gets it opened, so nobody is ever shut in.
- **Behind the door:** the treasury and the portal room. The Greed **Sin Portal Frame** stands 4 blocks in front of
  the tuff portal frame: use the Greed Key and a 3x3 portal fills it.
- **Loot:** barrels get a bit of loot (gold and iron nuggets, raw gold, emeralds, bread, the odd diamond...) and a
  2% chance of a tome, Beginner or Advanced (`chests/greed_vault_barrel`).
- **The Statues of Greed** (exactly 3 per vault): when someone comes within 16 blocks, one rises from each of the
  three smooth stone blocks in the floor. A robed figure of pale stone, its gold leaf flaking off except on its
  hands, purse and crown; head hanging tilted on a too-long neck, arms almost to the floor, a blank face with two
  pinprick gold eyes and a smile cut too wide.
  - Hunts the nearest player inside the vault, along the corridors, silently and at a crawl (~0.25 blocks a second).
    It's drawn in stop motion (its pose only updates every 16 ticks), and now and then the cave noises come from
    right where it stands. Its colours are dull: grey stone and tarnished gold.
  - Touching it deals 28.8 damage (over 14 hearts, before armor) and throws you back. It can't be hurt, pushed, burned,
    blown up or slowed by cobwebs, can't climb the ladder, and is put back on its stone if it ever leaves the vault.
  - Player-sized, so it can follow you anywhere in the vault you can go.
- **Resetting:** once nobody is within 48 blocks (and no Greed fight is running), the statues sink back into their
  stones, the door closes, the levers go back off and taken clue books reappear. Statues are never saved: they rise
  again on the next visit. Everything else is read back from the world, so a restart changes nothing.
- Tuning knobs: `GreedVaults` (margins, effect levels), `GreedStatueEntity.DAMAGE` / `SPEED`, the lever code in
  `GreedVaultLayout.CODE`.

### Pride: the Tower of Pride (built)

`structure/pride/`. After Bruegel's *Tower of Babel* and the spiral minaret of Samarra.
- **Where:** overworld, very rare: one chance per 300x300 chunks (~4800 blocks apart, `worldgen/structure_set/tower_of_pride.json`),
  and only where the ground is dry and level (within 20 blocks) in plains, sunflower plains, meadow, savanna,
  desert, badlands, snowy plains, forest, birch forest or taiga (`tags/worldgen/biome/has_structure/tower_of_pride.json`),
  so in practice further apart than that. Kept 8 chunks from villages. `/locate structure fmscraft:tower_of_pride`.
- **Shape** (`PrideTowerShape`, pure logic, unit tested): a cone 97 blocks across at the foot, 210 blocks tall
  (less if the ground is high, never under 150), with one ramp winding 5 times round the outside up to a round summit.
  Each turn steps in 6 blocks (5 to walk + a battlemented parapet). The ramp climbs in half-block steps (slabs),
  so you never have to jump, and every open edge has a parapet.
- **Look:** the walls between turns are arcades: storeys of round-topped arches with dark recesses, a ledge between
  storeys, and keystones. The stone gets prouder as you climb: mossy, cracked stone bricks with vines at the foot,
  then sandstone with terracotta recesses, then white quartz with blackstone recesses, blended in clumps. Lanterns
  stand on the parapet. The foot sits on a paved, levelled plaza that blends back into the land.
- **Summit:** a ring of quartz columns carrying a lintel; every other column rises into a point of the crown, capped
  with gold and an end rod. The Pride **Sin Portal Frame** stands in the middle, facing south, with a gold-cornered
  quartz frame 4 blocks behind it: use the Pride Key on it and a 3x3 portal fills the frame.
- **Loot:** a chest halfway round every turn of the ramp, backed against the parapet (`chests/tower_of_pride`: gold,
  golden carrots, diamonds, enchanted books, golden horse armor..., plus a 5% Beginner and a 5% Advanced tome) and two by the lock (`chests/tower_of_pride_summit`: gold blocks,
  diamonds, god apple, level-30 books, netherite scrap, Pigstep, and the same 10% Advanced / 2% Master tome
  chance as the Envy safe room).
- Not built yet: the mirror hazard (reflections of you that only move when you aren't looking).

### Sloth: the Sphere of Sloth (built)

`structure/sloth/`. Preview: `docs/sloth-sphere.png` (left: from the night side; right: cut open).
- **Where:** the outer End only, at least 1500 blocks from the dragon's island, hanging alone in the void: no land
  within 80 blocks of its center (it looks up to 2 chunks around its start chunk for a clear spot, otherwise skips).
  Very rare: one chance per 180x180 chunks (~2900 blocks apart, `worldgen/structure_set/sphere_of_sloth.json`), kept
  6 chunks from end cities, and many chances fail for want of open void. Center at y = 100. Its mouth faces the main
  island. No endermen spawn inside. `/locate structure fmscraft:sphere_of_sloth` (in the End).
- **Shape** (`SlothSphereShape`, pure logic, unit tested): a 96-block hollow sphere with a 4-thick shell. Inside, a
  48-block obsidian sphere (the **core**) hangs from four bundles of chains. A **stair** winds three times round the
  core in the gap between them, from the bottom bell (y -23 from the center) to a landing on top of the core (y +25).
  It climbs in half-block steps (no jumping anywhere, checked by a test that walks it), levelling out into flat
  landings at the bells and where it passes the **mouth**: a round opening in the shell, level with the middle of the
  stair, with a bridge in to it and a soul lantern either side so it can be spotted from afar.
- **Look:** the shell is shaded like a lit planet: grey deepslate on the side facing the main island, dithered down
  through blackstone and coal to black concrete on the night side, with purple-sheened obsidian creeping up from
  underneath, which weeps crying obsidian. Inside, the shell is old deepslate speckled with crying obsidian like a
  starry sky; the stair is cracked deepslate with broken rails, cobwebs and old unlit candles, and soul lanterns hang
  under every turn. Crying obsidian tears run down the outside of the core.
- **The challenge: Drowsiness** (`Drowsiness`, `SlothSpheres`). Everyone inside gets a "Drowsiness" boss bar that fills
  in 3 minutes, or **1 minute standing still** (idling is what the sphere wants). At 40% Slowness I, at 70% Slowness II
  and the bar turns purple. At 90% Darkness pulses. At 100% you **nod off** where you stand: 5 seconds blind and
  unable to move (a snore, "You nodded off..."), then wake at 60%. The core is calm: drowsiness drains there. The
  whole sphere also gives Mining Fatigue III, like an ocean monument, so nobody digs to the core.
- **The bells:** one at the bottom of the stair, two in bays along it, one on the landing. Ringing one (by hand,
  arrow or redstone) wakes everyone within 24 blocks (drowsiness back to 0) and lights the four grey candles in front
  of it, which burn down one at a time over 160 seconds. **Ring all four while they're all still burning and the
  crying obsidian seal on top of the core crumbles open.** Groups can split up; one player can just make it by
  hurrying from the bottom up. "Bells ringing: n/4" shows for everyone inside.
- **The core:** drop down the shaft into a pool. A quiet obsidian room with candles burning: the Sloth **Sin Portal
  Frame** before a crying-obsidian-cornered frame on one side (use the Sloth Key on it), and two rows of grey beds
  either side of the pool (they explode if anyone tries to sleep, this being the End). A vanilla **end gateway**
  (bedrock and all) on the other side takes you back out to the mouth.
- **The seal closes again** once nobody is within 64 blocks of the center, and the bells go quiet, so every visit
  is the same challenge. Nothing is saved: after a restart the bells start silent and an open seal closes the next
  time someone comes by.
- **Loot:** a chest by each of the three lower bells (`chests/sphere_of_sloth`: ender pearls, rockets, phantom
  membranes, slow falling potions, golden carrots, diamonds, a clock, Mellohi..., plus a 5% Beginner and 5% Advanced
  tome) and two by the lock (`chests/sphere_of_sloth_core`: diamonds, level-30 books, a totem, a god apple, netherite
  scrap, a rare elytra, and the same 10% Advanced / 2% Master tome chance as the other final rooms).
- Tuning knobs: `Drowsiness` (rates and thresholds), `SlothSpheres.BELL_TICKS`.

### Lust: Lust's Garden (built)

`structure/lust/` + `entity/LustStatueEntity`. Preview from above: `docs/lusts-garden.png`.
- **Where:** the twilight's plains only (`tags/worldgen/biome/has_structure/lusts_garden.json`), on the surface. Very
  rare: one chance per 110x110 chunks, about 1760 blocks apart (`worldgen/structure_set/lusts_garden.json`), kept
  12 chunks from Greed Vaults and 10 from the twilight mod's temples (read from the registry by id, so it can't bury
  a temple portal). It needs the ground level (within 12 blocks) and in its biome all the way out to the hedge; the
  twilight is flat enough that it takes ~95% of its chances. Generates last, clearing whatever stood there. No mobs
  spawn in it. `/locate structure fmscraft:lusts_garden` (in the twilight).
- **Shape** (`LustGardenPlan`, pure logic, unit tested; built by `LustGardenPiece`): about 200 blocks across and never
  round: the hedge's radius wanders between ~84 and ~107 blocks and its thickness between 4 and 8. A 10-block skirt
  outside eases the ground back into the land.
- **The hedge:** azalea leaves with drifts of flowering azalea, at least 12 high through its middle (up to 18), with a
  lumpy top, rounded shoulders, and leaves bulging out over the lawn and the skirt. All persistent, so it never decays.
  One **gate**: a 5-wide tunnel through it, roofed with leaves, a lantern hanging halfway, tuff brick pillars with
  lanterns at its mouth. The paved avenue comes in through it.
- **Inside:** a tuff avenue (kerbed in polished tuff, lit by lantern posts) winds to the fountain, crossing two ring
  paths joined by spokes; four footpaths run off the outer ring into dead-end nooks by the hedge. Gravel and dirt
  paths edged with low azalea hedges, lawns with drifts of flowers in four colour schemes (pink, white, purple, red:
  tulips, peonies, lilacs, rose bushes, lilies of the valley, the twilight's roses...), pink petals, moss, grass and
  ferns, ~36 cherry trees, ~12 azalea trees, clipped topiary, up to 5 ponds with lily pads, sugar cane and mossy
  shores, 2 cherry-roofed pavilions and 2 gardener's sheds, arbors of leaves over the paths, benches, broken old walls.
- **The fountain:** a round basin with a ledge and a low rim, a tuff pillar with four spouts and a flowering azalea on
  top, in a paved plaza 16 blocks out. Round it, on the four compass points, **four empty chiseled tuff plinths**, two
  unlit pink candles at the foot of each.
- **The fog:** inside the hedge the twilight's grey fog turns a faint dusty rose (`GardenFog`, told by the server with
  `GardenFogPayload`). It doesn't get any closer: the statues come out of the same 11 blocks of fog as everywhere.
- **The rules inside:** Slowness II, Mining Fatigue X, Block Barrier, and **Item Barrier** (a new effect: nothing but
  food can be used, so no bows, pearls, potions, rockets or tomes; using blocks like chests, levers and the lock still
  works; `ItemStackMixin`). All of it lifts when the garden is at peace.
- **The Statues of Lust** (one per plinth, ~25-30 per garden: beside the paths facing them, two facing each other
  across the avenue inside the gate, one at the end of every nook). A weeping woman carved in tuff after the reference
  statue: veiled, long hair down her back, a crown of roses, one hand at her breast, the other gripping her skirt,
  mouth open in a scream, eyes shut (two dark 2x1 slits). 3 blocks tall but as slim as a player, on a chiseled tuff
  brick plinth. Raised when someone comes within
  24 blocks of the garden (never where anyone within 16 blocks could see it appear), removed when everyone is 40 away.
  - **Asleep** it never moves. **Look it in the face** (its head within 10 degrees of the middle of your view, standing
    in front of it, within sight in the fog, nothing in the way) and its eyes open, with the faintest grit of stone.
    That's its only animation: the slits turn mid grey for 3 ticks, then pale grey (three textures).
  - **Awake** it comes for the nearest survival player in the garden, silently, at ~6 blocks a second (faster than you
    can sprint even without Slowness), but only while **nobody at all** can see it: any player (creative too) with any part
    of it within 80 degrees of where they're looking, within 11 blocks in the twilight's fog (48 elsewhere), nothing
    between. The moment anyone does, it's stone mid-step: no movement, no turning. Blindness means you can't hold it.
    It never leaves the garden and won't wade into ponds.
  - **Never seen moving:** the client only lets a statue's drawn position catch up while that drawn statue is off
    screen, behind something or lost in the fog (`LustStatueRenderer`), so even lag can't show it gliding.
  - Reaching you unwatched, it **embraces** you: 24 damage and you're thrown back, then 2 seconds before it can again.
    It can't be hurt, pushed, burned or blown up.
- **The challenge:** lure four awake statues home. An awake statue that comes within 3 blocks of an empty fountain
  plinth while nobody is looking at it steps up onto it, turns to the fountain and closes its eyes for good; the two
  candles at its foot light and everyone hears "A statue has found its place... (n/4)". It takes the group working
  together: someone wakes a statue and walks away from it with their back turned to draw it in, the others keep every
  other awake statue in view, and nobody may look at the one being drawn until it's home.
  With all four home the garden is **at peace**: the challenge fanfare, the effects lift, every statue closes its eyes,
  the spouts stop, the basin drains, and the basin floor over the stair (a 3x3 of tuff bricks with a chiseled block in
  the middle, the one hint) sinks away a row at a time.
- **The crypt:** a stair under the basin goes down 9 blocks into a vaulted tuff crypt: pillars, roots hanging through
  the ceiling, hanging lanterns, candles along the walls, a pink runner up the aisle to the Lust **Sin Portal Frame**
  (facing the stair) with its tuff frame 4 blocks behind. Anyone in the crypt while the way up is shut (back from the
  fight after a reset) gets it opened, so nobody is ever shut in.
- **Loot:** barrels in the pavilions, sheds and crypt (`chests/lusts_garden_barrel`: bone meal, seeds, petals, berries,
  bread, candles, emeralds, gold nuggets, the odd shears or name tag..., and a 2% tome, Beginner or Advanced) and two
  chests by the lock (`chests/lusts_garden_crypt`: diamonds, level-30 books, golden apples, amethyst, cherry saplings,
  netherite scrap, a totem, *Creator (Music Box)*, a god apple, and the same 10% Advanced / 2% Master tome chance as
  the other final rooms).
- **Resetting:** once nobody is within 40 blocks (and no Lust fight is on), the statues go, the basin floor closes, the
  basin fills, the spouts run and the candles go out. Statues are never saved: they're raised fresh on every visit.
  Whether it's at peace is read back from the world (is the basin floor there?), so a restart changes nothing.
- Textures: `tools/textures/lust_textures.py` (the statue in three eye states, and the Item Barrier icon).
- Tuning knobs: `LustStatueEntity` (`SPEED`, `DAMAGE`, `SEEN_IN_FOG`, `VIEW_COS`, `FACE_COS`), `LustGardens`
  (`CAPTURE`, margins, effect levels), `LustGardenPlan` (sizes), `LustGardenStructure.MAX_UNEVENNESS`.

### Wrath: the Den of Wrath (built)

`structure/wrath/` + `entity/MotherWardenEntity`. Preview: `docs/den-of-wrath.png` (top: from above with the dome
off; red = her, orange = chests, yellow = pressure plates, white = tripwires, cyan = shriekers, purple = the lock;
bottom: cut through the middle from the gate (right) to the portal room (left)).
- **Where:** the Nether only, buried with the hall's floor at y = 22 (under the lava sea). It needs to sit mostly in
  solid ground (60% of the points sampled round its shell), and its entrance tunnel has to reach an open cave: for each
  of the four ways it can face it looks out from the gate, takes the nearest cave (4 to 40 blocks past the wall), and
  the tunnel runs exactly that far, so it always opens out of a cave wall. Kept 8 chunks from fortresses and bastions.
  One chance per 67x67 chunks (`worldgen/structure_set/den_of_wrath.json`), and about one chance in seven finds room,
  so roughly 2500 to 3000 blocks apart (20,000 to 24,000 in the overworld), as rare as the Hall of Gluttony. Generates last; no mobs spawn in it. `/locate structure fmscraft:den_of_wrath` (in the
  Nether).
- **Shape** (`WrathDenShape`, pure logic, unit tested; built by `WrathDenPiece`): one round hall 88 blocks across,
  straight walls 40 high and a dome up to 66, in a 4-thick shell (netherrack outside so it reads as rock). You come in
  high on the east wall, through a gate of reinforced deepslate like the ancient cities' portal, onto a balcony that
  looks out over the whole hall. A ledge winds down the wall once all the way round, in half-block steps (no jumping
  anywhere, checked by a test that walks it), to the floor. At the foot of the west wall a doorway **one block wide and
  two high** (far too small for her) leads through a 14-block tunnel to the portal room.
- **Look:** an ancient city's hall gone feral in the Nether. Blackstone and basalt walls with sculk creeping up them,
  a floor nearly all sculk, soul soil and old cracked tiles, crossed by glowing cracks of magma; a dome blotched with
  crimson growth, weeping vines and the odd shroomlight; rock spires and stalactites; the ribcage of a Nether fossil
  arching over the floor; soul lanterns on long chains. The ledge and gate are old deepslate, its rail mostly broken
  away, lit by soul torches. Her **nest** in the middle is a ring of sculk and bone with four catalysts and four gaps,
  six shriekers round it (they can't summon wardens; two stand right in front of gaps) and three soul campfires.
- **The Mother Warden** (`MotherWardenEntity`): a real vanilla warden (same brain, roar, sniffing, darkness, sonic
  boom), six times the size (scale attribute: 5.4 x 17.4 blocks), 1000 health, 45 attack, 10 armor, deeper voice. Her
  textures are the warden's with every blue turned blood red (`tools/textures/wrath_textures.py` recolors the vanilla
  ones out of the Minecraft jar), and a faint red haze always rises off her. Put in her nest, asleep, when someone comes
  within 24 blocks of the den; taken away when everyone is 64 away. Never saved.
  - **Asleep** she doesn't think at all (no AI), slumps over to one side with lavender Z's drifting up off her, exactly
    like the magic mod's sleep darts, and snores every 6 seconds, very deep and loud (`entity.mother_warden.snore`: the
    fox's sleeping breaths slowed to about a third of their pitch, below what the game itself can pitch a sound, by
    `tools/sounds/wrath_sounds.py` with ffmpeg; heard across the hall, with a sniffer's breath under it). Her
    heartbeat goes on as any warden's does.
  - **Noise stirs her.** Anything a warden could hear, anywhere in the hall, by the vanilla sculk sensor rules:
    sneaking muffles footsteps and landings, wool muffles everything, but chests, pressure plates, tripwires,
    shriekers, eating, a fall or a hurt all count. Creative and spectating players don't, nor do mobs that wander in,
    nor noise in the tunnels or the portal room. A vibration flies from the noise to her head, her tendrils twitch,
    she growls, and everyone in the hall reads "The Mother Warden stirs... (n/3)". After each stir she lies listening
    for 3 seconds, and anything in that time is the same disturbance (open a chest and close it quickly: one stir).
    She forgets one stir per 2 minutes of quiet (`Stirring`).
  - **The third stir wakes her**, and so does anything that hurts her. She roars and comes for whoever woke her (or
    the nearest player in the hall), with a red boss bar for everyone in the hall (and only while she's awake). She
    fights as a warden does: whoever she can reach she mauls; whoever she can't (up on the ledge or the balcony) gets
    her sonic boom, which reaches 2.5 times as far as a warden's (37 blocks out, 50 up) and hits 1.5 times as hard. Her
    rage doesn't cool while anyone is left in the hall, and she never digs away into the ground. She only hunts in her hall: anyone
    who gets out of it (into the tunnels, the doorway, the portal room) is forgotten. Once nobody is left in the hall
    for 15 seconds she walks back to her nest and nods off again, every stir forgotten ("The Mother Warden settles
    back into her nest...").
- **Traps** (things to make noise by accident): a chest in an alcove off three landings of the ledge, two of them with
  a polished blackstone pressure plate on the polished blackstone right in front; tripwires across three landings
  (strung between a post at the ledge's edge and the wall; you can squeeze past by the hooks, or hop it sneaking);
  six pressure plates hidden in the old paving before the doorway, one right in the way; one under the fossil's ribs
  by its chest; a chest in her nest beside her; shriekers round the nest; magma cracks that only hurt if you don't
  sneak.
- **The hall's rules:** anyone in the hall itself (alcoves and balcony too, not the tunnels or the portal room) has
  **Block Barrier** (no placing or breaking blocks, enforced like adventure mode on both sides) and **Item Barrier**
  (nothing but food can be used, so no pearls, rockets or bows across the hall). No Mining Fatigue: it slows your
  swing (10% a level), and anyone who wakes her may have to fight.
- **The portal room:** quiet polished blackstone with red nether brick, red candles burning in the corners, a red
  runner from the doorway to the Wrath **Sin Portal Frame** (facing the doorway), and its frame 4 blocks behind,
  reinforced deepslate at the corners.
- **Loot:** the hall's chests (`chests/den_of_wrath`: echo shards, sculk, bones, gold, quartz, diamonds, golden apples,
  Swift Sneak books, disc fragment 5..., plus a 5% Beginner and 5% Advanced tome), her hoard in the nest
  (`chests/den_of_wrath_nest`: echo shards, diamonds, level-30 and Swift Sneak books, a god apple, netherite scrap,
  Ward and Silence trims, Otherside, and a 10% Advanced tome), and two by the lock (`chests/den_of_wrath_sanctum`, the
  same 10% Advanced / 2% Master tome chance as the other final rooms). Killing her (if anyone manages it) drops
  sculk catalysts and echo shards; she stays dead until everyone has left. Gone any other way, she's back in her nest,
  asleep.
- Tuning knobs: `Stirring` (`WAKE_AT`, `LISTEN_TICKS`, `FORGET_TICKS`), `WrathDens` (`CALM_TICKS`, margins),
  `MotherWardenEntity` (attributes, `SCALE`, `BOOM_REACH`, `BOOM_DAMAGE`, `SNORE_TICKS`), `WrathDenStructure` (`FLOOR_Y`, `MIN_SOLID`).

### Gluttony: the Hall of Gluttony (built)

`structure/gluttony/` + `entity/GorgedBruteEntity`. Preview: `docs/hall-of-gluttony.png` (left: the front; right: the side,
and under it the floor plan, the bridge on the right, the chapel on the left).
- **Where:** the Nether only, standing in a big lava lake: its floor is at y = 34, three blocks over the lava sea.
  The middle and two rings round it (20 and 40 blocks out) have to be open lava (one miss allowed on the inner ring,
  four on the outer), then at least 75% of the points sampled under its terrace (the rest can be shore), with open air
  through the hall up to the tops of its walls (85%) and at its ridge (50%); its spires may reach up into the cavern's
  roof, as a fortress runs into the rock. Then its bridge has to reach land: for each of the four ways it can face it
  looks out from the front of the terrace for the nearest shore (ground between the lava's surface and the bridge's
  deck, air over it) within 80 blocks, and turns to the closest. Kept 8 chunks from fortresses and bastions. One
  chance per 24x24 chunks (`worldgen/structure_set/hall_of_gluttony.json`), and only about one in 50 of those finds
  a lake big enough, so roughly 2500 to 3000 blocks apart (20,000 to 24,000 in the overworld), as rare as the Den of Wrath. Generates last; no monsters spawn in
  it. `/locate structure fmscraft:hall_of_gluttony` (in the Nether).
- **Shape** (`GluttonyHallShape`, pure logic, unit tested; built by `GluttonyHallPiece`): a cathedral built the way the
  piglins build bastions, on a paved terrace (105 x 53) with a crenellated parapet, its foundations going down into the
  lava. A 5-wide bridge on arches runs from the terrace to the shore, its deck cut on into the land at the end.
  - The nave: 64 long, 17 wide, a pointed vault 33 high with a rib across it at every pier and a ridge rib along the
    top (shroomlights set in where they meet). Arcades of polished basalt piers with gilded capitals and pointed arches
    open onto the aisles either side. Lancet windows in the clerestory, stained glass fading from red at the bottom
    through orange to yellow, like the lava's glow.
  - The aisles: lower vaulted passages with windows in the outer walls, lean-to roofs, and a buttress at every pier
    with a pinnacle (gold-tipped) and a flying buttress up over the aisle roof to the clerestory.
  - The front: two towers, 11 square, with belfries (a bell in each, open arches on every face) and nether brick
    spires to 61 blocks, gold finials and corner pinnacles. Between them the great door, a pointed arch with gilded
    archivolts, and a rose window over it (gold heart, orange ring, red and blue petals between polished blackstone
    tracery). A gable with a gold finial over that, and a slender spire over the middle of the roof.
  - Steep roofs of nether brick stairs with a red nether brick ridge. Walls of polished blackstone bricks, some
    cracked, the odd one gilded.
  - Inside, the banquet: a crimson table the length of the nave on a red nether brick runner, benches either side,
    laid with cakes, candle cakes, melons, pumpkins, hay, pots and lanterns; lanterns hanging on long chains over it.
    The aisles are the kitchens: in each bay a pantry barrel, a smoker, a cooking fire and hay against the outer wall.
  - At the west end a dais, the Gorged Brute's, his hoard heaped behind him (gold blocks, raw gold, two chests), in
    front of the **gate**: 3 wide and 4 high, gilded blackstone, in a frame with a gold keystone.
  - Behind the gate a passage leads into the chapel: quiet polished blackstone, lit by soul fire (soul campfires and
    hanging soul lanterns, which idle piglins keep away from), a yellow runner up to the Gluttony **Sin Portal Frame**
    (facing the passage) and its frame 4 blocks behind with gold corners. Two chests by the lock.
- **The feast:** when someone comes within 32 blocks, 10 piglins are put at the table and 4 piglin brutes about the
  hall: real vanilla ones, grown, armed as a bastion's are (crossbows, golden swords, golden axes), and immune to
  zombification. They behave exactly as they do anywhere: piglins leave you alone if you wear gold, brutes never do,
  and opening a pantry barrel angers them. While anyone is in the hall, one more comes in every 10 seconds, out of
  sight down the aisles (never within 12 blocks of anyone), up to as many as the feast began with. They're tagged
  `fmscraft.gluttony_feast` and cleared away with the hall.
- **The hall's rules:** anyone in the hall itself (the nave, the aisles and the dais, not the doorway or the chapel)
  has **Block Barrier**, so nobody walls themselves in or tunnels round the gate. No Mining Fatigue, unlike the other
  structures: it slows your swing (10% a level, so level X leaves you nearly harmless), and this hall is a fight.
  Weapons, shields, bows and potions all work as normal.
- **The Gorged Brute** (`GorgedBruteEntity`): a piglin brute twice the size (scale attribute: 1.2 x 3.9), the vanilla
  brute's model and texture. He stands on his dais in front of the gate and never moves; he can't be hurt, pushed,
  burned or blown up. He watches whoever comes near and grunts now and then, deep.
  - **Feed him:** use any food on him (anything with a food component: bread, stew, rotten flesh, golden apples...). He
    takes one mouthful a second, whoever is feeding him (try sooner and he grunts and the food stays in your hand),
    holds it up, chews with crumbs flying, and burps every tenth. A bowl or bottle comes back to you. He swells as he
    eats, up to 2.6 times a brute's size.
  - A yellow boss bar ("Gorged Brute") fills as he eats, shown to everyone in the hall, and each mouthful tells the hall
    "The Gorged Brute gulps it down... (n/30)".
  - **At 30 he bursts:** a bang that harms nothing, a cloud of crumbs, a few gold ingots and porkchops spilled, the
    challenge-complete fanfare for everyone near, "The Gorged Brute bursts! The way to the portal lies open.", the
    rules lift, no more piglins come, and the gate sinks into the floor a row at a time. The feast that's left keeps
    fighting.
- **The chapel is safe:** nothing can target a player in the chapel or the passage to it (`LivingEntity.canTarget`,
  LivingEntityMixin: brains and goals alike forget them), and any of the feast that wanders in is turned round and sent
  back into the hall. Anyone in there while the gate is shut (back from the fight after a reset) gets it opened, so
  nobody is ever shut in.
- **Loot:** pantry barrels (`chests/hall_of_gluttony_pantry`: porkchops, bread, potatoes, beef, pie, cookies, stews,
  honey, berries, golden carrots, rotten flesh, gold nuggets..., and a 2% Beginner tome), enough to feed him if you
  can get at them; his hoard on the dais (`chests/hall_of_gluttony`: gold, porkchops, golden carrots and apples, cake,
  diamonds, enchanted golden axes and crossbows, books, the Snout trim, plus a 5% Beginner and 5% Advanced tome); and
  two by the lock (`chests/hall_of_gluttony_sanctum`, the same 10% Advanced / 2% Master tome chance as the other final
  rooms).
- **Resetting:** once nobody is within 64 blocks (and no Gluttony fight is on), the feast is cleared away, the gate
  closes and he's back, hungry, next time. He's never saved; the gate is read back from the world, so a restart
  changes nothing (what he's eaten is forgotten).
- Tuning knobs: `GorgedBruteEntity` (`FOOD_TO_BURST`, `FEED_COOLDOWN`, `SCALE`, `FULL_SCALE`), `GluttonyHalls`
  (`PIGLINS`, `BRUTES`, `REINFORCE_TICKS`, `REINFORCE_CLEARANCE`, margins), `GluttonyHallStructure`
  (`FLOOR_Y`, `MIN_LAVA`, `MIN_OPEN`), `GluttonyHallShape` (sizes, `MAX_BRIDGE`).

## Compatibility with the other two mods

- **Ragnars Twilight Dimension** (`ragnarstwilightdimension`): Greed and Lust portals generate in `ragnarstwilightdimension:twilight`. Referenced by id only.
- **Ragnars Magic Mod** (`ragnarsmagicmod`): its summons count as pets for Sloth via the entity tag `data/fmscraft/tags/entity_type/sloth_summons.json` (currently `steve`, `knight`, `clone`; entries are `required: false` so nothing breaks if the mod is missing).
- Neither mod is a hard dependency (`recommends` in `fabric.mod.json`), but the server is meant to run all three.

## Implementation roadmap

**Phase 1: Fragments and keys (short)** - done
1. Settle the open questions above.
2. Item textures for fragments, keys and souls.
3. The six remaining fragment triggers, one sin at a time, each tested in-game.

**Phase 2: The boss fight framework (built once, used by all 7)** - done, except the per-structure lock (step 4), which is designed with each structure
4. Portal block: right-click with the matching key to consume it and open the portal once.
5. Boss arena dimensions, one per sin (a void dimension with a saved arena structure).
6. Fight manager: who's in the fight, any death kicks everyone out and closes the portal, dead players' loot goes
   into a chest near the overworld portal, a win drops XP and the soul and marks the sin defeated, arena resets.
7. Music system: play, stop and switch a boss's song from the server, plus the beat clock (already in `SinBossEntity`).
8. A throwaway **test boss** that uses all of the above, so the framework is proven before real bosses.

**Phase 3: Boss fights (the long part)**, one boss at a time. For each one:
9. Design it together: song(s), BPM, arena, phases, attacks on the beat.
10. Model and textures (blocky, vanilla style), animations.
11. Attacks and AI, then tuning in playtests with friends.

Suggested order: start with the simplest idea to learn the workflow, save the most ambitious for last.

**Phase 4: Structures**
12. Seven portal structures plus worldgen in their dimensions (can be built in-game in parallel with phase 3).
13. True Ending structure, soul pedestals, credits and music, "True Ending" advancement.

**Phase 5: Release**
14. Full playthrough on a test server with all three mods, balancing, then the real server.
