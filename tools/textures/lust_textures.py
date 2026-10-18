"""
Paints the Lust's Garden textures: the Statue of Lust (three textures, the same but for the eyes: shut, half open,
open) and the icon of the Item Barrier effect. The garden's key lock is the shared Sin Portal Frame (frame_textures.py).

Usage:  python tools/textures/lust_textures.py [preview.png]
The BOXES list must match the cuboids in LustStatueModel.java (uv offset and size).
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures"

# Tuff, as in tuff bricks: grey with a green-olive cast, darkest to lightest
TUFF = [(0x58, 0x5b, 0x52), (0x6a, 0x6d, 0x62), (0x7c, 0x7f, 0x73), (0x8e, 0x91, 0x84), (0xa0, 0xa3, 0x95)]
# Her hair and veil, a shade darker and warmer
HAIR = [(0x4a, 0x4b, 0x43), (0x58, 0x59, 0x4f), (0x67, 0x68, 0x5c), (0x76, 0x77, 0x6a)]
MOSS = [(0x47, 0x5a, 0x26), (0x59, 0x6d, 0x2e), (0x6b, 0x80, 0x38)]
CRACK = (0x46, 0x48, 0x40)
MOUTH = (0x24, 0x25, 0x21)
THROAT = (0x11, 0x11, 0x0f)
LASH = (0x31, 0x32, 0x2c)
OPEN = (0xc6, 0xc8, 0xbd)  # open, the dark slits of her eyes turn a pale stone grey
OPENING = (0x8a, 0x8c, 0x82)

# name, u, v, w, h, d, style
BOXES = [
    ("head", 0, 0, 7, 8, 7, "head"),
    ("crown", 28, 0, 8, 1, 8, "roses"),
    ("hem", 60, 0, 11, 3, 8, "robe"),
    ("girdle", 98, 0, 8, 4, 5, "roses"),
    ("skirt", 0, 15, 10, 19, 7, "robe"),
    ("bodice", 34, 15, 8, 11, 5, "bodice"),
    ("hair", 60, 15, 7, 26, 2, "hair"),
    ("arm_l", 78, 15, 2, 14, 2, "stone"),
    ("arm_r", 86, 15, 2, 8, 2, "stone"),
    ("sleeve", 94, 15, 1, 12, 3, "robe"),
    ("hand_l", 102, 15, 3, 3, 3, "stone"),
    ("neck", 114, 15, 2, 3, 2, "stone"),
    ("hand_r", 102, 22, 3, 2, 2, "stone"),
    ("foot", 114, 22, 2, 1, 1, "stone"),
]


def faces(u, v, w, h, d):
    """Minecraft box UV unwrap: name -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def pick(ramp, rng, shade, spread=(-1, 0, 0, 0, 1)):
    return ramp[min(len(ramp) - 1, max(0, shade + rng.choice(spread)))]


