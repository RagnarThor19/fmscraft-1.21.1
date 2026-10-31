package net.ragnar.fmscraft.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/**
 * Sound events (assets/fmscraft/sounds.json). Most are made of vanilla sound files; the bosses' songs and voice lines
 * are the only files of our own.
 */
public class ModSounds {
    /** The Mother Warden snoring: a fox's sleepy snore, pitched right down, heard from anywhere in her hall. */
    public static final SoundEvent MOTHER_WARDEN_SNORE = register("entity.mother_warden.snore");
    /** RAGGIMEL's themes: Just The Way You Are (phase 1), Panzerknacker slowed (phase 2). Streamed, they're long. */
    public static final SoundEvent GREED_PHASE_ONE = register("music.greed.phase_one");
    public static final SoundEvent GREED_PHASE_TWO = register("music.greed.phase_two");
    /** What RAGGIMEL says in the dark between his two phases. */
    public static final SoundEvent RAGGIMEL_VOICELINE = register("entity.raggimel.voiceline");

    /** ORNAMIX's themes: N***as in Paris (phase 1), Return to Zero slowed (Ragemode). */
    public static final SoundEvent WRATH_PHASE_ONE = register("music.wrath.phase_one");
    public static final SoundEvent WRATH_PHASE_TWO = register("music.wrath.phase_two");
    /** "Ragemode": what ORNAMIX says in the dark before his second phase. */
    public static final SoundEvent ORNAMIX_RAGEMODE = register("entity.ornamix.ragemode");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(FmsCraft.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void initialize() {
    }
}
