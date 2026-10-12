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

All three mods add blocks, items or entities, so they must be installed on both the server and every client.
