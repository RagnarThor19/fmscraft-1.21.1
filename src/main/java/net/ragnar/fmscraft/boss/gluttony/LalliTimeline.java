package net.ragnar.fmscraft.boss.gluttony;

/**
 * When everything in LalliLarge's fight happens, in ticks (20 = 1 second) or song seconds, and how it scales. Pure
 * numbers, so they're unit tested. All of it was measured from the songs themselves (onset detection on the audio).
 *
 * <h2>Apple Pie (phase 1)</h2>
 * Travis Scott - Apple Pie (slowed + reverb), 3:29. Six chord strums in the background, at 3.49, 3.97, 4.71, 5.41,
 * 5.88 and 6.63 s, then the same six again every 3.867 s: eight times round, until they fall out at about 35 s. On every
 * strum the sky (the stars and the sun) turns {@link #STRUM_TURN} of its whole cycle, swinging round in
 * {@link #TURN_SECONDS}. The beat under it is 124.1 BPM, its kick first at 3.69 s.
 *
 * <h2>Intro</h2>
 * The wheel (GreedTimeline's zoom) zooms in on Gluttony. Apple Pie starts {@link #SONG_LEAD} ticks (3.5 s) before the
 * portal closes, so its first 3.5 s are the last of the wheel; it fades as the first strum hits. Everyone is then high
 * over the Earth, frozen, floating down to its north pole while the sky turns on the strums; they land at 25 s
 * ({@link #PLAYERS_LAND}). LalliLarge appears high over the pole at 20 s and floats down slowly after them, landing at
 * 35 s ({@link #BOSS_LANDS}) with a wave that throws everyone back, as the strums fall out.
 *
 * <h2>SIRENS (phase 2)</h2>
 * Travis Scott - SIRENS, 3:26. A slow, floating opening, then the switch-up at 26.005 s: from there it's 128 BPM. At
 * half health he rises over the pole and SIRENS starts; on the switch-up everyone is taken to the snowfield. The giant
 * statue there nods its head on every beat from the switch-up until the song runs out ({@link #nod}).
 */
public final class LalliTimeline {
    // ---- Apple Pie ----
    public static final double PIE_SECONDS = 209.39;
    public static final double PIE_BPM = 124.1;
    /** The kick at 3.69 s. */
    public static final int PIE_FIRST_BEAT = 74;
    public static final double STRUM_START = 3.49, STRUM_LOOP = 3.867;
    public static final double[] STRUM_OFFSETS = {0, 0.48, 1.22, 1.92, 2.39, 3.14};
    public static final int STRUM_LOOPS = 8;
    /** How far the sky turns on each strum (a share of its whole cycle), and how long the swing takes. */
    public static final double STRUM_TURN = 0.3, TURN_SECONDS = 0.22;
    /** Where the sky starts: the sun a little off overhead. */
    public static final double SKY_START = 0.12;

    // ---- Intro and his arrival, in Apple Pie's ticks ----
    /** The song starts this long before the portal closes: the last of the wheel. */
    public static final int SONG_LEAD = 70;
    public static final int REVEAL_TICKS = 12;
    public static final int PLAYERS_LAND = 500;
    /** He appears this long before everyone lands, and lands this long after. */
    public static final int APPEAR_BEFORE = 100, LAND_AFTER = 200;
    public static final int BOSS_LANDS = PLAYERS_LAND + LAND_AFTER;
    /** The shortest float down there can be (a short intro): never less, even if it misses 25 s. */
    public static final int MIN_FALL = 60;
    /** How far over the pole everyone starts, and he appears. */
    public static final double SKY_HEIGHT = 170, BOSS_HEIGHT = 110;
    /** Getting up after the landing; he can't be hurt until he's up. */
    public static final int RISE_TICKS = 30;
    /** How far out from the pole everyone comes down (a ring round where he'll land). */
    public static final double LANDING_RING = 12;

    // ---- SIRENS ----
    public static final double SIRENS_SECONDS = 206.0;
    public static final double SIRENS_BPM = 128;
    public static final double SWITCH_SECONDS = 26.005;
    /** The switch-up, in ticks since SIRENS started: everyone is in the snowfield, and the fight goes on. */
    public static final int SWITCH_TICK = 520;
    /** The statue nods from the switch-up to here (the song's last beats). */
    public static final double NOD_END = 201.0;
    /** Before the switch-up: the screen whites out, everyone is held still, then taken to the snowfield under the white. */
    public static final int WHITE_AT = SWITCH_TICK - 30, WHITE_FADE = 24, MOVE_AT = SWITCH_TICK - 4, CLEAR_TICKS = 40;
    // ---- Half health, before SIRENS: the mini cutscene ----
    /** Apple Pie fades out over this as he's brought to his knees. */
    public static final int PIE_FADE = 60;
    /** Kneeling, the ice cracking off him; then he stands and looks up; then arms out, and SIRENS starts. */
    public static final int STAND_AT = 54, LIFT_AT = 74, CUTSCENE = 92;
    public static final int SIRENS_FADE = 30;
    /** On SIRENS he flies up into the sky over the pole, this high by the time the screen whites out. */
    public static final double ASCENT_HEIGHT = 55;
    /** How long he takes to drift over to the pole's axis as he rises. */
    public static final int ASCENT_DRIFT = 140;
    /** How far the sky drifts (cycles) while he hangs over the pole, speeding up towards the switch-up. */
    public static final double DRIFT = 0.0025;

