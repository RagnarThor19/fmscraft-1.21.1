"""
Builds assets/fmscraft/video/memories.fmv, the memories video that plays after the True Ending credits, through a
hole blasted into the void.

The source (tools/video/source/memories.mp4, 720x1080) has its clips in the middle of a black frame. Only the
middle is ever seen, through a jagged hole of whole "blocks", so the script:
- crops to the block grid round the middle (CROP), scales it down and grades it like a memory: a little warmer and
  softer, with a faint glow (all baked in, so the game only has to show the frames);
- designs the hole: which cells of the grid are open, and when each one breaks away (centre first, out to the
  ragged rim). It's stored in the file, so the shape is decided here and previewed (--preview) before the game sees it;
- packs everything into one file the game streams frame by frame (see client/credits/CreditsVideo.java):

    "FMSV", int version, int frameWidth, int frameHeight, int fps*1000, int frameCount,
    int gridCols, int gridRows, byte[gridCols*gridRows] cells (0 = void, else the order it breaks in, 1..255),
    then per frame: int length, JPEG bytes          (big-endian ints)

The video's own sound isn't used: the credits music carries on under it.

Usage:  python tools/video/credits_video.py [source.mp4] [--preview]
Needs ffmpeg on the PATH, Pillow and numpy.
"""
import math
import random
import shutil
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools/video/source/memories.mp4"
OUT = ROOT / "src/main/resources/assets/fmscraft/video/memories.fmv"
PREVIEW = ROOT / "build/credits_video_preview.png"

# The part of the 720x1080 source behind the hole's grid: where the clips sit (measured over the whole video)
CELL = 54                               # source pixels a block: chunky, so the rim reads as blocks mined out
COLS, ROWS = 9, 14
CROP_W, CROP_H = COLS * CELL, ROWS * CELL
CROP_X, CROP_Y = 360 - CROP_W // 2, 535 - CROP_H // 2
SCALE = 0.8                             # 388x605 frames, shown at about 60% of the screen's height
FPS = 24
JPEG_QUALITY = 4                        # ffmpeg's -q:v (2 best .. 31 worst)

GRADE = ",".join([
    "eq=saturation=0.94:contrast=1.05:gamma=0.97",
    "colorbalance=rs=0.035:gs=0.01:bs=-0.035:rm=0.025:bm=-0.02:rh=0.02:bh=-0.01",
    # a soft glow, like an old memory
    "split[a][b];[b]gblur=sigma=9[g];[a][g]blend=all_mode=screen:all_opacity=0.2",
])

SEED = 7

# The crater, drawn by hand on the block grid (# open, . void): a blast a little off true, taller than wide like the
# clips, with stepped walls and a chipped rim. Change it and run with --shape to see it.
HOLE = """
....##...
..#####..
.######..
.#######.
########.
.########
.########
########.
.#######.
.#######.
..######.
..#####..
...###...
....#....
"""


def design_hole():
    """The open cells and the order they break in (1 first .. 255 last); 0 stays void."""
    rows = HOLE.strip().split()
    assert len(rows) == ROWS and all(len(r) == COLS for r in rows), "HOLE must be COLS x ROWS"
    open_ = np.array([[ch == "#" for ch in row] for row in rows])
    ys, xs = np.nonzero(open_)
    cy, cx = ys.mean(), xs.mean()
    # Breaking order: out from the middle with a little jitter, so it spreads like a blast, not a ring
    rnd = random.Random(SEED)
    keys = sorted((math.hypot((c - cx) / COLS, (r - cy) / ROWS) + rnd.uniform(0, 0.05), r, c) for r, c in zip(ys, xs))
    cells = np.zeros((ROWS, COLS), np.uint8)
    for i, (_, r, c) in enumerate(keys):
        cells[r, c] = 1 + round(254 * i / max(1, len(keys) - 1))
    return cells


def encode_frames(src, tmp):
    w, h = round(CROP_W * SCALE) // 2 * 2, round(CROP_H * SCALE)
    vf = f"fps={FPS},crop={CROP_W}:{CROP_H}:{CROP_X}:{CROP_Y},scale={w}:{h}:flags=lanczos,{GRADE}"
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-vf", vf,
                    "-q:v", str(JPEG_QUALITY), "-an", str(tmp / "%05d.jpg")], check=True)
    frames = sorted(tmp.glob("*.jpg"))
    return w, h, frames


