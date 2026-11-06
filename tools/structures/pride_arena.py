"""
Builds data/fmscraft/structure/arena/pride.nbt, LEONARD's arena: a round marble plaza floating high over a sea of
clouds, with the classical temple (tools/structures/pride_arena_source.nbt, "ClassicalArchitecture", saved in 1.21.1)
standing on its north side, its south steps opening onto the plaza.

- The plaza: a disc 38 blocks out from the middle (world x/z 0), its floor at world y 63 (you stand at 64): smooth quartz
  with rings of polished diorite every 8 blocks, eight rays of quartz bricks, a gold sun in the middle where he strikes
  down, a rim of chiseled quartz set with gold. Eight short marble columns stand round it at 29 out (some broken off),
  cover from his zaps and bolts. The east is kept open: that's where Munch rises.
- Under it, an island hanging in the sky: marble and calcite thinning to a point of stone, puffs of white wool cloud
  clinging round the rim beneath.
- The temple: kept as it was, but its signs, the oak pressure plates along the roof and the redstone hidden in it are
  gone (quartz where the redstone was), and the one tuff slab in its floor is diorite like the rest.

PrideArena.java uses CORNER = (-40, 40, -66), so world = template + CORNER.

Usage:  python tools/structures/pride_arena.py
"""
import math
import random
from pathlib import Path

from greed_arena import read_nbt, write_structure

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "pride_arena_source.nbt"
OUT = ROOT / "src/main/resources/data/fmscraft/structure/arena/pride.nbt"
AIR = ("minecraft:air", "minecraft:cave_air", "minecraft:structure_void")

CORNER = (-40, 40, -66)
SIZE = (81, 60, 107)
FLOOR = 63          # world y of the plaza's blocks
RADIUS = 38
# The temple's template corner in world coordinates: x -25..25, z -63..-21, standing on the plaza
TEMPLE = (-25, 64, -63)
COLUMN_RADIUS = 29
DROP = {"minecraft:spruce_sign", "minecraft:spruce_wall_sign", "minecraft:oak_pressure_plate"}
SWAP = {"minecraft:redstone_block": ("minecraft:quartz_bricks", {}),
        "minecraft:polished_tuff_slab": ("minecraft:polished_diorite_slab", {"type": "bottom", "waterlogged": "false"})}


def in_island(x, z):
    """The plaza's disc, and the ground under the temple."""
    return x * x + z * z <= (RADIUS + 0.5) ** 2 or (-27 <= x <= 27 and -65 <= z <= -19)


def edge_distance(x, z):
    """Roughly how far in from the island's edge (x, z) is."""
    d = RADIUS + 0.5 - math.sqrt(x * x + z * z)
    if -27 <= x <= 27 and -65 <= z <= -19:
        d = max(d, min(x + 27, 27 - x, z + 65, -19 - z))
    return d


