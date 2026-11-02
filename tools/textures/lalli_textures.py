"""
Paints LalliLarge's glowing ice eyes: lalli_eyes.png, the layer drawn over his face in phase 2 (LalliRenderer, like a
spider's or an enderman's eyes). A 64x64 skin-shaped sheet that's see-through everywhere but his eyes: the irises a
bright glacier blue, the whites a pale frost white-blue, and a faint blue haze round them.

LalliLarge himself wears lalli.png (Larus's skin, slim arms), copied in as it is, not painted here; the statue in the
snowfield wears statue.png, copied in too. The eye pixels below are where that skin has its eyes (front of the head,
rows 12 and 13).

Usage:  python tools/textures/lalli_textures.py [preview.png]
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures/entity/lalli"

IRIS = (0x8c, 0xe8, 0xff, 255)
IRIS_LOW = (0x4a, 0xb8, 0xf0, 255)
WHITE = (0xe0, 0xf6, 0xff, 255)
HAZE = (0x6a, 0xc8, 0xff, 120)

IRISES = [(10, 12), (13, 12)]
IRISES_LOW = [(10, 13), (13, 13)]
WHITES = [(9, 13), (14, 13)]
HAZES = [(9, 12), (14, 12), (10, 11), (13, 11)]


def main():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for p in HAZES:
        img.putpixel(p, HAZE)
    for p in WHITES:
        img.putpixel(p, WHITE)
    for p in IRISES:
        img.putpixel(p, IRIS)
    for p in IRISES_LOW:
        img.putpixel(p, IRIS_LOW)
    out = ROOT / "lalli_eyes.png"
    img.save(out)
    print("wrote", out)
    if len(sys.argv) > 1:
        img.resize((512, 512), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
