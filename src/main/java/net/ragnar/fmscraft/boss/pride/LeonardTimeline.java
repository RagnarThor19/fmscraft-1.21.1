package net.ragnar.fmscraft.boss.pride;

/**
 * When everything in LEONARD's fight happens, in ticks (20 = 1 second), and how it scales. Pure numbers, so they're unit
 * tested. Both songs were measured from the audio (onset detection).
 *
 * <h2>The arrival</h2>
 * The wheel fades on the plaza in the sky ({@link #REVEAL_TICKS}) and then, for ten seconds, nothing: no music, no
 * boss, only the wind ({@link #SILENCE} from the portal closing). Then Grove St. Party starts. Munch rises out of the
 * clouds with its first five seconds ({@link #munchRise}), risen by {@link #RISEN_SECONDS}, nodding on every beat
 * ({@link #nod}). On the drop, 8.37 s in ({@link #STRIKE}), LEONARD strikes down in the middle of the plaza.
 *
 * <h2>Grove St. Party (phase 1)</h2>
 * Wacka Flacka Flame - Grove St. Party (hood trap + slowed, TikTok version), 4:28. 126.0 BPM, the grid on 0.27 s
 * ({@link #GROVE_FIRST_BEAT}); the drop at 8.37 s is beat 17.
 *
 * <h2>The epic orchestra (phase 2)</h2>
 * Epic Orchestra and Choir, 1:26 (trimmed of the 5 s of silence at its end, so the loop doesn't gap). 120 BPM, the grid
 * on 0.12 s. Quiet for its first 8 s, then the whole orchestra comes in at 8.70 s ({@link #CONE_HIT}): that's when the
 * traffic cone lands on his head. At half health the Grove St. Party cuts, Munch topples back into the clouds, LEONARD
 * soars up over the plaza, the orchestra fades in ({@link #EPIC_AT}, over {@link #EPIC_FADE}), he grows to five blocks
 * ({@link #growScale}), the cone comes down out of the sky ({@link #coneHeight}) and lands on the hit: CONEMAN.
 */
public final class LeonardTimeline {
    // ---- Grove St. Party ----
    public static final double GROVE_BPM = 126.0;
    public static final int GROVE_FIRST_BEAT = 5;
    public static final double GROVE_FIRST_BEAT_SECONDS = 0.27;
    /** The drop: he strikes down. */
    public static final int STRIKE = 167;
    /** He can be hurt once he's up out of his landing. */
    public static final int READY_AFTER = 22;

    // ---- The arrival, counted from the portal closing ----
    /** How long the wheel takes to fade once the portal has closed. */
    public static final int REVEAL_TICKS = 40;
    /** The wheel fades, then ten seconds of nothing: the song starts this long after the portal closed. */
    public static final int SILENCE = REVEAL_TICKS + 200;
    /** Munch is all the way up this far into the song. */
    public static final double RISEN_SECONDS = 5.0;
    /** How far west of the middle everyone stands when the wheel fades. */
    public static final double WATCH_RADIUS = 14;

    // ---- The orchestra ----
    public static final double EPIC_BPM = 120.0;
    public static final int EPIC_FIRST_BEAT = 2;
    /** The orchestra comes in: the cone lands. */
    public static final int CONE_HIT = 174;

    // ---- Half health, counted from the moment it starts ----
    /** Munch topples back into the clouds over this long. */
    public static final int MUNCH_FALL = 70;
    /** The orchestra starts this long in, fading in over {@link #EPIC_FADE}; from then on it's timed by the song. */
    public static final int EPIC_AT = 20, EPIC_FADE = 150;
    /** Song ticks: he soars up over the plaza... */
    public static final int SOAR_END = 60;
    public static final double SOAR_HEIGHT = 9;
    /** ...grows to five blocks while he hangs there... */
    public static final int GROW_START = 66, GROW_END = 136;
    /** ...the cone appears high over him and falls, landing on the hit; then he's on the ground, CONEMAN. */
    public static final int CONE_APPEAR = 112;
    public static final double CONE_DROP = 56;
    /** CONEMAN's bar fills over this long after the hit, and he's loose when it's full. */
    public static final int BAR_FILL = 40;
    /** Everyone can move again this long after the hit. */
    public static final int FREE_AFTER = 12;

    /** CONEMAN is five blocks tall: his scale over a player's 1.8. */
    public static final float CONE_SCALE = 5f / 1.8f;

    private LeonardTimeline() {
    }

