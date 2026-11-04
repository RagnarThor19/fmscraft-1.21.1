"""Title screen "FMS EDITION" (replaces vanilla's "JAVA EDITION" under the MINECRAFT logo).

Fits the logo in tools/textures/source/fms_edition_logo.png into vanilla's edition slot. Vanilla draws the top 14/16
of an 8:1 texture as a 128x14 strip, so the logo goes, centered, in the top 7/8 of a 1024x128 texture (higher
resolution than vanilla's 512x64 keeps it sharp; only the proportions matter).

    python tools/textures/edition_texture.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/textures/source/fms_edition_logo.png"
OUT = ROOT / "src/main/resources/assets/minecraft/textures/gui/title/edition.png"

W, H = 1024, 128
VISIBLE_H = H * 14 // 16
MARGIN = 2


def main():
    logo = Image.open(SOURCE).convert("RGBA")
    # Crop to the solid logo; the source has faint specks around it
    solid = logo.getchannel("A").point(lambda a: 255 if a > 40 else 0)
    logo = logo.crop(solid.getbbox())
    scale = min((W - 2 * MARGIN) / logo.width, (VISIBLE_H - 2 * MARGIN) / logo.height)
    size = (round(logo.width * scale), round(logo.height * scale))
    logo = logo.resize(size, Image.LANCZOS)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    img.alpha_composite(logo, ((W - size[0]) // 2, (VISIBLE_H - size[1]) // 2))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, optimize=True)
    print("wrote", OUT, size)


if __name__ == "__main__":
    main()
