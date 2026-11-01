package net.ragnar.fmscraft.boss.envy;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.fight.ArenaLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GUMMSS's arena: a round floating floor 101 blocks across, hanging alone in a black void filled with deep green fog.
 * Built in code (not a template) from blackstone, deepslate and obsidian. Seen from above, as everyone falls towards
 * it in the intro, the whole floor is one great eye: a calcite-and-copper eye in the middle, the iris's fibres running
 * out from it to the rim in lines of polished blackstone, rings of blackstone and obsidian, and a low wall round the
 * edge. Obsidian pillars stand round it (cover from his darts and swords), a ring of broken deepslate columns closer in.
 * <p>
 * Eyes hang everywhere round it, watching (drawn by EnvySky on each client from {@link #eyes()}): huge ones out in the
 * void that turn to follow you and blink, and smaller ones on the pillars, staring in at the middle.
 * Checked by the envyFightFallsIntoTheArena game test.
 */
public final class EnvyArena {
    public static final int RADIUS = 50;
    /** The floor blocks' layer; feet stand one higher. */
    public static final int FLOOR_BLOCK_Y = 63, FLOOR_Y = 64;
    public static final Vec3d CENTER = new Vec3d(0.5, FLOOR_Y, 0.5);
    /** Players wait on the floor behind the wheel, south of the middle, then are lifted into the ring. */
    public static final Vec3d PLAYER_SPAWN = new Vec3d(0.5, FLOOR_Y, 20.5);
    public static final float PLAYER_YAW = 180f;
    /** He starts high over the middle and falls with everyone (see {@link GummssTimeline}). */
    public static final Vec3d BOSS_SPAWN = CENTER.add(0, GummssTimeline.FALL_HEIGHT, 0);
    /** Bottom-left of the exit portal, near the north edge. An empty frame stands there all fight. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(0, FLOOR_Y, -38);
    /** Anyone further out than this, or this far under the floor, is caught by his telekinesis and dragged back. */
    public static final double CATCH_RADIUS = RADIUS + 1.5, CATCH_DEPTH = 4;

    /** The eye in the middle: half as long (x) as it is wide, half as tall (z), its pupil and iris. */
    static final double EYE_A = 13, EYE_B = 7, PUPIL = 3.2, IRIS_IN = 4.4, IRIS_OUT = 5.8;
    static final int SPOKES = 16, OUTER_PILLARS = 12, INNER_COLUMNS = 6;
    static final double OUTER_PILLAR_R = 43, INNER_COLUMN_R = 25;

    private static final int CLEAR = RADIUS + 6;
    public static final ArenaLayout LAYOUT = new ArenaLayout(PLAYER_SPAWN, PLAYER_YAW, BOSS_SPAWN, EXIT_PORTAL,
            BlockPos.ORIGIN, new Box(-CLEAR, FLOOR_BLOCK_Y - 30, -CLEAR, CLEAR + 1, FLOOR_Y + 40, CLEAR + 1),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.OXIDIZED_CHISELED_COPPER.getDefaultState());

    private EnvyArena() {
    }

    // ---- Shape (pure, so it's easy to reason about) ----

    public static double flatDistance(double x, double z) {
        return Math.sqrt((x - CENTER.x) * (x - CENTER.x) + (z - CENTER.z) * (z - CENTER.z));
    }

    /** Whether {@code pos} is over the floor (not out past the rim, not under it). */
    public static boolean onFloor(Vec3d pos) {
        return flatDistance(pos.x, pos.z) <= RADIUS - 0.5 && pos.y > FLOOR_Y - CATCH_DEPTH;
    }

    /** 0 in the middle of the eye, 1 on its lids, over 1 outside it. */
    static double lid(int x, int z) {
        double half = EYE_B * (1 - (x / EYE_A) * (x / EYE_A));
        if (half <= 0.3) return 9;
        return Math.abs(z) / half;
    }

    /** How deep the floor's underside reaches under column {@code x, z} (more in the middle, a cone like an island's). */
    static int depth(int x, int z, double d) {
        long h = hash(x, z);
        return 2 + (int) Math.round((RADIUS - d) * 0.42) + (int) (h & 3) - 1;
    }

    static long hash(int x, int z) {
        long h = x * 3129871L ^ z * 116129781L;
        h = h * h * 42317861L + h * 11L;
        return (h >> 16) & 0xFFFF;
    }

    /** Angle between {@code x, z} and the nearest iris spoke, times the distance out: how far off the line, in blocks. */
    static double offSpoke(int x, int z) {
        double a = Math.atan2(z, x), step = 2 * Math.PI / SPOKES;
        double off = Math.abs(a - Math.round(a / step) * step);
        return off * Math.sqrt(x * x + z * z);
    }

    // ---- The eyes that watch the fight ----

    /**
     * An eye in the dark round the arena: its middle, how wide it is, and whether it turns to look at you (the ones
     * out in the void) or stares in at the middle from where it's fixed (the ones on the pillars, {@code yaw}).
     */
    public record Eye(double x, double y, double z, double width, boolean turns, float yaw, long seed) {
    }

    /** Every eye, always the same ones in the same places. */
    public static List<Eye> eyes() {
        List<Eye> eyes = new ArrayList<>();
        Random r = new Random(0xE7E5L);
        // Out in the void: a few rings of them at different heights and distances, so they're everywhere you look
        int count = 34;
        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count + r.nextDouble() * 0.15;
            double d = 72 + r.nextDouble() * 52;
            double y = FLOOR_Y - 30 + r.nextDouble() * 85;
            double w = 7 + r.nextDouble() * 10;
            eyes.add(new Eye(CENTER.x + Math.cos(a) * d, y, CENTER.z + Math.sin(a) * d, w, true, 0, r.nextLong()));
        }
        // A few right underneath, looking up through the gaps as you fall
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 58 + r.nextDouble() * 20;
            eyes.add(new Eye(CENTER.x + Math.cos(a) * d, FLOOR_Y - 45 - r.nextDouble() * 20, CENTER.z + Math.sin(a) * d,
                    12 + r.nextDouble() * 8, true, 0, r.nextLong()));
        }
        // On the face of every tall pillar, staring in at the middle
        // (yaw as for a mob: 0 faces south, 90 west)
        for (int i = 0; i < OUTER_PILLARS; i++) {
            if (pillarHeight(i) < 8) continue;
            double a = pillarAngle(i);
            int[] c = pillarCenter(i);
            double x, z;
            float yaw;
            if (Math.abs(Math.cos(a)) >= Math.abs(Math.sin(a))) {
                boolean east = Math.cos(a) > 0;
                x = east ? c[0] - 1 - 0.03 : c[0] + 2 + 0.03;
                z = c[1] + 0.5;
                yaw = east ? 90f : -90f;
            } else {
                boolean south = Math.sin(a) > 0;
                x = c[0] + 0.5;
                z = south ? c[1] - 1 - 0.03 : c[1] + 2 + 0.03;
                yaw = south ? 180f : 0f;
            }
            eyes.add(new Eye(x, FLOOR_Y + 5.2, z, 2.6, false, yaw, r.nextLong()));
        }
        return eyes;
    }

    static double pillarAngle(int i) {
        return Math.PI / OUTER_PILLARS + i * 2 * Math.PI / OUTER_PILLARS;
    }

    /** The middle block of outer pillar {@code i} (it's 3 by 3 round it). */
    static int[] pillarCenter(int i) {
        double a = pillarAngle(i);
        return new int[]{(int) Math.round(Math.cos(a) * OUTER_PILLAR_R), (int) Math.round(Math.sin(a) * OUTER_PILLAR_R)};
    }

    /** The outer pillars, every third one broken off short. */
    static int pillarHeight(int i) {
        if (i % 3 == 2) return 4 + i % 3;
        return 10 + (i * 7) % 6;
    }

    // ---- Building it ----

    /** Builds the whole arena (the area has already been cleared). */
    public static void build(ServerWorld world) {
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > RADIUS + 0.5) continue;
                world.setBlockState(pos.set(x, FLOOR_BLOCK_Y, z), floor(x, z, d), flags);
                int depth = depth(x, z, d);
                for (int k = 1; k <= depth; k++) {
                    world.setBlockState(pos.set(x, FLOOR_BLOCK_Y - k, z), under(x, z, k, depth), flags);
                }
                // The low wall round the rim
                if (d > RADIUS - 0.5) world.setBlockState(pos.set(x, FLOOR_Y, z), Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState(), flags);
            }
        }
        for (int i = 0; i < OUTER_PILLARS; i++) outerPillar(world, i, flags);
        for (int i = 0; i < INNER_COLUMNS; i++) innerColumn(world, i, flags);
        // Wall blocks only join up once their neighbours are there
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= RADIUS - 0.5 || d > RADIUS + 0.5) continue;
                pos.set(x, FLOOR_Y, z);
                BlockState wall = world.getBlockState(pos);
                world.setBlockState(pos, Block.postProcessState(wall, world, pos), flags);
            }
        }
    }

    private static BlockState floor(int x, int z, double d) {
        long h = hash(x, z);
        double lid = lid(x, z);
        if (lid <= 1) {
            if (d <= PUPIL) {
                // The pupil, with the three glints of the eye it's drawn from
                if (z == -1 && (x == -2 || x == 0 || x == 2)) return Blocks.QUARTZ_BLOCK.getDefaultState();
                return Blocks.OBSIDIAN.getDefaultState();
            }
            if (d <= IRIS_IN) return h % 7 == 0 ? Blocks.VERDANT_FROGLIGHT.getDefaultState() : Blocks.OXIDIZED_COPPER.getDefaultState();
            if (d <= IRIS_OUT) return h % 5 == 0 ? Blocks.OXIDIZED_CUT_COPPER.getDefaultState() : Blocks.GREEN_TERRACOTTA.getDefaultState();
            if (lid > 0.82) return Blocks.POLISHED_BLACKSTONE.getDefaultState();
            if (lid > 0.62) return Blocks.POLISHED_ANDESITE.getDefaultState();
            if (lid > 0.4) return Blocks.SMOOTH_STONE.getDefaultState();
            return Blocks.CALCITE.getDefaultState();
        }
        // A dark shadow round the lids
        if (lid <= 1.35 || Math.abs(x) <= EYE_A + 1 && Math.abs(z) <= 1 && lid < 9.5) return Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (d > RADIUS - 3) return (x + z & 1) == 0 ? Blocks.DEEPSLATE_BRICKS.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState();
        if (d >= 15.5 && d < 17) return h % 9 == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        if (d >= 31 && d < 33) return h % 11 == 0 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.OBSIDIAN.getDefaultState();
        if (d >= 17 && offSpoke(x, z) < 0.55) {
            // The iris's fibres, with a light set into every other one
            boolean lit = Math.round(Math.atan2(z, x) / (2 * Math.PI / SPOKES)) % 2 == 0;
            if (lit && Math.abs(d - Math.round(d / 7.0) * 7) < 0.5 && d > 20) return Blocks.VERDANT_FROGLIGHT.getDefaultState();
            return Blocks.POLISHED_BLACKSTONE.getDefaultState();
        }
        // Worn old floor: mostly polished deepslate, cracked and patched here and there
        int n = (int) (h % 23);
        if (n == 0) return Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
        if (n == 1) return Blocks.COBBLED_DEEPSLATE.getDefaultState();
        if (n == 2) return Blocks.BLACKSTONE.getDefaultState();
        if (n < 6) return Blocks.DEEPSLATE_TILES.getDefaultState();
        return Blocks.POLISHED_DEEPSLATE.getDefaultState();
    }

    private static BlockState under(int x, int z, int k, int depth) {
        long h = hash(x * 7 + k, z * 13 - k);
        if (k == 1) return h % 3 == 0 ? Blocks.COBBLED_DEEPSLATE.getDefaultState() : Blocks.DEEPSLATE.getDefaultState();
        if (k == depth) return h % 4 == 0 ? Blocks.OBSIDIAN.getDefaultState() : Blocks.BLACKSTONE.getDefaultState();
        return switch ((int) (h % 6)) {
            case 0, 1 -> Blocks.BLACKSTONE.getDefaultState();
            case 2 -> Blocks.BASALT.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
            case 3 -> Blocks.SMOOTH_BASALT.getDefaultState();
            default -> Blocks.DEEPSLATE.getDefaultState();
        };
    }

    /** An obsidian pillar with blackstone corners and a light on top; the broken ones end in a jagged stump. */
    private static void outerPillar(ServerWorld world, int i, int flags) {
        int[] c = pillarCenter(i);
        int cx = c[0], cz = c[1];
        int height = pillarHeight(i);
        boolean broken = i % 3 == 2;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean corner = dx != 0 && dz != 0;
                int top = broken ? height - (int) (hash(cx + dx, cz + dz) % 3) : height;
                for (int y = 0; y < top; y++) {
                    BlockState state = corner ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState()
                            : hash(cx + dx, y * 31 + cz + dz) % 9 == 0 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.OBSIDIAN.getDefaultState();
                    if (!broken && y == top - 1) state = Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState();
                    world.setBlockState(pos.set(cx + dx, FLOOR_Y + y, cz + dz), state, flags);
                }
            }
        }
        if (broken) {
            // Rubble lying round the stump
            for (int k = 0; k < 6; k++) {
                long h = hash(cx * 5 + k, cz * 3 - k);
                int rx = cx + (int) (h % 7) - 3, rz = cz + (int) ((h >> 4) % 7) - 3;
                if (Math.abs(rx - cx) <= 1 && Math.abs(rz - cz) <= 1) continue;
                if (Math.sqrt(rx * rx + rz * rz) > RADIUS - 2) continue;
                world.setBlockState(pos.set(rx, FLOOR_Y, rz), k % 2 == 0 ? Blocks.OBSIDIAN.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), flags);
            }
        } else {
            world.setBlockState(pos.set(cx, FLOOR_Y + height, cz), Blocks.VERDANT_FROGLIGHT.getDefaultState(), flags);
        }
    }

    /** A broken deepslate column, 2 by 2. */
    private static void innerColumn(ServerWorld world, int i, int flags) {
        double a = Math.PI / 6 + i * 2 * Math.PI / INNER_COLUMNS;
        int cx = (int) Math.round(Math.cos(a) * INNER_COLUMN_R), cz = (int) Math.round(Math.sin(a) * INNER_COLUMN_R);
        int[] heights = {3, 6, 4, 7, 3, 5};
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int top = heights[i] - (int) (hash(cx + dx, cz + dz) % 2);
                for (int y = 0; y < top; y++) {
                    BlockState state = hash(cx + dx * 3, y + cz + dz) % 4 == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState();
                    world.setBlockState(pos.set(cx + dx, FLOOR_Y + y, cz + dz), state, flags);
                }
            }
        }
    }
}
