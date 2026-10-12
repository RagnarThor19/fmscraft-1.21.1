package net.ragnar.fmscraft.boss;

/**
 * Timing info for one boss theme, used to make attacks land on the beat.
 * Measure the song's BPM and when its first downbeat hits (in ticks, 20 ticks = 1 second).
 *
 * Music plays on each client while the beat clock runs on the server, so expect up to ~1 tick of drift.
 * Restart the song from the server at each phase change to re-sync.
 */
public record BossSong(String soundId, double bpm, int firstBeatTick) {
    public double ticksPerBeat() {
        return 20.0 * 60.0 / bpm;
    }

    /** Which beat we're on, {@code ticksSinceStart} ticks after the song started (negative before the first beat). */
    public int beatAt(int ticksSinceStart) {
        return (int) Math.floor((ticksSinceStart - firstBeatTick) / ticksPerBeat());
    }

    /** True on the single tick a new beat starts. */
    public boolean isBeatTick(int ticksSinceStart) {
        return ticksSinceStart >= firstBeatTick && beatAt(ticksSinceStart) != beatAt(ticksSinceStart - 1);
    }
}
