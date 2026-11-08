package net.ragnar.fmscraft.boss.greed;

/**
 * The numbers behind RAGGIMEL's Deluge (kept apart from him so they can be unit tested): where an orb comes down it hits
 * everyone within {@link #RADIUS}, hardest in the middle, falling away towards the edge, and throws them out.
 */
public final class GreedDeluge {
    public static final double RADIUS = 10;
    /** What an orb does right where it lands, and the share of that left at the very edge (before DAMAGE_DEALT). */
    public static final float CENTER_DAMAGE = 36f, EDGE_SHARE = 0.15f;

    private GreedDeluge() {
    }

    /** How close to the middle {@code distance} is: 1 right on it, 0 at the edge and beyond. */
    public static double closeness(double distance) {
        return distance >= RADIUS ? 0 : 1 - Math.max(0, distance) / RADIUS;
    }

    /** What it does at {@code distance} blocks from where it landed (0 outside the radius). */
    public static float damageAt(double distance) {
        if (distance > RADIUS) return 0;
        double u = closeness(distance);
        return (float) (CENTER_DAMAGE * (EDGE_SHARE + (1 - EDGE_SHARE) * Math.pow(u, 1.5)));
    }

    /** How hard it throws you out sideways at {@code distance}. */
    public static double throwAt(double distance) {
        return 0.4 + 1.5 * closeness(distance);
    }

    /** ...and up. */
    public static double liftAt(double distance) {
        return 0.4 + 0.8 * closeness(distance);
    }
}
