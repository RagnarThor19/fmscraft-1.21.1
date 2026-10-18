"""
Builds data/fmscraft/structure/greed_vault.nbt from the two halves saved in the Builds world.

A structure block can only save 48 blocks across, so the vault was saved in two parts: greed_vault2 is the west
part (x 0-28 of the vault) and greed_vault the east part (x 29-76), side by side at the same height and z.
The game itself has no size limit on templates, so this joins them into one 77x15x35 template and:

- Drops the air outside the building (everything the structure blocks saved around it that a flood fill from the
  sides and bottom of the box can reach), so placing the vault doesn't carve a cave around it into the terrain.
  The air inside the rooms, the corridors and up the pipe stays, so it clears the stone out of them.
- Adds a ladder up the inside of the pipe, the only way back out (Mining Fatigue and Block Barrier stop digging
  and pillaring).
- Writes a 7 into the empty book and quill in the chiseled bookshelf, so 5 and 7 both have two clues.

Usage:  python tools/structures/greed_vault.py [structures folder]
The folder defaults to run/saves/Builds/generated/minecraft/structures. Re-run after re-saving either half.
Positions the mod relies on (levers, door, lock, statue stones, pipe) are in GreedVaultLayout.java and are checked
by the vaultIsBuiltWithItsPuzzle game test.
"""
import gzip
import io
import struct
import sys
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/fmscraft/structure/greed_vault.nbt"
DEFAULT_IN = ROOT / "run/saves/Builds/generated/minecraft/structures"

PARTS = (("greed_vault2", 0), ("greed_vault", 29))  # (name, x offset)
SIZE = (77, 15, 35)
PIPE = (72, 75, 13, 16)  # inside of the pipe: x min/max, z min/max
LADDER_X, LADDER_Z = 75, 14  # against the pipe's east wall
SEVEN = "Blanche-Neige vivait avec sept nains."

# ---- NBT, keeping every tag's type so the file round-trips exactly ----
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


class Tag:
    def __init__(self, type, value):
        self.type, self.value = type, value

    def __repr__(self):
        return f"Tag({self.type}, {self.value!r})"


def _read(f, n):
    data = f.read(n)
    if len(data) < n:
        raise EOFError
    return data


def read_payload(f, t):
    if t == BYTE: return struct.unpack(">b", _read(f, 1))[0]
    if t == SHORT: return struct.unpack(">h", _read(f, 2))[0]
    if t == INT: return struct.unpack(">i", _read(f, 4))[0]
    if t == LONG: return struct.unpack(">q", _read(f, 8))[0]
    if t == FLOAT: return struct.unpack(">f", _read(f, 4))[0]
    if t == DOUBLE: return struct.unpack(">d", _read(f, 8))[0]
    if t == BYTES:
        n = struct.unpack(">i", _read(f, 4))[0]
        return bytes(_read(f, n))
    if t == STRING:
        n = struct.unpack(">H", _read(f, 2))[0]
        return _read(f, n).decode("utf-8")
    if t == LIST:
        et = _read(f, 1)[0]
        n = struct.unpack(">i", _read(f, 4))[0]
        return (et, [read_payload(f, et) for _ in range(n)])
    if t == COMPOUND:
        d = {}
        while True:
            tt = _read(f, 1)[0]
            if tt == END:
                return d
            name = read_payload(f, STRING)
            d[name] = Tag(tt, read_payload(f, tt))
    if t == INTS:
        n = struct.unpack(">i", _read(f, 4))[0]
        return list(struct.unpack(">%di" % n, _read(f, 4 * n)))
    if t == LONGS:
        n = struct.unpack(">i", _read(f, 4))[0]
        return list(struct.unpack(">%dq" % n, _read(f, 8 * n)))
    raise ValueError(f"bad tag type {t}")


def write_payload(f, t, v):
    if t == BYTE: f.write(struct.pack(">b", v))
    elif t == SHORT: f.write(struct.pack(">h", v))
    elif t == INT: f.write(struct.pack(">i", v))
    elif t == LONG: f.write(struct.pack(">q", v))
    elif t == FLOAT: f.write(struct.pack(">f", v))
    elif t == DOUBLE: f.write(struct.pack(">d", v))
    elif t == BYTES:
        f.write(struct.pack(">i", len(v)))
        f.write(v)
    elif t == STRING:
        data = v.encode("utf-8")
        f.write(struct.pack(">H", len(data)))
        f.write(data)
    elif t == LIST:
        et, items = v
        f.write(bytes([et]))
        f.write(struct.pack(">i", len(items)))
        for item in items:
            write_payload(f, et, item)
    elif t == COMPOUND:
        for name, tag in v.items():
            f.write(bytes([tag.type]))
            write_payload(f, STRING, name)
            write_payload(f, tag.type, tag.value)
        f.write(bytes([END]))
    elif t == INTS:
        f.write(struct.pack(">i", len(v)))
        f.write(struct.pack(">%di" % len(v), *v))
    elif t == LONGS:
        f.write(struct.pack(">i", len(v)))
        f.write(struct.pack(">%dq" % len(v), *v))
    else:
        raise ValueError(f"bad tag type {t}")


