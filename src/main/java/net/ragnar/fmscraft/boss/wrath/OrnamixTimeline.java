package net.ragnar.fmscraft.boss.wrath;

/**
 * When everything in ORNAMIX's cutscenes happens, in ticks (20 = 1 second), and the beat the world blinks to in
 * Ragemode. Pure numbers, so they're unit tested.
 *
 * <h2>Intro</h2>
 * The same 30 seconds as RAGGIMEL's (GreedTimeline): frozen behind the Seven Deadly Sins wheel, which zooms in on
 * Wrath; N***as in Paris starts 2 seconds before the fight, so its beat drops (5.6 s in, beat 13) 72 ticks into it.
 * When the wheel fades he's kneeling in the middle of the pit, head down, his sword planted in the sand, and on each
 * of the four beats before the drop seams of magma split further out across the sand from it. A beat before the drop
 * he rips the sword out and leaps; on the drop he drives it into the sand: every seam bursts into a wall of flame, a
 * ring of fire rolls out over the pit, and he roars in a pillar of fire.
 *
 * <h2>Ragemode (at 40% health)</h2>
 * Black, frozen, the music cut. Under the black the sky turns almost black red and he takes the middle of the pit;
 * the black lifts on him kneeling there, his sword driven into the sand. A heartbeat, and with each beat the seams
 * of magma spread further and a pulse of fire rolls out. Then his voice line ("ragemode"): he rises into the air on a
 * pillar of fire, arms spread, chunks of the pit torn up and whirling round him, closing in. As it ends he comes down
 * like a hammer: the chunks are flung away, the seams erupt, the blast throws everyone back, and Return to Zero starts.
 *
 * <h2>The blink</h2>
 * Return to Zero's bass comes in at 19.25 s and hits every 0.6 s (100 BPM) until it drops out at about 59 s, then
 * comes back on the same grid at 76.85 s until about 117 s. On every hit the world blinks dark for a moment. The song
 * loops (124.9 s long), and the blinks wait for 19.25 s again each time round. Worked out from the song's own clock on
 * each client (WrathSky), so it stays on the beat whatever the server is doing.
 */
public final class OrnamixTimeline {
    // ---- Entrance, counted from the fight starting ----
    /** N***as in Paris starts this long before the fight (under the wheel), fading in over {@link #SONG_LEAD_FADE}. */
    public static final int SONG_LEAD = 40;
    public static final int SONG_LEAD_FADE = 16;
    /** The song's own tick its beat drops in on: 5.6 s, beat 13 (measured from the audio). */
    public static final int DROP_SONG_TICK = 112;
    /** The slam, on the drop. */
    public static final int ENTRANCE_SLAM = DROP_SONG_TICK - SONG_LEAD;
    /** One beat (140 BPM) is 8.57 ticks. */
    public static final double BEAT = 20 * 60 / 140.0;
    /** A beat before the drop he rips his sword out of the sand and leaps. */
    public static final int ENTRANCE_LEAP = ENTRANCE_SLAM - (int) Math.round(BEAT);
    public static final double LEAP_HEIGHT = 3.2;
    /** Roaring in his pillar of fire after the slam; he can't be hurt until it's over. */
    public static final int ENTRANCE_ROAR = 30;
    public static final int ENTRANCE_END = ENTRANCE_SLAM + ENTRANCE_ROAR;
    /** How many beats before the drop the seams grow on, and how far out they reach at the end. */
    public static final int CRACK_BEATS = 4;
    public static final float CRACK_LENGTH = 13f;

    // ---- Ragemode, counted from the moment it starts ----
    public static final int BLACKOUT_FADE = 6;
    /** Under the black: the sky turns and he takes the middle of the pit, kneeling. */
    public static final int RESHAPE_AT = 14;
    /** The black lifts on him. */
    public static final int LIFT_AT = 22;
    public static final int LIFT_TICKS = 16;
    /** A heartbeat every second until his voice; each one spreads the seams. */
    public static final int HEARTBEAT = 20;
    public static final int VOICE_AT = 100;
    /** ragemode.ogg is 4.6 s. */
    public static final int VOICE_TICKS = 92;
    /** As the voice line ends he comes down on the pit, the song starts and everyone can move again. */
    public static final int REVEAL_AT = VOICE_AT + VOICE_TICKS;
    /** How high he rises while it plays, and how long the fall back down takes. */
    public static final double RISE_HEIGHT = 4.5;
    public static final int SLAM_TICKS = 3;
    public static final int SONG_FADE = 10;
    /** He roars in his pillar of fire before he comes for anyone; he can't be hurt until then. */
    public static final int RAGE_ROAR = 30;
    public static final int TRANSITION_END = REVEAL_AT + RAGE_ROAR;

