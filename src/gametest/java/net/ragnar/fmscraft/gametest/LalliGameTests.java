package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.gluttony.GluttonyArena;
import net.ragnar.fmscraft.boss.gluttony.LalliEntity;
import net.ragnar.fmscraft.boss.gluttony.LalliTimeline;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * LalliLarge, the Gluttony boss. The full fight test uses the real arena: the Earth, the float down, his landing, the
 * transition to SIRENS and the snowfield, and winning. The others put him on a stone floor in the test world and start
 * his attacks by hand, each in a batch of its own (he goes for every player near him).
 */
public class LalliGameTests implements FabricGameTest {
    private static final int JOIN = 40;
    private static final Identifier FROZEN = Identifier.of(FmsCraft.MOD_ID, "lalli_frozen");

    private static ServerPlayerEntity survivalPlayer(ServerWorld world, Vec3d at, float yaw) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-gluttony-player");
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

    private static void floor(TestContext ctx) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static LalliEntity spawn(TestContext ctx, BlockPos rel) {
        LalliEntity boss = ModBossEntities.LALLI.create(ctx.getWorld());
        boss.setSin(Sin.GLUTTONY);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        boss.holdStill();
        ctx.getWorld().spawnEntity(boss);
        return boss;
    }


    /**
     * His death: the last blow (phase 2) leaves him on his last breath, untouchable; he covers his face, takes two steps
     * towards you, looks at each of you (two here), bows his head, and is gone in a fall of snow (9 s), Defeated
     * LALLILARGE given on the way. (Put into phase 2 by hand, off his Earth.)
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700, batchId = "lalli_death")
    public void heKnowsYouTooLateAndLetsItBe(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(3, 1, 1));
        ServerPlayerEntity a = player(ctx, new BlockPos(1, 1, 6), 180), b = player(ctx, new BlockPos(6, 1, 6), 180);
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 4));
        whenReady(ctx, boss, () -> {
            try {
                java.lang.reflect.Field phase = net.ragnar.fmscraft.boss.SinBossEntity.class.getDeclaredField("phase");
                phase.setAccessible(true);
                phase.setInt(boss, 2);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            boss.setYaw(0);
            boss.setBodyYaw(0);
            boss.setHeadYaw(0);
            boss.setHealth(3);
            boss.damage(ctx.getWorld().getDamageSources().magic(), 20);
            ctx.assertTrue(boss.isAlive() && boss.isDying(), "the last blow: he lets it be, at " + boss.getHealth());
            float health = boss.getHealth();
            boss.damage(ctx.getWorld().getDamageSources().playerAttack(a), 20);
            ctx.assertEquals(boss.getHealth(), health, "nothing more reaches him");
            ctx.assertTrue(boss.deathLooks().size() >= 2 && boss.deathLooks().subList(0, 2).containsAll(java.util.List.of(a.getUuid(), b.getUuid())), "he'll look at both of them first");
            Vec3d from = boss.getPos();
            ctx.waitAndRun(LalliEntity.DEATH_COVERED + 4, () -> {
                ctx.assertEquals(boss.poseState(), LalliEntity.POSE_FAREWELL, "his hands over his face");
                ctx.assertTrue(boss.getPos().distanceTo(from) < 0.1, "standing still");
                var advancement = ctx.getWorld().getServer().getAdvancementLoader().get(net.minecraft.util.Identifier.of(net.ragnar.fmscraft.FmsCraft.MOD_ID, "defeated_gluttony"));
                ctx.assertTrue(advancement != null && a.getAdvancementTracker().getProgress(advancement).isDone(), "Defeated LALLILARGE, during it");
            });
            ctx.waitAndRun(LalliEntity.DEATH_WALKED + 2, () -> {
                double walked = boss.getPos().distanceTo(from);
                ctx.assertTrue(walked > 1.5 && walked < 2.2, "two steps forward, " + walked);
            });
            ctx.waitAndRun(LalliEntity.DEATH_LOOK + LalliEntity.DEATH_LOOK_EACH * 2 - 2, () -> {
                ServerPlayerEntity second = boss.deathLooks().get(1).equals(a.getUuid()) ? a : b;
                Vec3d to = second.getPos().subtract(boss.getPos());
                float yaw = (float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90f;
                float off = Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(yaw - boss.getHeadYaw()));
                ctx.assertTrue(off < 20, "looking at the second of them, " + off + " degrees off");
            });
            ctx.waitAndRun(LalliEntity.DEATH_TICKS + 4, () -> {
                ctx.assertFalse(boss.isAlive(), "and he's gone after 9 s");
                ctx.getWorld().getServer().getPlayerManager().remove(a);
                ctx.getWorld().getServer().getPlayerManager().remove(b);
                ctx.complete();
            });
        });
    }
    /** Runs {@code then} as soon as he's up and can be hurt (spawned here, he skips the float down). */
    private static void whenReady(TestContext ctx, LalliEntity boss, Runnable then) {
        if (!boss.isUntouchable() && boss.currentAction().equals("MOVE")) then.run();
        else ctx.waitAndRun(1, () -> whenReady(ctx, boss, then));
    }