def load(path):
    f = io.BytesIO(gzip.decompress(path.read_bytes()))
    assert _read(f, 1)[0] == COMPOUND
    read_payload(f, STRING)
    return read_payload(f, COMPOUND)


def save(path, root):
    f = io.BytesIO()
    f.write(bytes([COMPOUND]))
    write_payload(f, STRING, "")
    write_payload(f, COMPOUND, root)
    path.write_bytes(gzip.compress(f.getvalue(), mtime=0))


def compound(**tags):
    return dict(tags)


# ---- Building the vault ----

def state_key(state):
    props = state.get("Properties")
    props = tuple(sorted((k, t.value) for k, t in props.value.items())) if props else ()
    return state["Name"].value, props


def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_IN
    palette, keys = [], {}
    grid = {}  # (x, y, z) -> (palette index, nbt compound or None)
    data_version = None

    def index_of(state):
        key = state_key(state)
        if key not in keys:
            keys[key] = len(palette)
            palette.append(state)
        return keys[key]

    for name, ox in PARTS:
        part = load(src / f"{name}.nbt")
        data_version = data_version or part["DataVersion"].value
        assert not part["entities"].value[1], f"{name} has entities; the vault expects none"
        local = [index_of(s) for s in part["palette"].value[1]]
        for block in part["blocks"].value[1]:
            x, y, z = block["pos"].value[1]
            nbt = block["nbt"].value if "nbt" in block else None
            grid[(x + ox, y, z)] = (local[block["state"].value], nbt)

    air = keys[("minecraft:air", ())]
    sx, sy, sz = SIZE
    is_air = lambda p: grid.get(p, (air, None))[0] == air

    # Air the outside can reach (from the sides and the bottom; not the top, or it would pour in down the pipe)
    outside, queue = set(), deque()
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                p = (x, y, z)
                if (x in (0, sx - 1) or z in (0, sz - 1) or y == 0) and is_air(p):
                    outside.add(p)
                    queue.append(p)
    while queue:
        x, y, z = queue.popleft()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if 0 <= n[0] < sx and 0 <= n[1] < sy and 0 <= n[2] < sz and n not in outside and is_air(n):
                outside.add(n)
                queue.append(n)
    x0, x1, z0, z1 = PIPE
    assert not any((x, 8, z) in outside for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)), \
        "the inside of the vault leaks to the outside"
    for p in outside:
        grid.pop(p, None)

    # Ladder up the pipe, from the floor of the room under it to the top of the pipe
    ladder = index_of(compound(Name=Tag(STRING, "minecraft:ladder"), Properties=Tag(COMPOUND, compound(
        facing=Tag(STRING, "west"), waterlogged=Tag(STRING, "false")))))
    for y in range(1, sy):
        p = (LADDER_X, y, LADDER_Z)
        assert is_air(p), f"ladder spot {p} isn't empty"
        assert not is_air((LADDER_X + 1, y, LADDER_Z)), f"no wall behind the ladder at {p}"
        grid[p] = (ladder, None)

    # The empty book and quill becomes the second 7
    filled = 0
    for p, (state, nbt) in grid.items():
        if nbt is None or "Items" not in nbt:
            continue
        for item in nbt["Items"].value[1]:
            if item["id"].value == "minecraft:writable_book" and "components" not in item:
                page = compound(raw=Tag(STRING, SEVEN))
                content = compound(pages=Tag(LIST, (COMPOUND, [page])))
                item["components"] = Tag(COMPOUND, {"minecraft:writable_book_content": Tag(COMPOUND, content)})
                filled += 1
                print(f"wrote the 7 into the book at {p}")
    if not filled:
        print("no empty book found (already filled?)")

    # Only states still in use, air included (inside air clears the rooms)
    used = sorted({state for state, _ in grid.values()})
    remap = {old: new for new, old in enumerate(used)}
    blocks = []
    for (x, y, z), (state, nbt) in sorted(grid.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        block = compound(pos=Tag(LIST, (INT, [x, y, z])), state=Tag(INT, remap[state]))
        if nbt is not None:
            block["nbt"] = Tag(COMPOUND, nbt)
        blocks.append(block)

    root = compound(
        DataVersion=Tag(INT, data_version),
        size=Tag(LIST, (INT, list(SIZE))),
        palette=Tag(LIST, (COMPOUND, [palette[i] for i in used])),
        blocks=Tag(LIST, (COMPOUND, blocks)),
        entities=Tag(LIST, (END, [])),
    )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    save(OUT, root)
    print(f"{OUT.relative_to(ROOT)}: {len(blocks)} blocks ({len(outside)} outside air dropped), {len(used)} states")


if __name__ == "__main__":
    main()
