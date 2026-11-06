package net.ragnar.fmscraft.boss.pride;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.fight.ArenaLayout;

/**
 * LEONARD's arena: a round marble plaza floating high over a sea of clouds, the classical temple on its north side
 * (data/fmscraft/structure/arena/pride.nbt, built by tools/structures/pride_arena.py, which uses the same CORNER).
 * The plaza is 38 blocks out from world x/z 0, its floor at y 63 (you stand at 64); the temple's floor stands 4.5 higher.
 * Players arrive on the west side looking east, over the middle where he strikes down, towards where Munch rises out of
 * the clouds, 64 blocks out.
 */
public final class PrideArena {
    public static final int SIZE_X = 81, SIZE_Y = 60, SIZE_Z = 107;
    public static final BlockPos CORNER = new BlockPos(-40, 40, -66);
    /** Feet height on the plaza. */
    public static final int FLOOR_Y = 64;
    public static final double RADIUS = 38;
    public static final Vec3d CENTER = new Vec3d(0.5, FLOOR_Y, 0.5);
    /** Everyone arrives west of the middle, looking east. */
    public static final Vec3d PLAYER_SPAWN = new Vec3d(-16.5, FLOOR_Y, 0.5);
    public static final float PLAYER_YAW = -90f;
    /** He waits high over the middle, unseen, until he strikes down. */
    public static final Vec3d BOSS_SPAWN = CENTER.add(0, 30, 0);
    /** Bottom-left of the exit portal, on the plaza's south side. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(0, FLOOR_Y, 31);
    /** The temple's floor (x -25..25, z -63..-21), 4.5 over the plaza. */
    public static final double TEMPLE_MIN_X = -25, TEMPLE_MAX_X = 26, TEMPLE_MIN_Z = -63, TEMPLE_MAX_Z = -20;
    /** Below this you've fallen off; above this you're too high to be anywhere honest. */
    public static final double MIN_Y = FLOOR_Y - 8, MAX_Y = FLOOR_Y + 40;
    /** The vanilla cloud layer is drawn this high: a sea of cloud under the plaza (PrideSky). */
    public static final float CLOUD_SEA = 48f;

    // ---- Munch ----
    /**
     * Where Munch stands, out past the east rim, risen: his feet just under the cloud sea, his body from the waist up
     * towering over the plaza, the top of his head 22 blocks over it.
     */
    public static final Vec3d MUNCH = new Vec3d(64.5, 46, 0.5);
    /** He faces west, in over the plaza. */
    public static final float MUNCH_YAW = 90f;
    /** He's 40 blocks tall (a player model is 2 blocks from feet to the top of its head). */
    public static final float MUNCH_SCALE = 20f;
    /** How far under his risen spot he starts: well under the clouds, out of sight. */
    public static final double MUNCH_DEPTH = 80;

    public static final ArenaLayout LAYOUT = new ArenaLayout(PLAYER_SPAWN, PLAYER_YAW, BOSS_SPAWN, EXIT_PORTAL, CORNER,
            new Box(CORNER.getX() - 2, CORNER.getY() - 4, CORNER.getZ() - 2,
                    CORNER.getX() + SIZE_X + 2, CORNER.getY() + SIZE_Y + 4, CORNER.getZ() + SIZE_Z + 2),
            Blocks.QUARTZ_BRICKS.getDefaultState(), Blocks.GOLD_BLOCK.getDefaultState());

    private PrideArena() {
    }

    /** The middle of Munch's head, at his eyes (his neck is 1.5 blocks up the model, the head 0.5 over it). */
    public static Vec3d munchHead() {
        return MUNCH.add(0, (1.5 + 0.22) * MUNCH_SCALE, 0);
    }

    /** Munch's face with his head turned to look at {@code toward}: where his beam comes out. */
    public static Vec3d munchFace(Vec3d toward) {
        Vec3d head = munchHead();
        Vec3d dir = toward.subtract(head);
        dir = dir.lengthSquared() > 1e-6 ? dir.normalize() : new Vec3d(-1, 0, 0);
        return head.add(dir.multiply(0.27 * MUNCH_SCALE));
    }

    /** Whether {@code pos} is on (or over) the plaza or the temple. */
    public static boolean inBounds(Vec3d pos) {
        if (pos.y < MIN_Y || pos.y > MAX_Y) return false;
        double dx = pos.x - CENTER.x, dz = pos.z - CENTER.z;
        if (dx * dx + dz * dz <= (RADIUS + 1) * (RADIUS + 1)) return true;
        return pos.x >= TEMPLE_MIN_X - 2 && pos.x <= TEMPLE_MAX_X + 2 && pos.z >= TEMPLE_MIN_Z - 2 && pos.z <= TEMPLE_MAX_Z;
    }

    /** Where the lightning sets down someone carried back from {@code pos}: 8 in from the rim, towards where they were. */
    public static Vec3d returnSpot(Vec3d pos) {
        double dx = pos.x - CENTER.x, dz = pos.z - CENTER.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) return CENTER;
        double r = Math.min(d, RADIUS - 8);
        return new Vec3d(CENTER.x + dx / d * r, FLOOR_Y, CENTER.z + dz / d * r);
    }

    public static double flatDistance(double x, double z, Vec3d to) {
        return Math.sqrt(MathHelper.square(x - to.x) + MathHelper.square(z - to.z));
    }
}
