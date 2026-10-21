package net.ragnar.fmscraft.structure.gluttony;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of the Hall of Gluttony. Pure logic, no Minecraft classes, so it can be unit tested.
 *
 * A piglin cathedral standing out of a lava lake on a terrace, a bridge running from its doors to the shore. Outside
 * it's a cathedral: a tall nave under a steep roof, lower aisles either side held up by flying buttresses, two towers
 * with spires at the front, a rose window over the great door and a slender spire over the middle of the roof. Inside
 * it's one long banquet: a table the length of the nave, kitchens down the aisles, and at the far end a dais where the
 * Gorged Brute stands in front of a gilded gate. Behind the gate a short passage leads into the chapel at the back,
 * where the portal is.
 *
 * Coordinates are local: blocks from the middle of the hall's floor (the floor block is y = 0; you walk at y = 1),
 * with the great door on the +x side ("east") and the gate on the -x side; the piece rotates them. The nave runs along
 * x, and everything is mirrored across z = 0.
 */
public final class GluttonyHallShape {
    // ---- The nave ----
    /** The nave's inside reaches to here either side of the middle. */
    public static final int NAVE_HALF = 8;
    /** The arcade and the clerestory wall above it: two blocks thick. */
    public static final int ARCADE_IN = 9, ARCADE_OUT = 10;
    /** Where the hall's inside ends at each end of the nave. */
    public static final int HALL_WEST = -30, NAVE_EAST = 33;
    /** The vault springs from the walls here, and is a pointed arch of two circles of {@link #VAULT_RADIUS}. */
    public static final int SPRING = 22;
    public static final double VAULT_RADIUS = 12, VAULT_OFFSET = VAULT_RADIUS - (NAVE_HALF + 0.5);
    /** The roof's ridge; it falls a block a block either side, out to {@link #EAVE}. */
    public static final int RIDGE = 37, EAVE = 11;
    /** The roofs reach from the west wall to the facade (the nave's) and to the towers (the aisles'). */
    public static final int ROOF_WEST = -32, ROOF_EAST = 35;
    /** Piers of the arcade: two blocks along x from each of these. The bays are the six blocks between them. */
    public static final int[] PIERS = {-28, -20, -12, -4, 4, 12, 20, 28};
    /** The clerestory windows: in the middle two blocks of each bay. */
    public static final int CLERESTORY_BOTTOM = 19, CLERESTORY_TOP = 24;

    // ---- The aisles ----
    public static final int AISLE_IN = 11, AISLE_OUT = 16;
    public static final int WALL_IN = 17, WALL_OUT = 18;
    public static final int AISLE_EAST = 27;
    /** The outer walls stand this high; the aisle roofs lean against the clerestory from just above them. */
    public static final int WALL_TOP = 13;
    /** Twice the aisle roof's height at its eave ({@link #AISLE_EAVE} out from the middle). */
    private static final int AISLE_ROOF_HALF = 28;
    public static final int AISLE_EAVE = 19;
    public static final int AISLE_SPRING = 8;
    public static final int AISLE_WINDOW_BOTTOM = 3, AISLE_WINDOW_TOP = 8;
    public static final int BUTTRESS_OUT = 21;

    // ---- The ends ----
    /** The west wall, with the gate in it, and the facade, with the great door. */
    public static final int WEST_WALL_IN = -31, WEST_WALL_OUT = -32;
    public static final int FACADE_IN = 34, FACADE_OUT = 36;
    /** The middle of the rose window over the door. */
    public static final int ROSE_Y = 19;
    public static final double ROSE_RADIUS = 6.5;
    /** The towers: 11 blocks square either side of the facade. */
    public static final int TOWER_X = 33, TOWER_Z = 16, TOWER_HALF = 5;
    public static final int BELFRY_FLOOR = 31, BELFRY_ROOF = 40, TOWER_TOP = 41;
    public static final int SPIRE_TOP = 60;
    /** The slender spire over the middle of the roof. */
    public static final int FLECHE_X = 0;

    // ---- The gate and the chapel behind it ----
    /** The gate: three wide, four high, in the west wall's inner face. */
    public static final int GATE_HALF = 1, GATE_HEIGHT = 4;
    /** The passage from the gate to the chapel, through both walls. */
    public static final int PASSAGE_WEST = -35;
    /** The chapel's inside. */
    public static final int ROOM_EAST = -36, ROOM_WEST = -48, ROOM_HALF = 6, ROOM_HEIGHT = 8;
    public static final int CHAPEL_WEST = -50, CHAPEL_HALF = 8, CHAPEL_RIDGE = 19;

