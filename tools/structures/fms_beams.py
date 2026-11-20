"""
Builds data/fmscraft/structure/fms_beams.nbt, the True Ending shrine where the seven souls go, from fms_beams saved
in the Builds world.

- Every Sin Portal Frame in the build becomes that sin's Soul Pedestal (same facing, empty), so the shrine can be
  built and re-saved with the frames from the creative tab.
- All the air the structure block saved is dropped: the shrine is set into the ground by TrueEnding.place, which
  levels and clears the spot itself, so the template must only ever add blocks, never carve the terrain.

Usage:  python tools/structures/fms_beams.py [structures folder]
The folder defaults to run/saves/Builds/generated/minecraft/structures. Re-run after re-saving the shrine.
The pedestals are found by scanning the placed template (TrueEnding.pedestals), so no positions are hard-coded.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from greed_vault import COMPOUND, INT, LIST, STRING, Tag, load, save  # noqa: E402  (the shared NBT reader/writer)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/fmscraft/structure/fms_beams.nbt"
DEFAULT_IN = ROOT / "run/saves/Builds/generated/minecraft/structures"

FRAME = "fmscraft:sin_portal_frame"
PEDESTAL = "fmscraft:soul_pedestal"


def main():
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_IN
    root = load(src / "fms_beams.nbt")

    palette = root["palette"].value[1]
    for state in palette:
        if state["Name"].value != FRAME:
            continue
        props = state["Properties"].value
        state["Name"] = Tag(STRING, PEDESTAL)
        state["Properties"] = Tag(COMPOUND, {
            "facing": props["facing"],
            "sin": props["sin"],
            "soul": Tag(STRING, "false"),
        })

    air = {i for i, state in enumerate(palette) if state["Name"].value in ("minecraft:air", "minecraft:structure_void")}
    blocks = [b for b in root["blocks"].value[1] if b["state"].value not in air]

    # Keep only the states still in use, renumbered
    used = sorted({b["state"].value for b in blocks})
    remap = {old: new for new, old in enumerate(used)}
    for b in blocks:
        b["state"] = Tag(INT, remap[b["state"].value])
    root["palette"] = Tag(LIST, (COMPOUND, [palette[i] for i in used]))
    root["blocks"] = Tag(LIST, (COMPOUND, blocks))

    pedestals = sum(1 for i in used if palette[i]["Name"].value == PEDESTAL)
    if pedestals != 7:
        sys.exit(f"expected 7 sin portal frames in fms_beams, found {pedestals}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    save(OUT, root)
    size = [v for v in root["size"].value[1]]
    print(f"{OUT.relative_to(ROOT)}: {size[0]}x{size[1]}x{size[2]}, {len(blocks)} blocks, {len(used)} states, 7 pedestals")


if __name__ == "__main__":
    main()
