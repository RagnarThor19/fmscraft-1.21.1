"""Builds build/release: the server's mods folder and a Modrinth pack (.mrpack) for players. Run after `gradlew build prepareServer`.

If Fabric API's version changes, update FABRIC_API and FABRIC_API_URL (look the jar up at api.modrinth.com/v2/version_file/<sha1>).
"""
import hashlib
import json
import shutil
import sys
import zipfile
from pathlib import Path

project = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
mods = project / "build" / "server-mods"
release = project / "build" / "release"
FABRIC_API = "fabric-api-0.116.15+1.21.1.jar"
FABRIC_API_URL = "https://cdn.modrinth.com/data/P7dR8mSH/versions/Nlt8gI9z/fabric-api-0.116.15%2B1.21.1.jar"

if release.exists():
    shutil.rmtree(release)
(release / "server-mods").mkdir(parents=True)

jars = sorted(mods.glob("*.jar"))
assert {j.name for j in jars} >= {FABRIC_API}, jars
for jar in jars:
    shutil.copy2(jar, release / "server-mods" / jar.name)


def digest(path, algorithm):
    h = hashlib.new(algorithm)
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


api = mods / FABRIC_API
index = {
    "formatVersion": 1,
    "game": "minecraft",
    "versionId": "1.0.0",
    "name": "FMSCraft",
    "summary": "The Seven Deadly Sins, with Ragnar's Twilight Dimension and Ragnar's Magic Mod.",
    "files": [{
        "path": "mods/" + FABRIC_API,
        "hashes": {"sha1": digest(api, "sha1"), "sha512": digest(api, "sha512")},
        "env": {"client": "required", "server": "required"},
        "downloads": [FABRIC_API_URL],
        "fileSize": api.stat().st_size,
    }],
    "dependencies": {"minecraft": "1.21.1", "fabric-loader": "0.19.3"},
}

pack = release / "FMSCraft-1.0.0.mrpack"
with zipfile.ZipFile(pack, "w") as z:
    z.writestr("modrinth.index.json", json.dumps(index, indent=2), compress_type=zipfile.ZIP_DEFLATED)
    for jar in jars:
        if jar.name != FABRIC_API:
            z.write(jar, "overrides/mods/" + jar.name, compress_type=zipfile.ZIP_STORED)

with zipfile.ZipFile(pack) as z:
    for info in z.infolist():
        print(f"{info.file_size:>12}  {info.filename}")
print("pack", pack.stat().st_size)
