"""
Builds data/fmscraft/structure/arena/sloth.nbt, RICKY's arena: the heart of the "Abandoned City" map (a 1.18.1 world
save from Planet Minecraft), read straight out of the world's region files.

The city is a flooded ruin: a flat plain under one block of water (gravel under it), overgrown streets of broken walls,
six moss-streaked skyscrapers 190 blocks tall, and in the middle a green hill with a glasshouse and the ruins of a
village. The fight is in the clearing on the hill's south-west slope (world x 572, z 640 in the save): flat grass at
y 59 with a dirt path through it, the flooded plaza and three of the towers to the west (where the sun sets), the hill
and its big building to the north, ruins and trees to the south and east.

This:
- cuts the box x 340..720, z 400..900, y 58..255 out of the save (all six towers and the hill), moving it so the
  clearing's middle comes out at world x/z 0 (SlothArena.java: CORNER = (-232, 58, -240)); heights stay as they are,
- drops the villagers (the save's entities aren't read), the command block (and its gold block), the redstone and
  observers of a chimney, and the barriers under the hill building's scaffolding (stone in their place),
- renames what 1.21.1 calls differently (grass is short_grass), and makes every leaf persistent,
- leaves every container empty (no loot to farm fight after fight) and the beehives without bees,
- hollows the towers and the hill: a block with full opaque blocks on all six sides can't be seen from anywhere and
  is left out (never one that could fall, or holds one up), which takes the towers from solid stone to shells,
- leaves out the layers under the water (y 56 stone, 57 gravel) and the endless flooded plain round the box: SlothArena
  builds those in code, out to the fog.

Usage:  python tools/structures/sloth_arena.py [path to the Abandoned_City world folder]
Needs numpy.
"""
import gzip
import io
import os
import struct
import sys
import zlib
from collections import Counter
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/fmscraft/structure/arena/sloth.nbt"
DEFAULT_WORLD = Path.home() / "for boss fights/Ricky/Abandoned_City/Abandoned_City"
DATA_VERSION = 3955  # 1.21.1

# The box cut out of the save, and where the fight's middle is in it
X0, X1, Z0, Z1, Y0, Y1 = 340, 721, 400, 901, 58, 256
CENTER = (572, 640)
CORNER = (X0 - CENTER[0], Y0, Z0 - CENTER[1])

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}
DROP = {"minecraft:command_block": "minecraft:gravel", "minecraft:barrier": "minecraft:stone",
        "minecraft:observer": "minecraft:stone_bricks", "minecraft:redstone_block": "minecraft:stone",
        "minecraft:scaffolding": None}
RENAME = {"minecraft:grass": "minecraft:short_grass"}
# Full, opaque cubes: a block buried in these on every side is never seen
OPAQUE = {"stone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "cobblestone", "mossy_cobblestone",
          "andesite", "polished_andesite", "diorite", "granite", "tuff", "dirt", "coarse_dirt", "grass_block",
          "moss_block", "clay", "coal_ore", "iron_ore", "copper_ore", "lapis_ore", "sandstone", "oak_planks",
          "spruce_planks", "gray_concrete", "coal_block", "gray_wool", "oak_log", "oak_wood", "stripped_oak_wood",
          "gravel", "sand", "polished_basalt", "magma_block", "gold_block"}
FALLS = {"gravel", "sand"}

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


# ---- Reading the save ----

def parse(buf):
    f = io.BytesIO(buf)
    r = f.read

    def string():
        return r(struct.unpack(">H", r(2))[0]).decode("utf-8", "replace")

    def payload(t):
        if t == BYTE: return struct.unpack(">b", r(1))[0]
        if t == SHORT: return struct.unpack(">h", r(2))[0]
        if t == INT: return struct.unpack(">i", r(4))[0]
        if t == LONG: return struct.unpack(">q", r(8))[0]
        if t == FLOAT: return struct.unpack(">f", r(4))[0]
        if t == DOUBLE: return struct.unpack(">d", r(8))[0]
        if t == BYTES: return r(struct.unpack(">i", r(4))[0])
        if t == STRING: return string()
        if t == LIST:
            et, n = r(1)[0], struct.unpack(">i", r(4))[0]
            return [payload(et) for _ in range(n)]
        if t == COMPOUND:
            out = {}
            while (tt := r(1)[0]) != END:
                k = string()
                out[k] = payload(tt)
            return out
        if t == INTS:
            n = struct.unpack(">i", r(4))[0]
            return np.frombuffer(r(4 * n), ">i4")
        if t == LONGS:
            n = struct.unpack(">i", r(4))[0]
            return np.frombuffer(r(8 * n), ">i8")
        raise ValueError(t)

    t = r(1)[0]
    string()
    return payload(t)


