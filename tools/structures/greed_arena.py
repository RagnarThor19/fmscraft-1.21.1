"""
Builds data/fmscraft/structure/arena/greed.nbt, RAGGIMEL's floating island, from the island the arena was built as
(tools/structures/greed_arena_source.nbt, "massive-floating-island-energy-core").

The source was saved by a newer Minecraft (data version 5023) in a slightly different layout ("id"/"properties"
instead of "Name"/"Properties"), and uses blocks 1.21.1 doesn't have. This:

- writes it in the 1.21.1 structure format (data version 3955),
- swaps every block 1.21.1 doesn't know for the closest one it does (SWAPS below), keeping properties that still apply,
- drops the air and the two purple wool corner markers, and crops the box to what's left.

It also prints where the fight happens: the flattest open stretch of the island's top, which GreedArena.java uses
for the boss and player spawns (the numbers it prints go there). Re-run after replacing the source.

Usage:  python tools/structures/greed_arena.py [source.nbt]
"""
import gzip
import io
import struct
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "greed_arena_source.nbt"
OUT = ROOT / "src/main/resources/data/fmscraft/structure/arena/greed.nbt"
DATA_VERSION = 3955  # 1.21.1

# Newer block -> (1.21.1 block, properties to keep or set). None keeps all properties.
SWAPS = {
    "bush": ("fern", {}),
    "firefly_bush": ("azalea", {}),
    "stripped_pale_oak_log": ("stripped_birch_log", None),
    "pale_oak_stairs": ("birch_stairs", None),
    "pale_oak_slab": ("birch_slab", None),
    "pale_oak_trapdoor": ("birch_trapdoor", None),
    "pale_oak_door": ("birch_door", None),
    "pale_moss_block": ("moss_block", {}),
    "pale_hanging_moss": ("hanging_roots", {"waterlogged": "false"}),
    "iron_chain": ("chain", None),
    # Shelves hang on a wall: an open trapdoor facing the same way sits flat against the same wall
    "dark_oak_shelf": ("dark_oak_trapdoor", "shelf"),
    "pale_oak_shelf": ("birch_trapdoor", "shelf"),
    "oxidized_copper_golem_statue": ("oxidized_chiseled_copper", {}),
}
MARKER = "purple_wool"
# Tops that aren't ground you'd stand and fight on
NOT_GROUND = ("leaves", "log", "wood", "glass", "amethyst_cluster", "lantern", "fence", "wall", "chain", "trapdoor")

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


# ---- NBT (just what structure files use) ----

def read_nbt(path):
    f = io.BytesIO(gzip.open(path).read())

    def r(n):
        return f.read(n)

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
        raise ValueError(f"tag {t}")

    t = r(1)[0]
    string()
    return payload(t)


def write_structure(path, size, palette, blocks):
    out = io.BytesIO()

    def s(v):
        b = v.encode("utf-8")
        out.write(struct.pack(">H", len(b)) + b)

    def named(t, name):
        out.write(bytes([t]))
        s(name)

    def int_list(name, values):
        named(LIST, name)
        out.write(bytes([INT]) + struct.pack(">i", len(values)))
        for v in values: out.write(struct.pack(">i", v))

    named(COMPOUND, "")
    named(INT, "DataVersion"); out.write(struct.pack(">i", DATA_VERSION))
    int_list("size", list(size))
    named(LIST, "entities"); out.write(bytes([COMPOUND]) + struct.pack(">i", 0))
    named(LIST, "palette"); out.write(bytes([COMPOUND]) + struct.pack(">i", len(palette)))
    for name, props in palette:
        named(STRING, "Name"); s(name)
        if props:
            named(COMPOUND, "Properties")
            for k, v in sorted(props.items()):
                named(STRING, k); s(v)
            out.write(bytes([END]))
        out.write(bytes([END]))
    named(LIST, "blocks"); out.write(bytes([COMPOUND]) + struct.pack(">i", len(blocks)))
    for pos, state in blocks:
        int_list("pos", list(pos))
        named(INT, "state"); out.write(struct.pack(">i", state))
        out.write(bytes([END]))
    out.write(bytes([END]))
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(out.getvalue())


# ---- Converting ----

def convert(entry):
    name = entry["id"].split(":", 1)[1]
    props = dict(entry.get("properties", {}))
    if name not in SWAPS:
        return name, props
    new, keep = SWAPS[name]
    if keep is None:
        return new, props
    if keep == "shelf":
        return new, {"facing": props.get("facing", "north"), "half": "bottom", "open": "true",
                     "powered": "false", "waterlogged": props.get("waterlogged", "false")}
    return new, dict(keep)


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE
    root = read_nbt(source)
    src_palette = root["palette"]

    kept = []
    for b in root["blocks"]:
        name = src_palette[b["state"]]["id"].split(":", 1)[1]
        if name in ("air", "structure_void", "cave_air"):
            continue
        if name == MARKER and (b["pos"][1] == 0 or b["pos"][1] == root["size"][1] - 1):
            continue
        kept.append(b)

    lo = [min(b["pos"][i] for b in kept) for i in range(3)]
    hi = [max(b["pos"][i] for b in kept) for i in range(3)]
    size = [hi[i] - lo[i] + 1 for i in range(3)]

    palette, index, blocks, swapped = [], {}, [], Counter()
    for b in kept:
        src = src_palette[b["state"]]
        name, props = convert(src)
        if name != src["id"].split(":", 1)[1]:
            swapped[src["id"]] += 1
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            palette.append(("minecraft:" + name, props))
        blocks.append(([b["pos"][i] - lo[i] for i in range(3)], index[key]))

    write_structure(OUT, size, palette, blocks)
    print(f"Wrote {OUT.relative_to(ROOT)}: {size[0]}x{size[1]}x{size[2]}, {len(blocks)} blocks, {len(palette)} states")
    for k, n in swapped.most_common():
        print(f"  swapped {n:4d} x {k}")

    # ---- Where to fight: the column whose surroundings are the flattest open ground ----
    top, top_name = {}, {}
    for pos, state in blocks:
        k = (pos[0], pos[2])
        if pos[1] > top.get(k, -1):
            top[k], top_name[k] = pos[1], palette[state][0].split(":")[1]
    ground = {k for k in top if not any(w in top_name[k] for w in NOT_GROUND)}
    best = None
    for (x, z) in ground:
        if (x + z) % 2:
            continue
        h, score = top[(x, z)], 0
        for dx in range(-18, 19, 2):
            for dz in range(-18, 19, 2):
                if dx * dx + dz * dz > 18 * 18:
                    continue
                k = (x + dx, z + dz)
                if k in ground and abs(top[k] - h) <= 3:
                    score += 1
        if best is None or score > best[0]:
            best = (score, x, z)
    _, cx, cz = best
    print(f"Fight center (template-local): x={cx} z={cz}, ground top y={top[(cx, cz)]}")
    for dx in (-20, -18, -16, 16, 18, 20):
        k = (cx + dx, cz)
        if k in ground:
            print(f"  ground at x{dx:+d}: y={top[k]} ({top_name[k]})")
    core = max(((pos, palette[s][0]) for pos, s in blocks if "froglight" in palette[s][0]), key=lambda p: p[0][1])
    print(f"Highest froglight (the energy core): {core[0]}")


if __name__ == "__main__":
    main()
