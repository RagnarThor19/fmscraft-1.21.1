package net.ragnar.fmscraft.structure.pride;

/**
 * The shape of the Tower of Pride, after Bruegel's <i>Tower of Babel</i> and the spiral minaret of Samarra: a huge
 * tapering cone with one ramp winding round the outside of it all the way to the top. Pure logic, no Minecraft
 * classes, so it can be unit tested.
 *
 * Every column of the tower is solid up to its floor and open above. Going round the tower the ramp climbs steadily;
 * after each full turn it has stepped in by {@link #RAMP} blocks, so the turn below shows as a wall (the arcades) on
 * the inside of the ramp. After {@link #TURNS} turns it arrives on the round summit. Coordinates are relative to the
 * tower's center, and heights are counted in blocks above the ground at its foot.
 */
public final class PrideTowerShape {
    /** Radius at the ground. */
    public static final int RADIUS = 48;
    /** How far the ramp steps in every turn: 5 blocks of walkway plus the parapet. */
    public static final int RAMP = 6;
    public static final int TURNS = 5;
    /** The round summit at the top. */
    public static final int SUMMIT_RADIUS = RADIUS - RAMP * (TURNS + 1);
    public static final int FULL_HEIGHT = 210;

    private static final double TAU = Math.PI * 2;
    private static final double END = TAU * TURNS;

    public enum Kind { OUTSIDE, RAMP, PARAPET, SUMMIT }

    /** One column: what it is and its floor, in half blocks (an odd value means a bottom slab on top). */
    public record Column(Kind kind, int halfFloor, double phi) {
        /** Number of full blocks of tower in this column (the slab, if any, sits on top of them). */
        public int fullBlocks() {
            return Math.floorDiv(halfFloor, 2);
        }

        public boolean slab() {
            return Math.floorMod(halfFloor, 2) == 1;
        }

        public boolean walkable() {
            return kind == Kind.RAMP || kind == Kind.SUMMIT;
        }
    }

    public final int height;
    /** Blocks climbed per block walked along the middle of the ramp. */
    public final double grade;

    public PrideTowerShape(int height) {
        this.height = height;
        this.grade = height / pathLength(END);
    }

    /** Distance walked along the middle of the ramp to reach cumulative angle {@code phi}. */
    private static double pathLength(double phi) {
        return (RADIUS - RAMP / 2.0) * phi - RAMP * phi * phi / (2 * TAU);
    }

    /** Height of the ramp's floor at cumulative angle {@code phi} (0 at the foot, a full turn per 2 pi). */
    public double rise(double phi) {
        return phi >= END ? height : grade * pathLength(Math.max(0, phi));
    }

    /** Walking distance along the ramp at cumulative angle {@code phi}, for spacing details like lanterns. */
    public double distanceAlong(double phi) {
        return pathLength(Math.min(phi, END));
    }

    public static double angle(double dx, double dz) {
        double theta = Math.atan2(dz, dx);
        return theta < 0 ? theta + TAU : theta;
    }

    /** Which turn of the ramp passes over (dx, dz), or -1 outside the tower. Turn {@link #TURNS} is the summit. */
    public static int turn(double dx, double dz) {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r <= SUMMIT_RADIUS) return TURNS;
        int m = (int) Math.floor((RADIUS - r) / RAMP - angle(dx, dz) / TAU);
        return Math.min(m, TURNS);
    }

    /** Floor height (continuous) of the column centered on (dx, dz); 0 outside the tower. */
    public double floor(double dx, double dz) {
        int m = turn(dx, dz);
        if (m < 0) return 0;
        if (m >= TURNS) return height;
        return rise(angle(dx, dz) + TAU * m);
    }

    private int halfFloor(int dx, int dz) {
        return (int) Math.floor(floor(dx + 0.5, dz + 0.5) * 2);
    }

    /**
     * The column at block (dx, dz) from the center. A walkway column with a neighbor more than half a block lower
     * becomes parapet, so nobody can walk off an edge.
     */
    public Column column(int dx, int dz) {
        double cx = dx + 0.5, cz = dz + 0.5;
        int m = turn(cx, cz);
        if (m < 0) return new Column(Kind.OUTSIDE, 0, 0);
        int half = halfFloor(dx, dz);
        double phi = m >= TURNS ? END : angle(cx, cz) + TAU * m;
        boolean edge = halfFloor(dx + 1, dz) < half - 1 || halfFloor(dx - 1, dz) < half - 1
                || halfFloor(dx, dz + 1) < half - 1 || halfFloor(dx, dz - 1) < half - 1;
        Kind kind = edge ? Kind.PARAPET : m >= TURNS ? Kind.SUMMIT : Kind.RAMP;
        return new Column(kind, half, phi);
    }

    /**
     * How far (dx, dz) lies inside the tower's outer surface at height {@code y}: below 0 is open air beside the
     * tower, [0, 1) is the outer layer of the wall. {@link Double#NEGATIVE_INFINITY} above the summit.
     */
    public double depth(double dx, double dz, int y) {
        double theta = angle(dx, dz);
        double r = Math.sqrt(dx * dx + dz * dz);
        for (int m = 0; m <= TURNS; m++) {
            if (rise(theta + TAU * m) > y) {
                double surface = m >= TURNS ? Math.max(SUMMIT_RADIUS, RADIUS - RAMP * (theta / TAU + m)) : RADIUS - RAMP * (theta / TAU + m);
                return surface - r;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** Floor of the ramp running along the foot of the wall at (dx, dz), height {@code y}: what stands in front of it. */
    public double floorInFront(double dx, double dz, int y) {
        double theta = angle(dx, dz);
        for (int m = 0; m <= TURNS; m++) {
            if (rise(theta + TAU * m) > y) return m == 0 ? 0 : rise(theta + TAU * (m - 1));
        }
        return height;
    }

    /** Arc position along the wall surface at (dx, dz), height y, for laying out bays of arches. */
    public double along(double dx, double dz, int y) {
        double depth = depth(dx, dz, y);
        double r = Math.sqrt(dx * dx + dz * dz);
        return angle(dx, dz) * (r + depth);
    }
}
