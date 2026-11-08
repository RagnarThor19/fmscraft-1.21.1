package net.ragnar.fmscraft.boss.greed;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RAGGIMEL's second arena: the Roswall manor and its gardens (structure/arena/greed_manor.nbt, built by
 * tools/structures/greed_manor.py from the litematic), floating in the same sky as his island, far out to the east of
 * it. The tide carries everyone here at the phase change. The fight is in the formal garden in front of the house:
 * between its two round fountains, on the stone path down the middle, the lawns and hedges south of it, the house and
 * its wings to the north. Fall off the edge of the grounds and you're gone.
 * <p>
 * Positions are given in template coordinates (the script prints the checks they rely on: the ground there is the
 * litematic's bottom layer, at template y 10) and moved into the world by {@link #CORNER}, which puts the middle of the
 * garden at x 1400, z 0, its ground at y 40. It's built once, when the server starts (half a minute the first time);
 * nothing in a fight breaks it, so before each one only what was left lying about is cleared away.
 */
public final class GreedManor {
    public static final int SIZE_X = 308, SIZE_Y = 94, SIZE_Z = 457;
    /** Template y of the ground. */
    public static final int GROUND = 10;
    /** World y of the ground blocks (feet stand one higher). */
    public static final int FLOOR_Y = 40;
    public static final BlockPos CORNER = new BlockPos(1400 - 153, FLOOR_Y - GROUND, -291);

    /** The middle of the garden, between the fountains, on the stone path. He comes up out of the water here. */
    public static final Vec3d CENTER = at(153.5, 291.5);
    /** Everyone surfaces in an arc 15 blocks south of him, looking north at him and the house. */
    public static final Vec3d PLAYER_ARC = at(153.5, 306.5);
    public static final float PLAYER_YAW = 180f;
    /** Bottom-left of the exit portal (along x), on the path south of the garden. */
    public static final BlockPos EXIT_PORTAL = CORNER.add(152, GROUND + 1, 318);
    /** How far from the middle the fight goes (the garden, the lawns; not the house): past it the water brings you back. */
    public static final double RADIUS = 52;
    /** Below this you've fallen off the grounds. */
    public static final int VOID_Y = CORNER.getY() - 8;
    public static final Box BOX = new Box(CORNER.getX() - 2, CORNER.getY() - 2, CORNER.getZ() - 2,
            CORNER.getX() + SIZE_X + 2, CORNER.getY() + SIZE_Y + 2, CORNER.getZ() + SIZE_Z + 2);

    private GreedManor() {
    }

    private static Vec3d at(double x, double z) {
        return new Vec3d(CORNER.getX() + x, FLOOR_Y + 1, CORNER.getZ() + z);
    }

    /** Whether {@code pos} is in the manor's grounds at all (rather than on the island). */
    public static boolean contains(Vec3d pos) {
        return BOX.contains(pos);
    }

    public static boolean inBounds(Vec3d pos) {
        double dx = pos.x - CENTER.x, dz = pos.z - CENTER.z;
        return dx * dx + dz * dz <= RADIUS * RADIUS && pos.y > FLOOR_Y - 4 && pos.y < FLOOR_Y + 70;
    }

    /** Where someone wandering off at {@code pos} is set back down: 8 in from the edge of the fight, towards them. */
    public static Vec3d returnSpot(Vec3d pos) {
        double dx = pos.x - CENTER.x, dz = pos.z - CENTER.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) return CENTER;
        double r = Math.min(d, RADIUS - 8);
        return new Vec3d(CENTER.x + dx / d * r, FLOOR_Y + 4, CENTER.z + dz / d * r);
    }

    /** Where each of {@code count} players surfaces: an arc round {@link #PLAYER_ARC}, a block and a half apart. */
    public static List<Vec3d> arrivals(int count) {
        List<Vec3d> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double off = (i - (count - 1) / 2.0) * 1.6;
            double a = off / 15.0;
            spots.add(new Vec3d(CENTER.x + Math.sin(a) * 15, PLAYER_ARC.y, CENTER.z + Math.cos(a) * 15));
        }
        return spots;
    }

    /** Yaw that looks from {@code from} at the middle. */
    public static float yawToCenter(Vec3d from) {
        return (float) (MathHelper.atan2(CENTER.z - from.z, CENTER.x - from.x) * (180 / Math.PI)) - 90f;
    }

    // ---- Building it ----

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerWorld world = Arenas.world(server, Sin.GREED);
            if (world == null || built(world)) return;
            long start = System.currentTimeMillis();
            place(world);
            FmsCraft.LOGGER.info("Built RAGGIMEL's manor in {} ms", System.currentTimeMillis() - start);
        });
    }

    /** Whether the manor is there: the stone path in the middle of the garden, a far lawn, the earth under the edge. */
    public static boolean built(ServerWorld world) {
        return world.getBlockState(BlockPos.ofFloored(CENTER).down()).isOf(Blocks.STONE_BRICKS)
                && world.getBlockState(CORNER.add(66, GROUND, 378)).isOf(Blocks.GRASS_BLOCK);
    }

    /** Before each fight (Arenas.reset): builds it if it isn't there, and clears away anything left from the last one. */
    public static void prepare(ServerWorld world) {
        if (!built(world)) place(world);
        for (Entity e : world.getEntitiesByClass(Entity.class, BOX, e -> !(e instanceof PlayerEntity))) e.discard();
    }

    private static void place(ServerWorld world) {
        Optional<StructureTemplate> template = world.getStructureTemplateManager().getTemplate(Identifier.of(FmsCraft.MOD_ID, "arena/greed_manor"));
        if (template.isEmpty()) {
            FmsCraft.LOGGER.error("RAGGIMEL's manor template arena/greed_manor is missing");
            return;
        }
        template.get().place(world, CORNER, CORNER, new StructurePlacementData(), world.getRandom(), Block.NOTIFY_LISTENERS);
        for (Entity dropped : world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, BOX, e -> true)) dropped.discard();
    }
}
