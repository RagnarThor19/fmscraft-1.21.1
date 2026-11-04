"""
Paints everything of MUGEN's that isn't Karl's skin:

- mugen_eyes.png / mugen_eyes_red.png: his glowing eyes, the layer drawn over his face (MugenRenderer, like a spider's
  or an enderman's eyes). 64x64 skin-shaped sheets, see-through everywhere but his eyes: a cold violet under the moon
  in phase 1, a burning red in the eclipse.
- item/kunai.png, item/shuriken.png, item/shadow_orb.png, item/shadow_charge.png: what he throws (drawn in the world
  by item displays), in the vanilla palette: steel like iron's, a black orb like an ender pearl gone dark, a black
  blaze ball like a fire charge.
- environment/eclipse.png (the corona, drawn glowing) and environment/eclipse_disc.png (the black moon over it): the
  phase 2 sky, 32x32 like vanilla's sun.

MUGEN himself wears mugen.png, Karl's skin (wide arms), copied in as it is from tools/textures/source/mugen_skin.png.
The eye pixels below are where that skin has its eyes (front of the head, row 12).

Usage:  python tools/textures/mugen_textures.py [preview.png]
"""
import math
import random
import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/fmscraft/textures"
SKIN = Path(__file__).resolve().parent / "source/mugen_skin.png"

# ---- His eyes ----
WHITES = [(9, 12), (14, 12)]
PUPILS = [(10, 12), (13, 12)]
BROWS = [(9, 11), (10, 11), (13, 11), (14, 11)]
VIOLET = {"white": (0xd8, 0xb8, 0xff, 255), "pupil": (0x9a, 0x4c, 0xe8, 255), "haze": (0x7a, 0x3c, 0xc8, 110)}
RED = {"white": (0xff, 0x8a, 0x7a, 255), "pupil": (0xe8, 0x1c, 0x24, 255), "haze": (0xb0, 0x10, 0x20, 130)}


def eyes(colors):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for p in BROWS:
        img.putpixel(p, colors["haze"])
    for p in WHITES:
        img.putpixel(p, colors["white"])
    for p in PUPILS:
        img.putpixel(p, colors["pupil"])
    return img


# ---- Items ----
OUT = (0x1c, 0x1a, 0x22, 255)
STEEL = [(0xf0, 0xf0, 0xf4, 255), (0xc4, 0xc4, 0xcc, 255), (0x8c, 0x8c, 0x96, 255), (0x58, 0x58, 0x62, 255)]
WRAP = [(0x6a, 0x3c, 0x8a, 255), (0x42, 0x22, 0x5c, 255)]

KUNAI = [
    "................",
    "..............K.",
    ".............KWK",
    "............KWLK",
    "...........KWLMK",
    "..........KWLMK.",
    ".........KWLMK..",
    "........KLLMK...",
    ".......KLMDK....",
    "......KKDKK.....",
    ".....KpPK.......",
    "....KPpK........",
    "...KpPK.........",
    "..KKKK..........",
    ".K..K...........",
    ".KKK............",
]
KUNAI_COLORS = {"K": OUT, "W": STEEL[0], "L": STEEL[1], "M": STEEL[2], "D": STEEL[3], "p": WRAP[0], "P": WRAP[1]}


