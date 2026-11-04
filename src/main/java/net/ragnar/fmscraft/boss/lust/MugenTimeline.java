package net.ragnar.fmscraft.boss.lust;

/**
 * When everything in MUGEN's fight happens, in ticks (20 = 1 second), and how it scales. Pure numbers, so they're unit
 * tested. All of it was measured from the songs themselves (onset detection on the audio).
 *
 * <h2>COMË N GO (phase 1)</h2>
 * YEAT - COMË N GO [reversed intro] (slowed, 852Hz, reverb), 5:23. The first 34.9 s are the song's intro played
 * backwards; it ends on a hard hit at 34.91 s ({@link #BOOM}), and from there it's 109.4 BPM, the grid on 35.0 s
 * ({@link #COMENGO_FIRST_BEAT}). The song starts the moment the portal closes, so the whole reversed intro is his
 * entrance: the shadow spreads, the hands drag him up out of the ground, he draws his blades, flickers round everyone,
 * and on the hit he's loose.
 *
 * <h2>Toxic (phase 2)</h2>
 * Playboi Carti ft. Skepta - Toxic [reversed intro] (slowed, reverb, bass boosted), 2:57. Its reversed intro runs to
 * 14.68 s, cuts to silence, and the beat slams in at 15.60 s ({@link #TOXIC_DROP}): 61.4 BPM measured, a half-time
 * feel over 122.8 ({@link #TOXIC_BPM}), the grid on the drop. At half health the screen goes black, eerie sounds for
 * {@link #EERIE_TICKS}, then Toxic starts under the black; the black lifts on the eclipse with him hanging in the sky
 * ({@link #REVEAL_AT}), he dives in the silence ({@link #DIVE_AT}) and lands on the drop.
 */
public final class MugenTimeline {
    // ---- COMË N GO ----
    public static final double COMENGO_SECONDS = 323.36;
    public static final double COMENGO_BPM = 109.4;
    /** The beat grid's first beat, 35.0 s. */
    public static final int COMENGO_FIRST_BEAT = 700;
    /** The hit that ends the reversed intro, 34.91 s: he's loose. */
    public static final int BOOM = 698;
    /** After the boom he can be hurt once he's out of his crouch. */
    public static final int READY_AFTER = 12;

    // ---- The entrance, in COMË N GO's ticks ----
    /** How long the wheel takes to fade once the portal has closed. */
    public static final int REVEAL_TICKS = 40;
    /** The shadow spreads out from where he'll rise: three pools one after another, each the Tome of Unseen Hands'. */
    public static final int[] POOLS = {60, 200, 340};
    /** A pool creeps out for this long before its hands come up (the tome's own timing), and lets go this long after. */
    public static final int POOL_CREEP = 60, POOL_HOLD = 80;
    /** The hands drag him up from here to {@link #RISEN}, slowly: from deep under the ground to standing on it. */
    public static final int RISE_START = POOLS[0] + POOL_CREEP, RISEN = POOLS[2] + POOL_CREEP + POOL_HOLD;
    /** How far under the ground his feet start (his head just under the surface). */
    public static final double RISE_DEPTH = 1.95;
    /** He lifts his head and his eyes light; then draws his blades; then flickers round everyone; then crouches. */
    public static final int LOOK_UP = 500, DRAW = 540, FLICKER = 600, FLICKER_STEP = 12, FLICKERS = 4, STANCE = 664;
    /** How far from him everyone stands to watch (further for a big group). */
    public static final double WATCH_RADIUS = 9;

    // ---- Toxic ----
    public static final double TOXIC_SECONDS = 177.52;
    public static final double TOXIC_BPM = 122.8;
    /** The drop, 15.60 s: he lands, and the grid starts. */
    public static final int TOXIC_DROP = 312;
    /** The silence before the drop, 14.68 s: he dives. */
    public static final int DIVE_AT = 294;

