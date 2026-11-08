"""
Builds RAGGIMEL's second arena (data/fmscraft/structure/arena/greed_manor.nbt): the Roswall manor and its gardens,
from the litematic in ~/for boss fights/Raggimel ("roswall manor.litematic", saved in Minecraft 1.21.5).

    python tools/structures/greed_manor.py ["path/to/roswall manor.litematic"]

Needs numpy. What it does:
  * reads the litematic's one region (Litematica packs block states tightly across longs);
  * swaps the blocks 1.21.1 doesn't have (pale oak, bushes, wildflowers, leaf litter, cactus flowers) for the nearest
    ones it does, drops barriers, and makes the leaves persistent (nothing in the arena decays);
  * hangs a skirt of earth and stone under the edge of the grounds, deeper the further in, so the estate reads as an
    island floating in RAGGIMEL's night sky. Its ground (the litematic's bottom layer) ends up at template y 10.
Block entities (signs, banners' patterns, chest contents) and the litematic's entities aren't carried over.
Positions in GreedManor are in this template's coordinates; the script prints the checks they rely on.
"""
import gzip
import io
import struct
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from greed_arena import write_structure  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path.home() / "for boss fights" / "Raggimel" / "roswall manor.litematic"
OUT = ROOT / "src/main/resources/data/fmscraft/structure/arena/greed_manor.nbt"
SKIRT = 10  # layers added under the ground
DROP = {"air", "cave_air", "void_air", "structure_void", "barrier", "leaf_litter"}
# Newer block -> (1.21.1 block, properties: None keeps them all, a dict replaces them)
SWAPS = {
    "bush": ("fern", {}),
    "cactus_flower": ("pink_tulip", {}),
    "wildflowers": ("pink_petals", None),
    "pale_oak_door": ("birch_door", None),
    "pale_oak_fence": ("birch_fence", None),
    "pale_oak_slab": ("birch_slab", None),
    "pale_oak_stairs": ("birch_stairs", None),
    "pale_oak_trapdoor": ("birch_trapdoor", None),
    "pale_oak_wall_sign": ("birch_wall_sign", None),
    "stripped_pale_oak_wood": ("stripped_birch_wood", None),
}

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


def read_nbt(path):
    f = io.BytesIO(gzip.open(path).read())
    r = f.read

    def string():
        return r(struct.unpack(">H", r(2))[0]).decode("utf-8")

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
            return list(struct.unpack(f">{n}i", r(4 * n)))
        if t == LONGS:
            n = struct.unpack(">i", r(4))[0]
            return np.frombuffer(r(8 * n), dtype=">i8").astype(np.int64)
        raise ValueError(f"tag {t}")

    t = r(1)[0]
    string()
    return payload(t)


def unpack(longs, bits, count):
    """Litematica's packing: entry i is the `bits` bits starting at bit i * bits, spanning two longs if it must."""
    data = np.append(longs.view(np.uint64), np.uint64(0))
    idx = np.arange(count, dtype=np.int64) * bits
    li = idx >> 6
    off = (idx & 63).astype(np.uint64)
    lo = data[li] >> off
    hi = np.where(off + np.uint64(bits) > np.uint64(64), data[li + 1] << ((np.uint64(64) - off) % np.uint64(64)), np.uint64(0))
    return ((lo | hi) & np.uint64((1 << bits) - 1)).astype(np.int64)


def convert(name, props):
    if name.endswith("leaves") and "persistent" in props:
        props = dict(props, persistent="true")
    if name not in SWAPS:
        return name, props
    new, keep = SWAPS[name]
    return new, dict(props) if keep is None else dict(keep)


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE
    root = read_nbt(source)
    (region,) = root["Regions"].values()
    sx, sy, sz = (abs(region["Size"][k]) for k in "xyz")
    palette_in = region["BlockStatePalette"]
    bits = max(2, (len(palette_in) - 1).bit_length())
    vals = unpack(region["BlockStates"], bits, sx * sy * sz).reshape(sy, sz, sx)
    print(f"{source.name}: {sx} x {sy} x {sz}, {len(palette_in)} states")

    palette, index, remap = [], {}, np.full(len(palette_in), -1)
    for i, entry in enumerate(palette_in):
        name = entry["Name"].split(":", 1)[1]
        if name in DROP:
            continue
        key = convert(name, dict(entry.get("Properties", {})))
        k = (key[0], tuple(sorted(key[1].items())))
        if k not in index:
            index[k] = len(palette)
            palette.append(key)
        remap[i] = index[k]
    states = remap[vals]

    # The skirt under the grounds: shallow at the very edge, deeper further in
    ground = (states[:3] >= 0).any(axis=0)  # (z, x)
    dist = np.zeros(ground.shape, dtype=np.int32)
    inner = ground.copy()
    for d in range(1, 5):
        shrunk = inner.copy()
        shrunk[1:, :] &= inner[:-1, :]
        shrunk[:-1, :] &= inner[1:, :]
        shrunk[:, 1:] &= inner[:, :-1]
        shrunk[:, :-1] &= inner[:, 1:]
        shrunk[0, :] = shrunk[-1, :] = False
        shrunk[:, 0] = shrunk[:, -1] = False
        dist[shrunk] = d
        inner = shrunk
    rng = np.random.default_rng(7)
    depth = np.where(ground, 2 + 2 * dist + rng.integers(0, 2, ground.shape), 0)
    depth[dist >= 4] = 0  # (well inside the rim nobody sees under it)
    for name in ("dirt", "stone", "andesite", "tuff"):
        k = (name, ())
        if k not in index:
            index[k] = len(palette)
            palette.append((name, {}))
    dirt, stone, andesite, tuff = (index[(n, ())] for n in ("dirt", "stone", "andesite", "tuff"))

    blocks = []
    ys, zs, xs = np.nonzero(states >= 0)
    for y, z, x, s in zip(ys.tolist(), zs.tolist(), xs.tolist(), states[ys, zs, xs].tolist()):
        blocks.append(((x, y + SKIRT, z), s))
    skirt = 0
    zz, xx = np.nonzero(depth > 0)
    for z, x in zip(zz.tolist(), xx.tolist()):
        for layer in range(1, int(depth[z, x]) + 1):
            if layer == 1:
                s = dirt
            else:
                roll = (x * 31 + z * 17 + layer * 7) % 9
                s = andesite if roll == 0 else tuff if roll == 1 else stone
            blocks.append(((x, SKIRT - layer, z), s))
            skirt += 1
    blocks.sort(key=lambda b: (b[0][1], b[0][2], b[0][0]))
    write_structure(OUT, (sx, sy + SKIRT, sz), palette, blocks)
    print(f"wrote {OUT.relative_to(ROOT)}: {len(blocks)} blocks ({skirt} in the skirt), {len(palette)} states")

    # Checks GreedManor's positions rely on (template coordinates, ground at y 10)
    def at(x, y, z):
        s = states[y - SKIRT, z, x] if 0 <= y - SKIRT < sy else -1
        return palette[s][0] if s >= 0 else "air"

    for label, (x, z) in {"centre": (153, 291), "player arc": (153, 306), "exit frame": (152, 318),
                          "probe grass": (66, 378)}.items():
        print(f"  {label} ({x}, {z}): ground {at(x, SKIRT, z)}, above {[at(x, SKIRT + h, z) for h in range(1, 5)]}")


if __name__ == "__main__":
    main()