    private static double ease(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    /** How far Munch has risen, 0 (deep under the clouds) to 1 (looking in), {@code seconds} into Grove St. Party. */
    public static double munchRise(double seconds) {
        if (seconds < 0) return 0;
        double u = Math.min(1, seconds / RISEN_SECONDS);
        // Fast out of the clouds, slowing as he comes up to his full height
        return 1 - Math.pow(1 - u, 2.2);
    }

    /**
     * How far Munch's head is bowed, 0-1, {@code seconds} into Grove St. Party: down hard onto every beat, back up
     * slowly after it.
     */
    public static double nod(double seconds) {
        if (seconds < GROVE_FIRST_BEAT_SECONDS) return 0;
        double beat = 60.0 / GROVE_BPM, p = ((seconds - GROVE_FIRST_BEAT_SECONDS) / beat) % 1.0;
        if (p >= 0.78) return ease((p - 0.78) / 0.22);
        return 1 - ease(p / 0.78);
    }

    /** How far Munch has toppled, 0-1, {@code t} ticks after he started to fall. */
    public static double munchFall(double t) {
        double u = Math.max(0, Math.min(1, t / MUNCH_FALL));
        return u * u;
    }

    /** How high over the plaza LEONARD hangs, {@code s} ticks into the orchestra. Down to the ground on the hit. */
    public static double soarHeight(int s) {
        if (s >= CONE_HIT) return 0;
        if (s <= 0) return 0;
        if (s < SOAR_END) return SOAR_HEIGHT * ease(s / (double) SOAR_END);
        return SOAR_HEIGHT;
    }

    /** His scale (1 = a player) {@code s} ticks into the orchestra: growing to {@link #CONE_SCALE}. */
    public static float growScale(int s) {
        if (s <= GROW_START) return 1f;
        if (s >= GROW_END) return CONE_SCALE;
        return (float) (1 + (CONE_SCALE - 1) * ease((s - GROW_START) / (double) (GROW_END - GROW_START)));
    }

    /** How far over his head the falling cone is, {@code s} ticks into the orchestra: accelerating down onto the hit. */
    public static double coneHeight(int s) {
        if (s <= CONE_APPEAR) return CONE_DROP;
        if (s >= CONE_HIT) return 0;
        double u = (s - CONE_APPEAR) / (double) (CONE_HIT - CONE_APPEAR);
        return CONE_DROP * (1 - u * u);
    }

    /** How full CONEMAN's bar is, {@code s} ticks into the orchestra. */
    public static float barFill(int s) {
        if (s <= CONE_HIT) return 0f;
        return (float) Math.min(1, (s - CONE_HIT) / (double) BAR_FILL);
    }

    /** How far from the middle everyone stands when the wheel fades (further for a big group). */
    public static double watchRadius(int n) {
        return Math.max(WATCH_RADIUS, n * 1.7 / (Math.PI * 0.5) * 0.6);
    }

    /** Where watcher {@code i} of {@code n} stands, as a unit vector (x, z) out from the middle: an arc to the west. */
    public static double[] watchSlot(int i, int n) {
        double spread = Math.toRadians(Math.min(70, 12.0 * (n - 1)));
        double a = Math.PI + (n <= 1 ? 0 : -spread + 2 * spread * i / (n - 1));
        return new double[]{Math.cos(a), Math.sin(a)};
    }

    /**
     * His health, as a share of {@link LeonardEntity#MAX_HEALTH}, for {@code players} in the fight: full for 5-7, down to
     * 60% alone, and 8% more for each player past 7 (124% for 10), at most double. The same as the others.
     */
    public static float healthScale(int players) {
        if (players <= 0) return 1f;
        if (players <= 7) return Math.min(1f, 0.5f + 0.1f * players);
        return Math.min(2f, 1f + 0.08f * (players - 7));
    }

    /** How many dashes his Zoom makes. */
    public static final int ZOOM_DASHES = 15;
    /** His Lightning Strike: this many bolts, this many ticks apart (0.2 s), each marked this long before it falls. */
    public static final int STRIKES = 15, STRIKE_GAP = 4, STRIKE_MARK = 14;

    /** How many bolts his Lightning Strike calls down: 15, 24 as CONEMAN, three more for each player past seven. */
    public static int strikes(int players, boolean coneman) {
        return (coneman ? 24 : STRIKES) + Math.max(0, players - 7) * 3;
    }

    /** How many ball lightnings CONEMAN lets loose: three, one more for every three players past three, at most six. */
    public static int balls(int players) {
        return Math.min(6, 3 + Math.max(0, players - 3) / 3);
    }
}
