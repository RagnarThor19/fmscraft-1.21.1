package net.ragnar.fmscraft.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;

/** Sound events. Each is made of vanilla sound files (see assets/fmscraft/sounds.json), so nothing new to download. */
public class ModSounds {
    /** The Mother Warden snoring: a fox's sleepy snore, pitched right down, heard from anywhere in her hall. */
    public static final SoundEvent MOTHER_WARDEN_SNORE = register("entity.mother_warden.snore");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(FmsCraft.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void initialize() {
    }
}
