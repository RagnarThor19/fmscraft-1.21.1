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

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1600, batchId = "gummss_fight")
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
            // No second phase, however low he gets (held: no Covet, nothing new)
            boss.holdStill();
            boss.setHealth(boss.getMaxHealth() * 0.2f);
        });
        ctx.runAtTick(fighting + 25, () -> {
            GummssEntity boss = (GummssEntity) arena.getEntity(BossFights.session(Sin.ENVY).bossId());
            ctx.assertEquals(boss.getPhase(), 1, "phase");
            ctx.assertTrue(BossFights.session(Sin.ENVY).song() == GummssEntity.SONG, "the same song plays on");
            // Struck down the moment he can be (not in the middle of something he can't be hurt in)
            whenReady(ctx, boss, () -> {
                boss.setHealth(3);
                boss.damage(arena.getDamageSources().magic(), 20);
                ctx.assertTrue(boss.isAlive() && boss.isDying(), "the last blow: he comes apart, at " + boss.getHealth());
                ctx.assertTrue(BossFights.session(Sin.ENVY).stage() == FightSession.Stage.WON, "fight should be won");
                ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.ENVY), "Envy should be marked defeated");
                ctx.assertFalse(BossFights.rewarded(Sin.ENVY), "the portal and the wheel wait for him");
                float health = boss.getHealth();
                boss.damage(arena.getDamageSources().playerAttack(player), 20);
                ctx.assertEquals(boss.getHealth(), health, "nothing more reaches him");
            UUID[] bossId = {boss.getUuid()};
            ctx.waitAndRun(GummssEntity.DEATH_HONOUR_AT + 5, () -> {
                ctx.assertEquals(boss.poseState(), GummssEntity.POSE_SKY, "looking up into the sky");
                ctx.assertEquals(boss.echoCount(), 0, "nothing else yet");
                var advancement = server.getAdvancementLoader().get(net.minecraft.util.Identifier.of(net.ragnar.fmscraft.FmsCraft.MOD_ID, "defeated_envy"));
                ctx.assertTrue(advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone(), "Defeated GUMMSS, during it");
            });
            ctx.waitAndRun(GummssEntity.DEATH_GLITCH + 60, () -> {
                ctx.assertTrue(boss.echoCount() >= 15, "glitching: copies of him everywhere, " + boss.echoCount());
                long up = arena.getEntitiesByClass(net.ragnar.fmscraft.boss.envy.GummssColossusEntity.class, boss.getBoundingBox().expand(12), e -> true).size();
                ctx.assertTrue(up >= 15, "in the world, " + up);
                ctx.assertTrue(arena.getBlockState(EnvyArena.EXIT_PORTAL).isAir(), "no portal yet");
            });
            ctx.waitAndRun(GummssEntity.DEATH_SORROW + 10, () -> {
                ctx.assertTrue(boss.isAlive(), "still here");
                ctx.assertEquals(boss.echoCount(), 0, "the copies gone");
                ctx.assertTrue(arena.getEntitiesByClass(net.ragnar.fmscraft.boss.envy.GummssColossusEntity.class, boss.getBoundingBox().expand(12), e -> true).isEmpty(), "every one");
                ctx.assertFalse(boss.isInvisible(), "him, alone");
                ctx.assertEquals(boss.poseState(), GummssEntity.POSE_SORROW, "looking down, sad");
            });
            ctx.waitAndRun(GummssEntity.DEATH_TICKS + 5, () -> {
                ctx.assertTrue(arena.getEntity(bossId[0]) == null || !arena.getEntity(bossId[0]).isAlive(), "and he's gone");
                ctx.assertFalse(BossFights.rewarded(Sin.ENVY), "a moment's quiet before the portal");
                ctx.assertTrue(arena.getBlockState(EnvyArena.EXIT_PORTAL).isAir(), "no portal yet");
            });
            ctx.waitAndRun(GummssEntity.DEATH_TICKS + GummssEntity.DEATH_REWARDS_AFTER + 4, () -> {
                ctx.assertTrue(BossFights.rewarded(Sin.ENVY), "then the portal and the wheel");
                ctx.assertTrue(arena.getBlockState(EnvyArena.EXIT_PORTAL).isOf(net.ragnar.fmscraft.block.ModBlocks.SIN_PORTAL), "the portal home is open");
                BossFights.forceEnd(server, Sin.ENVY);
                FmsWorldState.get(server).clearDefeated(Sin.ENVY);
                server.getPlayerManager().remove(player);
                forceArena(arena, false);
                ctx.complete();
            });
            });
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

    // ---- Rubble Barrage, and his fall from the sky at half health ----

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "gummss_rubble")
    public void rubbleBarrageTearsUpTheFloorHurlsItAndMendsIt(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity a = player(ctx, new BlockPos(0, 1, 0), 0);
        ServerPlayerEntity b = player(ctx, new BlockPos(7, 1, 7), 0);
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 3));
        whenReady(ctx, boss, () -> {
            start(boss, "RUBBLE");
            Map<BlockPos, net.minecraft.block.BlockState> torn = new java.util.LinkedHashMap<>();
            ctx.waitAndRun(20, () -> {
                int count = boss.rubbleCount();
                ctx.assertTrue(count >= 6 && count <= 12, "6 to 12 blocks torn up, got " + count);
                torn.putAll(boss.rubbleHoles());
                ctx.assertFalse(torn.isEmpty(), "holes torn in the floor");
                for (BlockPos pos : torn.keySet()) ctx.assertTrue(ctx.getWorld().getBlockState(pos).isAir(), "a hole at " + pos);
                long blocks = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                        boss.getBoundingBox().expand(8), e -> true).size();
                ctx.assertTrue(blocks >= count, "the blocks hang in the air round him, " + blocks + " of " + count);
                ctx.assertEquals(a.getHealth(), a.getMaxHealth(), "nothing thrown yet");
            });
            ctx.waitAndRun(200, () -> {
                ctx.assertTrue(a.getHealth() < a.getMaxHealth() || b.getHealth() < b.getMaxHealth(),
                        "the barrage hurt someone, " + a.getHealth() + " and " + b.getHealth());
                ctx.assertTrue(boss.currentAction().equals("MOVE"), "done, got " + boss.currentAction());
                for (var hole : torn.entrySet()) {
                    ctx.assertTrue(ctx.getWorld().getBlockState(hole.getKey()) == hole.getValue(), "the floor at " + hole.getKey() + " is mended");
                }
                long left = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                        boss.getBoundingBox().expand(30), e -> true).size();
                ctx.assertEquals((int) left, 0, "no blocks left hanging");
                boss.discard();
                done(ctx, a, b);
            });
        });
    }

    /** His fall from the sky (one of his attacks after his Covet): up into the dark, flipped, and down heavy on the crowd. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "gummss_ascend")
    public void heFallsUpThenQuakesWhereHeLands(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity a = player(ctx, new BlockPos(1, 1, 1), 0);
        ServerPlayerEntity b = player(ctx, new BlockPos(2, 1, 1), 0);
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 600, 2));
        whenReady(ctx, boss, () -> {
            double floorY = boss.getY();
            start(boss, "ASCEND");
            ctx.waitAndRun(10, () -> ctx.assertFalse(boss.isFlipped(), "not before his cast is done"));
            ctx.waitAndRun(40, () -> {
                ctx.assertTrue(boss.isFlipped(), "upside down, falling up");
                ctx.assertTrue(boss.getY() > floorY + 3, "up off the floor, at " + boss.getY());
            });
            // Cast 24, up 32, hang 14 (righted halfway), down 14
            ctx.waitAndRun(80, () -> {
                ctx.assertFalse(boss.isFlipped(), "the right way up again for the drop");
                ctx.assertEquals(a.getHealth(), a.getMaxHealth(), "nothing yet");
            });
            ctx.waitAndRun(90, () -> {
                ctx.assertTrue(Math.abs(boss.getY() - floorY) < 0.6, "down on the floor, at " + boss.getY());
                ctx.assertTrue(a.getHealth() < a.getMaxHealth() - 6 && b.getHealth() < b.getMaxHealth() - 6,
                        "heavy where he lands, " + a.getHealth() + " and " + b.getHealth());
                ctx.assertEquals(boss.getPhase(), 1, "no second phase");
                ctx.assertTrue(BossFights.session(Sin.ENVY) == null || BossFights.session(Sin.ENVY).song() == GummssEntity.SONG, "same song");
            });
            ctx.waitAndRun(120, () -> {
                ctx.assertFalse(boss.isAwake(), "his fall alone doesn't wake the void");
                boss.discard();
            });
            ctx.waitAndRun(122, () -> {
                long left = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                        new net.minecraft.util.math.Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(20), e -> true).size();
                ctx.assertEquals((int) left, 0, "nothing of it is left behind");
                done(ctx, a, b);
            });
        });
    }

    /** Below half health his Covet comes by itself, as soon as he's between attacks, whatever his rest. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "gummss_covet_auto")
    public void halfHealthBringsHisCovetByItself(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity a = player(ctx, new BlockPos(1, 1, 1), 0);
        a.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
        whenReady(ctx, boss, () -> {
            setHeldStill(boss, false);
            boss.setHealth(boss.getMaxHealth() * 0.45f);
            waitForAction(ctx, boss, "COVET", 40, () -> {
                boss.discard();
                done(ctx, a);
            });
        });
    }

    // ---- His Covet, at half health ----

    /**
     * He's gone, the song with him; four colossal copies of him rise and cast, a world of blocks forming over him; it
     * comes down on the beat and crushes whoever is still inside its reach, not whoever got out; they go, he comes back,
     * awake, with the green blocks round him. Nothing hurts him meanwhile, and nothing is left behind.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 420, batchId = "gummss_covet")
    public void covetComesDownOnWhoeverStaysInside(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity inside = player(ctx, new BlockPos(3, 1, 3), 0), outside = player(ctx, new BlockPos(0, 1, 0), 0);
        // Enough health to show what it did
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{inside, outside}) {
            p.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(400);
            p.setHealth(400);
        }
        int fade = GummssTimeline.COVET_FADE, castEnd = fade + GummssTimeline.COVET_CAST + GummssTimeline.COVET_DROP;
        whenReady(ctx, boss, () -> {
            boss.setHealth(boss.getMaxHealth() * 0.45f);
            start(boss, "COVET");
            ctx.waitAndRun(4, () -> {
                ctx.assertEquals(boss.currentAction(), "COVET", "his Covet");
                ctx.assertTrue(boss.isInvisible() && boss.isUntouchable(), "he's gone, and can't be hurt");
                ctx.assertTrue(boss.covetTicks() > 0, "the void knows (it blinks, the song fades)");
                ctx.assertEquals(boss.colossusCount(), 0, "nothing has risen yet");
            });
            ctx.waitAndRun(fade + GummssTimeline.COVET_RISE + 30, () -> {
                ctx.assertEquals(boss.colossusCount(), 4, "four of him");
                List<net.ragnar.fmscraft.boss.envy.GummssColossusEntity> four = ctx.getWorld().getEntitiesByClass(
                        net.ragnar.fmscraft.boss.envy.GummssColossusEntity.class, boss.getBoundingBox().expand(30), e -> true);
                ctx.assertEquals(four.size(), 4, "in the world");
                for (var c : four) {
                    double scale = c.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_SCALE);
                    ctx.assertTrue(scale > 2, "huge, " + scale);
                    ctx.assertEquals(c.poseState(), GummssEntity.POSE_COVET, "arms raised");
                    ctx.assertFalse(c.damage(ctx.getWorld().getDamageSources().playerAttack(inside), 10), "nothing reaches them");
                }
                ctx.assertTrue(boss.hoardBlocks() > 10, "a world forming between them, " + boss.hoardBlocks() + " blocks so far");
                int impact = boss.covetImpact();
                ctx.assertTrue(impact >= castEnd && impact < castEnd + 14, "an eight-second cast, landing on the beat, at " + impact);
                float health = boss.getHealth();
                boss.damage(ctx.getWorld().getDamageSources().playerAttack(inside), 10);
                ctx.assertEquals(boss.getHealth(), health, "nothing reaches him either");
                ctx.assertEquals(inside.getHealth(), inside.getMaxHealth(), "nothing has come down yet");
            });
            ctx.waitAndRun(castEnd + 14 + 4, () -> {
                float lost = inside.getMaxHealth() - inside.getHealth();
                ctx.assertTrue(lost > 40, "it came down on the one who stayed, " + lost);
                ctx.assertEquals(outside.getHealth(), outside.getMaxHealth(), "not on the one who got out");
            });
            int back = castEnd + 14 + GummssTimeline.COVET_LINGER + GummssTimeline.COVET_GONE + 4;
            ctx.waitAndRun(castEnd + 14 + GummssTimeline.COVET_LINGER + 3, () -> {
                ctx.assertEquals(boss.colossusCount(), 0, "the four of him are gone");
                ctx.assertTrue(ctx.getWorld().getEntitiesByClass(net.ragnar.fmscraft.boss.envy.GummssColossusEntity.class,
                        boss.getBoundingBox().expand(30), e -> true).isEmpty(), "every one");
            });
            ctx.waitAndRun(back, () -> {
                ctx.assertEquals(boss.currentAction(), "MOVE", "on with the fight");
                ctx.assertFalse(boss.isInvisible() || boss.isUntouchable(), "he's back, and can be hurt");
                ctx.assertTrue(boss.isAwake(), "the void is awake");
                ctx.assertEquals(boss.covetTicks(), 0, "and has stopped blinking");
                long green = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                        boss.getBoundingBox().expand(4), e -> e.getBlockState().isOf(Blocks.EMERALD_BLOCK) || e.getBlockState().isOf(Blocks.MOSS_BLOCK)).size();
                ctx.assertTrue(green >= 2, "green blocks drift round him from now on, found " + green);
                boss.discard();
            });
            ctx.waitAndRun(back + 2, () -> {
                long left = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                        boss.getBoundingBox().expand(13), e -> true).size();
                ctx.assertEquals((int) left, 0, "nothing of it is left behind");
                done(ctx, inside, outside);
            });
        });
    }

    /** Killed (or the fight ends) in the middle of it: the four of him and the world go with him. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "gummss_covet_cut")
    public void covetCutShortLeavesNothingBehind(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(4, 1, 4));
        ServerPlayerEntity a = player(ctx, new BlockPos(1, 1, 1), 0);
        a.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 4));
        whenReady(ctx, boss, () -> {
            start(boss, "COVET");
            ctx.waitAndRun(GummssTimeline.COVET_FADE + 60, () -> {
                ctx.assertEquals(boss.colossusCount(), 4, "mid-cast");
                boss.kill();
            });
            ctx.waitAndRun(GummssTimeline.COVET_FADE + 63, () -> {
                ctx.assertTrue(ctx.getWorld().getEntitiesByClass(net.ragnar.fmscraft.boss.envy.GummssColossusEntity.class,
                        boss.getBoundingBox().expand(13), e -> true).isEmpty(), "the four of him gone");
                long left = ctx.getWorld().getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class,
                        boss.getBoundingBox().expand(13), e -> true).size();
                ctx.assertEquals((int) left, 0, "and the world they were making");
                done(ctx, a);
            });
        });
    }


    // ---- Disguise ----

    private static final int GUISE_HIDDEN_AT = GummssEntity.GUISE_POINT + GummssEntity.CUP_DROP + 2;
    private static final int GUISE_WORN_AT = GummssEntity.GUISE_POINT + GummssEntity.CUP_DROP + GummssEntity.GUISE_SHUFFLE + 3;

    private static List<net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity> blocks(ServerWorld world, GummssEntity boss, double range,
                                                                                                  net.minecraft.block.Block... of) {
        return world.getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class, boss.getBoundingBox().expand(range), d -> {
            if (of.length == 0) return true;
            for (net.minecraft.block.Block b : of) if (d.getBlockState().isOf(b)) return true;
            return false;
        });
    }

    /** Disguise: the cups hide both of them, he takes one player's face (trading places if the cups say so), a hit knocks it off. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "gummss_disguise")
    public void disguiseHidesBothTradesFacesAndAHitBreaksIt(TestContext ctx) {
        floor(ctx);
        ServerWorld world = ctx.getWorld();
        GummssEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity[] players = {player(ctx, new BlockPos(6, 1, 1), 0), player(ctx, new BlockPos(6, 1, 6), 0), player(ctx, new BlockPos(1, 1, 6), 0)};
        whenReady(ctx, boss, () -> {
            Vec3d bossStart = boss.getPos();
            Vec3d[] starts = new Vec3d[players.length];
            for (int i = 0; i < players.length; i++) starts[i] = players[i].getPos();
            boss.forceGuiseSwap(true);
            start(boss, "DISGUISE");
            ctx.waitAndRun(10, () -> ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_OFF, "pointing at them first: nothing yet"));
            ctx.waitAndRun(GUISE_HIDDEN_AT, () -> {
                ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_HIDDEN, "both shut in the cups");
                ServerPlayerEntity wearer = world.getServer().getPlayerManager().getPlayer(boss.guisePlayer());
                ctx.assertTrue(wearer != null, "one of the players is under the other cup");
                ctx.assertTrue(Cinematics.isFrozen(wearer), "held still in it");
                float before = boss.getHealth();
                boss.damage(world.getDamageSources().playerAttack(players[0] == wearer ? players[1] : players[0]), 6);
                ctx.assertEquals(boss.getHealth(), before, "nothing reaches him in the cup");
                int walls = blocks(world, boss, 12, Blocks.EMERALD_BLOCK).size();
                ctx.assertTrue(walls >= 4, "two emerald cups, " + walls + " walls");
            });
            ctx.waitAndRun(GUISE_WORN_AT, () -> {
                ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_WORN, "the cups shattered: he's wearing their face");
                ServerPlayerEntity wearer = world.getServer().getPlayerManager().getPlayer(boss.guisePlayer());
                int w = java.util.Arrays.asList(players).indexOf(wearer);
                ctx.assertTrue(w >= 0, "one of them");
                ctx.assertFalse(Cinematics.isFrozen(wearer), "and they're free again");
                ctx.assertTrue(boss.getPos().distanceTo(starts[w]) < 1.5, "traded round: he's where they stood, at " + boss.getPos());
                ctx.assertTrue(wearer.getPos().distanceTo(bossStart) < 1.5, "and they're where he stood, at " + wearer.getPos());
                ctx.assertTrue(blocks(world, boss, 12, Blocks.VERDANT_FROGLIGHT, Blocks.MOSS_BLOCK).size() > 0, "green blocks flying from the cups");
                float before = boss.getHealth();
                boss.damage(world.getDamageSources().playerAttack(players[(w + 1) % players.length]), 6);
                ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_OFF, "found out: the face is knocked off him");
                ctx.assertEquals(boss.currentAction(), "EXPOSED", "and he reels");
                ctx.assertTrue(boss.getHealth() < before, "the hit lands");
            });
            ctx.waitAndRun(GUISE_WORN_AT + 40, () -> {
                ctx.assertTrue(blocks(world, boss, 20).isEmpty(), "every green block gone");
                boss.discard();
                done(ctx, players);
            });
        });
    }

    /** Left alone, the Disguise lasts its ten seconds, then the cups come down again and the faces go back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 420, batchId = "gummss_disguise_end")
    public void disguiseWearsOffAfterTenSeconds(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity[] players = {player(ctx, new BlockPos(6, 1, 1), 0), player(ctx, new BlockPos(6, 1, 6), 0), player(ctx, new BlockPos(1, 1, 6), 0)};
        int over = GUISE_WORN_AT + GummssEntity.GUISE_TICKS + GummssEntity.CUP_DROP + GummssEntity.GUISE_UNDO + 2;
        whenReady(ctx, boss, () -> {
            Vec3d bossStart = boss.getPos();
            boss.forceGuiseSwap(false);
            start(boss, "DISGUISE");
            ctx.waitAndRun(GUISE_WORN_AT, () -> {
                ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_WORN, "wearing a face");
                ctx.assertTrue(boss.getPos().distanceTo(bossStart) < 1.5, "not traded round this time");
            });
            ctx.waitAndRun(GUISE_WORN_AT + GummssEntity.GUISE_TICKS - 10, () ->
                    ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_WORN, "for ten seconds"));
            ctx.waitAndRun(over, () -> {
                ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_OFF, "the faces back where they belong");
                ctx.assertTrue(boss.guisePlayer() == null, "nobody's");
                for (ServerPlayerEntity p : players) ctx.assertFalse(Cinematics.isFrozen(p), "nobody left in a cup");
                ctx.assertFalse(boss.currentAction().equals("DISGUISE"), "and he's on to the next thing, " + boss.currentAction());
                boss.discard();
                done(ctx, players);
            });
        });
    }

    /** With only two of you there's nobody to hide among: no Disguise. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120, batchId = "gummss_disguise_few")
    public void noDisguiseWithTwoPlayers(TestContext ctx) {
        floor(ctx);
        GummssEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity a = player(ctx, new BlockPos(6, 1, 1), 0), b = player(ctx, new BlockPos(6, 1, 6), 0);
        whenReady(ctx, boss, () -> {
            start(boss, "DISGUISE");
            ctx.waitAndRun(4, () -> {
                ctx.assertEquals(boss.currentAction(), "MOVE", "he doesn't even start it");
                ctx.assertEquals(boss.guiseStage(), GummssEntity.GUISE_OFF, "and wears nobody's face");
                boss.discard();
                done(ctx, a, b);
            });
        });
    }

    private static void waitForAction(TestContext ctx, GummssEntity boss, String action, int ticksLeft, Runnable then) {
        if (boss.currentAction().equals(action)) then.run();
        else if (ticksLeft <= 0) ctx.throwGameTestException("he should have started " + action + ", is doing " + boss.currentAction());
        else ctx.waitAndRun(1, () -> waitForAction(ctx, boss, action, ticksLeft - 1, then));
    }

    private static void setHeldStill(GummssEntity boss, boolean still) {
        try {
            Field field = GummssEntity.class.getDeclaredField("heldStill");
            field.setAccessible(true);
            field.setBoolean(boss, still);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
