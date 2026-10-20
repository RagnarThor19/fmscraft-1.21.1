package net.ragnar.fmscraft.structure.wrath;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of the Den of Wrath. Pure logic, no Minecraft classes, so it can be unit tested.
 *
 * One enormous round hall buried deep in the Nether, the Mother Warden's home: straight walls {@link #RADIUS} out from
 * the middle up to {@link #WALL_TOP}, then a dome. You come in high up on the east wall, through an ancient gate onto a
 * balcony that looks out over the whole hall, and a ledge winds down the wall, once all the way round, to the floor.
 * She sleeps in her nest in the middle. At the foot of the west wall a doorway one block wide (far too small for her)
 * leads through a short tunnel to the portal room.
 *
 * Coordinates are local: blocks from the middle of the hall's floor (the floor block is y = 0; you walk at y = 1), with
 * the gate on the +x side ("east"); the piece rotates them. Angles are in degrees from +x toward +z, and the ledge
 * goes the same way: it leaves the balcony heading toward +z and comes down to the floor on the -z side of the gate.
 */
public final class WrathDenShape {
    /** The inside of the hall's wall. */
    public static final int RADIUS = 44;
    public static final int SHELL = 4;
    public static final int OUTER = RADIUS + SHELL;
    /** The wall goes straight up to here; above it the dome closes in. */
    public static final int WALL_TOP = 40;
    /** How far the dome rises above {@link #WALL_TOP}. */
    public static final int DOME = 26;
    public static final int TOP = WALL_TOP + DOME + SHELL;
    public static final int BOTTOM = -SHELL;

    // ---- The balcony and the way in ----
    /** Floor block of the balcony and the entrance tunnel (you walk one higher). */
    public static final int BALCONY = 38;
    /** The balcony reaches in from the wall to here. */
    public static final int BALCONY_INNER = 35;
    public static final int BALCONY_HALF = 5;
    /** The entrance tunnel: 3 wide, 4 high, from the gate out to where it meets the Nether. */
    public static final int TUNNEL_HALF = 1;
    public static final int TUNNEL_HEIGHT = 4;
    /** How far the entrance tunnel may run on past the outside of the wall. */
    public static final int MIN_TUNNEL = 4, MAX_TUNNEL = 40;

    // ---- The ledge ----
    /** The walkway runs between here and the wall; a broken rail stands just inside it. */
    public static final double PATH_INNER = 40.5;
    public static final double RAIL_INNER = 39.5;
    public static final double PATH_START = 7, PATH_END = 330;
    /** Flat landings: one with a chest in an alcove, and ones with a tripwire across them. */
    public static final double[] CHEST_ANGLES = {50, 140, 230};
    public static final double[] TRIP_ANGLES = {90, 180, 270};
    private static final double CHEST_LANDING = 3, TRIP_LANDING = 2.5;
    private static final double[] KNOT_ANGLE, KNOT_Y;

    // ---- The way out ----
    /** The doorway at the foot of the west wall, and the end of its tunnel (just inside the portal room's wall). */
    public static final int EXIT_END = -57;
    /** The portal room's inside. */
    public static final int ROOM_EAST = -58, ROOM_WEST = -70, ROOM_HALF = 6, ROOM_HEIGHT = 7;
    /** Leftmost block of the whole den. */
    public static final int WEST = ROOM_WEST - 2;

    // ---- The nest ----
    public static final double NEST_RIM_IN = 8, NEST_RIM_OUT = 10.5;
    /** The cracked old paving in front of the doorway. */
    public static final int PLAZA_EAST = -32, PLAZA_HALF = 7;

    /** The Nether fossil: a ribcage of bone arching over the floor on the +z side. */
    public static final int FOSSIL_Z = 27, RIB_RADIUS = 7;
    public static final int[] RIBS = {-9, -6, -3, 0, 3, 6};
    public static final int SPINE_WEST = -11, SPINE_EAST = 8;

    public enum Part {
        /** Beyond the den: left as it was. */
        OUTSIDE,
        AIR,
        /** The hall's shell: its outermost layer, the fill, and the inner face you see. */
        SKIN, FILL, FACE,
        FLOOR, FOUNDATION,
        PATH_FLOOR, PATH_SLAB, PATH_BODY, PATH_UNDERSIDE, RAIL,
        BALCONY_FLOOR, BALCONY_RAIL,
        /** The ancient gate round the top of the entrance tunnel: reinforced deepslate, and its trim. */
        GATE_FRAME, GATE_TRIM,
        TUNNEL_FLOOR, TUNNEL_WALL, TUNNEL_ROOF, LINING,
        /** The archway at the tunnel's outer end. */
        MOUTH,
        EXIT_FRAME, EXIT_FLOOR, EXIT_WALL,
        ROOM_FLOOR, ROOM_BORDER, ROOM_WALL, ROOM_CEILING, ROOM_PILLAR,
        NEST_FLOOR, NEST_RIM, PLAZA,
        SPIRE, STALACTITE, BONE;

        public boolean solid() {
            return this != OUTSIDE && this != AIR;
        }
    }

    /** A block position with a horizontal direction: 0 = +x, 1 = +z, 2 = -x, 3 = -z (east, south, west, north). */
    public record Spot(int x, int y, int z, int dir) {
    }

    /** A tripwire across the ledge: a post, a hook on it, the string, and a hook on the wall. Hooks face {@code dir} along it. */
    public record Tripwire(Spot post, Spot innerHook, List<Spot> string, Spot outerHook) {
    }

    /** A cone of rock: rising from the floor (a spire) or hanging from the dome (a stalactite). */
    public record Cone(int x, int z, double radius, int length) {
    }

    /** A soul lantern hanging on a chain from the dome, the lantern at {@code y}. */
    public record Hanging(int x, int y, int z) {
    }

    static final int[] DX = {1, 0, -1, 0};
    static final int[] DZ = {0, 1, 0, -1};

    public static final List<Cone> SPIRES = List.of(
            new Cone(22, 16, 3.5, 20), new Cone(-18, 26, 3.0, 15), new Cone(-24, -18, 4.0, 24),
            new Cone(12, -30, 3.0, 13), new Cone(-6, -24, 2.5, 10), new Cone(30, 2, 2.5, 8));
    public static final List<Cone> STALACTITES = List.of(
            new Cone(2, -3, 4.0, 16), new Cone(-10, 8, 3.0, 14), new Cone(16, -10, 3.5, 18),
            new Cone(-28, -2, 3.0, 10), new Cone(8, 30, 2.5, 9), new Cone(26, 26, 3.0, 8), new Cone(-20, -30, 3.0, 9));
    /** Where each stalactite hangs from: the underside of the dome over its middle. */
    private static final int[] DRIP_CEILING = new int[STALACTITES.size()];
    public static final List<Hanging> CHAINS = List.of(
            new Hanging(-20, 30, 6), new Hanging(14, 28, 14), new Hanging(-12, 26, -12), new Hanging(22, 32, -22),
            new Hanging(-30, 34, 14), new Hanging(6, 40, -14), new Hanging(-4, 36, 16));

    // ---- Spots for blocks and things the runtime needs ----
    /** Where the Mother Warden sleeps, facing the gate. */
    public static final Spot MOTHER = new Spot(0, 1, 0, 0);
    /** A chest in an alcove off each chest landing, facing out over the hall. */
    public static final List<Spot> LEDGE_CHESTS = new ArrayList<>();
    /** Under the ribs of the fossil. */
    public static final Spot FOSSIL_CHEST = new Spot(-2, 1, FOSSIL_Z, 3);
    /** Her hoard, right there in the nest beside her. */
    public static final Spot NEST_CHEST = new Spot(-5, 1, -2, 2);
    /** Polished blackstone pressure plates, each on polished blackstone so they're hard to see. */
    public static final List<Spot> PLATES = new ArrayList<>();
    public static final List<Tripwire> TRIPWIRES = new ArrayList<>();
    /** Soul torches on the wall along the ledge, facing {@code dir} (away from the wall). */
    public static final List<Spot> TORCHES = new ArrayList<>();
    public static final List<Spot> CATALYSTS = new ArrayList<>();
    public static final List<Spot> SHRIEKERS = new ArrayList<>();
    public static final List<Spot> CAMPFIRES = new ArrayList<>();
    /** Soul lanterns standing either side of the doorway, so it can be found from above. */
    public static final List<Spot> EXIT_LANTERNS = List.of(new Spot(-43, 1, -2, 0), new Spot(-43, 1, 2, 0));

    // The portal room
    public static final Spot LOCK = new Spot(-64, 1, 0, 0);
    public static final List<Spot> ROOM_CHESTS = List.of(new Spot(-64, 1, -2, 0), new Spot(-64, 1, 2, 0));
    /** The middle of the portal frame's bottom row (set into the floor), 4 blocks behind the lock. */
    public static final Spot FRAME = new Spot(-68, 0, 0, 0);

    static {
        for (int i = 0; i < STALACTITES.size(); i++) DRIP_CEILING[i] = ceilingAt(Math.hypot(STALACTITES.get(i).x, STALACTITES.get(i).z));
        List<double[]> knots = new ArrayList<>();
        knots.add(new double[]{PATH_START, BALCONY + 1});
        knots.add(new double[]{PATH_START + 4, BALCONY + 1});
        List<double[]> landings = new ArrayList<>();
        for (double a : CHEST_ANGLES) landings.add(new double[]{a, CHEST_LANDING});
        for (double a : TRIP_ANGLES) landings.add(new double[]{a, TRIP_LANDING});
        landings.sort((p, q) -> Double.compare(p[0], q[0]));
        for (double[] landing : landings) {
            double y = Math.round(grade(landing[0]));
            knots.add(new double[]{landing[0] - landing[1], y});
            knots.add(new double[]{landing[0] + landing[1], y});
        }
        knots.add(new double[]{PATH_END - 4, 1});
        knots.add(new double[]{PATH_END, 1});
        KNOT_ANGLE = new double[knots.size()];
        KNOT_Y = new double[knots.size()];
        for (int i = 0; i < knots.size(); i++) {
            KNOT_ANGLE[i] = knots.get(i)[0];
            KNOT_Y[i] = knots.get(i)[1];
        }

        for (int i = 0; i < CHEST_ANGLES.length; i++) {
            double a = Math.toRadians(CHEST_ANGLES[i]);
            int s = (int) surface(CHEST_ANGLES[i]);
            double cos = Math.cos(a), sin = Math.sin(a);
            LEDGE_CHESTS.add(new Spot(round((RADIUS + 1.5) * cos), s, round((RADIUS + 1.5) * sin), cardinal(-cos, -sin)));
            // A plate right in front of the first and last chests: easy to step on while you loot
            if (i != 1) PLATES.add(new Spot(round((RADIUS - 1.5) * cos), s, round((RADIUS - 1.5) * sin), 0));
        }
        for (double angle : TRIP_ANGLES) {
            int d = cardinal(Math.cos(Math.toRadians(angle)), Math.sin(Math.toRadians(angle)));
            int s = (int) surface(angle);
            TRIPWIRES.add(new Tripwire(new Spot(39 * DX[d], s, 39 * DZ[d], d), new Spot(40 * DX[d], s, 40 * DZ[d], d),
                    List.of(new Spot(41 * DX[d], s, 41 * DZ[d], d), new Spot(42 * DX[d], s, 42 * DZ[d], d)),
                    new Spot(43 * DX[d], s, 43 * DZ[d], (d + 2) % 4)));
        }
        // Plates in the old paving before the doorway, one of them right in the way
        for (int[] p : new int[][]{{-42, 0}, {-38, -2}, {-35, 3}, {-40, 4}, {-33, -5}, {-37, 6}}) {
            PLATES.add(new Spot(p[0], 1, p[1], 0));
        }
        // And one under the ribs, by the chest
        PLATES.add(new Spot(-2, 1, FOSSIL_Z + 2, 0));

        placeTorches();
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + 90 * i);
            CATALYSTS.add(new Spot(round(9 * Math.cos(a)), 1, round(9 * Math.sin(a)), 0));
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            SHRIEKERS.add(new Spot(round(13 * Math.cos(a)), 1, round(13 * Math.sin(a)), 0));
        }
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(30 + 120 * i);
            CAMPFIRES.add(new Spot(round(14 * Math.cos(a)), 1, round(14 * Math.sin(a)), cardinal(-Math.cos(a), -Math.sin(a))));
        }
    }

    private WrathDenShape() {
    }

    static int round(double v) {
        return (int) Math.floor(v + 0.5);
    }

    /** The direction index nearest to (dx, dz). */
    static int cardinal(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? 0 : 2;
        return dz > 0 ? 1 : 3;
    }

    /** Angle of column (x, z) round the middle, in degrees in [0, 360). */
    public static double angle(double x, double z) {
        double a = Math.toDegrees(Math.atan2(z, x));
        return a < 0 ? a + 360 : a;
    }

    // ---- The ledge ----

    /** The steady descent the landings are cut into: from the balcony down to the floor. */
    private static double grade(double angle) {
        return BALCONY + 1 - (BALCONY) * (angle - (PATH_START + 4)) / ((PATH_END - 4) - (PATH_START + 4));
    }

    /** Walking height of the ledge at {@code angle}: flat at the landings and both ends, steady in between. */
    public static double surface(double angle) {
        if (angle <= KNOT_ANGLE[0]) return KNOT_Y[0];
        for (int i = 1; i < KNOT_ANGLE.length; i++) {
            if (angle <= KNOT_ANGLE[i]) {
                double t = (angle - KNOT_ANGLE[i - 1]) / (KNOT_ANGLE[i] - KNOT_ANGLE[i - 1]);
                return KNOT_Y[i - 1] + (KNOT_Y[i] - KNOT_Y[i - 1]) * t;
            }
        }
        return KNOT_Y[KNOT_Y.length - 1];
    }

    /** Walking height in half blocks (an odd value means a slab on top). */
    public static int halfSurface(double angle) {
        return (int) Math.floor(surface(angle) * 2);
    }

    public static boolean onLedge(double angle) {
        return angle >= PATH_START && angle <= PATH_END;
    }

    // ---- The hall ----

    private static double dome(double r, int y, double radius, double rise) {
        double a = r / radius, b = (y - WALL_TOP) / rise;
        return a * a + b * b;
    }

    /** Inside the hall's air (before anything stands in it). */
    static boolean hollow(double r, int y) {
        if (y < 1) return false;
        return y <= WALL_TOP ? r < RADIUS : dome(r, y, RADIUS, DOME) < 1;
    }

    private static boolean withinShell(double r, int y) {
        if (y < BOTTOM) return false;
        return y <= WALL_TOP ? r < OUTER : dome(r, y, OUTER, DOME + SHELL) < 1;
    }

    private static boolean nearHollow(double r, int y) {
        if (y < 0) return false;
        return y <= WALL_TOP ? r < RADIUS + 1 : dome(r, y, RADIUS + 1, DOME + 1) < 1;
    }

    private static boolean onSkin(double r, int y) {
        if (y == BOTTOM) return true;
        return y <= WALL_TOP ? r >= OUTER - 1 : dome(r, y, OUTER - 1, DOME + SHELL - 1) >= 1;
    }

    /** Height of the underside of the dome over a column {@code r} out from the middle. */
    public static int ceilingAt(double r) {
        if (r >= RADIUS) return WALL_TOP;
        int y = WALL_TOP + DOME;
        while (y > WALL_TOP && !hollow(r, y)) y--;
        return y;
    }

    /**
     * Whether (x, y, z) is in the hall itself (the alcoves and balcony too, not the tunnels): where you have to keep
     * quiet, and can't build or dig.
     */
    public static boolean inHall(int x, int y, int z) {
        double r = Math.sqrt(x * x + z * z);
        if (y < 0 || r >= RADIUS + 2.5) return false;
        if (x >= RADIUS && Math.abs(z) <= 3) return false; // the gate and the tunnel behind it
        if (x <= -RADIUS && Math.abs(z) <= 1) return false; // the doorway out
        return y <= ceilingAt(Math.min(r, RADIUS - 1)) + 1;
    }

    /** Whether (x, y, z) is in the portal room or the tunnel to it. */
    public static boolean inSanctum(int x, int y, int z) {
        if (y < 0 || y > ROOM_HEIGHT + 1) return false;
        if (x >= ROOM_WEST - 1 && x <= ROOM_EAST + 1 && Math.abs(z) <= ROOM_HALF + 1) return true;
        return x <= -RADIUS && x > ROOM_EAST && Math.abs(z) <= 1;
    }

    // ---- Every block ----

    /** What's at (x, y, z), given how far the entrance tunnel runs on past the outside of the wall. */
    public static Part part(int x, int y, int z, int tunnel) {
        if (y < BOTTOM || y > TOP) return Part.OUTSIDE;
        Part p = sanctum(x, y, z);
        if (p != null) return p;
        p = entrance(x, y, z, tunnel);
        if (p != null) return p;
        double r = Math.sqrt(x * x + z * z);
        if (!withinShell(r, y)) return Part.OUTSIDE;
        if (hollow(r, y)) return inside(x, y, z, r);
        if (y == 0 && r < RADIUS) return floor(x, z, r);
        p = alcove(x, y, z, r);
        if (p != null) return p;
        p = exit(x, y, z);
        if (p != null) return p;
        if (onSkin(r, y)) return Part.SKIN;
        if (y < 0) return Part.FOUNDATION;
        return nearHollow(r, y) ? Part.FACE : Part.FILL;
    }

    private static Part floor(int x, int z, double r) {
        if (r < NEST_RIM_OUT) return Part.NEST_FLOOR;
        if (x <= PLAZA_EAST && Math.abs(z) <= PLAZA_HALF) return Part.PLAZA;
        return Part.FLOOR;
    }

    /** Everything standing in the hall's air. */
    private static Part inside(int x, int y, int z, double r) {
        Part p = balcony(x, y, z, r);
        if (p == null) p = ledge(x, y, z, r);
        if (p != null) return p;
        // The rim of her nest, with four gaps in it, one each way (a shrieker in front of two of them)
        if (y == 1 && r >= NEST_RIM_IN && r < NEST_RIM_OUT && Math.abs(x) > 1 && Math.abs(z) > 1) return Part.NEST_RIM;
        if (isBone(x, y, z)) return Part.BONE;
        for (Cone spire : SPIRES) {
            double d = Math.hypot(x - spire.x, z - spire.z);
            if (d < spire.radius * (1 - (y - 1) / (double) spire.length)) return Part.SPIRE;
        }
        for (int i = 0; i < STALACTITES.size(); i++) {
            Cone drip = STALACTITES.get(i);
            double d = Math.hypot(x - drip.x, z - drip.z);
            int ceiling = DRIP_CEILING[i];
            if (y <= ceiling && d < drip.radius * (1 - (ceiling - y) / (double) drip.length)) return Part.STALACTITE;
        }
        return Part.AIR;
    }

    /** The ribs of the fossil (arches in the x = rib planes) and the spine along their tops. */
    static boolean isBone(int x, int y, int z) {
        if (y < 1 || y > RIB_RADIUS) return false;
        if (y == RIB_RADIUS && z == FOSSIL_Z && x >= SPINE_WEST && x <= SPINE_EAST) return true;
        for (int rib : RIBS) {
            if (x != rib) continue;
            double d = Math.hypot(y, z - FOSSIL_Z);
            if (Math.abs(d - RIB_RADIUS) < 0.6) return true;
        }
        return false;
    }

    /** The balcony inside the gate, with a parapet on the hall side and the -z side; the +z side opens onto the ledge. */
    private static Part balcony(int x, int y, int z, double r) {
        if (x < BALCONY_INNER || Math.abs(z) > BALCONY_HALF || y < BALCONY - 3 || y > BALCONY + 1) return null;
        if (y == BALCONY) return Part.BALCONY_FLOOR;
        if (y == BALCONY - 1) return Part.PATH_BODY;
        if (y == BALCONY - 2) return x >= BALCONY_INNER + 2 ? Part.PATH_BODY : null;
        if (y == BALCONY - 3) return x >= BALCONY_INNER + 5 ? Part.PATH_UNDERSIDE : null;
        boolean parapet = x == BALCONY_INNER || z == -BALCONY_HALF || (z == BALCONY_HALF && r < PATH_INNER);
        return parapet ? Part.BALCONY_RAIL : Part.AIR;
    }

    /**
     * The ledge: a 3-block-thick walkway against the wall, stepping down in half blocks, with a corbel under its outer
     * edge and the stumps of a rail along its inner edge.
     */
    static Part ledge(int x, int y, int z, double r) {
        if (r < RAIL_INNER) return null;
        double angle = angle(x, z);
        if (!onLedge(angle)) return null;
        int half = halfSurface(angle);
        int top = Math.floorDiv(half, 2);
        boolean slab = (half & 1) == 1;
        if (y > top + 3 || y < top - 4) return null;
        if (r >= PATH_INNER) {
            if (y == top && slab) return Part.PATH_SLAB;
            if (y >= top) return Part.AIR;
            if (y == top - 1) return Part.PATH_FLOOR;
            if (y >= top - 3) return Part.PATH_BODY;
            return r >= RADIUS - 2 ? Part.PATH_UNDERSIDE : null;
        }
        int railBase = slab ? top + 1 : top;
        if (y > railBase) return Part.AIR;
        if (y == railBase) return Part.RAIL;
        return y >= top - 3 ? Part.PATH_BODY : null;
    }

    /** The alcove cut into the wall off each chest landing, the chest at its back. */
    private static Part alcove(int x, int y, int z, double r) {
        if (r < RADIUS - 0.5) return null;
        for (Spot chest : LEDGE_CHESTS) {
            if (Math.hypot(x - chest.x, z - chest.z) > 1.5) continue;
            if (y >= chest.y && y <= chest.y + 2) return Part.AIR;
            if (y == chest.y - 1) return Part.PATH_FLOOR;
        }
        return null;
    }

    /** The doorway at the foot of the west wall: one block wide, two high, framed in reinforced deepslate. */
    private static Part exit(int x, int y, int z) {
        if (x > -RADIUS || Math.abs(z) > 1 || y < 0 || y > 3) return null;
        if (z == 0 && (y == 1 || y == 2)) return Part.AIR;
        if (z == 0 && y == 0) return Part.EXIT_FLOOR;
        return x == -RADIUS ? Part.EXIT_FRAME : Part.EXIT_WALL;
    }

    /** The entrance tunnel, from the gate out to the Nether, and the gate itself on the hall's face. */
    private static Part entrance(int x, int y, int z, int tunnel) {
        int az = Math.abs(z);
        int end = OUTER + tunnel - 1;
        if (x < RADIUS || x > end || az > 3 || y < BALCONY - 1 || y > BALCONY + 7) return null;
        boolean open = az <= TUNNEL_HALF && y > BALCONY && y <= BALCONY + TUNNEL_HEIGHT;
        if (x == RADIUS) {
            // The gate: a tall frame of reinforced deepslate round the tunnel's mouth, like the ancient cities' portal
            if (open) return Part.AIR;
            if (y == BALCONY) return az <= TUNNEL_HALF ? Part.TUNNEL_FLOOR : Part.GATE_TRIM;
            if (y < BALCONY) return null;
            return az == 3 || y == BALCONY + 7 ? Part.GATE_FRAME : Part.GATE_TRIM;
        }
        if (open) return Part.AIR;
        if (y > BALCONY + TUNNEL_HEIGHT + 2) return null;
        if (x == end && x >= OUTER && (az == 2 || y == BALCONY + TUNNEL_HEIGHT + 1)) return Part.MOUTH;
        if (y == BALCONY && az <= TUNNEL_HALF) return Part.TUNNEL_FLOOR;
        if (az == 2 && y >= BALCONY && y <= BALCONY + TUNNEL_HEIGHT) return Part.TUNNEL_WALL;
        if (y == BALCONY + TUNNEL_HEIGHT + 1 && az <= 2) return Part.TUNNEL_ROOF;
        // Lining all round, to keep the Nether's lava out (inside the wall, the wall does that)
        return x >= OUTER ? Part.LINING : null;
    }

    /** The tunnel from the doorway, and the portal room at its end. */
    private static Part sanctum(int x, int y, int z) {
        int az = Math.abs(z);
        if (x <= -OUTER && x >= EXIT_END && az <= 2 && y >= -1 && y <= 4) {
            if (az == 0 && (y == 1 || y == 2)) return Part.AIR;
            if (az <= 1 && y >= 0 && y <= 3) return y == 0 && az == 0 ? Part.EXIT_FLOOR : Part.EXIT_WALL;
            if (x >= EXIT_END + 1) return Part.LINING;
        }
        if (x < WEST || x > ROOM_EAST + 2 || az > ROOM_HALF + 2 || y < -1 || y > ROOM_HEIGHT + 2) return null;
        boolean insideX = x >= ROOM_WEST && x <= ROOM_EAST, insideZ = az <= ROOM_HALF;
        if (insideX && insideZ && y >= 1 && y <= ROOM_HEIGHT) {
            boolean corner = (x == ROOM_WEST || x == ROOM_EAST) && az == ROOM_HALF;
            return corner ? Part.ROOM_PILLAR : Part.AIR;
        }
        boolean wallX = x >= ROOM_WEST - 1 && x <= ROOM_EAST + 1, wallZ = az <= ROOM_HALF + 1;
        if (wallX && wallZ && y >= 0 && y <= ROOM_HEIGHT + 1) {
            if (y == 0) return insideX && insideZ && !(x == ROOM_WEST || x == ROOM_EAST || az == ROOM_HALF) ? Part.ROOM_FLOOR : Part.ROOM_BORDER;
            if (y == ROOM_HEIGHT + 1) return Part.ROOM_CEILING;
            return Part.ROOM_WALL;
        }
        return Part.LINING;
    }

    // ---- Torches ----

    /** A soul torch on the wall every 20 degrees down the ledge, at head height, away from the landings. */
    private static void placeTorches() {
        for (double angle = 20; angle < PATH_END - 10; angle += 20) {
            boolean clear = true;
            for (double a : CHEST_ANGLES) clear &= Math.abs(angle - a) > 8;
            for (double a : TRIP_ANGLES) clear &= Math.abs(angle - a) > 8;
            if (!clear) continue;
            Spot torch = torchAt(angle);
            if (torch != null) TORCHES.add(torch);
        }
    }

    /** The open walkway cell nearest the wall at {@code angle} with solid wall right behind it, facing away from the wall. */
    private static Spot torchAt(double angle) {
        double rad = Math.toRadians(angle), cos = Math.cos(rad), sin = Math.sin(rad);
        // The wall is curved: behind may be either of the two outward directions
        int[] outs = {cardinal(cos, sin), Math.abs(cos) >= Math.abs(sin) ? (sin > 0 ? 1 : 3) : (cos > 0 ? 0 : 2)};
        for (double r = RADIUS - 0.6; r >= PATH_INNER + 1; r -= 0.25) {
            int x = round(r * cos), z = round(r * sin);
            int y = Math.floorDiv(halfSurface(angle(x, z)), 2) + 1;
            if (part(x, y, z, MIN_TUNNEL) != Part.AIR) continue;
            for (int out : outs) {
                if (part(x + DX[out], y, z + DZ[out], MIN_TUNNEL).solid()) return new Spot(x, y, z, (out + 2) % 4);
            }
        }
        return null;
    }
}