def write(out, w, h, frames, cells):
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "wb") as f:
        f.write(b"FMSV")
        f.write(struct.pack(">iiiiiii", 1, w, h, FPS * 1000, len(frames), COLS, ROWS))
        f.write(cells.tobytes())
        for frame in frames:
            data = frame.read_bytes()
            f.write(struct.pack(">i", len(data)))
            f.write(data)


def preview(frame, cells, out):
    """Roughly what the game shows: the hole in the void with a frame behind it and the edges shaded."""
    W, H = 1920, 1080
    rnd = random.Random(1)
    img = Image.new("RGB", (W, H), (9, 14, 22))
    d = ImageDraw.Draw(img)
    for _ in range(2500):
        x, y = rnd.randrange(W), rnd.randrange(H)
        col = rnd.choice([(40, 90, 80), (60, 100, 120), (30, 60, 70), (90, 130, 140), (50, 40, 90)])
        s = rnd.choice([2, 3, 4])
        d.rectangle([x, y, x + s, y + s], fill=col)
    hole_h = H * 0.6
    cell = hole_h / ROWS
    hole_w = cell * COLS
    x0, y0 = (W - hole_w) / 2, (H - hole_h) / 2
    video = Image.open(frame).convert("RGB").resize((round(hole_w), round(hole_h)), Image.LANCZOS)
    mask = Image.new("L", video.size, 0)
    md = ImageDraw.Draw(mask)
    for r in range(ROWS):
        for c in range(COLS):
            if cells[r, c]:
                md.rectangle([c * cell, r * cell, (c + 1) * cell - 1, (r + 1) * cell - 1], fill=255)
    # inner shadow: the walls of the hole
    shade = Image.new("L", video.size, 0)
    sd = ImageDraw.Draw(shade)
    for r in range(ROWS):
        for c in range(COLS):
            if not cells[r, c]:
                continue
            def closed(rr, cc):
                return not (0 <= rr < ROWS and 0 <= cc < COLS and cells[rr, cc])
            px, py = c * cell, r * cell
            for side, depth in (("t", 0.55), ("l", 0.4), ("r", 0.4), ("b", 0.25)):
                rr, cc = {"t": (r - 1, c), "b": (r + 1, c), "l": (r, c - 1), "r": (r, c + 1)}[side]
                if not closed(rr, cc):
                    continue
                n = int(cell * depth)
                for k in range(n):
                    a = int(200 * (1 - k / n) ** 1.6)
                    if side == "t": sd.line([px, py + k, px + cell, py + k], fill=a)
                    if side == "b": sd.line([px, py + cell - 1 - k, px + cell, py + cell - 1 - k], fill=a)
                    if side == "l": sd.line([px + k, py, px + k, py + cell], fill=a)
                    if side == "r": sd.line([px + cell - 1 - k, py, px + cell - 1 - k, py + cell], fill=a)
    black = Image.new("RGB", video.size, (0, 0, 0))
    video = Image.composite(black, video, shade)
    img.paste(video, (round(x0), round(y0)), mask)
    # the credits' vignette
    vig = Image.new("L", (W, H), 0)
    ImageDraw.Draw(vig).ellipse([-W * 0.25, -H * 0.35, W * 1.25, H * 1.35], fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(220))
    img = Image.composite(img, Image.new("RGB", (W, H)), vig)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"preview: {out}")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--shape" in sys.argv:
        # Just the hole, over a few frames: quick to iterate on
        cells = design_hole()
        for r in range(ROWS):
            print("".join("#" if v else "." for v in cells[r]))
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            w, h = round(CROP_W * SCALE), round(CROP_H * SCALE)
            vf = f"crop={CROP_W}:{CROP_H}:{CROP_X}:{CROP_Y},scale={w}:{h}:flags=lanczos,{GRADE}"
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-ss", "33", "-i", str(SOURCE), "-vf", vf,
                            "-frames:v", "1", str(tmp / "f.png")], check=True)
            preview(tmp / "f.png", cells, PREVIEW)
        return
    src = Path(args[0]) if args else SOURCE
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg isn't on the PATH")
    cells = design_hole()
    for r in range(ROWS):
        print("".join("#" if v else "." for v in cells[r]))
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        w, h, frames = encode_frames(src, tmp)
        if "--preview" in sys.argv:
            preview(frames[len(frames) // 3], cells, PREVIEW)
        write(OUT, w, h, frames, cells)
    size = OUT.stat().st_size / 1e6
    print(f"{OUT.relative_to(ROOT)}: {len(frames)} frames of {w}x{h} at {FPS} fps "
          f"({len(frames) / FPS:.1f} s), {int((cells > 0).sum())} open cells, {size:.1f} MB")


if __name__ == "__main__":
    main()
