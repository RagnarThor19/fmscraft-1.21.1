package net.ragnar.fmscraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BellBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.network.ConnectedClientData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalFrameBlock;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.reward.WheelTier;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.sloth.Drowsiness;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape;
import net.ragnar.fmscraft.structure.sloth.SlothSphereShape.Spot;
import net.ragnar.fmscraft.structure.sloth.SlothSpherePiece;
import net.ragnar.fmscraft.structure.sloth.SlothSphereStructure;
import net.ragnar.fmscraft.structure.sloth.SlothSpheres;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Each test builds a whole sphere far from the others and registers it as a structure start, the way world
 * generation does, so the game finds it like a generated one (bells, drowsiness and all).
 */
public class SlothGameTests implements FabricGameTest {

    private static SlothSpherePiece build(TestContext ctx, int at, BlockRotation rotation) {
        ServerWorld world = ctx.getWorld();
        BlockPos center = new BlockPos(at, SlothSphereStructure.CENTER_Y, at);
        SlothSpherePiece piece = new SlothSpherePiece(42L, center, rotation);
        Structure structure = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(SlothSpheres.SPHERE);
        ChunkPos home = new ChunkPos(center);
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
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

    private static void forceLoad(ServerWorld world, SlothSpherePiece piece, boolean forced) {
        BlockBox box = piece.getBoundingBox();
        for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
            for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) world.setChunkForced(cx, cz, forced);
        }
    }

    /** Like TestContext's mock player, but in survival (that one always counts as creative, which the sphere leaves alone). */
    private static ServerPlayerEntity survivalPlayer(ServerWorld world) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-sleepy-player");
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
        return player;
    }

    private static void ring(ServerWorld world, BlockPos bell) {
        ((BellBlock) Blocks.BELL).ring(world, bell, null);
    }

    private static boolean sealShut(ServerWorld world, SlothSpherePiece piece) {
        for (BlockPos pos : piece.sealBlocks()) if (!world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN)) return false;
        return true;
    }

    private static boolean sealOpen(ServerWorld world, SlothSpherePiece piece) {
        for (BlockPos pos : piece.sealBlocks()) if (!world.getBlockState(pos).isAir()) return false;
        return true;
    }

    // ---- The structure ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1600)
    public void sphereOfSlothIsBuiltAroundItsCore(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        SlothSpherePiece piece = build(ctx, -20480, BlockRotation.CLOCKWISE_90);

        // The Sloth lock in the core, turned with the rest of the sphere, before a clear frame
        BlockState lock = world.getBlockState(piece.lock());
        ctx.assertTrue(lock.isOf(ModBlocks.SIN_PORTAL_FRAME) && lock.get(SinPortalFrameBlock.SIN) == Sin.SLOTH, "the Sloth lock should be in the core, got " + lock);
        ctx.assertEquals(lock.get(SinPortalFrameBlock.FACING), BlockRotation.CLOCKWISE_90.rotate(Direction.EAST), "lock facing");
        BlockPos portal = piece.lock().offset(lock.get(SinPortalFrameBlock.FACING).getOpposite(), SinPortalFrameBlock.PORTAL_DISTANCE);
        Direction across = lock.get(SinPortalFrameBlock.FACING).rotateYClockwise();
        for (int l = -1; l <= 1; l++) {
            for (int h = 0; h <= 2; h++) ctx.assertTrue(world.getBlockState(portal.offset(across, l).up(h)).isAir(), "the portal opening should be clear");
        }
        ctx.assertTrue(world.getBlockState(portal.offset(across, 2).down()).isOf(Blocks.CRYING_OBSIDIAN), "crying obsidian frame corner");

        // The seal is shut, with the pool to land in right under it
        ctx.assertTrue(sealShut(world, piece), "the seal should start shut");
        ctx.assertTrue(world.getBlockState(piece.toWorld(0, SlothSphereShape.CORE_FLOOR, 0)).isOf(Blocks.WATER), "pool under the seal");
        ctx.assertTrue(world.getBlockState(piece.toWorld(0, SlothSphereShape.CORE - 1, 0)).isAir(), "open drop under the seal");

        // The gateway in the core sends you out to the mouth
        BlockPos gateway = piece.gateway();
        ctx.assertTrue(world.getBlockEntity(gateway) instanceof EndGatewayBlockEntity, "end gateway in the core");
        Vec3d exit = ((EndGatewayBlockEntity) world.getBlockEntity(gateway)).getOrCreateExitPortalPos(world, gateway);
        ctx.assertTrue(exit != null && exit.squaredDistanceTo(piece.exit().toBottomCenterPos()) < 0.01, "the gateway should lead to the mouth, got " + exit);
        ctx.assertTrue(world.getBlockState(piece.exit()).isAir() && !world.getBlockState(piece.exit().down()).isAir(), "somewhere to stand at the exit");

        // The mouth is open through the shell, and the shell is shaded with many blocks
        for (int x = SlothSphereShape.INNER; x < SlothSphereShape.RADIUS; x++) {
            ctx.assertTrue(world.getBlockState(piece.toWorld(x, SlothSphereShape.MOUTH_FLOOR + 2, 0)).isAir(), "the mouth should be open");
        }
        Set<BlockState> shades = new HashSet<>();
        for (int a = 0; a < 360; a += 5) {
            for (int b = -80; b <= 80; b += 10) {
                double t = Math.toRadians(a), p = Math.toRadians(b), r = SlothSphereShape.RADIUS - 1.2;
                BlockState state = world.getBlockState(piece.toWorld((int) Math.round(r * Math.cos(p) * Math.cos(t)),
                        (int) Math.round(r * Math.sin(p)), (int) Math.round(r * Math.cos(p) * Math.sin(t))));
                if (!state.isAir()) shades.add(state);
            }
        }
        ctx.assertTrue(shades.size() >= 6, "the shell should be shaded with at least 6 blocks, got " + shades);

        // A bell, unlit candles and (except at the top) a chest at each of the four stations
        List<BlockPos> bells = piece.bells(), candles = piece.candles();
        for (int i = 0; i < bells.size(); i++) {
            ctx.assertTrue(world.getBlockState(bells.get(i)).isOf(Blocks.BELL), "bell " + i + " at " + bells.get(i));
            BlockState candle = world.getBlockState(candles.get(i));
            ctx.assertTrue(candle.isOf(Blocks.GRAY_CANDLE) && candle.get(CandleBlock.CANDLES) == 4 && !candle.get(CandleBlock.LIT), "unlit candles at bell " + i);
        }
        int chests = 0;
        for (Spot spot : SlothSphereShape.CHESTS) {
            if (world.getBlockEntity(piece.toWorld(spot)) instanceof LootableContainerBlockEntity c && c.getLootTable() == SlothSpherePiece.STAIR_LOOT) chests++;
        }
        ctx.assertEquals(chests, 3, "stair chests with loot");
        int core = 0;
        BlockPos lockPos = piece.lock();
        for (BlockPos pos : BlockPos.iterate(lockPos.add(-3, 0, -3), lockPos.add(3, 0, 3))) {
            if (world.getBlockEntity(pos) instanceof LootableContainerBlockEntity c && c.getLootTable() == SlothSpherePiece.CORE_LOOT) core++;
        }
        ctx.assertEquals(core, 2, "core chests with loot");

        // All the way up the stair there's a floor under its middle and room to walk over it
        int checked = 0;
        for (double u = 0; u <= SlothSphereShape.TURNS; u += 0.002) {
            double theta = Math.PI + Math.PI * 2 * u;
            int x = (int) Math.floor(SlothSphereShape.PATH_MID * Math.cos(theta) + 0.5), z = (int) Math.floor(SlothSphereShape.PATH_MID * Math.sin(theta) + 0.5);
            // The block rounded to may sit a step either side of the exact height at u
            BlockPos at = null;
            for (int y = (int) Math.floor(SlothSphereShape.surface(u)) + 1; y >= (int) Math.floor(SlothSphereShape.surface(u)) - 1 && at == null; y--) {
                BlockPos pos = piece.toWorld(x, y, z);
                BlockState floor = world.getBlockState(pos), below = world.getBlockState(pos.down());
                if (floor.isOf(Blocks.DEEPSLATE_TILE_SLAB) || (below.isOpaqueFullCube(world, pos.down()) && floor.getCollisionShape(world, pos).isEmpty())) at = pos;
            }
            ctx.assertTrue(at != null, "no floor on the stair at u=" + u + " " + piece.toWorld(x, (int) SlothSphereShape.surface(u), z));
            for (int h = 1; h <= 2; h++) {
                BlockState over = world.getBlockState(at.up(h));
                ctx.assertTrue(over.getCollisionShape(world, at.up(h)).isEmpty() || over.isOf(Blocks.BELL) || over.isOf(Blocks.CHEST),
                        "no headroom on the stair at " + at.up(h) + ": " + over);
            }
            checked++;
        }
        ctx.assertTrue(checked > 1000, "checked " + checked);

        // The Sloth key opens its portal. Another test holds a Sloth fight open for a while, so wait for it to end,
        // then open and end ours within the same tick
        ServerPlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
        boolean[] done = {false};
        ctx.runAtEveryTick(() -> {
            if (done[0] || BossFights.session(Sin.SLOTH) != null) return;
            done[0] = true;
            world.getBlockState(piece.lock()).onUseWithItem(new ItemStack(ModItems.KEYS.get(Sin.SLOTH)), world, player, Hand.MAIN_HAND,
                    new BlockHitResult(Vec3d.ofCenter(piece.lock()), Direction.UP, piece.lock(), false));
            ctx.assertTrue(BossFights.session(Sin.SLOTH) != null, "the Sloth key should open the fight");
            ctx.assertTrue(world.getBlockState(portal.up()).isOf(ModBlocks.SIN_PORTAL), "the portal should fill the frame");
            BossFights.forceEnd(world.getServer(), Sin.SLOTH);
            world.getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sphereChestsSometimesHaveTomes(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        int[] found = new int[3];
        var stair = world.getServer().getReloadableRegistries().getLootTable(SlothSpherePiece.STAIR_LOOT);
        var core = world.getServer().getReloadableRegistries().getLootTable(SlothSpherePiece.CORE_LOOT);
        for (int i = 0; i < 4000; i++) {
            LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ZERO).build(LootContextTypes.CHEST);
            stair.generateLoot(params, stack -> {
                if (MagicTomes.of(WheelTier.BEGINNER).contains(stack.getItem())) found[0]++;
            });
            core.generateLoot(params, stack -> {
                if (MagicTomes.of(WheelTier.ADVANCED).contains(stack.getItem())) found[1]++;
                if (MagicTomes.of(WheelTier.MASTER).contains(stack.getItem())) found[2]++;
            });
        }
        ctx.assertTrue(found[0] >= 140 && found[0] <= 260, "expected ~5% beginner tomes on the stair, got " + found[0] + "/4000");
        ctx.assertTrue(found[1] >= 320 && found[1] <= 480, "expected ~10% advanced tomes in the core, got " + found[1] + "/4000");
        ctx.assertTrue(found[2] >= 40 && found[2] <= 130, "expected ~2% master tomes in the core, got " + found[2] + "/4000");
        ctx.complete();
    }

    // ---- The bells ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void ringingAllFourBellsInTimeOpensTheCore(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        SlothSpherePiece piece = build(ctx, -16384, BlockRotation.CLOCKWISE_180);
        List<BlockPos> bells = piece.bells(), candles = piece.candles();
        SlothSpheres.Sphere sphere = SlothSpheres.sphere(world, piece);
        long now = world.getTime();

        // A bell lights its candles, which burn down one at a time
        ring(world, bells.get(0));
        ctx.assertTrue(world.getBlockState(candles.get(0)).get(CandleBlock.LIT), "ringing a bell lights its candles");
        sphere.setRungAt(0, now - SlothSpheres.CANDLE_TICKS * 2 - 1);
        SlothSpheres.update(sphere);
        BlockState burning = world.getBlockState(candles.get(0));
        ctx.assertTrue(burning.get(CandleBlock.LIT) && burning.get(CandleBlock.CANDLES) == 2, "half burnt down: " + burning);

        // The first bell has gone quiet by the time the other three ring: the seal holds
        sphere.setRungAt(0, now - SlothSpheres.BELL_TICKS - 1);
        for (int i = 1; i < 4; i++) ring(world, bells.get(i));
        ctx.assertTrue(sealShut(world, piece), "three ringing bells shouldn't open the seal");
        SlothSpheres.update(sphere);
        ctx.assertFalse(world.getBlockState(candles.get(0)).get(CandleBlock.LIT), "the first bell's candles have burnt out");

        // Ring it again while the others still ring, and the seal crumbles
        ring(world, bells.get(0));
        ctx.assertTrue(sealOpen(world, piece), "four ringing bells should open the seal");

        // Ringing some other bell does nothing
        BlockPos stray = piece.toWorld(0, SlothSphereShape.TOP, 6);
        world.setBlockState(stray, Blocks.BELL.getDefaultState());
        ring(world, stray);

        // With nobody near, the seal closes again and the bells go quiet
        SlothSpheres.update(sphere);
        ctx.assertTrue(sealShut(world, piece), "the seal should close once nobody is near");
        for (int i = 0; i < 4; i++) {
            ctx.assertFalse(sphere.ringing(i), "bell " + i + " should be quiet again");
            ctx.assertFalse(world.getBlockState(candles.get(i)).get(CandleBlock.LIT), "candles out at bell " + i);
        }
        ctx.complete();
    }

    // ---- Drowsiness ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void drowsinessCreepsUpUntilYouNodOffAndABellWakesYou(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        SlothSpherePiece piece = build(ctx, -12288, BlockRotation.NONE);
        forceLoad(world, piece, true);
        Spot bell = SlothSphereShape.BELLS.get(1);
        int[] dx = {1, 0, -1, 0}, dz = {0, 1, 0, -1};
        BlockPos stand = piece.toWorld(bell.x() + 2 * dx[bell.dir()], bell.y(), bell.z() + 2 * dz[bell.dir()]);
        ServerPlayerEntity player = survivalPlayer(world);
        player.refreshPositionAndAngles(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);

        ctx.runAtTick(50, () -> {
            Drowsiness drowsiness = SlothSpheres.drowsiness(player);
            ctx.assertTrue(drowsiness != null && drowsiness.value() > 0, "drowsiness should build up inside the sphere");
            ctx.assertTrue(SlothSpheres.showsDrowsiness(player), "the drowsiness bar should show");
            StatusEffectInstance fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
            ctx.assertTrue(fatigue != null && fatigue.getAmplifier() == 2, "the sphere gives Mining Fatigue III");
            drowsiness.set(Drowsiness.FULL - 0.01f);
        });
        ctx.runAtTick(55, () -> {
            StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
            ctx.assertTrue(slowness != null && slowness.getAmplifier() >= 6, "asleep: can't move, got " + slowness);
            ctx.assertTrue(player.hasStatusEffect(StatusEffects.BLINDNESS), "asleep: can't see");
            ctx.assertEquals(SlothSpheres.drowsiness(player).value(), Drowsiness.AFTER_NAP, "drowsiness after a nap");
            ring(world, piece.bells().get(1));
            ctx.assertTrue(SlothSpheres.drowsiness(player).value() < 0.01f, "the bell should wake you right up");
            ctx.assertFalse(player.hasStatusEffect(StatusEffects.SLOWNESS), "awake: free to move");
            ctx.assertFalse(player.hasStatusEffect(StatusEffects.BLINDNESS), "awake: can see");
        });
        ctx.runAtTick(60, () -> {
            // Out of the sphere, the bar goes and drowsiness is forgotten
            player.refreshPositionAndAngles(stand.getX() + 200, stand.getY(), stand.getZ(), 0, 0);
        });
        ctx.runAtTick(65, () -> {
            ctx.assertTrue(SlothSpheres.drowsiness(player) == null, "drowsiness is forgotten outside");
            ctx.assertFalse(SlothSpheres.showsDrowsiness(player), "no bar outside");
            world.getServer().getPlayerManager().remove(player);
            Box box = Box.from(Vec3d.ofCenter(piece.center())).expand(SlothSphereShape.RADIUS + 16);
            world.getEntitiesByType(EntityType.PHANTOM, box, e -> true).forEach(e -> e.discard());
            forceLoad(world, piece, false);
            ctx.complete();
        });
    }
}
