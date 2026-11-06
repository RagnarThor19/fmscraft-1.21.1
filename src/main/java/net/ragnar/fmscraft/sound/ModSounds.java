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

    /** GUMMSS's theme: Seekae - Test & Recognise (Flume re-work, slowed). The bass drops at 21.575 s, as everyone lands. */
    public static final SoundEvent ENVY_THEME = register("music.envy.theme");

    /**
     * LalliLarge's themes: Travis Scott - Apple Pie (slowed + reverb) over the Earth, whose chord strums turn the sky;
     * then SIRENS, whose switch-up at 26 s takes the fight to the snowfield.
     */
    public static final SoundEvent GLUTTONY_PHASE_ONE = register("music.gluttony.phase_one");
    public static final SoundEvent GLUTTONY_PHASE_TWO = register("music.gluttony.phase_two");

    /**
     * MUGEN's themes: YEAT - COMË N GO with its reversed intro (his whole entrance is those 35 seconds), then Playboi
     * Carti ft. Skepta - Toxic with its reversed intro, which drops at 15.6 s as he lands in the eclipse.
     */
    public static final SoundEvent LUST_PHASE_ONE = register("music.lust.phase_one");
    public static final SoundEvent LUST_PHASE_TWO = register("music.lust.phase_two");

    /**
     * LEONARD's themes: Wacka Flacka Flame - Grove St. Party (hood trap, slowed), which starts after ten seconds of
     * silence and drops at 8.37 s as he strikes down; then an epic orchestra and choir, fading in as he becomes CONEMAN.
     */
    public static final SoundEvent PRIDE_PHASE_ONE = register("music.pride.phase_one");
    public static final SoundEvent PRIDE_PHASE_TWO = register("music.pride.phase_two");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(FmsCraft.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void initialize() {
    }
}