    // ---- Return to Zero ----
    public static final double SONG_SECONDS = 124.9;
    public static final double BASS_START = 19.25;
    public static final double BASS_PERIOD = 0.6;
    /** The hits (counted from the first at 19.25 s) the bass actually plays: two stretches with a break between. */
    private static final int[][] BASS_HITS = {{0, 66}, {96, 162}};
    /** How dark a blink gets (0-1) and how quickly it fades. */
    public static final float BLINK_DEPTH = 0.32f;
    public static final double BLINK_SECONDS = 0.2;

    private OrnamixTimeline() {
    }

    /** Which bass hit is the latest at {@code seconds} into the song (looping), or -1 before 19.25 s. */
    public static int bassHit(double seconds) {
        double t = loop(seconds);
        if (t < BASS_START) return -1;
        return (int) Math.floor((t - BASS_START) / BASS_PERIOD + 1e-9);
    }

    /** Whether hit number {@code hit} is one the bass plays. */
    public static boolean isPlayed(int hit) {
        for (int[] stretch : BASS_HITS) {
            if (hit >= stretch[0] && hit <= stretch[1]) return true;
        }
        return false;
    }

    /** How dark the world is blinked {@code seconds} into Return to Zero: 0 between hits, up to {@link #BLINK_DEPTH}. */
    public static float blink(double seconds) {
        int hit = bassHit(seconds);
        if (hit < 0 || !isPlayed(hit)) return 0;
        double since = loop(seconds) - (BASS_START + hit * BASS_PERIOD);
        if (since >= BLINK_SECONDS) return 0;
        double fade = 1 - since / BLINK_SECONDS;
        return (float) (BLINK_DEPTH * fade * fade);
    }

    private static double loop(double seconds) {
        double t = seconds % SONG_SECONDS;
        return t < 0 ? t + SONG_SECONDS : t;
    }

    // ---- Tuning that depends on the group ----

    /**
     * His health, as a share of {@link OrnamixEntity#MAX_HEALTH}, for {@code players} in the fight. Tuned for 5-7
     * (full health): with fewer the fight would drag (a boss can only be hit so often, and alone you're dodging
     * everything), so it goes down to 60% for one player.
     */
    public static float healthScale(int players) {
        if (players <= 0) return 1f;
        return Math.min(1f, 0.5f + 0.1f * players);
    }

    /** The tick (counted from the fight starting) of beat {@code k} before the drop: k = 1 is the leap. */
    public static int beatBeforeDrop(int k) {
        return (int) Math.round(ENTRANCE_SLAM - k * BEAT);
    }

    /** How high he is off the sand {@code t} ticks into the entrance: up off the leap, and down on the drop. */
    public static double leapHeight(int t) {
        if (t <= ENTRANCE_LEAP || t >= ENTRANCE_SLAM) return 0;
        double u = (t - ENTRANCE_LEAP) / (double) (ENTRANCE_SLAM - ENTRANCE_LEAP);
        // Up fast, hanging at the top, then straight down
        return LEAP_HEIGHT * Math.sin(Math.PI * Math.pow(u, 0.75));
    }

    /** How high he hangs {@code t} ticks into Ragemode: rising through his voice line, then down like a hammer. */
    public static double riseHeight(int t) {
        if (t <= VOICE_AT || t >= REVEAL_AT) return 0;
        int top = REVEAL_AT - SLAM_TICKS;
        if (t >= top) return RISE_HEIGHT * (REVEAL_AT - t) / (double) SLAM_TICKS;
        double u = (t - VOICE_AT) / (double) (top - VOICE_AT);
        return RISE_HEIGHT * u * u * (3 - 2 * u);
    }
}
