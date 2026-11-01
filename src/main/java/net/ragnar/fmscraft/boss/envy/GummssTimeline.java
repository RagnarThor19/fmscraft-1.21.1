package net.ragnar.fmscraft.boss.envy;

/**
 * When everything in GUMMSS's intro happens, in ticks (20 = 1 second), and how the fight scales. Pure numbers, so
 * they're unit tested.
 *
 * <h2>The song</h2>
 * Seekae's Test &amp; Recognise (Flume re-work, slowed): a quiet build, then the bass drops at 21.575 s and pulses on a
 * 90 BPM grid (0.667 s a beat) from there. The beat grid is lined up with the drop: beat 32 lands on it.
 *
 * <h2>Intro</h2>
 * The same wheel as the others (GreedTimeline), zooming in on Envy. The song starts during the zoom, timed so the drop
 * lands {@link #HOVER_TICKS} + {@link #FALL_TICKS} after the portal closes. When it closes, everyone is lifted high
 * over the arena into a ring round GUMMSS, facing him, while the wheel fades. They hang there a moment, then fall
 * with him, slowly at first and then faster, frozen, watching him. Everyone lands as the bass drops and the fight starts.
 */
public final class GummssTimeline {
    // ---- The song ----
    public static final double DROP_SECONDS = 21.575;
    /** The tick (since the song started) the bass drops on: everyone lands. */
    public static final int DROP_TICK = 432;
    public static final double BPM = 90;
    /** Puts beat 32 on {@link #DROP_TICK}: 432 - 32 * 13.33 = 5. */
    public static final int FIRST_BEAT_TICK = 5;
    public static final int MUSIC_FADE = 60;

    // ---- The fall, counted from the portal closing ----
    /** Lifted into the ring while the wheel fades: hanging still. */
    public static final int HOVER_TICKS = 30;
    public static final int FALL_TICKS = 220;
    public static final int REVEAL_TICKS = 30;
    /** How high over the floor the ring starts. */
    public static final double FALL_HEIGHT = 62;
    /** How far from him everyone hangs (further for a big group, so nobody's crowded: see {@link #ringRadius}). */
    public static final double RING_RADIUS = 6.5;
    /** Shortest fall there can be (a late or short intro): never less than this, even if it misses the drop. */
    public static final int MIN_FALL = 60;
    /** The landing: he rises from his crouch and the shockwave throws everyone back; he can't be hurt until he's up. */
    public static final int LAND_TICKS = 34;

    private GummssTimeline() {
    }

    /** Tick (since the portal opened) the song starts, so the drop lands as everyone does. */
    public static int musicStart(int joinTicks) {
        return Math.max(0, joinTicks - (DROP_TICK - HOVER_TICKS - FALL_TICKS));
    }

    /**
     * How many ticks his fall lasts, if the song is already {@code songTicks} in when the fight starts: until the
     * drop, after hanging for {@link #HOVER_TICKS}, but never shorter than {@link #MIN_FALL}.
     */
    public static int fallTicks(int songTicks) {
        return Math.max(MIN_FALL, DROP_TICK - songTicks - HOVER_TICKS);
    }

    /**
     * Height over the floor {@code t} ticks into a fall {@code length} long: drifting down at first, then faster and
     * faster, hitting the ground still moving.
     */
    public static double fallHeight(int t, int length, double from) {
        double u = Math.max(0, Math.min(1, t / (double) Math.max(1, length)));
        return from * (1 - Math.pow(u, 1.7));
    }

    /** How far out the ring hangs for {@code n} players: 6.5 blocks, wider once there are too many to fit 3.5 apart. */
    public static double ringRadius(int n) {
        return Math.max(RING_RADIUS, n * 3.5 / (2 * Math.PI));
    }

    /** Where slot {@code i} of {@code n} sits round him (unit circle, x and z), the first one to the south. */
    public static double[] ringSlot(int i, int n) {
        double a = Math.PI / 2 + i * 2 * Math.PI / Math.max(1, n);
        return new double[]{Math.cos(a), Math.sin(a)};
    }

    /**
     * His health, as a share of {@link GummssEntity#MAX_HEALTH}, for {@code players} in the fight: full for 5-7,
     * down to 60% alone (like ORNAMIX), and 8% more for each player past 7 (124% for 10), at most double.
     */
    public static float healthScale(int players) {
        if (players <= 0) return 1f;
        if (players <= 7) return Math.min(1f, 0.5f + 0.1f * players);
        return Math.min(2f, 1f + 0.08f * (players - 7));
    }
}
