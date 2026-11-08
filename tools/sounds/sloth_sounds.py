"""
Makes RICKY's music and sounds from the files he was given (C:/Users/Notandi/for boss fights/Ricky by default):

- music/sloth_phase_one.ogg: Ken Carson - overseas (Instrumental), with the 4.7 s of silence at its end cut (it ends at
  140.26 s), so the loop doesn't gap. Stereo, as it came.
- music/sloth_phase_two.ogg: Chief Keef - Kills (slowed to perfection + reverb + bass boosted), as it came.
- mob/ricky/powerup.ogg: the Super Saiyan power-up, mono (so it plays from him), made louder (it came in quiet) with a
  limiter so it doesn't clip.
- mob/ricky/teleport.ogg: the DBZ teleport, mono, louder, the 0.75 s of silence before it and the long silence after cut.

Usage:  python tools/sounds/sloth_sounds.py [folder with the source files]
Needs ffmpeg (with libvorbis) on the PATH, or its path in the FFMPEG environment variable.
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/sounds"
SOURCE = Path.home() / "for boss fights/Ricky"
OVERSEAS = "overseas (Instrumental) - Ken Carson (prod. Lukrative, Lucian, Esko & Nick Spiders).ogg"
KILLS = "chief keef - kills (slowed to perfection + reverb + bass boosted).ogg"
POWERUP = "Anime Powerup (Dragonball becoming Super Saiyan) - Sound Effect for editing.ogg"
TELEPORT = "Teleport DBZ - Sound Effect HD.ogg"


def main():
    ffmpeg = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if not ffmpeg:
        sys.exit("ffmpeg not found: put it on the PATH or set FFMPEG")
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else SOURCE

    def run(name, out, *args):
        out = ASSETS / out
        out.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", str(src / name), *args, "-c:a", "libvorbis", str(out)], check=True)
        print("wrote", out.relative_to(ASSETS.parents[3]))

    run(OVERSEAS, "music/sloth_phase_one.ogg", "-t", "140.30", "-q:a", "6")
    run(KILLS, "music/sloth_phase_two.ogg", "-q:a", "6")
    run(POWERUP, "mob/ricky/powerup.ogg", "-ac", "1", "-af", "volume=14dB,alimiter=limit=0.95", "-q:a", "5")
    run(TELEPORT, "mob/ricky/teleport.ogg", "-ac", "1", "-ss", "0.75", "-t", "1.15", "-af", "volume=6dB,alimiter=limit=0.95,afade=t=out:st=1.0:d=0.15", "-q:a", "5")


if __name__ == "__main__":
    main()
