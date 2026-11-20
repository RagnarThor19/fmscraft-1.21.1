package net.ragnar.fmscraft.client.credits;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * The vanilla credits music, played by the credits themselves rather than the music tracker, so it can carry on
 * under the memories and fade out with them at the very end. While it plays, the tracker starts nothing else
 * (MusicTrackerMixin).
 */
public final class CreditsMusic extends MovingSoundInstance {
    private static CreditsMusic current;
    private float level = 1.0f;

    private CreditsMusic() {
        super(SoundEvents.MUSIC_CREDITS.value(), SoundCategory.MUSIC, SoundInstance.createRandom());
        this.repeat = false;
        this.relative = true;
        this.attenuationType = AttenuationType.NONE;
        this.volume = level;
    }

    public static void start() {
        stop();
        MinecraftClient client = MinecraftClient.getInstance();
        client.getMusicTracker().stop();
        current = new CreditsMusic();
        client.getSoundManager().play(current);
    }

    public static void stop() {
        if (current != null) {
            MinecraftClient.getInstance().getSoundManager().stop(current);
            current.setDone();
            current = null;
        }
    }

    /** 0-1 of its full volume. */
    public static void setLevel(float level) {
        if (current != null) current.level = Math.max(0.0001f, Math.min(1f, level));
    }

    public static boolean isPlaying() {
        return current != null && !current.isDone();
    }

    @Override
    public void tick() {
        volume = level;
    }
}
