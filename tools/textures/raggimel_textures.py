"""
Paints RAGGIMEL's bubble: the see-through skin of the bubbles he rides and blows (GreedBubbleRenderer, and around him
in RaggimelRenderer). A 16x16x16 cube unwrapped onto 64x32, the same 16x16 tile on every face: a faint pale-blue film,
a brighter rim, and a couple of white glints like the shine on glass or a slime's outer layer.

RAGGIMEL himself wears raggimel.png (the skin he was drawn with) and the intro uses gui/seven_sins.png; both are
copied in as they are, not painted here.

Usage:  python tools/textures/raggimel_textures.py [preview.png]
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures"

FILM = (0xa8, 0xdc, 0xf0, 70)
FILM_LIGHT = (0xc4, 0xec, 0xfa, 90)
RIM = (0xd6, 0xf4, 0xff, 150)
RIM_SHADE = (0x7c, 0xbc, 0xdc, 130)
GLINT = (0xff, 0xff, 0xff, 220)
GLINT_SOFT = (0xf0, 0xfc, 0xff, 150)


def tile():
    t = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                # Lit from the top left like every vanilla texture
                t.putpixel((x, y), RIM if x == 0 or y == 0 else RIM_SHADE)
            elif (x + y) % 7 == 0 and 3 < x < 13:
                t.putpixel((x, y), FILM_LIGHT)
            else:
                t.putpixel((x, y), FILM)
    for x, y in [(2, 2), (3, 2), (4, 2), (2, 3), (2, 4)]:
        t.putpixel((x, y), GLINT)
    for x, y in [(3, 3), (5, 2), (2, 5)]:
        t.putpixel((x, y), GLINT_SOFT)
    for x, y in [(12, 12), (13, 12), (12, 13)]:
        t.putpixel((x, y), GLINT_SOFT)
    return t


def bubble():
    img = Image.new("RGBA", (64, 32))
    t = tile()
    for u in range(4):
        for v in range(2):
            img.paste(t, (u * 16, v * 16))
    return img


def main():
    out = ROOT / "entity/raggimel/bubble.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img = bubble()
    img.save(out)
    print(f"Wrote {out}")
    if len(sys.argv) > 1:
        img.resize((512, 256), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
