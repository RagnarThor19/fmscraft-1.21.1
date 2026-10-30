package net.ragnar.fmscraft.boss.greed;

/**
 * When everything in RAGGIMEL's two cutscenes happens, in ticks (20 = 1 second). Pure numbers, so they're unit tested.
 *
 * <h2>Intro (while the portal is open, normally 30 s)</h2>
 * Everyone who comes through is frozen behind the Seven Deadly Sins wheel. Halfway through it slowly zooms in on
 * Greed; 5 seconds before the fight Just The Way You Are starts fading in; at 0 the wheel fades away and RAGGIMEL
 * comes floating down in his bubble.
 *
 * <h2>Phase change (at 70% health)</h2>
 * Black, frozen, eerie sounds for 5 s; then the voice line (10 s), under which he grows to 3 blocks and moves to his
 * perch over the island's middle shrine and the sky turns to night. The black lifts as the voice line ends, the second
 * song fades in, and he fires lasers from the perch for 12 s before leaping back into the ring.
 */
public final class GreedTimeline {
    // ---- Intro, counted from the portal opening ----
    /** How long the zoom into Greed takes. It ends {@link #ZOOM_HOLD} ticks before the fight. */
    public static final int ZOOM_TICKS = 220;
    public static final int ZOOM_HOLD = 60;
    /** The first song starts this long before the fight, fading in over {@link #MUSIC_FADE}. */
    public static final int MUSIC_LEAD = 100;
    public static final int MUSIC_FADE = 160;
    /** How long the wheel takes to fade away once the fight starts. */
    public static final int INTRO_REVEAL = 30;

    // ---- Phase change, counted from the moment it starts ----
    public static final int BLACKOUT_FADE = 6;
    public static final int VOICE_AT = 100;
    /** Under the black: he grows, moves to his perch, the sky turns. */
    public static final int RESHAPE_AT = 120;
    public static final int REVEAL_AT = 290;
    public static final int REVEAL_TICKS = 40;
    /** The voice line is 10 s; the second song comes in as it ends, and everyone can move again. */
    public static final int SONG_AT = 305;
    public static final int SONG_FADE = 100;
    /** Lasers from the perch, then the leap back into the ring. */
    public static final int PERCH_TICKS = 240;
    public static final int LEAP_TICKS = 40;
    public static final int TRANSITION_END = SONG_AT + PERCH_TICKS + LEAP_TICKS;

    private GreedTimeline() {
    }

    /** Tick (since the portal opened) the zoom starts, for an intro {@code duration} ticks long. */
    public static int zoomStart(int duration) {
        return Math.max(0, duration - ZOOM_HOLD - ZOOM_TICKS);
    }

    /** How far the zoom into Greed has got, 0-1, eased in and out. */
    public static float zoom(float elapsed, int duration) {
        int start = zoomStart(duration);
        int length = Math.max(1, Math.min(ZOOM_TICKS, duration - ZOOM_HOLD - start));
        float t = Math.max(0f, Math.min(1f, (elapsed - start) / length));
        return t * t * (3 - 2 * t);
    }

    /** Tick (since the portal opened) the first song starts. */
    public static int musicStart(int duration) {
        return Math.max(0, duration - MUSIC_LEAD);
    }

    /** Height of RAGGIMEL's leap back into the ring, {@code t} ticks into it: a smooth arc, 0 at both ends. */
    public static double leapHeight(int t) {
        double u = Math.max(0, Math.min(1, t / (double) LEAP_TICKS));
        return 18 * Math.sin(Math.PI * u);
    }
}
