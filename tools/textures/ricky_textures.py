"""
Paints RICKY's (Sloth) look:

- entity/ricky/ricky.png: Patrik's skin (wide arms: a hoodie with a red emblem, black hair), copied in as it is from
  tools/textures/source/ricky_skin.png.
- entity/ricky/ricky_saiyan.png: the same, gone Super Saiyan (spiked golden hair, teal eyes), from
  tools/textures/source/ricky_saiyan_skin.png. He wears it from his transformation at half health on.
- entity/ricky/ricky_saiyan_glow.png: what of the Super Saiyan skin burns with its own light, drawn glowing over him
  (RickyRenderer, like a spider's eyes): the golden hair, a little dimmed so the glow doesn't wash its shading out, and
  the eyes at full strength. See-through everywhere else.

Usage:  python tools/textures/ricky_textures.py
"""
import colorsys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/fmscraft/textures/entity/ricky"
SOURCE = Path(__file__).resolve().parent / "source"


def glow(saiyan):
    """The golden hair (any strong yellow, base and hat layers) at 70%, and the teal eyes whole."""
    img = Image.new("RGBA", saiyan.size, (0, 0, 0, 0))
    for y in range(saiyan.height):
        for x in range(saiyan.width):
            r, g, b, a = saiyan.getpixel((x, y))
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if 0.09 < h < 0.2 and s > 0.5 and v > 0.6:
                img.putpixel((x, y), (r, g, b, 178))
            elif 0.45 < h < 0.6 and s > 0.4 and v > 0.6:
                img.putpixel((x, y), (r, g, b, 255))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    skin = Image.open(SOURCE / "ricky_skin.png").convert("RGBA")
    saiyan = Image.open(SOURCE / "ricky_saiyan_skin.png").convert("RGBA")
    for img in (skin, saiyan):
        if img.size != (64, 64):
            raise SystemExit(f"a skin should be 64x64, got {img.size}")
    skin.save(OUT / "ricky.png")
    saiyan.save(OUT / "ricky_saiyan.png")
    glow(saiyan).save(OUT / "ricky_saiyan_glow.png")
    print("wrote ricky.png, ricky_saiyan.png, ricky_saiyan_glow.png to", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
