"""
Paints GUMMSS's glowing eyes: gummss_eyes.png, the layer drawn over his face while he casts (GummssRenderer, like
a spider's or an enderman's eyes). A 64x64 skin-shaped sheet that's see-through everywhere but his eyes: the irises a
poisonous green, the whites a pale sickly green, and a faint green haze on the brow above.

GUMMSS himself wears gummss.png (the skin he was drawn with), copied in as it is, not painted here. The eye pixels
below are where that skin has its eyes (front of the head, row 12).

Usage:  python tools/textures/gummss_textures.py [preview.png]
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures/entity/gummss"

IRIS = (0x7d, 0xff, 0x5a, 255)
WHITE = (0xc8, 0xf5, 0xb4, 255)
HAZE = (0x3c, 0xa0, 0x32, 110)

WHITES = [(9, 12), (14, 12)]
IRISES = [(10, 12), (13, 12)]
BROW = [(10, 11), (13, 11)]


def main():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for p in WHITES:
        img.putpixel(p, WHITE)
    for p in IRISES:
        img.putpixel(p, IRIS)
    for p in BROW:
        img.putpixel(p, HAZE)
    out = ROOT / "gummss_eyes.png"
    img.save(out)
    print("wrote", out)
    if len(sys.argv) > 1:
        img.resize((512, 512), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