    // ---- The terrace and the bridge ----
    public static final int TERRACE_WEST = -56, TERRACE_EAST = 48, TERRACE_HALF = 26;
    /** How deep the foundations go: well below the lava's surface (which is at y = -3). */
    public static final int BOTTOM = -10;
    public static final int TOP = SPIRE_TOP + 2;
    public static final int LAVA = -3;
    /** The bridge: a 5-wide deck from the terrace to the shore, with a rail either side. */
    public static final int BRIDGE_HALF = 2;
    public static final int MIN_BRIDGE = 1, MAX_BRIDGE = 80;
    /** How far past the shore the bridge's deck runs on, cut into the ground. */
    public static final int LANDING = 2;

    // ---- The banquet ----
    public static final int TABLE_WEST = -18, TABLE_EAST = 26, TABLE_HALF = 1;
    /** The dais at the west end, where the Gorged Brute stands. */
    public static final int DAIS_WEST = HALL_WEST, DAIS_EAST = -24, DAIS_HALF = 5;

    public enum Part {
        /** Beyond the hall: left as it was. */
        OUTSIDE,
        AIR,
        TERRACE, TERRACE_EDGE, PARAPET, FOUNDATION,
        FLOOR, RUNNER, AISLE_FLOOR,
        /** Walls: the outside and inside faces, and the hidden fill in between. */
        WALL, FILL,
        PIER, PIER_BASE, CAPITAL, ARCH,
        VAULT, RIB,
        WINDOW, ROSE, TRACERY, DOOR_TRIM, GABLE_TRIM,
        ROOF, ROOF_RIDGE, AISLE_ROOF, AISLE_ROOF_SLAB,
        BUTTRESS, PINNACLE, FINIAL, FLYER,
        TOWER, TOWER_TRIM, TOWER_FLOOR, SPIRE, SPIRE_TRIM,
        FLECHE,
        GATE, GATE_FRAME,
        ROOM_FLOOR, ROOM_BORDER, ROOM_WALL, ROOM_CEILING, ROOM_PILLAR, CHAPEL_ROOF, CHAPEL_RIDGE_PART,
        TABLE, BENCH, DAIS, DAIS_EDGE,
        BRIDGE_DECK, BRIDGE_RAIL, BRIDGE_PIER;

        public boolean solid() {
            return this != OUTSIDE && this != AIR;
        }
    }

    /** A block position with a horizontal direction: 0 = +x, 1 = +z, 2 = -x, 3 = -z (east, south, west, north). */
    public record Spot(int x, int y, int z, int dir) {
    }

    static final int[] DX = {1, 0, -1, 0};
    static final int[] DZ = {0, 1, 0, -1};

    // ---- Spots for blocks and things the runtime needs ----
    /** Where the Gorged Brute stands, on the dais in front of the gate, facing down the hall. */
    public static final Spot GORGED = new Spot(-28, 2, 0, 0);
    /** His hoard: a chest either side of him on the dais. */
    public static final List<Spot> HOARD_CHESTS = List.of(new Spot(-29, 2, -4, 0), new Spot(-29, 2, 4, 0));
    /** The pantry barrels down the aisles, one in each bay on each side, and the kitchens beside them. */
    public static final List<Spot> PANTRIES = new ArrayList<>();
    public static final List<Spot> SMOKERS = new ArrayList<>();
    public static final List<Spot> HEARTHS = new ArrayList<>();
    public static final List<Spot> HAY = new ArrayList<>();
    /** Lanterns hanging over the nave and the aisles, each on a chain up to the vault. */
    public static final List<Spot> CHANDELIERS = new ArrayList<>();
    /** Where the feast's piglins and brutes stand when someone comes. */
    public static final List<Spot> PIGLIN_SPOTS = new ArrayList<>();
    public static final List<Spot> BRUTE_SPOTS = new ArrayList<>();
    /** Where more of them come in from while anyone is in the hall: the aisles and the far end of the nave. */
    public static final List<Spot> REINFORCE_SPOTS = new ArrayList<>();
    /** A bell in each tower's belfry. */
    public static final List<Spot> BELLS = List.of(new Spot(TOWER_X, BELFRY_ROOF - 1, -TOWER_Z, 0), new Spot(TOWER_X, BELFRY_ROOF - 1, TOWER_Z, 0));

