package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

/**
 * Plays boss themes on the client. Songs loop and play on the Jukebox/Note Blocks volume slider
 * (so players who mute background music still hear them); vanilla music is paused while one plays.
 */
public final class ClientBossMusic {
    private static SoundInstance current;

    private ClientBossMusic() {
    }

    public static void play(String soundId) {
        stop();
        if (soundId.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.getMusicTracker().stop();
        current = new PositionedSoundInstance(Identifier.of(soundId), SoundCategory.RECORDS, 1.0f, 1.0f,
                SoundInstance.createRandom(), true, 0, SoundInstance.AttenuationType.NONE, 0, 0, 0, true);
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
}
