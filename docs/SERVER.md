# Running the server with all three mods

## Testing in dev

`gradlew runClient` / `gradlew runServer` automatically load the twilight and magic mods next to this one
(jar paths are in `gradle.properties`). After changing one of the other mods, run `gradlew build` in that project
first so its jar is up to date. If you bump a mod's version, update the path in `gradle.properties`.

## Real server

1. In each of the other two projects, run `gradlew build`.
2. Here, run `gradlew build prepareServer`. Everything the server needs ends up in `build/server-mods/`:
   - `fmscraft-<version>.jar`
   - `ragnarstwilightdimension-<version>.jar`
   - `ragnarsmagicmod-<version>.jar`
   - `fabric-api-<version>.jar`
3. Download the Fabric server launcher for Minecraft **1.21.1** from https://fabricmc.net/use/server/ into an empty folder.
4. Run it once (`java -Xmx4G -jar fabric-server-launch.jar nogui`), accept the EULA in `eula.txt`, then copy the jars
   from step 2 into its `mods/` folder and start it again.
5. Every player needs the **same four jars** in their own `.minecraft/mods` folder, with Fabric Loader for 1.21.1.

## Release (hosted server + Modrinth pack)

1. Build all three mods as above (`gradlew build` in the other two, `gradlew build prepareServer` here).
2. `python tools/release/make_mrpack.py` puts everything in `build/release/`:
   - `server-mods/`: the four jars for the server's `mods` folder.
   - `FMSCraft-<version>.mrpack`: the pack for players. Fabric API comes from Modrinth; our three jars are inside it.
3. Host (e.g. ApexHosting): pick **Fabric**, Minecraft **1.21.1**, Fabric Loader **0.19.3 or newer**, then upload the
   four jars from `server-mods/` into `mods/` and restart. The first start of a new world builds the arenas and the
   True Ending shrine (about a minute, "Can't keep up" in the log is expected then); later starts are quick.
4. Players: Modrinth App, **+** (create instance), **Import from file**, pick the `.mrpack`, then Play. It installs
   Minecraft 1.21.1, Fabric and all four mods. After an update, import the new pack again.

All three mods add blocks, items or entities, so they must be installed on both the server and every client.
