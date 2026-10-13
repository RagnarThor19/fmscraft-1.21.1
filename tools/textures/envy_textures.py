"""
Paints the Envy maze textures: the Statue of Envy (entity + glowing eyes layer) and the Envious Eye lock block.

Usage:  python tools/textures/envy_textures.py [preview.png]
The BOXES list must match the cuboids in EnvyStatueModel.java (uv offset and size).
"""
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures"

STONE = [(0x47, 0x4d, 0x45), (0x5c, 0x63, 0x59), (0x6e, 0x75, 0x6a), (0x7d, 0x84, 0x7a), (0x9a, 0xa2, 0x94)]
MOSS = [(0x4a, 0x60, 0x2c), (0x5f, 0x7a, 0x3a), (0x7a, 0x9a, 0x48)]
COPPER = [(0x24, 0x52, 0x46), (0x2f, 0x6b, 0x5a), (0x4f, 0x9a, 0x83), (0x6f, 0xc0, 0xa6)]
CRACK = (0x33, 0x38, 0x31)
GLOW = (0x8d, 0xff, 0x8a)
GLOW_DIM = (0x2c, 0x6a, 0x3c)

# name, u, v, w, h, d, style
BOXES = [
    ("plinth", 0, 0, 44, 6, 44, "plinth"),
    ("robe", 0, 50, 28, 20, 20, "robe"),
    ("chest", 96, 50, 24, 16, 16, "robe"),
    ("head", 176, 0, 14, 14, 14, "head"),
    ("ear_l", 176, 28, 2, 8, 5, "stone"),
    ("ear_r", 190, 28, 2, 8, 5, "stone"),
    ("arm", 176, 42, 6, 14, 6, "stone"),
    ("raised_arm", 200, 42, 6, 16, 6, "stone"),
    ("purse", 232, 0, 7, 7, 5, "copper"),
    ("flame", 232, 12, 6, 8, 6, "flame"),
    ("serpent_1", 176, 66, 3, 3, 6, "copper"),
    ("serpent_2", 194, 66, 3, 3, 3, "copper"),
    ("serpent_head", 206, 66, 4, 3, 5, "serpent_head"),
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
    i = min(4, max(0, shade + rng.choice([-1, 0, 0, 0, 1])))
    return STONE[i]


def paint_face(img, rect, rng, style, face):
    x0, y0, fw, fh = rect
    base = {"top": 3, "bottom": 1}.get(face, 2)
    for y in range(fh):
        for x in range(fw):
            if style in ("copper", "flame", "serpent_head"):
                c = COPPER[min(3, max(0, (3 if face == "top" else 2) + rng.choice([-1, 0, 0, 1]) - (1 if y > fh * 0.7 else 0)))]
            else:
                c = stone_pixel(rng, base)
                # Weathered edges
                if x == 0 or y == 0 or x == fw - 1 or y == fh - 1:
                    c = STONE[max(0, base - 1)] if rng.random() < 0.6 else c
                # Moss on top faces and creeping down from the top of the sides
                if face == "top" and rng.random() < 0.45:
                    c = rng.choice(MOSS)
                elif face not in ("top", "bottom") and y < 2 and rng.random() < (0.5 if y == 0 else 0.25):
                    c = rng.choice(MOSS)
                # Robe folds: darker vertical grooves
                if style == "robe" and face in ("front", "back", "east", "west") and x % 5 == 2 and y > 1:
                    c = STONE[1]
            img.putpixel((x0 + x, y0 + y), c + (255,))
    # Cracks on big faces
    if style != "copper" and fw * fh > 60:
        for _ in range(max(1, fw * fh // 160)):
            cx, cy = rng.randrange(fw), rng.randrange(fh)
            for _ in range(rng.randint(3, 7)):
                if 0 <= cx < fw and 0 <= cy < fh:
                    img.putpixel((x0 + cx, y0 + cy), CRACK + (255,))
                cx += rng.choice([-1, 0, 1])
                cy += 1


HEAD_FRONT = [
    # 14x14: hood, heavy brow, deep sockets (G = glowing eye), long nose, gaping mouth for the serpent
    "11111111111111",
    "12222222222221",
    "12333333333321",
    "1300000000003.",
    "13KKKK33KKKK31",
    "13KGGK33KGGK31",
    "13KKKK44KKKK31",
    "1333334433333.",
    "1333334433333.",
    "13333CCCC33331",
    "1333KKKKKK3331",
    "1333KKKKKK3331",
    "13C33333333C31",
    "11333333333311",
]


def paint_head_front(img, eyes, rect, rng):
    x0, y0, _, _ = rect
    shades = {"0": STONE[0], "1": STONE[1], "2": STONE[2], "3": STONE[3], "4": STONE[4],
              "K": (0x22, 0x26, 0x20), "C": CRACK, "G": GLOW, ".": STONE[2]}
    for y, row in enumerate(HEAD_FRONT):
        assert len(row) == 14, row
        for x, ch in enumerate(row):
            c = shades[ch]
            if ch == "3":
                c = stone_pixel(rng, 3)
            img.putpixel((x0 + x, y0 + y), c + (255,))
            if ch == "G":
                eyes.putpixel((x0 + x, y0 + y), GLOW + (255,))


def statue():
    rng = random.Random(1306)  # the year Giotto painted Invidia
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    for name, u, v, w, h, d, style in BOXES:
        for face, rect in faces(u, v, w, h, d).items():
            paint_face(img, rect, rng, style, face)
            if style == "flame":
                # Flame tips glow faintly; the top face and the upper rows of each side
                fx, fy, fw, fh = rect
                for y in range(fh if face == "top" else min(3, fh)):
                    for x in range(fw):
                        if face == "top" or rng.random() < 0.6 - y * 0.15:
                            eyes.putpixel((fx + x, fy + y), GLOW_DIM + (255,))
        if name == "head":
            paint_head_front(img, eyes, faces(u, v, w, h, d)["front"], rng)
        if style == "serpent_head":
            fx, fy, fw, fh = faces(u, v, w, h, d)["front"]
            for ex in (fx, fx + fw - 1):
                img.putpixel((ex, fy + 1), GLOW + (255,))
                eyes.putpixel((ex, fy + 1), GLOW + (255,))
    return img, eyes


# ---- Envious Eye block ----

OPEN_EYE = [
    ".....KKKKKK.....",
    "...KKWWWWWWKK...",
    "..KWWGGGGGGWWK..",
    ".KWWGGDPPDGGWWK.",
    ".KWWGGDPPHGGWWK.",
    "..KWWGGGGGGWWK..",
    "...KKWWWWWWKK...",
    ".....KKKKKK.....",
]
CLOSED_EYE = [
    "................",
    "................",
    ".KK..........KK.",
    "..KKK......KKK..",
    "....KKKKKKKK....",
    "....K.K..K.K....",
    "................",
    "................",
]


def block_base(rng, copper_band=False):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = stone_pixel(rng, 2)
            if x in (0, 15) or y in (0, 15):
                c = STONE[1]
            elif rng.random() < 0.12:
                c = rng.choice(MOSS)
            if copper_band and y in (7, 8) and 0 < x < 15:
                c = COPPER[2 if y == 7 else 1]
            img.putpixel((x, y), c + (255,))
    return img


def eye_front(rng, lit):
    img = block_base(rng)
    pal = {"K": (0x1e, 0x22, 0x1c), "W": (0xc8, 0xd6, 0xb8), "G": (0x3a, 0xb0, 0x4c), "D": (0x1f, 0x6a, 0x2c),
           "P": (0x0a, 0x10, 0x0a), "H": (0xe8, 0xff, 0xe0)}
    if lit:
        pal.update({"W": (0xd8, 0xf0, 0xc8), "G": (0x6d, 0xf0, 0x6a), "D": (0x2f, 0xa0, 0x3c)})
    rows = OPEN_EYE if lit else CLOSED_EYE
    for y, row in enumerate(rows):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y + 4), pal[ch] + (255,))
    return img


def eye_top(rng):
    img = block_base(rng)
    for y in range(4, 12):
        for x in range(4, 12):
            if x in (4, 11) or y in (4, 11):
                img.putpixel((x, y), COPPER[2] + (255,))
    img.putpixel((7, 7), COPPER[3] + (255,))
    img.putpixel((8, 8), COPPER[1] + (255,))
    return img


def main():
    (ROOT / "entity").mkdir(parents=True, exist_ok=True)
    (ROOT / "block").mkdir(parents=True, exist_ok=True)
    tex, eyes = statue()
    tex.save(ROOT / "entity/envy_statue.png")
    eyes.save(ROOT / "entity/envy_statue_eyes.png")

    rng = random.Random(77)
    front, front_lit = eye_front(rng, False), eye_front(rng, True)
    side, top = block_base(rng, copper_band=True), eye_top(rng)
    front.save(ROOT / "block/envious_eye_front.png")
    front_lit.save(ROOT / "block/envious_eye_front_lit.png")
    side.save(ROOT / "block/envious_eye_side.png")
    top.save(ROOT / "block/envious_eye_top.png")

    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (256 * 3 + 16, 128 * 3 * 2 + 16 + 16 * 10), (110, 110, 110, 255))
        sheet.alpha_composite(tex.resize((768, 384), Image.NEAREST), (8, 8))
        sheet.alpha_composite(eyes.resize((768, 384), Image.NEAREST), (8, 400))
        for i, b in enumerate((front, front_lit, side, top)):
            sheet.alpha_composite(b.resize((160, 160), Image.NEAREST), (8 + i * 170, 792))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
