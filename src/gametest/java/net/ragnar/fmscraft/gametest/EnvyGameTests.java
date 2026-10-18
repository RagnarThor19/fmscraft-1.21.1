package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.reward.WheelTier;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.entity.EnvyStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.envy.EnvyMazeLayout;
import net.ragnar.fmscraft.structure.envy.EnvyMazePiece;
import net.ragnar.fmscraft.structure.envy.EnvyMazeStructure;
import net.ragnar.fmscraft.structure.envy.EnvyMazes;
import net.ragnar.fmscraft.structure.pride.PrideTowerPiece;
import net.ragnar.fmscraft.structure.pride.PrideTowerShape;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.ChunkPos;

/**
 * The statue tests run in statue_corridor: a straight 3-unit corridor along x (unit centers at x = 7, 12, 17,
 * z = 7) with dead ends at both ends and a 1-wide tunnel going south off the middle unit. Test coordinates are one
 * block below the template's, so the template floor is at y = 1 and the walkway starts at y = 2.
 */
public class EnvyGameTests implements FabricGameTest {
    private static final String CORRIDOR = "fmscraft-gametest:statue_corridor";
    private static final int SLIDE = EnvyStatueEntity.SLIDE_TICKS;
    private static final int WALK_Y = 2;

    private static EnvyStatueEntity spawnStatue(TestContext ctx) {
        EnvyStatueEntity statue = ModEntities.ENVY_STATUE.create(ctx.getWorld());
        statue.place(ctx.getAbsolutePos(new BlockPos(7, WALK_Y, 7)), Direction.EAST);
        ctx.getWorld().spawnEntity(statue);
        return statue;
    }

    private static double relX(TestContext ctx, EnvyStatueEntity statue) {
        return statue.getX() - ctx.getAbsolutePos(BlockPos.ORIGIN).getX();
    }

    private static double relZ(TestContext ctx, EnvyStatueEntity statue) {
        return statue.getZ() - ctx.getAbsolutePos(BlockPos.ORIGIN).getZ();
    }

    /** The walkway center the statue last arrived at (it only changes when a slide finishes). */
    private static double anchorX(TestContext ctx, EnvyStatueEntity statue) {
        return statue.getAnchor().x - ctx.getAbsolutePos(BlockPos.ORIGIN).getX();
    }

    @GameTest(templateName = CORRIDOR, tickLimit = 100)
    public void statueSlidesFiveBlocksAtATimeDownTheMiddle(TestContext ctx) {
        EnvyStatueEntity statue = spawnStatue(ctx);
        ctx.runAtTick(SLIDE + 1, () -> {
            ctx.assertTrue(Math.abs(anchorX(ctx, statue) - 12.5) < 0.01, "should be one unit along, x=" + anchorX(ctx, statue));
            ctx.assertTrue(Math.abs(relZ(ctx, statue) - 7.5) < 0.01, "should stay in the middle, z=" + relZ(ctx, statue));
        });
        // Passing the 1-wide tunnel, it keeps going straight instead of trying to squeeze in
        ctx.runAtTick(2 * SLIDE + 1, () -> {
            ctx.assertTrue(Math.abs(anchorX(ctx, statue) - 17.5) < 0.01, "should reach the dead end, x=" + anchorX(ctx, statue));
            ctx.assertTrue(Math.abs(relZ(ctx, statue) - 7.5) < 0.01, "ignored the tunnel, z=" + relZ(ctx, statue));
            ctx.complete();
        });
    }

    @GameTest(templateName = CORRIDOR, tickLimit = 220)
    public void statueTurnsSlowlyAtADeadEndAndComesBack(TestContext ctx) {
        EnvyStatueEntity statue = spawnStatue(ctx);
        int turn = 2 * EnvyStatueEntity.TICKS_PER_QUARTER_TURN;
        // Halfway through the 180 degree turn it's still standing at the dead end
        ctx.runAtTick(2 * SLIDE + turn / 2, () -> {
            ctx.assertTrue(statue.getState() == EnvyStatueEntity.STATE_TURNING, "should be turning");
            ctx.assertTrue(Math.abs(relX(ctx, statue) - 17.5) < 0.01, "doesn't move while turning");
        });
        ctx.runAtTick(2 * SLIDE + turn + SLIDE + 2, () -> {
            ctx.assertTrue(statue.getFacing() == Direction.WEST, "should face back the way it came");
            ctx.assertTrue(Math.abs(anchorX(ctx, statue) - 12.5) < 0.01, "should slide back one unit, x=" + anchorX(ctx, statue));
            ctx.assertTrue(Math.abs(relZ(ctx, statue) - 7.5) < 0.01, "still in the middle");
            ctx.complete();
        });
    }