def paint_face(img, rect, rng, style, face, name):
    x0, y0, fw, fh = rect
    base = {"top": 3, "bottom": 1}.get(face, 2)
    for y in range(fh):
        for x in range(fw):
            low = y / max(1, fh - 1)
            if style == "hair":
                # Long wavy strands: darker every other column, lighter crests drifting down
                shade = 1 + (1 if (x + (y // 3)) % 2 == 0 else 0) + (1 if rng.random() < 0.15 else 0)
                c = HAIR[min(3, shade)]
            elif style == "roses":
                c = pick(TUFF, rng, base)
                # A rose every few pixels: a pale petal blob with a dark heart
                if face in ("front", "back", "east", "west", "top") and (x + y * 2) % 3 == 0:
                    c = TUFF[4] if rng.random() < 0.7 else TUFF[3]
                if face in ("front", "back", "east", "west") and (x + y * 2) % 3 == 1 and rng.random() < 0.4:
                    c = TUFF[1]
            else:
                c = pick(TUFF, rng, base)
                if style == "robe" and face not in ("top", "bottom"):
                    # Folds of drapery falling straight down, a little wavy
                    if (x + (1 if y > fh // 2 else 0)) % 3 == 0:
                        c = TUFF[max(0, base - 1)]
                    elif (x + (1 if y > fh // 2 else 0)) % 3 == 1 and rng.random() < 0.5:
                        c = TUFF[min(4, base + 1)]
                    # Moss creeping up from the hem
                    if name in ("hem", "skirt") and low > 0.7 and rng.random() < (low - 0.7) * 1.2:
                        c = rng.choice(MOSS)
                if style == "bodice" and face == "front" and x in (2, 5) and y < fh - 1:
                    c = TUFF[base - 1]  # the cloth crossed over her breast
                if face not in ("top", "bottom") and (x == 0 or x == fw - 1) and rng.random() < 0.4:
                    c = TUFF[max(0, base - 1)]
            if style != "hair" and rng.random() < 0.012:
                c = rng.choice(MOSS[:2])  # lichen
            img.putpixel((x0 + x, y0 + y), c + (255,))
    # Weathering cracks on the big faces
    if fw * fh > 40 and style in ("robe", "bodice", "stone"):
        for _ in range(max(1, fw * fh // 140)):
            cx, cy = rng.randrange(fw), rng.randrange(fh)
            for _ in range(rng.randint(2, 5)):
                if 0 <= cx < fw and 0 <= cy < fh:
                    img.putpixel((x0 + cx, y0 + cy), CRACK + (255,))
                cx += rng.choice([-1, 0, 1])
                cy += 1


# 7x8 face. H hair/veil, 2-4 stone, L lid, K shut eye (dark slit), G open eye (pale grey), O half open,
# N nose shadow, M mouth, D throat. The eyes are 2x1 each; opening only changes their colour.
# Row 1 is behind the crown of roses.
FACE_ROWS = {
    "top": ["HHHHHHH", "HHHHHHH", "H33433H"],
    "closed": ["HLL3LLH", "HKK3KKH"],
    "opening": ["HLL3LLH", "HOO3OOH"],
    "open": ["HLL3LLH", "HGG3GGH"],
    "bottom": ["H32N23H", "H2MMM2H", "H2MDM2H"],
}


def paint_head_front(img, rect, rng, eyes):
    x0, y0, _, _ = rect
    rows = FACE_ROWS["top"] + FACE_ROWS[eyes] + FACE_ROWS["bottom"]
    assert len(rows) == 8, rows
    for y, row in enumerate(rows):
        assert len(row) == 7, row
        for x, ch in enumerate(row):
            if ch == "H":
                c = HAIR[1 + (x + y) % 2]
            elif ch in "234":
                c = pick(TUFF, rng, int(ch), (0, 0, 1))
            else:
                c = {"L": TUFF[3], "K": LASH, "G": OPEN, "O": OPENING, "N": TUFF[1], "M": MOUTH, "D": THROAT}[ch]
            img.putpixel((x0 + x, y0 + y), c + (255,))


def statue(eyes):
    rng = random.Random(1306)  # the year of Giotto's Vices, like the Statue of Envy
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    for name, u, v, w, h, d, style in BOXES:
        fs = faces(u, v, w, h, d)
        for face, rect in fs.items():
            if name == "head" and face in ("east", "west", "back", "top"):
                paint_face(img, rect, rng, "hair", face, name)  # veiled all round
            else:
                paint_face(img, rect, rng, style, face, name)
        if name == "head":
            paint_head_front(img, fs["front"], rng, eyes)
    return img


def item_barrier_icon():
    """18x18 like the vanilla effect icons: a little iron sword under the barrier's red no-entry sign."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    blade, edge, hilt, grip = (0xd8, 0xd8, 0xd8), (0x6b, 0x6b, 0x6b), (0x8a, 0x6a, 0x3a), (0x4a, 0x35, 0x1c)
    for i in range(8):  # blade up to the top right
        x, y = 6 + i, 11 - i
        img.putpixel((x, y), blade + (255,))
        img.putpixel((x + 1, y), edge + (255,))
    for x, y in ((4, 11), (5, 12), (6, 13), (7, 12)):
        img.putpixel((x, y), hilt + (255,))  # crossguard
    for x, y in ((4, 13), (3, 14)):
        img.putpixel((x, y), grip + (255,))
    red, dark = (0xd9, 0x2d, 0x2d), (0x5e, 0x0e, 0x0e)
    cx = cy = 8.5
    for y in range(18):
        for x in range(18):
            r = math.hypot(x + 0.5 - cx - 0.5, y + 0.5 - cy - 0.5)
            on_slash = abs((x - 2) - (y - 2)) <= 1 and 3 <= x <= 14
            if 6.0 <= r <= 8.0 or (on_slash and r < 7.0):
                img.putpixel((x, y), red + (255,))
            elif 5.2 <= r < 6.0 or 8.0 < r <= 8.8:
                img.putpixel((x, y), dark + (255,))
    return img


def main():
    (ROOT / "entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "mob_effect").mkdir(parents=True, exist_ok=True)
    textures = {name: statue(eyes) for name, eyes in
                (("lust_statue", "closed"), ("lust_statue_opening", "opening"), ("lust_statue_open", "open"))}
    for name, img in textures.items():
        img.save(ROOT / f"entity/{name}.png")
    icon = item_barrier_icon()
    icon.save(ROOT / "mob_effect/item_barrier.png")

    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (768 + 16 + 108, 384 * 3 + 32), (110, 110, 110, 255))
        for i, img in enumerate(textures.values()):
            sheet.alpha_composite(img.resize((768, 384), Image.NEAREST), (8, 8 + i * 392))
        sheet.alpha_composite(icon.resize((108, 108), Image.NEAREST), (784, 8))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
