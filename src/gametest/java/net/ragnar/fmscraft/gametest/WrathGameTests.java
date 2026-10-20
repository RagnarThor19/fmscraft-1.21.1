package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
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
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.MotherWardenEntity;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.wrath.Stirring;
import net.ragnar.fmscraft.structure.wrath.WrathDenPiece;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Spot;
import net.ragnar.fmscraft.structure.wrath.WrathDenShape.Tripwire;
import net.ragnar.fmscraft.structure.wrath.WrathDenStructure;
import net.ragnar.fmscraft.structure.wrath.WrathDens;

import java.util.List;
import java.util.UUID;

/**
 * Each test builds a whole Den of Wrath far from the others and registers it as a structure start, the way world
 * generation does, so the game finds it like a generated one.
 */
public class WrathGameTests implements FabricGameTest {

    private static WrathDenPiece build(TestContext ctx, int at, BlockRotation rotation) {
        ServerWorld world = ctx.getWorld();
        BlockPos center = new BlockPos(-at, WrathDenStructure.FLOOR_Y, at);
        WrathDenPiece piece = new WrathDenPiece(42L, center, rotation, 6);
        Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(WrathDens.DEN);
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

    private static void unload(ServerWorld world, WrathDenPiece piece) {
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) world.setChunkForced(cx, cz, false);
        }
    }

    /** Like TestContext's mock player, but in survival (that one always counts as creative, which the den leaves alone). */
    private static ServerPlayerEntity survivalPlayer(ServerWorld world, BlockPos at, boolean creative) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-quiet-player");
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
        // The test server puts everyone in creative, which leaves them unable to take damage, and so untargetable
        if (!creative) player.changeGameMode(GameMode.SURVIVAL);
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return player;
    }

    /** A den with a player standing in its hall, and her in her nest. */
    private record Setup(WrathDenPiece piece, WrathDens.Den den, ServerPlayerEntity player, MotherWardenEntity mother) {
    }

    private static Setup setup(TestContext ctx, int at, BlockRotation rotation) {
        ServerWorld world = ctx.getWorld();
        WrathDenPiece piece = build(ctx, at, rotation);
        ServerPlayerEntity player = survivalPlayer(world, piece.toWorld(30, 1, -10), false);
        WrathDens.Den den = WrathDens.den(world, piece);
        WrathDens.update(den);
        MotherWardenEntity mother = WrathDens.mother(den);
        ctx.assertTrue(mother != null, "the Mother Warden should be in her nest once someone comes");
        return new Setup(piece, den, player, mother);
    }

    private static void finish(TestContext ctx, Setup s) {
        cleanUp(ctx, s);
        ctx.complete();
    }

    /** Everyone leaves, and the den is let go. */
    private static void cleanUp(TestContext ctx, Setup s) {
        s.player.refreshPositionAndAngles(s.piece.center().getX() + 1000, 200, s.piece.center().getZ(), 0, 0);
        WrathDens.update(s.den);
        ctx.getWorld().getServer().getPlayerManager().remove(s.player);
        unload(ctx.getWorld(), s.piece);
    }

    private static void step(ServerWorld world, net.minecraft.entity.Entity player) {
        world.emitGameEvent(GameEvent.STEP, player.getPos(), GameEvent.Emitter.of(player, Blocks.SCULK.getDefaultState()));
    }

    // ---- The structure ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void denOfWrathIsBuiltWithItsTrapsAndLock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockRotation rotation = BlockRotation.CLOCKWISE_90;
        WrathDenPiece piece = build(ctx, 36864, rotation);

        // The Wrath lock in the portal room, turned with the rest of the den, before a clear frame
        BlockState lock = world.getBlockState(piece.lock());
        ctx.assertTrue(lock.isOf(ModBlocks.SIN_PORTAL_FRAME) && lock.get(SinPortalFrameBlock.SIN) == Sin.WRATH, "the Wrath lock should be in the portal room, got " + lock);
        ctx.assertEquals(lock.get(SinPortalFrameBlock.FACING), rotation.rotate(Direction.EAST), "lock facing");
        BlockPos portal = piece.lock().offset(lock.get(SinPortalFrameBlock.FACING).getOpposite(), SinPortalFrameBlock.PORTAL_DISTANCE);
        Direction across = lock.get(SinPortalFrameBlock.FACING).rotateYClockwise();
        for (int l = -1; l <= 1; l++) {
            for (int h = 0; h <= 2; h++) ctx.assertTrue(world.getBlockState(portal.offset(across, l).up(h)).isAir(), "the portal opening should be clear");
        }
        ctx.assertTrue(world.getBlockState(portal.offset(across, 2).down()).isOf(Blocks.REINFORCED_DEEPSLATE), "reinforced deepslate frame corner");

        // Chests with the right loot: three on the ledge and one by the fossil, the hoard in the nest, two by the lock
        List<BlockPos> chests = piece.chests();
        for (int i = 0; i < chests.size(); i++) {
            var expected = i < 4 ? WrathDenPiece.HALL_LOOT : i == 4 ? WrathDenPiece.NEST_LOOT : WrathDenPiece.SANCTUM_LOOT;
            ctx.assertTrue(world.getBlockEntity(chests.get(i)) instanceof LootableContainerBlockEntity c && expected.equals(c.getLootTable()),
                    "chest " + i + " at " + chests.get(i) + " should have " + expected.getValue());
        }

        // Tripwires strung across the ledge, pressure plates in the paving, the nest, the gate and the doorway
        for (Tripwire wire : WrathDenShape.TRIPWIRES) {
            // Built here in a live world, block by block, a hook can be checked before the far one is in; world
            // generation sets them all at once. Touch the string once everything's in to wire it up, as the game would
            BlockPos first = piece.toWorld(wire.string().get(0));
            BlockState string0 = world.getBlockState(first);
            world.setBlockState(first, Blocks.AIR.getDefaultState());
            world.setBlockState(first, string0);
            BlockState hook = world.getBlockState(piece.toWorld(wire.outerHook()));
            ctx.assertTrue(hook.isOf(Blocks.TRIPWIRE_HOOK) && hook.get(TripwireHookBlock.ATTACHED), "a hooked-up tripwire hook, got " + hook);
            for (Spot s : wire.string()) {
                BlockState string = world.getBlockState(piece.toWorld(s));
                ctx.assertTrue(string.isOf(Blocks.TRIPWIRE) && string.get(TripwireBlock.ATTACHED), "tripwire string, got " + string);
            }
        }
        for (Spot plate : WrathDenShape.PLATES) {
            ctx.assertTrue(world.getBlockState(piece.toWorld(plate)).isOf(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE), "a pressure plate at " + plate);
            ctx.assertTrue(world.getBlockState(piece.toWorld(plate).down()).isOf(Blocks.POLISHED_BLACKSTONE), "hidden on polished blackstone");
        }
        for (Spot catalyst : WrathDenShape.CATALYSTS) ctx.assertTrue(world.getBlockState(piece.toWorld(catalyst)).isOf(Blocks.SCULK_CATALYST), "catalyst in the nest");
        for (Spot shrieker : WrathDenShape.SHRIEKERS) {
            BlockState state = world.getBlockState(piece.toWorld(shrieker));
            ctx.assertTrue(state.isOf(Blocks.SCULK_SHRIEKER) && !state.get(net.minecraft.block.SculkShriekerBlock.CAN_SUMMON),
                    "a shrieker that can't summon wardens, got " + state);
        }
        ctx.assertTrue(world.getBlockState(piece.toWorld(WrathDenShape.RADIUS, WrathDenShape.BALCONY + 7, 0)).isOf(Blocks.REINFORCED_DEEPSLATE), "the gate");
        ctx.assertTrue(world.getBlockState(piece.toWorld(-WrathDenShape.RADIUS, 1, 0)).isAir()
                && world.getBlockState(piece.toWorld(-WrathDenShape.RADIUS, 1, 1)).isOf(Blocks.REINFORCED_DEEPSLATE), "the little doorway out");
        ctx.assertTrue(world.getBlockState(piece.toWorld(1, 0, 1)).isOf(Blocks.SCULK) || world.getBlockState(piece.toWorld(1, 0, 1)).isOf(Blocks.BONE_BLOCK)
                || world.getBlockState(piece.toWorld(1, 0, 1)).isOf(Blocks.SOUL_SOIL), "the nest floor");
        unload(world, piece);
        ctx.complete();
    }

    /**
     * The real thing, asked of the real Nether generator: how often a den finds room (the test world doesn't generate
     * structures, so this asks the den's placement directly, the way world generation would, for a run of placement
     * regions). Then the first one found is built into the generated Nether there, and its tunnel has to come out
     * into the open.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void densFindRoomInTheNether(TestContext ctx) {
        ServerWorld nether = ctx.getWorld().getServer().getWorld(net.minecraft.world.World.NETHER);
        Structure den = nether.getRegistryManager().get(RegistryKeys.STRUCTURE).get(WrathDens.DEN);
        var set = nether.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(net.minecraft.util.Identifier.of("fmscraft", "den_of_wrath"));
        var spread = (net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement) set.placement();
        var calculator = nether.getChunkManager().getStructurePlacementCalculator();
        var generator = nether.getChunkManager().getChunkGenerator();
        int tried = 0, built = 0;
        WrathDenPiece first = null;
        for (int rx = -4; rx < 4; rx++) {
            for (int rz = -4; rz < 4; rz++) {
                ChunkPos start = spread.getStartChunk(calculator.getStructureSeed(), rx * spread.getSpacing(), rz * spread.getSpacing());
                tried++;
                if (!spread.shouldGenerate(calculator, start.x, start.z)) continue; // too near a fortress or bastion
                var context = new Structure.Context(nether.getRegistryManager(), generator, generator.getBiomeSource(),
                        nether.getChunkManager().getNoiseConfig(), nether.getStructureTemplateManager(), calculator.getStructureSeed(), start, nether,
                        den.getValidBiomes()::contains);
                var position = den.getValidStructurePosition(context);
                if (position.isEmpty()) continue;
                built++;
                if (first == null) first = (WrathDenPiece) position.get().generate().toList().pieces().get(0);
            }
        }
        net.ragnar.fmscraft.FmsCraft.LOGGER.info("A Den of Wrath would be built in {} of {} Nether placement regions", built, tried);
        ctx.assertTrue(built >= tried / 8, "dens should find room in at least an eighth of the regions, got " + built + "/" + tried);

        // Build the first one into the Nether as generated there, and walk out of its tunnel
        WrathDenPiece piece = first;
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                nether.getChunk(cx, cz);
                ChunkPos pos = new ChunkPos(cx, cz);
                piece.generate(nether, nether.getStructureAccessor(), generator, nether.getRandom(),
                        new BlockBox(pos.getStartX(), box.getMinY(), pos.getStartZ(), pos.getEndX(), box.getMaxY(), pos.getEndZ()), pos, BlockPos.ORIGIN);
            }
        }
        ctx.assertTrue(nether.getBlockState(piece.lock()).isOf(ModBlocks.SIN_PORTAL_FRAME), "the Wrath lock");
        BlockPos mouth = piece.mouth();
        int open = 0;
        for (int h = 0; h < 3; h++) if (nether.getBlockState(mouth.up(h)).getCollisionShape(nether, mouth.up(h)).isEmpty()) open++;
        ctx.assertTrue(open >= 2, "the tunnel should come out into the open at " + mouth + " (tunnel " + piece.tunnel() + ")");
        for (int x = WrathDenShape.RADIUS; x < WrathDenShape.OUTER + piece.tunnel(); x++) {
            ctx.assertTrue(nether.getBlockState(piece.toWorld(x, WrathDenShape.BALCONY + 1, 0)).isAir(), "the tunnel is clear at " + x);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void denChestsSometimesHaveTomes(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int[] found = new int[3];
        var hall = world.getServer().getReloadableRegistries().getLootTable(WrathDenPiece.HALL_LOOT);
        var sanctum = world.getServer().getReloadableRegistries().getLootTable(WrathDenPiece.SANCTUM_LOOT);
        for (int i = 0; i < 4000; i++) {
            LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST);
            hall.generateLoot(params, stack -> {
                if (MagicTomes.of(WheelTier.BEGINNER).contains(stack.getItem())) found[0]++;
            });
            sanctum.generateLoot(params, stack -> {
                if (MagicTomes.of(WheelTier.ADVANCED).contains(stack.getItem())) found[1]++;
                if (MagicTomes.of(WheelTier.MASTER).contains(stack.getItem())) found[2]++;
            });
        }
        ctx.assertTrue(found[0] >= 140 && found[0] <= 260, "expected ~5% beginner tomes in the hall, got " + found[0] + "/4000");
        ctx.assertTrue(found[1] >= 320 && found[1] <= 480, "expected ~10% advanced tomes by the lock, got " + found[1] + "/4000");
        ctx.assertTrue(found[2] >= 40 && found[2] <= 130, "expected ~2% master tomes by the lock, got " + found[2] + "/4000");
        ctx.complete();
    }

    // ---- The Mother Warden ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void theMotherWardenSleepsInHerNest(TestContext ctx) {
        Setup s = setup(ctx, 40960, BlockRotation.NONE);
        MotherWardenEntity mother = s.mother;
        ctx.assertTrue(mother.sleepPhase() == MotherWardenEntity.ASLEEP && mother.isAiDisabled(), "asleep, and not thinking");
        ctx.assertTrue(mother.getBlockPos().equals(s.piece.nest()), "in her nest, got " + mother.getBlockPos());
        ctx.assertTrue(Math.abs(mother.getWidth() - 5.4f) < 0.01f && Math.abs(mother.getHeight() - 17.4f) < 0.01f,
                "six times a warden's size, got " + mother.getWidth() + "x" + mother.getHeight());
        // Nothing in the hall hangs low enough over her nest to catch on her
        List<BlockPos> inTheWay = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(mother.getBoundingBox().getMinPos()), BlockPos.ofFloored(mother.getBoundingBox().getMaxPos().add(0, 1, 0)))) {
            if (!ctx.getWorld().getBlockState(pos).getCollisionShape(ctx.getWorld(), pos).isEmpty()) inTheWay.add(s.piece.toLocal(pos));
        }
        ctx.assertTrue(inTheWay.isEmpty(), "room to stand up in her nest, but in the way: " + inTheWay);
        ctx.assertTrue(mother.getMaxHealth() >= 1000, "much tougher than a warden");
        // Only one of her, however often it's checked
        WrathDens.update(s.den);
        ctx.assertEquals(ctx.getWorld().getEntitiesByClass(MotherWardenEntity.class, net.minecraft.util.math.Box.from(s.den.box()), e -> true).size(), 1, "mothers");
        finish(ctx, s);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void threeNoisesWakeHer(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup s = setup(ctx, 45056, BlockRotation.COUNTERCLOCKWISE_90);
        ctx.runAtTick(5, () -> {
            step(world, s.player);
            ctx.assertEquals(s.den.stirring().stirs(), 1, "a footstep stirs her");
            ctx.assertTrue(s.mother.isDozing(), "but she sleeps on");
            // Opening a chest and closing it again straight after is one disturbance
            world.emitGameEvent(GameEvent.CONTAINER_OPEN, s.player.getPos(), GameEvent.Emitter.of(s.player, null));
            world.emitGameEvent(GameEvent.CONTAINER_CLOSE, s.player.getPos(), GameEvent.Emitter.of(s.player, null));
            ctx.assertEquals(s.den.stirring().stirs(), 1, "noises while she listens don't count again");
        });
        ctx.runAtTick(5 + Stirring.LISTEN_TICKS + 1, () -> {
            world.emitGameEvent(GameEvent.BLOCK_ACTIVATE, s.player.getPos(), GameEvent.Emitter.of(s.player, Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE.getDefaultState()));
            ctx.assertEquals(s.den.stirring().stirs(), 2, "a pressure plate");
            ctx.assertTrue(s.mother.isDozing(), "still asleep at two");
        });
        ctx.runAtTick(5 + 2 * Stirring.LISTEN_TICKS + 2, () -> {
            step(world, s.player);
            ctx.assertFalse(s.mother.isDozing(), "the third noise wakes her");
            ctx.assertFalse(s.mother.isAiDisabled(), "and she thinks again");
            ctx.assertTrue(s.mother.getAngerManager().getAngerFor(s.player) >= 80, "she's angry at whoever woke her");
        });
        ctx.runAtTick(5 + 2 * Stirring.LISTEN_TICKS + 15, () -> {
            WrathDens.update(s.den);
            ctx.assertTrue(s.den.showsBar(s.player), "her boss bar shows in the hall");
            finish(ctx, s);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void sneakingAndOutsidersMakeNoNoise(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup s = setup(ctx, 49152, BlockRotation.CLOCKWISE_180);
        ServerPlayerEntity player = s.player;
        player.setSneaking(true);
        step(world, player);
        world.emitGameEvent(GameEvent.HIT_GROUND, player.getPos(), GameEvent.Emitter.of(player, null));
        ctx.assertEquals(s.den.stirring().stirs(), 0, "sneaking footsteps and landings are silent");
        player.setSneaking(false);

        // A creative player, a mob that wandered in, and noise outside the hall don't count
        ServerPlayerEntity builder = survivalPlayer(world, s.piece.toWorld(28, 1, -12), true);
        step(world, builder);
        ZombieEntity zombie = EntityType.ZOMBIE.create(world);
        zombie.refreshPositionAndAngles(s.piece.toWorld(26, 1, -12), 0, 0);
        world.spawnEntity(zombie);
        step(world, zombie);
        Vec3d tunnel = Vec3d.ofBottomCenter(s.piece.toWorld(WrathDenShape.RADIUS + 3, WrathDenShape.BALCONY + 1, 0));
        world.emitGameEvent(GameEvent.STEP, tunnel, GameEvent.Emitter.of(player, null));
        Vec3d room = Vec3d.ofBottomCenter(s.piece.toWorld(WrathDenShape.LOCK.x() + 2, 1, 0));
        world.emitGameEvent(GameEvent.STEP, room, GameEvent.Emitter.of(player, null));
        ctx.assertEquals(s.den.stirring().stirs(), 0, "creative players, mobs and noise in the tunnels don't stir her");

        // Walking normally does
        step(world, player);
        ctx.assertEquals(s.den.stirring().stirs(), 1, "a footstep does");
        zombie.discard();
        world.getServer().getPlayerManager().remove(builder);
        finish(ctx, s);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void hurtingHerWakesHer(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup s = setup(ctx, 53248, BlockRotation.NONE);
        s.mother.damage(world.getDamageSources().playerAttack(s.player), 2f);
        ctx.assertFalse(s.mother.isDozing(), "a hit wakes her at once");
        ctx.assertTrue(s.mother.getAngerManager().getAngerFor(s.player) >= 80, "angry at whoever hit her");
        finish(ctx, s);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void sheForgetsWhoeverEscapesAndSettlesBackDown(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup s = setup(ctx, 57344, BlockRotation.CLOCKWISE_90);
        WrathDens.wake(s.den, s.mother, s.player);
        ctx.assertTrue(s.mother.getAngerManager().getAngerFor(s.player) >= 80, "awake and angry");

        // Into the portal room: out of her hall, out of her mind
        BlockPos room = s.piece.toWorld(WrathDenShape.LOCK.x() + 3, 1, 3);
        s.player.refreshPositionAndAngles(room.getX() + 0.5, room.getY(), room.getZ() + 0.5, 0, 0);
        WrathDens.update(s.den);
        ctx.assertEquals(s.mother.getAngerManager().getAngerFor(s.player), 0, "she forgets anyone who leaves the hall");
        ctx.assertFalse(s.den.showsBar(s.player), "no boss bar outside the hall");

        // Back to her nest, and to sleep, every stir forgotten
        WrathDens.settle(s.den, s.mother);
        ctx.assertTrue(s.mother.sleepPhase() == MotherWardenEntity.DROWSY, "she nods off");
        ctx.assertEquals(s.den.stirring().stirs(), 0, "stirs forgotten");
        ctx.runAtTick(MotherWardenEntity.DROWSY_TICKS + 5, () -> {
            ctx.assertTrue(s.mother.sleepPhase() == MotherWardenEntity.ASLEEP, "and is asleep two seconds later");
            ctx.assertTrue(s.mother.getBlockPos().equals(s.piece.nest()), "in her nest");
            // Everyone leaves: she goes too
            s.player.refreshPositionAndAngles(s.piece.center().getX() + 1000, 200, s.piece.center().getZ(), 0, 0);
            WrathDens.update(s.den);
            ctx.assertTrue(s.mother.isRemoved(), "she's gone once nobody is near");
            world.getServer().getPlayerManager().remove(s.player);
            unload(world, s.piece);
            ctx.complete();
        });
    }

    /** Awake, she's a warden on the hunt: she roars at whoever woke her and comes after them. She doesn't dig away. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void sheHuntsWhoeverWokeHer(TestContext ctx) {
        Setup s = setup(ctx, 65536, BlockRotation.NONE);
        ServerPlayerEntity player = toughen(s.player);
        MotherWardenEntity mother = s.mother;
        double start = mother.distanceTo(player);
        WrathDens.wake(s.den, mother, player);
        double[] closest = {start};
        // Checked every tick until it's all happened (the test passes then), or the time runs out
        ctx.addInstantFinalTask(() -> {
            ctx.assertFalse(mother.isRemoved() || mother.isInPose(EntityPose.DIGGING), "she never digs away");
            ctx.assertFalse(mother.getBrain().hasActivity(Activity.DIG), "she doesn't even think of digging");
            closest[0] = Math.min(closest[0], mother.distanceTo(player));
            ctx.assertTrue(mother.getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player, "she goes for the player" + state(s, mother));
            ctx.assertTrue(start - closest[0] >= 10, "she comes after them (from " + start + ", closest " + closest[0] + ")");
            ctx.assertTrue(player.getHealth() <= 1000 - 40, "and hits them hard" + state(s, mother));
            ctx.assertTrue(s.den.showsBar(player), "with her boss bar up");
            cleanUp(ctx, s);
        });
    }

    /**
     * Whoever she can't reach (up on the balcony, far over her head) gets her sonic boom, from much further off than a
     * warden's, and harder.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void sheSonicBoomsWhoeverSheCantReach(TestContext ctx) {
        Setup s = setup(ctx, 73728, BlockRotation.COUNTERCLOCKWISE_90);
        ServerPlayerEntity player = toughen(s.player);
        BlockPos balcony = s.piece.toWorld(WrathDenShape.BALCONY_INNER + 2, WrathDenShape.BALCONY + 1, 3);
        player.refreshPositionAndAngles(balcony.getX() + 0.5, balcony.getY(), balcony.getZ() + 0.5, 0, 0);
        MotherWardenEntity mother = s.mother;
        ctx.assertTrue(player.getY() - mother.getY() > 20 * 1.5, "the balcony is far beyond a warden's boom");
        WrathDens.wake(s.den, mother, player);
        float[] boom = {0};
        ctx.addInstantFinalTask(() -> {
            DamageSource last = player.getRecentDamageSource();
            if (boom[0] == 0 && last != null && last.isOf(DamageTypes.SONIC_BOOM)) boom[0] = 1000 - player.getHealth();
            ctx.assertTrue(boom[0] > 0, "her sonic boom reaches the balcony" + state(s, mother));
            ctx.assertTrue(boom[0] >= 10 * MotherWardenEntity.BOOM_DAMAGE - 0.5f, "harder than a warden's, got " + boom[0]);
            ctx.assertFalse(mother.isRemoved(), "still about");
            cleanUp(ctx, s);
        });
    }

    /** Lots of health, so a test player survives long enough to see what she does. */
    private static ServerPlayerEntity toughen(ServerPlayerEntity player) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        return player;
    }

    private static String state(Setup s, MotherWardenEntity mother) {
        return " (activities " + mother.getBrain().getPossibleActivities() + ", pose " + mother.getPose() + ", anger "
                + mother.getAngerManager().getAngerFor(s.player) + ", at " + s.piece.toLocal(mother.getBlockPos()) + ", player health " + s.player.getHealth() + ")";
    }

    /**
     * Her boss bar is only up while she's awake and the player is in the hall with her. If she's gone (other than
     * killed) she's back in her nest, asleep; killed, she stays dead and the bar goes with her.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void herBossBarGoesWhenSheDoes(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup s = setup(ctx, 69632, BlockRotation.CLOCKWISE_180);
        WrathDens.wake(s.den, s.mother, s.player);
        WrathDens.update(s.den);
        ctx.assertTrue(s.den.showsBar(s.player), "bar up once she's awake");

        // Out into the portal room: no bar there, and she doesn't dig away for want of anyone to hunt
        BlockPos room = s.piece.toWorld(WrathDenShape.LOCK.x() + 3, 1, 3);
        s.player.refreshPositionAndAngles(room.getX() + 0.5, room.getY(), room.getZ() + 0.5, 0, 0);
        WrathDens.update(s.den);
        ctx.assertFalse(s.den.showsBar(s.player), "no bar outside the hall");
        ctx.runAtTick(100, () -> {
            ctx.assertFalse(s.mother.isRemoved(), "still about, not dug away");
            ctx.assertTrue(s.mother.getBrain().hasMemoryModule(MemoryModuleType.DIG_COOLDOWN), "never ready to dig");

            // Back in, and she vanishes some other way: she's back in her nest, asleep, and the bar goes
            BlockPos hall = s.piece.toWorld(30, 1, -10);
            s.player.refreshPositionAndAngles(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, 0, 0);
            WrathDens.update(s.den);
            ctx.assertTrue(s.den.showsBar(s.player), "bar up again back in the hall");
            s.mother.discard();
            WrathDens.update(s.den);
            ctx.assertFalse(s.den.showsBar(s.player), "no bar once she's gone");
            MotherWardenEntity back = WrathDens.mother(s.den);
            ctx.assertTrue(back != null && back != s.mother && back.isDozing() && back.getBlockPos().equals(s.piece.nest()), "she's back in her nest, asleep");

            // Killed: the bar goes, and she stays dead while anyone's about
            WrathDens.wake(s.den, back, s.player);
            WrathDens.update(s.den);
            ctx.assertTrue(s.den.showsBar(s.player), "bar up for the fight");
            back.kill();
            ctx.assertFalse(s.den.showsBar(s.player), "bar gone when she dies");
        });
        ctx.runAtTick(160, () -> {
            WrathDens.update(s.den);
            ctx.assertTrue(WrathDens.mother(s.den) == null, "she stays dead");
            ctx.assertEquals(world.getEntitiesByClass(MotherWardenEntity.class, net.minecraft.util.math.Box.from(s.den.box()), e -> !e.isRemoved()).size(), 0, "no new mother");
            ctx.assertFalse(s.den.showsBar(s.player), "and no bar");
            finish(ctx, s);
        });
    }

    // ---- The hall's rules ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void noBuildingDiggingOrPearlsInTheHall(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        Setup s = setup(ctx, 61440, BlockRotation.COUNTERCLOCKWISE_90);
        ServerPlayerEntity player = s.player;
        StatusEffectInstance fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
        ctx.assertTrue(fatigue != null && fatigue.getAmplifier() == WrathDens.FATIGUE, "Mining Fatigue X in the hall");
        ctx.assertTrue(player.hasStatusEffect(ModEffects.BLOCK_BARRIER) && player.hasStatusEffect(ModEffects.ITEM_BARRIER), "Block Barrier and Item Barrier");
        BlockPos floor = player.getBlockPos().down();
        ctx.assertTrue(player.isBlockBreakingRestricted(world, floor, GameMode.SURVIVAL), "can't break blocks in the hall");
        ctx.assertTrue(ModEffects.blocksUsing(player, new net.minecraft.item.ItemStack(net.minecraft.item.Items.ENDER_PEARL)), "can't throw pearls");

        // Nothing of the kind in the portal room
        ServerPlayerEntity resting = survivalPlayer(world, s.piece.toWorld(WrathDenShape.LOCK.x() + 3, 1, -3), false);
        WrathDens.update(s.den);
        ctx.assertFalse(resting.hasStatusEffect(ModEffects.BLOCK_BARRIER) || resting.hasStatusEffect(StatusEffects.MINING_FATIGUE), "no rules in the portal room");
        ctx.assertFalse(resting.isBlockBreakingRestricted(world, resting.getBlockPos().down(), GameMode.SURVIVAL), "can dig in the portal room");
        world.getServer().getPlayerManager().remove(resting);
        finish(ctx, s);
    }
}
