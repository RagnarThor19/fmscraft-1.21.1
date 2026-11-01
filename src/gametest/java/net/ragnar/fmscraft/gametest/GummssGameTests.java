package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.compat.MagicSpells;
import net.ragnar.fmscraft.boss.envy.EnvyArena;
import net.ragnar.fmscraft.boss.envy.GummssEntity;
import net.ragnar.fmscraft.boss.envy.GummssIllusionEntity;
import net.ragnar.fmscraft.boss.envy.GummssTimeline;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUMMSS, the Envy boss. The full fight test uses the real arena (the Envy key test only opens and closes the portal);
 * the others put him on a stone floor in the test world and start his attacks by hand, each in a batch of its own:
 * he goes for every player near him, and other tests' players shouldn't be pulled into his.
 */
public class GummssGameTests implements FabricGameTest {
    private static final int JOIN = 40;

    private static ServerPlayerEntity survivalPlayer(ServerWorld world, Vec3d at, float yaw) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-envy-player");
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
        // New players can't be hurt for their first 3 seconds; these ones need to be from the start
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

    /**
     * Stone under the test's 8 by 8 box. Everything in these tests stays inside it: the test framework walls each test
     * in with barriers (which block his line of sight) and only keeps the chunks under it ticking.
     */
    private static void floor(TestContext ctx) {
        floor(ctx, 0, 7);
    }

