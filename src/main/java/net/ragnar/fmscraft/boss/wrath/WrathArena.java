package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.fight.ArenaLayout;

/**
 * ORNAMIX's arena: the burning colosseum in data/fmscraft/structure/arena/wrath.nbt (built by
 * tools/structures/wrath_arena.py). The fight is down in its round sand pit, 81 blocks across, walled in 5 high with
 * the stands rising all round; the tunnels out under the stands are barred. The template keeps the source's
 * positions, so its middle (template x/z 69) lands on world x/z 0 and the sand's top is at world y 65.
 * Checked by the wrathFightPlaysTheIntroThenOrnamix game test.
 */
public final class WrathArena {
    public static final int SIZE_X = 139, SIZE_Y = 32, SIZE_Z = 139;
    public static final BlockPos CORNER = new BlockPos(-69, 63, -69);
    /** Feet height on the sand. */
    public static final int FLOOR_Y = 65;
    /** The middle of the pit, on the sand. He lands here and comes back here for the phase change. */
    public static final Vec3d CENTER = new Vec3d(0.5, FLOOR_Y, 0.5);
    /** How far out the sand goes; the pit wall stands just past it. */
    public static final double PIT_RADIUS = 40.6;
    /** Players arrive on the south side of the pit, facing north towards the middle. */
    public static final Vec3d PLAYER_SPAWN = new Vec3d(0.5, FLOOR_Y, 30.5);
    public static final float PLAYER_YAW = 180f;
    /** He starts high over the middle and comes down like a meteor (see {@link OrnamixTimeline#ENTRANCE_FALL}). */
    public static final Vec3d BOSS_SPAWN = CENTER.add(0, 34, 0);
    /** Bottom-left of the exit portal, by the north wall. An empty frame stands there all fight. */
    public static final BlockPos EXIT_PORTAL = new BlockPos(0, FLOOR_Y, -35);

    public static final ArenaLayout LAYOUT = new ArenaLayout(PLAYER_SPAWN, PLAYER_YAW, BOSS_SPAWN, EXIT_PORTAL, CORNER,
            new Box(CORNER.getX() - 2, CORNER.getY() - 2, CORNER.getZ() - 2,
                    CORNER.getX() + SIZE_X + 2, CORNER.getY() + SIZE_Y + 2, CORNER.getZ() + SIZE_Z + 2),
            Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.GILDED_BLACKSTONE.getDefaultState());

    private WrathArena() {
    }

    /** Whether {@code pos} is down in the pit (not up on the wall, in the stands or anywhere else). */
    public static boolean inPit(Vec3d pos) {
        double dx = pos.x - CENTER.x, dz = pos.z - CENTER.z;
        return dx * dx + dz * dz <= (PIT_RADIUS + 0.6) * (PIT_RADIUS + 0.6) && pos.y < FLOOR_Y + 5 && pos.y > FLOOR_Y - 3;
    }
}