    // ---- Half health, counted from the moment it starts ----
    /** The black comes down this fast, and holds for the eerie sounds before Toxic starts under it. */
    public static final int BLACK_FADE = 6, EERIE_TICKS = 90;
    /** Under the black, the sky turns to the eclipse and he's set in the air over the courtyard. */
    public static final int SKY_AT = 40;
    /** Toxic's ticks: the black lifts on him hanging in the eclipse, everyone free to move. */
    public static final int REVEAL_AT = 50, REVEAL_FADE = 40;
    /** How high over the courtyard he hangs, rising slowly to {@link #HANG_TOP} by the dive. */
    public static final double HANG_HEIGHT = 11, HANG_TOP = 15;

    // ---- His fight ----
    /** The Blackout: the world goes black round everyone, seeing only this far, for this long. */
    public static final int BLACKOUT_TICKS = 140;
    public static final float BLACKOUT_SIGHT = 4f;

    private MugenTimeline() {
    }

    /** How far under the ground his feet are (positive: under), {@code t} ticks into COMË N GO. Eased: slow out of the dark. */
    public static double riseDepth(int t) {
        if (t <= RISE_START) return RISE_DEPTH;
        if (t >= RISEN) return 0;
        double u = (t - RISE_START) / (double) (RISEN - RISE_START);
        double eased = u * u * (3 - 2 * u);
        return RISE_DEPTH * (1 - eased);
    }

    /** Which pool (index into {@link #POOLS}) the hands of come up at tick {@code t}, or -1. */
    public static int grabAt(int t) {
        for (int i = 0; i < POOLS.length; i++) if (t == POOLS[i] + POOL_CREEP) return i;
        return -1;
    }

    /** Which flicker (0 to {@link #FLICKERS} - 1) he's on at tick {@code t}, or -1 outside them. */
    public static int flicker(int t) {
        if (t < FLICKER || t >= FLICKER + FLICKERS * FLICKER_STEP) return -1;
        return (t - FLICKER) / FLICKER_STEP;
    }

    /** How high over the courtyard he hangs, {@code s} ticks into Toxic. */
    public static double hangHeight(int s) {
        if (s <= REVEAL_AT) return HANG_HEIGHT;
        if (s >= DIVE_AT) return HANG_TOP;
        double u = (s - REVEAL_AT) / (double) (DIVE_AT - REVEAL_AT);
        return HANG_HEIGHT + (HANG_TOP - HANG_HEIGHT) * u;
    }

    /** His height over the ground diving from {@link #HANG_TOP}, {@code s} ticks into Toxic: accelerating down onto the drop. */
    public static double diveHeight(int s) {
        if (s <= DIVE_AT) return HANG_TOP;
        if (s >= TOXIC_DROP) return 0;
        double u = (s - DIVE_AT) / (double) (TOXIC_DROP - DIVE_AT);
        return HANG_TOP * (1 - u * u);
    }

    /** How far from him everyone stands for the entrance, so nobody's crowded. */
    public static double watchRadius(int n) {
        return Math.max(WATCH_RADIUS, n * 1.6 / (Math.PI * 0.42) * 0.5);
    }

    /**
     * Where watcher {@code i} of {@code n} stands, as a unit vector (x, z) out from him: an arc to the east (the way
     * everyone arrives), at most 75 degrees either side, since the courtyard is long east to west and narrow north to south.
     */
    public static double[] watchSlot(int i, int n) {
        double spread = Math.toRadians(Math.min(75, 12.0 * (n - 1)));
        double a = n <= 1 ? 0 : -spread + 2 * spread * i / (n - 1);
        return new double[]{Math.cos(a), Math.sin(a)};
    }

    /**
     * His health, as a share of {@link MugenEntity#MAX_HEALTH}, for {@code players} in the fight: full for 5-7, down to
     * 60% alone, and 8% more for each player past 7 (124% for 10), at most double. The same as the others.
     */
    public static float healthScale(int players) {
        if (players <= 0) return 1f;
        if (players <= 7) return Math.min(1f, 0.5f + 0.1f * players);
        return Math.min(2f, 1f + 0.08f * (players - 7));
    }

    /** How many shadow clones he makes for {@code players}: three, five once he's in phase 2, one more past seven players. */
    public static int clones(int players, boolean phaseTwo) {
        return (phaseTwo ? 5 : 3) + (players > 7 ? 1 : 0);
    }
}
