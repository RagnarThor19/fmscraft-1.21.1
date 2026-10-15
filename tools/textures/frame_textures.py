"""
Paints the Sin Portal Frame, the key lock shared by every sin's structure: the vanilla end portal frame, recolored
per sin. Only the color changes. Every pixel keeps its brightness and is pulled partway toward the sin's color,
then dulled a little, so each frame reads as the same block in a muted shade of its boss.

The vanilla textures are read straight from the Minecraft jar in the project's loom cache (run any gradle task
first if it's missing), so nothing of Mojang's is copied into the repo.

Usage:  python tools/textures/frame_textures.py [preview.png]
Writes  block/sin_portal_frame_<sin>_{top,side,bottom}.png
"""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parents[2]
ROOT = PROJECT / "src/main/resources/assets/fmscraft/textures/block"

# Same colors as Sin.java
SINS = {
    "greed": 0x7A8FAE,
    "wrath": 0xA04A3C,
    "pride": 0xC2A85A,
    "gluttony": 0x4A5D8A,
    "envy": 0x587559,
    "lust": 0x7F6894,
    "sloth": 0x5F6263,
}
# How far each pixel moves toward the sin's color, and how much it's greyed afterwards
TINT = 0.55
DULL = 0.35
# Vanilla texture -> our face name
FACES = {"end_portal_frame_top": "top", "end_portal_frame_side": "side", "end_stone": "bottom"}


def vanilla_textures():
    jars = sorted(PROJECT.glob(".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged*/*/minecraft-merged*.jar"))
    jars = [j for j in jars if "sources" not in j.name]
    if not jars:
        sys.exit("Minecraft jar not found in .gradle/loom-cache: run any gradle task (e.g. gradlew compileJava) first")
    with zipfile.ZipFile(jars[-1]) as jar:
        return {name: Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/block/{name}.png"))).convert("RGBA")
                for name in FACES}


def luma(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


def recolor(img, color):
    cr, cg, cb = (color >> 16) & 255, (color >> 8) & 255, color & 255
    cl = luma(cr, cg, cb)
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            l = luma(r, g, b)
            # The sin's color at this pixel's brightness
            tr, tg, tb = (c * l / cl for c in (cr, cg, cb))
            r, g, b = (o + (t - o) * TINT for o, t in ((r, tr), (g, tg), (b, tb)))
            # Dull it toward its own grey, keeping the brightness
            l2 = luma(r, g, b)
            r, g, b = (c + (l2 - c) * DULL for c in (r, g, b))
            px[x, y] = tuple(max(0, min(255, round(c))) for c in (r, g, b)) + (a,)
    return out


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    vanilla = vanilla_textures()
    tiles = {}
    for sin, color in SINS.items():
        for name, face in FACES.items():
            img = recolor(vanilla[name], color)
            img.save(ROOT / f"sin_portal_frame_{sin}_{face}.png")
            tiles[sin, face] = img

    if len(sys.argv) > 1:
        # Preview: one column per sin (vanilla first), rows top / side / bottom
        columns = [("vanilla", None)] + list(SINS.items())
        sheet = Image.new("RGBA", (18 * len(columns), 18 * 3), (40, 40, 40, 255))
        for col, (sin, _) in enumerate(columns):
            for row, (name, face) in enumerate(FACES.items()):
                tile = vanilla[name] if sin == "vanilla" else tiles[sin, face]
                sheet.paste(tile, (col * 18 + 1, row * 18 + 1), tile)
        sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
