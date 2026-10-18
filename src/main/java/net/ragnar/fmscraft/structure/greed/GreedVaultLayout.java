package net.ragnar.fmscraft.structure.greed;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Set;

/**
 * Where things are in the Greed Vault template ({@code data/fmscraft/structure/greed_vault.nbt}, built by
 * tools/structures/greed_vault.py), in template coordinates: x 0-76 west to east, y 0-14, z 0-34 north to south.
 * The vaultIsBuiltWithItsPuzzle game test checks these against the template, so a re-saved vault that moved
 * something fails there first.
 */
public final class GreedVaultLayout {
    public static final int SIZE_X = 77, SIZE_Y = 15, SIZE_Z = 35;
    /** The vault turns around this block; it lands on the center of the chunk it starts in. */
    public static final BlockPos PIVOT = new BlockPos(SIZE_X / 2, 0, SIZE_Z / 2);
    /** The ceiling of the rooms; the pipe rises above it. */
    public static final int BODY_TOP = 6;
    /** The first template y above the ground at the pipe: 4 blocks of pipe stick up out of the surface. */
    public static final int SURFACE = 11;

    /** The inside of the pipe, the way in (and, by its ladder, out). */
    public static final int PIPE_MIN_X = 72, PIPE_MAX_X = 75, PIPE_MIN_Z = 13, PIPE_MAX_Z = 16;
    public static final BlockPos PIPE_CENTER = new BlockPos(74, SIZE_Y - 1, 15);

    /**
     * The 8 levers, numbered the way they read to someone facing the lever wall: top row 1-4 left to right, bottom
     * row 5-8. They face north, so "left" is east.
     */
    public static final List<BlockPos> LEVERS = List.of(
            new BlockPos(28, 4, 18), new BlockPos(26, 4, 18), new BlockPos(24, 4, 18), new BlockPos(22, 4, 18),
            new BlockPos(28, 2, 18), new BlockPos(26, 2, 18), new BlockPos(24, 2, 18), new BlockPos(22, 2, 18));
    /** The numbers the clue books hide. Only exactly these levers on opens the door. */
    public static final Set<Integer> CODE = Set.of(1, 5, 7);

    /** The 4x4 iron block door, x = 20, y 1-4, z 13-16, between the lever room and the vault proper. */
    public static final int DOOR_X = 20, DOOR_MIN_Y = 1, DOOR_MIN_Z = 13, DOOR_SIZE = 4;
    /**
     * Behind the door: the treasury and the portal room. Anyone in here while the door is shut (back from the fight
     * after the vault reset, or logged out in here) gets it opened, so nobody is ever shut in.
     */
    public static final int INNER_MIN_X = 1, INNER_MAX_X = 19, INNER_MIN_Z = 5, INNER_MAX_Z = 24, INNER_MAX_Y = 5;

    /** The Greed lock, facing the way players stand to use it; the frame's opening is 4 blocks north of it at z = 9. */
    public static final BlockPos LOCK = new BlockPos(6, 1, 13);
    public static final Direction LOCK_FACING = Direction.SOUTH;

    /** The three smooth stone blocks in the floor the Statues of Greed rise from (the block above each). */
    public static final List<BlockPos> STATUE_HOMES = List.of(
            new BlockPos(39, 1, 33), new BlockPos(42, 1, 1), new BlockPos(45, 1, 28));

    private GreedVaultLayout() {
    }

    public static List<BlockPos> doorBlocks() {
        List<BlockPos> blocks = new java.util.ArrayList<>();
        for (int y = DOOR_MIN_Y + DOOR_SIZE - 1; y >= DOOR_MIN_Y; y--) {
            for (int z = DOOR_MIN_Z; z < DOOR_MIN_Z + DOOR_SIZE; z++) blocks.add(new BlockPos(DOOR_X, y, z));
        }
        return blocks;
    }

    /** One row of the door, top (0) to bottom (3): the order it sinks into the floor. */
    public static List<BlockPos> doorRow(int row) {
        return doorBlocks().subList(row * DOOR_SIZE, (row + 1) * DOOR_SIZE);
    }

    /** Whether these lever numbers (1-8) being on, and no others, is the code. */
    public static boolean opens(Set<Integer> on) {
        return on.equals(CODE);
    }

    /** The buried part of the vault, the rooms and corridors, not the pipe above them. */
    public static boolean inBody(BlockPos local) {
        return local.getX() >= 0 && local.getX() < SIZE_X && local.getZ() >= 0 && local.getZ() < SIZE_Z
                && local.getY() >= 0 && local.getY() <= BODY_TOP;
    }

    public static boolean inner(BlockPos local) {
        return local.getX() >= INNER_MIN_X && local.getX() <= INNER_MAX_X && local.getZ() >= INNER_MIN_Z
                && local.getZ() <= INNER_MAX_Z && local.getY() >= 0 && local.getY() <= INNER_MAX_Y;
    }
}
