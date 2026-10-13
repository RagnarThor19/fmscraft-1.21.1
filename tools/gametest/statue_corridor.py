"""
Writes the game test arena for the Envy statue: a 3-unit straight corridor (5 wide, 5 high) with dead ends at both
ends, and a 1-wide tunnel off the middle unit that the statue must ignore. Same 5-block unit grid as the maze.

Usage: python tools/gametest/statue_corridor.py
"""
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "src/gametest/resources/data/fmscraft-gametest/gametest/structure/statue_corridor.snbt"
UNITS_X, UNITS_Z, H = 5, 3, 7
PATH = {(1, 1), (2, 1), (3, 1)}

palette = ["minecraft:air", "minecraft:stone_bricks", "minecraft:mossy_cobblestone"]
lines = []
for x in range(UNITS_X * 5):
    for y in range(H):
        for z in range(UNITS_Z * 5):
            unit = (x // 5, z // 5)
            state = "minecraft:stone_bricks"
            if unit in PATH and 1 <= y <= 5:
                state = "minecraft:air"
            elif unit in PATH and y == 0:
                state = "minecraft:mossy_cobblestone"
            # The narrow side tunnel: 1 wide, 2 high, into the south wall of the middle unit
            if x == 12 and 10 <= z <= 12 and 1 <= y <= 2:
                state = "minecraft:air"
            lines.append(f'        {{pos: [{x}, {y}, {z}], state: "{state}"}}')

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("{\n    DataVersion: 3955,\n    size: [%d, %d, %d],\n    data: [\n%s\n    ],\n    entities: [],\n    palette: [\n%s\n    ]\n}\n" % (
    UNITS_X * 5, H, UNITS_Z * 5, ",\n".join(lines), ",\n".join(f'        "{p}"' for p in palette)))
