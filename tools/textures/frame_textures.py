"""
Paints the Sin Portal Frame: the key lock shared by every sin's structure. It is built on the vanilla end portal
frame (same end stone body, same 13-pixel height) but with a keyhole socket instead of the eye socket, an
obsidian-dark band instead of the teal one, and a greyscale trim layer that the game tints in each sin's color.

Usage:  python tools/textures/frame_textures.py [preview.png]
Writes  block/sin_portal_frame_{top,side}.png        (the body)
        block/sin_portal_frame_{top,side}_trim.png   (greyscale overlay, tinted per sin)
"""
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures/block"

# End stone, light to dark (the same family of tones the vanilla frame uses)
END_STONE = [(0xF0, 0xF4, 0xBA), (0xE4, 0xE7, 0xAC), (0xD8, 0xDA, 0x9A), (0xCB, 0xC8, 0x8C), (0xBC, 0xAD, 0x78)]
# Obsidian-dark socket and band, with a faint purple cast
DARK = [(0x0E, 0x0A, 0x16), (0x18, 0x12, 0x24), (0x26, 0x1D, 0x36), (0x36, 0x2A, 0x4A)]
# Trim greyscale: multiplied by the sin's color in game
TRIM = [(0x50, 0x50, 0x50), (0x7C, 0x7C, 0x7C), (0xA8, 0xA8, 0xA8), (0xD8, 0xD8, 0xD8), (0xFF, 0xFF, 0xFF)]
CLEAR = (0, 0, 0, 0)


def end_stone(rng, img, x, y, bias=0):
    i = min(4, max(0, rng.choice([0, 1, 1, 2, 2, 3]) + bias))
    img.putpixel((x, y), END_STONE[i] + (255,))


def keyhole(x, y):
    """The socket: a round hole over a tapering slot, like an old lock."""
    dx, dy = x - 7.5, y - 5.5
    if dx * dx + dy * dy <= 2.6 ** 2:
        return True
    return 7 <= y <= 11 and abs(x - 7.5) <= (1.5 if y < 10 else 2.5)


def top():
    rng = random.Random(11)
    body = Image.new("RGBA", (16, 16))
    trim = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if keyhole(x, y):
                # Deeper toward the middle; the lower-right lip catches a little light
                lip = not keyhole(x - 1, y) or not keyhole(x, y - 1)
                body.putpixel((x, y), (DARK[0] if not lip else DARK[2]) + (255,))
            elif any(keyhole(x + ox, y + oy) for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                body.putpixel((x, y), DARK[3] + (255,))  # rim around the socket
            else:
                end_stone(rng, body, x, y, bias=1 if edge <= 2 else 0)
            if edge <= 1:
                body.putpixel((x, y), DARK[1] + (255,))
                # Border trim: bright corners and studs, a darker inner line
                corner = (x <= 1 or x >= 14) and (y <= 1 or y >= 14)
                stud = edge == 0 and (x in (5, 10) or y in (5, 10))
                shade = 4 if corner else 3 if stud else 2 if edge == 0 else 1
                trim.putpixel((x, y), TRIM[shade] + (255,))
    # Four small gems set in the ring, on the diagonals
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        body.putpixel((x, y), DARK[1] + (255,))
        trim.putpixel((x, y), TRIM[4] + (255,))
    return body, trim


def side():
    rng = random.Random(12)
    body = Image.new("RGBA", (16, 16))
    trim = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            if y < 3:
                body.putpixel((x, y), CLEAR)  # above the 13-pixel body, never shown
            elif y <= 6:
                body.putpixel((x, y), DARK[1] + (255,))
                inset = y in (4, 5) and 3 <= x <= 12
                if inset:
                    end_stone(rng, body, x, y, bias=1)  # the stone panel set into the band
                else:
                    stud = y == 4 and x in (1, 14) or y == 5 and x in (1, 14)
                    shade = 4 if stud else 3 if y == 3 else 1 if y == 6 else 2
                    trim.putpixel((x, y), TRIM[shade] + (255,))
            elif y == 7:
                body.putpixel((x, y), DARK[3] + (255,) if x % 5 else END_STONE[4] + (255,))
            else:
                end_stone(rng, body, x, y)
    return body, trim


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    top_body, top_trim = top()
    side_body, side_trim = side()
    top_body.save(ROOT / "sin_portal_frame_top.png")
    top_trim.save(ROOT / "sin_portal_frame_top_trim.png")
    side_body.save(ROOT / "sin_portal_frame_side.png")
    side_trim.save(ROOT / "sin_portal_frame_side_trim.png")

    if len(sys.argv) > 1:
        # Preview: body, trim, and the two composited in a few sin colors
        tints = [(0xC2, 0xA8, 0x5A), (0x58, 0x75, 0x59), (0xA0, 0x4A, 0x3C)]
        sheet = Image.new("RGBA", (16 * 2 + 16 * len(tints), 32), (255, 255, 255, 255))
        for row, (body, trim) in enumerate(((top_body, top_trim), (side_body, side_trim))):
            sheet.paste(body, (0, row * 16), body)
            sheet.paste(trim, (16, row * 16), trim)
            for i, tint in enumerate(tints):
                tinted = trim.copy()
                px = tinted.load()
                for yy in range(16):
                    for xx in range(16):
                        r, g, b, a = px[xx, yy]
                        px[xx, yy] = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a)
                tile = body.copy()
                tile.alpha_composite(tinted)
                sheet.paste(tile, (32 + i * 16, row * 16), tile)
        sheet.resize((sheet.width * 12, sheet.height * 12), Image.NEAREST).save(sys.argv[1])


if __name__ == "__main__":
    main()