    private static boolean held(ServerPlayerEntity p) {
        var speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(FROZEN);
    }

    private static void forceAround(ServerWorld world, Vec3d at, int chunks, boolean force) {
        int cx = (int) Math.floor(at.x) >> 4, cz = (int) Math.floor(at.z) >> 4;
        for (int x = cx - chunks; x <= cx + chunks; x++) {
            for (int z = cz - chunks; z <= cz + chunks; z++) world.setChunkForced(x, z, force);
        }
    }

    private static LalliEntity boss(ServerWorld arena) {
        return (LalliEntity) arena.getEntity(BossFights.session(Sin.GLUTTONY).bossId());
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1800, batchId = "lalli_fight")
    public void gluttonyFightFromTheEarthToTheSnowfield(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ctx.assertTrue(BossFights.open(Sin.GLUTTONY, world, base, Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity player = player(ctx, new BlockPos(5, 1, 5), 0);
        BossFights.enter(player, Sin.GLUTTONY);
        ServerWorld arena = Arenas.world(server, Sin.GLUTTONY);
        forceAround(arena, GluttonyArena.POLE, 4, true);
        forceAround(arena, GluttonyArena.SNOW_CENTER, 4, true);

        // Both worlds are built: the Earth with its pole, the snowfield, the castle, the empty exit frame
        ctx.runAtTick(2, () -> {
            ctx.assertFalse(arena.getBlockState(new BlockPos(0, GluttonyArena.POLE_BLOCK_Y, 0)).isAir(), "the north pole");
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, GluttonyArena.POLE_BLOCK_Y + 1, 0)).isAir(), "open sky over it");
            boolean equator = false;
            for (int x = 120; x <= 132; x++) equator |= !arena.getBlockState(new BlockPos(x, GluttonyArena.EARTH_CORNER.getY() + 128, 0)).isAir();
            ctx.assertTrue(equator, "the Earth's equator, 129 out");
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, GluttonyArena.EARTH_CORNER.getY() + 128, 0)).isAir(), "it's hollow");
            ctx.assertTrue(arena.getBlockState(new BlockPos(10, GluttonyArena.SNOW_BLOCK_Y, GluttonyArena.SNOW_Z + 10)).isOf(Blocks.GRASS_BLOCK), "snowy ground");
            ctx.assertTrue(arena.getBlockState(new BlockPos(10, GluttonyArena.SNOW_Y, GluttonyArena.SNOW_Z + 10)).isOf(Blocks.SNOW), "under snow");
            BlockPos portal = GluttonyArena.EXIT_PORTAL;
            ctx.assertTrue(arena.getBlockState(portal.down()).isOf(Blocks.PACKED_ICE), "packed ice under the exit portal");
            ctx.assertTrue(arena.getBlockState(portal.add(-1, -1, 0)).isOf(Blocks.BLUE_ICE), "blue ice corner");
            ctx.assertTrue(arena.getBlockState(portal).isAir(), "the frame starts empty");
            boolean castle = false;
            BlockPos c = GluttonyArena.CASTLE_CORNER;
            for (int x = 0; x < 74 && !castle; x += 3) {
                for (int z = 0; z < 70 && !castle; z += 3) {
                    for (int y = 4; y < 30 && !castle; y++) {
                        BlockState s = arena.getBlockState(c.add(x, y, z));
                        castle = s.isOf(Blocks.STONE) || s.isOf(Blocks.ANDESITE) || s.isOf(Blocks.COBBLESTONE);
                    }
                }
            }
            ctx.assertTrue(castle, "the ruined castle stands in the distance");
        });

        // Behind the wheel on the pole, Apple Pie already playing (a short intro starts it at once)
        ctx.runAtTick(JOIN / 2, () -> {
            ctx.assertTrue(player.getServerWorld() == arena, "player should be in the Gluttony arena");
            ctx.assertTrue(player.squaredDistanceTo(GluttonyArena.PLAYER_SPAWN) < 1, "player should arrive at the spawn");
            ctx.assertTrue(Cinematics.isFrozen(player), "frozen during the intro");
            ctx.assertTrue(BossFights.session(Sin.GLUTTONY).song() == LalliEntity.PHASE_ONE, "Apple Pie should be playing");
        });

        // The portal closes: everyone is high in space over the pole, frozen; he's up there too, unseen
        ctx.runAtTick(JOIN + 3, () -> {
            FightSession session = BossFights.session(Sin.GLUTTONY);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(arena.getEntity(session.bossId()) instanceof LalliEntity, "the boss should be LalliLarge");
            LalliEntity boss = boss(arena);
            ctx.assertEquals(boss.currentAction(), "ENTRANCE", "action");
            ctx.assertTrue(boss.isInvisible(), "not there yet");
            ctx.assertTrue(player.getY() > GluttonyArena.POLE_Y + LalliTimeline.SKY_HEIGHT - 5, "far up in space, at " + player.getY());
            ctx.assertTrue(Cinematics.isFrozen(player), "frozen while floating down");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health before he's down");
            ctx.assertTrue(Math.abs(boss.getHeight() - 3.0f) < 0.05f, "three blocks tall, " + boss.getHeight());
        });
        // (A test player has no client to move it, so it hangs where it is until it's set down; a real one floats)
        ctx.runAtTick(250, () -> {
            ctx.assertTrue(Cinematics.isFrozen(player), "still frozen, floating down");
            ctx.assertTrue(boss(arena).isInvisible(), "and he isn't there yet");
        });
        ctx.runAtTick(LalliTimeline.PLAYERS_LAND - LalliTimeline.APPEAR_BEFORE + 5, () ->
                ctx.assertFalse(boss(arena).isInvisible(), "he appears high over the pole at 20 s"));

        // Everyone sets down on the pole at 25 s, unhurt, and can move
        ctx.runAtTick(LalliTimeline.PLAYERS_LAND + 6, () -> {
            ctx.assertTrue(Math.abs(player.getY() - GluttonyArena.POLE_Y) < 4, "down on the pole, at " + player.getY());
            ctx.assertTrue(GluttonyArena.inBounds(player.getPos()), "on the fighting ground");
            ctx.assertFalse(Cinematics.isFrozen(player), "free to move once landed");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "nobody is hurt landing");
            LalliEntity boss = boss(arena);
            ctx.assertTrue(boss.getY() > GluttonyArena.POLE_Y + 20, "he's still coming down, at " + boss.getY());
            ctx.assertTrue(boss.isUntouchable(), "untouchable till he lands");
        });

        // He lands at 35 s; the wave throws everyone back and hurts nobody
        int fighting = LalliTimeline.BOSS_LANDS + LalliTimeline.RISE_TICKS + 6;
        ctx.runAtTick(LalliTimeline.BOSS_LANDS + 4, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertTrue(Math.abs(boss.getY() - GluttonyArena.POLE_Y) < 3 && GluttonyArena.flatDistance(boss.getX(), boss.getZ(), GluttonyArena.POLE) < 1,
                    "he lands on the pole, at " + boss.getPos());
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "the wave hurts nobody");
        });
        ctx.runAtTick(fighting, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertFalse(boss.isUntouchable(), "and can be hurt once he's up");
            ctx.assertEquals(boss.getMaxHealth(), LalliEntity.MAX_HEALTH * LalliTimeline.healthScale(1), "health for one player");
            boss.holdStill();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 2000, 4));
            // Phase 1 can't be skipped by one big hit
            boss.damage(arena.getDamageSources().playerAttack(player), Float.MAX_VALUE);
            ctx.assertTrue(boss.getHealth() >= boss.getMaxHealth() * 0.5f - 0.01f, "one hit stops at half, at " + boss.getHealth());
            // Blown off the pole: the cold wind carries you back
            player.teleport(arena, GluttonyArena.EARTH_RADIUS + 10, GluttonyArena.POLE_Y - 10, 0.5, 0, 0);
        });
        ctx.runAtTick(fighting + 20, () -> {
            ctx.assertTrue(GluttonyArena.inBounds(player.getPos()), "carried back onto the pole, at " + player.getPos());
            LalliEntity boss = boss(arena);
            // Real hits, through his armor, all the way down: his health must land exactly on half and start phase 2
            for (int k = 0; k < 400 && boss.getPhase() == 1 && boss.getHealth() > boss.getMaxHealth() * 0.5f; k++) {
                boss.timeUntilRegen = 0;
                boss.damage(arena.getDamageSources().playerAttack(player), 40f);
            }
            ctx.assertEquals(boss.getHealth(), boss.getMaxHealth() * 0.5f, "health stops exactly at half");
        });

        // Half health: the mini cutscene. Everyone held still, Apple Pie fading out, nothing hurts him
        int transition = fighting + 21;
        ctx.runAtTick(transition + 10, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertEquals(boss.getPhase(), 2, "phase");
            ctx.assertEquals(boss.currentAction(), "TRANSITION", "action");
            ctx.assertTrue(BossFights.session(Sin.GLUTTONY).song() == null, "Apple Pie fading out, SIRENS not yet");
            ctx.assertTrue(Cinematics.isFrozen(player), "held still for the cutscene");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health during the cutscene");
        });
        // SIRENS: he flies up into the sky; everyone can move and watch
        int sirens = transition + LalliTimeline.CUTSCENE - 1;
        ctx.runAtTick(sirens + 100, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertTrue(BossFights.session(Sin.GLUTTONY).song() == LalliEntity.PHASE_TWO, "SIRENS should be playing");
            ctx.assertFalse(Cinematics.isFrozen(player), "free to move while he rises");
            ctx.assertTrue(boss.getY() > GluttonyArena.POLE_Y + 3, "rising, at " + boss.getY());
            ctx.assertFalse(GluttonyArena.inSnowfield(player.getPos()), "still on the Earth");
        });
        ctx.runAtTick(sirens + LalliTimeline.WHITE_AT - 4, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertTrue(boss.getY() > GluttonyArena.POLE_Y + LalliTimeline.ASCENT_HEIGHT - 3, "high in the sky by the white, at " + boss.getY());
            ctx.assertTrue(boss.isUntouchable(), "and untouchable");
        });
        ctx.runAtTick(sirens + LalliTimeline.WHITE_AT + 4, () -> ctx.assertTrue(Cinematics.isFrozen(player), "held still in the white"));

        // He goes on ahead to the snowfield a moment before everyone (so their clients get him afresh there)
        ctx.runAtTick(sirens + LalliTimeline.MOVE_AT - 1, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertTrue(GluttonyArena.inSnowfield(boss.getPos()), "gone on ahead to the snowfield, at " + boss.getPos());
            ctx.assertFalse(GluttonyArena.inSnowfield(player.getPos()), "while everyone's still on the Earth");
        });
        // The switch-up: everyone is in the snowfield, his eyes burn, and the fight goes on
        ctx.runAtTick(sirens + LalliTimeline.SWITCH_TICK + 4, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertTrue(GluttonyArena.inSnowfield(player.getPos()), "in the snowfield, at " + player.getPos());
            ctx.assertTrue(GluttonyArena.inSnowfield(boss.getPos()), "and so is he, at " + boss.getPos());
            ctx.assertTrue(Math.abs(player.getY() - GluttonyArena.SNOW_Y) < 1.5, "standing on the snow, at " + player.getY());
            ctx.assertFalse(Cinematics.isFrozen(player), "free again");
            ctx.assertTrue(boss.hasGlowingEyes(), "his eyes burn blue");
            ctx.assertTrue(boss.hasTransitioned(), "phase 2 for real");
        });
        int phaseTwo = sirens + LalliTimeline.SWITCH_TICK + 36;
        ctx.runAtTick(phaseTwo, () -> {
            LalliEntity boss = boss(arena);
            ctx.assertFalse(boss.isUntouchable(), "he can be hurt again");
            ctx.assertEquals(boss.currentAction(), "MOVE", "action");
            // Out into the blizzard: back you go
            player.teleport(arena, 0.5, GluttonyArena.SNOW_Y, GluttonyArena.SNOW_Z + GluttonyArena.SNOW_RADIUS + 12, 0, 0);
        });
        ctx.runAtTick(phaseTwo + 20, () -> {
            ctx.assertTrue(GluttonyArena.inBounds(player.getPos()), "carried back into the fight, at " + player.getPos());
            boss(arena).kill();
            ctx.assertTrue(BossFights.session(Sin.GLUTTONY).stage() == FightSession.Stage.WON, "fight should be won");
            ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.GLUTTONY), "Gluttony should be marked defeated");
            ctx.assertTrue(arena.getBlockState(GluttonyArena.EXIT_PORTAL).isOf(net.ragnar.fmscraft.block.ModBlocks.SIN_PORTAL), "exit portal opens in the frame");
            ctx.assertTrue(arena.getEntitiesByClass(DisplayEntity.class, new Box(GluttonyArena.SNOW_CENTER, GluttonyArena.SNOW_CENTER).expand(80),
                    d -> true).isEmpty(), "no ice of his left behind");
        });
        ctx.runAtTick(phaseTwo + 24, () -> {
            BossFights.forceEnd(server, Sin.GLUTTONY);
            FmsWorldState.get(server).clearDefeated(Sin.GLUTTONY);
            server.getPlayerManager().remove(player);
            forceAround(arena, GluttonyArena.POLE, 4, false);
            forceAround(arena, GluttonyArena.SNOW_CENTER, 4, false);
            ctx.complete();
        });
    }

    // ---- His attacks, one at a time ----

    /** The Ice Punch knocks you away and freezes you where you land; the ice lets go in the end. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "lalli_punch")
    public void iceFreezesWhoItHitsAndLetsGo(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = player(ctx, new BlockPos(3, 1, 3), 90);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("ICE_PUNCH");
            ctx.waitAndRun(40, () -> {
                ctx.assertTrue(held(player), "frozen solid after the punch");
                ctx.assertFalse(ctx.getWorld().getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, player.getBoundingBox().expand(1), d -> true).isEmpty(),
                        "a block of ice round them");
                ctx.waitAndRun(60, () -> {
                    ctx.assertFalse(held(player), "the ice lets go");
                    boss.discard();
                    ctx.getWorld().getServer().getPlayerManager().remove(player);
                    ctx.complete();
                });
            });
        });
    }

    /** Nobody is ever left frozen: when he's taken away mid-freeze, the ice goes and they can move. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "lalli_thaw")
    public void nobodyIsLeftFrozenWhenHeGoes(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = player(ctx, new BlockPos(3, 1, 3), 90);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("ICE_PUNCH");
            ctx.waitAndRun(36, () -> {
                ctx.assertTrue(held(player), "frozen");
                boss.discard();
                ctx.waitAndRun(2, () -> {
                    ctx.assertFalse(held(player), "free the moment he's gone");
                    ctx.assertTrue(ctx.getWorld().getEntitiesByClass(DisplayEntity.class, player.getBoundingBox().expand(6), d -> true).isEmpty(),
                            "no ice left");
                    ctx.getWorld().getServer().getPlayerManager().remove(player);
                    ctx.complete();
                });
            });
        });
    }

    /** The Large Push: everyone close is thrown hard. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "lalli_push")
    public void largePushThrowsEveryoneFar(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity a = player(ctx, new BlockPos(6, 1, 3), 90);
        ServerPlayerEntity b = player(ctx, new BlockPos(3, 1, 6), 180);
        a.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
        b.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("LARGE_PUSH");
            ctx.waitAndRun(37, () -> {
                for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) {
                    double v = p.getVelocity().horizontalLength();
                    ctx.assertTrue(v > 2.5, "thrown hard, " + v);
                }
                boss.discard();
                ctx.getWorld().getServer().getPlayerManager().remove(a);
                ctx.getWorld().getServer().getPlayerManager().remove(b);
                ctx.complete();
            });
        });
    }

    /** The Tome of Freezing: the globe swells, then everyone in it freezes. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "lalli_freezing")
    public void freezingFreezesEveryoneInTheGlobe(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = player(ctx, new BlockPos(6, 1, 6), 0);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("FREEZING");
            ctx.waitAndRun(40, () -> ctx.assertFalse(held(player), "not yet, while it swells"));
            ctx.waitAndRun(64, () -> {
                ctx.assertTrue(held(player), "frozen when it bursts");
                boss.discard();
                ctx.getWorld().getServer().getPlayerManager().remove(player);
                ctx.complete();
            });
        });
    }

    /** His ice never touches the world: icicles, spikes and the impaling spike are all display entities. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 320, batchId = "lalli_ice")
    public void hisIceNeverPlacesABlock(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = player(ctx, new BlockPos(6, 1, 6), 0);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 4));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("ICICLES");
            ctx.waitAndRun(30, () -> boss.forceAttack("IMPALING"));
            ctx.waitAndRun(60, () -> boss.forceAttack("RISING_SPIKES"));
            ctx.waitAndRun(95, () -> boss.forceAttack("ICE_CANNON"));
            ctx.waitAndRun(75, () -> ctx.assertFalse(ctx.getWorld().getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class,
                    new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(30), d -> true).isEmpty(), "there's ice about"));
            ctx.waitAndRun(170, () -> {
                for (int x = 0; x <= 7; x++) {
                    for (int z = 0; z <= 7; z++) {
                        ctx.assertTrue(ctx.getBlockState(new BlockPos(x, 0, z)).isOf(Blocks.STONE), "the floor is untouched");
                        for (int y = 1; y <= 6; y++) {
                            BlockState s = ctx.getBlockState(new BlockPos(x, y, z));
                            ctx.assertFalse(s.isOf(Blocks.ICE) || s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.BLUE_ICE), "no real ice placed");
                        }
                    }
                }
                boss.discard();
                ctx.getWorld().getServer().getPlayerManager().remove(player);
                ctx.complete();
            });
        });
    }

    /** Ice Blade: the blade forms in his hand, he cuts, then spins and throws it, and he's empty-handed again. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240, batchId = "lalli_blade")
    public void iceBladeCutsThenIsThrownAndHesEmptyHandedAgain(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = player(ctx, new BlockPos(4, 1, 3), 90);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 3));
        whenReady(ctx, boss, () -> {
            boss.forceAttack("ICE_BLADE");
            ctx.waitAndRun(16, () -> ctx.assertTrue(boss.hasBlade(), "the blade forms in his hand"));
            ctx.waitAndRun(40, () -> ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "the cuts land"));
            ctx.waitAndRun(64, () -> {
                ctx.assertEquals(boss.currentAction(), "BLADE_THROW", "straight into the throw");
                ctx.assertTrue(boss.hasBlade(), "still holding it as he spins");
            });
            ctx.waitAndRun(100, () -> {
                ctx.assertFalse(boss.hasBlade(), "empty-handed again once it's thrown");
                boss.discard();
                ctx.getWorld().getServer().getPlayerManager().remove(player);
                ctx.complete();
            });
        });
    }

    /** Ice Cannon: three icicles spin up over his head and fire at you, one by one. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "lalli_cannon")
    public void iceCannonFiresThreeIciclesAtYou(TestContext ctx) {
        floor(ctx);
        LalliEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = player(ctx, new BlockPos(6, 1, 6), 0);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 3));
        ctx.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ragnarsmagicmod")
                || net.ragnar.fmscraft.compat.MagicSpells.hasEarthquake(), "the Tome of Earthquake's packets are found, for his Jump Slam");
        whenReady(ctx, boss, () -> {
            boss.forceAttack("ICE_CANNON");
            ctx.waitAndRun(10, () -> ctx.assertEquals(ctx.getWorld().getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class,
                    boss.getBoundingBox().expand(6), d -> d.getY() > boss.getY() + boss.getHeight()).size(), 12, "three icicles over his head"));
            ctx.waitAndRun(25, () -> ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "not fired yet"));
            ctx.waitAndRun(70, () -> {
                ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "they hit");
                boss.discard();
                ctx.getWorld().getServer().getPlayerManager().remove(player);
                ctx.complete();
            });
        });
    }
}