    @GameTest(templateName = CORRIDOR, tickLimit = 100)
    public void statueCrushesWhateverItHits(TestContext ctx) {
        ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new Vec3d(12.5, WALK_Y, 7.5));
        zombie.setAiDisabled(true);
        spawnStatue(ctx);
        ctx.runAtTick(SLIDE + 10, () -> {
            // Zombies have a little natural armor, so slightly less than the full damage lands
            ctx.assertTrue(zombie.isDead() || zombie.getHealth() <= zombie.getMaxHealth() - EnvyStatueEntity.DAMAGE * 0.9f,
                    "zombie should be crushed, health " + zombie.getHealth());
            ctx.complete();
        });
    }

    @GameTest(templateName = CORRIDOR, tickLimit = 100)
    public void statueSmashesBlocksPlayersPutInItsWay(TestContext ctx) {
        BlockPos dirt = new BlockPos(12, WALK_Y + 1, 7);
        BlockPos brick = new BlockPos(10, WALK_Y, 7); // a single maze-stone block isn't enough to stop it
        ctx.setBlockState(dirt, Blocks.DIRT);
        ctx.setBlockState(brick, Blocks.STONE_BRICKS);
        spawnStatue(ctx);
        ctx.runAtTick(SLIDE + 5, () -> {
            ctx.expectBlock(Blocks.AIR, dirt);
            ctx.expectBlock(Blocks.AIR, brick);
            ctx.complete();
        });
    }

    @GameTest(templateName = CORRIDOR, tickLimit = 100)
    public void statueIsUnharmedByAttacks(TestContext ctx) {
        EnvyStatueEntity statue = spawnStatue(ctx);
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        statue.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1000f);
        statue.damage(ctx.getWorld().getDamageSources().explosion(null, null), 1000f);
        ctx.runAtTick(5, () -> {
            ctx.assertTrue(statue.isAlive(), "statue can't be destroyed");
            ctx.complete();
        });
    }

    // ---- The lock ----

    private static BlockState frameAt(TestContext ctx, BlockPos pos, Sin sin) {
        ctx.setBlockState(pos, SinPortalFrameBlock.of(sin, Direction.SOUTH));
        return ctx.getWorld().getBlockState(ctx.getAbsolutePos(pos));
    }

    private static void useKey(TestContext ctx, ServerPlayerEntity player, BlockPos rel, Sin sin) {
        BlockPos pos = ctx.getAbsolutePos(rel);
        ItemStack key = new ItemStack(ModItems.KEYS.get(sin));
        ctx.getWorld().getBlockState(pos).onUseWithItem(key, ctx.getWorld(), player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.SOUTH, pos, false));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void sinKeyOpensThePortalBehindItsFrame(TestContext ctx) {
        BlockPos lock = new BlockPos(3, 1, 6);
        frameAt(ctx, lock, Sin.ENVY);
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();

        useKey(ctx, player, lock, Sin.GREED);
        ctx.assertTrue(BossFights.session(Sin.ENVY) == null, "the Greed key must not open the Envy portal");
        ctx.assertFalse(ctx.getWorld().getBlockState(ctx.getAbsolutePos(lock)).get(SinPortalFrameBlock.KEY), "a wrong key doesn't go in");

        useKey(ctx, player, lock, Sin.ENVY);
        ctx.assertTrue(BossFights.session(Sin.ENVY) != null, "the Envy key should open the fight");
        BlockPos portalCenter = lock.north(SinPortalFrameBlock.PORTAL_DISTANCE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) ctx.expectBlock(ModBlocks.SIN_PORTAL, portalCenter.east(dx).up(dy));
        }
        ctx.assertTrue(ctx.getWorld().getBlockState(ctx.getAbsolutePos(lock)).get(SinPortalFrameBlock.KEY), "the key should stand in the lock");

        BossFights.forceEnd(ctx.getWorld().getServer(), Sin.ENVY);
        ctx.runAtTick(30, () -> {
            ctx.assertFalse(ctx.getWorld().getBlockState(ctx.getAbsolutePos(lock)).get(SinPortalFrameBlock.KEY), "the key crumbles away after the fight");
            ctx.complete();
        });
    }

    // ---- Loot ----

    private static int tomes(TestContext ctx, net.minecraft.registry.RegistryKey<net.minecraft.loot.LootTable> table, WheelTier tier, int rolls) {
        ServerWorld world = ctx.getWorld();
        var loot = world.getServer().getReloadableRegistries().getLootTable(table);
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
    public void safeRoomChestHasRareTomes(TestContext ctx) {
        int advanced = tomes(ctx, EnvyMazePiece.SAFE_ROOM_LOOT, WheelTier.ADVANCED, 4000);
        int master = tomes(ctx, EnvyMazePiece.SAFE_ROOM_LOOT, WheelTier.MASTER, 4000);
        ctx.assertTrue(advanced >= 320 && advanced <= 480, "expected ~10% advanced tomes, got " + advanced + "/4000");
        ctx.assertTrue(master >= 50 && master <= 115, "expected ~2% master tomes, got " + master + "/4000");
        ctx.assertEquals(tomes(ctx, EnvyMazePiece.SAFE_ROOM_PLAIN_LOOT, WheelTier.ADVANCED, 500), 0, "the other half has no tomes");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void barrelsSometimesHaveTomes(TestContext ctx) {
        int beginner = tomes(ctx, EnvyMazePiece.BARREL_LOOT, WheelTier.BEGINNER, 6000);
        int advanced = tomes(ctx, EnvyMazePiece.BARREL_LOOT, WheelTier.ADVANCED, 6000);
        ctx.assertTrue(beginner >= 125 && beginner <= 240, "expected ~3% beginner tomes, got " + beginner + "/6000");
        ctx.assertTrue(advanced >= 30 && advanced <= 95, "expected ~1% advanced tomes, got " + advanced + "/6000");
        ctx.assertEquals(tomes(ctx, EnvyMazePiece.BARREL_LOOT, WheelTier.MASTER, 2000), 0, "no master tomes in barrels");
        ctx.complete();
    }

    // ---- World generation ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void envyMazeGeneratesSealedWithItsSafeRoom(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int origin = 8192; // far from the other tests, on a chunk corner
        long seed = 20261013L;
        EnvyMazePiece piece = buildMaze(world, origin, seed);
        EnvyMazeLayout layout = piece.layout();

        int c = EnvyMazeLayout.CENTER * EnvyMazePiece.UNIT + 2;
        BlockPos lock = new BlockPos(origin + c, EnvyMazePiece.FLOOR_Y + 1, origin + c);
        BlockState lockState = world.getBlockState(lock);
        ctx.assertTrue(lockState.isOf(ModBlocks.SIN_PORTAL_FRAME) && lockState.get(SinPortalFrameBlock.SIN) == Sin.ENVY,
                "the Envy lock should be in the middle of the safe room, got " + lockState);

        // Sealed above and below
        for (int y : new int[]{EnvyMazePiece.FLOOR_Y - 1, EnvyMazePiece.FLOOR_Y - 2,
                EnvyMazePiece.FLOOR_Y + EnvyMazePiece.HEIGHT, EnvyMazePiece.FLOOR_Y + EnvyMazePiece.HEIGHT + 1}) {
            ctx.assertTrue(world.getBlockState(new BlockPos(origin + 12, y, origin + 12)).isOf(Blocks.DEEPSLATE_TILES), "shell at y " + y);
        }

        // The shrine leaves an empty 3x3 behind the lock for the portal, and the doorway lines up with the tunnel
        Direction facing = EnvyMazePiece.direction(layout.doorDir);
        Direction lateral = BossFights.along(facing.rotateYClockwise().getAxis());
        BlockPos frame = lock.offset(facing.getOpposite(), SinPortalFrameBlock.PORTAL_DISTANCE).down();
        for (int l = -1; l <= 1; l++) {
            for (int h = 1; h <= 3; h++) ctx.assertTrue(world.getBlockState(frame.offset(lateral, l).up(h)).isAir(), "portal space is empty until the key is used");
        }
        for (int step = 8; step <= 10; step++) {
            BlockPos tunnel = lock.offset(facing, step);
            ctx.assertTrue(world.getBlockState(tunnel).isAir() && world.getBlockState(tunnel.up()).isAir(), "doorway into the tunnel at " + step);
        }

        // The containers from the template, with loot: exactly one half of one double chest holds the tomes
        int barrels = 0, chestHalves = 0, tomeChests = 0;
        for (BlockPos pos : BlockPos.iterate(lock.add(-7, 0, -7), lock.add(7, 3, 7))) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.BARREL)) barrels++;
            if (state.isOf(Blocks.CHEST)) chestHalves++;
            if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity container) {
                ctx.assertTrue(container.getLootTable() != null, "every container in the safe room has loot, not at " + pos);
                if (EnvyMazePiece.SAFE_ROOM_LOOT.equals(container.getLootTable())) tomeChests++;
            }
        }
        ctx.assertEquals(barrels, 7, "barrels in the safe room");
        ctx.assertEquals(chestHalves, 5, "two double chests and a single chest in the safe room");
        ctx.assertEquals(tomeChests, 1, "one tome chest");

        int chests = 0;
        for (BlockPos pos : BlockPos.iterate(origin, EnvyMazePiece.FLOOR_Y, origin,
                origin + EnvyMazePiece.SIZE - 1, EnvyMazePiece.FLOOR_Y + EnvyMazePiece.HEIGHT - 1, origin + EnvyMazePiece.SIZE - 1)) {
            if (world.getBlockEntity(pos) instanceof ChestBlockEntity) chests++;
        }
        ctx.assertTrue(chests >= 6, "dead-end chests plus the double chest, found " + chests);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void everyMazeAlwaysHasFourStatues(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int origin = -8192;
        long seed = 99L;
        EnvyMazePiece piece = buildMaze(world, origin, seed);
        Box maze = new Box(origin, EnvyMazePiece.FLOOR_Y, origin, origin + EnvyMazePiece.SIZE, EnvyMazePiece.FLOOR_Y + EnvyMazePiece.HEIGHT, origin + EnvyMazePiece.SIZE);
        forceLoad(world, piece, true); // keep the statues loaded while we watch them

        EnvyMazes.ensureStatues(world, piece.origin(), seed);
        var statues = world.getEntitiesByClass(EnvyStatueEntity.class, maze, e -> true);
        ctx.assertEquals(statues.size(), EnvyMazes.STATUES, "statues after the first visit");
        java.util.Set<BlockPos> corners = new java.util.HashSet<>();
        for (EnvyMazeLayout.Spot spot : piece.layout().statues) corners.add(piece.unitCenter(spot.x(), spot.z()));
        java.util.Set<BlockPos> used = new java.util.HashSet<>();
        for (EnvyStatueEntity statue : statues) {
            BlockPos at = statue.getBlockPos();
            for (int h = 0; h <= 3; h++) ctx.assertTrue(world.getBlockState(at.up(h)).isAir(), "statue should start in a clear walkway");
            ctx.assertTrue(corners.contains(at), "statue should start in a corner, at " + at);
            used.add(at);
        }
        ctx.assertEquals(used.size(), EnvyMazes.STATUES, "one statue per corner");

        // Dig a wall next to a statue right out (as a mineshaft or a player might): it still won't go that way
        EnvyStatueEntity statue = statues.get(0);
        for (Direction dir : Direction.Type.HORIZONTAL) {
            if (statue.canEnter(dir)) continue;
            BlockPos center = BlockPos.ofFloored(statue.getAnchor());
            for (BlockPos pos : BlockPos.iterate(center.offset(dir, 3).offset(dir.rotateYClockwise(), -2),
                    center.offset(dir, 7).offset(dir.rotateYClockwise(), 2).up(4))) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
            ctx.assertFalse(statue.canEnter(dir), "a statue must never leave its maze through a broken wall");
            break;
        }

        // One is destroyed: the maze gets a replacement
        statues.get(1).kill();
        EnvyMazes.ensureStatues(world, piece.origin(), seed);
        ctx.assertEquals(world.getEntitiesByClass(EnvyStatueEntity.class, maze, e -> e.isAlive()).size(), EnvyMazes.STATUES, "statues after one was destroyed");

        // A fifth one turning up (say, from an old save) crumbles away when it checks in
        EnvyStatueEntity extra = ModEntities.ENVY_STATUE.create(world);
        extra.place(piece.unitCenter(3, 1), Direction.SOUTH, piece.origin(), seed);
        world.spawnEntity(extra);
        ctx.runAtTick(5, () -> {
            ctx.assertTrue(extra.isRemoved() && extra.getRemovalReason() == Entity.RemovalReason.DISCARDED, "a fifth statue should crumble: removed=" + extra.isRemoved() + " reason=" + extra.getRemovalReason() + " age=" + extra.age
                    + " slots=" + net.ragnar.fmscraft.world.FmsWorldState.get(world.getServer()).envyStatues(piece.origin().asLong()).size()
                    + " alive=" + world.getEntitiesByClass(EnvyStatueEntity.class, maze, e -> e.isAlive()).size());
            ctx.assertEquals(world.getEntitiesByClass(EnvyStatueEntity.class, maze, e -> e.isAlive()).size(), EnvyMazes.STATUES, "still four");
            for (EnvyStatueEntity e : world.getEntitiesByClass(EnvyStatueEntity.class, maze, e -> true)) e.discard();
            forceLoad(world, piece, false);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void statuesWaitUntilAPlayerComesNear(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int origin = -16384; // far from the other tests (and their players)
        long seed = 5L;
        EnvyMazePiece piece = buildMaze(world, origin, seed);
        Box maze = new Box(origin, EnvyMazePiece.FLOOR_Y, origin, origin + EnvyMazePiece.SIZE, EnvyMazePiece.FLOOR_Y + EnvyMazePiece.HEIGHT, origin + EnvyMazePiece.SIZE);
        forceLoad(world, piece, true);
        EnvyMazes.ensureStatues(world, piece.origin(), seed);
        var statues = world.getEntitiesByClass(EnvyStatueEntity.class, maze, e -> true);
        java.util.Map<EnvyStatueEntity, Vec3d> start = new java.util.HashMap<>();
        for (EnvyStatueEntity statue : statues) start.put(statue, statue.getPos());

        ctx.runAtTick(40, () -> {
            for (EnvyStatueEntity statue : statues) {
                ctx.assertFalse(statue.isAwake(), "statue should be asleep with nobody near");
                ctx.assertTrue(statue.getPos().squaredDistanceTo(start.get(statue)) < 0.01, "statue moved with nobody near");
            }
            // A player turns up on the surface, 40 blocks off the maze's west side
            ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
            player.refreshPositionAndAngles(origin - 40, 70, origin + 60, 0, 0);
        });
        ctx.runAtTick(100, () -> {
            for (EnvyStatueEntity statue : statues) {
                ctx.assertTrue(statue.isAwake(), "statue should wake with a player within 50 blocks");
                ctx.assertTrue(statue.getPos().squaredDistanceTo(start.get(statue)) > 1, "statue should have started roaming");
            }
            for (EnvyStatueEntity statue : statues) statue.discard();
            forceLoad(world, piece, false);
            ctx.complete();
        });
    }

    /** The game only builds a structure in chunks up to 8 from its start chunk: anything further out would be missing. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sinStructuresFitWithinReachOfTheirStartChunk(TestContext ctx) {
        for (ChunkPos start : new ChunkPos[]{new ChunkPos(0, 0), new ChunkPos(-37, 112), new ChunkPos(500, -500)}) {
            BlockPos origin = EnvyMazeStructure.origin(start);
            BlockBox maze = new EnvyMazePiece(1L, origin).getBoundingBox();
            BlockBox tower = new PrideTowerPiece(1L, new BlockPos(start.getCenterX(), 70, start.getCenterZ()), PrideTowerShape.FULL_HEIGHT).getBoundingBox();
            BlockBox garden = new net.ragnar.fmscraft.structure.lust.LustGardenPiece(1L, new BlockPos(start.getCenterX(), 70, start.getCenterZ())).getBoundingBox();
            for (BlockBox box : new BlockBox[]{maze, tower, garden}) {
                int reach = Math.max(Math.max(Math.abs((box.getMinX() >> 4) - start.x), Math.abs((box.getMaxX() >> 4) - start.x)),
                        Math.max(Math.abs((box.getMinZ() >> 4) - start.z), Math.abs((box.getMaxZ() >> 4) - start.z)));
                ctx.assertTrue(reach <= 8, "a structure reaches " + reach + " chunks from its start " + start);
            }
        }
        ctx.complete();
    }

    private static void forceLoad(ServerWorld world, EnvyMazePiece piece, boolean forced) {
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) world.setChunkForced(cx, cz, forced);
        }
    }

    /** Generates a whole maze from a known seed, chunk by chunk, the same way world generation does. */
    private static EnvyMazePiece buildMaze(ServerWorld world, int origin, long seed) {
        EnvyMazePiece piece = new EnvyMazePiece(seed, new BlockPos(origin, EnvyMazePiece.FLOOR_Y, origin));
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
        return piece;
    }
}