    // The chapel
    public static final Spot LOCK = new Spot(-40, 1, 0, 0);
    public static final List<Spot> ROOM_CHESTS = List.of(new Spot(-40, 1, -2, 0), new Spot(-40, 1, 2, 0));
    /** The middle of the portal frame's bottom row (set into the floor), 4 blocks behind the lock. */
    public static final Spot FRAME = new Spot(-44, 0, 0, 0);

    static {
        for (int b = 0; b < PIERS.length - 1; b++) {
            int p = PIERS[b];
            for (int side : new int[]{-1, 1}) {
                // Against the outer wall, facing into the aisle
                int dir = side > 0 ? 3 : 1;
                PANTRIES.add(new Spot(p + 3, 1, side * AISLE_OUT, dir));
                SMOKERS.add(new Spot(p + 4, 1, side * AISLE_OUT, dir));
                HEARTHS.add(new Spot(p + 5, 1, side * AISLE_OUT, dir));
                HAY.add(new Spot(p + 6, 1, side * AISLE_OUT, dir));
                CHANDELIERS.add(new Spot(p + 4, 14, side * 4, 0));
                CHANDELIERS.add(new Spot(p + 5, 9, side * 13, 0));
                REINFORCE_SPOTS.add(new Spot(p + 4, 1, side * 13, 0));
            }
        }
        for (int x = TABLE_WEST + 2; x <= TABLE_EAST; x += 6) {
            PIGLIN_SPOTS.add(new Spot(x, 1, -4, 1));
            PIGLIN_SPOTS.add(new Spot(x + 3, 1, 4, 3));
        }
        for (int[] b : new int[][]{{-22, -6}, {-22, 6}, {-6, -13}, {10, 13}, {18, -13}, {26, 6}}) BRUTE_SPOTS.add(new Spot(b[0], 1, b[1], 2));
        REINFORCE_SPOTS.add(new Spot(NAVE_EAST - 2, 1, -5, 2));
        REINFORCE_SPOTS.add(new Spot(NAVE_EAST - 2, 1, 5, 2));
    }

    private GluttonyHallShape() {
    }

    /** The direction index nearest to (dx, dz). */
    static int cardinal(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? 0 : 2;
        return dz > 0 ? 1 : 3;
    }

    // ---- Where things are, for the runtime ----

    /**
     * Whether (x, y, z) is in the hall itself (the nave, the aisles and the dais, not the towers, the doorway or the
     * chapel): where the feast is, and you can't build or dig.
     */
    public static boolean inHall(int x, int y, int z) {
        int az = Math.abs(z);
        if (y < 1 || y > RIDGE - 3 || x < HALL_WEST || x > NAVE_EAST || az > AISLE_OUT) return false;
        if (az >= AISLE_IN && x > AISLE_EAST) return false;
        return true;
    }

    /** Whether (x, y, z) is in the chapel behind the gate or the passage to it: where the portal is, and nothing can get at you. */
    public static boolean inSanctum(int x, int y, int z) {
        int az = Math.abs(z);
        if (y < 1) return false;
        if (x >= PASSAGE_WEST && x <= WEST_WALL_OUT && az <= GATE_HALF && y <= GATE_HEIGHT) return true;
        return x >= ROOM_WEST && x <= ROOM_EAST && az <= ROOM_HALF && y <= ROOM_HEIGHT;
    }

    /** The gate's blocks, row by row from the top down. */
    public static List<List<Spot>> gateRows() {
        List<List<Spot>> rows = new ArrayList<>();
        for (int y = GATE_HEIGHT; y >= 1; y--) {
            List<Spot> row = new ArrayList<>();
            for (int z = -GATE_HALF; z <= GATE_HALF; z++) row.add(new Spot(WEST_WALL_IN, y, z, 0));
            rows.add(row);
        }
        return rows;
    }

    /** The highest point of the box of the whole hall (the bridge is added to {@link #TERRACE_EAST}). */
    public static int east(int bridge) {
        return TERRACE_EAST + bridge + LANDING;
    }

    // ---- The nave's vault ----