def grid(rows, colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                img.putpixel((x, y), colors[ch])
    return img


def shuriken():
    """A four-pointed star, its points swept round like a pinwheel, a hole in the middle."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    c = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            # Four points, each swept a little round
            point = abs(math.cos(2 * (a - 0.3 * r / 7.5))) ** 2.2
            reach = 2.9 + 4.7 * point
            if r > reach or r < 1.0:
                continue
            edge = r > reach - 1.0
            light = -math.cos(a + math.pi / 4)  # lit from the top left
            if edge:
                col = OUT
            elif r < 1.8:
                col = STEEL[3]
            else:
                col = STEEL[0] if light > 0.6 else STEEL[1] if light > 0 else STEEL[2] if light > -0.6 else STEEL[3]
            img.putpixel((x, y), col)
    return img


def orb(core, mid, rim, glint, swirl=None, radius=7.0, seed=5):
    """A round orb: dark in the middle, its colour at the rim, a glint at the top left; {swirl} cracks of colour across it."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    c = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if r > radius:
                continue
            if r > radius - 0.9:
                col = OUT
            elif r > radius - 2.0:
                col = rim
            elif r > radius - 3.4:
                col = mid
            else:
                col = core
            if swirl and r < radius - 0.9:
                a = math.atan2(dy, dx)
                if math.sin(3 * a + r * 0.9) > 0.72 or rnd.random() < 0.05:
                    col = swirl[0] if r < radius - 2.5 else swirl[1]
            img.putpixel((x, y), col)
    for p in [(4, 4), (5, 4), (4, 5)]:
        img.putpixel(p, glint)
    return img


# ---- The eclipse ----

def eclipse():
    """The corona, drawn glowing (added to the sky): ragged red rays round where the black moon sits."""
    rnd = random.Random(7)
    rays = [rnd.uniform(0.6, 1.0) for _ in range(24)]
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    disc = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    c = 15.5
    for y in range(32):
        for x in range(32):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            i = int(a * len(rays))
            reach = 9.5 + 5.5 * rays[i]
            if r <= 8.6:
                disc.putpixel((x, y), (6, 0, 2, 255))
            if r < 7.8 or r > reach:
                continue
            f = (r - 7.8) / (reach - 7.8)
            if f < 0.18:
                col = (255, 214, 170)
            elif f < 0.4:
                col = (255, 92, 60)
            elif f < 0.7:
                col = (196, 22, 30)
            else:
                col = (110, 6, 16)
            img.putpixel((x, y), col + (255,))
    return img, disc


# ---- The void spike ----
# Pointed dripstone's shape (drawn as a cross, like dripstone's own model), in the black of the void: violet at its
# edges, a few faint specks of starlight in it. One texture per section, base to tip; each is (width at the bottom,
# width at the top) in pixels, centred, pointing up.
SPIKE_PARTS = ("base", "middle", "frustum", "tip")
SPIKE_WIDTHS = {"base": (12, 10), "middle": (10, 8), "frustum": (8, 4), "tip": (4, 0)}
VOID = [(0x06, 0x02, 0x0a, 255), (0x14, 0x06, 0x22, 255), (0x2c, 0x0e, 0x46, 255), (0x5a, 0x22, 0x8c, 255)]
STAR = (0xc8, 0xa0, 0xff, 255)


def void_spike(part):
    rnd = random.Random(3 + SPIKE_PARTS.index(part))
    bottom, top = SPIKE_WIDTHS[part]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        # Row 15 is the bottom of the section
        u = (15 - y) / 15.0
        w = bottom + (top - bottom) * u
        if w < 0.6:
            continue
        x0, x1 = 8 - w / 2, 8 + w / 2
        for x in range(16):
            if x + 0.5 < x0 or x + 0.5 > x1:
                continue
            edge = min(x + 0.5 - x0, x1 - x - 0.5)
            if edge < 1:
                col = VOID[3] if x + 0.5 < 8 else VOID[2]  # lit from the left
            elif edge < 2:
                col = VOID[1]
            else:
                col = VOID[0]
            if edge >= 1 and rnd.random() < 0.05:
                col = STAR
            img.putpixel((x, y), col)
    return img


def main():
    entity = ASSETS / "entity/mugen"
    entity.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SKIN, entity / "mugen.png")
    eyes(VIOLET).save(entity / "mugen_eyes.png")
    eyes(RED).save(entity / "mugen_eyes_red.png")
    item = ASSETS / "item"
    kunai = grid(KUNAI, KUNAI_COLORS)
    kunai.save(item / "kunai.png")
    star = shuriken()
    star.save(item / "shuriken.png")
    black = orb((0x0a, 0x06, 0x0e, 255), (0x1e, 0x0e, 0x2c, 255), (0x4a, 0x1c, 0x6e, 255), (0xb0, 0x7c, 0xe0, 255))
    black.save(item / "shadow_orb.png")
    blaze = orb((0x14, 0x10, 0x18, 255), (0x22, 0x18, 0x26, 255), (0x34, 0x22, 0x36, 255), (0x8a, 0x60, 0x9c, 255),
                swirl=((0xb0, 0x22, 0x36, 255), (0x5a, 0x1a, 0x6a, 255)), radius=6.6, seed=11)
    blaze.save(item / "shadow_charge.png")
    env = ASSETS / "environment"
    env.mkdir(parents=True, exist_ok=True)
    corona, disc = eclipse()
    corona.save(env / "eclipse.png")
    disc.save(env / "eclipse_disc.png")
    block = ASSETS / "block"
    block.mkdir(parents=True, exist_ok=True)
    spikes = []
    for part in SPIKE_PARTS:
        im = void_spike(part)
        im.save(block / f"void_spike_{part}.png")
        spikes.append(im)
    print("wrote", entity, "and the items, the eclipse and the void spike")
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (16 * 6, 32 + 64), (60, 60, 70, 255))
        for i, im in enumerate([kunai, star, black, blaze]):
            sheet.paste(im, (i * 16, 0), im)
        sheet.paste(corona, (64, 0), corona)
        sheet.paste(disc, (64, 0), disc)
        # The spike stacked as it stands, tip at the top
        for k, im in enumerate(reversed(spikes)):
            sheet.paste(im, (8, 32 + k * 16), im)
        sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
