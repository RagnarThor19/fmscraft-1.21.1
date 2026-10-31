package net.ragnar.fmscraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
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
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.wrath.FlameRoot;
import net.ragnar.fmscraft.boss.wrath.OrnamixEntity;
import net.ragnar.fmscraft.boss.wrath.OrnamixFireballEntity;
import net.ragnar.fmscraft.boss.wrath.OrnamixTimeline;
import net.ragnar.fmscraft.boss.wrath.WrathArena;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.fight.FightSession;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * ORNAMIX, the Wrath boss. The full fight test uses the real colosseum (no other test opens a Wrath fight); the others
 * put him on a stone floor in the test world and start his attacks by hand.
 */
public class OrnamixGameTests implements FabricGameTest {
    private static final int JOIN = 40;

    /** Like TestContext's mock player, but in survival: creative players aren't fought, rooted or frozen. */
    private static ServerPlayerEntity survivalPlayer(ServerWorld world, Vec3d at) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-wrath-player");
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

    private static void forceColosseum(ServerWorld arena, boolean force) {
        for (int cx = -5; cx <= 4; cx++) {
            for (int cz = -5; cz <= 4; cz++) arena.setChunkForced(cx, cz, force);
        }
    }

    private static void floor(TestContext ctx, int size) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) ctx.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static OrnamixEntity spawn(TestContext ctx, BlockPos rel) {
        OrnamixEntity boss = ModBossEntities.ORNAMIX.create(ctx.getWorld());
        boss.setSin(Sin.WRATH);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        ctx.getWorld().spawnEntity(boss);
        return boss;
    }

    /** Starts one of his attacks by name (the attacks are private to him). */
    private static void start(OrnamixEntity boss, String attack) {
        try {
            Class<?> action = Class.forName(OrnamixEntity.class.getName() + "$Action");
            Method method = OrnamixEntity.class.getDeclaredMethod("startAttack", action);
            method.setAccessible(true);
            method.invoke(boss, Enum.valueOf(action.asSubclass(Enum.class), attack));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean solid(ServerWorld world, BlockPos pos) {
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 600)
    public void wrathFightPlaysTheIntroThenOrnamix(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ctx.assertTrue(BossFights.open(Sin.WRATH, world, base, Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity player = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(5, 1, 5))));
        BossFights.enter(player, Sin.WRATH);
        ServerWorld arena = Arenas.world(server, Sin.WRATH);
        // Mock players don't tick, so they don't keep the chunks around them loaded the way real ones do
        forceColosseum(arena, true);

        // The colosseum is built, the pit closed in, the exit frame waiting, and everything on fire
        ctx.runAtTick(2, () -> {
            for (Vec3d spot : new Vec3d[]{WrathArena.PLAYER_SPAWN, WrathArena.CENTER}) {
                BlockPos feet = BlockPos.ofFloored(spot);
                ctx.assertTrue(arena.getBlockState(feet.down()).isOf(Blocks.SMOOTH_SANDSTONE), "sand under " + spot);
                ctx.assertTrue(arena.getBlockState(feet).isAir() && arena.getBlockState(feet.up()).isAir(), "room to stand at " + spot);
            }
            ctx.assertTrue(WrathArena.inPit(WrathArena.PLAYER_SPAWN) && WrathArena.inPit(WrathArena.CENTER), "both in the pit");
            for (Direction way : Direction.Type.HORIZONTAL) {
                boolean shut = false;
                for (int r = 38; r <= 46 && !shut; r++) {
                    BlockPos feet = new BlockPos(way.getOffsetX() * r, WrathArena.FLOOR_Y, way.getOffsetZ() * r);
                    shut = solid(arena, feet) || solid(arena, feet.up());
                }
                ctx.assertTrue(shut, "no way out of the pit to the " + way);
            }
            BlockPos portal = WrathArena.EXIT_PORTAL;
            ctx.assertTrue(arena.getBlockState(portal.down()).isOf(Blocks.POLISHED_BLACKSTONE_BRICKS), "frame under the exit portal");
            ctx.assertTrue(arena.getBlockState(portal.add(-1, -1, 0)).isOf(Blocks.GILDED_BLACKSTONE), "gilded corner");
            ctx.assertTrue(arena.getBlockState(portal.add(2, 3, 0)).isOf(Blocks.GILDED_BLACKSTONE), "gilded corner");
            for (int w = 0; w < 2; w++) {
                for (int h = 0; h < 3; h++) ctx.assertTrue(arena.getBlockState(portal.add(w, h, 0)).isAir(), "the frame starts empty");
            }
            int fires = 0;
            for (BlockPos pos : BlockPos.iterate(WrathArena.CORNER, WrathArena.CORNER.add(WrathArena.SIZE_X - 1, WrathArena.SIZE_Y - 1, WrathArena.SIZE_Z - 1))) {
                if (arena.getBlockState(pos).isOf(Blocks.FIRE)) fires++;
            }
            ctx.assertTrue(fires > 300, "the colosseum should be burning, found " + fires + " fires");
        });

        // Waiting in the intro: frozen in the arena, the first song already started
        ctx.runAtTick(JOIN / 2, () -> {
            ctx.assertTrue(player.getServerWorld() == arena, "player should be in the Wrath arena");
            ctx.assertTrue(player.squaredDistanceTo(WrathArena.PLAYER_SPAWN) < 1, "player should arrive at the spawn");
            ctx.assertTrue(Cinematics.isFrozen(player), "player should be frozen during the intro");
            ctx.assertTrue(BossFights.session(Sin.WRATH).song() == OrnamixEntity.PHASE_ONE, "the phase 1 song should be playing");
        });

        // The fight: ORNAMIX coming down out of the sky, untouchable until he's landed and roared
        ctx.runAtTick(JOIN + 10, () -> {
            FightSession session = BossFights.session(Sin.WRATH);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(arena.getEntity(session.bossId()) instanceof OrnamixEntity, "the boss should be ORNAMIX");
            OrnamixEntity boss = (OrnamixEntity) arena.getEntity(session.bossId());
            ctx.assertFalse(Cinematics.isFrozen(player), "player should be free once the fight starts");
            ctx.assertEquals(boss.currentAction(), "ENTRANCE", "action");
            ctx.assertTrue(boss.getY() > WrathArena.CENTER.y + 2, "still coming down, at " + boss.getY());
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().magic(), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health during his entrance");
        });

        ctx.runAtTick(JOIN + OrnamixTimeline.ENTRANCE_FALL + 6, () -> {
            OrnamixEntity boss = (OrnamixEntity) arena.getEntity(BossFights.session(Sin.WRATH).bossId());
            ctx.assertTrue(boss.getPos().distanceTo(WrathArena.CENTER) < 1, "he lands in the middle of the pit, at " + boss.getPos());
            ctx.assertTrue(boss.isUntouchable(), "and kneels there, untouchable, before he rises");
        });

        int landed = JOIN + OrnamixTimeline.ENTRANCE_END + 10;
        ctx.runAtTick(landed, () -> {
            OrnamixEntity boss = (OrnamixEntity) arena.getEntity(BossFights.session(Sin.WRATH).bossId());
            ctx.assertFalse(boss.isUntouchable(), "and can be hurt once he's up");
            ctx.assertEquals(boss.getMaxHealth(), OrnamixEntity.MAX_HEALTH * OrnamixTimeline.healthScale(1), "health for one player");
            ctx.assertTrue(boss.getMainHandStack().isOf(Items.IRON_SWORD) || boss.getMainHandStack().isOf(Items.BOW), "his iron sword");
            boss.setHealth(boss.getMaxHealth() * 0.39f);
        });

        // Ragemode: he can't be hurt, everyone is frozen in the dark, the music stops
        ctx.runAtTick(landed + 5, () -> {
            OrnamixEntity boss = (OrnamixEntity) arena.getEntity(BossFights.session(Sin.WRATH).bossId());
            ctx.assertEquals(boss.getPhase(), 2, "phase");
            ctx.assertEquals(boss.currentAction(), "TRANSITION", "action");
            ctx.assertTrue(boss.isUntouchable(), "he can't be hurt during the phase change");
            ctx.assertTrue(Cinematics.isFrozen(player), "players freeze in the dark");
            ctx.assertTrue(BossFights.session(Sin.WRATH).song() == null, "the music stops");
        });
        ctx.runAtTick(landed + OrnamixTimeline.RESHAPE_AT + 5, () -> {
            OrnamixEntity boss = (OrnamixEntity) arena.getEntity(BossFights.session(Sin.WRATH).bossId());
            ctx.assertTrue(boss.isRaging(), "his eyes catch fire under the black");
            ctx.assertTrue(boss.getPos().distanceTo(WrathArena.CENTER) < 1.5, "he takes the middle of the pit");
        });
        ctx.runAtTick(landed + OrnamixTimeline.REVEAL_AT + 5, () -> {
            ctx.assertFalse(Cinematics.isFrozen(player), "free again when the black lifts");
            ctx.assertTrue(BossFights.session(Sin.WRATH).song() == OrnamixEntity.PHASE_TWO, "Return to Zero starts");
        });

        // Beating him wins the fight
        ctx.runAtTick(landed + OrnamixTimeline.TRANSITION_END + 10, () -> {
            OrnamixEntity boss = (OrnamixEntity) arena.getEntity(BossFights.session(Sin.WRATH).bossId());
            ctx.assertFalse(boss.isUntouchable(), "the fight is on again");
            boss.kill();
            ctx.assertTrue(BossFights.session(Sin.WRATH).stage() == FightSession.Stage.WON, "fight should be won");
            ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.WRATH), "Wrath should be marked defeated");
        });
        ctx.runAtTick(landed + OrnamixTimeline.TRANSITION_END + 14, () -> {
            BossFights.forceEnd(server, Sin.WRATH);
            FmsWorldState.get(server).clearDefeated(Sin.WRATH);
            server.getPlayerManager().remove(player);
            forceColosseum(arena, false);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void phaseOneCantBeSkippedWithOneHit(TestContext ctx) {
        floor(ctx, 8);
        OrnamixEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = survivalPlayer(ctx.getWorld(), Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(7, 1, 7))));
        ctx.runAtTick(OrnamixTimeline.ENTRANCE_END + 5, () -> {
            ctx.assertFalse(boss.isUntouchable(), "up and fighting");
            boss.setHealth(boss.getMaxHealth() * OrnamixEntity.RAGE_AT + 5);
            boss.damage(ctx.getWorld().getDamageSources().magic(), 1000f);
            ctx.assertTrue(boss.getHealth() >= boss.getMaxHealth() * OrnamixEntity.RAGE_AT - 0.02f,
                    "one hit can't take him past 40%, got " + boss.getHealth());
            ctx.assertTrue(boss.getHealth() < boss.getMaxHealth() * OrnamixEntity.RAGE_AT + 5, "but it still hurts");
            // Back up before he notices: Ragemode would freeze every player near him, other tests' too
            boss.setHealth(boss.getMaxHealth() * 0.9f);
        });
        ctx.runAtTick(OrnamixTimeline.ENTRANCE_END + 8, () -> {
            boss.setHealth(boss.getMaxHealth() * 0.9f);
            float before = boss.getHealth();
            boss.timeUntilRegen = 0;
            boss.damage(ctx.getWorld().getDamageSources().magic(), 200f);
            ctx.assertTrue(before - boss.getHealth() <= OrnamixEntity.MAX_HIT, "one hit takes at most " + OrnamixEntity.MAX_HIT);
            boss.discard();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void floorWavesRootWhoeverDoesntJump(TestContext ctx) {
        floor(ctx, 12);
        ServerWorld world = ctx.getWorld();
        OrnamixEntity boss = spawn(ctx, new BlockPos(6, 1, 6));
        // Both 4 blocks from him; one on the ground, one in the air
        ServerPlayerEntity grounded = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(10, 1, 6))));
        ServerPlayerEntity jumping = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(2, 1, 6))));
        ctx.runAtTick(3, () -> start(boss, "FLOOR_WAVE"));
        for (int t = 3; t < 60; t++) {
            ctx.runAtTick(t, () -> {
                grounded.setOnGround(true);
                jumping.setOnGround(false);
            });
        }
        ctx.runAtTick(60, () -> {
            ctx.assertTrue(FlameRoot.isRooted(grounded), "the wave should root a player standing on the floor");
            ctx.assertTrue(grounded.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) == 0, "who can't move");
            ctx.assertTrue(grounded.isOnFire(), "and burns");
            ctx.assertFalse(FlameRoot.isRooted(jumping), "but not one who jumped it");
        });
        ctx.runAtTick(60 + FlameRoot.TICKS + 5, () -> {
            ctx.assertFalse(FlameRoot.isRooted(grounded), "it wears off after 5 seconds");
            // (Checked by its own modifier: a boss in another test going into phase 2 can freeze everyone near it)
            ctx.assertTrue(grounded.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                    .getModifier(Identifier.of(FmsCraft.MOD_ID, "flame_root")) == null, "and they can move again");
            boss.discard();
            world.getServer().getPlayerManager().remove(grounded);
            world.getServer().getPlayerManager().remove(jumping);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void fireballsBreakNothingAndCanBeHitBack(TestContext ctx) {
        floor(ctx, 12);
        ServerWorld world = ctx.getWorld();
        for (int y = 1; y <= 3; y++) {
            for (int z = 4; z <= 8; z++) ctx.setBlockState(new BlockPos(11, y, z), Blocks.GLASS);
        }
        OrnamixEntity boss = spawn(ctx, new BlockPos(2, 1, 6));
        ServerPlayerEntity player = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(6, 1, 1))));
        Vec3d wall = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(11, 2, 6)));
        ctx.runAtTick(5, () -> {
            Vec3d from = wall.add(-4, 0, 0);
            OrnamixFireballEntity.shoot(world, boss, from, new Vec3d(1, 0, 0), 5f);
        });
        ctx.runAtTick(25, () -> {
            for (int y = 1; y <= 3; y++) {
                for (int z = 4; z <= 8; z++) ctx.expectBlock(Blocks.GLASS, new BlockPos(11, y, z));
            }
            ctx.assertTrue(world.getEntitiesByClass(OrnamixFireballEntity.class, new Box(wall, wall).expand(10), e -> true).isEmpty(), "it burst on the glass");
        });
        // Hit back (by the player), into him
        float[] before = new float[1];
        ctx.runAtTick(OrnamixTimeline.ENTRANCE_END + 5, () -> {
            ctx.assertFalse(boss.isUntouchable(), "up and fighting");
            before[0] = boss.getHealth();
            // Right next to him and coming straight at him, as if just punched back
            Vec3d at = boss.getBoundingBox().getCenter();
            OrnamixFireballEntity ball = OrnamixFireballEntity.shoot(world, boss, at.add(2.5, -0.5, 0), new Vec3d(-1, 0, 0), 5f);
            ball.setOwner(player);
        });
        ctx.runAtTick(OrnamixTimeline.ENTRANCE_END + 9, () -> {
            ctx.assertTrue(boss.getHealth() < before[0] - 10, "a returned fireball hurts him, health " + before[0] + " -> " + boss.getHealth());
            boss.discard();
            world.getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    /**
     * One meteor, dropped by hand on this test's player (left to himself he'd pick from every player in the shared test
     * world). It lands while he's still in his entrance, so nothing else can hurt them.
     */
    @SuppressWarnings("unchecked")
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void meteorsBreakNothing(TestContext ctx) throws ReflectiveOperationException {
        floor(ctx, 16);
        ServerWorld world = ctx.getWorld();
        OrnamixEntity boss = spawn(ctx, new BlockPos(2, 1, 2));
        Vec3d stand = Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(13, 1, 13)));
        ServerPlayerEntity player = survivalPlayer(world, stand);
        Class<?> meteor = Class.forName("net.ragnar.fmscraft.boss.wrath.WrathHazards$Meteor");
        var make = meteor.getDeclaredConstructor(Vec3d.class, Vec3d.class, int.class, int.class, double.class, float.class);
        make.setAccessible(true);
        java.lang.reflect.Field hazards = OrnamixEntity.class.getDeclaredField("hazards");
        hazards.setAccessible(true);
        int fall = 30;
        ctx.runAtTick(3, () -> {
            try {
                ((java.util.List<Object>) hazards.get(boss)).add(make.newInstance(stand, boss.getPos(), 0, fall, 5.5, 16f));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        ctx.runAtTick(3 + fall - 3, () -> {
            ctx.assertTrue(!world.getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, new Box(stand, stand).expand(80), e -> true).isEmpty(),
                    "a burning rock coming down");
            ctx.assertFalse(player.isOnFire(), "not yet");
        });
        // (Mock players never tick, so they never lose the invulnerability every player has just after joining: what
        // the hit leaves behind shows it landed instead)
        ctx.runAtTick(3 + fall + 4, () -> ctx.assertTrue(player.isOnFire() && player.getVelocity().y > 0.5,
                "the meteor hit the player where they stood: burning " + player.isOnFire() + ", thrown " + player.getVelocity()));
        // The dust settles: not a block broken, and the rock and its debris gone
        ctx.runAtTick(3 + fall + 60, () -> {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) ctx.expectBlock(Blocks.STONE, new BlockPos(x, 0, z));
            }
            ctx.assertTrue(world.getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, new Box(stand, stand).expand(80), e -> true).isEmpty(),
                    "the meteor and its debris are gone");
            boss.discard();
            world.getServer().getPlayerManager().remove(player);
            ctx.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void witherSkeletonsComeArmoredAndGoWithHim(TestContext ctx) {
        floor(ctx, 16);
        ServerWorld world = ctx.getWorld();
        OrnamixEntity boss = spawn(ctx, new BlockPos(8, 1, 8));
        ServerPlayerEntity a = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(3, 1, 3))));
        ServerPlayerEntity b = survivalPlayer(world, Vec3d.ofBottomCenter(ctx.getAbsolutePos(new BlockPos(12, 1, 12))));
        ctx.runAtTick(3, () -> start(boss, "SUMMON"));
        ctx.runAtTick(40, () -> {
            // (Game tests share a world: he may have put some next to another test's players)
            var skeletons = world.getEntitiesByClass(WitherSkeletonEntity.class, boss.getBoundingBox().expand(120),
                    s -> s.getCommandTags().contains(OrnamixEntity.SUMMON_TAG));
            ctx.assertTrue(skeletons.size() >= 2, "wither skeletons climb out of the flames, got " + skeletons.size());
            ctx.assertEquals(boss.summonCount(), skeletons.size(), "he keeps track of them");
            for (WitherSkeletonEntity s : skeletons) {
                ctx.assertTrue(s.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(Items.IRON_CHESTPLATE), "in armor");
            }
            boss.discard();
        });
        ctx.runAtTick(42, () -> {
            ctx.assertTrue(world.getEntitiesByClass(WitherSkeletonEntity.class, new Box(ctx.getAbsolutePos(BlockPos.ORIGIN)).expand(130),
                    s -> s.getCommandTags().contains(OrnamixEntity.SUMMON_TAG)).isEmpty(), "his skeletons go with him");
            world.getServer().getPlayerManager().remove(a);
            world.getServer().getPlayerManager().remove(b);
            ctx.complete();
        });
    }

    /** Fire in an arena never spreads, burns anything or goes out (FireBlockMixin): the colosseum can't burn down. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void arenaFireNeverSpreads(TestContext ctx) {
        ServerWorld arena = Arenas.world(ctx.getWorld().getServer(), Sin.LUST);
        BlockPos at = new BlockPos(48, 120, 48);
        arena.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
        int flags = Block.NOTIFY_ALL;
        // Stone: not one fire burns on forever by itself
        arena.setBlockState(at, Blocks.STONE.getDefaultState(), flags);
        arena.setBlockState(at.east(), Blocks.RED_WOOL.getDefaultState(), flags);
        arena.setBlockState(at.east().up(), Blocks.OAK_PLANKS.getDefaultState(), flags);
        arena.setBlockState(at.up(), Blocks.FIRE.getDefaultState(), flags);
        ctx.runAtTick(350, () -> {
            ctx.assertTrue(arena.getBlockState(at.up()).isOf(Blocks.FIRE), "the fire still burns");
            ctx.assertTrue(arena.getBlockState(at.east()).isOf(Blocks.RED_WOOL), "the wool next to it is still there");
            ctx.assertTrue(arena.getBlockState(at.east().up()).isOf(Blocks.OAK_PLANKS), "and so are the planks");
            for (BlockPos pos : new BlockPos[]{at, at.east(), at.east().up(), at.up()}) arena.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
            arena.setChunkForced(at.getX() >> 4, at.getZ() >> 4, false);
            ctx.complete();
        });
    }
}
