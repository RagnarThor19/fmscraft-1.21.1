"""
Makes the Mother Warden's snore: the vanilla fox's five sleeping breaths, read from the game's own sound assets, slowed
down so they come out far deeper and longer than the game itself can play them (it won't pitch a sound below 0.5).
Each one is mixed to mono, so it plays from her, with a low rumble of itself an octave under mixed back in, made
louder (the fox breathes quietly) and faded out at the end.

Usage:  python tools/sounds/wrath_sounds.py
Needs ffmpeg (with libvorbis) on the PATH, or its path in the FFMPEG environment variable. The sounds are found in
Loom's asset cache (~/.gradle/caches/fabric-loom/assets); run any gradle task once if they aren't there yet.
"""
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/sounds/mob/mother_warden"
ASSETS = Path.home() / ".gradle/caches/fabric-loom/assets"
INDEX = ASSETS / "indexes/1.21.1-17.json"
# How much slower (and so deeper) than the fox; the game plays it at pitch ~0.7 on top of this
SLOW = 0.42
RATE = 44100


def main():
    ffmpeg = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if not ffmpeg:
        sys.exit("ffmpeg not found: put it on the PATH or set FFMPEG")
    if not INDEX.exists():
        sys.exit(f"asset index not found at {INDEX}")
    objects = json.loads(INDEX.read_text())["objects"]
    ROOT.mkdir(parents=True, exist_ok=True)
    for i in range(1, 6):
        digest = objects[f"minecraft/sounds/mob/fox/sleep{i}.ogg"]["hash"]
        source = ASSETS / "objects" / digest[:2] / digest
        out = ROOT / f"snore{i}.ogg"
        # Slow it right down by playing it at a lower rate, and lay the same breath an octave lower under it
        graph = (f"[0:a]aformat=channel_layouts=mono,aresample={RATE},asplit[a][b];"
                 f"[a]asetrate={RATE * SLOW:.0f},aresample={RATE}[deep];"
                 f"[b]asetrate={RATE * SLOW / 2:.0f},aresample={RATE},volume=0.6[rumble];"
                 f"[deep][rumble]amix=inputs=2:duration=first:normalize=0,lowpass=f=2400,volume=15dB,alimiter=limit=0.95,areverse,afade=t=in:d=0.4,areverse")
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", str(source), "-filter_complex", graph,
                        "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", str(out)], check=True)
    print("wrote snore1-5 to", ROOT)


if __name__ == "__main__":
    main()
