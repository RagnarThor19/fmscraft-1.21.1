package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.ragnar.fmscraft.boss.sloth.RickyEntity;
import net.ragnar.fmscraft.boss.sloth.RickyLook;
import net.ragnar.fmscraft.boss.sloth.RickyTimeline;
import net.ragnar.fmscraft.boss.sloth.SlothArena;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * RICKY, the Sloth boss. The full fight test uses the real city: the build, the wheel, asleep by the water, waking on
 * overseas' drop, the phase 1 clamp, the city dragging strays back, the transformation on Kills' drop, and winning. The
 * others put him on a stone floor in the test world and start his moves by hand, each in a batch of its own.
 */
public class RickyGameTests implements FabricGameTest {
    private static final int JOIN = 40;

    private static ServerPlayerEntity survivalPlayer(ServerWorld world, Vec3d at, float yaw) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-sloth-player");
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

    /** A player who can take what he dishes out for a while (200 health), so his moves can be measured. */
    private static ServerPlayerEntity tough(TestContext ctx, BlockPos rel, float yaw) {
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel)), yaw);
        player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        player.setHealth(200);
        player.setOnGround(true);
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

    private static boolean floorIntact(TestContext ctx) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) if (!ctx.getBlockState(new BlockPos(x, 0, z)).isOf(Blocks.STONE)) return false;
        }
        return true;
    }

    private static RickyEntity spawn(TestContext ctx, BlockPos rel) {
        RickyEntity boss = ModBossEntities.RICKY.create(ctx.getWorld());
        boss.setSin(Sin.SLOTH);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        boss.holdStill();
        ctx.getWorld().spawnEntity(boss);
        return boss;
    }

    /**
     * Runs {@code then} as soon as he's up and can be hurt (spawned here, he wakes at once). His waking stamp throws
     * everyone near back, and a test player keeps whatever velocity it's given: it's stilled again first, or he'd lead
     * his aim after where it would be going.
     */
    private static void whenReady(TestContext ctx, RickyEntity boss, Runnable then) {
        if (!boss.isUntouchable() && boss.currentAction().equals("MOVE")) {
            for (ServerPlayerEntity p : ctx.getWorld().getPlayers(p -> p.squaredDistanceTo(boss) < 14 * 14)) {
                p.setVelocity(Vec3d.ZERO);
                p.setOnGround(true);
            }
            then.run();
        } else {
            ctx.waitAndRun(1, () -> whenReady(ctx, boss, then));
        }
    }

    /** A step of the whole fight: if it fails, the fight is ended so the other Sloth tests aren't left waiting on it. */
    private static Runnable step(MinecraftServer server, Runnable body) {
        return () -> {
            try {
                body.run();
            } catch (RuntimeException | AssertionError e) {
                BossFights.forceEnd(server, Sin.SLOTH);
                throw e;
            }
        };
    }

    /** Runs {@code each} now and on each of the next {@code ticks} ticks, one tick at a time (never runAtEveryTick in a task). */
    private static void every(TestContext ctx, int ticks, Runnable each) {
        each.run();
        if (ticks > 0) ctx.waitAndRun(1, () -> every(ctx, ticks - 1, each));
    }

    /** Display entities over the test's own floor (a neighbouring test's are 13 blocks off: keep the range under that). */
    private static int displaysNear(TestContext ctx, double range) {
        Vec3d c = ctx.getAbsolute(new Vec3d(4, 1, 4));
        return ctx.getWorld().getEntitiesByClass(DisplayEntity.class, new Box(c, c).expand(range), e -> true).size();
    }

    private static void forceAround(ServerWorld world, Vec3d at, int chunks, boolean force) {
        int cx = (int) Math.floor(at.x) >> 4, cz = (int) Math.floor(at.z) >> 4;
        for (int x = cx - chunks; x <= cx + chunks; x++) {
            for (int z = cz - chunks; z <= cz + chunks; z++) world.setChunkForced(x, z, force);
        }
    }

    private static RickyEntity boss(ServerWorld arena) {
        return (RickyEntity) arena.getEntity(BossFights.session(Sin.SLOTH).bossId());
    }

    // ---- The whole fight, in the city ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 6000, batchId = "ricky_fight")
    public void slothFightSleepsWakesOnTheDropAndGoesSuperSaiyan(TestContext ctx) {
        // One Sloth fight at a time: the generic fight tests use it too (with the stand-in)
        FightGameTests.withSloth(ctx, 1, false, () -> {
            ServerWorld world = ctx.getWorld();
            MinecraftServer server = world.getServer();
            BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
            ctx.assertTrue(BossFights.open(Sin.SLOTH, world, base, Direction.Axis.X, JOIN), "portal should open");
            ServerPlayerEntity player = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5))), 0);
            player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
            player.setHealth(200);
            BossFights.enter(player, Sin.SLOTH);
            ServerWorld arena = Arenas.world(server, Sin.SLOTH);
            forceAround(arena, SlothArena.CENTER, 4, true);

            // The city is there: the clearing's grass, the flooded plain, a tower's mossy top, the empty exit frame
            ctx.waitAndRun(2, step(server, () -> {
                ctx.assertTrue(arena.getBlockState(new BlockPos(0, SlothArena.GROUND_Y, 1)).isOf(Blocks.GRASS_BLOCK), "the clearing's grass in the middle");
                ctx.assertTrue(arena.getBlockState(new BlockPos(0, SlothArena.FLOOR_Y + 2, 0)).isAir(), "open over it");
                ctx.assertTrue(arena.getBlockState(new BlockPos(SlothArena.PLAIN + 40, SlothArena.WATER_Y, 0)).isOf(Blocks.WATER), "the flooded plain, out past the city");
                ctx.assertTrue(arena.getBlockState(new BlockPos(-195, 233, -85)).isOf(Blocks.GRASS_BLOCK), "a tower's top, high over the west");
                BlockPos portal = SlothArena.EXIT_PORTAL;
                ctx.assertTrue(arena.getBlockState(portal.down()).isOf(Blocks.MOSSY_STONE_BRICKS), "the exit frame");
                ctx.assertTrue(arena.getBlockState(portal.add(-1, -1, 0)).isOf(Blocks.CRACKED_STONE_BRICKS), "its corner");
                ctx.assertTrue(arena.getBlockState(portal).isAir(), "the frame starts empty");
            }));
            ctx.waitAndRun(JOIN / 2, step(server, () -> {
                ctx.assertTrue(player.getServerWorld() == arena, "player should be in the Sloth arena");
                ctx.assertTrue(Cinematics.isFrozen(player), "frozen behind the wheel");
            }));

            // The portal closes: the wheel fades on the clearing, overseas starts, and he's asleep in the grass
            ctx.waitAndRun(JOIN + 3, step(server, () -> {
                FightSession session = BossFights.session(Sin.SLOTH);
                ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
                ctx.assertTrue(arena.getEntity(session.bossId()) instanceof RickyEntity, "the boss should be RICKY");
                RickyEntity boss = boss(arena);
                ctx.assertEquals(boss.currentAction(), "ENTRANCE", "action");
                ctx.assertEquals(boss.poseState(), RickyLook.POSE_SLEEP, "asleep");
                ctx.assertTrue(session.song() == RickyEntity.PHASE_ONE, "overseas should be playing");
                ctx.assertTrue(player.getX() > SlothArena.CENTER.x + 5, "everyone to the east, at " + player.getPos());
                float health = boss.getHealth();
                boss.damage(arena.getDamageSources().playerAttack(player), 50f);
                ctx.assertEquals(boss.getHealth(), health, "health while he sleeps");
            }));
            ctx.waitAndRun(JOIN + RickyTimeline.REVEAL_TICKS + 5, step(server, () -> ctx.assertFalse(Cinematics.isFrozen(player), "free once the wheel has gone")));
            ctx.waitAndRun(JOIN + RickyTimeline.SIT + 5, step(server, () -> ctx.assertEquals(boss(arena).poseState(), RickyLook.POSE_SIT, "sitting up")));
            ctx.waitAndRun(JOIN + RickyTimeline.GATHER + 5, step(server, () -> ctx.assertEquals(boss(arena).aura(), RickyLook.AURA_DARK, "the dark gathering round him")));

            // On the drop he stamps: it hurts nobody. Then he's loose
            ctx.waitAndRun(JOIN + RickyTimeline.DROP + 3, step(server, () -> ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "the stamp hurts nobody")));
            int fighting = JOIN + RickyTimeline.DROP + RickyTimeline.READY_AFTER + 6;
            ctx.waitAndRun(fighting, step(server, () -> {
                RickyEntity boss = boss(arena);
                ctx.assertFalse(boss.isUntouchable(), "he can be hurt once he's up: " + boss.describe());
                ctx.assertEquals(boss.currentAction(), "MOVE", "fighting");
                ctx.assertEquals(boss.getMaxHealth(), RickyEntity.MAX_HEALTH * RickyTimeline.healthScale(1), "health for one player");
                boss.holdStill();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 4000, 4));
                // Phase 1 can't be skipped by one big hit
                boss.damage(arena.getDamageSources().playerAttack(player), Float.MAX_VALUE);
                ctx.assertTrue(boss.getHealth() >= boss.getMaxHealth() * 0.5f - 0.01f, "one hit stops at half, at " + boss.getHealth());
                // Wandered off, up a tower: the city drags you back
                player.teleport(arena, -195.5, 234, -85.5, 0, 0);
            }));
            ctx.waitAndRun(fighting + 16, step(server, () -> {
                ctx.assertTrue(SlothArena.inBounds(player.getPos()), "dragged back to the clearing, at " + player.getPos());
                RickyEntity boss = boss(arena);
                for (int k = 0; k < 400 && boss.getPhase() == 1 && boss.getHealth() > boss.getMaxHealth() * 0.5f; k++) {
                    boss.timeUntilRegen = 0;
                    boss.damage(arena.getDamageSources().playerAttack(player), 40f);
                }
                ctx.assertEquals(boss.getHealth(), boss.getMaxHealth() * 0.5f, "health stops exactly at half");
            }));

            // Half health: the music cuts, the sky turns, everyone's held, nothing touches him
            int transition = fighting + 17;
            ctx.waitAndRun(transition + 10, step(server, () -> {
                RickyEntity boss = boss(arena);
                ctx.assertEquals(boss.getPhase(), 2, "phase");
                ctx.assertEquals(boss.currentAction(), "TRANSITION", "action");
                ctx.assertTrue(BossFights.session(Sin.SLOTH).song() == null, "the music cuts");
                ctx.assertTrue(Cinematics.isFrozen(player), "held to watch");
                float health = boss.getHealth();
                boss.damage(arena.getDamageSources().playerAttack(player), 50f);
                ctx.assertEquals(boss.getHealth(), health, "health while he powers up");
            }));
            int kills = transition + RickyTimeline.KILLS_AT;
            ctx.waitAndRun(kills + RickyTimeline.POWERUP_AT - 10, step(server, () -> {
                RickyEntity boss = boss(arena);
                ctx.assertTrue(BossFights.session(Sin.SLOTH).song() == RickyEntity.PHASE_TWO, "Kills");
                ctx.assertFalse(Cinematics.isFrozen(player), "free to move while he powers up");
                ctx.assertTrue(boss.getY() > boss.getBlockY() - 1 && !boss.saiyan(), "not gold yet");
                ctx.assertTrue(boss.hazardCount() >= 0, "");
            }));
            ctx.waitAndRun(kills + RickyTimeline.KILLS_DROP - 40, step(server, () -> ctx.assertFalse(boss(arena).hasTransitioned(), "still powering up")));
            // The drop: Super Saiyan
            ctx.waitAndRun(kills + RickyTimeline.KILLS_DROP + 3, step(server, () -> {
                RickyEntity boss = boss(arena);
                ctx.assertTrue(boss.hasTransitioned(), "Super Saiyan on the drop: " + boss.describe());
                ctx.assertTrue(boss.saiyan(), "the golden hair");
                ctx.assertEquals(boss.aura(), RickyLook.AURA_SAIYAN, "the golden aura");
            }));
            ctx.waitAndRun(kills + RickyTimeline.KILLS_DROP + RickyTimeline.LOOSE_AFTER + 6, step(server, () -> {
                RickyEntity boss = boss(arena);
                ctx.assertFalse(boss.isUntouchable(), "and he can be hurt");
                ctx.assertEquals(boss.currentAction(), "MOVE", "fighting");
                boss.damage(arena.getDamageSources().genericKill(), Float.MAX_VALUE);
                FightSession session = BossFights.session(Sin.SLOTH);
                ctx.assertTrue(session.stage() == FightSession.Stage.WON, "fight should be won");
                ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.SLOTH), "Sloth should be marked defeated");
                ctx.assertTrue(!arena.getEntitiesByClass(ItemEntity.class, new Box(boss.getBlockPos()).expand(6),
                        e -> e.getStack().isOf(ModItems.SOULS.get(Sin.SLOTH))).isEmpty(), "Soul of Sloth should drop");
                ctx.assertTrue(arena.getBlockState(SlothArena.EXIT_PORTAL).isOf(ModBlocks.SIN_PORTAL), "exit portal should open");
            }));
            ctx.waitAndRun(kills + RickyTimeline.KILLS_DROP + RickyTimeline.LOOSE_AFTER + 40, step(server, () -> {
                ctx.assertTrue(arena.getEntitiesByClass(DisplayEntity.class, new Box(SlothArena.CENTER, SlothArena.CENTER).expand(60), e -> true).isEmpty(),
                        "nothing of his left behind");
                BossFights.forceEnd(server, Sin.SLOTH);
                FmsWorldState.get(server).clearDefeated(Sin.SLOTH);
                forceAround(arena, SlothArena.CENTER, 4, false);
                leave(ctx, player);
                ctx.complete();
            }));
        });
    }

    // ---- His moves, on a stone floor ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "ricky_spikes")
    public void risingSpikesChargeTwoSecondsThenTossYou(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(4, 1, 6), 180);
        RickyEntity boss = spawn(ctx, new BlockPos(4, 1, 1));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("SPIKES");
            ctx.waitAndRun(30, () -> {
                ctx.assertEquals(boss.hazards("SpikeWave"), 0, "still charging after a second and a half");
                ctx.assertEquals(player.getHealth(), 200f, "nothing yet");
            });
            ctx.waitAndRun(42, () -> ctx.assertEquals(boss.hazards("SpikeWave"), 3, "three waves of spikes"));
            ctx.waitAndRun(60, () -> {
                ctx.assertTrue(player.getHealth() < 200f, "the spikes came up under them, health " + player.getHealth());
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
            });
            ctx.waitAndRun(120, () -> {
                ctx.assertEquals(boss.hazards("SpikeWave"), 0, "the spikes have sunk away");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "ricky_impale")
    public void impalingThrowsYouUp(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(5, 1, 5), 0);
        RickyEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("IMPALE");
            ctx.waitAndRun(14, () -> ctx.assertEquals(boss.hazards("Impale"), 1, "the ground marked under them"));
            boolean[] thrown = {false};
            every(ctx, 40, () -> thrown[0] |= player.getVelocity().y > 1.0);
            ctx.waitAndRun(42, () -> {
                ctx.assertTrue(thrown[0], "thrown high into the air");
                ctx.assertTrue(player.getHealth() < 200f, "impaled, health " + player.getHealth());
            });
            ctx.waitAndRun(120, () -> {
                ctx.assertEquals(boss.hazards("Impale"), 0, "the spike has sunk away");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "ricky_cannon")
    public void stoneCannonBeepsThreeTimesAndBlows(TestContext ctx) {
        floor(ctx);
        // A wall behind the player, so what misses them lands near them
        for (int y = 1; y <= 3; y++) for (int x = 0; x <= 7; x++) ctx.setBlockState(new BlockPos(x, y, 7), Blocks.STONE);
        ServerPlayerEntity player = tough(ctx, new BlockPos(4, 1, 6), 180);
        RickyEntity boss = spawn(ctx, new BlockPos(4, 1, 1));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("CANNON");
            ctx.waitAndRun(5, () -> ctx.assertTrue(boss.hazards("Cannon") >= 1, "a spike spinning up beside him"));
            ctx.waitAndRun(110, () -> {
                ctx.assertEquals(boss.hazards("Cannon"), 0, "every spike has gone off");
                ctx.assertTrue(player.getHealth() < 200f, "hit, health " + player.getHealth());
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                for (int x = 0; x <= 7; x++) ctx.assertTrue(ctx.getBlockState(new BlockPos(x, 2, 7)).isOf(Blocks.STONE), "the wall is untouched");
                ctx.assertEquals(displaysNear(ctx, 8), 0, "nothing left behind");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "ricky_smash")
    public void smashingThreeTimesInARow(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(5, 1, 5), 0);
        RickyEntity boss = spawn(ctx, new BlockPos(2, 1, 2));
        whenReady(ctx, boss, () -> {
            double floorY = boss.getY();
            boss.forceAttack("SMASH");
            double[] highest = {floorY};
            int[] landings = {0};
            boolean[] up = {false};
            every(ctx, 110, () -> {
                highest[0] = Math.max(highest[0], boss.getY());
                if (boss.getY() > floorY + 3) up[0] = true;
                if (up[0] && boss.getY() < floorY + 0.2) {
                    landings[0]++;
                    up[0] = false;
                }
            });
            ctx.waitAndRun(112, () -> {
                ctx.assertTrue(highest[0] > floorY + 6, "up into the air, " + (highest[0] - floorY) + " high");
                ctx.assertEquals(landings[0], 3, "three times");
                ctx.assertTrue(player.getHealth() < 200f, "and down on them, health " + player.getHealth());
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "ricky_snakes")
    public void snakeSpikesChargeThreeSecondsThenSnakeAndSink(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 4), 90);
        RickyEntity boss = spawn(ctx, new BlockPos(2, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("SNAKES");
            ctx.waitAndRun(50, () -> ctx.assertEquals(boss.hazards("Snake"), 0, "still charging after two and a half seconds"));
            ctx.waitAndRun(64, () -> ctx.assertEquals(boss.hazards("Snake"), RickyTimeline.snakes(false), "four rows of spikes"));
            ctx.waitAndRun(60 + 90 + 40, () -> {
                ctx.assertEquals(boss.hazards("Snake"), 0, "gone after a few seconds");
                ctx.assertEquals(displaysNear(ctx, 8), 0, "every spike sunk away");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "ricky_hands")
    public void shadowHandsRiseAndSlamDownOnYou(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(5, 1, 4), 90);
        RickyEntity boss = spawn(ctx, new BlockPos(1, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("HANDS");
            ctx.waitAndRun(8, () -> ctx.assertEquals(boss.hazards("Hand"), 1, "a hand coming for them"));
            int[] tallest = {0};
            every(ctx, 50, () -> {
                double top = 0;
                Vec3d c = player.getPos();
                for (DisplayEntity.BlockDisplayEntity d : ctx.getWorld().getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, new Box(c, c).expand(14), e -> true)) {
                    var shape = DisplayEntity.getTransformation(d.getDataTracker());
                    top = Math.max(top, shape.getTranslation().y() + shape.getScale().y());
                }
                tallest[0] = Math.max(tallest[0], (int) top);
            });
            ctx.waitAndRun(4 + 6 + RickyEntityTimings.HAND_SLAM + 4, () -> {
                ctx.assertTrue(tallest[0] >= 7, "a huge hand, at least 7 blocks tall: " + tallest[0]);
                ctx.assertTrue(player.getHealth() < 200f, "slammed, health " + player.getHealth());
            });
            ctx.waitAndRun(130, () -> {
                ctx.assertEquals(boss.hazards("Hand"), 0, "sunk back into the ground");
                ctx.assertEquals(displaysNear(ctx, 8), 0, "nothing left behind");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "ricky_dashpunch")
    public void dashpunchThrowsYouFar(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 4), 90);
        RickyEntity boss = spawn(ctx, new BlockPos(1, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("DASH_PUNCH");
            double[] fastest = {0};
            every(ctx, 40, () -> fastest[0] = Math.max(fastest[0], player.getVelocity().horizontalLength()));
            ctx.waitAndRun(42, () -> {
                ctx.assertTrue(player.getHealth() < 200f, "punched, health " + player.getHealth());
                ctx.assertTrue(fastest[0] > 2.5, "thrown hard, " + fastest[0] + " a tick");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "ricky_rocks")
    public void rocksAndBouldersAreThrownAndBreakNothing(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 6), 0);
        RickyEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("ROCKS");
            ctx.waitAndRun(40, () -> boss.forceAttack("BOULDER"));
            ctx.waitAndRun(130, () -> {
                ctx.assertTrue(player.getHealth() < 200f, "hit by something, health " + player.getHealth());
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                ctx.assertEquals(boss.getMainHandStack().isEmpty(), true, "the tome put away");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    // ---- Super Saiyan ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "ricky_beam")
    public void blackBeamBurnsWhoeverItFollows(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 1), 90);
        RickyEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        whenReady(ctx, boss, () -> {
            boss.goSaiyan();
            boss.forceAttack("BEAM");
            ctx.waitAndRun(30, () -> ctx.assertEquals(player.getHealth(), 200f, "nothing while he gathers it"));
            ctx.waitAndRun(42, () -> ctx.assertEquals(boss.hazards("Beam"), 1, "the beam"));
            ctx.waitAndRun(90, () -> {
                ctx.assertTrue(player.getHealth() < 200f, "burned, health " + player.getHealth());
                ctx.assertEquals(boss.hazards("Beam"), 0, "the beam is gone");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                ctx.assertEquals(displaysNear(ctx, 8), 0, "nothing left behind");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "ricky_final_beam")
    public void finalBeamSweepsRoundAndOnlyCoverSavesYou(TestContext ctx) {
        floor(ctx);
        // A wall between him and the second player, wide enough that the beam can't graze them past its ends
        for (int y = 1; y <= 3; y++) for (int x = 0; x <= 4; x++) ctx.setBlockState(new BlockPos(x, y, 5), Blocks.STONE);
        ServerPlayerEntity open = tough(ctx, new BlockPos(6, 1, 1), 90);
        ServerPlayerEntity hidden = tough(ctx, new BlockPos(1, 1, 7), 180);
        RickyEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        whenReady(ctx, boss, () -> {
            boss.goSaiyan();
            ctx.assertTrue(boss.coveredFromBeam(hidden), "the wall hides them");
            ctx.assertFalse(boss.coveredFromBeam(open), "nothing hides them");
            boss.forceAttack("FINAL_BEAM");
            ctx.waitAndRun(60, () -> {
                ctx.assertTrue(boss.isUntouchable(), "nothing touches him while he charges it");
                ctx.assertEquals(open.getHealth(), 200f, "nothing yet");
            });
            ctx.waitAndRun(70 + 76 + 12, () -> {
                ctx.assertTrue(open.getHealth() < 200f, "the sweep found them in the open, health " + open.getHealth());
                ctx.assertEquals(hidden.getHealth(), 200f, "never through the wall");
                ctx.assertEquals(boss.hazards("Beam"), 0, "the beam is gone");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                for (int y = 1; y <= 3; y++) for (int x = 0; x <= 4; x++) ctx.assertTrue(ctx.getBlockState(new BlockPos(x, y, 5)).isOf(Blocks.STONE), "the wall too");
                leave(ctx, open);
                leave(ctx, hidden);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "ricky_spirit_bomb")
    public void spiritBombGathersThenCrushes(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 4), 90);
        RickyEntity boss = spawn(ctx, new BlockPos(1, 1, 4));
        whenReady(ctx, boss, () -> {
            boss.goSaiyan();
            boss.forceAttack("SPIRIT_BOMB");
            ctx.waitAndRun(40, () -> {
                ctx.assertTrue(boss.isUntouchable(), "nothing touches him while he gathers it");
                float health = boss.getHealth();
                boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 50f);
                ctx.assertEquals(boss.getHealth(), health, "health while he gathers it");
            });
            ctx.waitAndRun(96 + 70 + 6, () -> {
                ctx.assertFalse(boss.isUntouchable(), "he can be hurt again");
                ctx.assertTrue(player.getHealth() < 200f, "crushed, health " + player.getHealth());
                ctx.assertEquals(boss.hazards("SpiritBomb"), 0, "it's burst");
                ctx.assertEquals(displaysNear(ctx, 8), 0, "nothing left behind");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600, batchId = "ricky_saiyan_moves")
    public void superSaiyanMovesHurtAndBreakNothing(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(5, 1, 5), 0);
        RickyEntity boss = spawn(ctx, new BlockPos(2, 1, 2));
        whenReady(ctx, boss, () -> {
            boss.goSaiyan();
            String[] moves = {"BARRAGE", "RUBBLE", "BIG_BANG", "RUSH", "DIVE", "STRIKE"};
            for (int i = 0; i < moves.length; i++) {
                String move = moves[i];
                ctx.waitAndRun(1 + i * 80, () -> {
                    player.setHealth(200);
                    player.refreshPositionAndAngles(ctx.getAbsolute(new Vec3d(5.5, 1, 5.5)), 0, 0);
                    player.setOnGround(true);
                    boss.refreshPositionAndAngles(ctx.getAbsolute(new Vec3d(2.5, 1, 2.5)), 0, 0);
                    boss.forceAttack(move);
                });
                ctx.waitAndRun(1 + i * 80 + 76, () -> {
                    ctx.assertTrue(player.getHealth() < 200f, move + " should hurt, health " + player.getHealth() + " (" + boss.describe() + ")");
                    ctx.assertTrue(floorIntact(ctx), move + " breaks nothing");
                });
            }
            ctx.waitAndRun(1 + moves.length * 80 + 10, () -> {
                ctx.assertEquals(boss.hazardCount(), 0, "nothing of his still out");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600, batchId = "ricky_transformation")
    public void heGoesSuperSaiyanOnKillsDrop(TestContext ctx) {
        floor(ctx);
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 6), 0);
        RickyEntity boss = spawn(ctx, new BlockPos(2, 1, 2));
        whenReady(ctx, boss, () -> {
            for (int k = 0; k < 400 && boss.getPhase() == 1; k++) {
                boss.timeUntilRegen = 0;
                boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 40f);
            }
            ctx.waitAndRun(5, () -> {
                ctx.assertEquals(boss.currentAction(), "TRANSITION", "powering up");
                ctx.assertTrue(boss.isUntouchable(), "nothing touches him");
                ctx.assertFalse(boss.saiyan(), "not gold yet");
            });
            int kills = RickyTimeline.KILLS_AT;
            ctx.waitAndRun(kills + RickyTimeline.RISE_END, () -> ctx.assertTrue(boss.getY() > ctx.getAbsolute(new Vec3d(0, 1, 0)).y + 2,
                    "risen off the ground, at " + boss.getY()));
            ctx.waitAndRun(kills + RickyTimeline.KILLS_DROP + 4, () -> {
                ctx.assertTrue(boss.hasTransitioned(), "Super Saiyan: " + boss.describe());
                ctx.assertTrue(boss.saiyan(), "the golden hair");
                ctx.assertEquals(player.getHealth(), 200f, "the burst hurts nobody");
            });
            ctx.waitAndRun(kills + RickyTimeline.KILLS_DROP + RickyTimeline.LOOSE_AFTER + 6, () -> {
                ctx.assertFalse(boss.isUntouchable(), "loose");
                ctx.assertEquals(displaysNear(ctx, 8), 0, "the rubble's gone");
                ctx.assertTrue(floorIntact(ctx), "the floor is untouched");
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    /** Timings the hand test waits on (RickyHazards is package-private to the boss). */
    private static final class RickyEntityTimings {
        /** From the hand appearing (its warning) to its palm hitting the ground. */
        static final int HAND_SLAM = 26 + 10 + 8 + 5;
    }
}
