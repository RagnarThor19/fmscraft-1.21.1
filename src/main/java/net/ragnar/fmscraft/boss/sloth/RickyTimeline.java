package net.ragnar.fmscraft.boss.sloth;

/**
 * When everything in RICKY's fight happens, in ticks (20 = 1 second), and how it scales. Pure numbers, so they're unit
 * tested. Both songs were measured from the audio (onset strength, its autocorrelation for the tempo, then the grid
 * slid to where the beats land).
 *
 * <h2>overseas (phase 1)</h2>
 * Ken Carson - overseas (Instrumental), 2:20 (trimmed of the 4.7 s of silence at its end). 151.4 BPM, the grid on
 * 0.187 s ({@link #OVERSEAS_FIRST_BEAT}). Its first 25 s are the quiet intro: the beat drops at 25.6 s, beat 64
 * ({@link #DROP}).
 *
 * <h2>The arrival (the intro of overseas)</h2>
 * Sloth: when the wheel fades he's asleep on the grass by the water, flat on his back, snoring. The song starts as the
 * portal closes. He stirs ({@link #STIR}), sits up and yawns ({@link #SIT}), gets to his feet and stretches
 * ({@link #STAND}), and the dark gathers round him ({@link #GATHER}). On the drop he stamps: a shockwave of dark red
 * throws everyone back (it hurts nobody), and he's loose.
 *
 * <h2>Kills (phase 2)</h2>
 * Chief Keef - Kills (slowed to perfection, reverb, bass boosted), 4:00. 74.92 BPM, the grid on 0.233 s
 * ({@link #KILLS_FIRST_BEAT}); the slow intro breaks into the beat at 17.05 s, beat 21 ({@link #KILLS_DROP}).
 *
 * <h2>Half health: Super Saiyan</h2>
 * The music cuts, everyone is held still and the sky turns: the sunset drains to black, red lights come out all over
 * it, a grey haze settles ({@link #SKY_FADE}). Kills starts {@link #KILLS_AT} in, and from then on everything is timed
 * by it: he rises off the ground, rubble lifting all round him ({@link #riseHeight}); the anime power-up starts
 * ({@link #POWERUP_AT}) so that it ends on the drop; his hair flickers gold, faster and faster ({@link #flicker}); and on
 * the drop it bursts: Super Saiyan, a golden aura, a blast that throws everyone back.
 */
public final class RickyTimeline {
    // ---- overseas ----
    public static final double OVERSEAS_BPM = 151.4;
    public static final int OVERSEAS_FIRST_BEAT = 4;
    /** The beat drops: he's awake, and the fight is on. */
    public static final int DROP = 512;
    /** He can be hurt once he's up out of his stamp. */
    public static final int READY_AFTER = 20;

    // ---- The arrival, counted in overseas' ticks (it starts as the portal closes) ----
    /** How long the wheel takes to fade once the portal has closed. */
    public static final int REVEAL_TICKS = 40;
    /** He rolls over in his sleep... sits up and yawns... stands and stretches... and the dark gathers round him. */
    public static final int STIR = 250, SIT = 330, STAND = 410, GATHER = 450;
    /** How far east of the middle everyone stands when the wheel fades. */
    public static final double WATCH_RADIUS = 15;

    // ---- Kills ----
    public static final double KILLS_BPM = 74.92;
    public static final int KILLS_FIRST_BEAT = 5;
    /** Kills breaks into the beat: he goes Super Saiyan. */
    public static final int KILLS_DROP = 341;
    /** The power-up sound is this long (8.19 s), so it starts this far into Kills to end on the drop. */
    public static final int POWERUP_TICKS = 164, POWERUP_AT = KILLS_DROP - POWERUP_TICKS;

    // ---- Half health, counted from the moment it starts until Kills does ----
    /** The sunset drains to black and the red lights come out over this long. */
    public static final int SKY_FADE = 60;
    /** Kills starts this long into the transition. */
    public static final int KILLS_AT = 30;
    /** Song ticks: everyone can move again (he's out of reach till he bursts)... */
    public static final int FREE_AT = 60;
    /** ...he lifts off the ground, rising to {@link #HOVER} over it... */
    public static final int RISE_START = 40, RISE_END = 200;
    public static final double HOVER = 3.5;
    /** ...and comes down onto the ground again on the drop, Super Saiyan. He's loose this long after. */
    public static final int LOOSE_AFTER = 26;

    private RickyTimeline() {
    }

    private static double ease(double u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    /** How high over the ground he hangs, {@code s} ticks into Kills during the transition. */
    public static double riseHeight(int s) {
        if (s <= RISE_START || s >= KILLS_DROP) return 0;
        if (s < RISE_END) return HOVER * ease((s - RISE_START) / (double) (RISE_END - RISE_START));
        // Shaking with the strain, a little higher still
        return HOVER + 0.6 * ease((s - RISE_END) / (double) (KILLS_DROP - RISE_END));
    }

    /**
     * Whether his hair shows gold {@code s} ticks into Kills during the transition: nothing before the power-up, then
     * flickers coming quicker and lasting longer (the way it goes in the show), gold for good from the drop.
     */
    public static boolean flicker(int s) {
        if (s >= KILLS_DROP) return true;
        if (s < POWERUP_AT + 20) return false;
        double u = (s - POWERUP_AT - 20) / (double) (KILLS_DROP - POWERUP_AT - 20);
        int period = (int) Math.round(24 - 18 * u);
        int on = (int) Math.max(1, Math.round(period * (0.15 + 0.6 * u)));
        return (s - POWERUP_AT) % period < on;
    }

    /** How strongly the aura and the ground shake while he powers up, 0-1, {@code s} ticks into Kills. */
    public static double strain(int s) {
        if (s <= RISE_START) return 0;
        if (s >= KILLS_DROP) return 1;
        return ease((s - RISE_START) / (double) (KILLS_DROP - RISE_START));
    }

    /** How far from the middle everyone stands when the wheel fades (further for a big group). */
    public static double watchRadius(int n) {
        return Math.max(WATCH_RADIUS, n * 1.7 / (Math.PI * 0.5) * 0.6);
    }

    /** Where watcher {@code i} of {@code n} stands, as a unit vector (x, z) out from the middle: an arc to the east. */
    public static double[] watchSlot(int i, int n) {
        double spread = Math.toRadians(Math.min(70, 12.0 * (n - 1)));
        double a = n <= 1 ? 0 : -spread + 2 * spread * i / (n - 1);
        return new double[]{Math.cos(a), Math.sin(a)};
    }

    /**
     * His health, as a share of {@link RickyEntity#MAX_HEALTH}, for {@code players} in the fight: full for 5-7, down to
     * 60% alone, and 8% more for each player past 7 (124% for 10), at most double. The same as the others.
     */
    public static float healthScale(int players) {
        if (players <= 0) return 1f;
        if (players <= 7) return Math.min(1f, 0.5f + 0.1f * players);
        return Math.min(2f, 1f + 0.08f * (players - 7));
    }

    /** How many shadow hands rise: one for each of you, at most six (and one more in phase 2, at most seven). */
    public static int hands(int players, boolean saiyan) {
        return Math.max(1, Math.min(saiyan ? 7 : 6, players + (saiyan ? 1 : 0)));
    }

    /** How many snaking rows of spikes go out: four, six once he's Super Saiyan. */
    public static int snakes(boolean saiyan) {
        return saiyan ? 6 : 4;
    }
}
