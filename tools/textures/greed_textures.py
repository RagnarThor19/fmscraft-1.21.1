"""
Paints the Greed Vault textures: the Statue of Greed (entity + faintly glowing eyes layer) and the icon of the
Block Barrier effect. The vault's key lock is the shared Sin Portal Frame (frame_textures.py).

Usage:  python tools/textures/greed_textures.py [preview.png]
The BOXES list must match the cuboids in GreedStatueModel.java (uv offset and size).
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures"

# Weathered statue stone, grey and dull
STONE = [(0x4a, 0x49, 0x47), (0x5e, 0x5c, 0x59), (0x71, 0x6f, 0x6b), (0x84, 0x81, 0x7c), (0x96, 0x93, 0x8d)]
# Old, tarnished gold leaf, darkest to brightest; long since lost its shine
GOLD = [(0x4c, 0x3c, 0x22), (0x6b, 0x55, 0x2c), (0x86, 0x6c, 0x3a), (0x9d, 0x83, 0x4b), (0xae, 0x97, 0x5f)]
CRACK = (0x2e, 0x2c, 0x29)
SOCKET = (0x16, 0x14, 0x12)
EYE = (0xc9, 0xad, 0x5a)

# name, u, v, w, h, d, style
BOXES = [
    ("robe", 0, 0, 10, 12, 6, "robe"),
    ("chest", 32, 0, 8, 9, 5, "stone"),
    ("shoulders", 0, 18, 10, 2, 6, "stone"),
    ("neck", 32, 14, 2, 4, 2, "stone"),
    ("arm_r", 40, 14, 2, 13, 2, "arm"),
    ("arm_l", 48, 14, 2, 13, 2, "arm"),
    ("head", 0, 26, 7, 7, 7, "stone"),
    ("hand", 28, 26, 3, 4, 3, "gold"),
    ("finger", 40, 30, 1, 3, 1, "gold"),
    ("purse", 44, 30, 3, 3, 2, "gold"),
    ("crown", 0, 40, 8, 1, 8, "gold"),
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


def stone_pixel(rng, shade):
    return STONE[min(4, max(0, shade + rng.choice([-1, 0, 0, 0, 1])))]


def gold_pixel(rng, shade):
    return GOLD[min(4, max(0, shade + rng.choice([-1, 0, 0, 1])))]


def leaf_mask(rng, fw, fh, coverage):
    """Where the gold leaf is still stuck on: a few blotches, grown from random seeds."""
    mask = [[False] * fw for _ in range(fh)]
    for _ in range(max(1, int(fw * fh * coverage / 6))):
        x, y = rng.randrange(fw), rng.randrange(fh)
        for _ in range(rng.randint(3, 8)):
            if 0 <= x < fw and 0 <= y < fh:
                mask[y][x] = True
            x += rng.choice([-1, 0, 1])
            y += rng.choice([-1, 0, 1])
    return mask


def paint_face(img, rect, rng, style, face):
    x0, y0, fw, fh = rect
    base = {"top": 3, "bottom": 1}.get(face, 2)
    if style == "gold":
        for y in range(fh):
            for x in range(fw):
                c = gold_pixel(rng, (3 if face == "top" else 2) - (1 if y >= fh - 1 and fh > 2 else 0))
                if rng.random() < 0.12:
                    c = GOLD[0]  # tarnish
                img.putpixel((x0 + x, y0 + y), c + (255,))
        return
    # The robe kept the most gold near its hem, the arms near the hands; everywhere else it has flaked away
    coverage = {"robe": 0.35, "arm": 0.25}.get(style, 0.12)
    mask = leaf_mask(rng, fw, fh, coverage)
    for y in range(fh):
        for x in range(fw):
            c = stone_pixel(rng, base)
            if x == 0 or y == 0 or x == fw - 1 or y == fh - 1:
                c = STONE[max(0, base - 1)] if rng.random() < 0.5 else c
            if style == "robe" and face in ("front", "back", "east", "west") and x % 3 == 1 and y > 0:
                c = STONE[max(0, base - 1)]  # folds
            low = y / max(1, fh - 1)
            gilded = mask[y][x] and (style not in ("robe", "arm") or rng.random() < 0.35 + low * 0.65)
            if style == "robe" and face not in ("top", "bottom") and y >= fh - 2:
                gilded = rng.random() < 0.8  # the gilded hem
            if gilded:
                c = gold_pixel(rng, 2)
            img.putpixel((x0 + x, y0 + y), c + (255,))
    if fw * fh > 40:
        for _ in range(max(1, fw * fh // 90)):
            cx, cy = rng.randrange(fw), rng.randrange(fh)
            for _ in range(rng.randint(2, 5)):
                if 0 <= cx < fw and 0 <= cy < fh:
                    img.putpixel((x0 + cx, y0 + cy), CRACK + (255,))
                cx += rng.choice([-1, 0, 1])
                cy += 1


FACE = [
    # 7x7: blank brow under the crown, black sockets each with a gold pinprick (G), tarnish weeping from them,
    # and a thin smile cut far too wide
    "4444444",
    "3333333",
    "3KG3GK3",
    "32T3T23",
    "C2T3T2C",
    "3CCCCC3",
    "2333332",
]


def paint_face_front(img, eyes, rect, rng):
    x0, y0, _, _ = rect
    for y, row in enumerate(FACE):
        assert len(row) == 7, row
        for x, ch in enumerate(row):
            if ch in "234":
                c = stone_pixel(rng, int(ch))
            else:
                c = {"K": SOCKET, "G": EYE, "C": CRACK, "T": GOLD[0]}[ch]
            img.putpixel((x0 + x, y0 + y), c + (255,))
            if ch == "G":
                eyes.putpixel((x0 + x, y0 + y), EYE + (255,))


def statue():
    rng = random.Random(1849)  # the gold rush
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for name, u, v, w, h, d, style in BOXES:
        for face, rect in faces(u, v, w, h, d).items():
            paint_face(img, rect, rng, style, face)
        if name == "head":
            paint_face_front(img, eyes, faces(u, v, w, h, d)["front"], rng)
    return img, eyes


def block_barrier_icon():
    """18x18 like the vanilla effect icons: a little stone block under the barrier's red no-entry sign."""
    rng = random.Random(18)
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    for y in range(4, 15):
        for x in range(4, 15):
            edge = x in (4, 14) or y in (4, 14)
            c = (0x2a, 0x2a, 0x2a) if edge else stone_pixel(rng, 3 if y < 8 else 2)
            img.putpixel((x, y), c + (255,))
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
    tex, eyes = statue()
    tex.save(ROOT / "entity/greed_statue.png")
    eyes.save(ROOT / "entity/greed_statue_eyes.png")
    icon = block_barrier_icon()
    icon.save(ROOT / "mob_effect/block_barrier.png")

    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (64 * 6 * 2 + 24 + 18 * 6, 64 * 6 + 16), (110, 110, 110, 255))
        sheet.alpha_composite(tex.resize((384, 384), Image.NEAREST), (8, 8))
        sheet.alpha_composite(eyes.resize((384, 384), Image.NEAREST), (400, 8))
        sheet.alpha_composite(icon.resize((108, 108), Image.NEAREST), (792, 8))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