    /** Inside the nave's air: straight walls up to {@link #SPRING}, then the pointed vault; ribs hang a block lower. */
    static boolean naveHollow(int az, int y, boolean rib) {
        if (az > NAVE_HALF || y < 1) return false;
        if (y <= SPRING) return true;
        double r = rib ? VAULT_RADIUS - 1 : VAULT_RADIUS;
        double dz = az + VAULT_OFFSET, dy = y - SPRING;
        return dz * dz + dy * dy < r * r && !(az == 0 && y >= ridgeCeiling());
    }

    /** The topmost block of air along the middle of the nave (the ridge rib runs above it). */
    private static int ridgeCeiling() {
        return SPRING + (int) Math.floor(Math.sqrt(VAULT_RADIUS * VAULT_RADIUS - VAULT_OFFSET * VAULT_OFFSET));
    }

    static boolean pierColumn(int x) {
        for (int p : PIERS) if (x == p || x == p + 1) return true;
        return false;
    }

    /** The pier to the west of this bay cell, or MIN_VALUE if x isn't in a bay (between two piers). */
    static int bayOf(int x) {
        for (int i = 0; i < PIERS.length - 1; i++) {
            if (x >= PIERS[i] + 2 && x <= PIERS[i] + 7) return PIERS[i];
        }
        return Integer.MIN_VALUE;
    }

    /** Top of the arcade's pointed arch over bay cell x (the arch's opening goes up to here). */
    static int archTop(int x, int pier) {
        double dx = Math.abs(x - (pier + 4.5));
        double r = 4, w = 3;
        return 8 + (int) Math.floor(Math.sqrt(Math.max(0, r * r - (dx + r - w) * (dx + r - w))));
    }

    // ---- The aisles' vaults ----

    static boolean aisleHollow(int az, int y, boolean rib) {
        if (az < AISLE_IN || az > AISLE_OUT || y < 1) return false;
        if (y <= AISLE_SPRING) return true;
        double r = rib ? 3.5 : 4.5;
        double dz = Math.abs(az - 13.5) + 1.5, dy = y - AISLE_SPRING;
        return dz * dz + dy * dy < r * r;
    }

    /** Twice the height of the top of the aisle roof {@code az} out from the middle. */
    static int aisleRoofHalf(int az) {
        return AISLE_ROOF_HALF + (AISLE_EAVE - az);
    }

    /** The flying buttress over the aisle roof, {@code az} out: the block it runs through at that distance. */
    static int flyerY(int az) {
        return (int) Math.round(14 + (AISLE_EAVE - az) * 9 / 8.0);
    }

    // ---- The towers ----

    static int spireHalf(int y) {
        return (int) Math.round(4.0 * (SPIRE_TOP - y) / (SPIRE_TOP - TOWER_TOP - 1));
    }

    // ---- The door and the rose window ----

    /** The great door's opening at {@code az} out from the middle, in the facade's outer layer or the inner two. */
    static int doorTop(int az, boolean outer) {
        if (outer) return switch (az) {
            case 0, 1 -> 10;
            case 2 -> 9;
            case 3 -> 7;
            default -> -1;
        };
        return switch (az) {
            case 0, 1 -> 9;
            case 2 -> 7;
            default -> -1;
        };
    }

    static double roseDistance(int y, int z) {
        return Math.hypot(y - ROSE_Y, z);
    }

    // ---- Every block ----

    /** What's at (x, y, z), given how long the bridge from the terrace to the shore is. */
    public static Part part(int x, int y, int z, int bridge) {
        if (y < BOTTOM || y > TOP) return Part.OUTSIDE;
        int az = Math.abs(z);
        if (x > TERRACE_EAST) return bridge(x, y, az, bridge);
        if (x < TERRACE_WEST || az > TERRACE_HALF) return Part.OUTSIDE;
        Part p = tower(x, y, z);
        if (p == null) p = x <= WEST_WALL_OUT - 1 ? chapel(x, y, az) : body(x, y, az);
        if (p != null) return p;
        return terrace(x, y, az);
    }

