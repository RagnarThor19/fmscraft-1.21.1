"""
Builds LalliLarge's two arenas (Gluttony) from the builds they were saved as:

- data/fmscraft/structure/arena/gluttony.nbt, the Earth (tools/structures/gluttony_earth_source.nbt,
  "the-earth"): a hollow globe 258 blocks across hanging in space. Phase 1 is fought on its north pole.
- data/fmscraft/structure/arena/gluttony_castle.nbt, the ruined castle (gluttony_castle_source.nbt, "ruined-castle")
  that stands in the distance in phase 2's snowfield, snowed over.

Both were saved by other Minecraft versions; this:

- writes them in the 1.21.1 structure format (data version 3955), keeping each template's size and positions
  (GluttonyArena.java relies on the Earth's: its north pole is the top block of template column 128, 128),
- swaps every block 1.21.1 doesn't know for the closest one it does (SWAPS below),
- drops the air, and the castle's mobs (creepers, skeletons... saved with it),
- snows the castle over: grass turns snowy and a layer of snow lies on every open top it could settle on.

It prints the numbers GluttonyArena.java uses. Re-run after replacing a source.

Usage:  python tools/structures/gluttony_arena.py
"""
from collections import Counter
from pathlib import Path

from greed_arena import read_nbt, write_structure

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "src/main/resources/data/fmscraft/structure/arena"
EARTH = HERE / "gluttony_earth_source.nbt"
CASTLE = HERE / "gluttony_castle_source.nbt"

# Newer block -> (1.21.1 block, properties: None keeps them all, a dict replaces them)
SWAPS = {
    "stripped_pale_oak_wood": ("stripped_birch_wood", None),
    "stripped_pale_oak_log": ("stripped_birch_log", None),
    "pale_moss_block": ("moss_block", {}),
    "pale_moss_carpet": ("moss_carpet", {}),
    # A shelf hangs on a wall: an open trapdoor facing the same way sits flat against it
    "pale_oak_shelf": ("spruce_trapdoor", "shelf"),
}
AIR = ("air", "cave_air", "structure_void")

# Tops snow can settle on in the castle (a full top face)
FULL_TOP = {"stone", "andesite", "tuff", "cobblestone", "mossy_cobblestone", "moss_block", "grass_block", "dirt",
            "cobbled_deepslate", "dead_brain_coral_block", "dead_fire_coral_block", "gray_wool", "glowstone"}
SLAB_TOP = {"andesite_slab", "stone_slab", "smooth_stone_slab", "mossy_cobblestone_slab", "polished_tuff_slab",
            "tuff_brick_slab"}


def convert(entry):
    name = entry["Name"].split(":", 1)[1]
    props = dict(entry.get("Properties", {}))
    if name not in SWAPS:
        return name, props
    new, keep = SWAPS[name]
    if keep is None:
        return new, props
    if keep == "shelf":
        return new, {"facing": props.get("facing", "north"), "half": "bottom", "open": "true",
                     "powered": "false", "waterlogged": "false"}
    return new, dict(keep)


def load(path):
    root = read_nbt(path)
    pal = root["palette"]
    blocks, swapped = {}, Counter()
    for b in root["blocks"]:
        src = pal[b["state"]]
        if src["Name"].split(":", 1)[1] in AIR:
            continue
        name, props = convert(src)
        if name != src["Name"].split(":", 1)[1]:
            swapped[src["Name"]] += 1
        blocks[tuple(b["pos"])] = (name, props)
    return root["size"], blocks, swapped


def save(out, size, blocks):
    palette, index, listed = [], {}, []
    for pos, (name, props) in sorted(blocks.items()):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            palette.append(("minecraft:" + name, props))
        listed.append((list(pos), index[key]))
    write_structure(out, size, palette, listed)
    print(f"Wrote {out.relative_to(ROOT)}: {size[0]}x{size[1]}x{size[2]}, {len(listed)} blocks, {len(palette)} states")


def earth():
    size, blocks, swapped = load(EARTH)
    save(OUT / "gluttony.nbt", size, blocks)
    for k, n in swapped.most_common():
        print(f"  swapped {n:5d} x {k}")
    top = {}
    for (x, y, z) in blocks:
        top[(x, z)] = max(top.get((x, z), -1), y)
    print(f"  north pole: template column 128, 128 tops out at y {top[(128, 128)]}")
    for r in (0, 20, 40, 55, 70):
        print(f"  {r:3d} blocks out from the pole the ground is {top[(128, 128)] - top[(128 + r, 128)]} lower")


def castle():
    size, blocks, swapped = load(CASTLE)
    snow, snowy = 0, 0
    for pos, (name, props) in list(blocks.items()):
        x, y, z = pos
        if name == "grass_block":
            blocks[pos] = (name, {"snowy": "true"})
            snowy += 1
        open_top = name in FULL_TOP or name in SLAB_TOP and props.get("type") in ("top", "double")
        if not open_top or y + 1 >= size[1]:
            continue
        # Only where nothing at all is above it: snow falls from the sky
        if any((x, yy, z) in blocks for yy in range(y + 1, size[1])):
            continue
        blocks[(x, y + 1, z)] = ("snow", {"layers": "1"})
        snow += 1
    save(OUT / "gluttony_castle.nbt", size, blocks)
    for k, n in swapped.most_common():
        print(f"  swapped {n:5d} x {k}")
    print(f"  snowed over: {snow} layers of snow, {snowy} snowy grass; dropped its mobs")


if __name__ == "__main__":
    earth()
    castle()
