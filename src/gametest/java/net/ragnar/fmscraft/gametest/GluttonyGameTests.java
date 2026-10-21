package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.mob.PiglinEntity;
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
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.GorgedBruteEntity;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallPiece;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallStructure;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHalls;

import java.util.List;
import java.util.UUID;

/**
 * Each test builds a whole Hall of Gluttony far from the others and registers it as a structure start, the way world
 * generation does, so the game finds it like a generated one.
 */
public class GluttonyGameTests implements FabricGameTest {
    private static final int BRIDGE = 10;

    private static GluttonyHallPiece build(TestContext ctx, int at, BlockRotation rotation) {
        ServerWorld world = ctx.getWorld();
        BlockPos center = new BlockPos(at, GluttonyHallStructure.FLOOR_Y, -at);
        GluttonyHallPiece piece = new GluttonyHallPiece(7L, center, rotation, BRIDGE);
        Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(GluttonyHalls.HALL);
        ChunkPos home = new ChunkPos(center);
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

    private static void unload(ServerWorld world, GluttonyHallPiece piece) {
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) world.setChunkForced(cx, cz, false);
        }
    }

    /** Like TestContext's mock player, but in survival (that one always counts as creative, which the hall leaves alone). */
    private static ServerPlayerEntity survivalPlayer(ServerWorld world, BlockPos at) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-hungry-player");
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        // Plenty of health, so nothing at the feast kills them before the test is done
        player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        moveTo(player, at);
        return player;
    }

    private static void moveTo(ServerPlayerEntity player, BlockPos at) {
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
    }

    /** A hall with a player standing in front of the Gorged Brute, and the feast laid. */
    private record Setup(GluttonyHallPiece piece, GluttonyHalls.Hall hall, ServerPlayerEntity player, GorgedBruteEntity brute) {
    }

    /**
     * Ticks to wait after building a hall before anyone comes: its chunks are forced in the same tick, and their
     * entities load a little later (the game won't put anything there until they have).
     */
    private static final int START = 40;

    /** Builds the hall now and, {@link #START} ticks later, has a player come and the feast laid; the setup is then in {@code s[0]}. */
    private static Setup[] setup(TestContext ctx, int at, BlockRotation rotation) {
        ServerWorld world = ctx.getWorld();
        GluttonyHallPiece piece = build(ctx, at, rotation);
        Setup[] s = new Setup[1];
        ctx.runAtTick(START, () -> {
            ctx.assertTrue(world.shouldTickEntity(piece.gorged()), "the hall's entities should have loaded by now");
            ServerPlayerEntity player = survivalPlayer(world, piece.toWorld(GluttonyHallShape.GORGED.x() + 3, 2, 0));
            GluttonyHalls.Hall hall = GluttonyHalls.hall(world, piece);
            GluttonyHalls.update(hall);
            GorgedBruteEntity brute = GluttonyHalls.brute(hall);
            ctx.assertTrue(brute != null, "the Gorged Brute should be on his dais once someone comes");
            s[0] = new Setup(piece, hall, player, brute);
        });
        return s;
    }

    private static void finish(TestContext ctx, Setup s) {
        cleanUp(ctx, s);
        ctx.complete();
    }

    /** Everyone leaves, and the hall is let go. */
    private static void cleanUp(TestContext ctx, Setup s) {
        moveTo(s.player, s.piece.center().add(2000, 100, 0));
        GluttonyHalls.update(s.hall);
        ctx.getWorld().getServer().getPlayerManager().remove(s.player);
        unload(ctx.getWorld(), s.piece);
    }

    // ---- The structure ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void hallOfGluttonyIsBuiltWithItsGateAndLock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockRotation rotation = BlockRotation.COUNTERCLOCKWISE_90;
        GluttonyHallPiece piece = build(ctx, 81920, rotation);

        // The Gluttony lock in the chapel, turned with the rest of the hall, before a clear frame
        BlockState lock = world.getBlockState(piece.lock());
        ctx.assertTrue(lock.isOf(ModBlocks.SIN_PORTAL_FRAME) && lock.get(SinPortalFrameBlock.SIN) == Sin.GLUTTONY, "the Gluttony lock should be in the chapel, got " + lock);
        ctx.assertEquals(lock.get(SinPortalFrameBlock.FACING), rotation.rotate(Direction.EAST), "lock facing");
        BlockPos portal = piece.lock().offset(lock.get(SinPortalFrameBlock.FACING).getOpposite(), SinPortalFrameBlock.PORTAL_DISTANCE);
        Direction across = lock.get(SinPortalFrameBlock.FACING).rotateYClockwise();
        for (int l = -1; l <= 1; l++) {
            for (int h = 0; h <= 2; h++) ctx.assertTrue(world.getBlockState(portal.offset(across, l).up(h)).isAir(), "the portal opening should be clear");
        }
        ctx.assertTrue(world.getBlockState(portal.offset(across, 2).down()).isOf(Blocks.GOLD_BLOCK), "gold frame corner");

        // The gilded gate, shut
        for (BlockPos pos : piece.gate()) ctx.assertTrue(world.getBlockState(pos).isOf(Blocks.GILDED_BLACKSTONE), "the gate at " + piece.toLocal(pos));

        // Barrels and chests with the right loot: pantries down the aisles, his hoard on the dais, two by the lock
        List<BlockPos> containers = piece.containers();
        int pantries = GluttonyHallShape.PANTRIES.size(), hoard = GluttonyHallShape.HOARD_CHESTS.size();
        for (int i = 0; i < containers.size(); i++) {
            var expected = i < pantries ? GluttonyHallPiece.PANTRY_LOOT : i < pantries + hoard ? GluttonyHallPiece.HOARD_LOOT : GluttonyHallPiece.SANCTUM_LOOT;
            ctx.assertTrue(world.getBlockEntity(containers.get(i)) instanceof LootableContainerBlockEntity c && expected.equals(c.getLootTable()),
                    "container " + i + " at " + piece.toLocal(containers.get(i)) + " should have " + expected.getValue());
        }

        // The banquet, the rose window, a bell in each tower, the bridge out to the shore
        ctx.assertTrue(world.getBlockState(piece.toWorld(0, 1, 0)).isOf(Blocks.CRIMSON_SLAB), "the banquet table");
        ctx.assertTrue(world.getBlockState(piece.toWorld(GluttonyHallShape.FACADE_OUT, GluttonyHallShape.ROSE_Y, 0)).isOf(Blocks.YELLOW_STAINED_GLASS), "the rose window's heart");
        for (GluttonyHallShape.Spot bell : GluttonyHallShape.BELLS) ctx.assertTrue(world.getBlockState(piece.toWorld(bell)).isOf(Blocks.BELL), "a bell in the belfry");
        for (int x = GluttonyHallShape.TERRACE_EAST; x <= GluttonyHallShape.TERRACE_EAST + BRIDGE; x++) {
            ctx.assertTrue(world.getBlockState(piece.toWorld(x, 0, 0)).isOf(Blocks.POLISHED_BLACKSTONE_BRICKS)
                    || world.getBlockState(piece.toWorld(x, 0, 0)).isOf(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), "the bridge's deck at " + x);
        }
        unload(world, piece);
        ctx.complete();
    }

    /**
     * The real thing, asked of the real Nether generator: how often a hall finds a lava lake (the test world doesn't
     * generate structures, so this asks the hall's placement directly, the way world generation would, for a run of
     * placement regions). Then the first one found is built into the generated Nether there, and its bridge has to
     * reach the shore.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1200)
    public void hallsStandInTheNethersLavaLakes(TestContext ctx) {
        ServerWorld nether = ctx.getWorld().getServer().getWorld(net.minecraft.world.World.NETHER);
        Structure hall = nether.getRegistryManager().get(RegistryKeys.STRUCTURE).get(GluttonyHalls.HALL);
        var set = nether.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(net.minecraft.util.Identifier.of("fmscraft", "hall_of_gluttony"));
        var spread = (net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement) set.placement();
        var calculator = nether.getChunkManager().getStructurePlacementCalculator();
        var generator = nether.getChunkManager().getChunkGenerator();
        int tried = 0, built = 0;
        GluttonyHallPiece first = null;
        for (int rx = -10; rx < 10; rx++) {
            for (int rz = -10; rz < 10; rz++) {
                ChunkPos start = spread.getStartChunk(calculator.getStructureSeed(), rx * spread.getSpacing(), rz * spread.getSpacing());
                tried++;
                if (!spread.shouldGenerate(calculator, start.x, start.z)) continue; // too near a fortress or bastion
                var context = new Structure.Context(nether.getRegistryManager(), generator, generator.getBiomeSource(),
                        nether.getChunkManager().getNoiseConfig(), nether.getStructureTemplateManager(), calculator.getStructureSeed(), start, nether,
                        hall.getValidBiomes()::contains);
                var position = hall.getValidStructurePosition(context);
                if (position.isEmpty()) continue;
                built++;
                if (first == null) first = (GluttonyHallPiece) position.get().generate().toList().pieces().get(0);
            }
        }
        net.ragnar.fmscraft.FmsCraft.LOGGER.info("A Hall of Gluttony would be built in {} of {} Nether placement regions ({} blocks across each)",
                built, tried, spread.getSpacing() * 16);
        ctx.assertTrue(built >= 1, "halls should find a lava lake somewhere in " + tried + " regions");
        ctx.assertTrue(built <= tried / 2, "halls should be rare, got " + built + "/" + tried);

        // Build the first one into the Nether as generated there, and walk off the end of its bridge
        GluttonyHallPiece piece = first;
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                nether.getChunk(cx, cz);
                ChunkPos pos = new ChunkPos(cx, cz);
                piece.generate(nether, nether.getStructureAccessor(), generator, nether.getRandom(),
                        new BlockBox(pos.getStartX(), box.getMinY(), pos.getStartZ(), pos.getEndX(), box.getMaxY(), pos.getEndZ()), pos, BlockPos.ORIGIN);
            }
        }
        ctx.assertTrue(nether.getBlockState(piece.lock()).isOf(ModBlocks.SIN_PORTAL_FRAME), "the Gluttony lock");
        int lava = 0, samples = 0;
        for (int x = GluttonyHallShape.TERRACE_WEST - 4; x <= GluttonyHallShape.TERRACE_EAST + 4; x += 6) {
            for (int z : new int[]{-GluttonyHallShape.TERRACE_HALF - 2, GluttonyHallShape.TERRACE_HALF + 2}) {
                samples++;
                if (nether.getBlockState(piece.toWorld(x, GluttonyHallShape.LAVA, z)).isOf(Blocks.LAVA)) lava++;
            }
        }
        ctx.assertTrue(lava >= samples * 3 / 4, "the hall should stand in a lava lake, but only " + lava + "/" + samples + " round it is lava");
        BlockPos end = piece.toWorld(GluttonyHallShape.east(piece.bridge()), 1, 0);
        ctx.assertTrue(nether.getBlockState(end).isAir() && nether.getBlockState(end.up()).isAir(), "the bridge should open onto the shore at " + end);
        boolean ground = false;
        for (int y = GluttonyHallShape.LAVA; y < 0; y++) {
            BlockState state = nether.getBlockState(piece.toWorld(GluttonyHallShape.TERRACE_EAST + piece.bridge(), y, 0));
            ground |= !state.isAir() && !state.isOf(Blocks.LAVA);
        }
        ctx.assertTrue(ground, "there should be land under the end of the bridge (bridge " + piece.bridge() + ")");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hallChestsSometimesHaveTomes(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int[] found = new int[3];
        var hoard = world.getServer().getReloadableRegistries().getLootTable(GluttonyHallPiece.HOARD_LOOT);
        var sanctum = world.getServer().getReloadableRegistries().getLootTable(GluttonyHallPiece.SANCTUM_LOOT);
        for (int i = 0; i < 4000; i++) {
            LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST);
            hoard.generateLoot(params, stack -> {
                if (MagicTomes.of(WheelTier.BEGINNER).contains(stack.getItem())) found[0]++;
            });
            sanctum.generateLoot(params, stack -> {
                if (MagicTomes.of(WheelTier.ADVANCED).contains(stack.getItem())) found[1]++;
                if (MagicTomes.of(WheelTier.MASTER).contains(stack.getItem())) found[2]++;
            });
        }
        ctx.assertTrue(found[0] >= 140 && found[0] <= 260, "expected ~5% beginner tomes in his hoard, got " + found[0] + "/4000");
        ctx.assertTrue(found[1] >= 320 && found[1] <= 480, "expected ~10% advanced tomes by the lock, got " + found[1] + "/4000");
        ctx.assertTrue(found[2] >= 40 && found[2] <= 130, "expected ~2% master tomes by the lock, got " + found[2] + "/4000");
        ctx.complete();
    }

    // ---- The feast ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void theFeastIsLaidWhenSomeoneComes(TestContext ctx) {
        Setup[] setup = setup(ctx, 86016, BlockRotation.NONE);
        ctx.runAtTick(START + 1, () -> {
            Setup s = setup[0];
            GorgedBruteEntity brute = s.brute;
            ctx.assertTrue(brute.getBlockPos().equals(s.piece.gorged()), "on his dais, got " + s.piece.toLocal(brute.getBlockPos()));
            ctx.assertTrue(Math.abs(brute.getWidth() - 1.2f) < 0.01f && Math.abs(brute.getHeight() - 3.9f) < 0.01f,
                    "twice a brute's size, got " + brute.getWidth() + "x" + brute.getHeight());
            List<AbstractPiglinEntity> feast = GluttonyHalls.feasters(s.hall);
            long piglins = feast.stream().filter(e -> e instanceof PiglinEntity).count();
            long brutes = feast.stream().filter(e -> e instanceof PiglinBruteEntity).count();
            ctx.assertEquals((int) piglins, GluttonyHalls.PIGLINS, "piglins at the banquet");
            ctx.assertEquals((int) brutes, GluttonyHalls.BRUTES, "brutes about the hall");
            for (AbstractPiglinEntity piglin : feast) {
                ctx.assertTrue(s.piece.inHall(piglin.getBlockPos()), "in the hall: " + s.piece.toLocal(piglin.getBlockPos()));
                ctx.assertTrue(piglin.isAdult() && !piglin.getMainHandStack().isEmpty(), "grown and armed: " + piglin);
            }

            // The hall's rules, and his boss bar
            ctx.assertTrue(s.player.hasStatusEffect(ModEffects.BLOCK_BARRIER), "Block Barrier in the hall");
            ctx.assertTrue(s.player.isBlockBreakingRestricted(ctx.getWorld(), s.player.getBlockPos().down(), net.minecraft.world.GameMode.SURVIVAL),
                    "can't break blocks in the hall");
            // But it's a fight: nothing slows your swing (Mining Fatigue would, to nothing)
            ctx.assertFalse(s.player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "no Mining Fatigue in the hall");
            double speed = s.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED);
            ctx.assertTrue(speed >= 4.0 - 0.01, "a full-speed swing, got attack speed " + speed);
            ctx.assertTrue(s.hall.showsBar(s.player), "his bar shows in the hall");

            // Only one of him and one feast, however often it's checked
            GluttonyHalls.update(s.hall);
            ctx.assertEquals(ctx.getWorld().getEntitiesByClass(GorgedBruteEntity.class, net.minecraft.util.math.Box.from(s.hall.box()), e -> true).size(), 1, "Gorged Brutes");
            ctx.assertEquals(GluttonyHalls.feasters(s.hall).size(), GluttonyHalls.PIGLINS + GluttonyHalls.BRUTES, "the feast isn't laid twice");
            finish(ctx, s);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 500)
    public void morePiglinsComeWhileAnyoneIsInTheHall(TestContext ctx) {
        Setup[] setup = setup(ctx, 90112, BlockRotation.CLOCKWISE_90);
        int[] left = new int[1];
        ctx.runAtTick(START + 1, () -> {
            List<AbstractPiglinEntity> feast = GluttonyHalls.feasters(setup[0].hall);
            for (int i = 0; i < 3; i++) feast.get(i).discard();
            left[0] = feast.size() - 3;
        });
        ctx.runAtTick(START + GluttonyHalls.REINFORCE_TICKS + 15, () -> {
            Setup s = setup[0];
            int now = GluttonyHalls.feasters(s.hall).size();
            ctx.assertTrue(now > left[0], "another should have come in, still " + now);
            ctx.assertTrue(now <= GluttonyHalls.PIGLINS + GluttonyHalls.BRUTES, "never more than the feast began with");
            finish(ctx, s);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 900)
    public void theGorgedBruteEatsAMouthfulASecondUntilHeBursts(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup[] setup = setup(ctx, 94208, BlockRotation.CLOCKWISE_180);
        float[] hungry = new float[1];
        ctx.runAtTick(START + 1, () -> {
            Setup s = setup[0];
            ServerPlayerEntity player = s.player;
            GorgedBruteEntity brute = s.brute;
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            brute.interact(player, Hand.MAIN_HAND);
            ctx.assertEquals(brute.eaten(), 0, "he doesn't eat sticks");

            // A bowl of stew: he eats it, and the bowl comes back
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.MUSHROOM_STEW));
            brute.interact(player, Hand.MAIN_HAND);
            ctx.assertEquals(brute.eaten(), 1, "one mouthful");
            ctx.assertTrue(player.getInventory().contains(new ItemStack(Items.BOWL)), "the bowl comes back");

            // Straight after, he's still chewing
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COOKED_PORKCHOP, 64));
            brute.interact(player, Hand.MAIN_HAND);
            ctx.assertEquals(brute.eaten(), 1, "only one mouthful a second");
            ctx.assertEquals(player.getMainHandStack().getCount(), 64, "and he doesn't take what he can't eat");
            hungry[0] = brute.getHeight();
        });

        // Then one a second, every second, until he's had his fill
        for (int i = 1; i < GorgedBruteEntity.FOOD_TO_BURST; i++) {
            int n = i;
            ctx.runAtTick(START + 1 + GorgedBruteEntity.FEED_COOLDOWN * i, () -> {
                Setup s = setup[0];
                moveTo(s.player, s.piece.toWorld(GluttonyHallShape.GORGED.x() + 3, 2, 0));
                s.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COOKED_PORKCHOP, 64));
                s.brute.interact(s.player, Hand.MAIN_HAND);
                if (n < GorgedBruteEntity.FOOD_TO_BURST - 1) {
                    ctx.assertEquals(s.brute.eaten(), n + 1, "a mouthful each second");
                    ctx.assertEquals(s.player.getMainHandStack().getCount(), 63, "taken from the player's hand");
                }
                if (n == 15) {
                    ctx.assertTrue(s.brute.getHeight() > hungry[0] + 0.3f, "he swells as he eats");
                    GluttonyHalls.update(s.hall);
                    ctx.assertTrue(s.hall.showsBar(s.player), "his bar shows how full he is");
                }
            });
        }
        ctx.runAtTick(START + 2 + GorgedBruteEntity.FEED_COOLDOWN * (GorgedBruteEntity.FOOD_TO_BURST - 1), () -> {
            Setup s = setup[0];
            ServerPlayerEntity player = s.player;
            ctx.assertTrue(s.brute.isRemoved(), "he bursts at the last mouthful");
            ctx.assertTrue(s.hall.sated(), "the hall gives in");
            ctx.assertFalse(s.hall.showsBar(player), "his bar goes with him");
            ctx.assertFalse(player.hasStatusEffect(ModEffects.BLOCK_BARRIER), "the rules lift");
            for (int i = 0; i < GluttonyHallShape.GATE_HEIGHT; i++) GluttonyHalls.update(s.hall);
            ctx.assertTrue(GluttonyHalls.gateOpen(s.hall), "the gate sinks away a row at a time");
            GluttonyHalls.update(s.hall);
            ctx.assertTrue(GluttonyHalls.brute(s.hall) == null, "and he doesn't come back while anyone's about");
            ctx.assertTrue(world.getEntitiesByClass(GorgedBruteEntity.class, net.minecraft.util.math.Box.from(s.hall.box()), e -> true).isEmpty(), "no Gorged Brute");
            finish(ctx, s);
        });
    }

    // ---- The chapel ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void nothingCanGetAtYouInTheChapel(TestContext ctx) {
        Setup[] setup = setup(ctx, 98304, BlockRotation.COUNTERCLOCKWISE_90);
        PiglinBruteEntity[] brute = new PiglinBruteEntity[1];
        PiglinEntity[] stray = new PiglinEntity[1];
        ctx.runAtTick(START + 1, () -> {
            Setup s = setup[0];
            ServerPlayerEntity player = s.player;
            brute[0] = (PiglinBruteEntity) GluttonyHalls.feasters(s.hall).stream().filter(e -> e instanceof PiglinBruteEntity).findFirst().orElseThrow();
            ctx.assertTrue(brute[0].canTarget(player), "in the hall, a brute can go for you");

            // Into the chapel (someone in there with the gate shut gets it opened)
            moveTo(player, s.piece.toWorld(GluttonyHallShape.LOCK.x() + 2, 1, 2));
            ctx.assertTrue(s.piece.inSanctum(player.getBlockPos()), "in the chapel");
            GluttonyHalls.update(s.hall);
            ctx.assertTrue(s.hall.sated(), "someone shut in the chapel gets the gate opened");
            ctx.assertFalse(brute[0].canTarget(player), "nothing can go for someone in the chapel");
            brute[0].getBrain().remember(MemoryModuleType.ATTACK_TARGET, player);
            ctx.assertFalse(player.hasStatusEffect(ModEffects.BLOCK_BARRIER), "no rules in the chapel");

            // One of the feast that wanders in is sent back out
            stray[0] = (PiglinEntity) GluttonyHalls.feasters(s.hall).stream().filter(e -> e instanceof PiglinEntity).findFirst().orElseThrow();
            BlockPos inside = s.piece.toWorld(GluttonyHallShape.ROOM_EAST - 1, 1, 0);
            stray[0].refreshPositionAndAngles(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, 0, 0);
        });
        ctx.runAtTick(START + 120, () -> {
            Setup s = setup[0];
            ServerPlayerEntity player = s.player;
            ctx.assertFalse(brute[0].getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player,
                    "the brute forgets whoever's in the chapel");
            ctx.assertTrue(player.getHealth() >= 1000 - 0.01f, "and nobody hurts them there, health " + player.getHealth());
            ctx.assertFalse(stray[0].isAlive() && s.piece.inSanctum(stray[0].getBlockPos()), "the stray is turned back, at " + s.piece.toLocal(stray[0].getBlockPos()));
            // Back out into the hall, and they can be gone for again
            moveTo(player, s.piece.toWorld(GluttonyHallShape.GORGED.x() + 3, 2, 0));
            ctx.assertTrue(brute[0].canTarget(player), "out of the chapel, fair game again");
            finish(ctx, s);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void theHallResetsOnceEveryoneHasGone(TestContext ctx) {
        Setup[] setup = setup(ctx, 102400, BlockRotation.NONE);
        ctx.runAtTick(START + 1, () -> {
            Setup s = setup[0];
            ServerPlayerEntity player = s.player;
            s.brute.setEaten(GorgedBruteEntity.FOOD_TO_BURST - 1);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BREAD));
            s.brute.interact(player, Hand.MAIN_HAND);
            ctx.assertTrue(s.hall.sated() && s.brute.isRemoved(), "the last mouthful bursts him");
            for (int i = 0; i < GluttonyHallShape.GATE_HEIGHT; i++) GluttonyHalls.update(s.hall);
            ctx.assertTrue(GluttonyHalls.gateOpen(s.hall), "the gate is down");

            // Everyone goes: the feast is cleared away and the gate shuts
            moveTo(player, s.piece.center().add(2000, 100, 0));
            GluttonyHalls.update(s.hall);
            ctx.assertTrue(GluttonyHalls.gateShut(s.hall) && !s.hall.sated(), "the gate shuts again");
            ctx.assertTrue(GluttonyHalls.feasters(s.hall).isEmpty(), "the feast is cleared away");

            // Back again
            moveTo(player, s.piece.toWorld(GluttonyHallShape.GORGED.x() + 3, 2, 0));
        });
        ctx.runAtTick(START + 5, () -> {
            Setup s = setup[0];
            GluttonyHalls.update(s.hall);
            GorgedBruteEntity back = GluttonyHalls.brute(s.hall);
            ctx.assertTrue(back != null && back != s.brute && back.eaten() == 0, "he's back, hungry");
            ctx.assertEquals(GluttonyHalls.feasters(s.hall).size(), GluttonyHalls.PIGLINS + GluttonyHalls.BRUTES, "and the feast is laid again");
            finish(ctx, s);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void heCantBeHurtOrMoved(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup[] setup = setup(ctx, 106496, BlockRotation.CLOCKWISE_90);
        ctx.runAtTick(START + 1, () -> {
            Setup s = setup[0];
            ctx.assertFalse(s.brute.damage(world.getDamageSources().playerAttack(s.player), 50f), "can't be hurt");
            ctx.assertFalse(s.brute.damage(world.getDamageSources().onFire(), 50f), "or burned");
            s.brute.takeKnockback(5, 1, 0);
        });
        ctx.runAtTick(START + 40, () -> {
            Setup s = setup[0];
            ctx.assertTrue(s.brute.isAlive() && s.brute.getHealth() == s.brute.getMaxHealth(), "unharmed");
            ctx.assertTrue(s.brute.getBlockPos().equals(s.piece.gorged()), "and still on his dais, at " + s.piece.toLocal(s.brute.getBlockPos()));
            finish(ctx, s);
        });
    }

    /** Chorus fruit can't skip the challenge: nothing teleports at random into the chapel while the gate is shut. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void noChorusFruitPastTheGate(TestContext ctx) {
        Setup[] setup = setup(ctx, 110592, BlockRotation.COUNTERCLOCKWISE_90);
        ctx.runAtTick(START + 1, () -> {
            Setup s = setup[0];
            BlockPos before = s.player.getBlockPos();
            BlockPos chapel = s.piece.toWorld(GluttonyHallShape.LOCK.x() + 2, 1, 2);
            ctx.assertFalse(s.player.teleport(chapel.getX() + 0.5, chapel.getY(), chapel.getZ() + 0.5, true), "no teleporting into the chapel while the gate is shut");
            ctx.assertTrue(s.player.getBlockPos().equals(before), "the player stays where they were");
            BlockPos hall = s.piece.toWorld(0, 1, 5);
            ctx.assertTrue(s.player.teleport(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, true), "but anywhere in the hall is fine");

            // Once he's burst and the gate is down, the chapel is open to anyone
            s.brute.setEaten(GorgedBruteEntity.FOOD_TO_BURST - 1);
            moveTo(s.player, s.piece.toWorld(GluttonyHallShape.GORGED.x() + 3, 2, 0));
            s.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BREAD));
            s.brute.interact(s.player, Hand.MAIN_HAND);
            for (int i = 0; i < GluttonyHallShape.GATE_HEIGHT; i++) GluttonyHalls.update(s.hall);
            ctx.assertTrue(GluttonyHalls.gateOpen(s.hall), "the gate is down");
            ctx.assertTrue(s.player.teleport(chapel.getX() + 0.5, chapel.getY(), chapel.getZ() + 0.5, true), "and with it down, teleporting in is fine");
            finish(ctx, s);
        });
    }
}
