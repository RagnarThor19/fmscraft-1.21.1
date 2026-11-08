package net.ragnar.fmscraft.boss.greed;

/**
 * When everything in RAGGIMEL's two cutscenes happens, in ticks (20 = 1 second). Pure numbers, so they're unit tested.
 *
 * <h2>Intro (while the portal is open, normally 30 s)</h2>
 * Everyone who comes through is frozen behind the Seven Deadly Sins wheel. Halfway through it slowly zooms in on
 * Greed; 5 seconds before the fight Just The Way You Are starts fading in; at 0 the wheel fades away and RAGGIMEL
 * comes floating down in his bubble.
 *
 * <h2>Phase change (at 70% health): the tide</h2>
 * Everyone is held where they stand (they can look round), the music cuts. RAGGIMEL lifts his staff and the sea answers:
 * a waterspout rises whirling round him, the sky goes navy and black holes open in it, and his voice line (10 s) plays.
 * A wall of water rises behind him and rolls over the island; as it reaches everyone the screen goes under, deep blue.
 * Under the water he grows to 3 blocks and the tide carries everyone to the Roswall manor (GreedManor). The deep blue
 * lifts on its garden under the same sky: he's high in a waterspout between the fountains. As the voice line ends the
 * spout bursts, he comes down with a crash, a ring of water rolls out, Panzerknacker starts and everyone can move.
 *
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
    /** The sky turns navy (black holes opening in it) over {@link #SKY_TICKS}. */
    public static final int SKY_AT = 10;
    public static final int SKY_TICKS = 60;
    /** The voice line (10 s) starts as the spout rises. */
    public static final int VOICE_AT = 20;
    public static final int VOICE_TICKS = 200;
    /** The waterspout round him grows this tall over this long. */
    public static final int SPOUT_TICKS = 110;
    public static final float SPOUT_HEIGHT = 16f;
    /** How high he's lifted in it. */
    public static final double SPOUT_LIFT = 4;
    /** The wave rises behind him, and reaches everyone at {@link #WAVE_HIT}: the screen goes under. */
    public static final int WAVE_AT = 96;
    public static final int WAVE_HIT = 150;
    public static final int UNDER_FADE = 6;
    /** Under the water: he grows, and everyone's carried to the manor. */
    public static final int RESHAPE_AT = WAVE_HIT + 10;
    /** The deep blue lifts on the manor garden. */
    public static final int SURFACE_AT = 186;
    public static final int SURFACE_TICKS = 30;
    /** As the voice line ends the spout bursts and he crashes down; the second song starts and everyone can move. */
    public static final int SONG_AT = VOICE_AT + VOICE_TICKS + 4;
    public static final int SONG_FADE = 40;
    /** He stands in the spray a moment before he comes for anyone; he can't be hurt until then. */
    public static final int TRANSITION_END = SONG_AT + 24;

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

    /** How high he's lifted in the spout {@code t} ticks into the phase change (on the island, then over the garden). */
    public static double spoutLift(int t) {
        if (t <= 0 || t >= SONG_AT) return 0;
        double u = Math.min(1, t / (double) SPOUT_TICKS);
        double lift = SPOUT_LIFT * u * u * (3 - 2 * u);
        // The last few ticks before the burst he's flung up a little more
        if (t > SONG_AT - 8) lift += (t - (SONG_AT - 8)) * 0.4;
        return lift;
    }
}
