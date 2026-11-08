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
import net.ragnar.fmscraft.boss.greed.GreedManor;
import net.ragnar.fmscraft.boss.greed.GreedTimeline;
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
        // And the manor's garden, where the tide carries everyone
        int mx = (int) GreedManor.CENTER.x >> 4, mz = (int) GreedManor.CENTER.z >> 4;
        for (int cx = mx - 2; cx <= mx + 2; cx++) {
            for (int cz = mz - 2; cz <= mz + 2; cz++) arena.setChunkForced(cx, cz, force);
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

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700)
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
            // The manor, built when the server started, with ground where everyone surfaces and where he comes up
            ctx.assertTrue(GreedManor.built(arena), "the manor is there");
            for (Vec3d spot : GreedManor.arrivals(3)) {
                BlockPos feet = BlockPos.ofFloored(spot);
                ctx.assertTrue(arena.getBlockState(feet.down()).isSolidBlock(arena, feet.down()), "manor ground under " + spot);
                ctx.assertTrue(arena.getBlockState(feet).isAir() && arena.getBlockState(feet.up()).isAir(), "room to stand at " + spot);
            }
            ctx.assertTrue(arena.getBlockState(BlockPos.ofFloored(GreedManor.CENTER).down()).isOf(Blocks.STONE_BRICKS), "the path between the fountains");
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

        // At 70% the phase change starts: he can't be hurt and everyone is held where they stand, watching
        int phase = JOIN + 15;
        ctx.runAtTick(phase, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            boss.setHealth(boss.getMaxHealth() * 0.69f);
        });
        ctx.runAtTick(JOIN + 20, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            ctx.assertEquals(boss.getPhase(), 2, "phase");
            ctx.assertTrue(boss.isUntouchable(), "he can't be hurt during the phase change");
            ctx.assertTrue(Cinematics.isFrozen(player), "players are held");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health during the phase change");
            ctx.assertTrue(BossFights.session(Sin.GREED).song() == null, "the music stops");
        });

        // The tide: the spout rising round him, the wave rolling in, everyone under, carried to the manor
        ctx.runAtTick(phase + GreedTimeline.WAVE_AT - 10, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            ctx.assertTrue(water(arena, boss.getPos(), 6) >= 30, "a waterspout round him, " + water(arena, boss.getPos(), 6));
        });
        ctx.runAtTick(phase + GreedTimeline.WAVE_AT + 10, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            ctx.assertTrue(water(arena, boss.getPos(), 60) >= 30 + 40, "and a wall of water rolling over the island");
        });
        ctx.runAtTick(phase + GreedTimeline.RESHAPE_AT + 4, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            ctx.assertTrue(GreedManor.contains(player.getPos()), "carried to the manor, at " + player.getPos());
            ctx.assertTrue(player.getPos().distanceTo(GreedManor.PLAYER_ARC) < 3, "surfacing south of him");
            ctx.assertTrue(Cinematics.isFrozen(player), "still held while it plays");
            ctx.assertTrue(boss != null && GreedManor.contains(boss.getPos()), "and him with them");
            ctx.assertTrue(boss.getScale() > 1.4f, "grown");
        });
        ctx.runAtTick(phase + GreedTimeline.SONG_AT + 4, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            ctx.assertFalse(Cinematics.isFrozen(player), "free when the spout bursts");
            ctx.assertTrue(BossFights.session(Sin.GREED).song() == RaggimelEntity.PHASE_TWO, "Panzerknacker starts");
            ctx.assertTrue(Math.abs(boss.getY() - GreedManor.CENTER.y) < 1, "down on the garden, at " + boss.getY());
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "none of it hurts");
        });
        ctx.runAtTick(phase + GreedTimeline.TRANSITION_END + 4, () -> {
            RaggimelEntity boss = (RaggimelEntity) arena.getEntity(BossFights.session(Sin.GREED).bossId());
            ctx.assertFalse(boss.isUntouchable(), "the fight is on again");
        });

        // Falling off the manor's grounds kills you (and ends the attempt)
        int fall = phase + GreedTimeline.TRANSITION_END + 10;
        ctx.runAtTick(fall, () -> {
            player.teleport(arena, GreedManor.CENTER.x, GreedManor.VOID_Y - 5, GreedManor.CENTER.z, 0, 0);
            player.onTeleportationDone();
        });
        ctx.runAtTick(fall + 4, () -> {
            ctx.assertTrue(BossFights.session(Sin.GREED) == null, "falling into the void should end the fight");
            ctx.assertFalse(Cinematics.isFrozen(player), "nobody stays frozen after a fight ends");
            server.getPlayerManager().remove(player);
            forceRing(arena, false);
            ctx.complete();
        });
    }

    /** His solid water (GreedWater.TAG) within {@code radius} of {@code at}. */
    private static int water(ServerWorld world, Vec3d at, double radius) {
        return world.getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                new net.minecraft.util.math.Box(at, at).expand(radius), e -> e.getCommandTags().contains("fmscraft.greed_water")).size();
    }

    private static void start(RaggimelEntity boss, String attack) {
        try {
            Class<?> action = Class.forName(RaggimelEntity.class.getName() + "$Action");
            java.lang.reflect.Method method = RaggimelEntity.class.getDeclaredMethod("startAttack", action);
            method.setAccessible(true);
            method.invoke(boss, Enum.valueOf(action.asSubclass(Enum.class), attack));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Anvil Slam: an anvil shaking over you, then down on you; step out and it misses. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "raggimel_anvil")
    public void anvilSlamComesDownOnWhoeverStaysUnder(TestContext ctx) {
        floor(ctx);
        ServerWorld world = ctx.getWorld();
        RaggimelEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity stays = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 2))));
        ServerPlayerEntity steps = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(2, 1, 6))));
        ctx.runAtTick(5, () -> start(boss, "ANVIL_SLAM"));
        ctx.runAtTick(5 + 12, () -> {
            var anvils = world.getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                    new net.minecraft.util.math.Box(stays.getPos(), stays.getPos()).expand(3, 12, 3), e -> e.getBlockState().isOf(Blocks.ANVIL));
            ctx.assertTrue(!anvils.isEmpty() && anvils.get(0).getY() > stays.getY() + 6, "an anvil hanging high over them");
        });
        // The anvil follows a moment, then locks: one of them steps out from under it before that
        ctx.runAtTick(5 + 24, () -> {
            Vec3d out = steps.getPos().add(3.5, 0, 0);
            steps.teleport(world, out.x, out.y, out.z, 0, 0);
            steps.onTeleportationDone();
        });
        ctx.runAtTick(5 + 1 + 28 + 3 + 3, () -> {
            ctx.assertTrue(stays.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS), "crushed under it, dazed");
            ctx.assertFalse(steps.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS), "but not who stepped out");
        });
        ctx.runAtTick(5 + 1 + 28 + 3 + 30, () -> {
            ctx.assertEquals(water(world, stays.getPos(), 12), 0, "the anvils sink into the ground and are gone");
            boss.discard();
            world.getServer().getPlayerManager().remove(stays);
            world.getServer().getPlayerManager().remove(steps);
            ctx.complete();
        });
    }

    /** Cloud Ride: up onto a cloud, round over everyone raking the ground with water bolts, then down with a crash. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "raggimel_cloud")
    public void cloudRideFliesRainsWaterAndCrashesDown(TestContext ctx) {
        floor(ctx);
        ServerWorld world = ctx.getWorld();
        RaggimelEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        Vec3d stand = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5)));
        ServerPlayerEntity player = survivalPlayer(world, stand);
        double ground = boss.getY();
        // He circles out past the test area: keep the chunks round it ticking (mock players load none)
        int cx = (int) Math.floor(stand.x) >> 4, cz = (int) Math.floor(stand.z) >> 4;
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) world.setChunkForced(x, z, true);
        ctx.runAtTick(5, () -> start(boss, "CLOUD_RIDE"));
        int flying = 5 + RaggimelEntity.RIDE_CROUCH + RaggimelEntity.RIDE_LEAP + 20;
        ctx.runAtTick(flying, () -> {
            ctx.assertEquals(boss.currentAction(), "CLOUD_RIDE", "riding");
            ctx.assertTrue(boss.getY() > ground + 8, "up in the sky, at " + (boss.getY() - ground));
            ctx.assertTrue(boss.getPassengerList().size() >= 9, "on a cloud riding along under him, " + boss.getPassengerList().size() + " puffs");
            ctx.assertTrue(boss.getPassengerList().stream().allMatch(e -> e.getCommandTags().contains("fmscraft.greed_water")), "of his own");
        });
        int[] bolts = {0};
        for (int t = flying; t < flying + 40; t++) {
            ctx.runAtTick(t, () -> bolts[0] = Math.max(bolts[0], world.getEntitiesByClass(
                    net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class, new net.minecraft.util.math.Box(stand, stand).expand(30),
                    e -> e.getBlockState().isOf(Blocks.BLUE_ICE)).size()));
        }
        ctx.runAtTick(flying + 40, () -> ctx.assertTrue(bolts[0] >= 3, "water bolts pouring down, " + bolts[0] + " at once"));
        // (Circling out past the test area he can pass through chunks that aren't ticking, mock players keeping none
        // loaded: wait for the ride to end rather than counting ticks)
        boolean[] down = {false};
        for (int t = flying + 41; t < 290; t++) {
            ctx.runAtTick(t, () -> {
                if (down[0] || boss.currentAction().equals("CLOUD_RIDE")) return;
                down[0] = true;
                ctx.assertTrue(Math.abs(boss.getY() - ground) < 1.5, "down with a crash, at " + (boss.getY() - ground));
                ctx.assertTrue(boss.getPassengerList().isEmpty(), "off the cloud");
                ctx.assertFalse(boss.hasNoGravity(), "back on his feet");
            });
        }
        ctx.runAtTick(290, () -> {
            for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) world.setChunkForced(x, z, false);
            ctx.assertTrue(down[0], "the ride ended");
            boss.discard();
            world.getServer().getPlayerManager().remove(player);
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

    private static void startAttack(RaggimelEntity boss, String name) {
        try {
            Class<?> action = Class.forName(RaggimelEntity.class.getName() + "$Action");
            java.lang.reflect.Method start = RaggimelEntity.class.getDeclaredMethod("startAttack", action);
            start.setAccessible(true);
            start.invoke(boss, Enum.valueOf(action.asSubclass(Enum.class), name));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Water Sprout: on top of it you're shot way up (it's the fall that hurts), near it not so high, further off nothing;
     * the water itself hurts nobody.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "raggimel_sprout")
    public void waterSproutShootsYouWayUp(TestContext ctx) {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        Vec3d spot = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5)));
        ServerPlayerEntity on = survivalPlayer(ctx.getWorld(), spot);
        ServerPlayerEntity near = survivalPlayer(ctx.getWorld(), spot.add(2.3, 0, 0));
        ServerPlayerEntity far = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 0))));
        // Just the one sprout, right under the first player (the attack's own ten land at random)
        ctx.runAtTick(5, () -> boss.sproutAt(spot));
        ctx.runAtTick(5 + 4, () -> ctx.assertTrue(water(ctx.getWorld(), spot, 2) >= 1, "the ground rippling where it'll burst"));
        ctx.runAtTick(5 + 12, () -> {
            ctx.assertTrue(ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                    new net.minecraft.util.math.Box(spot, spot).expand(2), e -> e.getBlockState().isOf(Blocks.ICE)).size() >= 2,
                    "a column of water shooting up");
            ctx.assertTrue(on.getVelocity().y > 2.3, "on top: shot way up (about 27 blocks), " + on.getVelocity().y);
            ctx.assertTrue(near.getVelocity().y > 1.0 && near.getVelocity().y < on.getVelocity().y, "near it: not so high, " + near.getVelocity().y);
            ctx.assertTrue(far.getVelocity().y < 0.1, "further off: nothing, " + far.getVelocity().y);
            for (ServerPlayerEntity p : new ServerPlayerEntity[]{on, near, far}) {
                ctx.assertEquals(p.getHealth(), p.getMaxHealth(), "the water itself hurts nobody");
            }
            boss.discard();
            for (ServerPlayerEntity p : new ServerPlayerEntity[]{on, near, far}) ctx.getWorld().getServer().getPlayerManager().remove(p);
            ctx.complete();
        });
    }

    /** Dive: he goes into the ground (unseen, untouchable) and bursts out under you, then lands hard. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220, batchId = "raggimel_dive")
    public void diveComesUpUnderYou(TestContext ctx) {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        Vec3d stand = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 6)));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), stand);
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 400, 3));
        // New players can't be hurt for their first 3 seconds, and a test player's never run out: this one can be
        try {
            java.lang.reflect.Field join = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
            join.setAccessible(true);
            join.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ctx.runAtTick(5, () -> startAttack(boss, "DIVE"));
        ctx.runAtTick(5 + 20, () -> {
            ctx.assertTrue(boss.isInvisible(), "under the ground");
            ctx.assertTrue(boss.isUntouchable(), "and can't be hit there");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "not up yet");
        });
        ctx.runAtTick(5 + 12 + 34 + 2, () -> {
            ctx.assertFalse(boss.isInvisible(), "he's out");
            ctx.assertTrue(Math.hypot(boss.getX() - stand.x, boss.getZ() - stand.z) < 1.5, "right under the player, at " + boss.getPos());
            ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "and it hurts");
        });
        ctx.runAtTick(5 + 12 + 34 + 70, () -> {
            ctx.assertFalse(boss.currentAction().equals("DIVE"), "landed, the dive over");
            ctx.assertFalse(boss.isInvisible(), "and in plain sight");
            boss.discard();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    /** Giant Slam: he shakes his head, grows four times his size, slams the spot in front of him, then poofs back down. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160, batchId = "raggimel_giant")
    public void giantSlamGrowsHimHitsTheSpotAndShrinksBack(TestContext ctx) {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 3))));
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 400, 3));
        try {
            java.lang.reflect.Field join = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
            join.setAccessible(true);
            join.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        double[] size = new double[1];
        ctx.runAtTick(5, () -> {
            size[0] = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            startAttack(boss, "GIANT_SLAM");
        });
        ctx.runAtTick(5 + 15, () -> ctx.assertEquals(boss.poseState(), RaggimelEntity.POSE_SHAKE, "shaking his head first"));
        ctx.runAtTick(5 + 22 + 20 + 4, () -> {
            double now = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            ctx.assertTrue(Math.abs(now - size[0] * 4) < 0.01, "four times his size, " + now);
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "not down yet");
        });
        ctx.runAtTick(5 + 22 + 20 + 10 + 1, () -> {
            ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "the staff comes down on the spot");
            ctx.assertTrue(player.getVelocity().horizontalLength() > 2, "and throws them hard, " + player.getVelocity());
        });
        ctx.runAtTick(5 + 22 + 20 + 10 + 16 + 2, () -> {
            double now = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            ctx.assertTrue(Math.abs(now - size[0]) < 0.01, "poofed back to his size, " + now);
            ctx.assertFalse(boss.currentAction().equals("GIANT_SLAM"), "and on with the fight");
            boss.discard();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    /** Chest Rain: chests spiral up round him, then rain down for 4 s while he's free to walk; they fall through the floor. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220, batchId = "raggimel_chests")
    public void chestRainLandsOnYouWhileHeWalks(TestContext ctx) {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 6))));
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 400, 2));
        try {
            java.lang.reflect.Field join = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
            join.setAccessible(true);
            join.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        Vec3d[] start = new Vec3d[1];
        ctx.runAtTick(5, () -> startAttack(boss, "CHEST_RAIN"));
        ctx.runAtTick(5 + 15, () -> {
            ctx.assertEquals(boss.currentAction(), "CHEST_RAIN", "casting: the chests rising round him");
            ctx.assertEquals(boss.fallingChests(), 0, "nothing falling yet");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "and nobody hurt");
        });
        ctx.runAtTick(5 + RaggimelEntity.CHEST_CAST + 2, () -> {
            ctx.assertEquals(boss.currentAction(), "CHASE", "cast: he's free to walk while it rains");
            ctx.assertTrue(boss.isRainingChests(), "and it's raining chests");
            ctx.assertTrue(boss.fallingChests() > 0, "chests coming down");
            start[0] = boss.getPos();
        });
        ctx.runAtTick(5 + RaggimelEntity.CHEST_CAST + 40, () -> {
            ctx.assertTrue(boss.chestHits() > 0, "a chest came down on the player (standing still)");
            ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "and it hurt");
            ctx.assertTrue(boss.getPos().distanceTo(start[0]) > 0.5, "he moved meanwhile, from " + start[0] + " to " + boss.getPos());
        });
        ctx.runAtTick(5 + RaggimelEntity.CHEST_CAST + RaggimelEntity.CHEST_RAIN + 60, () -> {
            ctx.assertFalse(boss.isRainingChests(), "the rain is over");
            ctx.assertEquals(boss.fallingChests(), 0, "every chest fell through the floor and is gone");
            ctx.assertTrue(ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                    boss.getBoundingBox().expand(40), d -> d.getBlockState().isOf(Blocks.CHEST) || d.getBlockState().isOf(Blocks.TRAPPED_CHEST)).isEmpty(),
                    "no chest left anywhere");
            boss.discard();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    /** Blocks of his water (block displays tagged by GreedWater) within {@code range} of {@code at}. */
    private static int tornadoPieces(ServerWorld world, Vec3d at, double range) {
        return world.getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                new net.minecraft.util.math.Box(at, at).expand(range), d -> d.getCommandTags().contains("fmscraft.greed_water")).size();
    }

    /**
     * Twister: he grows four times his size, eyes glowing, arms raised, while a tornado of water forms in front of him;
     * it takes whoever's in it up and throws them far; he shrinks back once it's loose; it roams, then falls apart.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 440, batchId = "raggimel_twister")
    public void twisterRisesThrowsYouFarAndFallsApart(TestContext ctx) {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        Vec3d stand = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 3)));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), stand);
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 400, 2));
        try {
            java.lang.reflect.Field join = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
            join.setAccessible(true);
            join.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        // Another, a way off: dragged in, not taken
        ServerPlayerEntity off = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(1, 1, 6))));
        double[] size = new double[1];
        int cast = RaggimelEntity.TWISTER_CAST;
        ctx.runAtTick(5, () -> {
            size[0] = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            startAttack(boss, "TWISTER");
        });
        ctx.runAtTick(5 + 30, () -> {
            ctx.assertEquals(boss.currentAction(), "TWISTER", "casting");
            ctx.assertEquals(boss.poseState(), RaggimelEntity.POSE_RAISE, "arms raised");
            ctx.assertTrue(boss.hasGlowingEyes(), "eyes glowing blue");
            double now = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            ctx.assertTrue(Math.abs(now - size[0] * 4) < 0.01, "four times his size, " + now);
            ctx.assertTrue(boss.hasTwister(), "a tornado forming");
            ctx.assertTrue(tornadoPieces(ctx.getWorld(), stand, 30) >= 80, "built of water, " + tornadoPieces(ctx.getWorld(), stand, 30));
            ctx.assertEquals(boss.twisterFlings(), 0, "not formed enough to take anyone yet");
        });
        ctx.runAtTick(5 + 75, () -> {
            Vec3d push = boss.lastPush(off);
            Vec3d toward = stand.subtract(off.getPos()).multiply(1, 0, 1).normalize();
            ctx.assertTrue(push.length() > 0.01, "the one a way off is dragged in, " + push);
            ctx.assertTrue(push.normalize().dotProduct(toward) > 0.5, "towards the tornado, " + push);
            ctx.assertEquals(off.getHealth(), off.getMaxHealth(), "but not taken");
        });
        ctx.runAtTick(5 + cast - 2, () -> {
            ctx.assertEquals(boss.currentAction(), "TWISTER", "still casting: 5 s");
            ctx.assertTrue(boss.twisterFlings() >= 1, "it took the player (standing in it) and threw them");
            ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "and it hurt");
            Vec3d fling = boss.lastTwisterFling();
            ctx.assertTrue(fling.horizontalLength() > 2.5 && fling.y > 1, "thrown far, " + fling);
            // Out of the way: the rest is about the tornado
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.getWorld().getServer().getPlayerManager().remove(off);
        });
        ctx.runAtTick(5 + cast + RaggimelEntity.TWISTER_THROW + 3, () -> {
            double now = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            ctx.assertTrue(Math.abs(now - size[0]) < 0.01, "back to his size, " + now);
            ctx.assertFalse(boss.hasGlowingEyes(), "eyes back to normal");
            ctx.assertFalse(boss.currentAction().equals("TWISTER"), "on with the fight");
            ctx.assertTrue(boss.hasTwister(), "while the tornado roams");
        });
        ctx.runAtTick(5 + cast + RaggimelEntity.TWISTER_ROAM + 40, () -> {
            ctx.assertFalse(boss.hasTwister(), "the tornado has fallen apart");
            ctx.assertEquals(tornadoPieces(ctx.getWorld(), stand, 40), 0, "and every piece of it is gone");
            boss.discard();
            ctx.complete();
        });
    }

    /**
     * Deluge: he goes under, four of him rise (only he can be hurt), huge, eyes glowing, each under an orb; they throw,
     * the false ones go, he shrinks back, and the orbs come down hard on the player.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 260, batchId = "raggimel_deluge")
    public void delugeRaisesFourOfHimAndBringsTheOrbsDown(TestContext ctx) {
        floor(ctx);
        RaggimelEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 6))));
        // Enough health to take every orb and show what they did
        player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(400);
        player.setHealth(400);
        try {
            java.lang.reflect.Field join = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
            join.setAccessible(true);
            join.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        double[] size = new double[1];
        int appear = RaggimelEntity.DELUGE_VANISH, thrown = appear + RaggimelEntity.DELUGE_CAST;
        ctx.runAtTick(5, () -> {
            size[0] = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            startAttack(boss, "DELUGE");
        });
        ctx.runAtTick(5 + 4, () -> {
            ctx.assertTrue(boss.isInvisible(), "gone into the water");
            ctx.assertTrue(boss.isUntouchable(), "and can't be hit there");
        });
        ctx.runAtTick(5 + appear + 25, () -> {
            var mirages = boss.mirages();
            ctx.assertEquals(mirages.size(), 3, "three false ones beside him");
            ctx.assertFalse(boss.isInvisible(), "and him, back up");
            ctx.assertEquals(boss.orbsUp(), 4, "an orb over each");
            double now = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            ctx.assertTrue(Math.abs(now - size[0] * 3) < 0.01, "three times his size, " + now);
            ctx.assertTrue(boss.hasGlowingEyes() && boss.poseState() == RaggimelEntity.POSE_RAISE, "eyes burning, arms raised");
            for (var m : mirages) {
                ctx.assertTrue(m.hasGlowingEyes() && m.poseState() == RaggimelEntity.POSE_RAISE, "every one exactly like him");
                double ms = m.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
                ctx.assertTrue(Math.abs(ms - now) < 0.01, "the same size, " + ms);
                ctx.assertFalse(m.damage(ctx.getWorld().getDamageSources().playerAttack(player), 10), "a false one can't be hurt");
                ctx.assertFalse(m.isRemoved(), "or got rid of");
            }
            float before = boss.getHealth();
            boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 10);
            ctx.assertTrue(boss.getHealth() < before, "the real one can");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "nothing's come down yet");
        });
        ctx.runAtTick(5 + thrown + RaggimelEntity.DELUGE_THROW + 3, () -> {
            ctx.assertEquals(boss.mirages().size(), 0, "the false ones are gone");
            ctx.assertTrue(ctx.getWorld().getEntitiesByClass(net.ragnar.fmscraft.boss.greed.RaggimelMirageEntity.class,
                    boss.getBoundingBox().expand(30), e -> true).isEmpty(), "every one of them");
            double now = boss.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
            ctx.assertTrue(Math.abs(now - size[0]) < 0.01, "he's back to his size, " + now);
            ctx.assertFalse(boss.hasGlowingEyes(), "eyes back to normal");
            ctx.assertFalse(boss.currentAction().equals("DELUGE"), "on with the fight");
            ctx.assertEquals(boss.orbsUp(), 4, "while the orbs are still up in the sky");
        });
        ctx.runAtTick(5 + thrown + 60 + 8, () -> {
            ctx.assertEquals(boss.orbsUp(), 0, "every orb has come down");
            ctx.assertTrue(boss.orbHits() >= 1, "on the player");
            float lost = player.getMaxHealth() - player.getHealth();
            ctx.assertTrue(lost > 30, "and it hit hard, " + lost);
            boss.discard();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }
}
