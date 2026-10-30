package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Plays boss themes on the client. Songs loop and play on the Jukebox/Note Blocks volume slider
 * (so players who mute background music still hear them); vanilla music is paused while one plays.
 * A song can fade in instead of starting at full volume.
 */
public final class ClientBossMusic {
    private static Song current;

    private ClientBossMusic() {
    }

    public static void play(String soundId) {
        play(soundId, 0);
    }

    public static void play(String soundId, int fadeTicks) {
        stop();
        if (soundId.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.getMusicTracker().stop();
        current = new Song(SoundEvent.of(Identifier.of(soundId)), fadeTicks);
        client.getSoundManager().play(current);
    }

    public static void stop() {
        if (current != null) {
            MinecraftClient.getInstance().getSoundManager().stop(current);
            current = null;
        }
    }

    public static boolean isPlaying() {
        return current != null;
    }

    /** A looping, non-positional song that ramps its volume up over its first {@code fadeTicks}. */
    private static final class Song extends MovingSoundInstance {
        private final int fadeTicks;
        private int age;

        Song(SoundEvent sound, int fadeTicks) {
            super(sound, SoundCategory.RECORDS, SoundInstance.createRandom());
            this.fadeTicks = fadeTicks;
            this.repeat = true;
            this.repeatDelay = 0;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
            this.volume = fadeTicks > 0 ? 0.01f : 1.0f;
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            if (fadeTicks > 0) volume = Math.max(0.01f, Math.min(1.0f, age / (float) fadeTicks));
        }
    }
}
