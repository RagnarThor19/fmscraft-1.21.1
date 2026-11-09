package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.pride.LeonardEntity;
import net.ragnar.fmscraft.boss.pride.LeonardTimeline;
import net.ragnar.fmscraft.boss.pride.PrideArena;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.network.PrideSkyPayload;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * LEONARD, the Pride boss. The full fight test uses the real arena: the plaza in the sky and its temple, the wheel, ten
 * seconds of nothing, the song, Munch, the strike on the drop, the phase 1 clamp, the lightning carrying strays back,
 * the cone coming down on the orchestra's hit, CONEMAN, and winning. The others put him on a stone floor in the test
 * world and start his moves by hand, each in a batch of its own (he goes for every player near him).
 */
public class LeonardGameTests implements FabricGameTest {
    private static final int JOIN = 40;

    private static ServerPlayerEntity survivalPlayer(ServerWorld world, Vec3d at, float yaw) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-pride-player");
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
        player.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        player.setHeadYaw(yaw);
        player.onTeleportationDone();
        try {
            Field join = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
            join.setAccessible(true);
            join.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return player;
    }

    private static ServerPlayerEntity player(TestContext ctx, BlockPos rel, float yaw) {
        return survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel)), yaw);
    }

    /** A player who can take what he dishes out for a while (200 health), so his moves can be measured. */
    private static ServerPlayerEntity tough(TestContext ctx, BlockPos rel, float yaw) {
        ServerPlayerEntity player = player(ctx, rel, yaw);
        player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        player.setHealth(200);
        return player;
    }

    private static void leave(TestContext ctx, ServerPlayerEntity player) {
        ctx.getWorld().getServer().getPlayerManager().remove(player);
    }

    /** A stone floor over the test's own 8x8 (the next test's area starts 13 blocks over: nothing of ours goes past 7). */
    private static void floor(TestContext ctx) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static LeonardEntity spawn(TestContext ctx, BlockPos rel) {
        LeonardEntity boss = ModBossEntities.LEONARD.create(ctx.getWorld());
        boss.setSin(Sin.PRIDE);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        boss.holdStill();
        ctx.getWorld().spawnEntity(boss);
        return boss;
    }


    /**
     * His death: the last blow on CONEMAN leaves him on his last breath, untouchable; poof, the cone flies off (yours to
     * keep) and it's just LEONARD, shades on; he looks round, guilty, whistling; he's caught; he runs for it,
     * sprint-jumping, a little way and no further (off the plaza: 2.5 blocks from where he was); and he's gone mid-jump
     * (10 s), Defeated LEONARD given on the way.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700, batchId = "leonard_death")
    public void heGetsCaughtAndRunsForIt(TestContext ctx) {
        floor(ctx);
        LeonardEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity player = player(ctx, new BlockPos(1, 1, 1), 0);
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 600, 4));
        whenReady(ctx, boss, () -> {
            try {
                java.lang.reflect.Field phase = net.ragnar.fmscraft.boss.SinBossEntity.class.getDeclaredField("phase");
                phase.setAccessible(true);
                phase.setInt(boss, 2);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            boss.goConeman();
            Vec3d start = boss.getPos();
            boss.setHealth(3);
            boss.damage(ctx.getWorld().getDamageSources().magic(), 20);
            ctx.assertTrue(boss.isAlive() && boss.isDying(), "the last blow: he's done, at " + boss.getHealth() + " (" + boss.describe() + ")");
            float health = boss.getHealth();
            boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 20);
            ctx.assertEquals(boss.getHealth(), health, "nothing more reaches him");
            ctx.assertTrue(boss.coned(), "still CONEMAN, for a moment");
            ctx.waitAndRun(LeonardEntity.DEATH_POOF + 4, () -> {
                ctx.assertFalse(boss.coned(), "poof: the cone's off");
                ctx.assertTrue(boss.getScale() < 1.05f, "just LEONARD again, " + boss.getScale());
                ctx.assertEquals(boss.poseState(), LeonardEntity.POSE_GUILTY, "looking very guilty");
                ctx.assertFalse(ctx.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, boss.getBoundingBox().expand(8),
                        e -> e.getStack().isOf(net.ragnar.fmscraft.item.ModItems.TRAFFIC_CONE)).isEmpty(), "and the cone's yours");
            });
            ctx.waitAndRun(LeonardEntity.DEATH_HONOUR_AT + 4, () -> {
                var advancement = ctx.getWorld().getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of(net.ragnar.fmscraft.FmsCraft.MOD_ID, "defeated_pride"));
                ctx.assertTrue(advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone(), "Defeated LEONARD, during it");
            });
            ctx.waitAndRun(LeonardEntity.DEATH_CAUGHT + 3, () -> {
                ctx.assertEquals(boss.poseState(), LeonardEntity.POSE_CAUGHT, "caught");
                ctx.assertTrue(boss.runTo() != null && boss.runTo().subtract(start).horizontalLength() <= LeonardEntity.DEATH_RUN_OFF + 0.6,
                        "he means to run, but not far, to " + boss.runTo());
            });
            ctx.waitAndRun(LeonardEntity.DEATH_FLEE + 25, () -> {
                ctx.assertEquals(boss.poseState(), LeonardEntity.POSE_FLEE, "running for it, arms in the air");
                ctx.assertTrue(boss.isSprinting(), "sprinting");
                ctx.assertTrue(boss.getPos().subtract(start).horizontalLength() > 1, "away from where he was, at " + boss.getPos());
            });
            ctx.waitAndRun(LeonardEntity.DEATH_TICKS + 4, () -> {
                ctx.assertFalse(boss.isAlive(), "gone, mid-jump, after 10 s");
                ctx.assertTrue(boss.getPos().subtract(start).horizontalLength() < LeonardEntity.DEATH_RUN_OFF + 1.2, "and never ran off, at " + boss.getPos());
                ctx.getWorld().getServer().getPlayerManager().remove(player);
                ctx.complete();
            });
        });
    }
    /** Runs {@code then} as soon as he's up and can be hurt (spawned here, his arrival is a quick strike). */
    private static void whenReady(TestContext ctx, LeonardEntity boss, Runnable then) {
        if (!boss.isUntouchable() && boss.currentAction().equals("MOVE")) then.run();
        else ctx.waitAndRun(1, () -> whenReady(ctx, boss, then));
    }

    /** Runs {@code then} once he's back between moves, or after {@code limit} ticks whatever he's doing. */
    private static void whenDone(TestContext ctx, LeonardEntity boss, int limit, Runnable then) {
        if (boss.currentAction().equals("MOVE") || limit <= 0) then.run();
        else ctx.waitAndRun(1, () -> whenDone(ctx, boss, limit - 1, then));
    }

    /** Runs {@code each} now and on each of the next {@code ticks} ticks, one tick at a time (never runAtEveryTick in a task). */
    private static void every(TestContext ctx, int ticks, Runnable each) {
        each.run();
        if (ticks > 0) ctx.waitAndRun(1, () -> every(ctx, ticks - 1, each));
    }

    private static void forceAround(ServerWorld world, Vec3d at, int chunks, boolean force) {
        int cx = (int) Math.floor(at.x) >> 4, cz = (int) Math.floor(at.z) >> 4;
        for (int x = cx - chunks; x <= cx + chunks; x++) {
            for (int z = cz - chunks; z <= cz + chunks; z++) world.setChunkForced(x, z, force);
        }
    }

    private static LeonardEntity boss(ServerWorld arena) {
        return (LeonardEntity) arena.getEntity(BossFights.session(Sin.PRIDE).bossId());
    }

    /** No fire anywhere round the test area: his lightning is the game's own, but it burns nothing. */
    private static boolean noFire(TestContext ctx) {
        for (BlockPos pos : BlockPos.iterate(ctx.getAbsolutePos(new BlockPos(-2, 0, -2)), ctx.getAbsolutePos(new BlockPos(9, 4, 9)))) {
            if (ctx.getWorld().getBlockState(pos).isOf(Blocks.FIRE)) return false;
        }
        return true;
    }

    private static boolean floorIntact(TestContext ctx) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) if (!ctx.getBlockState(new BlockPos(x, 0, z)).isOf(Blocks.STONE)) return false;
        }
        return true;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1400, batchId = "leonard_fight")
    public void prideFightPlaysTheArrivalThenLeonardThenConeman(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ctx.assertTrue(BossFights.open(Sin.PRIDE, world, base, Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity player = player(ctx, new BlockPos(5, 1, 5), 0);
        player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        player.setHealth(200);
        BossFights.enter(player, Sin.PRIDE);
        ServerWorld arena = Arenas.world(server, Sin.PRIDE);
        forceAround(arena, PrideArena.CENTER, 5, true);

        // The plaza is built: the gold sun in the middle, open sky over it, the temple to the north, the empty exit frame
        ctx.runAtTick(2, () -> {
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, PrideArena.FLOOR_Y - 1, 0)).isOf(Blocks.GOLD_BLOCK), "the gold sun in the middle");
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, PrideArena.FLOOR_Y, 0)).isAir(), "open over it");
            ctx.assertFalse(arena.getBlockState(new BlockPos(-16, PrideArena.FLOOR_Y - 1, 0)).isAir(), "floor where everyone arrives");
            ctx.assertFalse(arena.getBlockState(new BlockPos(0, PrideArena.FLOOR_Y + 4, -40)).isAir(), "the temple's floor, to the north");
            ctx.assertTrue(arena.getBlockState(new BlockPos(60, PrideArena.FLOOR_Y - 1, 0)).isAir(), "nothing past the rim but sky");
            BlockPos portal = PrideArena.EXIT_PORTAL;
            ctx.assertTrue(arena.getBlockState(portal.down()).isOf(Blocks.QUARTZ_BRICKS), "the exit frame");
            ctx.assertTrue(arena.getBlockState(portal.add(-1, -1, 0)).isOf(Blocks.GOLD_BLOCK), "its corner");
            ctx.assertTrue(arena.getBlockState(portal).isAir(), "the frame starts empty");
        });
        ctx.runAtTick(JOIN / 2, () -> {
            ctx.assertTrue(player.getServerWorld() == arena, "player should be in the Pride arena");
            ctx.assertTrue(Cinematics.isFrozen(player), "frozen behind the wheel");
            ctx.assertTrue(BossFights.session(Sin.PRIDE).song() == null, "silent behind the wheel");
        });

        // The portal closes: the wheel fades on the plaza, everyone to the west of the middle; no boss to be seen, no song
        ctx.runAtTick(JOIN + 3, () -> {
            FightSession session = BossFights.session(Sin.PRIDE);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(arena.getEntity(session.bossId()) instanceof LeonardEntity, "the boss should be LEONARD");
            LeonardEntity boss = boss(arena);
            ctx.assertEquals(boss.currentAction(), "ENTRANCE", "action");
            ctx.assertTrue(boss.isInvisible(), "nowhere to be seen");
            ctx.assertTrue(session.song() == null, "no song yet");
            double d = PrideArena.flatDistance(player.getX(), player.getZ(), PrideArena.CENTER);
            ctx.assertTrue(Math.abs(d - LeonardTimeline.watchRadius(1)) < 0.6, "standing back from the middle, " + d + " out");
            ctx.assertTrue(player.getX() < PrideArena.CENTER.x, "to the west");
            ctx.assertTrue(Math.abs(player.getY() - PrideArena.FLOOR_Y) < 0.6, "on the plaza, at " + player.getY());
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health before he's there");
        });
        // The wheel's gone: free to look round. Nothing happens
        ctx.runAtTick(JOIN + LeonardTimeline.REVEAL_TICKS + 5, () -> {
            ctx.assertFalse(Cinematics.isFrozen(player), "free once the wheel has gone");
            ctx.assertTrue(BossFights.session(Sin.PRIDE).song() == null, "still silent");
            ctx.assertEquals(boss(arena).munchState(), PrideSkyPayload.MUNCH_HIDDEN, "Munch under the clouds");
        });
        // Ten seconds later: the song, and Munch rising with it
        int song = JOIN + LeonardTimeline.SILENCE + 1;
        ctx.runAtTick(song + 2, () -> {
            ctx.assertTrue(BossFights.session(Sin.PRIDE).song() == LeonardEntity.PHASE_ONE, "Grove St. Party should be playing");
            LeonardEntity boss = boss(arena);
            ctx.assertEquals(boss.munchState(), PrideSkyPayload.MUNCH_WATCHING, "Munch rising");
            ctx.assertTrue(boss.isInvisible(), "and still no LEONARD");
        });
        // On the drop: he strikes down in the middle. It hurts nobody
        ctx.runAtTick(song + LeonardTimeline.STRIKE + 2, () -> {
            LeonardEntity boss = boss(arena);
            ctx.assertFalse(boss.isInvisible(), "there he is");
            ctx.assertTrue(PrideArena.flatDistance(boss.getX(), boss.getZ(), PrideArena.CENTER) < 1, "in the middle, at " + boss.getPos());
            ctx.assertTrue(Math.abs(boss.getY() - PrideArena.FLOOR_Y) < 0.6, "on the plaza, at " + boss.getY());
            ctx.assertTrue(boss.getMainHandStack().isOf(ModItems.LIGHTNING_BOLT), "his bolt in his hand");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "the strike hurts nobody");
        });
        int fighting = song + LeonardTimeline.STRIKE + LeonardTimeline.READY_AFTER + 6;
        ctx.runAtTick(fighting, () -> {
            LeonardEntity boss = boss(arena);
            ctx.assertFalse(boss.isUntouchable(), "and can be hurt once he's up");
            ctx.assertEquals(boss.currentAction(), "MOVE", "fighting");
            ctx.assertEquals(boss.getMaxHealth(), LeonardEntity.MAX_HEALTH * LeonardTimeline.healthScale(1), "health for one player");
            boss.holdStill();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 4000, 4));
            // Phase 1 can't be skipped by one big hit
            boss.damage(arena.getDamageSources().playerAttack(player), Float.MAX_VALUE);
            ctx.assertTrue(boss.getHealth() >= boss.getMaxHealth() * 0.5f - 0.01f, "one hit stops at half, at " + boss.getHealth());
            // Off the rim, falling into the clouds: the lightning carries you back
            player.teleport(arena, 55.5, PrideArena.FLOOR_Y - 2, 0.5, 0, 0);
        });
        ctx.runAtTick(fighting + 16, () -> {
            ctx.assertTrue(PrideArena.inBounds(player.getPos()), "carried back onto the plaza, at " + player.getPos());
            LeonardEntity boss = boss(arena);
            for (int k = 0; k < 400 && boss.getPhase() == 1 && boss.getHealth() > boss.getMaxHealth() * 0.5f; k++) {
                boss.timeUntilRegen = 0;
                boss.damage(arena.getDamageSources().playerAttack(player), 40f);
            }
            ctx.assertEquals(boss.getHealth(), boss.getMaxHealth() * 0.5f, "health stops exactly at half");
        });

        // Half health: the music cuts, Munch falls, everyone's held, nothing touches him
        int transition = fighting + 17;
        ctx.runAtTick(transition + 10, () -> {
            LeonardEntity boss = boss(arena);
            ctx.assertEquals(boss.getPhase(), 2, "phase");
            ctx.assertEquals(boss.currentAction(), "TRANSITION", "action");
            ctx.assertTrue(BossFights.session(Sin.PRIDE).song() == null, "the music cuts");
            ctx.assertEquals(boss.munchState(), PrideSkyPayload.MUNCH_FALLING, "Munch topples into the clouds");
            ctx.assertTrue(Cinematics.isFrozen(player), "held to watch");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health in the cutscene");
        });
        int epic = transition + LeonardTimeline.EPIC_AT;
        ctx.runAtTick(epic + LeonardTimeline.CONE_APPEAR + 10, () -> {
            LeonardEntity boss = boss(arena);
            ctx.assertTrue(BossFights.session(Sin.PRIDE).song() == LeonardEntity.PHASE_TWO, "the orchestra");
            ctx.assertTrue(boss.getY() > PrideArena.FLOOR_Y + LeonardTimeline.SOAR_HEIGHT - 1, "soaring over the plaza, at " + boss.getY());
            ctx.assertTrue(boss.getScale() > 2.5f, "grown, scale " + boss.getScale());
            ctx.assertFalse(arena.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class, new Box(boss.getBlockPos()).expand(70),
                    e -> e.getItemStack().isOf(ModItems.TRAFFIC_CONE)).isEmpty(), "the cone coming down out of the sky");
            ctx.assertFalse(boss.coned(), "not on yet");
        });
        // The hit: the cone's on, he's down, CONEMAN
        ctx.runAtTick(epic + LeonardTimeline.CONE_HIT + 4, () -> {
            LeonardEntity boss = boss(arena);
            ctx.assertTrue(boss.coned(), "the cone is on his head");
            ctx.assertTrue(boss.hasTransitioned(), "CONEMAN");
            ctx.assertTrue(Math.abs(boss.getY() - PrideArena.FLOOR_Y) < 0.6, "down on the plaza, at " + boss.getY());
            ctx.assertEquals(boss.getHealth(), boss.getMaxHealth(), "all his health again");
            ctx.assertTrue(Math.abs(boss.getHeight() - 5f) < 0.05f, "five blocks tall, " + boss.getHeight());
            ctx.assertTrue(arena.getEntitiesByClass(DisplayEntity.ItemDisplayEntity.class, new Box(boss.getBlockPos()).expand(70),
                    e -> e.getItemStack().isOf(ModItems.TRAFFIC_CONE)).isEmpty(), "the falling cone is gone");
        });
        ctx.runAtTick(epic + LeonardTimeline.CONE_HIT + LeonardTimeline.BAR_FILL + 6, () -> {
            LeonardEntity boss = boss(arena);
            ctx.assertFalse(Cinematics.isFrozen(player), "free again");
            ctx.assertFalse(boss.isUntouchable(), "and he can be hurt");
            ctx.assertEquals(boss.currentAction(), "MOVE", "fighting");
            // Much tougher: the same blow does less
            float before = boss.getHealth();
            boss.timeUntilRegen = 0;
            boss.damage(arena.getDamageSources().playerAttack(player), 20f);
            ctx.assertTrue(before - boss.getHealth() < 20f * LeonardEntity.DAMAGE_TAKEN, "tankier, took " + (before - boss.getHealth()));
            boss.damage(arena.getDamageSources().genericKill(), Float.MAX_VALUE);
            FightSession session = BossFights.session(Sin.PRIDE);
            ctx.assertTrue(session.stage() == FightSession.Stage.WON, "fight should be won");
            ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.PRIDE), "Pride should be marked defeated");
            ctx.assertTrue(!arena.getEntitiesByClass(ItemEntity.class, new Box(boss.getBlockPos()).expand(6),
                    e -> e.getStack().isOf(ModItems.SOULS.get(Sin.PRIDE))).isEmpty(), "Soul of Pride should drop");
            ctx.assertTrue(!arena.getEntitiesByClass(ItemEntity.class, new Box(boss.getBlockPos()).expand(6),
                    e -> e.getStack().isOf(ModItems.TRAFFIC_CONE)).isEmpty(), "and his traffic cone");
            ctx.assertTrue(arena.getBlockState(PrideArena.EXIT_PORTAL).isOf(ModBlocks.SIN_PORTAL), "exit portal should open");
        });
        ctx.runAtTick(epic + LeonardTimeline.CONE_HIT + LeonardTimeline.BAR_FILL + 30, () -> {
            ctx.assertTrue(arena.getEntitiesByClass(DisplayEntity.class, new Box(PrideArena.CENTER, PrideArena.CENTER).expand(70), e -> true).isEmpty(),
                    "nothing of his left behind");
            BossFights.forceEnd(server, Sin.PRIDE);
            FmsWorldState.get(server).clearDefeated(Sin.PRIDE);
            forceAround(arena, PrideArena.CENTER, 5, false);
            leave(ctx, player);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "leonard_discharge")
    public void dischargeParalysesOnlyWhoDoesntJump(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity grounded = tough(ctx, new BlockPos(1, 1, 1), 0);
        ServerPlayerEntity jumper = tough(ctx, new BlockPos(7, 1, 7), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("DISCHARGE");
            // The first ring leaves him on tick 16 and rolls out at 0.75 a tick. (Test players don't move on the server:
            // who's on the ground is set by hand, as a client would report it)
            every(ctx, 40, () -> {
                grounded.setOnGround(true);
                jumper.setOnGround(false);
            });
            ctx.waitAndRun(26, () -> {
                ctx.assertTrue(grounded.getHealth() < 200, "the ring got who stood on the floor, " + grounded.getHealth());
                ctx.assertTrue(boss.paralysed().contains(grounded), "and paralysed them");
                ctx.assertTrue(grounded.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getValue() < 0.01, "held where they stand");
                // The ring is a solid band of glowing glass you can see, not sparks
                int glass = ctx.getWorld().getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, new Box(boss.getBlockPos()).expand(8),
                        d -> d.getBlockState().isOf(Blocks.YELLOW_STAINED_GLASS)).size();
                ctx.assertTrue(glass >= 40, "a band of glass segments rolling out, found " + glass);
            });
            ctx.waitAndRun(130, () -> {
                ctx.assertFalse(boss.paralysed().contains(grounded), "let go");
                ctx.assertTrue(grounded.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).getValue() > 0.05, "free to move again");
                ctx.assertTrue(floorIntact(ctx) && noFire(ctx), "nothing broken or burning");
                ctx.assertTrue(ctx.getWorld().getEntitiesByClass(DisplayEntity.class, new Box(boss.getBlockPos()).expand(40), e -> true).isEmpty(),
                        "the rings are gone once they've rolled out");
                boss.discard();
                leave(ctx, grounded);
                leave(ctx, jumper);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "leonard_arrows")
    public void arrowsNeverLand(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity archer = tough(ctx, new BlockPos(1, 1, 1), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(5, 1, 5));
        whenReady(ctx, boss, () -> {
            float health = boss.getHealth();
            ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, ctx.getWorld());
            arrow.setOwner(archer);
            arrow.setPosition(archer.getEyePos());
            ctx.assertFalse(boss.damage(ctx.getWorld().getDamageSources().arrow(arrow, archer), 12f), "the arrow doesn't land");
            ctx.assertEquals(boss.getHealth(), health, "health");
            // A sword does
            boss.timeUntilRegen = 0;
            boss.damage(ctx.getWorld().getDamageSources().playerAttack(archer), 12f);
            ctx.assertTrue(boss.getHealth() < health, "a blade does");
            boss.discard();
            leave(ctx, archer);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "leonard_throw")
    public void theThrownBoltStrikesAndComesBack(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity target = tough(ctx, new BlockPos(1, 1, 4), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(6, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("THROW");
            ctx.waitAndRun(16, () -> ctx.assertTrue(boss.boltThrown() && boss.getMainHandStack().isEmpty(), "the bolt left his hand"));
            ctx.waitAndRun(60, () -> {
                ctx.assertTrue(target.getHealth() < 200, "it struck, " + target.getHealth());
                ctx.assertFalse(boss.boltThrown(), "back in his hand");
                ctx.assertTrue(boss.getMainHandStack().isOf(ModItems.LIGHTNING_BOLT), "holding it");
                ctx.assertTrue(floorIntact(ctx) && noFire(ctx), "nothing broken or burning");
                ctx.assertTrue(ctx.getWorld().getEntitiesByClass(DisplayEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(30), e -> true).isEmpty(),
                        "nothing left lying");
                boss.discard();
                leave(ctx, target);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "leonard_boomerang")
    public void theBoomerangGoesOutAndComesHome(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity target = tough(ctx, new BlockPos(1, 1, 4), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(6, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("BOOMERANG");
            ctx.waitAndRun(20, () -> ctx.assertTrue(boss.boltThrown(), "out of his hand"));
            ctx.waitAndRun(80, () -> {
                ctx.assertFalse(boss.boltThrown(), "home again");
                ctx.assertTrue(boss.getMainHandStack().isOf(ModItems.LIGHTNING_BOLT), "holding it");
                ctx.assertEquals(boss.hazardCount(), 0, "nothing still out");
                boss.discard();
                leave(ctx, target);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "leonard_strikes")
    public void lightningStrikesHurtAndBurnNothing(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity target = tough(ctx, new BlockPos(2, 1, 2), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(6, 1, 6));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("STRIKES");
            ctx.waitAndRun(110, () -> {
                ctx.assertTrue(target.getHealth() < 200, "struck standing still, " + target.getHealth());
                ctx.assertTrue(floorIntact(ctx) && noFire(ctx), "nothing broken or burning");
                ctx.assertEquals(boss.currentAction(), "MOVE", "done");
                boss.discard();
                leave(ctx, target);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "leonard_munch")
    public void munchsBeamHitsOnlyWhoStaysInTheRing(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity inside = tough(ctx, new BlockPos(0, 1, 0), 0);
        ServerPlayerEntity outside = tough(ctx, new BlockPos(7, 1, 7), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(7, 1, 0));
        whenReady(ctx, boss, () -> {
            boss.munchBeamAt(inside.getPos());
            ctx.assertEquals(boss.munchState(), PrideSkyPayload.MUNCH_CHARGING, "Munch gathers it");
            ctx.waitAndRun(LeonardEntity.BEAM_CHARGE / 2, () -> ctx.assertEquals(inside.getHealth(), 200f, "a long warning first"));
            ctx.waitAndRun(LeonardEntity.BEAM_CHARGE + 4, () -> {
                ctx.assertEquals(boss.munchState(), PrideSkyPayload.MUNCH_FIRING, "the beam");
                ctx.assertEquals(inside.getHealth(), 200 - LeonardEntity.dealt(LeonardEntity.BEAM_DAMAGE, false), "devastating to who stayed");
                ctx.assertEquals(outside.getHealth(), 200f, "nothing to who got out");
            });
            ctx.waitAndRun(LeonardEntity.BEAM_CHARGE + LeonardEntity.BEAM_FIRE + 6, () -> {
                ctx.assertEquals(boss.munchState(), PrideSkyPayload.MUNCH_WATCHING, "back to watching");
                ctx.assertTrue(floorIntact(ctx), "nothing broken");
                boss.discard();
                leave(ctx, inside);
                leave(ctx, outside);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "leonard_pull")
    public void almightyPullDragsInThenThrowsOut(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity target = tough(ctx, new BlockPos(0, 1, 0), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(6, 1, 6));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("PULL");
            // (Test players don't move on the server: what he does to them shows in the way they're sent)
            ctx.waitAndRun(50, () -> {
                Vec3d in = boss.getPos().subtract(target.getPos()).multiply(1, 0, 1);
                ctx.assertTrue(target.getVelocity().multiply(1, 0, 1).dotProduct(in) > 0.1, "dragged in towards him, " + target.getVelocity());
                target.teleport(ctx.getWorld(), boss.getX() - 3, boss.getY(), boss.getZ(), 0, 0);
            });
            ctx.waitAndRun(80, () -> {
                Vec3d out = target.getPos().subtract(boss.getPos()).multiply(1, 0, 1);
                ctx.assertTrue(target.getHealth() < 200, "the blast hurts up close, " + target.getHealth());
                ctx.assertTrue(target.getVelocity().multiply(1, 0, 1).dotProduct(out) > 0.5, "thrown far away, " + target.getVelocity());
                boss.discard();
                leave(ctx, target);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "leonard_coneman_hazards")
    public void conemansRodsAndBallsLeaveNothing(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity target = tough(ctx, new BlockPos(1, 1, 1), 0);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1000, 2));
        LeonardEntity boss = spawn(ctx, new BlockPos(6, 1, 6));
        whenReady(ctx, boss, () -> {
            boss.hitLikeConeman();
            boss.forceAttack("RODS");
            ctx.waitAndRun(40, () -> {
                ctx.assertFalse(ctx.getWorld().getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class,
                        new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(30), d -> d.getBlockState().isOf(Blocks.LIGHTNING_ROD)).isEmpty(), "the rods are in");
                boss.forceAttack("BALLS");
            });
            ctx.waitAndRun(330, () -> {
                ctx.assertEquals(boss.hazardCount(), 0, "all spent");
                ctx.assertTrue(ctx.getWorld().getEntitiesByClass(DisplayEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(30), e -> true).isEmpty(),
                        "nothing of theirs left standing");
                ctx.assertTrue(floorIntact(ctx) && noFire(ctx), "nothing broken or burning");
                ctx.assertTrue(target.getHealth() < 200, "something got them");
                boss.discard();
                leave(ctx, target);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "leonard_stomp")
    public void conemansStompThrowsWhoeverIsClose(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity near = tough(ctx, new BlockPos(3, 1, 4), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(5, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.hitLikeConeman();
            boss.forceAttack("STOMP");
            ctx.waitAndRun(14, () -> {
                ctx.assertTrue(near.getHealth() < 200, "stomped, " + near.getHealth());
                boss.discard();
                leave(ctx, near);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "leonard_zoom")
    public void zoomEndsBackOnHisFeet(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity target = tough(ctx, new BlockPos(1, 1, 1), 0);
        LeonardEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("ZOOM");
            // Fifteen dashes take about seven seconds; wait for them (other tests' players nearby can slow him), up to twelve
            whenDone(ctx, boss, 240, () -> {
                ctx.assertTrue(boss.currentAction().equals("MOVE"), "done dashing: " + boss.describe());
                ctx.assertFalse(boss.afterimages(), "no afterimages left");
                ctx.assertFalse(boss.isInvisible(), "visible");
                ctx.assertTrue(floorIntact(ctx), "nothing broken");
                ctx.assertTrue(boss.getX() >= ctx.getAbsolutePos(BlockPos.ORIGIN).getX() && boss.getX() <= ctx.getAbsolutePos(new BlockPos(8, 0, 0)).getX()
                        && boss.getZ() >= ctx.getAbsolutePos(BlockPos.ORIGIN).getZ() && boss.getZ() <= ctx.getAbsolutePos(new BlockPos(0, 0, 8)).getZ(),
                        "still on his floor: " + boss.describe());
                boss.discard();
                leave(ctx, target);
                ctx.complete();
            });
        });
    }
}
