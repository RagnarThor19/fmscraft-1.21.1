package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DetectorRailBlock;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.ragnar.fmscraft.world.FastMinecarts;

import java.util.ArrayList;
import java.util.List;

/**
 * The minecartSpeed gamerule. One test for everything, because the gamerule is the whole server's: a straight run at
 * speed 1 and at 10, then a track with two curves, a slope and a detector rail taken at 10.
 */
public class MinecartGameTests implements FabricGameTest {
    // Far from everything else, on the test world's flat ground
    private static final int X = 40000, Z = 40000;

    private static final class Track {
        final List<BlockPos> rails = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
    }

    private static void rail(ServerWorld world, Track track, BlockPos pos, BlockState state, boolean powered) {
        world.setBlockState(pos.down(), (powered ? Blocks.REDSTONE_BLOCK : Blocks.STONE).getDefaultState());
        world.setBlockState(pos, state);
        track.rails.add(pos);
        track.states.add(state);
    }

    private static BlockState powered(RailShape shape) {
        return Blocks.POWERED_RAIL.getDefaultState().with(PoweredRailBlock.SHAPE, shape).with(PoweredRailBlock.POWERED, true);
    }

    /** Rails reshape themselves as their neighbours go down; once they're all down, put back the shapes we meant. */
    private static void settle(ServerWorld world, Track track) {
        for (int i = 0; i < track.rails.size(); i++) {
            world.setBlockState(track.rails.get(i), track.states.get(i), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }

    private static void setSpeed(ServerWorld world, int speed) {
        world.getGameRules().get(FastMinecarts.SPEED).set(speed, world.getServer());
    }

    private static MinecartEntity cart(ServerWorld world, BlockPos on) {
        MinecartEntity cart = EntityType.MINECART.create(world);
        cart.refreshPositionAndAngles(on.getX() + 0.5, on.getY(), on.getZ() + 0.5, 0, 0);
        cart.setVelocity(0.4, 0, 0);
        world.spawnEntity(cart);
        return cart;
    }

    private static void forceChunks(ServerWorld world, boolean forced) {
        for (int cx = (X - 16) >> 4; cx <= (X + 140) >> 4; cx++) {
            for (int cz = (Z - 16) >> 4; cz <= (Z + 60) >> 4; cz++) world.setChunkForced(cx, cz, forced);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600, batchId = "fast_minecarts")
    public void minecartsGoFasterAndStillTakeEveryRail(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, X, Z) + 1;
        forceChunks(world, true);

        // A straight powered line, 130 long
        Track straight = new Track();
        for (int i = 0; i < 130; i++) rail(world, straight, new BlockPos(X + i, y, Z), powered(RailShape.EAST_WEST), true);
        settle(world, straight);

        // East 29, round a curve south, past a detector rail, up a slope, round a curve east, and east 30 more
        int z0 = Z + 20;
        Track bends = new Track();
        for (int i = 0; i < 29; i++) rail(world, bends, new BlockPos(X + i, y, z0), powered(RailShape.EAST_WEST), true);
        rail(world, bends, new BlockPos(X + 29, y, z0), Blocks.RAIL.getDefaultState().with(net.minecraft.block.RailBlock.SHAPE, RailShape.SOUTH_WEST), false);
        BlockPos detector = new BlockPos(X + 29, y, z0 + 10);
        for (int dz = 1; dz <= 14; dz++) {
            BlockPos at = new BlockPos(X + 29, y, z0 + dz);
            if (at.equals(detector)) rail(world, bends, at, Blocks.DETECTOR_RAIL.getDefaultState().with(DetectorRailBlock.SHAPE, RailShape.NORTH_SOUTH), false);
            else rail(world, bends, at, powered(RailShape.NORTH_SOUTH), true);
        }
        rail(world, bends, new BlockPos(X + 29, y, z0 + 15), powered(RailShape.ASCENDING_SOUTH), true);
        for (int dz = 16; dz <= 25; dz++) rail(world, bends, new BlockPos(X + 29, y + 1, z0 + dz), powered(RailShape.NORTH_SOUTH), true);
        rail(world, bends, new BlockPos(X + 29, y + 1, z0 + 26), Blocks.RAIL.getDefaultState().with(net.minecraft.block.RailBlock.SHAPE, RailShape.NORTH_EAST), false);
        for (int i = 30; i < 60; i++) rail(world, bends, new BlockPos(X + i, y + 1, z0 + 26), powered(RailShape.EAST_WEST), true);
        settle(world, bends);

        // Let the forced chunks start ticking first
        ctx.waitAndRun(5, () -> {
            setSpeed(world, 1);
            MinecartEntity slow = cart(world, new BlockPos(X, y, Z));
            ctx.waitAndRun(20, () -> {
                double slowDistance = slow.getX() - (X + 0.5);
                slow.discard();
                setSpeed(world, 10);
                MinecartEntity fast = cart(world, new BlockPos(X, y, Z));
                ctx.waitAndRun(20, () -> {
                    double fastDistance = fast.getX() - (X + 0.5);
                    boolean fastOnRail = AbstractRailBlock.isRail(world.getBlockState(fast.getBlockPos()));
                    fast.discard();
                    MinecartEntity round = cart(world, new BlockPos(X, y, z0));
                    boolean[] detected = {false};
                    double[] furthest = {0};
                    watch(ctx, world, round, detector, z0 + 26, detected, furthest, 120, () -> {
                        round.discard();
                        setSpeed(world, 1);
                        clear(world, straight);
                        clear(world, bends);
                        forceChunks(world, false);
                        net.ragnar.fmscraft.FmsCraft.LOGGER.info("Minecart test: {} blocks at speed 1, {} at speed 10 (on rail {}), detector {}, furthest x {} of {}",
                                slowDistance, fastDistance, fastOnRail, detected[0], furthest[0], X + 59);
                        ctx.assertTrue(slowDistance > 6 && slowDistance < 10, "at speed 1 a cart should do vanilla's 8 blocks a second, did " + slowDistance + " in 20 ticks");
                        ctx.assertTrue(fastDistance > 70 && fastDistance < 85, "at speed 10 a cart should do about 80 blocks a second, did " + fastDistance + " in 20 ticks");
                        ctx.assertTrue(fastOnRail, "the fast cart should still be on its rail");
                        ctx.assertTrue(detected[0], "the detector rail should notice a cart going by at speed 10");
                        ctx.assertTrue(furthest[0] >= X + 55, "the cart should take both curves and the slope at speed 10 and reach the end, got to x " + furthest[0]);
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** Follows the cart on the bendy track each tick: whether the detector rail went on, and how far along the last leg it got. */
    private static void watch(TestContext ctx, ServerWorld world, MinecartEntity cart, BlockPos detector, int lastLegZ,
                              boolean[] detected, double[] furthest, int ticksLeft, Runnable then) {
        if (world.getBlockState(detector).get(DetectorRailBlock.POWERED)) detected[0] = true;
        if (cart.getBlockZ() == lastLegZ && AbstractRailBlock.isRail(world.getBlockState(cart.getBlockPos()))) {
            furthest[0] = Math.max(furthest[0], cart.getX());
        }
        if (ticksLeft <= 0 || cart.isRemoved()) then.run();
        else ctx.waitAndRun(1, () -> watch(ctx, world, cart, detector, lastLegZ, detected, furthest, ticksLeft - 1, then));
    }

    private static void clear(ServerWorld world, Track track) {
        for (BlockPos pos : track.rails) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            world.setBlockState(pos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }
}