    /** The terrace round the hall: paved at floor level, a crenellated parapet round its edge, foundations under it. */
    private static Part terrace(int x, int y, int az) {
        boolean edge = x == TERRACE_WEST || x == TERRACE_EAST || az == TERRACE_HALF;
        if (y < 0) return edge && y >= LAVA ? Part.TERRACE_EDGE : Part.FOUNDATION;
        if (y == 0) return edge ? Part.TERRACE_EDGE : Part.TERRACE;
        if (y == 1 && edge) {
            if (x == TERRACE_EAST && az <= BRIDGE_HALF + 1) return Part.AIR; // the way onto the bridge
            return ((x + az) & 1) == 0 ? Part.PARAPET : Part.AIR;
        }
        // Clear the air over the terrace of whatever the Nether had there
        return y <= 8 ? Part.AIR : Part.OUTSIDE;
    }

    /** The bridge from the terrace to the shore, its deck cut on into the shore at the end. */
    private static Part bridge(int x, int y, int az, int bridge) {
        int along = x - TERRACE_EAST - 1, end = bridge + LANDING;
        if (along < 0 || along >= end || az > BRIDGE_HALF + 1) return Part.OUTSIDE;
        boolean pier = along % 8 == 0;
        boolean landing = along >= bridge;
        if (y == 0) return az <= BRIDGE_HALF ? Part.BRIDGE_DECK : Part.BRIDGE_RAIL;
        if (y > 0) {
            if (az <= BRIDGE_HALF) return y <= 3 ? Part.AIR : Part.OUTSIDE;
            if (y == 1 && !landing && (pier || (along & 1) == 1)) return Part.BRIDGE_RAIL;
            return Part.OUTSIDE;
        }
        if (landing) return Part.OUTSIDE;
        if (pier) return Part.BRIDGE_PIER;
        // The deck's underside, and an arch under it between piers
        int fromPier = Math.min(along % 8, 8 - along % 8);
        if (y == -1) return Part.BRIDGE_DECK;
        return y >= -1 - (4 - fromPier) && y >= LAVA ? Part.BRIDGE_DECK : Part.OUTSIDE;
    }

    // ---- The towers ----

    private static Part tower(int x, int y, int z) {
        int dx = x - TOWER_X, dz = Math.abs(z) - TOWER_Z;
        int ax = Math.abs(dx), adz = Math.abs(dz);
        int d = Math.max(ax, adz);
        if (d > TOWER_HALF || y < 0) return null;
        if (y == 0) return Part.WALL;
        if (y > TOWER_TOP) return towerTop(ax, adz, d, y);
        boolean corner = ax == TOWER_HALF && adz == TOWER_HALF;
        boolean band = y == 12 || y == 24 || y == BELFRY_FLOOR || y == TOWER_TOP;
        if (d >= TOWER_HALF - 1) {
            // Belfry openings, a pointed arch on every face
            int along = ax == d ? adz : ax;
            if (!corner && along <= 1 && y >= BELFRY_FLOOR + 2 && y <= BELFRY_ROOF - 3 + (along == 0 ? 1 : 0)) return Part.AIR;
            // A tall lancet low on each outward face (not the faces against the nave or the aisle)
            boolean outward = (ax == d && dx > 0) || (adz == d && dz > 0);
            if (outward && along == 0 && y >= 14 && y <= 20) return d == TOWER_HALF ? Part.WINDOW : Part.AIR;
            if (corner || band) return Part.TOWER_TRIM;
            return Part.TOWER;
        }
        if (y <= BELFRY_FLOOR) return y == BELFRY_FLOOR ? Part.TOWER_FLOOR : Part.FILL;
        if (y >= BELFRY_ROOF) return Part.TOWER;
        return Part.AIR;
    }

    /** The spire on top of the tower, a pinnacle on each corner round it. */
    private static Part towerTop(int ax, int adz, int d, int y) {
        if (ax == TOWER_HALF && adz == TOWER_HALF) {
            if (y <= TOWER_TOP + 4) return Part.PINNACLE;
            return y == TOWER_TOP + 5 ? Part.FINIAL : Part.OUTSIDE;
        }
        if (d == TOWER_HALF) return y == TOWER_TOP + 1 && ((ax + adz) & 1) == 0 ? Part.PARAPET : Part.OUTSIDE;
        if (y > SPIRE_TOP) return y == SPIRE_TOP + 1 && d == 0 ? Part.FINIAL : Part.OUTSIDE;
        int half = spireHalf(y);
        if (d > half) return Part.OUTSIDE;
        if (d < half) return Part.FILL;
        return (y - TOWER_TOP) % 6 == 0 ? Part.SPIRE_TRIM : Part.SPIRE;
    }

