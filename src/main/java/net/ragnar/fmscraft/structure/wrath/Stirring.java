package net.ragnar.fmscraft.structure.wrath;

/**
 * How close the Mother Warden is to waking. Pure logic, no Minecraft classes, so it can be unit tested.
 *
 * Every noise she hears in her hall stirs her once, and {@link #WAKE_AT} stirs wake her. After each one she lies
 * listening for {@link #LISTEN_TICKS}: anything else she hears in that time is part of the same disturbance (opening a
 * chest and closing it again, the clatter of a fall) and doesn't count again. Stirs fade: after {@link #FORGET_TICKS}
 * of quiet she forgets one, and another after the same again.
 */
public final class Stirring {
    public static final int WAKE_AT = 3;
    public static final int LISTEN_TICKS = 3 * 20;
    public static final int FORGET_TICKS = 120 * 20;

    private int stirs;
    private long listeningUntil = Long.MIN_VALUE / 2;
    private long quietSince;

    /** She hears something at {@code now}. Returns whether it stirred her (false while she's still listening). */
    public boolean hear(long now) {
        quietSince = now;
        if (now < listeningUntil) return false;
        stirs++;
        listeningUntil = now + LISTEN_TICKS;
        return true;
    }

    /** Lets a stir fade once she's had long enough without a sound. */
    public void tick(long now) {
        if (stirs > 0 && now - quietSince >= FORGET_TICKS) {
            stirs--;
            quietSince = now;
        }
    }

    public boolean listening(long now) {
        return now < listeningUntil;
    }

    public int stirs() {
        return stirs;
    }

    public boolean awake() {
        return stirs >= WAKE_AT;
    }

    public void reset() {
        stirs = 0;
        listeningUntil = Long.MIN_VALUE / 2;
    }
}
