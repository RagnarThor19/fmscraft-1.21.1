package net.ragnar.fmscraft.fragment;

/**
 * Pure timing logic for Wrath: 3 warden kills within {@link #WINDOW_TICKS}, by anyone. The first kill starts the
 * clock; it doesn't restart on later kills. Kept free of Minecraft classes so it can be unit tested.
 */
public class WrathTracker {
    public static final int KILLS_NEEDED = 3;
    public static final int WINDOW_TICKS = 4 * 60 * 20;

    public enum Result { STARTED, COUNTED, COMPLETED }

    private long startTick = -1;
    private int kills;

    public Result onKill(long now) {
        if (isActive() && ticksLeft(now) <= 0) reset();

        if (kills == 0) startTick = now;
        kills++;
        if (kills >= KILLS_NEEDED) {
            reset();
            return Result.COMPLETED;
        }
        return kills == 1 ? Result.STARTED : Result.COUNTED;
    }

    /** Call every tick; returns true once, on the tick the window runs out without enough kills. */
    public boolean checkExpired(long now) {
        if (isActive() && ticksLeft(now) <= 0) {
            reset();
            return true;
        }
        return false;
    }

    public boolean isActive() {
        return kills > 0;
    }

    public int kills() {
        return kills;
    }

    public long ticksLeft(long now) {
        return isActive() ? startTick + WINDOW_TICKS - now : 0;
    }

    public void reset() {
        startTick = -1;
        kills = 0;
    }
}
