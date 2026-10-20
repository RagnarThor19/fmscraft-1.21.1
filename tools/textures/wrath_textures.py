"""
Paints the Mother Warden: the vanilla warden's five textures, read straight out of the Minecraft client jar, with every
blue and teal turned to the red of Wrath. Her hide goes from blue-black to a dark blood maroon, the glowing spots,
heart and tendrils from cyan to ember red, and her pale ribs and face keep their colour but warm a little, like old
bone. Nothing else changes, so she's unmistakably a warden, just the wrong colour.

Usage:  python tools/textures/wrath_textures.py [preview.png]
The jar is found in Loom's cache (~/.gradle/caches/fabric-loom/1.21.1/minecraft-client.jar); run any gradle task once
if it isn't there yet.
"""
import colorsys
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/fmscraft/textures/entity/mother_warden"
JAR = Path.home() / ".gradle/caches/fabric-loom/1.21.1/minecraft-client.jar"
NAMES = ["warden", "warden_bioluminescent_layer", "warden_heart", "warden_pulsating_spots_1", "warden_pulsating_spots_2"]

# Hues (degrees) counted as the warden's blue: everything from teal round to deep blue
BLUE_FROM, BLUE_TO = 150, 250
# Where they land: just shy of pure red, the brighter ones leaning toward ember orange
RED = 356


def recolor(rgba):
    r, g, b, a = rgba
    if a == 0:
        return rgba
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    deg = h * 360
    if BLUE_FROM <= deg <= BLUE_TO or (s < 0.45 and v < 0.2):
        # The glow (bright and saturated) warms toward orange; the hide stays a deep red
        hue = (RED + 10 * v) % 360
        s = min(1.0, s * 1.1 + 0.08)
        # Red reads darker than cyan at the same value, so lift the glowing parts a touch
        v = min(1.0, v * (1.12 if v > 0.4 else 1.0))
        r2, g2, b2 = colorsys.hsv_to_rgb(hue / 360, s, v)
    elif s < 0.35 and v > 0.4:
        # Ribs and face: the same bone, a little warmer and ashier
        hue = 40 + (deg - 40) * 0.4
        r2, g2, b2 = colorsys.hsv_to_rgb((hue % 360) / 360, s * 0.9, v * 0.97)
    else:
        return rgba
    return round(r2 * 255), round(g2 * 255), round(b2 * 255), a


def main():
    if not JAR.exists():
        sys.exit(f"Minecraft client jar not found at {JAR}")
    ROOT.mkdir(parents=True, exist_ok=True)
    out = {}
    with zipfile.ZipFile(JAR) as jar:
        for name in NAMES:
            image = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/entity/warden/{name}.png"))).convert("RGBA")
            pixels = image.get_flattened_data() if hasattr(image, "get_flattened_data") else image.getdata()
            image.putdata([recolor(p) for p in pixels])
            image.save(ROOT / f"{name.replace('warden', 'mother_warden', 1)}.png")
            out[name] = image
    if len(sys.argv) > 1:
        # The body with every glowing layer on top, 4x, for a quick look
        preview = out["warden"].copy()
        for name in NAMES[1:]:
            preview.alpha_composite(out[name])
        preview.resize((512, 512), Image.NEAREST).save(sys.argv[1])
    print("wrote", ", ".join(NAMES), "to", ROOT)


if __name__ == "__main__":
    main()
