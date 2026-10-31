package net.ragnar.fmscraft.boss.wrath;

/**
 * When everything in ORNAMIX's cutscenes happens, in ticks (20 = 1 second), and the beat the world blinks to in
 * Ragemode. Pure numbers, so they're unit tested.
 *
 * <h2>Intro</h2>
 * The same 30 seconds as RAGGIMEL's (GreedTimeline): frozen behind the Seven Deadly Sins wheel, which zooms in on
 * Wrath; the first song fades in 5 seconds before the fight. Then his entrance: he comes down out of the red sky
 * like a meteor onto the middle of the pit, lands kneeling in a ring of fire, rises and roars.
 *
 * <h2>Ragemode (at 40% health)</h2>
 * Black, frozen, the music cut; a heartbeat and the fires roaring for 5 seconds. Under the black the sky turns almost
 * black red and he takes the middle of the pit. Then his voice line ("ragemode"), and as it ends the black lifts on
 * him standing in a pillar of fire, eyes burning, and Return to Zero starts.
 *
 * <h2>The blink</h2>
 * Return to Zero's bass comes in at 19.25 s and hits every 0.6 s (100 BPM) until it drops out at about 59 s, then
 * comes back on the same grid at 76.85 s until about 117 s. On every hit the world blinks dark for a moment. The song
 * loops (124.9 s long), and the blinks wait for 19.25 s again each time round. Worked out from the song's own clock on
 * each client (WrathSky), so it stays on the beat whatever the server is doing.
 */
public final class OrnamixTimeline {
    // ---- Entrance, counted from the fight starting ----
    /** Coming down from {@link WrathArena#BOSS_SPAWN}. */
    public static final int ENTRANCE_FALL = 28;
    /** Kneeling where he landed, then rising with a roar. */
    public static final int ENTRANCE_KNEEL = 22;
    public static final int ENTRANCE_ROAR = 28;
    public static final int ENTRANCE_END = ENTRANCE_FALL + ENTRANCE_KNEEL + ENTRANCE_ROAR;

    // ---- Ragemode, counted from the moment it starts ----
    public static final int BLACKOUT_FADE = 6;
    /** Under the black: the sky turns and he takes the middle of the pit. */
    public static final int RESHAPE_AT = 60;
    public static final int VOICE_AT = 100;
    /** ragemode.ogg is 4.6 s. */
    public static final int VOICE_TICKS = 92;
    /** As the voice line ends, the black lifts on him, the song starts and everyone can move again. */
    public static final int REVEAL_AT = VOICE_AT + VOICE_TICKS;
    public static final int REVEAL_TICKS = 14;
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

    /** Height of his entrance above where he lands, {@code t} ticks into the fall: slow at first, then a streak. */
    public static double entranceHeight(int t, double from) {
        double u = Math.max(0, Math.min(1, t / (double) ENTRANCE_FALL));
        return from * (1 - u * u);
    }
}
