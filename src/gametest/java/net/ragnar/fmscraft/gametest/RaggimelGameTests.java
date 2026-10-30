package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.greed.GreedArena;
import net.ragnar.fmscraft.boss.greed.GreedBubbleEntity;
import net.ragnar.fmscraft.boss.greed.RaggimelEntity;
import net.ragnar.fmscraft.compat.MagicSpells;
import net.ragnar.fmscraft.compat.MagicTomes;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.sin.Sin;

import java.util.UUID;

/**
 * RAGGIMEL, the Greed boss. The full fight test uses the real Greed arena (no other test opens a Greed fight); the
 * others put him on a little stone floor in the test world.
 */
public class RaggimelGameTests implements FabricGameTest {
    private static final int JOIN = 40;

    /** Like TestContext's mock player, but in survival: creative players aren't fought, bubbled or frozen. */
    private static ServerPlayerEntity survivalPlayer(ServerWorld world, Vec3d at) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-greed-player");
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
        player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        player.onTeleportationDone();
        return player;
    }

    private static void forceRing(ServerWorld arena, boolean force) {
        for (int cx = -3; cx <= 2; cx++) {
            for (int cz = -3; cz <= 2; cz++) arena.setChunkForced(cx, cz, force);
        }
    }

    private static void floor(TestContext ctx) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static RaggimelEntity spawn(TestContext ctx, BlockPos rel) {
        RaggimelEntity boss = ModBossEntities.RAGGIMEL.create(ctx.getWorld());
        boss.setSin(Sin.GREED);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        ctx.getWorld().spawnEntity(boss);
        return boss;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void greedFightPlaysTheIntroThenRaggimel(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ctx.assertTrue(BossFights.open(Sin.GREED, world, base, Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity player = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5))));
        BossFights.enter(player, Sin.GREED);
        ServerWorld arena = Arenas.world(server, Sin.GREED);
        // Mock players don't tick, so they don't keep the chunks around them loaded the way real ones do
        forceRing(arena, true);

        // The island is built with ground where everyone stands
        ctx.runAtTick(2, () -> {
            for (Vec3d spot : new Vec3d[]{GreedArena.PLAYER_SPAWN, GreedArena.LANDING}) {
                BlockPos feet = BlockPos.ofFloored(spot);
                ctx.assertTrue(arena.getBlockState(feet.down()).isSolidBlock(arena, feet.down()), "ground under " + spot);
                ctx.assertTrue(arena.getBlockState(feet).getCollisionShape(arena, feet).isEmpty()
                        && arena.getBlockState(feet.up()).isAir(), "room to stand at " + spot);
            }
            // The empty frame the exit portal will fill
            BlockPos portal = GreedArena.EXIT_PORTAL;
            ctx.assertTrue(arena.getBlockState(portal.down()).isOf(Blocks.DEEPSLATE_TILES), "frame under the exit portal");
            ctx.assertTrue(arena.getBlockState(portal.add(-1, -1, 0)).isOf(Blocks.GOLD_BLOCK), "gold in the frame's corner");
            ctx.assertTrue(arena.getBlockState(portal.add(2, 3, 0)).isOf(Blocks.GOLD_BLOCK), "gold in the frame's corner");
            for (int w = 0; w < 2; w++) {
                for (int h = 0; h < 3; h++) ctx.assertTrue(arena.getBlockState(portal.add(w, h, 0)).isAir(), "the frame starts empty");
            }
            ctx.assertTrue(arena.getBlockState(BlockPos.ofFloored(GreedArena.PERCH)).isAir(), "the perch is in the open air");
        });

        // Waiting in the intro: frozen in the arena, the first song already started
        ctx.runAtTick(JOIN / 2, () -> {
            ctx.assertTrue(player.getServerWorld() == arena, "player should be in the Greed arena");
            ctx.assertTrue(player.squaredDistanceTo(GreedArena.PLAYER_SPAWN) < 1, "player should arrive at the spawn");
            ctx.assertTrue(Cinematics.isFrozen(player), "player should be frozen during the intro");
            ctx.assertTrue(BossFights.session(Sin.GREED).song() == RaggimelEntity.PHASE_ONE, "Just The Way You Are should be playing");
        });

        // The fight: RAGGIMEL is up and everyone can move
        ctx.runAtTick(JOIN + 10, () -> {
            FightSession session = BossFights.session(Sin.GREED);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(arena.getEntity(session.bossId()) instanceof RaggimelEntity, "the boss should be RAGGIMEL");
            ctx.assertFalse(Cinematics.isFrozen(player), "player should be free once the fight starts");
        });

        // At 70% the phase change starts: he can't be hurt and everyone is frozen in the dark
        ctx.runAtTick(JOIN + 15, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            boss.setHealth(boss.getMaxHealth() * 0.69f);
        });
        ctx.runAtTick(JOIN + 20, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            ctx.assertEquals(boss.getPhase(), 2, "phase");
            ctx.assertTrue(boss.isUntouchable(), "he can't be hurt during the phase change");
            ctx.assertTrue(Cinematics.isFrozen(player), "players freeze in the dark");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health during the phase change");
            ctx.assertTrue(BossFights.session(Sin.GREED).song() == null, "the music stops");
        });

        // Falling off the island kills you (and ends the attempt)
        ctx.runAtTick(JOIN + 30, () -> {
            player.teleport(arena, GreedArena.PLAYER_SPAWN.x, GreedArena.VOID_Y - 5, GreedArena.PLAYER_SPAWN.z, 0, 0);
            player.onTeleportationDone();
        });
        ctx.runAtTick(JOIN + 34, () -> {
            ctx.assertTrue(BossFights.session(Sin.GREED) == null, "falling into the void should end the fight");
            ctx.assertFalse(Cinematics.isFrozen(player), "nobody stays frozen after a fight ends");
            server.getPlayerManager().remove(player);
            forceRing(arena, false);
            ctx.complete();
        });
    }

    /** A boss left over from before a crash (or anything else not belonging to a running fight) leaves its arena. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void aStrayBossLeavesItsArena(TestContext ctx) {
        ServerWorld arena = Arenas.world(ctx.getWorld().getServer(), Sin.ENVY);
        arena.setChunkForced(0, 0, true);
        Arenas.reset(arena, Sin.ENVY);
        Entity stray = ModBossEntities.forSin(Sin.ENVY).create(arena);
        stray.refreshPositionAndAngles(Arenas.BOSS_SPAWN.x, Arenas.BOSS_SPAWN.y, Arenas.BOSS_SPAWN.z, 0, 0);
        arena.spawnEntity(stray);
        ctx.runAtTick(30, () -> {
            ctx.assertTrue(stray.isRemoved(), "a boss with no fight running should be gone");
            arena.setChunkForced(0, 0, false);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void phaseOneCantBeSkippedWithOneHit(TestContext ctx) {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 6))));
        ctx.runAtTick(5, () -> {
            ctx.assertEquals(boss.currentAction(), "CHASE", "landed straight away on the floor");
            boss.setHealth(boss.getMaxHealth() * RaggimelEntity.PHASE_TWO_AT + 5);
            boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1000f);
            ctx.assertTrue(boss.getHealth() >= boss.getMaxHealth() * RaggimelEntity.PHASE_TWO_AT - 0.02f,
                    "one hit can't take him past 70%, got " + boss.getHealth());
            ctx.assertTrue(boss.getHealth() < boss.getMaxHealth() * RaggimelEntity.PHASE_TWO_AT + 5, "but it still hurts");
        });
        ctx.runAtTick(8, () -> {
            boss.setHealth(boss.getMaxHealth() * 0.9f);
            float before = boss.getHealth();
            boss.timeUntilRegen = 0;
            boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 200f);
            ctx.assertTrue(before - boss.getHealth() <= RaggimelEntity.MAX_HIT, "one hit takes at most " + RaggimelEntity.MAX_HIT);
            boss.discard();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    /** In frenzy his laser wants two different targets; alone, that once looped forever and froze the server. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void frenziedLaserWorksWithOnePlayer(TestContext ctx) throws ReflectiveOperationException {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 6))));
        java.lang.reflect.Field frenzy = RaggimelEntity.class.getDeclaredField("frenzy");
        frenzy.setAccessible(true);
        Class<?> action = Class.forName(RaggimelEntity.class.getName() + "$Action");
        java.lang.reflect.Method start = RaggimelEntity.class.getDeclaredMethod("startAttack", action);
        start.setAccessible(true);
        ctx.runAtTick(5, () -> {
            try {
                frenzy.setBoolean(boss, true);
                start.invoke(boss, Enum.valueOf(action.asSubclass(Enum.class), "LASER"));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        ctx.runAtTick(15, () -> {
            ctx.assertEquals(boss.currentAction(), "LASER", "action");
            ctx.assertTrue(boss.hasBeams(), "one laser at the only player");
            boss.discard();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void bubbleLiftsAPlayerUntilATeammatePopsIt(TestContext ctx) {
        floor(ctx);
        ServerWorld world = ctx.getWorld();
        Vec3d stand = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5)));
        ServerPlayerEntity caught = survivalPlayer(world, stand);
        ServerPlayerEntity friend = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(1, 1, 1))));
        GreedBubbleEntity bubble = GreedBubbleEntity.blow(world, stand.add(0, 1, -3), caught, 0.25, 4, 200);

        ctx.runAtTick(60, () -> {
            ctx.assertTrue(bubble.isHolding(caught), "the bubble should have caught the player");
            ctx.assertTrue(caught.getY() > stand.y + 2, "and lifted them, at " + caught.getY());
            ctx.assertFalse(bubble.damage(world.getDamageSources().playerAttack(caught), 1f), "can't pop your own bubble");
            ctx.assertTrue(bubble.damage(world.getDamageSources().playerAttack(friend), 1f), "a teammate pops it");
            ctx.assertTrue(bubble.isRemoved(), "popped");
            ctx.assertTrue(GreedBubbleEntity.bubbleOf(caught) == null, "and the player drops");
            world.getServer().getPlayerManager().remove(caught);
            world.getServer().getPlayerManager().remove(friend);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void raggimelCantBeCarriedOffInAMagicBubble(TestContext ctx) {
        if (!FabricLoader.getInstance().isModLoaded(MagicTomes.MOD_ID)) {
            ctx.complete();
            return;
        }
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        Entity bubble = MagicSpells.entity(MagicSpells.BUBBLE).map(t -> t.create(ctx.getWorld())).orElseThrow();
        bubble.refreshPositionAndAngles(boss.getX(), boss.getY(), boss.getZ(), 0, 0);
        ctx.getWorld().spawnEntity(bubble);
        ctx.runAtTick(12, () -> {
            ctx.assertTrue(bubble.isRemoved(), "RAGGIMEL bursts any bubble that gets him");
            boss.discard();
            ctx.complete();
        });
    }
}
