"""
Builds data/fmscraft/structure/arena/wrath.nbt, ORNAMIX's colosseum, from the gladiator arena it was built as
(tools/structures/wrath_arena_source.nbt, "gamarudos-gladiator-arena", saved in 1.20.1).

The fight is down in the round sand pit (81 blocks across) in the middle; the stands rise all round it. This:

- writes it in the 1.21.1 structure format (data version 3955), keeping the template's size and positions, so its
  middle (x/z 69) is the middle of the pit and the sand is at template y 1 (WrathArena.java relies on both),
- drops the air,
- shuts the three tunnels that lead out of the pit under the stands with iron bar portcullises (players stay in the
  pit; nothing else in the template changes),
- sets the colosseum on fire: a ring of fires along the top of the pit wall and fires dotted over the stands, each
  on a magma block like the ones already burning on its walls. Fires never spread or go out in an arena
  (FireBlockMixin), so nothing burns down.

It prints the layout numbers WrathArena.java uses. Re-run after replacing the source.

Usage:  python tools/structures/wrath_arena.py [source.nbt]
"""
import math
import random
import sys
from collections import Counter, deque
from pathlib import Path

from greed_arena import read_nbt, write_structure

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "wrath_arena_source.nbt"
OUT = ROOT / "src/main/resources/data/fmscraft/structure/arena/wrath.nbt"

CENTER = 69
FLOOR_Y = 1  # the sand
# The sand reaches 40.6 blocks from the middle; the pit wall stands just outside it
PIT_RADIUS = 40.6
# Fires: one on every n-th block round the top of the pit wall, and this share of the stone tops on the stands
WALL_FIRE_EVERY = 4
STAND_FIRE_CHANCE = 0.09
FIRE_BASE = "minecraft:magma_block"
# Tops fire can sit on (a full top face) and blocks it keeps away from (it couldn't burn them, but it would look wrong)
FULL = {"minecraft:stone", "minecraft:polished_andesite", "minecraft:smooth_sandstone", "minecraft:stone_bricks",
        "minecraft:chiseled_stone_bricks"}