    private static void floor(TestContext ctx, int from, int to) {
        for (int x = from; x <= to; x++) {
            for (int z = from; z <= to; z++) ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static GummssEntity spawn(TestContext ctx, BlockPos rel) {
        GummssEntity boss = ModBossEntities.GUMMSS.create(ctx.getWorld());
        boss.setSin(Sin.ENVY);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        boss.holdStill();
        ctx.getWorld().spawnEntity(boss);
        return boss;
    }

    /**
     * Runs {@code then} as soon as he's landed and can be hurt (he may start ticking a few ticks after he's spawned).
     * Spawned for these tests, he stands still between attacks and starts none of his own.
     */
    private static void whenReady(TestContext ctx, GummssEntity boss, Runnable then) {
        if (!boss.isUntouchable() && boss.currentAction().equals("MOVE")) then.run();
        else ctx.waitAndRun(1, () -> whenReady(ctx, boss, then));
    }

    /** Starts one of his attacks by name (they're private to him). */
    private static void start(GummssEntity boss, String attack) {
        try {
            Class<?> action = Class.forName(GummssEntity.class.getName() + "$Action");
            Method method = GummssEntity.class.getDeclaredMethod("startAttack", action);
            method.setAccessible(true);
            method.invoke(boss, Enum.valueOf(action.asSubclass(Enum.class), attack));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Who his Tome of Reckoning has caught so far (empty if it isn't running). */
    @SuppressWarnings("unchecked")
    private static Map<UUID, Float> reckoningCaught(GummssEntity boss) {
        try {
            Field hazards = GummssEntity.class.getDeclaredField("hazards");
            hazards.setAccessible(true);
            for (Object h : (List<Object>) hazards.get(boss)) {
                if (!h.getClass().getSimpleName().equals("Reckoning")) continue;
                Field caught = h.getClass().getDeclaredField("caught");
                caught.setAccessible(true);
                return (Map<UUID, Float>) caught.get(h);
            }
            return Map.of();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void forceArena(ServerWorld arena, boolean force) {
        for (int cx = -4; cx <= 3; cx++) {
            for (int cz = -4; cz <= 3; cz++) arena.setChunkForced(cx, cz, force);
        }
    }

    /** Keeps every chunk within {@code radius} of {@code rel} loaded (or lets them go): the test only loads its own box. */
    private static void forceAround(TestContext ctx, BlockPos rel, int radius, boolean force) {
        BlockPos at = ctx.getAbsolutePos(rel);
        for (int cx = (at.getX() - radius) >> 4; cx <= (at.getX() + radius) >> 4; cx++) {
            for (int cz = (at.getZ() - radius) >> 4; cz <= (at.getZ() + radius) >> 4; cz++) ctx.getWorld().setChunkForced(cx, cz, force);
        }
    }

    private static void done(TestContext ctx, ServerPlayerEntity... players) {
        for (ServerPlayerEntity p : players) ctx.getWorld().getServer().getPlayerManager().remove(p);
        ctx.complete();
    }

    // ---- The whole fight ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700, batchId = "gummss_fight")
    public void envyFightFallsIntoTheEyeAndLandsOnTheDrop(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ctx.assertTrue(BossFights.open(Sin.ENVY, world, base, Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity player = player(ctx, new BlockPos(5, 1, 5), 0);
        BossFights.enter(player, Sin.ENVY);
        ServerWorld arena = Arenas.world(server, Sin.ENVY);
        forceArena(arena, true);

        // The arena: a great eye of a floor over the void, walled round its rim, the exit frame waiting
        ctx.runAtTick(2, () -> {
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, EnvyArena.FLOOR_BLOCK_Y, 0)).isOf(Blocks.OBSIDIAN), "the pupil in the middle");
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, EnvyArena.FLOOR_BLOCK_Y, -1)).isOf(Blocks.QUARTZ_BLOCK), "a glint in the pupil");
            ctx.assertTrue(arena.getBlockState(new BlockPos(8, EnvyArena.FLOOR_BLOCK_Y, 0)).isOf(Blocks.CALCITE), "the white of the eye");
            ctx.assertTrue(arena.getBlockState(new BlockPos(50, EnvyArena.FLOOR_Y, 0)).isOf(Blocks.POLISHED_BLACKSTONE_BRICK_WALL), "the wall round the rim");
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, EnvyArena.FLOOR_BLOCK_Y - 40, 0)).isAir(), "nothing under it but the void");
            ctx.assertTrue(arena.getBlockState(new BlockPos(EnvyArena.RADIUS + 3, EnvyArena.FLOOR_BLOCK_Y, 0)).isAir(), "and nothing past the rim");
            BlockPos feet = BlockPos.ofFloored(EnvyArena.PLAYER_SPAWN);
            ctx.assertTrue(!arena.getBlockState(feet.down()).isAir() && arena.getBlockState(feet).isAir() && arena.getBlockState(feet.up()).isAir(),
                    "room to stand where players arrive");
            BlockPos portal = EnvyArena.EXIT_PORTAL;
            ctx.assertTrue(arena.getBlockState(portal.down()).isOf(Blocks.POLISHED_BLACKSTONE_BRICKS), "frame under the exit portal");
            ctx.assertTrue(arena.getBlockState(portal.add(-1, -1, 0)).isOf(Blocks.OXIDIZED_CHISELED_COPPER), "copper corner");
            for (int w = 0; w < 2; w++) {
                for (int h = 0; h < 3; h++) ctx.assertTrue(arena.getBlockState(portal.add(w, h, 0)).isAir(), "the frame starts empty");
            }
            ctx.assertTrue(EnvyArena.eyes().size() > 40, "eyes everywhere");
        });

        // Behind the wheel: frozen on the floor, the song already playing (a short intro starts it at once)
        ctx.runAtTick(JOIN / 2, () -> {
            ctx.assertTrue(player.getServerWorld() == arena, "player should be in the Envy arena");
            ctx.assertTrue(player.squaredDistanceTo(EnvyArena.PLAYER_SPAWN) < 1, "player should arrive at the spawn");
            ctx.assertTrue(Cinematics.isFrozen(player), "frozen during the intro");
            ctx.assertTrue(BossFights.session(Sin.ENVY).song() == GummssEntity.SONG, "Test & Recognise should be playing");
        });

        // The portal closes: everyone hangs high over the arena in a ring round him, frozen, and they fall together
        ctx.runAtTick(JOIN + 3, () -> {
            FightSession session = BossFights.session(Sin.ENVY);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(arena.getEntity(session.bossId()) instanceof GummssEntity, "the boss should be GUMMSS");
            GummssEntity boss = (GummssEntity) arena.getEntity(session.bossId());
            ctx.assertEquals(boss.currentAction(), "ENTRANCE", "action");
            ctx.assertTrue(boss.getY() > EnvyArena.FLOOR_Y + GummssTimeline.FALL_HEIGHT - 2, "he starts high over the arena, at " + boss.getY());
            ctx.assertTrue(player.getY() > EnvyArena.FLOOR_Y + GummssTimeline.FALL_HEIGHT - 2, "and so does the player, at " + player.getY());
            double ring = Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ());
            ctx.assertTrue(Math.abs(ring - GummssTimeline.RING_RADIUS) < 0.5, "in the ring round him, " + ring + " out");
            ctx.assertTrue(Cinematics.isFrozen(player), "frozen while they fall");
            ctx.assertTrue(player.hasStatusEffect(StatusEffects.SLOW_FALLING), "falling slowly");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health while he falls");
        });
        ctx.runAtTick(JOIN + 200, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            ctx.assertEquals(boss.currentAction(), "ENTRANCE", "still falling");
            ctx.assertTrue(boss.getY() < EnvyArena.FLOOR_Y + GummssTimeline.FALL_HEIGHT - 5 && boss.getY() > EnvyArena.FLOOR_Y + 5,
                    "partway down, at " + boss.getY());
        });

        // They land as the bass drops (21.575 s into the song), hurt by nothing
        int drop = GummssTimeline.DROP_TICK;
        ctx.runAtTick(drop - 6, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            ctx.assertTrue(boss.getY() > EnvyArena.FLOOR_Y + 0.3, "not down yet just before the drop, at " + boss.getY());
            ctx.assertTrue(Cinematics.isFrozen(player), "still frozen");
        });
        ctx.runAtTick(drop + 4, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            ctx.assertTrue(Math.abs(boss.getY() - EnvyArena.FLOOR_Y) < 0.1 && boss.getPos().distanceTo(EnvyArena.CENTER) < 1,
                    "he lands in the middle of the eye, at " + boss.getPos());
            ctx.assertTrue(Math.abs(player.getY() - EnvyArena.FLOOR_Y) < 0.1, "the player lands on the floor, at " + player.getY());
            ctx.assertFalse(Cinematics.isFrozen(player), "free to move once landed");
            ctx.assertFalse(player.hasStatusEffect(StatusEffects.SLOW_FALLING), "no slow falling left over");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "nobody is hurt landing");
            ctx.assertTrue(boss.isUntouchable(), "he rises from his landing before the fight is on");
        });

        int fighting = drop + GummssTimeline.LAND_TICKS + 6;
        ctx.runAtTick(fighting, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            ctx.assertFalse(boss.isUntouchable(), "and can be hurt once he's up");
            ctx.assertEquals(boss.getMaxHealth(), GummssEntity.MAX_HEALTH * GummssTimeline.healthScale(1), "health for one player");
            // Keep the test's one player alive through whatever he does next
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
            // Over the rim: his mind drags you back
            player.teleport(arena, EnvyArena.RADIUS + 6, EnvyArena.FLOOR_Y, 0.5, 0, 0);
        });
        ctx.runAtTick(fighting + 20, () -> {
            ctx.assertTrue(EnvyArena.onFloor(player.getPos()), "dragged back onto the floor, at " + player.getPos());
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            // No second phase, however low he gets
            boss.setHealth(boss.getMaxHealth() * 0.2f);
        });
        ctx.runAtTick(fighting + 25, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            ctx.assertEquals(boss.getPhase(), 1, "phase");
            ctx.assertTrue(BossFights.session(Sin.ENVY).song() == GummssEntity.SONG, "the same song plays on");
            boss.kill();
            ctx.assertTrue(BossFights.session(Sin.ENVY).stage() == FightSession.Stage.WON, "fight should be won");
            ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.ENVY), "Envy should be marked defeated");
        });
        ctx.runAtTick(fighting + 29, () -> {
            BossFights.forceEnd(server, Sin.ENVY);
            FmsWorldState.get(server).clearDefeated(Sin.ENVY);
            server.getPlayerManager().remove(player);
            forceArena(arena, false);
            ctx.complete();
        });
    }

    // ---- His attacks, one at a time ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160, batchId = "gummss_catch")
    public void arrowsAreCaughtAndSentBack(TestContext ctx) {
        floor(ctx);
        ServerWorld world = ctx.getWorld();
        GummssEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        ServerPlayerEntity archer = player(ctx, new BlockPos(7, 1, 3), 90);
        whenReady(ctx, boss, () -> {
            float health = boss.getHealth();
            ArrowEntity arrow = new ArrowEntity(world, archer, new ItemStack(Items.ARROW), null);
            arrow.setPosition(boss.getX() + 1, boss.getY() + 1.4, boss.getZ());
            world.spawnEntity(arrow);
            boss.forceNextCatch();
            ctx.assertFalse(boss.damage(world.getDamageSources().arrow(arrow, archer), 6f), "a caught arrow doesn't hurt him");
            ctx.assertEquals(boss.getHealth(), health, "health");
            ctx.assertTrue(arrow.isRemoved(), "the arrow is taken out of the air");
            // It goes round him once, then back at the archer
            ctx.waitAndRun(28, () -> {
                List<ArrowEntity> back = world.getEntitiesByClass(ArrowEntity.class, boss.getBoundingBox().expand(30), a -> a.getOwner() == boss);
                ctx.assertFalse(back.isEmpty(), "an arrow of his own should be on its way back");
                boss.discard();
                done(ctx, archer);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 180, batchId = "gummss_reckoning")
    public void reckoningCatchesOnlyWhoeverIsOnTheGround(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity standing = player(ctx, new BlockPos(7, 1, 3), 0);
        ServerPlayerEntity jumping = player(ctx, new BlockPos(0, 1, 3), 0);
        standing.setOnGround(true);
        jumping.setOnGround(false);
        whenReady(ctx, boss, () -> {
            start(boss, "RECKONING");
            // The ripple runs out from him at half a block a tick once he's cast it (26 ticks): 4 blocks in 8 more
            ctx.waitAndRun(26 + 14, () -> {
                Map<UUID, Float> caught = reckoningCaught(boss);
                ctx.assertTrue(caught.containsKey(standing.getUuid()), "the one standing on the floor is caught");
                ctx.assertFalse(caught.containsKey(jumping.getUuid()), "the one in the air as it passed is not");
                boss.discard();
                done(ctx, standing, jumping);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 180, batchId = "gummss_eye")
    public void theEvilEyePunishesOnlyThoseLookingAtHim(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        // Both face west: the one east of him is looking at him, the one west of him is looking away
        ServerPlayerEntity looking = player(ctx, new BlockPos(7, 1, 4), 90);
        ServerPlayerEntity away = player(ctx, new BlockPos(1, 1, 4), 90);
        whenReady(ctx, boss, () -> {
            start(boss, "EVIL_EYE");
            ctx.waitAndRun(20, () -> ctx.assertTrue(boss.isStaring(), "every eye turns on them"));
            ctx.waitAndRun(56, () -> {
                ctx.assertFalse(boss.isStaring(), "the stare ends");
                ctx.assertTrue(looking.hasStatusEffect(StatusEffects.NAUSEA), "looking at him when it ended: the mind reels");
                ctx.assertTrue(looking.getHealth() < looking.getMaxHealth(), "and it hurts");
                ctx.assertFalse(away.hasStatusEffect(StatusEffects.NAUSEA), "looking away: spared");
                ctx.assertEquals(away.getHealth(), away.getMaxHealth(), "unhurt");
                boss.discard();
                done(ctx, looking, away);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160, batchId = "gummss_images")
    public void mirrorImagesBreakAtATouchAndGoWithHim(TestContext ctx) {
        // His images appear in a ring round him, out past the test's walls: ground for them there, and the chunks loaded
        forceAround(ctx, new BlockPos(4, 0, 4), 14, true);
        floor(ctx, -8, 16);
        ServerWorld world = ctx.getWorld();
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity player = player(ctx, new BlockPos(1, 1, 1), 0);
        whenReady(ctx, boss, () -> {
            start(boss, "ILLUSIONS");
            ctx.waitAndRun(22, () -> {
                ctx.assertEquals(boss.illusionCount(), 3, "three more of him");
                List<GummssIllusionEntity> images = world.getEntitiesByClass(GummssIllusionEntity.class, new Box(boss.getBlockPos()).expand(20), e -> true);
                ctx.assertEquals(images.size(), 3, "images in the world");
                float health = boss.getHealth();
                images.get(0).damage(world.getDamageSources().playerAttack(player), 1f);
                ctx.assertTrue(images.get(0).isRemoved(), "one touch breaks an image");
                ctx.assertEquals(boss.getHealth(), health, "and doesn't hurt him");
            });
            ctx.waitAndRun(25, () -> {
                ctx.assertEquals(boss.illusionCount(), 2, "two left");
                boss.kill();
            });
            ctx.waitAndRun(27, () -> {
                ctx.assertTrue(world.getEntitiesByClass(GummssIllusionEntity.class, new Box(boss.getBlockPos()).expand(30), e -> true).isEmpty(),
                        "they go when he does");
                forceAround(ctx, new BlockPos(4, 0, 4), 14, false);
                done(ctx, player);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 140, batchId = "gummss_swap")
    public void swappingTradesPlacesWithWhoeverIsFurthest(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(0, 1, 0));
        ServerPlayerEntity near = player(ctx, new BlockPos(2, 1, 1), 0);
        ServerPlayerEntity far = player(ctx, new BlockPos(7, 1, 7), 0);
        Vec3d farStart = far.getPos();
        whenReady(ctx, boss, () -> {
            Vec3d bossStart = boss.getPos();
            start(boss, "SWAP");
            ctx.waitAndRun(20, () -> {
                ctx.assertTrue(boss.getPos().distanceTo(farStart) < 1, "he's where the furthest one stood, at " + boss.getPos());
                ctx.assertTrue(far.getPos().distanceTo(bossStart) < 1, "and they're where he stood, at " + far.getPos());
                boss.discard();
                done(ctx, near, far);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160, batchId = "gummss_poison")
    public void poisonSlashPoisonsAndLeavesACloud(TestContext ctx) {
        floor(ctx);
        ServerWorld world = ctx.getWorld();
        GummssEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = player(ctx, new BlockPos(5, 1, 3), 90);
        whenReady(ctx, boss, () -> {
            start(boss, "POISON_SLASH");
            ctx.waitAndRun(18, () -> {
                ctx.assertTrue(player.hasStatusEffect(StatusEffects.POISON), "his cuts poison");
                ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "and hurt");
            });
            ctx.waitAndRun(28, () -> {
                List<AreaEffectCloudEntity> clouds = world.getEntitiesByClass(AreaEffectCloudEntity.class, boss.getBoundingBox().expand(10), c -> c.getOwner() == boss);
                ctx.assertFalse(clouds.isEmpty(), "a cloud of poison where he stood");
                boss.discard();
            });
            ctx.waitAndRun(30, () -> {
                ctx.assertTrue(world.getEntitiesByClass(AreaEffectCloudEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(20), c -> true).isEmpty(),
                        "and it goes with him");
                done(ctx, player);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160, batchId = "gummss_levitation")
    public void levitationLiftsEveryoneThenTheQuiver(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity a = player(ctx, new BlockPos(0, 1, 0), 0);
        ServerPlayerEntity b = player(ctx, new BlockPos(7, 1, 7), 0);
        whenReady(ctx, boss, () -> {
            start(boss, "LEVITATION");
            ctx.waitAndRun(26, () -> {
                for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) {
                    StatusEffectInstance effect = p.getStatusEffect(StatusEffects.LEVITATION);
                    ctx.assertTrue(effect != null && effect.getAmplifier() == 1 && effect.getDuration() > 90, "everyone levitates for 5 s, got " + effect);
                }
            });
            ctx.waitAndRun(34, () -> {
                ctx.assertEquals(boss.currentAction(), "QUIVER", "and he looses arrows at them while they hang there");
                boss.discard();
                done(ctx, a, b);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 180, batchId = "gummss_judgement")
    public void judgementCrushesTheCrowdItFallsOn(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(6, 1, 1));
        // Two together in one corner, one alone in the far corner, over 9 blocks off
        ServerPlayerEntity a1 = player(ctx, new BlockPos(0, 1, 0), 0);
        ServerPlayerEntity a2 = player(ctx, new BlockPos(1, 1, 0), 0);
        ServerPlayerEntity alone = player(ctx, new BlockPos(7, 1, 7), 0);
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{a1, a2, alone}) {
            // Enough to live through it, for the test
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 3));
        }
        whenReady(ctx, boss, () -> {
            start(boss, "JUDGEMENT");
            ctx.waitAndRun(30, () -> ctx.assertEquals(a1.getHealth(), a1.getMaxHealth(), "nothing yet: the beam marks the spot first"));
            ctx.waitAndRun(56, () -> {
                ctx.assertTrue(a1.getHealth() < a1.getMaxHealth() - 8 && a2.getHealth() < a2.getMaxHealth() - 8,
                        "the two under it are crushed, " + a1.getHealth() + " and " + a2.getHealth());
                ctx.assertEquals(alone.getHealth(), alone.getMaxHealth(), "the one far off is untouched");
                boss.discard();
                done(ctx, a1, a2, alone);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 160, batchId = "gummss_upside_down")
    public void upsideDownFlipsEveryoneForASecondThenBack(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity a = player(ctx, new BlockPos(0, 1, 0), 0);
        ServerPlayerEntity b = player(ctx, new BlockPos(7, 1, 7), 0);
        whenReady(ctx, boss, () -> {
            start(boss, "UPSIDE_DOWN");
            ctx.waitAndRun(10, () -> ctx.assertFalse(MagicSpells.isFlipped(a), "not before his cast is done"));
            ctx.waitAndRun(26, () -> {
                ctx.assertTrue(MagicSpells.isFlipped(a) && MagicSpells.isFlipped(b), "the Tome of Upside Down flips everyone");
            });
            ctx.waitAndRun(46, () -> {
                ctx.assertFalse(MagicSpells.isFlipped(a) || MagicSpells.isFlipped(b), "and back a second later");
                // Cut short, nobody stays flipped
                start(boss, "UPSIDE_DOWN");
            });
            ctx.waitAndRun(46 + 25, () -> {
                ctx.assertTrue(MagicSpells.isFlipped(a), "flipped again");
                boss.discard();
                ctx.assertFalse(MagicSpells.isFlipped(a) || MagicSpells.isFlipped(b), "the right way up when he's gone");
                done(ctx, a, b);
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 700, batchId = "gummss_fight_ten")
    public void tenPlayersFallInARingAndAllLandUnhurt(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        ctx.assertTrue(BossFights.open(Sin.ENVY, world, ctx.getAbsolutePos(new BlockPos(2, 1, 2)), Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity[] players = new ServerPlayerEntity[10];
        for (int i = 0; i < players.length; i++) {
            players[i] = player(ctx, new BlockPos(i % 8, 1, 5), 0);
            BossFights.enter(players[i], Sin.ENVY);
        }
        ServerWorld arena = Arenas.world(server, Sin.ENVY);
        forceArena(arena, true);

        ctx.runAtTick(JOIN + 3, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            double radius = GummssTimeline.ringRadius(players.length);
            for (ServerPlayerEntity p : players) {
                ctx.assertTrue(p.getServerWorld() == arena && Cinematics.isFrozen(p), "everyone in the arena, frozen");
                double ring = Math.hypot(p.getX() - boss.getX(), p.getZ() - boss.getZ());
                ctx.assertTrue(Math.abs(ring - radius) < 0.5 && p.getY() > EnvyArena.FLOOR_Y + 50, "everyone in the ring high over the arena");
                for (ServerPlayerEntity o : players) {
                    if (o != p) ctx.assertTrue(o.squaredDistanceTo(p) > 3 * 3, "nobody crowded in the ring");
                }
            }
        });
        ctx.runAtTick(GummssTimeline.DROP_TICK + 4, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            float wanted = GummssEntity.MAX_HEALTH * GummssTimeline.healthScale(players.length);
            ctx.assertTrue(Math.abs(boss.effectiveMaxHealth() - wanted) < 1, "health for ten: " + boss.effectiveMaxHealth() + " of " + wanted);
            ctx.assertTrue(boss.getMaxHealth() <= 1024, "his health itself stays under Minecraft's cap");
            for (ServerPlayerEntity p : players) {
                ctx.assertTrue(Math.abs(p.getY() - EnvyArena.FLOOR_Y) < 0.1 && EnvyArena.onFloor(p.getPos()), "everyone lands on the floor, at " + p.getPos());
                ctx.assertFalse(Cinematics.isFrozen(p), "and is free to move");
                ctx.assertEquals(p.getHealth(), p.getMaxHealth(), "nobody is hurt landing");
            }
            BossFights.forceEnd(server, Sin.ENVY);
        });
        ctx.runAtTick(GummssTimeline.DROP_TICK + 8, () -> {
            for (ServerPlayerEntity p : players) server.getPlayerManager().remove(p);
            forceArena(arena, false);
            ctx.complete();
        });
    }
}
