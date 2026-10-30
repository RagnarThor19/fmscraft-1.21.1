package net.ragnar.fmscraft.fight;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Where things go in a sin's arena dimension.
 *
 * @param templateCorner  where the arena template's corner is placed, or null to center it on x/z 0 at {@link Arenas#FLOOR_Y}
 * @param clearBox        everything in here is wiped before each fight (entities and blocks)
 * @param exitPortal      bottom-left of the 2x3 exit portal (along x) that opens after a win
 * @param exitFrame       if not null, an empty frame of this around the exit portal is built with the arena, so
 *                        everyone can see where the way home will open; {@code exitFrameCorner} goes in its corners
 */
public record ArenaLayout(Vec3d playerSpawn, float playerYaw, Vec3d bossSpawn, BlockPos exitPortal,
                          BlockPos templateCorner, Box clearBox, BlockState exitFrame, BlockState exitFrameCorner) {
}
