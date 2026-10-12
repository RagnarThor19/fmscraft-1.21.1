"""
Generates the 16x16 item textures for sin fragments, keys and souls.

Usage:  python tools/textures/sin_textures.py [preview.png]
Needs Pillow (pip install pillow). Writes into src/main/resources/assets/fmscraft/textures/item/.
Pass a path to also write an enlarged preview sheet.

Keys share the vanilla trial key's shaft and teeth (rows 8-15) so they read as one family;
each sin gets its own head. Colors are dull and slightly weathered so everything feels ancient.
To recolor a sin, edit its RAMP (and the matching color in Sin.java) and re-run.
"""
import random
import sys
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures/item"

# Five tones per sin, darkest (outline) to lightest (highlight). Used by fragments, souls and keys.
RAMPS = {
    "greed":    ["1c2433", "34445e", "50678a", "7a8fae", "b4c0d2"],  # light navy blue
    "wrath":    ["2a1212", "4f2020", "7a3029", "a04a3c", "c98a78"],  # red
    "pride":    ["3a2f12", "6b5626", "9a7f3a", "c2a85a", "e2d4a0"],  # yellow
    "gluttony": ["10162a", "1e2a4a", "2e3f6b", "4a5d8a", "8592b4"],  # dark blue
    "envy":     ["141f17", "25392a", "3a5540", "587559", "8ea38c"],  # dark green
    "lust":     ["221a2c", "3e2f4f", "5c4672", "7f6894", "b3a2c2"],  # purple
    "sloth":    ["18191a", "2d2f30", "444748", "5f6263", "929595"],  # dark gray
}

# Shaft and teeth from the vanilla trial key. L/K dark, J/M mid, N/O light.
SHAFT = [
    "......KJJL......",
    "......KMJL......",
    "......KNML......",
    "......KOMJKK....",
    "......KOONNL....",
    "......KONLL.....",
    "......KNNNMK....",
    "......LLLLLL....",
]


