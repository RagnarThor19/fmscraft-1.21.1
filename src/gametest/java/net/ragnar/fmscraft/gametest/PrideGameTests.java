package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.pride.PrideTowerPiece;
import net.ragnar.fmscraft.structure.pride.PrideTowerShape;

public class PrideGameTests implements FabricGameTest {

    // ---- Loot ----

    private static int tomes(TestContext ctx, WheelTier tier, int rolls) {
        ServerWorld world = ctx.getWorld();
        var loot = world.getServer().getReloadableRegistries().getLootTable(PrideTowerPiece.RAMP_LOOT);
        int[] found = {0};
        for (int i = 0; i < rolls; i++) {
            loot.generateLoot(new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .build(LootContextTypes.CHEST), stack -> {
                if (MagicTomes.of(tier).contains(stack.getItem())) found[0]++;
            });
        }
        return found[0];
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rampChestsSometimesHaveTomes(TestContext ctx) {
        int beginner = tomes(ctx, WheelTier.BEGINNER, 4000);
        int advanced = tomes(ctx, WheelTier.ADVANCED, 4000);
        ctx.assertTrue(beginner >= 140 && beginner <= 260, "expected ~5% beginner tomes, got " + beginner + "/4000");
        ctx.assertTrue(advanced >= 140 && advanced <= 260, "expected ~5% advanced tomes, got " + advanced + "/4000");
        ctx.complete();
    }

    // ---- The tower ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void towerOfPrideRisesFromTheGroundToItsLock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int at = 24576; // far from the other tests
        world.getChunk(at >> 4, at >> 4);
        int ground = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, at, at);
        PrideTowerPiece piece = new PrideTowerPiece(42L, new BlockPos(at, ground, at), PrideTowerShape.FULL_HEIGHT);
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                world.getChunk(cx, cz);
                ChunkPos chunk = new ChunkPos(cx, cz);
                BlockBox chunkBox = new BlockBox(chunk.getStartX(), box.getMinY(), chunk.getStartZ(), chunk.getEndX(), box.getMaxY(), chunk.getEndZ());
                piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), world.getRandom(),
                        chunkBox, chunk, BlockPos.ORIGIN);
            }
        }
        BlockPos foot = piece.foot();
        PrideTowerShape shape = piece.shape();

        // The Pride lock stands in the middle of the summit, before its gold-cornered frame
        BlockPos lock = piece.lock();
        BlockState lockState = world.getBlockState(lock);
        ctx.assertTrue(lockState.isOf(ModBlocks.SIN_PORTAL_FRAME) && lockState.get(SinPortalFrameBlock.SIN) == Sin.PRIDE,
                "the Pride lock should be on the summit at " + lock + ", got " + lockState);
        BlockPos frame = lock.offset(lockState.get(SinPortalFrameBlock.FACING).getOpposite(), SinPortalFrameBlock.PORTAL_DISTANCE).down();
        ctx.assertTrue(world.getBlockState(frame.east(2)).isOf(Blocks.GOLD_BLOCK), "gold corner of the portal frame");
        for (int l = -1; l <= 1; l++) {
            for (int h = 1; h <= 3; h++) ctx.assertTrue(world.getBlockState(frame.east(l).up(h)).isAir(), "the portal opening should be clear");
        }

        // Stone at the foot, quartz near the top
        BlockState low = world.getBlockState(foot.add(-(PrideTowerShape.RADIUS - 6), 3, 0));
        ctx.assertTrue(low.isOf(Blocks.STONE), "plain stone inside the foot of the tower, got " + low);
        ctx.assertTrue(world.getBlockState(foot.up(piece.height() - 1)).isOf(Blocks.CHISELED_QUARTZ_BLOCK), "quartz summit floor");

        // Every walkway column has a floor under it and headroom over it, all the way up
        int checked = 0;
        for (int dx = -PrideTowerShape.RADIUS; dx <= PrideTowerShape.RADIUS; dx += 3) {
            for (int dz = -PrideTowerShape.RADIUS; dz <= PrideTowerShape.RADIUS; dz += 3) {
                PrideTowerShape.Column column = shape.column(dx, dz);
                if (column.kind() != PrideTowerShape.Kind.RAMP) continue;
                BlockPos surface = foot.add(dx, column.fullBlocks(), dz);
                ctx.assertTrue(!world.getBlockState(surface.down()).isAir(), "walkway floor missing at " + surface.down());
                for (int h = column.slab() ? 1 : 0; h <= 2; h++) {
                    BlockState over = world.getBlockState(surface.up(h));
                    ctx.assertTrue(over.getCollisionShape(world, surface.up(h)).isEmpty() || over.isOf(Blocks.CHEST),
                            "no headroom over the walkway at " + surface.up(h) + ": " + over);
                }
                checked++;
            }
        }
        ctx.assertTrue(checked > 300, "checked " + checked + " walkway columns");

        // A chest on every turn, plus two by the lock
        int chests = 0;
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                for (var entity : world.getChunk(cx, cz).getBlockEntities().values()) if (entity instanceof ChestBlockEntity) chests++;
            }
        }

        // Each ramp chest stands with its back right against the parapet
        for (int m = 0; m < PrideTowerShape.TURNS; m++) {
            int[] spot = piece.rampChest(m);
            ctx.assertTrue(spot != null, "turn " + m + " should have a chest");
            BlockPos column = foot.add(spot[0], 0, spot[1]);
            BlockPos chest = null;
            for (int y = 0; y <= piece.height() && chest == null; y++) {
                if (world.getBlockState(column.up(y)).isOf(Blocks.CHEST)) chest = column.up(y);
            }
            ctx.assertTrue(chest != null, "no chest on turn " + m);
            ctx.assertTrue(world.getBlockState(chest.west()).isOpaque(), "chest on turn " + m + " isn't against the parapet: " + world.getBlockState(chest.west()));
        }
        ctx.assertEquals(chests, PrideTowerShape.TURNS + 2, "chests on the ramp and summit");
        ctx.complete();
    }

}