def main():
    rnd = random.Random(7)
    blocks = {}

    def put(x, y, z, name, props=None):
        tx, ty, tz = x - CORNER[0], y - CORNER[1], z - CORNER[2]
        if 0 <= tx < SIZE[0] and 0 <= ty < SIZE[1] and 0 <= tz < SIZE[2]:
            blocks[(tx, ty, tz)] = (name, dict(props or {}))

    # ---- The island ----
    for x in range(-40, 41):
        for z in range(-66, 41):
            if not in_island(x, z):
                continue
            r = math.sqrt(x * x + z * z)
            e = edge_distance(x, z)
            # The plaza floor
            if RADIUS - 1.5 <= r <= RADIUS + 0.5 and not (-27 <= x <= 27 and z <= -19):
                top = "minecraft:gold_block" if (round(math.degrees(math.atan2(z, x))) % 30 == 0) else "minecraft:chiseled_quartz_block"
            elif r > RADIUS + 0.5:
                top = "minecraft:smooth_quartz"
            elif r <= 2.6:
                top = "minecraft:gold_block"
            elif r <= 3.6:
                top = "minecraft:chiseled_quartz_block"
            elif abs((r + 4) % 8 - 4) < 0.55:
                top = "minecraft:polished_diorite"
            else:
                angle = math.degrees(math.atan2(z, x)) % 45
                top = "minecraft:quartz_bricks" if (angle < 2.2 or angle > 42.8) and r > 4 else "minecraft:smooth_quartz"
            put(x, FLOOR, z, top)
            # Underneath, thinning to a point
            depth = int(min(22, 2 + e * 0.75 + rnd.random() * 2))
            for d in range(1, depth + 1):
                y = FLOOR - d
                if d <= 2:
                    name = "minecraft:smooth_quartz"
                elif d < depth - 3:
                    name = rnd.choice(["minecraft:calcite", "minecraft:diorite", "minecraft:calcite", "minecraft:polished_diorite"])
                else:
                    name = rnd.choice(["minecraft:stone", "minecraft:andesite", "minecraft:calcite"])
                put(x, y, z, name)

    # ---- Clouds clinging under the rim ----
    for i in range(46):
        a = rnd.random() * math.tau
        r = RADIUS - 2 + rnd.random() * 5
        cx, cz = math.cos(a) * r, math.sin(a) * r
        if not in_island(round(cx * 0.8), round(cz * 0.8)):
            continue
        cy = FLOOR - 3 - rnd.random() * 6
        sx, sy, sz = 2.5 + rnd.random() * 3, 1.2 + rnd.random() * 1.3, 2.5 + rnd.random() * 3
        for x in range(int(cx - sx) - 1, int(cx + sx) + 2):
            for z in range(int(cz - sz) - 1, int(cz + sz) + 2):
                for y in range(int(cy - sy) - 1, int(cy + sy) + 2):
                    if ((x - cx) / sx) ** 2 + ((y - cy) / sy) ** 2 + ((z - cz) / sz) ** 2 <= 1 and y < FLOOR:
                        key = (x - CORNER[0], y - CORNER[1], z - CORNER[2])
                        if key not in blocks:
                            put(x, y, z, "minecraft:white_wool")

    # ---- Columns round the plaza (none in the east, where Munch rises) ----
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        if math.cos(a) > 0.8:
            continue
        x, z = round(math.cos(a) * COLUMN_RADIUS), round(math.sin(a) * COLUMN_RADIUS)
        if z < -17 and -28 <= x <= 28:
            continue  # the temple is there
        height = [6, 3, 5, 6, 2, 6, 4, 5][k]
        put(x, FLOOR + 1, z, "minecraft:chiseled_quartz_block")
        for y in range(2, height + 1):
            put(x, FLOOR + y, z, "minecraft:quartz_pillar", {"axis": "y"})
        if height >= 5:
            put(x, FLOOR + height + 1, z, "minecraft:chiseled_quartz_block")
            put(x, FLOOR + height + 2, z, "minecraft:smooth_quartz_slab", {"type": "bottom", "waterlogged": "false"})
        else:
            # Broken off: rubble at its foot
            for dx, dz in ((1, 0), (0, 1), (-1, 1)):
                put(x + dx, FLOOR + 1, z + dz, "minecraft:quartz_slab", {"type": "bottom", "waterlogged": "false"})

    # ---- The temple ----
    root = read_nbt(SOURCE)
    palette = root["palette"]
    for b in root["blocks"]:
        entry = palette[b["state"]]
        name = entry["Name"]
        if name in AIR or name in DROP:
            continue
        props = dict(entry.get("Properties", {}))
        if name in SWAP:
            name, props = SWAP[name]
        x, y, z = b["pos"]
        put(TEMPLE[0] + x, TEMPLE[1] + y, TEMPLE[2] + z, name, props)

    out_palette, index, keep = [], {}, []
    for pos, (name, props) in blocks.items():
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(out_palette)
            out_palette.append((name, props))
        keep.append((list(pos), index[key]))
    keep.sort(key=lambda e: (e[0][1], e[0][2], e[0][0]))
    write_structure(OUT, SIZE, out_palette, keep)
    print(f"Wrote {OUT.relative_to(ROOT)}: {SIZE[0]}x{SIZE[1]}x{SIZE[2]}, {len(keep)} blocks, {len(out_palette)} states")
    mid = blocks.get((0 - CORNER[0], FLOOR - CORNER[1], 0 - CORNER[2]))
    print(f"  middle of the plaza: {mid}; temple floor at world y {TEMPLE[1] + 4} (slabs, stand at {TEMPLE[1] + 4.5})")


if __name__ == "__main__":
    main()