    // ---- The chapel behind the gate ----

    private static Part chapel(int x, int y, int az) {
        if (x < CHAPEL_WEST - 1 || az > CHAPEL_HALF + 2 || y < 0) return null;
        // Buttresses down its sides
        if ((x == -49 || x == -42) && az > CHAPEL_HALF && y <= 7) return Part.BUTTRESS;
        if (az > CHAPEL_HALF + 1) return null;
        int roof = 10 + (CHAPEL_HALF + 1 - az);
        if (y == roof) return az == 0 ? Part.CHAPEL_RIDGE_PART : Part.CHAPEL_ROOF;
        if (y > roof) return null;
        if (x < CHAPEL_WEST || az > CHAPEL_HALF) return y >= 10 ? Part.OUTSIDE : null; // the roof's overhang
        if (y >= 10) return x == CHAPEL_WEST ? Part.WALL : Part.FILL;
        // The passage from the gate
        if (x >= PASSAGE_WEST && az <= GATE_HALF + 1 && y <= GATE_HEIGHT + 1) {
            if (az <= GATE_HALF && y >= 1 && y <= GATE_HEIGHT) return Part.AIR;
            return y == 0 && az <= GATE_HALF ? Part.ROOM_FLOOR : Part.ROOM_WALL;
        }
        boolean insideX = x >= ROOM_WEST && x <= ROOM_EAST, insideZ = az <= ROOM_HALF;
        if (insideX && insideZ && y >= 1 && y <= ROOM_HEIGHT) {
            boolean corner = (x == ROOM_WEST || x == ROOM_EAST) && az == ROOM_HALF;
            return corner ? Part.ROOM_PILLAR : Part.AIR;
        }
        if (insideX && insideZ && y == 0) {
            return x == ROOM_WEST || x == ROOM_EAST || az == ROOM_HALF ? Part.ROOM_BORDER : Part.ROOM_FLOOR;
        }
        if (insideX && insideZ && y == ROOM_HEIGHT + 1) return Part.ROOM_CEILING;
        return y == 0 ? Part.FLOOR : Part.ROOM_WALL;
    }

    // ---- The church ----

    private static Part body(int x, int y, int az) {
        if (x < WEST_WALL_OUT || x > FACADE_OUT || az > BUTTRESS_OUT || y < 0) return null;
        if (y == 0) return floor(x, az);
        Part p = fleche(x, y, az);
        if (p != null) return p;
        if (x >= FACADE_IN && az <= ARCADE_OUT) return facade(x, y, az);
        if (az <= EAVE && x <= ROOF_EAST) {
            int roof = RIDGE - az;
            if (y == roof) return az == 0 ? Part.ROOF_RIDGE : Part.ROOF;
            if (y > roof) return Part.OUTSIDE;
        }
        if (az <= NAVE_HALF) return nave(x, y, az);
        if (az <= ARCADE_OUT) return arcade(x, y, az);
        if (x > AISLE_EAST) return null; // in front of the towers: the terrace
        p = flyer(x, y, az);
        if (p != null) return p;
        p = buttress(x, y, az);
        if (p != null) return p;
        return aisle(x, y, az);
    }

    private static Part floor(int x, int az) {
        if (x <= WEST_WALL_IN) return az <= AISLE_EAVE - 1 ? Part.WALL : null;
        if (x >= FACADE_IN) return az <= ARCADE_OUT ? (az <= 3 ? Part.FLOOR : Part.WALL) : null;
        if (az <= NAVE_HALF) {
            return az <= TABLE_HALF + 1 && x >= TABLE_WEST - 1 && x <= TABLE_EAST + 1 ? Part.RUNNER : Part.FLOOR;
        }
        if (az <= ARCADE_OUT) return pierColumn(x) || x > AISLE_EAST ? Part.WALL : Part.FLOOR;
        if (x > AISLE_EAST) return null;
        if (az <= AISLE_OUT) return Part.AISLE_FLOOR;
        if (az <= WALL_OUT) return Part.WALL;
        return buttressColumn(x) && az <= BUTTRESS_OUT ? Part.BUTTRESS : null;
    }

