package net.ragnar.fmscraft.structure.sloth;

/**
 * One player's drowsiness inside the Sphere of Sloth, 0 to {@link #FULL}. Pure logic, no Minecraft classes, so it
 * can be unit tested.
 *
 * It creeps up the whole time you're in the sphere, and three times as fast while you stand still: idling is what
 * the sphere wants. In the core it drains away. Ringing a bell wakes you right up. As it builds you slow down, then
 * slow down more, then the dark closes in, and when it's full you fall asleep where you stand.
 */
public class Drowsiness {
    public static final float FULL = 100;
    /** Moving, it fills in three minutes. */
    public static final int MOVING_TICKS = 180 * 20;
    /** Standing still, in one. */
    public static final int STILL_TICKS = 60 * 20;
    /** In the core it drains away in twenty seconds. */
    public static final int RESTING_TICKS = 20 * 20;
    public static final float SLOWNESS = 40;
    public static final float HEAVY_SLOWNESS = 70;
    public static final float DARKNESS = 90;
    /** Where it drops back to after you've nodded off. */
    public static final float AFTER_NAP = 60;

    private float value;

    /**
     * One tick in the sphere. Returns true on the tick you fall asleep (drowsiness then drops to {@link #AFTER_NAP}).
     */
    public boolean tick(boolean still, boolean resting) {
        if (resting) {
            value = Math.max(0, value - FULL / RESTING_TICKS);
            return false;
        }
        value += FULL / (still ? STILL_TICKS : MOVING_TICKS);
        if (value >= FULL) {
            value = AFTER_NAP;
            return true;
        }
        return false;
    }

    public void wake() {
        value = 0;
    }

    public float value() {
        return value;
    }

    public void set(float value) {
        this.value = Math.max(0, Math.min(FULL, value));
    }

    public float fraction() {
        return value / FULL;
    }

    /** The Slowness amplifier drowsiness gives, or -1 for none. */
    public int slowness() {
        if (value >= HEAVY_SLOWNESS) return 1;
        return value >= SLOWNESS ? 0 : -1;
    }

    public boolean darkness() {
        return value >= DARKNESS;
    }
}
