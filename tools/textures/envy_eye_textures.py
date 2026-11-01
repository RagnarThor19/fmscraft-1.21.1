"""
Makes the watching eye that hangs everywhere round GUMMSS's arena (EnvySky): textures/environment/envy_eye.png.

It's drawn from source/analog_eye.png (the eye picture GUMMSS was designed with), brought down to 48x32 pixels and a
small palette of the vanilla greys, with hard edges (every pixel either there or not), so it sits in the world like
the vanilla sun and moon do: pixel art, not a photo. The three glints in its pupil are kept.

Usage:  python tools/textures/envy_eye_textures.py [preview.png]
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "source/analog_eye.png"
OUT = HERE.parents[1] / "src/main/resources/assets/fmscraft/textures/environment/envy_eye.png"
SIZE = (48, 32)

# Darkest to lightest: deepslate black through stone grey to calcite white
PALETTE = [(0x0b, 0x0b, 0x0e), (0x1c, 0x1b, 0x20), (0x2f, 0x2e, 0x33), (0x48, 0x47, 0x4c), (0x63, 0x62, 0x66),
           (0x7f, 0x7e, 0x80), (0x9c, 0x9b, 0x9b), (0xbd, 0xbc, 0xb8), (0xdf, 0xde, 0xd8)]


def nearest(rgb):
    return min(PALETTE, key=lambda p: sum((a - b) ** 2 for a, b in zip(p, rgb)))


def main():
    src = Image.open(SOURCE).convert("RGBA")
    small = src.resize(SIZE, Image.BOX)
    out = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    for y in range(SIZE[1]):
        for x in range(SIZE[0]):
            r, g, b, a = small.getpixel((x, y))
            if a < 110:
                continue
            out.putpixel((x, y), nearest((r, g, b)) + (255,))
    # The glints: the brightest pixels in the source pupil, put back as single white pixels
    sx, sy = src.width / SIZE[0], src.height / SIZE[1]
    for y in range(SIZE[1]):
        for x in range(SIZE[0]):
            box = src.crop((int(x * sx), int(y * sy), int((x + 1) * sx), int((y + 1) * sy)))
            if box.getextrema()[0][1] > 230 and out.getpixel((x, y))[0] < 0x40 and out.getpixel((x, y))[3]:
                out.putpixel((x, y), (0xf4, 0xf4, 0xf0, 255))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT)
    print("wrote", OUT)
    if len(sys.argv) > 1:
        out.resize((SIZE[0] * 10, SIZE[1] * 10), Image.NEAREST).save(sys.argv[1])
        print("preview", sys.argv[1])


if __name__ == "__main__":
    main()
