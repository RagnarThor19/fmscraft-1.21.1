package net.ragnar.fmscraft.sin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;

/**
 * The seven sins. Everything per-sin (items, advancements, bosses, music, structures) is keyed off this enum,
 * so adding data for a sin means adding it here first.
 */
public enum Sin implements StringIdentifiable {
    GREED("greed", "Greed", "Ragnar Thor", SinWorlds.TWILIGHT, 0x7A8FAE),
    WRATH("wrath", "Wrath", "Aron Eggert", World.NETHER, 0xA04A3C),
    PRIDE("pride", "Pride", "Leo Gunnlaugsson", World.OVERWORLD, 0xC2A85A),
    GLUTTONY("gluttony", "Gluttony", "Larus Armann", World.NETHER, 0x4A5D8A),
    ENVY("envy", "Envy", "Gudmundur Sigurdsson", World.OVERWORLD, 0x587559),
    LUST("lust", "Lust", "Karl Phillip", SinWorlds.TWILIGHT, 0x7F6894),
    SLOTH("sloth", "Sloth", "Patrik Atanaia", World.END, 0x5F6263);

    private final String id;
    private final String displayName;
    private final String bossName;
    private final RegistryKey<World> portalWorld;
    private final int color;

    Sin(String id, String displayName, String bossName, RegistryKey<World> portalWorld, int color) {
        this.id = id;
        this.displayName = displayName;
        this.bossName = bossName;
        this.portalWorld = portalWorld;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The friend this sin's boss is named after. */
    public String bossName() {
        return bossName;
    }

    /** The dimension the portal structure for this sin generates in. */
    public RegistryKey<World> portalWorld() {
        return portalWorld;
    }

    /** Signature color (0xRRGGBB) for particles. Matches the "light" tone of the sin's ramp in tools/textures/sin_textures.py. */
    public int color() {
        return color;
    }

    @Override
    public String asString() {
        return id;
    }

    public static Sin byId(String id) {
        for (Sin sin : values()) {
            if (sin.id.equals(id)) return sin;
        }
        return null;
    }

    /** World keys from other mods, referenced by id only so they aren't needed on the compile classpath. */
    public static final class SinWorlds {
        public static final RegistryKey<World> TWILIGHT =
                RegistryKey.of(RegistryKeys.WORLD, Identifier.of("ragnarstwilightdimension", "twilight"));

        private SinWorlds() {
        }
    }
}
