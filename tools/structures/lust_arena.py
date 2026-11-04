"""
Builds data/fmscraft/structure/arena/lust.nbt, MUGEN's arena, from the Japanese compound it was built as
(tools/structures/lust_arena_source.nbt, "Japanese Cherry Base", saved in 1.21.1).

A walled compound 118 x 120 under cherry trees: the great hall on its west side, a garden of cherry trees, ponds and
bridges on the east round an open stone courtyard (template x 67-107, z 57-68, its stones at template y 12). This:

- keeps the template's size and positions, so the courtyard's middle (template x 84, z 62) lands on world x/z 0 and its
  stones on world y 63 (LustArena.java relies on both: CORNER = (-84, 51, -62)),
- drops the air,
- changes nothing else. The lava under the shrine at the south-west sits sealed under the floor, out of reach.

It prints the numbers LustArena.java uses, so a changed source can be checked. Re-run after replacing the source.

Usage:  python tools/structures/lust_arena.py [source.nbt]
"""
import sys
from collections import Counter
from pathlib import Path

from greed_arena import read_nbt, write_structure

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "lust_arena_source.nbt"
OUT = ROOT / "src/main/resources/data/fmscraft/structure/arena/lust.nbt"
AIR = ("minecraft:air", "minecraft:cave_air", "minecraft:structure_void")

# Where things are in the template (LustArena.java has the same, in world coordinates)
FLOOR = 12           # the courtyard's stones
CENTER = (84, 62)    # the courtyard's middle: he rises here
SPAWN = (100, 62)    # players arrive here, at the courtyard's east end
EXIT = (71, 57)      # the exit frame's bottom-left, on the courtyard's north edge
WALLS = (13, 106, 16, 109)  # inside the compound's walls: x from, x to, z from, z to


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE
    root = read_nbt(source)
    size = root["size"]
    palette = root["palette"]
    keep, names = [], Counter()
    blocks = {}
    used = {}
    out_palette = []
    for b in root["blocks"]:
        entry = palette[b["state"]]
        if entry["Name"] in AIR:
            continue
        key = (entry["Name"], tuple(sorted(dict(entry.get("Properties", {})).items())))
        if key not in used:
            used[key] = len(out_palette)
            out_palette.append((entry["Name"], dict(entry.get("Properties", {}))))
        pos = tuple(b["pos"])
        keep.append((list(pos), used[key]))
        blocks[pos] = entry["Name"]
        names[entry["Name"]] += 1
    keep.sort(key=lambda e: (e[0][1], e[0][2], e[0][0]))
    write_structure(OUT, size, out_palette, keep)
    print(f"Wrote {OUT.relative_to(ROOT)}: {size[0]}x{size[1]}x{size[2]}, {len(keep)} blocks, {len(out_palette)} states")

    def open_over(x, z):
        return all((x, y, z) not in blocks or blocks[(x, y, z)].endswith(("leaves", "petals", "short_grass"))
                   for y in (FLOOR + 1, FLOOR + 2))

    for name, (x, z) in (("middle", CENTER), ("spawn", SPAWN)):
        print(f"  {name}: template {x}, {FLOOR}, {z} is {blocks.get((x, FLOOR, z))}, open over it: {open_over(x, z)}")
    print(f"  exit frame at template {EXIT}: floor {blocks.get((EXIT[0], FLOOR, EXIT[1]))}, "
          f"the curb behind it: {blocks.get((EXIT[0] + 6, FLOOR + 1, EXIT[1] - 1))}")
    x0, x1, z0, z1 = WALLS
    wall = [max((y for y in range(size[1]) if (x, y, z) in blocks), default=-1) for x, z in
            ((x0 - 2, 62), (x1 + 2, 62), (60, z0 - 2), (60, z1 + 2))]
    print(f"  walls round it (west, east, north, south) top out at template y {wall}")
    print(f"  lava: {names['minecraft:lava']} blocks, highest at template y "
          f"{max((p[1] for p, n in blocks.items() if n == 'minecraft:lava'), default=None)} (sealed under the floor)")


if __name__ == "__main__":
    main()
