package net.ragnar.fmscraft.boss.lust;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.fight.ArenaLayout;

/**
 * MUGEN's arena: the walled Japanese compound under the cherry trees, in data/fmscraft/structure/arena/lust.nbt (built
 * by tools/structures/lust_arena.py from lust_arena_source.nbt). The great hall fills its west side; the east side is
 * a garden of cherry trees and ponds round an open stone courtyard. The fight starts in the courtyard, but the whole
 * compound inside its walls is his ground: the garden, the bridges, the roofs.
 * <p>
 * The template keeps the source's positions: the courtyard's stones (template y 12) are at world y 63 and the middle of
 * the courtyard (template x 84, z 62) is world x/z 0. Checked by the lustFightPlaysTheIntroThenMugen game test.
 */
public final class LustArena {
    public static final int SIZE_X = 118, SIZE_Y = 59, SIZE_Z = 120;
    public static final BlockPos CORNER = new BlockPos(-84, 51, -62);
    /** Feet height on the courtyard's stones. */
    public static final int FLOOR_Y = 64;
    /** The middle of the courtyard. He rises here, and comes back down here for Toxic. */
    public static final Vec3d CENTER = new Vec3d(0.5, FLOOR_Y, 0.5);
    /** Players arrive at the courtyard's east end, facing west down it towards the middle. */
    public static final Vec3d PLAYER_SPAWN = new Vec3d(16.5, FLOOR_Y, 0.5);
    public static final float PLAYER_YAW = 90f;
    /** He starts deep under the courtyard's middle and is dragged up out of it. */
    public static final Vec3d BOSS_SPAWN = CENTER.add(0, -MugenTimeline.RISE_DEPTH, 0);
    /** Bottom-left of the exit portal, on the courtyard's north edge. An empty frame stands there all fight. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(-13, FLOOR_Y, -5);
    /** Inside the compound's walls (template x 13-106, z 16-109). */
    public static final double MIN_X = CORNER.getX() + 13, MAX_X = CORNER.getX() + 107, MIN_Z = CORNER.getZ() + 16, MAX_Z = CORNER.getZ() + 110;
    /** Below this you've fallen through (there's nothing under the compound); above this you're over the roofs. */
    public static final double MIN_Y = FLOOR_Y - 8, MAX_Y = FLOOR_Y + 36;
    /** How far into the courtyard the shadows drag anyone back. */
    public static final double RETURN = 6;

    public static final ArenaLayout LAYOUT = new ArenaLayout(PLAYER_SPAWN, PLAYER_YAW, BOSS_SPAWN, EXIT_PORTAL, CORNER,
            new Box(CORNER.getX() - 2, CORNER.getY() - 4, CORNER.getZ() - 2,
                    CORNER.getX() + SIZE_X + 2, CORNER.getY() + SIZE_Y + 4, CORNER.getZ() + SIZE_Z + 2),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.CRYING_OBSIDIAN.getDefaultState());

    private LustArena() {
    }

    /** Whether {@code pos} is inside the compound's walls (the garden, the courtyard, the hall, its roofs). */
    public static boolean inBounds(Vec3d pos) {
        return pos.x >= MIN_X && pos.x <= MAX_X && pos.z >= MIN_Z && pos.z <= MAX_Z && pos.y >= MIN_Y && pos.y <= MAX_Y;
    }

    /** Where the shadows set down someone dragged back from {@code pos}: {@link #RETURN} out from the middle, towards where they were. */
    public static Vec3d returnSpot(Vec3d pos) {
        double dx = pos.x - CENTER.x, dz = pos.z - CENTER.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) return CENTER;
        // The courtyard is long east to west: keep the spot on its stones
        double x = CENTER.x + dx / d * RETURN, z = CENTER.z + MathHelper.clamp(dz / d * RETURN, -4, 4);
        return new Vec3d(x, FLOOR_Y, z);
    }

    public static double flatDistance(double x, double z, Vec3d to) {
        return Math.sqrt(MathHelper.square(x - to.x) + MathHelper.square(z - to.z));
    }
}
