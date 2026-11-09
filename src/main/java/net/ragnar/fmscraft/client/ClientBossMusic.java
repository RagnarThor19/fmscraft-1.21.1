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
 * A song can fade in instead of starting at full volume. How far into the song we are is counted on the sound's own
 * ticks (which stop while the game is paused, like the sound does), for effects that keep time with it.
 */
public final class ClientBossMusic {
    private static Song current;
    private static String currentId = "";
    /** A boss turning the song down (and back up) without losing its place: GUMMSS's Covet. 1 is as normal. */
    private static float duck = 1f;

    private ClientBossMusic() {
    }

    public static void play(String soundId) {
        play(soundId, 0);
    }

    public static void play(String soundId, int fadeTicks) {
        if (soundId.isEmpty() && fadeTicks > 0 && current != null) {
            // Fade out what's playing, then stop it (it keeps its place in time while it fades)
            current.fadeOut(fadeTicks);
            return;
        }
        stop();
        if (soundId.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.getMusicTracker().stop();
        current = new Song(SoundEvent.of(Identifier.of(soundId)), fadeTicks);
        currentId = soundId;
        client.getSoundManager().play(current);
    }

    public static void stop() {
        if (current != null) {
            MinecraftClient.getInstance().getSoundManager().stop(current);
            current = null;
        }
        currentId = "";
    }

    /** Turns whatever's playing down to {@code volume} (0-1) of itself, keeping time; 1 to put it back. */
    public static void duck(float volume) {
        duck = Math.max(0f, Math.min(1f, volume));
    }

    public static float duck() {
        return duck;
    }

    public static boolean isPlaying() {
        return current != null;
    }

    /** Seconds since {@code soundId} started (counting on past its end as it loops), or -1 if it isn't the one playing. */
    public static double secondsInto(String soundId, float tickDelta) {
        if (current == null || !currentId.equals(soundId)) return -1;
        return (current.age + tickDelta) / 20.0;
    }

    /** A looping, non-positional song that ramps its volume up over its first {@code fadeTicks}. */
    private static final class Song extends MovingSoundInstance {
        private final int fadeTicks;
        private int age, fadeOut, fadeOutLeft;
        private float fadeFrom, level;

        Song(SoundEvent sound, int fadeTicks) {
            super(sound, SoundCategory.RECORDS, SoundInstance.createRandom());
            this.fadeTicks = fadeTicks;
            this.repeat = true;
            this.repeatDelay = 0;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
            this.level = fadeTicks > 0 ? 0.01f : 1.0f;
            this.volume = level * Math.max(0.0001f, duck);
        }

        @Override
        public boolean shouldAlwaysPlay() {
            return true;
        }

        void fadeOut(int ticks) {
            fadeOut = fadeOutLeft = ticks;
            fadeFrom = level;
        }

        @Override
        public void tick() {
            age++;
            if (fadeOut > 0) {
                level = Math.max(0.0f, fadeFrom * --fadeOutLeft / (float) fadeOut);
                volume = level * duck;
                if (fadeOutLeft <= 0) {
                    setDone();
                    if (current == this) {
                        current = null;
                        currentId = "";
                    }
                }
                return;
            }
            if (fadeTicks > 0) level = Math.max(0.01f, Math.min(1.0f, age / (float) fadeTicks));
            // Never quite 0, so the sound engine keeps it playing (and in time) while it's turned all the way down
            volume = level * Math.max(0.0001f, duck);
        }
    }
}
