"""
The textures for the memories at the end of the True Ending credits (textures/gui/credits/), light on black (opaque,
the light in the color: the game adds them, tinted, over the void):
- crack_0..9.png: the glowing cracks that split the void before it bursts open;
- glow.png: a soft round light, for the glow behind the cracks and the flash of the burst.

They're vanilla's block-breaking stages (destroy_stage_0..9) turned inside out: vanilla's dark crack pixels become
light, the darkest ones brightest, so the void cracks exactly like a block being mined.
The vanilla textures are read from the Minecraft jar in the loom cache, like pedestal_textures.py does.

Usage:  python tools/textures/credits_textures.py
"""
from pathlib import Path

from PIL import Image

from pedestal_textures import vanilla  # noqa: E402  (reads block textures from the Minecraft jar)

PROJECT = Path(__file__).resolve().parents[2]
OUT = PROJECT / "src/main/resources/assets/fmscraft/textures/gui/credits"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    stages = vanilla([f"destroy_stage_{i}" for i in range(10)])
    for i in range(10):
        src = stages[f"destroy_stage_{i}"]
        out = Image.new("RGBA", src.size, (0, 0, 0, 255))
        for y in range(src.height):
            for x in range(src.width):
                r, g, b, a = src.getpixel((x, y))
                if a < 128:
                    continue
                # 61 is the crack's heart, 155 its edge
                glow = 255 if r < 100 else 150
                out.putpixel((x, y), (glow, glow, glow, 255))
        out.save(OUT / f"crack_{i}.png")

    size = 64
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    for y in range(size):
        for x in range(size):
            d = ((x + 0.5 - size / 2) ** 2 + (y + 0.5 - size / 2) ** 2) ** 0.5 / (size / 2)
            a = max(0.0, 1.0 - d) ** 2.2
            v = round(255 * a)
            glow.putpixel((x, y), (v, v, v, 255))
    glow.save(OUT / "glow.png")
    print(f"wrote the cracks and the glow to {OUT.relative_to(PROJECT)}")


if __name__ == "__main__":
    main()
