"""
Paints the Soul Pedestal, the seven sockets of the True Ending shrine. It's the end portal frame's shape again (like
the Sin Portal Frame), but made of the shrine's tuff: the end stone is swapped for polished tuff shade for shade,
the green trim takes the sin's color, and the dark socket turns a deep shade of it. Once a soul is in, the socket
glows in the sin's color (the _lit top).

The vanilla textures are read straight from the Minecraft jar in the project's loom cache (run any gradle task
first if it's missing), so nothing of Mojang's is copied into the repo. The bottom is vanilla tuff, by the model.

Usage:  python tools/textures/pedestal_textures.py [preview.png]
Writes  block/soul_pedestal_<sin>_{top,top_lit,side}.png
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


def vanilla(names):
    jars = sorted(PROJECT.glob(".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged*/*/minecraft-merged*.jar"))
    jars = [j for j in jars if "sources" not in j.name]
    if not jars:
        sys.exit("Minecraft jar not found in .gradle/loom-cache: run any gradle task (e.g. gradlew compileJava) first")
    with zipfile.ZipFile(jars[-1]) as jar:
        return {n: Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/block/{n}.png"))).convert("RGBA") for n in names}


def luma(rgb):
    r, g, b = rgb[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def rgb_of(color):
    return (color >> 16) & 255, (color >> 8) & 255, color & 255


def kind(px):
    """What part of the end portal frame a pixel is: its end stone, the green trim, or the dark socket."""
    r, g, b = px[:3]
    if luma(px) < 40:
        return "socket"
    if g > r + 12 and g >= b:
        return "trim"
    return "stone"


def shade(color, l, ref=150.0):
    """The color at brightness l (ref is the brightness the color itself stands for)."""
    return tuple(max(0, min(255, round(c * l / ref))) for c in rgb_of(color))


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def paint(frame, tuff_ramp, color, lit):
    out = frame.copy()
    px = out.load()
    stone = sorted({luma(px[x, y]) for y in range(out.height) for x in range(out.width)
                    if px[x, y][3] and kind(px[x, y]) == "stone"})
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            k = kind(p)
            l = luma(p)
            if k == "stone":
                # Same rank of brightness in polished tuff's shades (its middle ones, so it stays as calm as end stone)
                rank = 0.2 + 0.6 * stone.index(l) / max(1, len(stone) - 1)
                c = tuff_ramp[round(rank * (len(tuff_ramp) - 1))]
            elif k == "trim":
                # The sin's color, a little greyed so it sits in the stone
                c = mix(shade(color, l + 30), (round(l + 30),) * 3, 0.25)
            else:
                if lit:
                    # The soul's light: bright in the middle, the sin's color toward the rim
                    cx, cy = abs(x - 7.5), abs(y - 7.5)
                    glow = 1.0 - max(cx, cy) / 4.5
                    c = mix(shade(color, 170), (255, 255, 255), max(0.0, glow) * 0.55)
                else:
                    c = mix(shade(color, 45), (20, 20, 22), 0.4)
            px[x, y] = tuple(c) + (p[3],)
    return out


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    tex = vanilla(["end_portal_frame_top", "end_portal_frame_side", "polished_tuff"])
    tuff = tex["polished_tuff"]
    tuff_ramp = sorted({tuff.getpixel((x, y))[:3] for y in range(16) for x in range(16)}, key=luma)

    tiles = {}
    for sin, color in SINS.items():
        for name, face, lit in (("end_portal_frame_top", "top", False), ("end_portal_frame_top", "top_lit", True),
                                ("end_portal_frame_side", "side", False)):
            img = paint(tex[name], tuff_ramp, color, lit)
            img.save(ROOT / f"soul_pedestal_{sin}_{face}.png")
            tiles[sin, face] = img

    if len(sys.argv) > 1:
        faces = ("top", "top_lit", "side")
        sheet = Image.new("RGBA", (18 * len(SINS), 18 * len(faces)), (40, 40, 40, 255))
        for col, sin in enumerate(SINS):
            for row, face in enumerate(faces):
                sheet.paste(tiles[sin, face], (col * 18 + 1, row * 18 + 1), tiles[sin, face])
        sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
