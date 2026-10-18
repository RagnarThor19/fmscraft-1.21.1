package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.entity.DecoratedPotBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
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
import net.minecraft.structure.StructureTemplate;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
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
import net.ragnar.fmscraft.entity.GreedStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.greed.GreedVaultLayout;
import net.ragnar.fmscraft.structure.greed.GreedVaultPiece;
import net.ragnar.fmscraft.structure.greed.GreedVaults;

import java.util.List;
import java.util.UUID;

/**
 * Each vault test builds a whole Greed Vault far from the others, high in the air, and registers it as a structure
 * start the way world generation does, so the vault's logic finds it like a generated one.
 */
public class GreedGameTests implements FabricGameTest {
    private static final int Y = 100;

    private static GreedVaultPiece build(TestContext ctx, int at, BlockRotation rotation) {
        ServerWorld world = ctx.getWorld();
        BlockPos center = new BlockPos(at, Y, at);
        GreedVaultPiece piece = new GreedVaultPiece(world.getStructureTemplateManager(), center.subtract(GreedVaultLayout.PIVOT), rotation);
        Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(GreedVaults.VAULT);
        ChunkPos home = new ChunkPos(center);
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
                world.setChunkForced(cx, cz, true);
                Chunk chunk = world.getChunk(cx, cz);
                ChunkPos pos = new ChunkPos(cx, cz);
                BlockBox chunkBox = new BlockBox(pos.getStartX(), world.getBottomY(), pos.getStartZ(), pos.getEndX(), world.getTopY() - 1, pos.getEndZ());
                piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), world.getRandom(),
                        chunkBox, pos, BlockPos.ORIGIN);
                chunk.addStructureReference(structure, home.toLong());
            }
        }
        world.getChunk(home.x, home.z).setStructureStart(structure, new StructureStart(structure, home, 0, new StructurePiecesList(List.of(piece))));
        return piece;
    }

    private static void unload(ServerWorld world, GreedVaultPiece piece) {
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) world.setChunkForced(cx, cz, false);
        }
    }

    /** Like TestContext's mock player, but in survival (that one always counts as creative, which the vault leaves alone). */
    private static ServerPlayerEntity survivalPlayer(ServerWorld world, BlockPos at) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-greedy-player");
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
        player.teleport(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return player;
    }

    private static void leave(ServerPlayerEntity player) {
        player.getServer().getPlayerManager().remove(player);
    }

    private static void setLever(ServerWorld world, BlockPos pos, boolean on) {
        BlockState state = world.getBlockState(pos);
        if (state.get(LeverBlock.POWERED) != on) ((LeverBlock) Blocks.LEVER).togglePower(state, world, pos, null);
    }

    private static void setLevers(ServerWorld world, GreedVaultPiece piece, int... on) {
        List<BlockPos> levers = piece.levers();
        for (int i = 0; i < levers.size(); i++) {
            int number = i + 1;
            setLever(world, levers.get(i), java.util.Arrays.stream(on).anyMatch(n -> n == number));
        }
    }

    private static int ironInDoor(ServerWorld world, GreedVaultPiece piece) {
        int iron = 0;
        for (BlockPos pos : piece.door()) if (world.getBlockState(pos).isOf(Blocks.IRON_BLOCK)) iron++;
        return iron;
    }

    private static boolean holdsClue(ServerWorld world, GreedVaultPiece.Clue clue) {
        if (world.getBlockEntity(clue.pos()) instanceof DecoratedPotBlockEntity pot) {
            return ItemStack.areItemsAndComponentsEqual(pot.getStack(), clue.book());
        }
        if (!(world.getBlockEntity(clue.pos()) instanceof Inventory inventory)) return false;
        for (int i = 0; i < inventory.size(); i++) {
            if (ItemStack.areItemsAndComponentsEqual(inventory.getStack(i), clue.book())) return true;
        }
        return false;
    }

    // ---- The structure ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void vaultIsBuiltWithItsPuzzle(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockRotation rotation = BlockRotation.CLOCKWISE_90;
        GreedVaultPiece piece = build(ctx, 53248, rotation);

        for (BlockPos lever : piece.levers()) {
            BlockState state = world.getBlockState(lever);
            ctx.assertTrue(state.isOf(Blocks.LEVER) && !state.get(LeverBlock.POWERED), "an unpulled lever at " + lever + ", got " + state);
        }
        ctx.assertEquals(ironInDoor(world, piece), 16, "a 4x4 iron door");
        for (BlockPos home : piece.statueHomes()) {
            ctx.assertTrue(world.getBlockState(home.down()).isOf(Blocks.SMOOTH_STONE) && world.getBlockState(home).isAir(),
                    "smooth stone for a statue to rise from at " + home);
        }

        // The Greed lock before the empty 3x3 of the portal frame, turned with the vault
        BlockState lock = world.getBlockState(piece.lock());
        ctx.assertTrue(lock.isOf(ModBlocks.SIN_PORTAL_FRAME) && lock.get(SinPortalFrameBlock.SIN) == Sin.GREED, "the Greed lock, got " + lock);
        Direction facing = lock.get(SinPortalFrameBlock.FACING);
        ctx.assertEquals(facing, rotation.rotate(GreedVaultLayout.LOCK_FACING), "lock facing");
        BlockPos portal = piece.lock().offset(facing.getOpposite(), SinPortalFrameBlock.PORTAL_DISTANCE);
        Direction across = facing.rotateYClockwise();
        for (int l = -1; l <= 1; l++) {
            for (int h = 0; h <= 2; h++) ctx.assertTrue(world.getBlockState(portal.offset(across, l).up(h)).isAir(), "the portal opening should be clear");
            ctx.assertFalse(world.getBlockState(portal.offset(across, l).down()).isAir(), "the frame has a floor");
            ctx.assertFalse(world.getBlockState(portal.offset(across, l).up(3)).isAir(), "the frame has a top");
        }
        for (int s : new int[]{-2, 2}) {
            for (int h = 0; h <= 2; h++) ctx.assertFalse(world.getBlockState(portal.offset(across, s).up(h)).isAir(), "the frame has sides");
        }

        // Every clue book is where it was built, with something written in it; the barrels have loot instead
        List<GreedVaultPiece.Clue> clues = piece.clues(world.getRegistryManager());
        ctx.assertEquals(clues.size(), 5, "clue books (two 5s, two 7s, a 1)");
        for (GreedVaultPiece.Clue clue : clues) {
            ctx.assertTrue(holdsClue(world, clue), "the clue at " + clue.pos() + " should be in place");
            ctx.assertTrue(clue.book().contains(net.minecraft.component.DataComponentTypes.WRITABLE_BOOK_CONTENT), "a written clue at " + clue.pos());
        }
        StructureTemplate template = piece.getTemplate();
        int barrels = 0;
        for (StructureTemplate.StructureBlockInfo info : template.getInfosForBlock(piece.getPos(), GreedVaultPiece.placement(rotation), Blocks.BARREL)) {
            barrels++;
            ctx.assertTrue(world.getBlockEntity(info.pos()) instanceof LootableContainerBlockEntity barrel
                    && GreedVaultPiece.BARREL_LOOT.equals(barrel.getLootTable()), "barrel loot at " + info.pos());
        }
        ctx.assertTrue(barrels >= 20, "the vault's barrels, got " + barrels);
        for (BlockPos clue : clues.stream().map(GreedVaultPiece.Clue::pos).toList()) {
            if (world.getBlockEntity(clue) instanceof LootableContainerBlockEntity container) {
                ctx.assertTrue(container.getLootTable() == null, "clue chests don't get a loot table, at " + clue);
            }
        }

        // A ladder all the way up the pipe, open to the sky
        for (int y = 1; y < GreedVaultLayout.SIZE_Y; y++) {
            ctx.assertTrue(world.getBlockState(piece.toWorld(new BlockPos(75, y, 14))).isOf(Blocks.LADDER), "ladder up the pipe at y " + y);
        }
        ctx.assertTrue(world.getBlockState(piece.toWorld(GreedVaultLayout.PIPE_CENTER)).isAir(), "the pipe is open");

        // World and template coordinates agree both ways
        for (BlockPos local : List.of(GreedVaultLayout.LOCK, GreedVaultLayout.PIPE_CENTER, new BlockPos(0, 0, 0), new BlockPos(76, 14, 34))) {
            ctx.assertEquals(piece.toLocal(piece.toWorld(local)), local, "round trip of " + local);
        }
        unload(world, piece);
        ctx.complete();
    }

    // ---- The puzzle ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 500)
    public void onlyLevers157OpenTheDoor(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        GreedVaultPiece piece = build(ctx, 28672, BlockRotation.NONE);
        ServerPlayerEntity player = survivalPlayer(world, piece.toWorld(new BlockPos(25, 1, 15)));

        setLevers(world, piece, 1, 5);
        ctx.runAtTick(30, () -> {
            ctx.assertEquals(ironInDoor(world, piece), 16, "1 and 5 alone don't open it");
            ctx.assertEquals(GreedVaults.statues(GreedVaults.vault(world, piece)).size(), 3, "three statues rose once someone came");
            // One the vault has lost track of (say everyone teleported away and back): solving must clear it too
            BlockPos home = piece.statueHomes().get(1);
            GreedStatueEntity stray = ModEntities.GREED_STATUE.create(world);
            stray.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
            stray.bind(piece.getBoundingBox(), home);
            world.spawnEntity(stray);
            setLevers(world, piece, 1, 2, 5, 7);
        });
        ctx.runAtTick(60, () -> {
            ctx.assertEquals(ironInDoor(world, piece), 16, "1, 2, 5 and 7 don't open it");
            setLevers(world, piece, 1, 2, 3, 4, 5, 6, 7, 8);
        });
        ctx.runAtTick(90, () -> {
            ctx.assertEquals(ironInDoor(world, piece), 16, "all eight don't open it");
            setLevers(world, piece, 1, 5, 7);
        });
        ctx.runAtTick(115, () -> ctx.assertTrue(ironInDoor(world, piece) < 16, "1, 5 and 7 start the door sinking"));
        ctx.runAtTick(170, () -> {
            for (BlockPos pos : piece.door()) ctx.assertTrue(world.getBlockState(pos).isAir(), "the door has sunk away at " + pos);
            // Solved: the statues crumbled and the vault's weight lifted
            ctx.assertTrue(GreedVaults.statues(GreedVaults.vault(world, piece)).isEmpty(), "the statues are gone once it's solved");
            ctx.assertTrue(world.getEntitiesByClass(GreedStatueEntity.class, net.minecraft.util.math.Box.from(piece.getBoundingBox()), e -> true).isEmpty(),
                    "no statue left standing");
            ctx.assertFalse(player.hasStatusEffect(ModEffects.BLOCK_BARRIER), "no more Block Barrier");
            ctx.assertFalse(player.hasStatusEffect(StatusEffects.MINING_FATIGUE), "no more Mining Fatigue");
            ctx.assertFalse(player.hasStatusEffect(StatusEffects.SLOWNESS), "no more Slowness");
            // Once open it stays open, even if the levers change
            setLevers(world, piece);
        });
        ctx.runAtTick(200, () -> {
            ctx.assertEquals(ironInDoor(world, piece), 0, "the door stays open while people are here");
            ctx.assertTrue(GreedVaults.statues(GreedVaults.vault(world, piece)).isEmpty(), "and no statues rise again");
            ctx.assertFalse(player.hasStatusEffect(ModEffects.BLOCK_BARRIER), "nor do the effects come back");
            leave(player);
            unload(world, piece);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void nobodyIsEverShutInBehindTheDoor(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        GreedVaultPiece piece = build(ctx, 32768, BlockRotation.COUNTERCLOCKWISE_90);
        // Back in the portal room after the vault reset (or logged out in there): the door lets them out
        ServerPlayerEntity player = survivalPlayer(world, piece.toWorld(new BlockPos(8, 1, 16)));
        ctx.runAtTick(120, () -> {
            ctx.assertEquals(ironInDoor(world, piece), 0, "the door opens for someone inside");
            leave(player);
            unload(world, piece);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void vaultResetsOnceEveryoneHasLeft(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        GreedVaultPiece piece = build(ctx, 36864, BlockRotation.CLOCKWISE_180);
        List<GreedVaultPiece.Clue> clues = piece.clues(world.getRegistryManager());

        // The last group left it open, levers pulled and the clues taken
        for (BlockPos pos : piece.door()) world.setBlockState(pos, Blocks.AIR.getDefaultState());
        setLevers(world, piece, 1, 5, 7);
        for (GreedVaultPiece.Clue clue : clues) {
            if (world.getBlockEntity(clue.pos()) instanceof DecoratedPotBlockEntity pot) pot.setStack(ItemStack.EMPTY);
            else if (world.getBlockEntity(clue.pos()) instanceof Inventory inventory) inventory.clear();
        }

        // (update() does this once nobody is near and no Greed fight is on; the fight tests may be running one)
        GreedVaults.reset(GreedVaults.vault(world, piece));
        ctx.assertEquals(ironInDoor(world, piece), 16, "the door is shut again");
        for (BlockPos lever : piece.levers()) ctx.assertFalse(world.getBlockState(lever).get(LeverBlock.POWERED), "the levers are back off");
        for (GreedVaultPiece.Clue clue : clues) ctx.assertTrue(holdsClue(world, clue), "the clue at " + clue.pos() + " is back");

        // Running it again changes nothing (no extra copies)
        GreedVaults.reset(GreedVaults.vault(world, piece));
        for (GreedVaultPiece.Clue clue : clues) {
            if (world.getBlockEntity(clue.pos()) instanceof Inventory inventory && !(inventory instanceof DecoratedPotBlockEntity)) {
                int books = 0;
                for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(Items.WRITABLE_BOOK)) books++;
                ctx.assertEquals(books, 1, "one copy of the clue at " + clue.pos());
            }
        }
        unload(world, piece);
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void vaultWeighsOnThoseInsideAndItsStatuesSinkWhenTheyGo(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        GreedVaultPiece piece = build(ctx, 40960, BlockRotation.NONE);
        ServerPlayerEntity player = survivalPlayer(world, piece.toWorld(new BlockPos(25, 1, 15)));
        // Someone standing on the rim of the pipe, out of the vault proper
        ServerPlayerEntity climber = survivalPlayer(world, piece.toWorld(new BlockPos(71, GreedVaultLayout.SIZE_Y, 12)));
        climber.setNoGravity(true);
        ServerPlayerEntity builder = ctx.createMockCreativeServerPlayerInWorld();
        BlockPos builderAt = piece.toWorld(new BlockPos(24, 1, 14));
        builder.teleport(world, builderAt.getX() + 0.5, builderAt.getY(), builderAt.getZ() + 0.5, 0, 0);

        ctx.runAtTick(30, () -> {
            StatusEffectInstance fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
            StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
            ctx.assertTrue(fatigue != null && fatigue.getAmplifier() == 9, "Mining Fatigue X, got " + fatigue);
            ctx.assertTrue(slowness != null && slowness.getAmplifier() == 1, "Slowness II, got " + slowness);
            ctx.assertTrue(player.hasStatusEffect(ModEffects.BLOCK_BARRIER), "Block Barrier");
            ctx.assertFalse(builder.hasStatusEffect(ModEffects.BLOCK_BARRIER), "creative players are left alone");
            ctx.assertTrue(GreedVaults.inDarkness(player), "down in the vault: its fog");
            ctx.assertFalse(climber.hasStatusEffect(ModEffects.BLOCK_BARRIER), "nothing up at the pipe, so you can build up to it");
            ctx.assertFalse(climber.hasStatusEffect(StatusEffects.MINING_FATIGUE), "no fatigue up at the pipe");
            ctx.assertFalse(GreedVaults.inDarkness(climber), "no fog up at the pipe");
            leave(climber);
            ctx.assertEquals(GreedVaults.statues(GreedVaults.vault(world, piece)).size(), 3, "three statues");
            leave(player);
            builder.discard();
        });
        ctx.runAtTick(80, () -> {
            ctx.assertTrue(world.getEntitiesByClass(GreedStatueEntity.class, net.minecraft.util.math.Box.from(piece.getBoundingBox()), e -> true).isEmpty(),
                    "the statues sink back once everyone's gone");
            unload(world, piece);
            ctx.complete();
        });
    }

    // ---- Block Barrier ----

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blockBarrierStopsPlacingBlocksAndPouringBuckets(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
        BlockPos floor = ctx.getAbsolutePos(new BlockPos(2, 0, 2));
        BlockPos above = floor.up();
        world.setBlockState(floor, Blocks.STONE.getDefaultState());
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        player.setPosition(Vec3d.ofBottomCenter(above).add(4, 0, 0)); // out of the way of the block

        player.addStatusEffect(new StatusEffectInstance(ModEffects.BLOCK_BARRIER, 200));
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
        Items.COBBLESTONE.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        ctx.assertTrue(world.getBlockState(above).isAir(), "no block placed under Block Barrier");
        ctx.assertFalse(((BucketItem) Items.WATER_BUCKET).placeFluid(player, world, above, hit), "no water poured under Block Barrier");
        ctx.assertTrue(world.getBlockState(above).isAir(), "still nothing there");

        player.removeStatusEffect(ModEffects.BLOCK_BARRIER);
        Items.COBBLESTONE.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
        ctx.assertTrue(world.getBlockState(above).isOf(Blocks.COBBLESTONE), "placing works again once it wears off");
        ctx.complete();
    }

    // ---- The statues ----

    /** A long stone floor far from everything, for a statue to walk on. */
    private static BlockPos runway(ServerWorld world, int at) {
        BlockPos start = new BlockPos(at, 120, at);
        for (int cx = (at - 2) >> 4; cx <= (at + 20) >> 4; cx++) {
            for (int cz = (at - 2) >> 4; cz <= (at + 2) >> 4; cz++) world.setChunkForced(cx, cz, true);
        }
        for (int x = -2; x <= 20; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlockState(start.add(x, -1, z), Blocks.STONE.getDefaultState());
                for (int y = 0; y <= 3; y++) world.setBlockState(start.add(x, y, z), Blocks.AIR.getDefaultState());
            }
        }
        return start;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void statueCreepsSlowlyTowardPlayersAndCantBeHurt(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos start = runway(world, 45056);
        GreedStatueEntity statue = ModEntities.GREED_STATUE.create(world);
        statue.refreshPositionAndAngles(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        world.spawnEntity(statue);
        ServerPlayerEntity player = survivalPlayer(world, start.east(14));
        double before = statue.distanceTo(player);

        ServerPlayerEntity attacker = ctx.createMockCreativeServerPlayerInWorld();
        statue.damage(world.getDamageSources().playerAttack(attacker), 1000f);
        statue.damage(world.getDamageSources().explosion(null, null), 1000f);
        statue.damage(world.getDamageSources().lava(), 1000f);
        ctx.assertTrue(statue.isAlive() && statue.getHealth() == statue.getMaxHealth(), "a statue can't be hurt");

        ctx.runAtTick(240, () -> {
            double closed = before - statue.distanceTo(player);
            // About 0.25 blocks a second, so roughly 3 blocks in these 12 seconds
            ctx.assertTrue(closed > 1.5, "it should creep closer, closed " + closed);
            ctx.assertTrue(closed < 4.5, "but very slowly, closed " + closed);
            ctx.assertTrue(player.getHealth() == player.getMaxHealth(), "it hasn't reached the player yet");
            statue.kill();
            ctx.assertFalse(statue.isAlive(), "/kill still works on it");
            leave(player);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void statueCrushesAndThrowsWhatItTouches(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos start = runway(world, 49152);
        GreedStatueEntity statue = ModEntities.GREED_STATUE.create(world);
        statue.refreshPositionAndAngles(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        world.spawnEntity(statue);
        // An iron golem is tough enough to survive one touch, so we can see where it was thrown
        IronGolemEntity golem = EntityType.IRON_GOLEM.create(world);
        golem.refreshPositionAndAngles(start.getX() + 1.4, start.getY(), start.getZ() + 0.5, 0, 0);
        golem.setAiDisabled(true);
        world.spawnEntity(golem);
        ctx.runAtTick(5, () -> {
            ctx.assertTrue(golem.getHealth() <= golem.getMaxHealth() - GreedStatueEntity.DAMAGE + 0.01f,
                    "the golem takes the full " + GreedStatueEntity.DAMAGE + ", health " + golem.getHealth());
            ctx.assertTrue(golem.getVelocity().x > 0.5, "and is thrown away from the statue, velocity " + golem.getVelocity());
            statue.discard();
            golem.discard();
            ctx.complete();
        });
    }

    // ---- Loot ----

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void vaultBarrelsSometimesHaveATome(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        var loot = world.getServer().getReloadableRegistries().getLootTable(GreedVaultPiece.BARREL_LOOT);
        int[] found = new int[3];
        boolean[] other = {false};
        for (int i = 0; i < 6000; i++) {
            loot.generateLoot(new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ZERO)
                    .build(LootContextTypes.CHEST), stack -> {
                if (MagicTomes.of(WheelTier.BEGINNER).contains(stack.getItem())) found[0]++;
                else if (MagicTomes.of(WheelTier.ADVANCED).contains(stack.getItem())) found[1]++;
                else if (MagicTomes.of(WheelTier.MASTER).contains(stack.getItem())) found[2]++;
                else other[0] = true;
            });
        }
        int tomes = found[0] + found[1];
        ctx.assertTrue(tomes >= 80 && tomes <= 165, "expected a tome in ~2% of barrels, got " + tomes + "/6000");
        ctx.assertTrue(found[0] >= 25 && found[1] >= 25, "both beginner and advanced tomes, got " + found[0] + " and " + found[1]);
        ctx.assertEquals(found[2], 0, "no master tomes in barrels");
        ctx.assertTrue(other[0], "and a bit of other loot");
        ctx.complete();
    }
}