def mix(a, b):
    return "".join("%02x" % ((int(a[i:i + 2], 16) + int(b[i:i + 2], 16)) // 2) for i in (0, 2, 4))


def shaft(r, tip=None):
    return {"L": r[0], "K": r[1], "J": r[2], "M": mix(r[2], r[3]), "N": r[3], "O": tip or r[4]}


def key_heads():
    g, w, p, gl, e, l, s = (RAMPS[k] for k in ("greed", "wrath", "pride", "gluttony", "envy", "lust", "sloth"))
    return {
        # old coin with a pale stone set in it
        "greed": ([
            ".....AAAAAA.....",
            "....ABCCCCDA....",
            "...ABCEEEECDA...",
            "...ACEFGGFEDA...",
            "...ACEGHGGEDA...",
            "...ADEFGGFEDA...",
            "....ADEEEEDA....",
            ".....AADDAA.....",
        ], {"A": g[0], "B": g[4], "C": g[3], "D": g[1], "E": g[2],
            "F": "3e5a63", "G": "7c9ea3", "H": "c3d4d0", **shaft(g)}),

        # horned blackstone head with a smoldering ember
        "wrath": ([
            "..AA........AA..",
            "..ABA......ABA..",
            "...ABA....ABA...",
            "...AHBAAAABHA...",
            "....ACEFFECA....",
            "....ADFGGFDA....",
            "....ACEFFECA....",
            ".....AACCAA.....",
        ], {"A": "161212", "B": w[3], "H": w[1], "C": "2e2828", "D": "4a4241",
            "E": w[1], "F": w[3], "G": w[4], **shaft(w)}),

        # tarnished crown over a cloudy mirror
        "pride": ([
            "....B..BB..B....",
            "....BBABBABB....",
            "....ABBCCBBA....",
            "...DDDDDDDDDD...",
            "...DEFGGGGFED...",
            "...DEGHIIHGED...",
            "....DEGHHGED....",
            ".....DDEEDD.....",
        ], {"A": p[2], "B": p[3], "C": "7a3a34", "D": p[0], "E": p[1], "F": p[2],
            "G": p[3], "H": p[4], "I": "f0ead2", **shaft(p)}),

        # a dark apple with a bite taken out of it
        "gluttony": ([
            "........AB......",
            ".....CCCACH.....",
            "....CDFEDDH.....",
            "...CDFEEDDDH....",
            "...CDEEDDDDGC...",
            "...CDEDDDDGGC...",
            "....CDDDDGGC....",
            ".....CCGGCC.....",
        ], {"A": "2b2620", "B": "4f6b45", "C": gl[0], "D": gl[2], "E": gl[3], "F": gl[4],
            "G": gl[1], "H": "b9c0cf", **shaft(gl)}),

        # a jealous green eye
        "envy": ([
            ".....AAAAAA.....",
            "...AABBBBBBAA...",
            "..ABBCCCCCCBBA..",
            ".ABBCDDEEDDCBBA.",
            ".ABBCDEFEEDCBBA.",
            "..ABBCDDDDCBBA..",
            "...AABBBBBBAA...",
            ".....AAAAAA.....",
        ], {"A": e[0], "B": "a3ad94", "C": e[1], "D": e[3], "E": "0c110d", "F": "d6dacb", **shaft(e)}),

        # a heart
        "lust": ([
            "...AAAA..AAAA...",
            "..ABCCBAABBBBA..",
            ".ABCDDCBBBBBBBA.",
            ".ABCDCBBBBBBBBA.",
            ".ABBCBBBBBBBBEA.",
            "..ABBBBBBBBBEA..",
            "...ABBBBBBBEA...",
            "....AABBBEAA....",
        ], {"A": l[0], "B": l[2], "C": l[3], "D": l[4], "E": l[1], **shaft(l)}),

        # a mossy stone face, fast asleep
        "sloth": ([
            ".....AAAAAA.....",
            "....ACCBCCBA....",
            "...ACEEBBBBBA...",
            "...ABEBBBBBBA...",
            "...ABDDBBDDBA...",
            "...ABBBBBBBFA...",
            "...ACBBBBBFCA...",
            "....ACAAAACA....",
        ], {"A": s[0], "B": s[2], "C": "4e5e38", "D": "101111", "E": s[3], "F": s[1], **shaft(s, tip="4e5e38")}),
    }


# A broken crystal shard with a bright facet edge. A outline, B dark, C mid, D light, E highlight.
FRAGMENT = [
    "................",
    "...........AA...",
    "..........ADEA..",
    ".........ADEEA..",
    "........ADEDCA..",
    ".......ADEDDCA..",
    "......ADEDDCBA..",
    ".....ADEDDCBBA..",
    "....ACDDDCBBA...",
    "...ACCDDCABA....",
    "..ACCCDCABA.....",
    "..ABCCCABBA.....",
    "..ABBCABBA......",
    "...AABAABA......",
    ".....A..A.......",
    "................",
]

# A soul wisp with a little face, like the ones in soul sand.
SOUL = [
    "................",
    ".........A......",
    "........ABA.....",
    "........ACA.....",
    ".......ACBA.....",
    "......ACDBA.....",
    ".....ACDDCBA....",
    "....ACDEEDCBA...",
    "....ACDEEEDCA...",
    "...ACDEEEEDCBA..",
    "...ACDAEEADCBA..",
    "...ABCDEEDCCBA..",
    "....ABCDDCCBA...",
    ".....ABBBBBA....",
    "......AAAAA.....",
    "................",
]


def rgba(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def from_grid(rows, palette):
    assert len(rows) == 16, len(rows)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(palette[ch]))
    return img


def weather(img, seed, amount=0.12, outline=None):
    """Darkens a few scattered pixels so the surface looks worn and old. Same seed -> same result."""
    rng = random.Random(seed)
    skip = rgba(outline) if outline else None
    for y in range(16):
        for x in range(16):
            px = img.getpixel((x, y))
            if px[3] == 0 or px == skip or rng.random() > amount:
                continue
            img.putpixel((x, y), tuple(int(c * 0.82) for c in px[:3]) + (255,))
    return img


def soul(colors):
    img = from_grid(SOUL, dict(zip("ABCDE", colors)))
    light, high = rgba(colors[3]), rgba(colors[4])
    for sx, sy in ((2, 4), (13, 12)):
        img.putpixel((sx, sy), high)
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        img.putpixel((12 + dx, 3 + dy), light if (dx or dy) else high)
    return img


def portal_frames(frames=32):
    """Grayscale swirling sheet for the sin portal, tinted per sin in-game. Stacked vertically for animation."""
    import math
    sheet = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        t = 2 * math.pi * f / frames
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = (math.sin(a * 2 + r * 0.9 - t) + math.sin(x * 0.7 + t) * 0.5 + math.cos(y * 0.6 - t * 2) * 0.5) / 2
                level = int(170 + 70 * v)  # stays light so the tint shows the sin's color
                alpha = int(190 + 40 * v)
                sheet.putpixel((x, f * 16 + y), (level, level, level, max(0, min(255, alpha))))
    return sheet


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    images = []
    for sin, (head, palette) in key_heads().items():
        ramp = RAMPS[sin]
        key = weather(from_grid(head + SHAFT, palette), seed=sin, outline=palette["A"])
        shard = weather(from_grid(FRAGMENT, dict(zip("ABCDE", ramp))), seed=sin + "_fragment",
                        amount=0.06, outline=ramp[0])
        orb = soul(ramp)
        key.save(OUT / f"{sin}_key.png")
        shard.save(OUT / f"{sin}_fragment.png")
        orb.save(OUT / f"soul_of_{sin}.png")
        images.append((shard, key, orb))

    block_dir = OUT.parent / "block"
    block_dir.mkdir(parents=True, exist_ok=True)
    portal_frames().save(block_dir / "sin_portal.png")
    (block_dir / "sin_portal.png.mcmeta").write_text('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')

    if len(sys.argv) > 1:
        scale, pad = 12, 8
        cell = 16 * scale + pad
        sheet = Image.new("RGBA", (3 * cell + pad, len(images) * cell + pad), (139, 139, 139, 255))
        for row, imgs in enumerate(images):
            for col, img in enumerate(imgs):
                sheet.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST),
                                      (pad + col * cell, pad + row * cell))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