SLABS = {"minecraft:smooth_stone_slab", "minecraft:polished_andesite_slab"}
NEAR_NOT = ("wool", "wood", "fire")


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE
    root = read_nbt(source)
    sx, sy, sz = root["size"]
    palette = root["palette"]

    blocks = {}
    for b in root["blocks"]:
        entry = palette[b["state"]]
        name = entry["Name"]
        if name in ("minecraft:air", "minecraft:cave_air", "minecraft:structure_void"):
            continue
        blocks[tuple(b["pos"])] = (name, dict(entry.get("Properties", {})))

    def solid(p):
        b = blocks.get(p)
        return b is not None and b[0] != "minecraft:fire"

    def dist(x, z):
        return math.hypot(x - CENTER, z - CENTER)

    # ---- Shut the tunnels out of the pit ----
    # Walk the pit at feet height from the middle; anywhere you can walk on past the sand is a way out
    def walkable(x, z):
        return not solid((x, FLOOR_Y + 1, z)) and not solid((x, FLOOR_Y + 2, z))

    def flood():
        seen, todo, ways_out = {(CENTER, CENTER)}, deque([(CENTER, CENTER)]), set()
        while todo:
            x, z = todo.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, z + dz)
                if n in seen or not (0 <= n[0] < sx and 0 <= n[1] < sz) or not walkable(*n):
                    continue
                if dist(*n) > PIT_RADIUS + 1.0:
                    ways_out.add(n)
                    continue
                seen.add(n)
                todo.append(n)
        return seen, ways_out

    pit, ways_out = flood()
    bars = 0
    for x, z in ways_out:
        # Bars from the floor up to the tunnel's ceiling
        y = FLOOR_Y + 1
        while y < sy and not solid((x, y, z)):
            blocks[(x, y, z)] = ("minecraft:iron_bars", {})
            bars += 1
            y += 1
    # Iron bars join up with whatever is next to them (the game also does this when it places the template)
    for (x, y, z), (name, _) in list(blocks.items()):
        if name != "minecraft:iron_bars":
            continue
        props = {"waterlogged": "false"}
        for side, (dx, dz) in {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}.items():
            props[side] = "true" if solid((x + dx, y, z + dz)) else "false"
        blocks[(x, y, z)] = (name, props)
    _, still_out = flood()
    if still_out:
        sys.exit(f"the pit still has a way out at {sorted(still_out)[:5]}")

    # ---- Fire ----
    top = {}
    for (x, y, z), (name, props) in blocks.items():
        if y > top.get((x, z), (-1, None))[0]:
            top[(x, z)] = (y, name, props)

    def full_top(x, z):
        _, name, props = top[(x, z)]
        return name in FULL or name in SLABS and props.get("type") in ("top", "double")

    def clear_around(x, y, z):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (-1, 0, 1, 2):
                    b = blocks.get((x + dx, y + dy, z + dz))
                    if b and any(w in b[0] for w in NEAR_NOT):
                        return False
        return True

    fires = []

    def light(x, z):
        y = top[(x, z)][0]
        if not full_top(x, z) or y + 1 >= sy or not clear_around(x, y + 1, z):
            return False
        blocks[(x, y, z)] = (FIRE_BASE, {})
        blocks[(x, y + 1, z)] = ("minecraft:fire", {"age": "0", "east": "false", "north": "false", "south": "false",
                                                   "up": "false", "west": "false"})
        fires.append((x, y + 1, z))
        return True

    # Round the top of the pit wall: the highest full blocks just outside the sand
    wall = sorted(((math.atan2(z - CENTER, x - CENTER), (x, z)) for (x, z) in top
                   if PIT_RADIUS < dist(x, z) <= PIT_RADIUS + 2 and full_top(x, z)))
    last = None
    for i, (angle, (x, z)) in enumerate(wall):
        if last is not None and abs(angle - last) * (PIT_RADIUS + 2) < WALL_FIRE_EVERY:
            continue
        if light(x, z):
            last = angle
    wall_fires = len(fires)

    rand = random.Random(7)
    for (x, z) in sorted(top):
        if PIT_RADIUS + 4 <= dist(x, z) <= 64 and rand.random() < STAND_FIRE_CHANCE:
            light(x, z)

    # ---- Write ----
    out_palette, index, out_blocks = [], {}, []
    for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        name, props = blocks[pos]
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(out_palette)
            out_palette.append((name, props))
        out_blocks.append((list(pos), index[key]))
    write_structure(OUT, (sx, sy, sz), out_palette, out_blocks)

    print(f"Wrote {OUT.relative_to(ROOT)}: {sx}x{sy}x{sz}, {len(out_blocks)} blocks, {len(out_palette)} states")
    print(f"  {bars} iron bars across {len(ways_out)} gaps out of the pit")
    print(f"  {len(fires)} fires added ({wall_fires} round the pit wall), {Counter(n for n, _ in blocks.values())['minecraft:fire']} in all")
    sand = [(x, z) for (x, y, z), (n, _) in blocks.items() if y == FLOOR_Y and n == "minecraft:smooth_sandstone"]
    print(f"  pit: middle at template x/z {CENTER}, sand top at template y {FLOOR_Y + 1}, "
          f"{len(pit)} walkable cells, sand out to {max(dist(x, z) for x, z in sand):.1f}")
    # Room for the exit portal frame (4 wide, 5 tall) by the north wall
    for z in range(CENTER - 36, CENTER - 32):
        clear = all(not solid((x, y, z)) for x in range(CENTER - 1, CENTER + 3) for y in range(FLOOR_Y + 1, FLOOR_Y + 6))
        print(f"  exit frame at template z {z}: {'clear' if clear else 'blocked'}")


if __name__ == "__main__":
    main()
