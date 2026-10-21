package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.LustStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.lust.LustGardenPiece;
import net.ragnar.fmscraft.structure.lust.LustGardenPlan;
import net.ragnar.fmscraft.structure.lust.LustGardens;

import java.util.List;
import java.util.UUID;

/**
 * Garden tests grow a whole Lust's Garden far from the others and register it as a structure start the way world
 * generation does, so the garden's logic finds it like a generated one. Statue tests stand one on a stone floor
 * high in the air, away from everything.
 */
public class LustGameTests implements FabricGameTest {

    private static LustGardenPiece grow(TestContext ctx, int at, long seed) {
        ServerWorld world = ctx.getWorld();
        world.getChunk(at >> 4, at >> 4);
        // The test world is a thin superflat: lift the garden clear of its floor so the crypt fits under it
        int ground = world.getTopY(Heightmap.Type.WORLD_SURFACE, at, at);
        BlockPos origin = new BlockPos(at, Math.max(ground + 1, world.getBottomY() + LustGardenPiece.FOUNDATION + 20), at);
        LustGardenPiece piece = new LustGardenPiece(seed, origin);
        Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(LustGardens.GARDEN);
        ChunkPos home = new ChunkPos(origin);
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                world.setChunkForced(cx, cz, true);
                Chunk chunk = world.getChunk(cx, cz);
                ChunkPos pos = new ChunkPos(cx, cz);
                BlockBox chunkBox = new BlockBox(pos.getStartX(), box.getMinY(), pos.getStartZ(), pos.getEndX(), box.getMaxY(), pos.getEndZ());
                piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), world.getRandom(),
                        chunkBox, pos, BlockPos.ORIGIN);
                chunk.addStructureReference(structure, home.toLong());
            }
        }
        world.getChunk(home.x, home.z).setStructureStart(structure, new StructureStart(structure, home, 0, new StructurePiecesList(List.of(piece))));
        return piece;
    }

    private static void unload(ServerWorld world, LustGardenPiece piece) {
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) world.setChunkForced(cx, cz, false);
        }
    }

    /** A real (connected) server player, survival or creative. */
    private static ServerPlayerEntity player(ServerWorld world, BlockPos at, boolean creative) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), creative ? "test-gardener" : "test-lover");
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.teleport(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return player;
    }

    private static void leave(ServerPlayerEntity player) {
        player.getServer().getPlayerManager().remove(player);
    }

    /** Turns the player to look at a point (or anywhere, with yaw and pitch). */
    private static void look(ServerPlayerEntity player, float yaw, float pitch) {
        player.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        player.setHeadYaw(yaw);
    }

    private static void lookAt(ServerPlayerEntity player, Vec3d target) {
        Vec3d d = target.subtract(player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        look(player, yaw, pitch);
    }

    // ---- The garden ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void gardenGrowsRoundItsFountainWithTheCryptBeneath(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        LustGardenPiece piece = grow(ctx, 65536, 5L);
        LustGardenPlan plan = piece.plan();

        // The Lust lock in the crypt, facing the stair, before an empty 3x3 frame
        BlockState lock = world.getBlockState(piece.lock());
        ctx.assertTrue(lock.isOf(ModBlocks.SIN_PORTAL_FRAME) && lock.get(SinPortalFrameBlock.SIN) == Sin.LUST, "the Lust lock, got " + lock);
        ctx.assertEquals(lock.get(SinPortalFrameBlock.FACING), Direction.SOUTH, "lock facing the stair");
        BlockPos portal = piece.lock().north(SinPortalFrameBlock.PORTAL_DISTANCE);
        for (int l = -1; l <= 1; l++) {
            for (int h = 0; h <= 2; h++) ctx.assertTrue(world.getBlockState(portal.east(l).up(h)).isAir(), "the portal opening should be clear at " + portal.east(l).up(h));
            ctx.assertFalse(world.getBlockState(portal.east(l).down()).isAir(), "the frame has a floor");
            ctx.assertFalse(world.getBlockState(portal.east(l).up(3)).isAir(), "the frame has a top");
        }
        for (int s : new int[]{-2, 2}) for (int h = 0; h <= 2; h++) ctx.assertFalse(world.getBlockState(portal.east(s).up(h)).isAir(), "frame sides");

        // The fountain: water in the basin and at the spouts, the floor over the stair shut, the stair under it
        for (BlockPos pos : piece.basinWater()) ctx.assertTrue(world.getBlockState(pos).isOf(Blocks.WATER), "basin water at " + pos);
        for (BlockPos pos : piece.spouts()) ctx.assertTrue(world.getFluidState(pos).isStill(), "a spout at " + pos);
        for (BlockPos pos : piece.capBlocks()) ctx.assertFalse(world.getBlockState(pos).isAir(), "the basin floor over the stair at " + pos);
        for (int z = LustGardenPlan.CAP_Z0; z > LustGardenPlan.CRYPT_NEAR; z--) {
            BlockPos step = piece.toWorld(0, LustGardenPlan.stepY(z), z);
            ctx.assertTrue(world.getBlockState(step).isOf(Blocks.TUFF_BRICK_STAIRS), "a step at " + step);
            ctx.assertTrue(world.getBlockState(step.up()).isAir() || piece.capBlocks().contains(step.up()), "room over the step at " + step);
        }
        ctx.assertTrue(world.getBlockState(piece.toWorld(0, -11, -20)).isAir() || world.getBlockState(piece.toWorld(0, -11, -20)).isOf(Blocks.PINK_CARPET),
                "the crypt is hollow");

        // Four empty plinths round it, candles unlit
        for (int i = 0; i < 4; i++) {
            BlockPos top = piece.fountainHomes().get(i);
            ctx.assertTrue(world.getBlockState(top.down()).isOf(Blocks.CHISELED_TUFF_BRICKS) && world.getBlockState(top).isAir(), "an empty plinth at " + top);
            for (BlockPos candle : piece.fountainCandles(i)) {
                BlockState state = world.getBlockState(candle);
                ctx.assertTrue(state.isOf(Blocks.PINK_CANDLE) && !state.get(CandleBlock.LIT), "an unlit candle at " + candle + ", got " + state);
            }
        }

        // A plinth for every statue, with room on top
        ctx.assertTrue(piece.statueHomes().size() >= 20, "statues: " + piece.statueHomes().size());
        for (BlockPos home : piece.statueHomes()) {
            ctx.assertTrue(world.getBlockState(home.down()).isOf(Blocks.CHISELED_TUFF_BRICKS), "a chiseled tuff plinth under " + home);
            ctx.assertTrue(world.getBlockState(home).isAir() && world.getBlockState(home.up()).isAir() && world.getBlockState(home.up(2)).getCollisionShape(world, home.up(2)).isEmpty(),
                    "room for a statue on " + home);
        }

        // The hedge: persistent azalea leaves, at least 12 high all round (gate aside)
        int checked = 0;
        for (int deg = 0; deg < 360; deg += 15) {
            double a = Math.toRadians(deg), r = (plan.inner(a) + plan.outer(a)) / 2;
            int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
            if (plan.ground(x, z) != LustGardenPlan.Ground.HEDGE) continue;
            for (int y = 0; y < LustGardenPlan.MIN_HEDGE_HEIGHT - 2; y++) {
                BlockState leaf = world.getBlockState(piece.toWorld(x, y, z));
                ctx.assertTrue((leaf.isOf(Blocks.AZALEA_LEAVES) || leaf.isOf(Blocks.FLOWERING_AZALEA_LEAVES)) && leaf.get(LeavesBlock.PERSISTENT),
                        "hedge leaves at " + piece.toWorld(x, y, z) + ", got " + leaf);
            }
            checked++;
        }
        ctx.assertTrue(checked > 15, "checked the hedge in " + checked + " places");

        // Barrels with the garden's loot, chests by the lock with the crypt's
        ctx.assertTrue(piece.barrels().size() >= 15, "barrels: " + piece.barrels().size());
        for (BlockPos pos : piece.barrels()) {
            ctx.assertTrue(world.getBlockEntity(pos) instanceof LootableContainerBlockEntity barrel && LustGardenPiece.BARREL_LOOT.equals(barrel.getLootTable()),
                    "barrel loot at " + pos);
        }
        for (BlockPos pos : piece.chests()) {
            ctx.assertTrue(world.getBlockEntity(pos) instanceof LootableContainerBlockEntity chest && LustGardenPiece.CRYPT_LOOT.equals(chest.getLootTable()),
                    "crypt chest loot at " + pos);
        }
        ctx.assertEquals(piece.chests().size(), 2, "two chests by the lock");
        unload(world, piece);
        ctx.complete();
    }

    private static int tomes(TestContext ctx, int rolls) {
        ServerWorld world = ctx.getWorld();
        var loot = world.getServer().getReloadableRegistries().getLootTable(LustGardenPiece.BARREL_LOOT);
        int[] found = {0};
        for (int i = 0; i < rolls; i++) {
            loot.generateLoot(new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .build(LootContextTypes.CHEST), stack -> {
                if (MagicTomes.of(WheelTier.BEGINNER).contains(stack.getItem()) || MagicTomes.of(WheelTier.ADVANCED).contains(stack.getItem())) found[0]++;
            });
        }
        return found[0];
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gardenBarrelsRarelyHoldATome(TestContext ctx) {
        int found = tomes(ctx, 5000);
        ctx.assertTrue(found >= 60 && found <= 145, "expected ~2% tomes (Beginner or Advanced), got " + found + "/5000");
        ctx.complete();
    }

    /**
     * Where gardens may grow, asked of the real twilight generator. (The test world doesn't generate structures, so
     * this asks the garden's placement directly, the way world generation would, for a run of placement regions.)
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void gardensGrowOnTheTwilightPlains(TestContext ctx) {
        ServerWorld twilight = ctx.getWorld().getServer().getWorld(Sin.SinWorlds.TWILIGHT);
        ctx.assertTrue(twilight != null, "the twilight dimension is loaded");
        var structures = twilight.getRegistryManager().get(RegistryKeys.STRUCTURE);
        Structure garden = structures.get(LustGardens.GARDEN);
        var set = twilight.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(net.minecraft.util.Identifier.of("fmscraft", "lusts_garden"));
        var spread = (net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement) set.placement();
        var generator = twilight.getChunkManager().getChunkGenerator();
        int tried = 0, grown = 0;
        for (int rx = -4; rx < 4; rx++) {
            for (int rz = -4; rz < 4; rz++) {
                ChunkPos start = spread.getStartChunk(twilight.getSeed(), rx * spread.getSpacing(), rz * spread.getSpacing());
                var context = new Structure.Context(twilight.getRegistryManager(), generator, generator.getBiomeSource(),
                        twilight.getChunkManager().getNoiseConfig(), twilight.getStructureTemplateManager(), twilight.getSeed(), start, twilight,
                        garden.getValidBiomes()::contains);
                var position = garden.getValidStructurePosition(context);
                tried++;
                if (position.isEmpty()) continue;
                grown++;
                BlockPos at = position.get().position();
                ctx.assertTrue(at.getY() > 50 && at.getY() < 90, "a garden on the twilight's ground, at " + at);
            }
        }
        net.ragnar.fmscraft.FmsCraft.LOGGER.info("Lust's Garden would grow in {} of {} twilight placement regions", grown, tried);
        ctx.assertTrue(grown >= tried / 2, "gardens grow in most regions of the twilight's plains: " + grown + "/" + tried);
        ctx.complete();
    }

    // ---- The statues ----

    /** A long stone floor high in the air, with a statue standing at its west end, facing east. */
    private static LustStatueEntity standStatue(TestContext ctx, BlockPos at) {
        ServerWorld world = ctx.getWorld();
        forceChunks(world, at, true);
        for (int x = -4; x <= 30; x++) {
            for (int z = -4; z <= 4; z++) {
                world.setBlockState(at.add(x, -1, z), Blocks.STONE.getDefaultState());
                for (int y = 0; y <= 3; y++) world.setBlockState(at.add(x, y, z), Blocks.AIR.getDefaultState());
            }
        }
        LustStatueEntity statue = ModEntities.LUST_STATUE.create(world);
        statue.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        statue.setHeadYaw(-90);
        statue.setBodyYaw(-90);
        world.spawnEntity(statue);
        return statue;
    }

    /** Keeps the floor's chunks ticking (statues only move in ticking chunks). */
    private static void forceChunks(ServerWorld world, BlockPos at, boolean force) {
        for (int x = -8; x <= 40; x += 16) {
            for (int z = -8; z <= 8; z += 16) world.setChunkForced((at.getX() + x) >> 4, (at.getZ() + z) >> 4, force);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void aStatueOnlyMovesWhileNobodyLooks(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos at = new BlockPos(98304, 200, 98304);
        world.getChunk(at);
        LustStatueEntity statue = standStatue(ctx, at);
        ServerPlayerEntity player = player(world, at.east(20), false);
        double[] mark = new double[1];
        ctx.runAtTick(5, () -> {
            statue.wake();
            lookAt(player, statue.getEyePos());
            mark[0] = statue.getX();
        });
        ctx.runAtTick(45, () -> {
            ctx.assertTrue(statue.eyesOpen(), "awake, eyes open");
            ctx.assertTrue(statue.isObserved(), "seen while looked at");
            ctx.assertTrue(Math.abs(statue.getX() - mark[0]) < 0.01, "not a step while watched, moved " + (statue.getX() - mark[0]));
            look(player, -90, 0); // turn round, back to it
        });
        ctx.runAtTick(85, () -> {
            ctx.assertFalse(statue.isObserved(), "unseen once nobody looks");
            ctx.assertTrue(statue.getX() - mark[0] > 3, "it crept closer while nobody looked, only " + (statue.getX() - mark[0]));
            lookAt(player, statue.getEyePos());
        });
        ctx.runAtTick(88, () -> mark[0] = statue.getX());
        ctx.runAtTick(120, () -> {
            ctx.assertTrue(Math.abs(statue.getX() - mark[0]) < 0.01, "stone again the moment it's seen");
            // Nor does it move when anyone at all is looking, even if its prey isn't
            ServerPlayerEntity friend = player(world, at.east(26), true);
            lookAt(friend, statue.getEyePos());
            look(player, -90, 0);
        });
        ctx.runAtTick(125, () -> mark[0] = statue.getX());
        ctx.runAtTick(160, () -> {
            ctx.assertTrue(Math.abs(statue.getX() - mark[0]) < 0.01, "a friend watching holds it still");
            statue.discard();
            for (ServerPlayerEntity p : List.copyOf(world.getPlayers())) {
                if (p.getBlockPos().isWithinDistance(at, 60)) leave(p);
            }
            forceChunks(world, at, false);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void onlyLookingAStatueInTheFaceOpensItsEyes(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos at = new BlockPos(102400, 200, 102400);
        world.getChunk(at);
        LustStatueEntity statue = standStatue(ctx, at.east(10));
        // Behind it (it faces east), staring at the back of its head
        ServerPlayerEntity player = player(world, at.east(4), false);
        ctx.runAtTick(5, () -> lookAt(player, statue.getEyePos()));
        ctx.runAtTick(30, () -> {
            ctx.assertFalse(statue.eyesOpen(), "the back of its head doesn't count");
            // In front of it, but looking at its feet
            player.teleport(world, at.getX() + 18.5, at.getY(), at.getZ() + 0.5, 0, 0);
            lookAt(player, statue.getPos());
        });
        ctx.runAtTick(55, () -> {
            ctx.assertFalse(statue.eyesOpen(), "looking at its feet doesn't count");
            ctx.assertTrue(Math.abs(statue.getX() - (at.getX() + 10.5)) < 0.01, "asleep, it hasn't moved");
            lookAt(player, statue.getEyePos());
        });
        ctx.runAtTick(65, () -> {
            ctx.assertTrue(statue.eyesOpen() && statue.isAwake(), "looked in the face, its eyes open");
            statue.discard();
            leave(player);
            forceChunks(world, at.east(10), false);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itemBarrierLetsOnlyFoodBeUsed(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        ServerPlayerEntity player = player(world, new BlockPos(106496, 200, 106496), false);
        player.addStatusEffect(new StatusEffectInstance(ModEffects.ITEM_BARRIER, 200, 0));
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL, 4);
        player.setStackInHand(Hand.MAIN_HAND, pearl);
        ctx.assertEquals(pearl.use(world, player, Hand.MAIN_HAND).getResult(), ActionResult.FAIL, "no throwing pearls");
        ctx.assertEquals(pearl.getCount(), 4, "the pearl isn't used up");
        ItemStack bow = new ItemStack(Items.BOW);
        ctx.assertEquals(bow.use(world, player, Hand.MAIN_HAND).getResult(), ActionResult.FAIL, "no bows");
        ItemStack bread = new ItemStack(Items.BREAD);
        player.setStackInHand(Hand.MAIN_HAND, bread);
        player.getHungerManager().setFoodLevel(10);
        ctx.assertTrue(bread.use(world, player, Hand.MAIN_HAND).getResult() != ActionResult.FAIL, "food is fine");
        player.removeStatusEffect(ModEffects.ITEM_BARRIER);
        ctx.assertFalse(ModEffects.blocksUsing(player, pearl), "without the effect, anything goes");
        leave(player);
        ctx.complete();
    }

    // ---- The challenge ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 800)
    public void fourStatuesBroughtHomeOpenTheWayDown(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        LustGardenPiece piece = grow(ctx, 81920, 11L);
        ServerPlayerEntity lover = player(world, piece.toWorld(13, 0, 0), false);
        look(lover, 0, -90);
        LustGardens.Garden garden = LustGardens.garden(world, piece);
        ServerPlayerEntity[] gardener = new ServerPlayerEntity[1];

        ctx.runAtTick(40, () -> {
            // The garden weighs on whoever's inside
            StatusEffectInstance slowness = lover.getStatusEffect(StatusEffects.SLOWNESS);
            ctx.assertTrue(slowness != null && slowness.getAmplifier() == LustGardens.SLOWNESS, "Slowness II inside");
            ctx.assertTrue(lover.hasStatusEffect(StatusEffects.MINING_FATIGUE), "Mining Fatigue inside");
            ctx.assertTrue(lover.hasStatusEffect(ModEffects.BLOCK_BARRIER), "Block Barrier inside");
            ctx.assertTrue(lover.hasStatusEffect(ModEffects.ITEM_BARRIER), "Item Barrier inside");
            ctx.assertTrue(LustGardens.inGarden(lover), "and sees the garden's fog");
            List<LustStatueEntity> statues = LustGardens.statues(garden);
            ctx.assertEquals(statues.size(), piece.statueHomes().size(), "a statue on every plinth");
            for (LustStatueEntity statue : statues) ctx.assertFalse(statue.eyesOpen(), "all asleep");

            // From here a creative gardener watches the sky while four statues are brought home
            leave(lover);
            gardener[0] = player(world, piece.toWorld(30, 0, 30), true);
            look(gardener[0], 0, -90);
            for (int i = 0; i < 4; i++) {
                LustStatueEntity statue = statues.get(i);
                statue.wake();
                BlockPos top = piece.fountainHomes().get(i);
                Vec3d out = Vec3d.of(top.subtract(piece.toWorld(0, 1, 0))).normalize().multiply(2);
                statue.refreshPositionAndAngles(top.getX() + 0.5 + out.x, top.getY() - 1, top.getZ() + 0.5 + out.z, 0, 0);
            }
        });
        ctx.runAtTick(80, () -> {
            ctx.assertEquals(garden.settledCount(), 4, "all four statues found their place");
            for (int i = 0; i < 4; i++) {
                LustStatueEntity statue = LustGardens.settledOn(garden, i);
                BlockPos top = piece.fountainHomes().get(i);
                ctx.assertTrue(statue != null && statue.isSettled(), "statue " + i + " settled (and the client is told)");
                ctx.assertTrue(statue.getPos().squaredDistanceTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5) < 1e-6,
                        "statue " + i + " stands dead centre on its plinth, at " + statue.getPos());
            }
            for (int i = 0; i < 4; i++) {
                for (BlockPos candle : piece.fountainCandles(i)) ctx.assertTrue(world.getBlockState(candle).get(CandleBlock.LIT), "candles lit at " + candle);
            }
            ctx.assertTrue(LustGardens.solved(garden), "the garden is at peace");
        });
        ctx.runAtTick(200, () -> {
            for (BlockPos pos : piece.capBlocks()) ctx.assertTrue(world.getBlockState(pos).isAir(), "the basin floor sank away at " + pos + ", got " + world.getBlockState(pos));
            for (BlockPos pos : piece.basinWater()) ctx.assertFalse(world.getBlockState(pos).isOf(Blocks.WATER), "the basin ran dry at " + pos);
            for (int z = LustGardenPlan.CAP_Z0; z > LustGardenPlan.CRYPT_NEAR; z--) {
                BlockPos over = piece.toWorld(0, LustGardenPlan.stepY(z) + 1, z);
                ctx.assertTrue(world.getBlockState(over).isAir(), "a dry, clear stair at " + over + ", got " + world.getBlockState(over));
            }
            for (BlockPos pos : piece.spouts()) ctx.assertFalse(world.getFluidState(pos).isStill(), "the spouts stopped at " + pos);
            for (LustStatueEntity statue : LustGardens.statues(garden)) ctx.assertFalse(statue.eyesOpen(), "every statue closed its eyes");

            // (update() does this once nobody is near and no Lust fight is on; the fight tests may be running one)
            LustGardens.reset(garden);
            for (BlockPos pos : piece.capBlocks()) ctx.assertFalse(world.getBlockState(pos).isAir(), "the basin floor is back at " + pos);
            for (BlockPos pos : piece.basinWater()) ctx.assertTrue(world.getBlockState(pos).isOf(Blocks.WATER), "the basin is full again at " + pos);
            for (int i = 0; i < 4; i++) {
                for (BlockPos candle : piece.fountainCandles(i)) ctx.assertFalse(world.getBlockState(candle).get(CandleBlock.LIT), "candles out at " + candle);
            }
            // Someone left down in the crypt (back from the fight after a reset): the way up opens for them
            gardener[0].teleport(world, piece.lock().getX() + 0.5, piece.lock().getY(), piece.lock().getZ() + 6.5, 0, 0);
        });
        ctx.runAtTick(320, () -> {
            for (BlockPos pos : piece.capBlocks()) ctx.assertTrue(world.getBlockState(pos).isAir(), "opened for someone in the crypt at " + pos);
            leave(gardener[0]);
            unload(world, piece);
            ctx.complete();
        });
    }
}
