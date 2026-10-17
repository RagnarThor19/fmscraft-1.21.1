package net.ragnar.fmscraft.structure.sloth;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of the Sphere of Sloth. Pure logic, no Minecraft classes, so it can be unit tested.
 *
 * A hollow black sphere hangs alone in the void. Inside it, a smaller obsidian sphere (the core) hangs on chains,
 * and a stair winds three times round the core, floating in the gap between the two, from a bell at the bottom to a
 * landing on top of the core, where a crying obsidian seal closes the shaft down into it. A bridge from the mouth (an
 * opening in the outer shell, level with the middle of the stair) joins it halfway up. The core holds a flat floor
 * with a pool in the middle, right under the shaft, to land in.
 *
 * Coordinates are local: blocks from the center block, with the mouth on the +x side ("east"); the piece rotates
 * them to face the mouth toward the main island. The stair's angle is measured from -x, counting up toward +z, and
 * {@code u} counts turns along it from its bottom end: u = 0 at the bottom bell, u = {@link #TURNS} at the top.
 */
public final class SlothSphereShape {
    public static final int RADIUS = 48;
    /** The inside face of the outer shell (the shell is 4 thick). */
    public static final int INNER = RADIUS - 4;
    /** Outside of the core's shell. */
    public static final int BALL = 24;
    /** Inside of the core's shell. */
    public static final int CORE = 20;

    // The stair: a ribbon floating round the core, a rail on each side of a 3-wide walkway
    public static final int PATH_INNER = 26;
    public static final int PATH_OUTER = 31;
    public static final double PATH_MID = 28.5;
    public static final int TURNS = 3;
    /** Walking height at the bottom end of the stair. */
    public static final int BOTTOM = -23;
    public static final int PITCH = 16;
    /** Walking height at the top: the landing on the core. */
    public static final int TOP = BOTTOM + PITCH * TURNS;
    /** Where along the stair it passes the mouth (straight out along +x). */
    public static final double MOUTH_U = 1.5;
    /** Floor blocks of the mouth and its bridge (walk one higher). */
    public static final int MOUTH_FLOOR = (int) (BOTTOM + PITCH * MOUTH_U) - 1;
    /** Center height of the round mouth. */
    private static final int MOUTH_Y = MOUTH_FLOOR + 4;
    private static final double MOUTH_RADIUS = 4.5;

    /** Floor blocks of the core (walk one higher). */
    public static final int CORE_FLOOR = -6;
    public static final double POOL_RADIUS = 3.5;
    public static final int POOL_DEPTH = 3;
    /** The seal is a square plug this many blocks either side of the middle, through the top of the core. */
    public static final int SEAL_HALF = 2;
    public static final double LANDING_RADIUS = 8.5;

    private static final double TAU = Math.PI * 2;

    public enum Part {
        /** Beyond the sphere: left as it was. */
        OUTSIDE,
        AIR,
        /** The outer two layers of the shell, shaded like a lit planet. */
        SURFACE, SHELL_FILL, SHELL_FACE,
        MOUTH_FLOOR, MOUTH_LIP,
        /** The top block of the stair's walkway (or the bridges'). */
        PATH_FLOOR, PATH_SLAB, PATH_BODY, PATH_UNDERSIDE, RAIL,
        BAY_FLOOR, LANDING_FLOOR, CHAIN,
        BALL_FACE, BALL_FILL, CORE_FACE, SEAL,
        CORE_FLOOR, CORE_FILL, POOL, CARPET;

        public boolean solid() {
            return this != OUTSIDE && this != AIR && this != POOL && this != CARPET && this != CHAIN;
        }
    }

    /** A block position with a horizontal direction: 0 = +x, 1 = +z, 2 = -x, 3 = -z (east, south, west, north). */
    public record Spot(int x, int y, int z, int dir) {
    }

    /** A lantern hanging under {@code chain} chain blocks. */
    public record Hanging(int x, int y, int z, int chain) {
    }

    /** A widening of the stair (or its bottom end) with a bell on it. */
    record Bay(double cx, double cz, int surface, boolean end) {
    }

    static final int[] DX = {1, 0, -1, 0};
    static final int[] DZ = {0, 1, 0, -1};

    /** Where along the stair the two bays between its ends are. */
    static final double[] BAY_U = {1.1, 2.2};
    /** Half the length of a flat landing, in turns. */
    private static final double BAY_LANDING = 0.04, MOUTH_LANDING = 0.02, END_LANDING = 0.035;
    private static final double[] KNOT_U, KNOT_Y;

    static {
        double[][] knots = {
                {0, BOTTOM}, {END_LANDING, BOTTOM},
                {BAY_U[0] - BAY_LANDING, Math.round(grade(BAY_U[0]))}, {BAY_U[0] + BAY_LANDING, Math.round(grade(BAY_U[0]))},
                {MOUTH_U - MOUTH_LANDING, MOUTH_FLOOR + 1}, {MOUTH_U + MOUTH_LANDING, MOUTH_FLOOR + 1},
                {BAY_U[1] - BAY_LANDING, Math.round(grade(BAY_U[1]))}, {BAY_U[1] + BAY_LANDING, Math.round(grade(BAY_U[1]))},
                {TURNS - END_LANDING, TOP}, {TURNS, TOP}};
        KNOT_U = new double[knots.length];
        KNOT_Y = new double[knots.length];
        for (int i = 0; i < knots.length; i++) {
            KNOT_U[i] = knots[i][0];
            KNOT_Y[i] = knots[i][1];
        }
    }

    static final Bay[] BAYS;
    /** The four bells: the bottom of the stair, two bays, and the landing on top of the core. */
    public static final List<Spot> BELLS = new ArrayList<>();
    /** A candle (four, really) in front of each bell, burning down while it rings. */
    public static final List<Spot> CANDLES = new ArrayList<>();
    /** A chest by each bell except the top one. */
    public static final List<Spot> CHESTS = new ArrayList<>();
    public static final List<Hanging> LANTERNS = new ArrayList<>();

    static {
        BAYS = new Bay[1 + BAY_U.length];
        BAYS[0] = new Bay(-PATH_MID, 0, BOTTOM, true);
        for (int i = 0; i < BAY_U.length; i++) {
            double theta = angleAt(BAY_U[i]);
            BAYS[i + 1] = new Bay(PATH_OUTER * Math.cos(theta), PATH_OUTER * Math.sin(theta), (int) Math.round(surface(BAY_U[i])), false);
        }

        // Bottom bell: just past the end of the stair, which leaves it going toward -z
        Spot bottomBell = new Spot(round(-PATH_MID), BOTTOM, round(2.5), 3);
        BELLS.add(bottomBell);
        CANDLES.add(new Spot(bottomBell.x, BOTTOM, bottomBell.z - 1, 0));
        CHESTS.add(new Spot(bottomBell.x + 2, BOTTOM, bottomBell.z, 3));
        // Bay bells: out at the edge of each bay, facing back toward the walkway
        for (int i = 0; i < BAY_U.length; i++) {
            Bay bay = BAYS[i + 1];
            double theta = angleAt(BAY_U[i]);
            double ox = Math.cos(theta), oz = Math.sin(theta);
            int x = round(bay.cx + 2.5 * ox), z = round(bay.cz + 2.5 * oz);
            int toWalk = cardinal(-ox, -oz);
            int along = cardinal(-oz, ox);
            BELLS.add(new Spot(x, bay.surface, z, toWalk));
            CANDLES.add(new Spot(x + DX[toWalk], bay.surface, z + DZ[toWalk], 0));
            CHESTS.add(new Spot(x + 2 * DX[along], bay.surface, z + 2 * DZ[along], toWalk));
        }
        // Top bell: on the landing, across the seal from the bridge
        BELLS.add(new Spot(5, TOP, 0, 2));
        CANDLES.add(new Spot(4, TOP, 0, 0));

        hangLanterns();
    }

    private SlothSphereShape() {
    }

    private static int round(double v) {
        return (int) Math.floor(v + 0.5);
    }

    /** The direction index nearest to (dx, dz). */
    static int cardinal(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? 0 : 2;
        return dz > 0 ? 1 : 3;
    }

    // ---- The stair ----

    /**
     * Walking height at {@code u} turns from the bottom. The stair levels out into a flat landing at each bay, at the
     * mouth and at both ends, so the bays (whole blocks, for the bells and chests to stand on) meet it without a step,
     * and climbs at a steady grade in between.
     */
    public static double surface(double u) {
        if (u <= KNOT_U[0]) return KNOT_Y[0];
        for (int i = 1; i < KNOT_U.length; i++) {
            if (u <= KNOT_U[i]) {
                double t = (u - KNOT_U[i - 1]) / (KNOT_U[i] - KNOT_U[i - 1]);
                return KNOT_Y[i - 1] + (KNOT_Y[i] - KNOT_Y[i - 1]) * t;
            }
        }
        return KNOT_Y[KNOT_Y.length - 1];
    }

    /** The straight climb the landings are cut into. */
    private static double grade(double u) {
        return BOTTOM + PITCH * u;
    }


    /** Angle round the core at {@code u} turns from the bottom (which is on the -x side). */
    static double angleAt(double u) {
        return Math.PI + TAU * u;
    }

    /** How far round from the stair's bottom end (column (x, z)) is, as a fraction of a turn in [0, 1). */
    static double turnFraction(double x, double z) {
        double f = (Math.atan2(z, x) - Math.PI) / TAU;
        return f - Math.floor(f);
    }

    /** Walking height in half blocks of the stair at {@code u} (an odd value means a slab on top). */
    public static int halfSurface(double u) {
        return (int) Math.floor(surface(u) * 2);
    }

    /** The walking height (in half blocks) of turn {@code k} over column (x, z). */
    public static int halfSurface(int x, int z, int k) {
        return halfSurface(turnFraction(x, z) + k);
    }

    // ---- Every block ----

    public static Part part(int x, int y, int z) {
        double r = Math.sqrt(x * x + y * y + z * z);
        if (r >= RADIUS) return Part.OUTSIDE;
        if (r >= INNER) {
            if (inMouth(x, y, z)) return y <= MOUTH_FLOOR ? Part.MOUTH_FLOOR : Part.AIR;
            double m = mouthDistance(y, z);
            if (x > 0 && r >= RADIUS - 1.5 && m > MOUTH_RADIUS && m <= MOUTH_RADIUS + 1.25) return Part.MOUTH_LIP;
            if (r >= RADIUS - 2) return Part.SURFACE;
            return r >= INNER + 1 ? Part.SHELL_FILL : Part.SHELL_FACE;
        }
        if (isSeal(x, y, z)) return Part.SEAL;
        if (r >= BALL) return gap(x, y, z);
        if (r >= CORE) return r >= BALL - 1 ? Part.BALL_FACE : r < CORE + 1 ? Part.CORE_FACE : Part.BALL_FILL;
        return core(x, y, z);
    }

    private static double mouthDistance(int y, int z) {
        return Math.sqrt(z * z + (y - MOUTH_Y) * (y - MOUTH_Y));
    }

    /** The round opening through the shell on the +x side, with a flat floor across its bottom. */
    static boolean inMouth(int x, int y, int z) {
        return x > 0 && y >= MOUTH_FLOOR && mouthDistance(y, z) <= MOUTH_RADIUS;
    }

    public static boolean isSeal(int x, int y, int z) {
        return Math.abs(x) <= SEAL_HALF && Math.abs(z) <= SEAL_HALF && y >= CORE && y < TOP;
    }

    // ---- The gap between the shells ----

    private static Part gap(int x, int y, int z) {
        Part p = topBridge(x, y, z);
        if (p == null) p = landing(x, y, z);
        if (p == null) p = mouthBridge(x, y, z);
        if (p == null) p = bays(x, y, z);
        if (p == null) p = helix(x, y, z);
        if (p == null && isChain(x, y, z)) p = Part.CHAIN;
        return p == null ? Part.AIR : p;
    }

    /** The core hangs from four bundles of chains reaching up to the top of the outer shell. */
    static boolean isChain(int x, int y, int z) {
        int ax = Math.abs(x), az = Math.abs(z);
        return y > 0 && (ax == 8 || ax == 9) && (az == 8 || az == 9);
    }

    /** From the top of the stair (on the -x side) straight in to the landing. */
    private static Part topBridge(int x, int y, int z) {
        if (x < -30 || x > -8 || Math.abs(z) > 2 || y < TOP - 3 || y > TOP + 2) return null;
        if (y < TOP) return y == TOP - 1 ? Part.PATH_FLOOR : y == TOP - 3 ? Part.PATH_UNDERSIDE : Part.PATH_BODY;
        // The stair arrives from +z over the last few blocks, so that side stays open there
        boolean rail = (z == -2 && x <= -9) || (z == 2 && x >= -26 && x <= -9) || x == -30;
        return y == TOP && rail ? Part.RAIL : Part.AIR;
    }

    /** The round landing on top of the core, around the seal. */
    private static Part landing(int x, int y, int z) {
        double hr = Math.sqrt(x * x + z * z);
        if (hr >= LANDING_RADIUS + 1 || y < TOP - 3 || y > TOP + 2) return null;
        if (y < TOP) {
            if (y == TOP - 1) return hr < LANDING_RADIUS ? Part.LANDING_FLOOR : Part.PATH_FLOOR;
            return Part.PATH_BODY;
        }
        return y == TOP && hr >= LANDING_RADIUS ? Part.RAIL : Part.AIR;
    }

    /** From the mouth in to the stair, which passes it at the same height. */
    private static Part mouthBridge(int x, int y, int z) {
        if (x < PATH_OUTER - 1 || x > INNER || Math.abs(z) > 2 || y < MOUTH_FLOOR - 2 || y > MOUTH_FLOOR + 3) return null;
        if (y <= MOUTH_FLOOR) return y == MOUTH_FLOOR ? Part.PATH_FLOOR : y == MOUTH_FLOOR - 2 ? Part.PATH_UNDERSIDE : Part.PATH_BODY;
        boolean rail = Math.abs(z) == 2 && x >= PATH_OUTER && x < INNER - 1;
        return y == MOUTH_FLOOR + 1 && rail ? Part.RAIL : Part.AIR;
    }

    /** The flat, round bays the bells stand on. */
    private static Part bays(int x, int y, int z) {
        double hr = Math.sqrt(x * x + z * z);
        for (Bay bay : BAYS) {
            double d = Math.hypot(x - bay.cx, z - bay.cz);
            int s = bay.surface;
            if (d >= 5.5 || y < s - 3 || y > s + 3) continue;
            // Bays beside the stair only widen it outward; the inner rail stays
            if (!bay.end && hr < PATH_INNER + 1) continue;
            if (d >= 4.5 && walkwayNear(x, z, s)) continue; // where the stair runs into the bay
            if (y < s) return y == s - 1 ? Part.BAY_FLOOR : y == s - 3 ? Part.PATH_UNDERSIDE : Part.PATH_BODY;
            return y == s && d >= 4.5 ? Part.RAIL : Part.AIR;
        }
        return null;
    }

    /** Whether the walkway of some turn passes over (x, z) within two blocks of height {@code s}. */
    private static boolean walkwayNear(int x, int z, int s) {
        double hr = Math.sqrt(x * x + z * z);
        if (hr < PATH_INNER + 1 || hr >= PATH_OUTER - 1) return false;
        for (int k = 0; k < TURNS; k++) {
            if (Math.abs(halfSurface(x, z, k) / 2.0 - s) <= 2) return true;
        }
        return false;
    }

    /** Whether a bay's floor takes over column (x, z) from the stair at walking height {@code surface}. */
    private static boolean inBay(int x, int z, double surface) {
        double hr = Math.sqrt(x * x + z * z);
        for (Bay bay : BAYS) {
            if (!bay.end && hr < PATH_INNER + 1) continue;
            if (Math.hypot(x - bay.cx, z - bay.cz) < 4.5 && Math.abs(surface - bay.surface) < 4) return true;
        }
        return false;
    }

    /**
     * The stair itself. Each turn's walkway is 3 blocks thick (floor, body, underside) and climbs in half-block
     * steps; the rail columns either side stand one block above it. Turns are {@link #PITCH} blocks apart.
     */
    static Part helix(int x, int y, int z) {
        double hr = Math.sqrt(x * x + z * z);
        if (hr < PATH_INNER || hr >= PATH_OUTER) return null;
        double base = turnFraction(x, z);
        for (int k = 0; k < TURNS; k++) {
            int half = halfSurface(base + k);
            int top = Math.floorDiv(half, 2);
            boolean slab = (half & 1) == 1;
            if (y < top - 3 || y > top + 3) continue;
            if (inBay(x, z, half / 2.0)) continue;
            if (y == top - 3) return Part.PATH_UNDERSIDE;
            boolean walk = hr >= PATH_INNER + 1 && hr < PATH_OUTER - 1;
            if (walk) {
                if (y == top - 1) return Part.PATH_FLOOR;
                if (y < top) return Part.PATH_BODY;
                if (y == top && slab) return Part.PATH_SLAB;
                return Part.AIR;
            }
            int railBase = slab ? top + 1 : top;
            if (y < railBase) return Part.PATH_BODY;
            return y == railBase ? Part.RAIL : Part.AIR;
        }
        return null;
    }

    // ---- The core ----

    private static Part core(int x, int y, int z) {
        double hr = Math.sqrt(x * x + z * z);
        if (y > CORE_FLOOR) return y == CORE_FLOOR + 1 && hr >= 6 && hr < 8 ? Part.CARPET : Part.AIR;
        if (hr < POOL_RADIUS && y > CORE_FLOOR - POOL_DEPTH) return Part.POOL;
        return y == CORE_FLOOR ? Part.CORE_FLOOR : Part.CORE_FILL;
    }

    // ---- Lanterns ----

    private static void hangLanterns() {
        // Under the stair, every 14 blocks along it, lighting the turn below
        double perTurn = TAU * PATH_MID;
        for (double along = 7; along < perTurn * TURNS; along += 14) {
            double u = along / perTurn;
            if (u < 0.2 || u > TURNS - 0.15) continue;
            double theta = angleAt(u);
            int x = round(PATH_MID * Math.cos(theta)), z = round(PATH_MID * Math.sin(theta));
            if (x > 20 && Math.abs(z) < 6) continue; // the mouth bridge
            boolean nearBay = false;
            for (Bay bay : BAYS) nearBay |= Math.hypot(x - bay.cx, z - bay.cz) < 7.5;
            if (nearBay) continue;
            int k = (int) Math.floor(u);
            double cellU = turnFraction(x, z) + k;
            if (cellU - u > 0.5) cellU -= 1;
            if (u - cellU > 0.5) cellU += 1;
            int top = Math.floorDiv(halfSurface(cellU), 2);
            hang(x, top - 4, z, 1);
        }
        // From the top of the outer shell
        for (int i = 0; i < 8; i++) {
            double theta = Math.toRadians(22.5 + 45 * i);
            int x = round(20 * Math.cos(theta)), z = round(20 * Math.sin(theta));
            hang(x, highestBelow(x, z, INNER), z, 4);
        }
        // From the ceiling of the core
        for (int i = 0; i < 6; i++) {
            double theta = Math.toRadians(30 + 60 * i);
            int x = round(10 * Math.cos(theta)), z = round(10 * Math.sin(theta));
            hang(x, highestBelow(x, z, CORE), z, 8);
        }
    }

    /** The highest y in column (x, z) still inside {@code radius}. */
    private static int highestBelow(int x, int z, int radius) {
        int y = radius;
        while (x * x + y * y + z * z >= radius * radius) y--;
        return y;
    }

    /** A chain hanging down from {@code topChain}, with the lantern under it. */
    private static void hang(int x, int topChain, int z, int chain) {
        LANTERNS.add(new Hanging(x, topChain - chain, z, chain));
    }
}