def unpack(longs, bits):
    """A 1.16+ packed block state array: entries never straddle two longs."""
    per = 64 // bits
    a = longs.astype(np.uint64)
    out = np.zeros(len(a) * per, np.int64)
    mask = np.uint64((1 << bits) - 1)
    for j in range(per):
        out[j::per] = ((a >> np.uint64(j * bits)) & mask).astype(np.int64)
    return out[:4096]


def read_box(world):
    """The box as (y, z, x) indices into a palette of (name, props)."""
    vox = np.zeros((Y1 - Y0, Z1 - Z0, X1 - X0), np.uint16)
    palette, index = [("minecraft:air", ())], {("minecraft:air", ()): 0}
    regions = {}
    for cx in range(X0 >> 4, ((X1 - 1) >> 4) + 1):
        for cz in range(Z0 >> 4, ((Z1 - 1) >> 4) + 1):
            key = (cx >> 5, cz >> 5)
            if key not in regions:
                p = world / "region" / f"r.{key[0]}.{key[1]}.mca"
                regions[key] = p.read_bytes() if p.exists() else b""
            data = regions[key]
            if len(data) < 8192:
                continue
            i = (cx & 31) + (cz & 31) * 32
            off = int.from_bytes(data[i * 4:i * 4 + 3], "big")
            if off == 0:
                continue
            p = off * 4096
            length = struct.unpack(">i", data[p:p + 4])[0]
            chunk = parse(zlib.decompress(data[p + 5:p + 4 + length]))
            xs = slice(max(cx * 16, X0) - cx * 16, min(cx * 16 + 16, X1) - cx * 16)
            zs = slice(max(cz * 16, Z0) - cz * 16, min(cz * 16 + 16, Z1) - cz * 16)
            for section in chunk.get("sections", []):
                sy = section["Y"] * 16
                states = section.get("block_states")
                if sy + 16 <= Y0 or sy >= Y1 or not states:
                    continue
                ids = []
                for entry in states["palette"]:
                    k = (entry["Name"], tuple(sorted(entry.get("Properties", {}).items())))
                    if k not in index:
                        index[k] = len(palette)
                        palette.append(k)
                    ids.append(index[k])
                if "data" in states:
                    bits = max(4, (len(ids) - 1).bit_length())
                    cube = np.array(ids, np.uint16)[unpack(states["data"], bits)].reshape(16, 16, 16)
                else:
                    cube = np.full((16, 16, 16), ids[0], np.uint16)
                for ly in range(16):
                    y = sy + ly
                    if Y0 <= y < Y1:
                        vox[y - Y0, max(cz * 16, Z0) - Z0:min(cz * 16 + 16, Z1) - Z0,
                            max(cx * 16, X0) - X0:min(cx * 16 + 16, X1) - X0] = cube[ly, zs, xs]
    return vox, palette


# ---- Writing the template ----

