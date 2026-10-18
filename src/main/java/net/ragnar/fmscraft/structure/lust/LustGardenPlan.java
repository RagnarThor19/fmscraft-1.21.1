package net.ragnar.fmscraft.structure.lust;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The layout of one Lust's Garden, worked out from its seed. Pure logic, no Minecraft classes, so it can be unit
 * tested; {@link LustGardenPiece} turns it into blocks.
 *
 * Local coordinates: (0, 0) is the middle of the fountain, y = 0 is the first block above the garden's ground
 * (the lawn's grass is at y = -1). North is -z.
 *
 * <ul>
 *   <li><b>The hedge</b>: a ring of azalea leaves {@link #MIN_HEDGE_HEIGHT}+ blocks high, never round: its radius
 *       wanders between about 84 and 107 blocks and its thickness between 4 and 8, and both faces are lumpy. A
 *       {@link #SKIRT} outside it blends back into the land.</li>
 *   <li><b>The gate</b>: one tunnel through the hedge, where the paved avenue comes in.</li>
 *   <li><b>Paths</b>: the avenue winds from the gate to the fountain plaza, crossing two ring paths that are joined
 *       by spokes; a few footpaths lead off the outer ring into dead-end nooks by the hedge.</li>
 *   <li><b>The plaza</b>: paving round the fountain, with the four empty plinths at {@link #FOUNTAIN_PLINTH}.</li>
 *   <li>Ponds, trees, little buildings, benches, lanterns, arbors over the paths and old broken walls on the lawns.</li>
 *   <li><b>Statue plinths</b> beside the paths, each facing its path, plus two guarding the gate and one at the end of
 *       every nook.</li>
 *   <li><b>Below</b>: a stair under the fountain's basin down to the crypt and the portal.</li>
 * </ul>
 */
public final class LustGardenPlan {
    /** Nothing of the garden (skirt included) is further than this from the middle. */
    public static final int REACH = 118;
    private static final int SIZE = REACH * 2 + 1;
    public static final double MEAN_RADIUS = 95;
    public static final int SKIRT = 10;
    public static final int MIN_HEDGE_HEIGHT = 12;

    // ---- The middle ----
    public static final int PLAZA = 16;
    /** Water inside {@link #BASIN}, a ledge to stand on out to {@link #LEDGE}, then a low rim out to {@link #RIM}. */
    public static final double BASIN = 5.5, LEDGE = 6.5, RIM = 7.5;
    /** The fountain's pillar is 3x3 up to {@link #PILLAR_TOP}; water spills from four spouts at {@link #SPOUT_Y}. */
    public static final int PILLAR_TOP = 4, SPOUT_Y = 3;
    /** The four empty plinths round the fountain are this far out on the four compass points. */
    public static final int FOUNTAIN_PLINTH = 10;

    // ---- Below (the stair runs north from the basin) ----
    /** The cap over the stair: basin floor blocks at y = {@link #CAP_Y}, x -1..1, z {@link #CAP_Z0} (first) to {@link #CAP_Z1}. */
    public static final int CAP_Y = -2, CAP_Z0 = -3, CAP_Z1 = -5;
    /** Steps go down one a block northward, from (z -3, y -3) to (z -11, y -11). */
    public static final int STEPS = 9;
    public static final int CRYPT_FLOOR = -12;
    /** Inside of the crypt: x -6..6, z -36..-12, standing at y -11, with a vaulted ceiling 6 high in the middle. */
    public static final int CRYPT_HALF_WIDTH = 6, CRYPT_NEAR = -12, CRYPT_FAR = -36;
    /** The lock stands in the crypt facing south (toward the stair); its portal frame is 4 blocks north of it. */
    public static final int LOCK_Z = -28;
    /** Ponds keep this far from the crypt and the stair. */
    private static final int CRYPT_MARGIN = 4;

    public enum Ground {
        /** Not part of the garden. */
        OUTSIDE,
        /** Outside the hedge, levelled down into the land. */
        SKIRT,
        HEDGE,
        /** The tunnel through the hedge. */
        GATE,
        AVENUE, PATH, PLAZA,
        BASIN, LEDGE, RIM, PILLAR,
        POND, SHORE, LAWN,
        /** A low hedge along a path. */
        BORDER
    }

    /** Something standing at (x, z), facing {@code dir}: 0 south (+z), 1 west, 2 north, 3 east, like Direction.fromHorizontal. */
    public record Spot(int x, int z, int dir) {
    }

    /** A statue plinth, with the way its statue faces (Minecraft yaw, degrees). */
    public record Plinth(int x, int z, float yaw) {
    }

    public record Tree(int x, int z, Kind kind, int height) {
        public enum Kind { CHERRY, AZALEA, TOPIARY }
    }

    /** An arch over a path: posts at (x1, z1) and (x2, z2). */
    public record Arbor(int x1, int z1, int x2, int z2) {
    }

    /** A broken old wall on a lawn, {@code length} blocks from (x, z) in direction {@code dir}. */
    public record Ruin(int x, int z, int dir, int length) {
    }

    private record Segment(double x1, double z1, double x2, double z2, double half, boolean avenue) {
        double distance(double x, double z) {
            double dx = x2 - x1, dz = z2 - z1;
            double t = ((x - x1) * dx + (z - z1) * dz) / Math.max(1e-9, dx * dx + dz * dz);
            t = Math.max(0, Math.min(1, t));
            double px = x1 + dx * t - x, pz = z1 + dz * t - z;
            return Math.sqrt(px * px + pz * pz);
        }
    }

    /** A path as a line of points, kept for placing things along it. */
    private record Line(List<double[]> points, double half, boolean avenue) {
    }

    private final long seed;
    private final double[] phase = new double[16];
    private final double gateAngle;
    private final Ground[] ground = new Ground[SIZE * SIZE];
    private final float[] edge = new float[SIZE * SIZE];
    private final boolean[] reserved = new boolean[SIZE * SIZE];
    private final byte[] height = new byte[SIZE * SIZE];

    private final List<Segment> segments = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Plinth> plinths = new ArrayList<>();
    private final List<Spot> gazebos = new ArrayList<>();
    private final List<Spot> sheds = new ArrayList<>();
    private final List<Spot> benches = new ArrayList<>();
    private final List<Spot> lanterns = new ArrayList<>();
    private final List<Arbor> arbors = new ArrayList<>();
    private final List<Ruin> ruins = new ArrayList<>();
    private final List<Tree> trees = new ArrayList<>();
    private final List<int[]> gatePillars = new ArrayList<>();

    public LustGardenPlan(long seed) {
        this.seed = seed;
        Random random = new Random(seed);
        for (int i = 0; i < phase.length; i++) phase[i] = random.nextDouble() * Math.PI * 2;
        this.gateAngle = random.nextDouble() * Math.PI * 2;
        layPaths(random);
        classify();
        digPonds(random);
        placePlinths();
        placeBuildings(random);
        placeArbors(random);
        placeBenchesAndLanterns();
        placeRuins(random);
        plantTrees(random);
        plantBorders();
    }

    public long seed() {
        return seed;
    }

    // ---- Noise (shared with the piece, so every "random" block is the same whichever chunk asks) ----

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /** A hash of the position, 0 (inclusive) to 1. */
    public static double hash(long seed, int x, int y, int z, int salt) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ (salt * 0x27D4EB2F165667C5L);
        return (mix(h) >>> 11) * 0x1.0p-53;
    }

    public double hash(int x, int y, int z, int salt) {
        return hash(seed, x, y, z, salt);
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    /** Smooth value noise, 0 to 1, with features about {@code scale} blocks across. */
    public double smooth(double x, double z, double scale, int salt) {
        double fx = x / scale, fz = z / scale;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        double tx = fade(fx - ix), tz = fade(fz - iz);
        double a = hash(ix, 0, iz, salt), b = hash(ix + 1, 0, iz, salt);
        double c = hash(ix, 0, iz + 1, salt), d = hash(ix + 1, 0, iz + 1, salt);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    /** Smooth value noise in three dimensions, 0 to 1. */
    public double smooth(double x, double y, double z, double scale, int salt) {
        double fy = y / scale;
        int iy = (int) Math.floor(fy);
        double ty = fade(fy - iy);
        double low = smooth(x, z, scale, salt * 31 + iy), high = smooth(x, z, scale, salt * 31 + iy + 1);
        return low + (high - low) * ty;
    }

    // ---- The outline ----

    /** Radius of the hedge's outer face in direction {@code angle} (radians, atan2(z, x)). */
    public double outer(double angle) {
        return MEAN_RADIUS + 5 * Math.sin(2 * angle + phase[0]) + 3.5 * Math.sin(3 * angle + phase[1])
                + 2 * Math.sin(5 * angle + phase[2]) + Math.sin(8 * angle + phase[3]);
    }

    public double thickness(double angle) {
        return Math.max(4, 5.5 + 1.5 * Math.sin(3 * angle + phase[4]) + 0.8 * Math.sin(7 * angle + phase[5]));
    }

    public double inner(double angle) {
        return outer(angle) - thickness(angle);
    }

    /** The inner ring path's radius. */
    private double innerRing(double angle) {
        return 40 + 3 * Math.sin(3 * angle + phase[6]) + 1.5 * Math.sin(7 * angle + phase[7]);
    }

    /** The outer ring path keeps a lawn's width inside the hedge. */
    private double outerRing(double angle) {
        return inner(angle) - 14 + 1.5 * Math.sin(5 * angle + phase[8]);
    }

    /** How far the hedge's outer face bulges out (or in) at this column. */
    private double bulge(int x, int z) {
        return (smooth(x, z, 6, 1) - 0.5) * 3;
    }

    private double innerJitter(int x, int z) {
        return (smooth(x, z, 5, 2) - 0.5) * 3;
    }

    public double gateAngle() {
        return gateAngle;
    }

    // ---- Paths ----

    private static double[] polar(double angle, double r) {
        return new double[]{Math.cos(angle) * r, Math.sin(angle) * r};
    }

    private void addLine(List<double[]> points, double half, boolean avenue) {
        lines.add(new Line(points, half, avenue));
        for (int i = 0; i + 1 < points.size(); i++) {
            double[] a = points.get(i), b = points.get(i + 1);
            segments.add(new Segment(a[0], a[1], b[0], b[1], half, avenue));
        }
    }

    /** A path out along {@code angle} from radius r0 to r1, meandering sideways up to {@code sway} blocks in the middle. */
    private List<double[]> radial(double angle, double r0, double r1, double sway, double swayPhase) {
        List<double[]> points = new ArrayList<>();
        int steps = Math.max(2, (int) Math.ceil(Math.abs(r1 - r0) / 3));
        double px = -Math.sin(angle), pz = Math.cos(angle);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double r = r0 + (r1 - r0) * t;
            double off = sway * Math.sin(Math.PI * 2 * t + swayPhase) * Math.sin(Math.PI * t);
            double[] p = polar(angle, r);
            points.add(new double[]{p[0] + px * off, p[1] + pz * off});
        }
        return points;
    }

    private interface Radius {
        double at(double angle);
    }

    private List<double[]> ring(Radius radius) {
        List<double[]> points = new ArrayList<>();
        for (int deg = 0; deg <= 360; deg += 3) {
            double a = Math.toRadians(deg);
            points.add(polar(a, radius.at(a)));
        }
        return points;
    }

    private static double angleBetween(double a, double b) {
        double d = Math.abs(a - b) % (Math.PI * 2);
        return d > Math.PI ? Math.PI * 2 - d : d;
    }

    private final List<Double> spokeAngles = new ArrayList<>();

    private void layPaths(Random random) {
        // The avenue: winding in from the plaza, then dead straight through the hedge and out across the skirt
        double gateInner = inner(gateAngle);
        List<double[]> avenue = radial(gateAngle, PLAZA - 2, gateInner - 8, 7, random.nextDouble() * Math.PI * 2);
        for (double r = gateInner - 5; r <= outer(gateAngle) + SKIRT + 2; r += 3) avenue.add(polar(gateAngle, r));
        addLine(avenue, 2.5, true);

        addLine(ring(this::innerRing), 1.5, false);
        addLine(ring(this::outerRing), 1.5, false);

        double base = random.nextDouble() * Math.PI * 2;
        for (int k = 0; k < 5; k++) {
            double a = base + Math.PI * 2 * k / 5 + (random.nextDouble() - 0.5) * 0.3;
            if (angleBetween(a, gateAngle) < 0.45) continue;
            spokeAngles.add(a);
            addLine(radial(a, PLAZA - 2, innerRing(a), 2.5, random.nextDouble() * Math.PI * 2), 1.5, false);
        }
        base += Math.PI / 6;
        for (int k = 0; k < 6; k++) {
            double a = base + Math.PI * 2 * k / 6 + (random.nextDouble() - 0.5) * 0.3;
            if (angleBetween(a, gateAngle) < 0.4) continue;
            spokeAngles.add(a);
            addLine(radial(a, innerRing(a), outerRing(a), 3, random.nextDouble() * Math.PI * 2), 1.5, false);
        }
        // Footpaths off the outer ring into nooks by the hedge, each ending at a statue
        int nooks = 0;
        for (int tries = 0; tries < 40 && nooks < 4; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            if (angleBetween(a, gateAngle) < 0.5) continue;
            boolean clear = true;
            for (double s : spokeAngles) if (angleBetween(a, s) < 0.18) clear = false;
            if (!clear) continue;
            spokeAngles.add(a);
            double end = inner(a) - 5;
            addLine(radial(a, outerRing(a), end, 1.5, random.nextDouble() * Math.PI * 2), 1.0, false);
            double[] tip = polar(a, end + 2);
            nookEnds.add(new double[]{tip[0], tip[1], a});
            nooks++;
        }
    }

    private final List<double[]> nookEnds = new ArrayList<>();

    // ---- Classifying every column ----

    private static int index(int x, int z) {
        return (x + REACH) * SIZE + (z + REACH);
    }

    private static boolean inGrid(int x, int z) {
        return x >= -REACH && x <= REACH && z >= -REACH && z <= REACH;
    }

    private void classify() {
        // Distance from every column to the nearest path edge, through a coarse grid of the segments near it
        int cell = 16, cells = SIZE / cell + 1;
        List<List<Segment>> buckets = new ArrayList<>();
        for (int i = 0; i < cells * cells; i++) buckets.add(new ArrayList<>());
        double reach = 10;
        for (Segment s : segments) {
            int x0 = (int) Math.floor((Math.min(s.x1, s.x2) - reach + REACH) / cell), x1 = (int) Math.floor((Math.max(s.x1, s.x2) + reach + REACH) / cell);
            int z0 = (int) Math.floor((Math.min(s.z1, s.z2) - reach + REACH) / cell), z1 = (int) Math.floor((Math.max(s.z1, s.z2) + reach + REACH) / cell);
            for (int bx = Math.max(0, x0); bx <= Math.min(cells - 1, x1); bx++) {
                for (int bz = Math.max(0, z0); bz <= Math.min(cells - 1, z1); bz++) buckets.get(bx * cells + bz).add(s);
            }
        }
        boolean[] avenueAt = new boolean[SIZE * SIZE];
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                double best = reach;
                boolean avenue = false;
                for (Segment s : buckets.get(((x + REACH) / cell) * cells + (z + REACH) / cell)) {
                    double d = s.distance(x, z) - s.half;
                    if (d < best) best = d;
                    if (d <= 0 && s.avenue) avenue = true;
                }
                edge[index(x, z)] = (float) best;
                avenueAt[index(x, z)] = avenue;
            }
        }

        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                int i = index(x, z);
                double r = Math.sqrt(x * x + z * z);
                double a = Math.atan2(z, x);
                double ro = outer(a) + bulge(x, z), ri = inner(a) - innerJitter(x, z);
                boolean onPath = edge[i] <= 0;
                Ground g;
                if (r > REACH || r >= ro + SKIRT) {
                    g = Ground.OUTSIDE;
                } else if (r >= ro) {
                    g = onPath && avenueAt[i] ? Ground.AVENUE : Ground.SKIRT;
                } else if (r >= ri) {
                    g = inGate(x, z) ? Ground.GATE : Ground.HEDGE;
                } else if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    g = Ground.PILLAR;
                } else if (r < BASIN) {
                    g = Ground.BASIN;
                } else if (r < LEDGE) {
                    g = Ground.LEDGE;
                } else if (r < RIM) {
                    g = Ground.RIM;
                } else if (r < PLAZA) {
                    g = Ground.PLAZA;
                } else if (onPath) {
                    g = avenueAt[i] ? Ground.AVENUE : Ground.PATH;
                } else {
                    g = Ground.LAWN;
                }
                ground[i] = g;
                if (g == Ground.HEDGE || g == Ground.GATE) height[i] = (byte) hedgeHeightAt(x, z, r, ri, ro);
            }
        }
    }

    /** Whether (x, z) is in the tunnel through the hedge: within the avenue's width of the line out through the gate. */
    private boolean inGate(int x, int z) {
        double gx = Math.cos(gateAngle), gz = Math.sin(gateAngle);
        double along = x * gx + z * gz, across = -x * gz + z * gx;
        return along > 0 && Math.abs(across) <= 2.5;
    }

    /** Hedge height: at least {@link #MIN_HEDGE_HEIGHT} through its middle, lumpy on top, rounded off at the faces. */
    private int hedgeHeightAt(int x, int z, double r, double ri, double ro) {
        double middle = 15 + 2 * (smooth(x, z, 14, 3) * 2 - 1) + (smooth(x, z, 5, 4) * 2 - 1);
        double half = Math.max(1, (ro - ri) / 2), off = Math.abs(r - (ri + ro) / 2) / half;
        double rounded = middle - 3.5 * Math.pow(Math.min(1, off), 3);
        return (int) Math.max(6, Math.round(rounded));
    }

    // ---- Ponds ----

    private void digPonds(Random random) {
        int dug = 0;
        for (int tries = 0; tries < 400 && dug < 5; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = PLAZA + 14 + random.nextDouble() * (outerRing(a) - PLAZA - 18);
            int cx = (int) Math.round(Math.cos(a) * r), cz = (int) Math.round(Math.sin(a) * r);
            double size = 5 + random.nextDouble() * 4.5;
            double p1 = random.nextDouble() * Math.PI * 2, p2 = random.nextDouble() * Math.PI * 2;
            int reach = (int) Math.ceil(size * 1.45) + 2;
            List<int[]> water = new ArrayList<>(), shore = new ArrayList<>();
            boolean[][] wet = new boolean[reach * 2 + 1][reach * 2 + 1];
            for (int dx = -reach + 1; dx < reach; dx++) {
                for (int dz = -reach + 1; dz < reach; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz), phi = Math.atan2(dz, dx);
                    double rim = size * (1 + 0.25 * Math.sin(3 * phi + p1) + 0.15 * Math.sin(5 * phi + p2));
                    if (d >= rim) continue;
                    wet[dx + reach][dz + reach] = true;
                    water.add(new int[]{cx + dx, cz + dz, d < rim * 0.55 ? 2 : 1});
                }
            }
            // The shore: every dry column touching the water, corners included
            for (int dx = -reach + 1; dx < reach; dx++) {
                for (int dz = -reach + 1; dz < reach; dz++) {
                    if (wet[dx + reach][dz + reach]) continue;
                    boolean touches = false;
                    for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) touches |= wet[dx + ox + reach][dz + oz + reach];
                    if (touches) shore.add(new int[]{cx + dx, cz + dz});
                }
            }
            boolean ok = water.size() >= 40;
            for (List<int[]> cells : List.of(water, shore)) {
                for (int[] c : cells) {
                    int x = c[0], z = c[1];
                    if (!inGrid(x, z) || ground[index(x, z)] != Ground.LAWN || edge[index(x, z)] < 3 || nearCrypt(x, z, CRYPT_MARGIN)) ok = false;
                }
            }
            if (!ok) continue;
            // Keep ponds well apart: nothing but lawn a few blocks round the shore
            for (int[] s : shore) {
                for (int dx = -3; dx <= 3 && ok; dx++) {
                    for (int dz = -3; dz <= 3 && ok; dz++) {
                        int x = s[0] + dx, z = s[1] + dz;
                        if (inGrid(x, z) && (ground[index(x, z)] == Ground.POND || ground[index(x, z)] == Ground.SHORE)) ok = false;
                    }
                }
            }
            if (!ok) continue;
            for (int[] w : water) {
                ground[index(w[0], w[1])] = Ground.POND;
                height[index(w[0], w[1])] = (byte) w[2];
            }
            for (int[] s : shore) ground[index(s[0], s[1])] = Ground.SHORE;
            dug++;
        }
    }

    /** Whether (x, z) is over (or within {@code margin} of) the stair and crypt. */
    public static boolean nearCrypt(int x, int z, int margin) {
        return Math.abs(x) <= CRYPT_HALF_WIDTH + 1 + margin && z <= CAP_Z0 + margin && z >= CRYPT_FAR - 1 - margin;
    }

    // ---- Plinths ----

    private boolean isLawn(int x, int z) {
        return inGrid(x, z) && ground[index(x, z)] == Ground.LAWN && !reserved[index(x, z)];
    }

    /** Lawn all round (x, z) out to {@code r} blocks, with nothing else claimed on it. */
    private boolean clearLawn(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) if (!isLawn(x + dx, z + dz)) return false;
        }
        return true;
    }

    private void reserve(int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) if (inGrid(x + dx, z + dz)) reserved[index(x + dx, z + dz)] = true;
        }
    }

    /** Lawn cells only: a cell beside a path, {@code r} blocks clear round it, and nothing too close in {@code others}. */
    private static boolean farFrom(int x, int z, List<int[]> others, double distance) {
        for (int[] o : others) if ((o[0] - x) * (o[0] - x) + (o[1] - z) * (o[1] - z) < distance * distance) return false;
        return true;
    }

    private final List<int[]> taken = new ArrayList<>();

    private static float yawToward(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void addPlinth(int x, int z, double lookX, double lookZ) {
        plinths.add(new Plinth(x, z, yawToward(lookX - x, lookZ - z)));
        taken.add(new int[]{x, z});
        reserve(x, z, 2);
    }

    private void placePlinths() {
        // Two guarding the gate, inside it, facing each other across the avenue
        double gx = Math.cos(gateAngle), gz = Math.sin(gateAngle), px = -gz, pz = gx;
        double mouth = inner(gateAngle) - 4;
        for (int side : new int[]{-1, 1}) {
            int x = (int) Math.round(gx * mouth + px * 4.5 * side), z = (int) Math.round(gz * mouth + pz * 4.5 * side);
            ground[index(x, z)] = Ground.LAWN;
            addPlinth(x, z, gx * mouth, gz * mouth);
        }
        // One at the end of every nook, looking back down its footpath
        for (double[] nook : nookEnds) {
            int x = (int) Math.round(nook[0]), z = (int) Math.round(nook[1]);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int i = index(x + dx, z + dz);
                    if (ground[i] == Ground.HEDGE || ground[i] == Ground.LAWN) ground[i] = dx * dx + dz * dz <= 4 ? Ground.PATH : ground[i];
                }
            }
            ground[index(x, z)] = Ground.LAWN;
            double a = nook[2];
            addPlinth(x, z, x - Math.cos(a) * 5, z - Math.sin(a) * 5);
        }
        // Along the paths, on alternating sides
        double gateX = gx * mouth, gateZ = gz * mouth;
        int side = 1;
        for (Line line : lines) {
            if (line.half < 1.5) continue;
            double walked = 0, next = 6;
            for (int i = 0; i + 1 < line.points.size(); i++) {
                double[] a = line.points.get(i), b = line.points.get(i + 1);
                double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.sqrt(dx * dx + dz * dz);
                walked += len;
                if (walked < next || len < 1e-6) continue;
                next = walked + 9;
                side = -side;
                double nx = -dz / len * side, nz = dx / len * side;
                double off = line.half + 2;
                int x = (int) Math.round(b[0] + nx * off), z = (int) Math.round(b[1] + nz * off);
                if (!isLawn(x, z) || edge[index(x, z)] < 0.8 || edge[index(x, z)] > 3) continue;
                if (Math.sqrt(x * x + z * z) < 24) continue;
                if ((x - gateX) * (x - gateX) + (z - gateZ) * (z - gateZ) < 12 * 12) continue;
                if (!farFrom(x, z, taken, 16) || !clearLawn(x, z, 1)) continue;
                addPlinth(x, z, b[0], b[1]);
            }
        }
    }

    // ---- Buildings, arbors, benches, lanterns, ruins ----

    private static int dirToward(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? 3 : 1;
        return dz > 0 ? 0 : 2;
    }

    /** A spot {@code r} blocks clear of everything, set back from a ring path, facing the path. */
    private Spot besidePath(Random random, int r, List<int[]> keepAway, double away) {
        List<Line> rings = lines.subList(1, 3);
        for (int tries = 0; tries < 200; tries++) {
            Line line = rings.get(random.nextInt(rings.size()));
            int i = random.nextInt(line.points.size() - 1);
            double[] a = line.points.get(i), b = line.points.get(i + 1);
            double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-6) continue;
            int side = random.nextBoolean() ? 1 : -1;
            double nx = -dz / len * side, nz = dx / len * side, off = line.half + 1.5 + r;
            int x = (int) Math.round(b[0] + nx * off), z = (int) Math.round(b[1] + nz * off);
            if (!clearLawn(x, z, r) || !farFrom(x, z, keepAway, away) || !farFrom(x, z, taken, r + 5)) continue;
            return new Spot(x, z, dirToward(-nx, -nz));
        }
        return null;
    }

    private final List<int[]> buildings = new ArrayList<>();

    private void placeBuildings(Random random) {
        for (int k = 0; k < 2; k++) {
            Spot gazebo = besidePath(random, 5, buildings, 40);
            if (gazebo == null) continue;
            gazebos.add(gazebo);
            buildings.add(new int[]{gazebo.x(), gazebo.z()});
            reserve(gazebo.x(), gazebo.z(), 5);
        }
        for (int k = 0; k < 2; k++) {
            Spot shed = besidePath(random, 4, buildings, 40);
            if (shed == null) continue;
            sheds.add(shed);
            buildings.add(new int[]{shed.x(), shed.z()});
            reserve(shed.x(), shed.z(), 4);
        }
        taken.addAll(buildings);
    }

    private void placeArbors(Random random) {
        // Over the spokes and the outer ring: two posts either side of the path, leaves across the top
        for (int tries = 0; tries < 300 && arbors.size() < 7; tries++) {
            Line line = lines.get(1 + random.nextInt(lines.size() - 1));
            if (line.points.size() < 3) continue;
            int i = 1 + random.nextInt(line.points.size() - 2);
            double[] a = line.points.get(i - 1), b = line.points.get(i), c = line.points.get(i + 1);
            double dx = c[0] - a[0], dz = c[1] - a[1], len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-6) continue;
            double nx = -dz / len, nz = dx / len, off = line.half + 1;
            int x1 = (int) Math.round(b[0] + nx * off), z1 = (int) Math.round(b[1] + nz * off);
            int x2 = (int) Math.round(b[0] - nx * off), z2 = (int) Math.round(b[1] - nz * off);
            if (!isLawn(x1, z1) || !isLawn(x2, z2) || !farFrom(x1, z1, taken, 6) || !farFrom(x2, z2, taken, 6)) continue;
            if (Math.sqrt(b[0] * b[0] + b[1] * b[1]) < PLAZA + 4) continue;
            arbors.add(new Arbor(x1, z1, x2, z2));
            taken.add(new int[]{x1, z1});
            taken.add(new int[]{x2, z2});
            reserve(x1, z1, 0);
            reserve(x2, z2, 0);
        }
    }

    private void placeBenchesAndLanterns() {
        int side = 1;
        for (Line line : lines) {
            double walked = 0, nextLantern = 5, nextBench = 20;
            double lanternEvery = line.avenue ? 11 : 31;
            for (int i = 0; i + 1 < line.points.size(); i++) {
                double[] a = line.points.get(i), b = line.points.get(i + 1);
                double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.sqrt(dx * dx + dz * dz);
                walked += len;
                if (len < 1e-6) continue;
                if (walked >= nextLantern) {
                    side = -side;
                    double nx = -dz / len * side, nz = dx / len * side, off = line.half + 1;
                    int x = (int) Math.round(b[0] + nx * off), z = (int) Math.round(b[1] + nz * off);
                    if (isLawn(x, z) && edge[index(x, z)] > 0 && farFrom(x, z, taken, 3)) {
                        lanterns.add(new Spot(x, z, 0));
                        taken.add(new int[]{x, z});
                        reserve(x, z, 0);
                        nextLantern = walked + lanternEvery;
                    }
                }
                if (!line.avenue && walked >= nextBench && benches.size() < 12) {
                    double nx = dz / len * side, nz = -dx / len * side, off = line.half + 1;
                    int x = (int) Math.round(b[0] + nx * off), z = (int) Math.round(b[1] + nz * off);
                    int dir = dirToward(-nx, -nz);
                    int[] along = alongOf(dir);
                    int x2 = x + along[0], z2 = z + along[1];
                    if (isLawn(x, z) && isLawn(x2, z2) && edge[index(x, z)] > 0 && edge[index(x2, z2)] > 0
                            && farFrom(x, z, taken, 5) && clearLawn(x, z, 1)) {
                        benches.add(new Spot(x, z, dir));
                        taken.add(new int[]{x, z});
                        reserve(x, z, 0);
                        reserve(x2, z2, 0);
                        nextBench = walked + 37;
                    }
                }
            }
        }
        // Four benches round the plaza, between the fountain plinths, looking at the fountain
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + Math.PI / 2 * k;
            int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12);
            benches.add(new Spot(x, z, dirToward(-x, -z)));
        }
    }

    /** One block along a bench facing {@code dir}: its second seat. */
    public static int[] alongOf(int dir) {
        return switch (dir) {
            case 0, 2 -> new int[]{1, 0};
            default -> new int[]{0, 1};
        };
    }

    private void placeRuins(Random random) {
        for (int tries = 0; tries < 200 && ruins.size() < 6; tries++) {
            double a = random.nextDouble() * Math.PI * 2, r = PLAZA + 8 + random.nextDouble() * (outerRing(a) - PLAZA - 6);
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            int dir = random.nextInt(4), length = 4 + random.nextInt(4);
            int[] step = stepOf(dir);
            boolean ok = farFrom(x, z, taken, 8);
            for (int s = -1; s <= length && ok; s++) {
                int wx = x + step[0] * s, wz = z + step[1] * s;
                ok = clearLawn(wx, wz, 1) && edge[index(wx, wz)] >= 3;
            }
            if (!ok) continue;
            ruins.add(new Ruin(x, z, dir, length));
            for (int s = 0; s < length; s++) reserve(x + step[0] * s, z + step[1] * s, 0);
            taken.add(new int[]{x + step[0] * length / 2, z + step[1] * length / 2});
        }
    }

    public static int[] stepOf(int dir) {
        return switch (dir) {
            case 0 -> new int[]{0, 1};
            case 1 -> new int[]{-1, 0};
            case 2 -> new int[]{0, -1};
            default -> new int[]{1, 0};
        };
    }

    // ---- Trees ----

    private void plantTrees(Random random) {
        List<int[]> planted = new ArrayList<>();
        int[][] wanted = {{36, 0}, {12, 1}, {18, 2}};
        for (int[] want : wanted) {
            Tree.Kind kind = Tree.Kind.values()[want[1]];
            int count = 0;
            for (int tries = 0; tries < 3000 && count < want[0]; tries++) {
                double a = random.nextDouble() * Math.PI * 2, r = PLAZA + 4 + random.nextDouble() * (inner(a) - PLAZA - 8);
                int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                if (!clearLawn(x, z, kind == Tree.Kind.TOPIARY ? 0 : 1)) continue;
                double needEdge = kind == Tree.Kind.CHERRY ? 2.5 : 1.5;
                if (edge[index(x, z)] < needEdge) continue;
                if (!farFrom(x, z, planted, kind == Tree.Kind.CHERRY ? 8 : 5) || !farFrom(x, z, taken, 4)) continue;
                if (!farFrom(x, z, buildings, 9)) continue;
                int h = switch (kind) {
                    case CHERRY -> 5 + random.nextInt(3);
                    case AZALEA -> 3 + random.nextInt(2);
                    case TOPIARY -> 1 + random.nextInt(2);
                };
                trees.add(new Tree(x, z, kind, h));
                planted.add(new int[]{x, z});
                reserve(x, z, 0);
                count++;
            }
        }
    }

    // ---- Low hedges along the paths ----

    private void plantBorders() {
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                int i = index(x, z);
                if (ground[i] != Ground.LAWN || reserved[i]) continue;
                double r = Math.sqrt(x * x + z * z);
                boolean pathSide = edge[i] > 0 && edge[i] <= 1.0;
                boolean plazaSide = r >= PLAZA && r < PLAZA + 1.2;
                if (!pathSide && !plazaSide) continue;
                if (smooth(x, z, 7, 5) < 0.3) continue; // a gap now and then
                ground[i] = Ground.BORDER;
                height[i] = (byte) (smooth(x, z, 4, 6) > 0.72 ? 2 : 1);
            }
        }
    }

    // ---- Reading the plan ----

    public Ground ground(int x, int z) {
        return inGrid(x, z) ? ground[index(x, z)] : Ground.OUTSIDE;
    }

    /** Hedge height (HEDGE, GATE), low hedge height (BORDER) or water depth (POND). */
    public int height(int x, int z) {
        return inGrid(x, z) ? height[index(x, z)] : 0;
    }

    /** How far (x, z) is from the nearest path's edge (negative on a path), up to 10. */
    public double edge(int x, int z) {
        return inGrid(x, z) ? edge[index(x, z)] : 10;
    }

    /** Taken by something built (a plinth, building, bench, tree...): no flowers or low hedge here. */
    public boolean reserved(int x, int z) {
        return inGrid(x, z) && reserved[index(x, z)];
    }

    /** Inside the hedge: where the garden's rules hold. The tunnel through the gate isn't inside yet. */
    public boolean inside(int x, int z) {
        return switch (ground(x, z)) {
            case OUTSIDE, SKIRT, HEDGE, GATE -> false;
            case AVENUE -> Math.sqrt(x * x + z * z) < inner(Math.atan2(z, x)) - innerJitter(x, z);
            default -> true;
        };
    }

    /** 0 on the garden's level, rising to 1 at the skirt's edge where the natural ground takes over. */
    public double skirtBlend(int x, int z) {
        double r = Math.sqrt(x * x + z * z), ro = outer(Math.atan2(z, x)) + bulge(x, z);
        double t = Math.max(0, Math.min(1, (r - ro) / SKIRT));
        return fade(t);
    }

    public List<Plinth> plinths() {
        return plinths;
    }

    public List<Spot> gazebos() {
        return gazebos;
    }

    public List<Spot> sheds() {
        return sheds;
    }

    public List<Spot> benches() {
        return benches;
    }

    public List<Spot> lanterns() {
        return lanterns;
    }

    public List<Arbor> arbors() {
        return arbors;
    }

    public List<Ruin> ruins() {
        return ruins;
    }

    public List<Tree> trees() {
        return trees;
    }

    /** The two pillars at the gate's outer mouth, either side of the avenue: {x, z} each. */
    public List<int[]> gatePillars() {
        if (gatePillars.isEmpty()) {
            double gx = Math.cos(gateAngle), gz = Math.sin(gateAngle), r = outer(gateAngle) + 2;
            for (int side : new int[]{-1, 1}) {
                gatePillars.add(new int[]{(int) Math.round(gx * r - gz * 4 * side), (int) Math.round(gz * r + gx * 4 * side)});
            }
        }
        return gatePillars;
    }

    /** Where to stand just outside the gate, on the avenue: {x, z}. */
    public int[] outsideGate() {
        double r = outer(gateAngle) + SKIRT / 2.0;
        return new int[]{(int) Math.round(Math.cos(gateAngle) * r), (int) Math.round(Math.sin(gateAngle) * r)};
    }

    /** The four empty plinths round the fountain (north, east, south, west): {x, z} each. */
    public static List<int[]> fountainPlinths() {
        return List.of(new int[]{0, -FOUNTAIN_PLINTH}, new int[]{FOUNTAIN_PLINTH, 0}, new int[]{0, FOUNTAIN_PLINTH}, new int[]{-FOUNTAIN_PLINTH, 0});
    }

    /** The y of the step at {@code z} on the stair down, or Integer.MIN_VALUE if z isn't on it. */
    public static int stepY(int z) {
        int i = CAP_Z0 - z;
        return i >= 0 && i < STEPS ? -3 - i : Integer.MIN_VALUE;
    }

    /** Whether (x, y, z) is inside the crypt or the stair down to it (where someone could be shut in). */
    public static boolean inCrypt(int x, int y, int z) {
        if (y < CRYPT_FLOOR || y > CAP_Y) return false;
        if (Math.abs(x) <= 1 && z <= CAP_Z0 && z > CRYPT_NEAR) return y >= stepY(z);
        return Math.abs(x) <= CRYPT_HALF_WIDTH && z <= CRYPT_NEAR && z >= CRYPT_FAR && y <= -5;
    }
}