    /** The slender spire straddling the ridge over the middle of the nave. */
    private static Part fleche(int x, int y, int az) {
        int dx = Math.abs(x - FLECHE_X);
        if (dx > 1 || az > 1 || y < RIDGE - 1) return null;
        if (y <= RIDGE + 1 || y == RIDGE + 5) return Part.FLECHE;
        if (y <= RIDGE + 4) return dx == 1 && az == 1 ? Part.FLECHE : Part.AIR;
        if (dx != 0 || az != 0) return y <= RIDGE + 6 && (dx == 0 || az == 0) ? Part.FLECHE : Part.OUTSIDE;
        if (y <= RIDGE + 11) return Part.FLECHE;
        return y == RIDGE + 12 ? Part.FINIAL : Part.OUTSIDE;
    }

    /** The front: the great door, the rose window above it, and the gable over the roof. */
    private static Part facade(int x, int y, int az) {
        boolean outer = x == FACADE_OUT;
        int door = doorTop(az, outer);
        if (y <= door) return Part.AIR;
        if (outer && az <= 4 && y <= doorTop(Math.max(0, az - 1), true) + 1) return Part.DOOR_TRIM;
        double rose = roseDistance(y, az);
        if (rose < ROSE_RADIUS) {
            if (!outer) return Part.AIR;
            if (rose >= ROSE_RADIUS - 0.9 || (rose >= 2.8 && rose < 3.6)) return Part.TRACERY;
            double a = Math.toDegrees(Math.atan2(y - ROSE_Y, az));
            double spoke = Math.abs(((a % 45) + 45) % 45);
            if (rose >= 3.6 && (spoke < 7 || spoke > 38)) return Part.TRACERY;
            return Part.ROSE;
        }
        int gable = RIDGE - az + 1;
        if (outer) {
            if (y > gable) return y == gable + 1 && az == 0 ? Part.FINIAL : Part.OUTSIDE;
            return y == gable || y == gable - 1 && az == ARCADE_OUT ? Part.GABLE_TRIM : Part.WALL;
        }
        if (y >= gable - 1) return y == gable - 1 ? (az == 0 ? Part.ROOF_RIDGE : Part.ROOF) : Part.OUTSIDE;
        return y >= RIDGE - az - 1 || az == ARCADE_OUT ? Part.WALL : Part.FILL;
    }

    /** The nave: the hall's air under its vault, the vault itself, and the walls at either end. */
    private static Part nave(int x, int y, int az) {
        if (x <= WEST_WALL_IN) return westWall(x, y, az);
        boolean rib = pierColumn(x) || x == HALL_WEST || x == NAVE_EAST;
        if (naveHollow(az, y, rib)) return inside(x, y, az);
        // The vault's face (what you see from inside), and fill between it and the roof
        boolean face = naveHollow(az, y - 1, rib) || naveHollow(az - 1, y, rib) || naveHollow(az + 1, y, rib);
        // The ribs across the vault at each pier, and the ridge rib along the top of it
        if (face) return rib || az == 0 ? Part.RIB : Part.VAULT;
        return rib && naveHollow(az, y, false) ? Part.RIB : Part.FILL;
    }

    /** The two-thick wall between the nave and an aisle: piers and pointed arches below, the clerestory above. */
    private static Part arcade(int x, int y, int az) {
        if (x <= WEST_WALL_IN) return westWall(x, y, az);
        if (x > AISLE_EAST) return y >= RIDGE - az - 1 ? Part.WALL : az == ARCADE_OUT || y < SPRING ? Part.WALL : Part.FILL;
        int pier = bayOf(x);
        if (y <= 12) {
            if (pierColumn(x)) return y == 1 ? Part.PIER_BASE : y == 9 ? Part.CAPITAL : Part.PIER;
            if (pier == Integer.MIN_VALUE) return Part.WALL;
            int top = archTop(x, pier);
            if (y <= top) return Part.AIR;
            return y == top + 1 ? Part.ARCH : Part.WALL;
        }
        // The clerestory: a tall lancet in the middle of each bay, glazed in its outer face
        if (pier != Integer.MIN_VALUE && (x == pier + 4 || x == pier + 5) && y >= CLERESTORY_BOTTOM && y <= CLERESTORY_TOP) {
            return az == ARCADE_OUT ? Part.WINDOW : Part.AIR;
        }
        if (pierColumn(x) && az == ARCADE_OUT && y <= RIDGE - az - 2) return Part.PIER; // pilasters up the outside
        return Part.WALL;
    }

