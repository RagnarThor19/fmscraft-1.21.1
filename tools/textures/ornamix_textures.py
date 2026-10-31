"""
Paints ORNAMIX's burning eyes: ornamix_eyes.png, the glowing layer drawn over his face in Ragemode (OrnamixRenderer,
like a spider's or an enderman's eyes). A 64x64 skin-shaped sheet that's see-through everywhere but his eyes: the
irises white-hot yellow, the whites turned to orange flame, and a flicker of ember red rising off each eye.

ORNAMIX himself wears ornamix.png (the skin he was drawn with), copied in as it is, not painted here. The eye pixels
below are where that skin has its eyes (front of the head, row 12).

Usage:  python tools/textures/ornamix_textures.py [preview.png]
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures/entity/ornamix"

HOT = (0xff, 0xf2, 0x9e, 255)
FLAME = (0xff, 0x8a, 0x1e, 255)
EMBER = (0xd8, 0x3a, 0x12, 200)
EMBER_FAINT = (0xa8, 0x24, 0x0c, 120)

# (x, y) on the skin: the whites and irises of both eyes
WHITES = [(9, 12), (14, 12)]
IRISES = [(10, 12), (13, 12)]
# Flames licking up off the eyes, onto the brow (the row above) and, fainter, the one above that
FLICKER = [((9, 11), EMBER), ((10, 11), FLAME), ((13, 11), FLAME), ((14, 11), EMBER),
           ((10, 10), EMBER_FAINT), ((13, 10), EMBER_FAINT)]


def eyes():
    img = Image.new("RGBA", (64, 64))
    for p in WHITES:
        img.putpixel(p, FLAME)
    for p in IRISES:
        img.putpixel(p, HOT)
    for p, c in FLICKER:
        img.putpixel(p, c)
    return img


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    img = eyes()
    img.save(ROOT / "ornamix_eyes.png")
    print(f"Wrote {ROOT / 'ornamix_eyes.png'}")
    if len(sys.argv) > 1:
        # Preview: the eyes over the face, blown up
        skin = Image.open(ROOT / "ornamix.png").convert("RGBA")
        face = Image.alpha_composite(skin, img).crop((8, 8, 16, 16)).resize((256, 256), Image.NEAREST)
        face.save(sys.argv[1])
        print(f"Preview: {sys.argv[1]}")


if __name__ == "__main__":
    main()