    private LalliTimeline() {
    }

    // ---- The sky ----

    /** Every strum, in seconds into Apple Pie (the first time through). */
    public static double strum(int i) {
        return STRUM_START + (i / STRUM_OFFSETS.length) * STRUM_LOOP + STRUM_OFFSETS[i % STRUM_OFFSETS.length];
    }

    public static int strums() {
        return STRUM_LOOPS * STRUM_OFFSETS.length;
    }

    /**
     * How far round the sky has turned (in whole cycles) {@code seconds} into Apple Pie, counting on through the times
     * it loops: {@link #STRUM_TURN} more on every strum, each swung in over {@link #TURN_SECONDS}, eased.
     */
    public static double skyTurn(double seconds) {
        if (seconds < 0) return SKY_START;
        int loops = (int) Math.floor(seconds / PIE_SECONDS);
        double t = seconds - loops * PIE_SECONDS, turn = SKY_START + loops * strums() * STRUM_TURN;
        for (int i = 0; i < strums(); i++) {
            double since = t - strum(i);
            if (since <= 0) break;
            turn += STRUM_TURN * ease(Math.min(1, since / TURN_SECONDS));
        }
        return turn;
    }

    /** The slow drift while he hangs over the pole, {@code seconds} into SIRENS: speeding up to the switch-up. */
    public static double drift(double seconds) {
        double s = Math.max(0, Math.min(SWITCH_SECONDS, seconds));
        return DRIFT * s * s;
    }

    /**
     * His height over the pole {@code songTicks} into SIRENS, flying up from {@code from} (his height over it when he
     * lifted off): slow at first, gathering, easing in at the top by the time the screen whites out.
     */
    public static double ascent(int songTicks, double from) {
        double u = clamp01(songTicks / (double) WHITE_AT);
        return from + (ASCENT_HEIGHT - from) * ease(u);
    }

    static double ease(double u) {
        return u * u * (3 - 2 * u);
    }

    // ---- The statue ----

    /**
     * How far the statue's head is bowed (0 up, 1 all the way down) {@code seconds} into SIRENS: down on every beat from
     * the switch-up, coming up between, and still before the switch-up and once the song has run out.
     */
    public static double nod(double seconds) {
        if (seconds < 0) return 0;
        double t = seconds % SIRENS_SECONDS;
        if (t < SWITCH_SECONDS || t >= NOD_END) return 0;
        double beat = 60.0 / SIRENS_BPM, p = ((t - SWITCH_SECONDS) / beat) % 1.0;
        // Down quickly onto the beat, back up slowly after it
        if (p >= 0.72) return ease((p - 0.72) / 0.28);
        return 1 - ease(p / 0.72);
    }

    // ---- The arrival ----

    /** When everyone lands, if the fight starts {@code startTick} into Apple Pie. */
    public static int landTick(int startTick) {
        return Math.max(PLAYERS_LAND, startTick + MIN_FALL);
    }

    /**
     * How high over the ground a player is at {@code tick}, floating down from {@link #SKY_HEIGHT} at {@code startTick}
     * to land at {@code landTick}: drifting off slowly, gliding, then settling softly.
     */
    public static double floatHeight(int tick, int startTick, int landTick) {
        double u = clamp01((tick - startTick) / (double) Math.max(1, landTick - startTick));
        return SKY_HEIGHT * (1 - ease(u));
    }

    /** How far through their float down a player is (0-1), for drifting in from the ring to where they'll land. */
    public static double floatProgress(int tick, int startTick, int landTick) {
        return ease(clamp01((tick - startTick) / (double) Math.max(1, landTick - startTick)));
    }

    /** His height over the pole at {@code tick}: appearing high, sinking slowly, then crashing down at the end. */
    public static double bossHeight(int tick, int appearTick, int landTick) {
        double u = clamp01((tick - appearTick) / (double) Math.max(1, landTick - appearTick));
        return BOSS_HEIGHT * (1 - u * u * u);
    }

    /** How far out the ring in the sky hangs for {@code n} players: 6 blocks, wider once there are too many to fit 4 apart. */
    public static double ringRadius(int n) {
        return Math.max(6, n * 4.0 / (2 * Math.PI));
    }

    /** Where slot {@code i} of {@code n} sits round the middle (unit circle, x and z), the first one to the south. */
    public static double[] ringSlot(int i, int n) {
        double a = Math.PI / 2 + i * 2 * Math.PI / Math.max(1, n);
        return new double[]{Math.cos(a), Math.sin(a)};
    }

    // ---- Scaling ----

    /**
     * His health, as a share of {@link LalliEntity#MAX_HEALTH}, for {@code players} in the fight: full for 5-7, down to
     * 60% alone, and 8% more for each player past 7 (124% for 10), at most double.
     */
    public static float healthScale(int players) {
        if (players <= 0) return 1f;
        if (players <= 7) return Math.min(1f, 0.5f + 0.1f * players);
        return Math.min(2f, 1f + 0.08f * (players - 7));
    }

    /** How many of something he aims at a crowd of {@code players} at once: one each, at most {@code max}. */
    public static int perPlayer(int players, int max) {
        return Math.max(1, Math.min(max, players));
    }

    private static double clamp01(double u) {
        return Math.max(0, Math.min(1, u));
    }
}