    /** The west wall, the gate in its inner face and the passage through it. */
    private static Part westWall(int x, int y, int az) {
        int top = az <= EAVE ? RIDGE - az : aisleRoofHalf(az) / 2;
        if (y >= top) return null;
        if (az <= GATE_HALF && y <= GATE_HEIGHT) return x == WEST_WALL_IN ? Part.GATE : Part.AIR;
        if (x == WEST_WALL_IN && az <= GATE_HALF + 1 && y <= GATE_HEIGHT + 2) {
            // A pointed frame round the gate, gilded
            if (y == GATE_HEIGHT + 2 && az > 0) return Part.WALL;
            return Part.GATE_FRAME;
        }
        if (x == WEST_WALL_OUT && az <= GATE_HALF + 1 && y <= GATE_HEIGHT + 1) return Part.ROOM_WALL;
        return Part.WALL;
    }

    /** The aisles: their vaults, the outer walls with their windows, and the lean-to roofs over them. */
    private static Part aisle(int x, int y, int az) {
        int half = aisleRoofHalf(az), top = half / 2;
        boolean slab = (half & 1) == 1;
        if (az <= AISLE_EAVE) {
            if (y == top && slab) return Part.AISLE_ROOF_SLAB;
            if (y >= top) return null;
            if (y == top - 1) return Part.AISLE_ROOF;
        }
        if (az > WALL_OUT) return null;
        if (x <= WEST_WALL_IN) return westWall(x, y, az);
        if (az >= WALL_IN) {
            int pier = bayOf(x);
            if (pier != Integer.MIN_VALUE && (x == pier + 4 || x == pier + 5) && y >= AISLE_WINDOW_BOTTOM && y <= AISLE_WINDOW_TOP) {
                return az == WALL_OUT ? Part.WINDOW : Part.AIR;
            }
            if (y > WALL_TOP) return Part.FILL;
            return pierColumn(x) && az == WALL_OUT ? Part.PIER : Part.WALL;
        }
        boolean rib = pierColumn(x) || x == HALL_WEST;
        if (aisleHollow(az, y, rib)) return inside(x, y, az);
        boolean face = aisleHollow(az, y - 1, rib) || aisleHollow(az - 1, y, rib) || aisleHollow(az + 1, y, rib);
        if (face || (rib && aisleHollow(az, y, false))) return rib ? Part.RIB : Part.VAULT;
        return Part.FILL;
    }

    static boolean buttressColumn(int x) {
        for (int i = 0; i < PIERS.length - 1; i++) if (x == PIERS[i] || x == PIERS[i] + 1) return true;
        return false;
    }

    /** A buttress against the outer wall at every pier, stepping in as it rises, with a pinnacle on top. */
    private static Part buttress(int x, int y, int az) {
        if (!buttressColumn(x) || az < AISLE_EAVE) return null;
        int top = switch (az) {
            case 19 -> 14;
            case 20 -> 12;
            default -> 9;
        };
        if (y <= top) return Part.BUTTRESS;
        if (az == 20) {
            if (y <= 14) return Part.PINNACLE;
            if (y == 15) return Part.FINIAL;
        }
        return null;
    }

    /** The flying buttress from the top of each buttress over the aisle roof to the clerestory wall. */
    private static Part flyer(int x, int y, int az) {
        if (!buttressColumn(x) || az < AISLE_IN || az > WALL_OUT) return null;
        int fy = flyerY(az);
        if (y == fy) return Part.FLYER;
        // Under its inner end, where it meets the wall
        if (az == AISLE_IN && y == fy - 1) return Part.FLYER;
        return null;
    }

    /** Things standing on the floor of the hall: the banquet table and its benches, and the dais. */
    private static Part inside(int x, int y, int az) {
        if (y == 1) {
            if (x >= TABLE_WEST && x <= TABLE_EAST) {
                if (az <= TABLE_HALF) return Part.TABLE;
                if (az == TABLE_HALF + 1 && (x - TABLE_WEST) % 9 != 8) return Part.BENCH;
            }
            if (x >= DAIS_WEST && x <= DAIS_EAST && az <= DAIS_HALF) {
                return x == DAIS_EAST || az == DAIS_HALF ? Part.DAIS_EDGE : Part.DAIS;
            }
        }
        return Part.AIR;
    }
}
