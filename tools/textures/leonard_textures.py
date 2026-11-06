"""
Paints everything of LEONARD's (Pride) fight:

- entity/leonard/leonard.png: Leo's skin (wide arms, a gold crown and shades on the hat layer), copied in as it is from
  tools/textures/source/leonard_skin.png.
- entity/leonard/leonard_eyes.png: lightning glinting on his shades in phase 1, the layer drawn glowing over his face
  (LeonardRenderer, like a spider's eyes). See-through everywhere but the lenses on the hat layer.
- entity/leonard/munch.png: Munch, the white giant who rises out of the clouds to watch (PrideSky). The source
  (tools/textures/source/munch.png) is drawn on the skin's outer layers only; here it's moved onto the inner ones, so the
  giant is solid and nothing is drawn twice.
- entity/leonard/munch_eyes.png: Munch's eyes burning white-gold while he gathers his beam.
- item/lightning_bolt.png: LEONARD's weapon, the bolt he holds and throws, redrawn at 16x16 from the pixel art bolt it
  was drawn from (14x21, too tall for an item): the same dark brown outline, the yellow, the white glints down its
  left edge and the orange shade down its right, tip to the top right like a sword's.
- item/traffic_cone.png: the traffic cone's sheet (its model, models/item/traffic_cone.json, maps its tiers onto the
  bands): orange, the two white reflective bands, the black rubber base.

Usage:  python tools/textures/leonard_textures.py [preview.png]
"""
import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/fmscraft/textures"
SOURCE = Path(__file__).resolve().parent / "source"

# ---- Leonard's shades (hat layer, front of the head): the lenses, rows 12-13 ----
LENSES = [(40, 12), (41, 12), (42, 12), (45, 12), (46, 12), (47, 12), (41, 13), (42, 13), (45, 13), (46, 13)]
GLINT = {(41, 12): (0xff, 0xff, 0xf0, 255), (46, 12): (0xff, 0xff, 0xf0, 255)}
LENS_GLOW = (0xff, 0xe0, 0x4a, 200)


def leonard_eyes():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for p in LENSES:
        img.putpixel(p, GLINT.get(p, LENS_GLOW))
    return img


# ---- Munch: outer layers moved onto the inner ones ----
# (outer region, inner region), each (x, y, w, h) on the 64x64 skin
LAYERS = [
    ((32, 0, 32, 16), (0, 0, 32, 16)),     # hat -> head
    ((16, 32, 24, 16), (16, 16, 24, 16)),  # jacket -> body
    ((40, 32, 16, 16), (40, 16, 16, 16)),  # right sleeve -> right arm
    ((0, 32, 16, 16), (0, 16, 16, 16)),    # right pants -> right leg
    ((0, 48, 16, 16), (16, 48, 16, 16)),   # left pants -> left leg
    ((48, 48, 16, 16), (32, 48, 16, 16)),  # left sleeve -> left arm
]
# His eyes on the inner head's front, once moved (two pixels each, row 10)
MUNCH_EYES = [(9, 10), (10, 10), (13, 10), (14, 10)]


def munch():
    src = Image.open(SOURCE / "munch.png").convert("RGBA")
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for (ox, oy, w, h), (ix, iy, _, _) in LAYERS:
        out.paste(src.crop((ox, oy, ox + w, oy + h)), (ix, iy))
    return out


def munch_eyes():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for i, p in enumerate(MUNCH_EYES):
        img.putpixel(p, (0xff, 0xff, 0xf4, 255) if i in (1, 2) else (0xff, 0xd8, 0x50, 255))
    # A haze of light round them
    for x, y in MUNCH_EYES:
        for dx, dy in ((0, -1), (0, 1)):
            if img.getpixel((x + dx, y + dy))[3] == 0:
                img.putpixel((x + dx, y + dy), (0xff, 0xc8, 0x40, 90))
    return img


# ---- The lightning bolt ----
BOLT_COLORS = {
    "K": (0x3a, 0x16, 0x08, 255),   # outline
    "W": (0xff, 0xfb, 0xe8, 255),   # white glint
    "L": (0xfd, 0xf0, 0x6a, 255),   # light yellow
    "Y": (0xfd, 0xd2, 0x1c, 255),   # yellow
    "O": (0xf0, 0x86, 0x14, 255),   # orange shade
    "D": (0xc0, 0x55, 0x08, 255),   # deep shade
}
BOLT = [
    "........KKKKKKK.",
    ".......KWLLYYOOK",
    "......KWLYYYYOK.",
    ".....KWLYYYYOK..",
    "....KWLYYYYOK...",
    "...KWLYYYYOKKKKK",
    "..KWLLLLYYYLYOOK",
    "..KKYYYYYYYYYOK.",
    "....KKKKYYYYOK..",
    ".......KWYYOK...",
    "......KWYYOK....",
    ".....KWYOOK.....",
    "....KWYODK......",
    "...KLYDK........",
    "..KLDK..........",
    "..KKK...........",
]

# ---- The traffic cone's sheet: bands top to bottom ----
ORANGE = [(0xff, 0x8a, 0x2a, 255), (0xf2, 0x6c, 0x14, 255), (0xd8, 0x56, 0x0c, 255)]
WHITE = [(0xff, 0xff, 0xff, 255), (0xe4, 0xe8, 0xec, 255)]
RUBBER = [(0x3a, 0x3a, 0x3e, 255), (0x24, 0x24, 0x28, 255)]
# rows: 0-3 orange (tip), 4-5 white, 6-9 orange, 10-11 white, 12-13 orange, 14-15 rubber
CONE_BANDS = ["O"] * 4 + ["W"] * 2 + ["O"] * 4 + ["W"] * 2 + ["O"] * 2 + ["R"] * 2


def grid(rows, colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colors:
                img.putpixel((x, y), colors[c])
    return img


def cone():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, band in enumerate(CONE_BANDS):
        for x in range(16):
            # Lit from the left, in shade to the right; a little speckle like vanilla's orange concrete
            shade = 0 if x < 6 else 1 if x < 12 else 2
            if (x * 7 + y * 3) % 11 == 0:
                shade = min(2, shade + 1)
            if band == "O":
                c = ORANGE[shade]
            elif band == "W":
                c = WHITE[0 if shade < 2 else 1]
            else:
                c = RUBBER[0 if shade == 0 else 1]
            img.putpixel((x, y), c)
    return img


def main():
    entity = ASSETS / "entity/leonard"
    item = ASSETS / "item"
    entity.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SOURCE / "leonard_skin.png", entity / "leonard.png")
    leonard_eyes().save(entity / "leonard_eyes.png")
    giant = munch()
    giant.save(entity / "munch.png")
    munch_eyes().save(entity / "munch_eyes.png")
    bolt = grid(BOLT, BOLT_COLORS)
    bolt.save(item / "lightning_bolt.png")
    sheet = cone()
    sheet.save(item / "traffic_cone.png")
    print(f"Wrote {entity.relative_to(ROOT)}/{{leonard,leonard_eyes,munch,munch_eyes}}.png, "
          f"item/lightning_bolt.png, item/traffic_cone.png")
    if len(sys.argv) > 1:
        preview = Image.new("RGBA", (16 * 2 + 64 * 2 + 12, 64), (90, 140, 200, 255))
        preview.alpha_composite(bolt, (0, 0))
        preview.alpha_composite(sheet, (0, 20))
        preview.alpha_composite(giant, (20, 0))
        preview.alpha_composite(Image.open(entity / "leonard.png").convert("RGBA"), (88, 0))
        preview.alpha_composite(leonard_eyes(), (88, 0))
        preview.resize((preview.width * 6, preview.height * 6), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
