package net.ragnar.fmscraft.boss.greed;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.fight.ArenaLayout;

/**
 * RAGGIMEL's arena: the floating island in data/fmscraft/structure/arena/greed.nbt (built by
 * tools/structures/greed_arena.py). He fights on the wide, flat grass plain west of the amethyst shrine near the
 * island's east end (template x 120-140, z 14-32, floor at template y 110). Players arrive on the plain too; the
 * shrine's pillars and the trees give cover from the lasers. The island drops into the void on every side.
 * <p>
 * Positions are given in template coordinates (as the script prints them) and moved into the world by {@link #CORNER},
 * which puts the middle of the shrine ring at x/z 0 (so the plain's middle is at about x -30, z 0). They're checked by
 * the greedFightPlaysTheIntroThenRaggimel game test.
 */
public final class GreedArena {
    /** Template size, for the area wiped before each fight. */
    public static final int SIZE_X = 217, SIZE_Y = 169, SIZE_Z = 55;
    /** Where the template's corner goes. */
    public static final BlockPos CORNER = new BlockPos(-161, 20, -28);

    /** Players arrive on the west side of the plain (world x -40, z 0), facing east towards where he comes down. */
    public static final Vec3d PLAYER_SPAWN = at(121.5, 112, 28.5);
    public static final float PLAYER_YAW = -90f;
    /** The middle of the plain (world -30, 0), at floor height. He lands here, and the night's effects center on it. */
    public static final Vec3d CENTER = at(131.5, 111, 28.5);
    /** RAGGIMEL starts floating in his bubble 13 blocks over the middle of the plain. */
    public static final Vec3d BOSS_SPAWN = CENTER.add(0, 13, 0);
    /** Where he comes back down after the phase change. */
    public static final Vec3d LANDING = CENTER;
    /** Where he hangs over the island's middle shrine, west of the plain, firing lasers before phase 2 starts properly. */
    public static final Vec3d PERCH = at(109.5, 133, 24.5);
    /**
     * Bottom-left of the exit portal, on the north edge of the plain. An empty frame stands there all fight
     * (deepslate tiles, gold corners); the portal fills it when he's beaten.
     */
    public static final BlockPos EXIT_PORTAL = CORNER.add(130, 111, 18);
    /** Below this, you've fallen off the island. */
    public static final int VOID_Y = CORNER.getY() - 8;

    public static final ArenaLayout LAYOUT = new ArenaLayout(PLAYER_SPAWN, PLAYER_YAW, BOSS_SPAWN, EXIT_PORTAL, CORNER,
            new Box(CORNER.getX() - 2, CORNER.getY() - 2, CORNER.getZ() - 2,
                    CORNER.getX() + SIZE_X + 2, CORNER.getY() + SIZE_Y + 2, CORNER.getZ() + SIZE_Z + 2),
            Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.GOLD_BLOCK.getDefaultState());

    private GreedArena() {
    }

    private static Vec3d at(double x, double y, double z) {
        return new Vec3d(CORNER.getX() + x, CORNER.getY() + y, CORNER.getZ() + z);
    }
}