def write_structure(path, size, palette, positions, states):
    out = io.BytesIO()
    w = out.write

    def s(v):
        b = v.encode("utf-8")
        w(struct.pack(">H", len(b)) + b)

    def named(t, name):
        w(bytes([t]))
        s(name)

    named(COMPOUND, "")
    named(INT, "DataVersion"); w(struct.pack(">i", DATA_VERSION))
    named(LIST, "size"); w(bytes([INT]) + struct.pack(">i", 3) + struct.pack(">3i", *size))
    named(LIST, "entities"); w(bytes([COMPOUND]) + struct.pack(">i", 0))
    named(LIST, "palette"); w(bytes([COMPOUND]) + struct.pack(">i", len(palette)))
    for name, props in palette:
        named(STRING, "Name"); s(name)
        if props:
            named(COMPOUND, "Properties")
            for k, v in props:
                named(STRING, k); s(v)
            w(bytes([END]))
        w(bytes([END]))
    named(LIST, "blocks"); w(bytes([COMPOUND]) + struct.pack(">i", len(states)))
    # Every block entry is the same shape: pos (a list of three ints), state, end
    head = bytes([LIST]) + struct.pack(">H", 3) + b"pos" + bytes([INT]) + struct.pack(">i", 3)
    mid = bytes([INT]) + struct.pack(">H", 5) + b"state"
    for (x, y, z), st in zip(positions, states):
        w(head + struct.pack(">3i", x, y, z) + mid + struct.pack(">i", st) + bytes([END]))
    w(bytes([END]))
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb", compresslevel=9) as f:
        f.write(out.getvalue())


def main():
    world = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_WORLD
    if not (world / "region").is_dir():
        sys.exit(f"no world save at {world}")
    vox, palette = read_box(world)

    # Clean the palette: what goes, what's renamed
    names = []
    for name, props in palette:
        if name in DROP:
            name, props = (DROP[name], ()) if DROP[name] else ("minecraft:air", ())
        name = RENAME.get(name, name)
        if name.endswith("_leaves"):
            # Hollowing can leave a tree's log out: leaves that remember it would rot away round the fight
            props = tuple((k, "true" if k == "persistent" else v) for k, v in props)
        names.append((name, props))
    is_air = np.array([n in AIR for n, _ in names])
    short = [n.split(":", 1)[1] for n, _ in names]
    opaque = np.array([s in OPAQUE for s in short])
    falls = np.array([s in FALLS for s in short])

    solid = ~is_air[vox]
    op = opaque[vox]
    # Hidden: opaque on all six sides (the box's own faces count as open, so its edges stay whole)
    hidden = op.copy()
    for axis in range(3):
        for shift in (1, -1):
            n = np.roll(op, shift, axis=axis)
            edge = [slice(None)] * 3
            edge[axis] = slice(0, 1) if shift == 1 else slice(-1, None)
            n[tuple(edge)] = False
            hidden &= n
    # Never leave out something that could fall, or the block holding one up
    fall = falls[vox]
    hidden &= ~fall
    hidden &= ~np.roll(fall, -1, axis=0)
    keep = solid & ~hidden

    ys, zs, xs = np.nonzero(keep)
    used = np.unique(vox[ys, zs, xs])
    remap = {int(u): i for i, u in enumerate(used)}
    out_palette = [names[int(u)] for u in used]
    states = [remap[int(v)] for v in vox[ys, zs, xs]]
    positions = list(zip(xs.tolist(), ys.tolist(), zs.tolist()))
    size = (X1 - X0, Y1 - Y0, Z1 - Z0)
    write_structure(OUT, size, out_palette, positions, states)

    kinds = Counter(out_palette[s][0] for s in states)
    print(f"Wrote {OUT.relative_to(ROOT)}: {size[0]}x{size[1]}x{size[2]}, {len(states)} blocks "
          f"({int(solid.sum()) - len(states)} hidden ones left out), {len(out_palette)} states, "
          f"{OUT.stat().st_size // 1024} KB")
    for k, n in kinds.most_common(12):
        print(f"  {n:8d} {k}")
    print(f"SlothArena.CORNER = {CORNER}")

    # The ground at the fight's middle and round it (SlothArena.FLOOR_Y is the feet height on the clearing)
    def top(x, z):
        col = keep[:, z - Z0, x - X0] & ~np.isin(vox[:, z - Z0, x - X0], [i for i, (n, _) in enumerate(names) if "grass" in n and n != "minecraft:grass_block" or "fern" in n or "flower" in n])
        ys = np.nonzero(col)[0]
        return Y0 + int(ys.max()) if len(ys) else None
    for label, (x, z) in {"middle": CENTER, "players (east)": (CENTER[0] + 16, CENTER[1]), "boss (west)": (CENTER[0] - 14, CENTER[1])}.items():
        print(f"  ground at {label}: world {x - CENTER[0]}, {z - CENTER[1]} -> top block y {top(x, z)}")


if __name__ == "__main__":
    main()
