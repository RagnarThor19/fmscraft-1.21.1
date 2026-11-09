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
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.lust.LustArena;
import net.ragnar.fmscraft.boss.lust.MugenCloneEntity;
import net.ragnar.fmscraft.boss.lust.MugenEntity;
import net.ragnar.fmscraft.boss.lust.MugenTimeline;
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
 * MUGEN, the Lust boss. The full fight test uses the real arena: the compound, the 35 s entrance (the shadow, the
 * hands, the boom), the phase 1 clamp, the shadows dragging strays back, the black and the eclipse, his landing on
 * Toxic's drop, and winning. The others put him on a stone floor in the test world and start his moves by hand, each in
 * a batch of its own (he goes for every player near him).
 */
public class MugenGameTests implements FabricGameTest {
    private static final int JOIN = 40;

    private static ServerPlayerEntity survivalPlayer(ServerWorld world, Vec3d at, float yaw) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-lust-player");
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

    private static MugenEntity spawn(TestContext ctx, BlockPos rel) {
        MugenEntity boss = ModBossEntities.MUGEN.create(ctx.getWorld());
        boss.setSin(Sin.LUST);
        Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        boss.holdStill();
        ctx.getWorld().spawnEntity(boss);
        return boss;
    }

    /** Runs {@code then} as soon as he's up and can be hurt (spawned here, his entrance is a quick landing). */
    private static void whenReady(TestContext ctx, MugenEntity boss, Runnable then) {
        if (!boss.isUntouchable() && boss.currentAction().equals("MOVE")) then.run();
        else ctx.waitAndRun(1, () -> whenReady(ctx, boss, then));
    }

    /**
     * Runs {@code each} now and on each of the next {@code ticks} ticks, one tick at a time. (Not runAtEveryTick: from
     * inside a running task, that adds a task for every tick left at once while the test's tasks are being gone through.)
     */
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

    private static MugenEntity boss(ServerWorld arena) {
        return (MugenEntity) arena.getEntity(BossFights.session(Sin.LUST).bossId());
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 2400, batchId = "mugen_fight")
    public void lustFightPlaysTheIntroThenMugen(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        ctx.assertTrue(BossFights.open(Sin.LUST, world, base, Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity player = player(ctx, new BlockPos(5, 1, 5), 0);
        BossFights.enter(player, Sin.LUST);
        ServerWorld arena = Arenas.world(server, Sin.LUST);
        forceAround(arena, LustArena.CENTER, 4, true);

        // The compound is built: the courtyard's stones, open sky over them, the walls, the empty exit frame
        ctx.runAtTick(2, () -> {
            ctx.assertFalse(arena.getBlockState(new BlockPos(0, LustArena.FLOOR_Y - 1, 0)).isAir(), "the courtyard's stones");
            ctx.assertTrue(arena.getBlockState(new BlockPos(0, LustArena.FLOOR_Y, 0)).isAir(), "open over them");
            ctx.assertFalse(arena.getBlockState(new BlockPos(16, LustArena.FLOOR_Y - 1, 0)).isAir(), "stones where everyone arrives");
            boolean wall = false;
            for (int y = LustArena.FLOOR_Y; y < LustArena.FLOOR_Y + 12; y++) wall |= !arena.getBlockState(new BlockPos(24, y, 0)).isAir();
            ctx.assertTrue(wall, "the compound's east wall");
            BlockPos portal = LustArena.EXIT_PORTAL;
            ctx.assertTrue(arena.getBlockState(portal.down()).isOf(Blocks.POLISHED_BLACKSTONE_BRICKS), "the exit frame");
            ctx.assertTrue(arena.getBlockState(portal.add(-1, -1, 0)).isOf(Blocks.CRYING_OBSIDIAN), "its corner");
            ctx.assertTrue(arena.getBlockState(portal).isAir(), "the frame starts empty");
        });

        ctx.runAtTick(JOIN / 2, () -> {
            ctx.assertTrue(player.getServerWorld() == arena, "player should be in the Lust arena");
            ctx.assertTrue(Cinematics.isFrozen(player), "frozen behind the wheel");
            ctx.assertTrue(BossFights.session(Sin.LUST).song() == null, "silent until the portal closes");
        });

        // The portal closes: COMË N GO starts, everyone stands watching the middle of the courtyard, and he's under it
        ctx.runAtTick(JOIN + 3, () -> {
            FightSession session = BossFights.session(Sin.LUST);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(arena.getEntity(session.bossId()) instanceof MugenEntity, "the boss should be MUGEN");
            ctx.assertTrue(session.song() == MugenEntity.PHASE_ONE, "COMË N GO should be playing");
            MugenEntity boss = boss(arena);
            ctx.assertEquals(boss.currentAction(), "ENTRANCE", "action");
            ctx.assertTrue(boss.getY() < LustArena.FLOOR_Y - 1.5, "under the ground, at " + boss.getY());
            ctx.assertTrue(Cinematics.isFrozen(player), "frozen, watching");
            double d = LustArena.flatDistance(player.getX(), player.getZ(), LustArena.CENTER);
            ctx.assertTrue(Math.abs(d - MugenTimeline.watchRadius(1)) < 0.6, "standing back to watch, " + d + " out");
            ctx.assertTrue(player.getX() > LustArena.CENTER.x, "to the east");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health in his entrance");
        });
        // The hands drag him up out of the ground, slowly
        ctx.runAtTick(JOIN + (MugenTimeline.RISE_START + MugenTimeline.RISEN) / 2, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertTrue(boss.getY() > LustArena.FLOOR_Y - MugenTimeline.RISE_DEPTH + 0.2 && boss.getY() < LustArena.FLOOR_Y - 0.2,
                    "halfway up out of the ground, at " + boss.getY());
        });
        ctx.runAtTick(JOIN + MugenTimeline.RISEN + 5, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertTrue(Math.abs(boss.getY() - LustArena.FLOOR_Y) < 0.3, "standing on the stones, at " + boss.getY());
            ctx.assertTrue(Cinematics.isFrozen(player), "still watching");
        });
        ctx.runAtTick(JOIN + MugenTimeline.DRAW + 24, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertTrue(boss.getMainHandStack().isOf(Items.NETHERITE_SWORD) && boss.getOffHandStack().isOf(Items.NETHERITE_SWORD), "both blades drawn");
            ctx.assertEquals(boss.eyes(), MugenEntity.EYES_VIOLET, "eyes lit");
        });

        // The hit: everyone's free, nobody's hurt
        ctx.runAtTick(JOIN + MugenTimeline.BOOM + 4, () -> {
            ctx.assertFalse(Cinematics.isFrozen(player), "free on the hit");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "the blast hurts nobody");
            MugenEntity boss = boss(arena);
            ctx.assertTrue(LustArena.flatDistance(boss.getX(), boss.getZ(), LustArena.CENTER) < 1, "back in the middle, at " + boss.getPos());
        });
        int fighting = JOIN + MugenTimeline.BOOM + MugenTimeline.READY_AFTER + 6;
        ctx.runAtTick(fighting, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertFalse(boss.isUntouchable(), "and can be hurt once he's up");
            ctx.assertEquals(boss.getMaxHealth(), MugenEntity.MAX_HEALTH * MugenTimeline.healthScale(1), "health for one player");
            boss.holdStill();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 4000, 4));
            // Phase 1 can't be skipped by one big hit
            boss.damage(arena.getDamageSources().playerAttack(player), Float.MAX_VALUE);
            ctx.assertTrue(boss.getHealth() >= boss.getMaxHealth() * 0.5f - 0.01f, "one hit stops at half, at " + boss.getHealth());
            // Over the wall: the shadows drag you back
            player.teleport(arena, LustArena.MAX_X + 10, LustArena.FLOOR_Y, 0.5, 0, 0);
        });
        ctx.runAtTick(fighting + 16, () -> {
            ctx.assertTrue(LustArena.inBounds(player.getPos()), "dragged back inside, at " + player.getPos());
            MugenEntity boss = boss(arena);
            // Real hits, through his armor, all the way down: his health must land exactly on half and start phase 2
            for (int k = 0; k < 400 && boss.getPhase() == 1 && boss.getHealth() > boss.getMaxHealth() * 0.5f; k++) {
                boss.timeUntilRegen = 0;
                boss.damage(arena.getDamageSources().playerAttack(player), 40f);
            }
            ctx.assertEquals(boss.getHealth(), boss.getMaxHealth() * 0.5f, "health stops exactly at half");
        });

        // Half health: black, eerie, the music gone; nothing touches him
        int transition = fighting + 17;
        ctx.runAtTick(transition + 20, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertEquals(boss.getPhase(), 2, "phase");
            ctx.assertEquals(boss.currentAction(), "TRANSITION", "action");
            ctx.assertTrue(BossFights.session(Sin.LUST).song() == null, "silence in the black");
            ctx.assertTrue(Cinematics.isFrozen(player), "held still in the black");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 50f);
            ctx.assertEquals(boss.getHealth(), health, "health in the black");
        });
        // Toxic, the black lifts on him hanging in the eclipse; everyone can move
        int toxic = transition + MugenTimeline.EERIE_TICKS + 1;
        ctx.runAtTick(toxic + MugenTimeline.REVEAL_AT + 10, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertTrue(BossFights.session(Sin.LUST).song() == MugenEntity.PHASE_TWO, "Toxic should be playing");
            ctx.assertFalse(Cinematics.isFrozen(player), "free to move while he hangs there");
            ctx.assertTrue(boss.getY() > LustArena.FLOOR_Y + MugenTimeline.HANG_HEIGHT - 1, "hanging in the sky, at " + boss.getY());
            ctx.assertTrue(boss.isUntouchable(), "untouchable up there");
            ctx.assertEquals(boss.eyes(), MugenEntity.EYES_RED, "eyes burning red");
        });
        // He lands on the drop; the blast hurts nobody
        ctx.runAtTick(toxic + MugenTimeline.TOXIC_DROP + 4, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertTrue(Math.abs(boss.getY() - LustArena.FLOOR_Y) < 0.5, "landed on the drop, at " + boss.getY());
            ctx.assertTrue(boss.hasTransitioned(), "phase 2 under way");
            ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "the landing hurts nobody");
        });
        ctx.runAtTick(toxic + MugenTimeline.TOXIC_DROP + MugenTimeline.READY_AFTER + 6, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertFalse(boss.isUntouchable(), "and he can be hurt");
            ctx.assertEquals(boss.currentAction(), "MOVE", "fighting");
            boss.holdStill();
            boss.setHealth(3);
            boss.damage(arena.getDamageSources().magic(), 20);
            ctx.assertTrue(boss.isAlive() && boss.isDying(), "the last blow: he's done fighting, at " + boss.getHealth());
            FightSession session = BossFights.session(Sin.LUST);
            ctx.assertTrue(session.stage() == FightSession.Stage.WON, "fight should be won");
            ctx.assertTrue(FmsWorldState.get(server).isDefeated(Sin.LUST), "Lust should be marked defeated");
            ctx.assertFalse(BossFights.rewarded(Sin.LUST), "the portal and the wheel wait for him");
            float health = boss.getHealth();
            boss.damage(arena.getDamageSources().playerAttack(player), 20);
            ctx.assertEquals(boss.getHealth(), health, "nothing more reaches him");
        });
        int dies = toxic + MugenTimeline.TOXIC_DROP + MugenTimeline.READY_AFTER + 6;
        ctx.runAtTick(dies + MugenEntity.DEATH_SKY + 20, () -> {
            MugenEntity boss = boss(arena);
            ctx.assertTrue(boss.isAlive(), "still with us");
            ctx.assertEquals(boss.poseState(), MugenEntity.POSE_SIT_SKY, "sat down, looking up at the sky");
            ctx.assertEquals(boss.eyes(), MugenEntity.EYES_NONE, "the anger gone out of his eyes");
            ctx.assertTrue(boss.getMainHandStack().isEmpty() && boss.getOffHandStack().isEmpty(), "his blades let go");
            ctx.assertEquals(arena.getEntitiesByClass(DisplayEntity.class, new Box(boss.getBlockPos()).expand(3),
                    e -> e.getCommandTags().contains(MugenEntity.REMAINS_TAG)).size(), 2, "lying either side of him");
            var advancement = server.getAdvancementLoader().get(net.minecraft.util.Identifier.of(net.ragnar.fmscraft.FmsCraft.MOD_ID, "defeated_lust"));
            ctx.assertTrue(advancement != null && player.getAdvancementTracker().getProgress(advancement).isDone(), "Defeated MUGEN, during it");
            ctx.assertTrue(arena.getBlockState(LustArena.EXIT_PORTAL).isAir(), "no portal yet");
        });
        ctx.runAtTick(dies + MugenEntity.DEATH_TICKS + 4, () -> {
            ctx.assertTrue(arena.getEntitiesByClass(MugenEntity.class, new Box(LustArena.CENTER, LustArena.CENTER).expand(80), MugenEntity::isAlive).isEmpty(),
                    "a poof of clouds, and he's gone");
            ctx.assertTrue(!arena.getEntitiesByClass(ItemEntity.class, new Box(LustArena.CENTER, LustArena.CENTER).expand(80),
                    e -> e.getStack().isOf(ModItems.SOULS.get(Sin.LUST))).isEmpty(), "Soul of Lust should drop");
            ctx.assertFalse(BossFights.rewarded(Sin.LUST), "and a moment's quiet before the portal");
        });
        ctx.runAtTick(dies + MugenEntity.DEATH_TICKS + MugenEntity.DEATH_REWARDS_AFTER + 4, () -> {
            ctx.assertTrue(BossFights.rewarded(Sin.LUST), "then the portal and the wheel");
            ctx.assertTrue(arena.getBlockState(LustArena.EXIT_PORTAL).isOf(ModBlocks.SIN_PORTAL), "exit portal should open");
            ctx.assertTrue(arena.getEntitiesByClass(DisplayEntity.class, new Box(LustArena.CENTER, LustArena.CENTER).expand(60),
                    e -> !e.getCommandTags().contains(MugenEntity.REMAINS_TAG)).isEmpty(), "nothing of his left behind but his blades");
            BossFights.forceEnd(server, Sin.LUST);
            FmsWorldState.get(server).clearDefeated(Sin.LUST);
            forceAround(arena, LustArena.CENTER, 4, false);
            leave(ctx, player);
            ctx.complete();
        });
    }

    /**
     * Someone dies to MUGEN in his compound: the fight ends, everyone else is sent back out through the portal, and the
     * fallen player's things are in a chest by the portal, not lying in the arena where nobody could get them back.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 1200, batchId = "mugen_death")
    public void aDeathToMugenChestsTheLootByThePortal(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 1, 3));
        floor(ctx);
        ctx.assertTrue(BossFights.open(Sin.LUST, world, base, Direction.Axis.X, JOIN), "portal should open");
        ServerPlayerEntity fallen = player(ctx, new BlockPos(1, 1, 1), 0);
        ServerPlayerEntity other = player(ctx, new BlockPos(6, 1, 6), 0);
        BossFights.enter(fallen, Sin.LUST);
        BossFights.enter(other, Sin.LUST);
        ServerWorld arena = Arenas.world(server, Sin.LUST);
        forceAround(arena, LustArena.CENTER, 4, true);

        int fighting = JOIN + MugenTimeline.BOOM + MugenTimeline.READY_AFTER + 8;
        ctx.runAtTick(fighting, () -> {
            FightSession session = BossFights.session(Sin.LUST);
            ctx.assertTrue(session != null && session.stage() == FightSession.Stage.ACTIVE, "fight should be running");
            ctx.assertTrue(fallen.getServerWorld() == arena && other.getServerWorld() == arena, "both in the compound");
            MugenEntity boss = boss(arena);
            ctx.assertFalse(boss.isUntouchable(), "he's loose");
            boss.holdStill();
            fallen.getInventory().insertStack(new net.minecraft.item.ItemStack(Items.DIAMOND_SWORD));
            fallen.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(Items.NETHERITE_CHESTPLATE));
            fallen.onTeleportationDone();
            fallen.damage(arena.getDamageSources().mobAttack(boss), 1000f);
            ctx.assertTrue(fallen.isDead(), "he's down");
        });
        ctx.runAtTick(fighting + 3, () -> {
            // Read everything first and send both players away, so a failure here can't leave them for other tests' bosses
            boolean ended = BossFights.session(Sin.LUST) == null;
            boolean bossGone = arena.getEntitiesByClass(MugenEntity.class, new Box(LustArena.CENTER, LustArena.CENTER).expand(100), e -> true).isEmpty();
            var lying = arena.getEntitiesByClass(ItemEntity.class, new Box(LustArena.CENTER, LustArena.CENTER).expand(100),
                    e -> e.getStack().isOf(Items.DIAMOND_SWORD) || e.getStack().isOf(Items.NETHERITE_CHESTPLATE));
            boolean sword = false, chestplate = false;
            for (BlockPos pos : BlockPos.iterate(base.add(-7, -3, -7), base.add(7, 3, 7))) {
                if (world.getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                    for (int i = 0; i < chest.size(); i++) {
                        sword |= chest.getStack(i).isOf(Items.DIAMOND_SWORD);
                        chestplate |= chest.getStack(i).isOf(Items.NETHERITE_CHESTPLATE);
                    }
                }
            }
            boolean otherOut = other.getServerWorld() == world, otherByPortal = other.getBlockPos().getSquaredDistance(base) < 5 * 5;
            boolean otherFree = !Cinematics.isFrozen(other);
            String otherAt = other.getServerWorld().getRegistryKey().getValue() + " " + other.getBlockPos();
            forceAround(arena, LustArena.CENTER, 4, false);
            leave(ctx, other);
            leave(ctx, fallen);
            ctx.assertTrue(ended, "the fight ends when someone dies");
            ctx.assertTrue(bossGone, "he's gone");
            ctx.assertTrue(lying.isEmpty(), "the fallen player's things aren't lying in the arena: " + lying);
            ctx.assertTrue(sword && chestplate, "the fallen player's things are in a chest by the portal (sword " + sword + ", chestplate " + chestplate + ")");
            ctx.assertTrue(otherOut && otherByPortal, "the other player is sent back out by the portal, at " + otherAt);
            ctx.assertTrue(otherFree, "and free to move");
            ctx.complete();
        });
    }

    /** Kunai: eight of them, one after another, four hearts each. Most find you close up. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "mugen_kunai")
    public void kunaiHitHardAndFast(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(1, 1, 4));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 4), 90f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("KUNAI");
            ctx.waitAndRun(50, () -> {
                float lost = player.getMaxHealth() - player.getHealth();
                ctx.assertTrue(lost >= 16, "at least two kunai found him, lost " + lost);
                boss.discard();
                ctx.waitAndRun(2, () -> {
                    ctx.assertTrue(ctx.getWorld().getEntitiesByClass(DisplayEntity.class, boss.getBoundingBox().expand(30), e -> true).isEmpty(), "no kunai left");
                    leave(ctx, player);
                    ctx.complete();
                });
            });
        });
    }

    /** The Tome of Logs: a hit on him never lands; a log takes it, and he's behind you. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "mugen_log")
    public void aLogTakesTheHit(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(3, 1, 2));
        ServerPlayerEntity player = tough(ctx, new BlockPos(3, 1, 4), 180f);
        whenReady(ctx, boss, () -> {
            float health = boss.getHealth();
            boss.evadeNext(true);
            boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 10f);
            ctx.assertEquals(boss.getHealth(), health, "the log took it");
            ctx.assertTrue(boss.leftALog(), "a log where he stood");
            ctx.assertEquals(boss.currentAction(), "LOG_STRIKE", "and he's going for you");
            Vec3d behind = player.getPos().add(Vec3d.fromPolar(0, player.getYaw()).multiply(-1));
            ctx.assertTrue(boss.getPos().distanceTo(behind) < 1.6, "behind you, at " + boss.getPos() + " (you at " + player.getPos() + ")");
            ctx.waitAndRun(12, () -> {
                ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "and cut you from behind");
                // Phase: out of the next one in a blur
                float now = boss.getHealth();
                ctx.waitAndRun(10, () -> {
                    Vec3d was = boss.getPos();
                    boss.evadeNext(false);
                    boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 10f);
                    ctx.assertEquals(boss.getHealth(), now, "Phased out of it");
                    ctx.waitAndRun(6, () -> {
                        ctx.assertTrue(boss.getPos().distanceTo(was) > 2, "a few blocks aside, from " + was + " to " + boss.getPos());
                        boss.discard();
                        leave(ctx, player);
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** Get Over Here: the chain hooks you, drags you in, and he stabs you. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 240, batchId = "mugen_chain")
    public void theChainDragsYouIn(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(0, 1, 0));
        ServerPlayerEntity player = tough(ctx, new BlockPos(7, 1, 7), 45f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("CHAIN");
            double start = player.distanceTo(boss);
            boolean[] hooked = {false};
            every(ctx, 58, () -> hooked[0] |= boss.chainHooked());
            ctx.waitAndRun(60, () -> {
                ctx.assertTrue(hooked[0], "the chain hooked him");
                ctx.assertTrue(player.distanceTo(boss) < start - 4, "dragged in, from " + start + " to " + player.distanceTo(boss));
                ctx.assertTrue(player.getMaxHealth() - player.getHealth() >= 18, "and stabbed, lost " + (player.getMaxHealth() - player.getHealth()));
                boss.discard();
                ctx.waitAndRun(2, () -> {
                    ctx.assertTrue(ctx.getWorld().getEntitiesByClass(DisplayEntity.class, boss.getBoundingBox().expand(30), e -> true).isEmpty(), "no chain left");
                    leave(ctx, player);
                    ctx.complete();
                });
            });
        });
    }

    /** The Tome of Unseen Hands: the shadow spreads under you, the hands hold you, and they crush you. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mugen_hands")
    public void theUnseenHandsHoldAndCrush(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(0, 1, 3));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 3), 90f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("UNSEEN_HANDS");
            Vec3d at = player.getPos();
            ctx.waitAndRun(8 + MugenTimeline.POOL_CREEP + 6, () -> {
                ctx.assertTrue(boss.heldByHands().contains(player), "the hands have him");
                ctx.waitAndRun(MugenTimeline.POOL_HOLD, () -> {
                    float lost = player.getMaxHealth() - player.getHealth();
                    ctx.assertTrue(lost >= 20, "crushed, lost " + lost);
                    ctx.assertTrue(player.getPos().distanceTo(at) < 1.5, "held where he stood");
                    ctx.assertTrue(boss.heldByHands().isEmpty(), "and let go");
                    boss.discard();
                    leave(ctx, player);
                    ctx.complete();
                });
            });
        });
    }

    /** Shadow Clones: three of him; they go up in smoke at a touch, and go with him. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "mugen_clones")
    public void clonesBreakAtATouch(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = tough(ctx, new BlockPos(7, 1, 7), 0f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("CLONES");
            ctx.waitAndRun(22, () -> {
                ctx.assertEquals(boss.cloneCount(), MugenTimeline.clones(1, false), "clones");
                var clones = ctx.getWorld().getEntitiesByClass(MugenCloneEntity.class, boss.getBoundingBox().expand(10), e -> !e.isEcho());
                ctx.assertTrue(!clones.isEmpty(), "they're out there");
                clones.get(0).damage(ctx.getWorld().getDamageSources().playerAttack(player), 1f);
                ctx.assertTrue(clones.get(0).isRemoved(), "one touch and it's smoke");
                ctx.assertEquals(boss.cloneCount(), MugenTimeline.clones(1, false) - 1, "one fewer");
                boss.discard();
                ctx.waitAndRun(2, () -> {
                    ctx.assertTrue(ctx.getWorld().getEntitiesByClass(MugenCloneEntity.class, boss.getBoundingBox().expand(20), e -> true).isEmpty(), "they go with him");
                    leave(ctx, player);
                    ctx.complete();
                });
            });
        });
    }

    /** The Blackout: the world goes black for 7 seconds while he hunts in it, then lifts. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mugen_blackout")
    public void theBlackoutLifts(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(1, 1, 1));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 6), 0f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("BLACKOUT");
            ctx.waitAndRun(30, () -> {
                ctx.assertTrue(boss.blackoutLeft() > 0, "the world is black");
                ctx.waitAndRun(MugenTimeline.BLACKOUT_TICKS + 10, () -> {
                    ctx.assertEquals(boss.blackoutLeft(), 0, "and it lifts");
                    ctx.assertFalse(boss.isInvisible(), "and there he is");
                    ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "he found him in the dark");
                    boss.discard();
                    leave(ctx, player);
                    ctx.complete();
                });
            });
        });
    }

    /** Blade Throw: one of his blades leaves his hand, and comes back to it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "mugen_blade")
    public void theThrownBladeComesBack(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(0, 1, 0));
        ServerPlayerEntity player = tough(ctx, new BlockPos(7, 1, 7), 45f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("BLADE_THROW");
            ctx.waitAndRun(12, () -> {
                ctx.assertTrue(boss.bladeThrown(), "thrown");
                ctx.assertTrue(boss.getMainHandStack().isEmpty(), "his hand is empty");
                ctx.waitAndRun(40, () -> {
                    ctx.assertFalse(boss.bladeThrown(), "back");
                    ctx.assertTrue(boss.getMainHandStack().isOf(Items.NETHERITE_SWORD), "in his hand again");
                    ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "it found him on the way");
                    boss.discard();
                    leave(ctx, player);
                    ctx.complete();
                });
            });
        });
    }

    /** Sword Rain and the Taunt break nothing; the Taunt hurts nobody and its echoes crumble. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "mugen_rain")
    public void swordRainAndTheTauntBreakNothing(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 3), 90f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("SWORD_RAIN");
            ctx.waitAndRun(80, () -> {
                ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "a sword found him");
                for (int x = 0; x <= 7; x++) {
                    for (int z = 0; z <= 7; z++) ctx.expectBlock(Blocks.STONE, new BlockPos(x, 0, z));
                }
                player.setHealth(player.getMaxHealth());
                boss.forceAttack("TAUNT");
                ctx.waitAndRun(30, () -> {
                    ctx.assertTrue(boss.isUntouchable(), "untouchable while he taunts");
                    ctx.assertTrue(!ctx.getWorld().getEntitiesByClass(MugenCloneEntity.class, boss.getBoundingBox().expand(20), MugenCloneEntity::isEcho).isEmpty(),
                            "his echoes stand there");
                    ctx.waitAndRun(80, () -> {
                        ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "the Taunt hurts nobody");
                        ctx.assertTrue(player.hasStatusEffect(StatusEffects.DARKNESS) || boss.currentAction().equals("MOVE"), "it darkens your sight");
                        ctx.assertTrue(ctx.getWorld().getEntitiesByClass(MugenCloneEntity.class, boss.getBoundingBox().expand(20), e -> true).isEmpty(), "the echoes crumble");
                        ctx.assertFalse(boss.isUntouchable(), "and he can be hurt again");
                        boss.discard();
                        leave(ctx, player);
                        ctx.complete();
                    });
                });
            });
        });
    }

    private static boolean noDisplaysNear(TestContext ctx, MugenEntity boss) {
        return ctx.getWorld().getEntitiesByClass(DisplayEntity.class, boss.getBoundingBox().expand(30), e -> true).isEmpty();
    }

    /** Void Spikes: the ground goes black, spikes burst up and hit hard, and nothing is broken or left behind. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mugen_spikes")
    public void voidSpikesHitHardAndBreakNothing(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(3, 1, 3));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 6), 0f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("VOID_SPIKES");
            boss.voidSpikeUnder(player.getPos());
            ctx.waitAndRun(12, () -> {
                ctx.assertTrue(boss.voidSpikes() >= 6, "about seven spikes coming, " + boss.voidSpikes());
                ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "nothing yet: the ground is only going black");
                ctx.waitAndRun(16, () -> {
                    float lost = player.getMaxHealth() - player.getHealth();
                    ctx.assertTrue(lost >= 18, "the one under him hit hard, lost " + lost);
                    ctx.waitAndRun(80, () -> {
                        for (int x = 0; x <= 7; x++) {
                            for (int z = 0; z <= 7; z++) ctx.expectBlock(Blocks.STONE, new BlockPos(x, 0, z));
                        }
                        ctx.assertEquals(boss.voidSpikes(), 0, "all sunk back");
                        ctx.assertTrue(noDisplaysNear(ctx, boss), "nothing of them left");
                        boss.discard();
                        leave(ctx, player);
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** Iaido: hit him while he stands sheathed and his blade gets you first; leave him be and he cuts along the line. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mugen_iaido")
    public void iaidoCountersAndCuts(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(3, 1, 2));
        ServerPlayerEntity player = tough(ctx, new BlockPos(3, 1, 5), 180f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("IAIDO");
            ctx.waitAndRun(6, () -> {
                ctx.assertTrue(boss.inIaido(), "standing sheathed");
                ctx.assertTrue(boss.getMainHandStack().isEmpty(), "blades away");
                float health = boss.getHealth();
                boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 10f);
                ctx.assertEquals(boss.getHealth(), health, "the hit never lands");
                float lost = player.getMaxHealth() - player.getHealth();
                ctx.assertTrue(lost >= 15, "his blade got there first, lost " + lost);
                ctx.waitAndRun(16, () -> {
                    ctx.assertEquals(boss.currentAction(), "MOVE", "and on");
                    ctx.assertTrue(boss.getMainHandStack().isOf(Items.NETHERITE_SWORD), "blades back out");
                    player.setHealth(player.getMaxHealth());
                    // This time nobody touches him: he cuts along the line through the player
                    boss.forceAttack("IAIDO");
                    ctx.waitAndRun(60, () -> {
                        ctx.assertTrue(player.getHealth() <= player.getMaxHealth() - 15, "cut along the line, at " + player.getHealth());
                        boss.discard();
                        leave(ctx, player);
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** Shadow Bind: the band of shadow sweeps across the ground and holds whoever's in it for three seconds. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mugen_bind")
    public void shadowBindHoldsForThreeSeconds(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(0, 1, 3));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 3), 90f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("SHADOW_BIND");
            ctx.waitAndRun(24, () -> {
                ctx.assertTrue(boss.bound().contains(player), "held fast");
                ctx.waitAndRun(62, () -> {
                    ctx.assertFalse(boss.bound().contains(player), "and let go after three seconds");
                    ctx.waitAndRun(30, () -> {
                        ctx.assertTrue(noDisplaysNear(ctx, boss), "the band is gone");
                        boss.discard();
                        leave(ctx, player);
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** Shadow Sneak: he sinks into the ground, his shadow crosses to you, and he bursts up out of it spinning. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mugen_sneak")
    public void shadowSneakBurstsUpUnderYou(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(0, 1, 0));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 6), 0f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("SHADOW_SNEAK");
            ctx.waitAndRun(24, () -> {
                ctx.assertTrue(boss.isInvisible() && boss.isUntouchable(), "under the ground");
                ctx.assertTrue(boss.getY() < player.getY() - 1, "below it, at " + boss.getY());
                ctx.waitAndRun(50, () -> {
                    ctx.assertFalse(boss.isInvisible(), "out again");
                    float lost = player.getMaxHealth() - player.getHealth();
                    ctx.assertTrue(lost >= 12, "came up spinning under him, lost " + lost);
                    ctx.waitAndRun(30, () -> {
                        ctx.assertEquals(boss.currentAction(), "MOVE", "and on");
                        ctx.assertTrue(noDisplaysNear(ctx, boss), "the shadow is gone");
                        boss.discard();
                        leave(ctx, player);
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** The Black Hole: five seconds gathering it, untouchable; then thrown, and its heart hits whoever it reaches, once. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "mugen_hole")
    public void theBlackHoleHitsOnceAndGoes(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(0, 1, 3));
        ServerPlayerEntity player = tough(ctx, new BlockPos(7, 1, 3), 90f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("BLACKHOLE");
            ctx.waitAndRun(MugenEntity.HOLE_CAST - 10, () -> {
                float health = boss.getHealth();
                boss.damage(ctx.getWorld().getDamageSources().playerAttack(player), 20f);
                ctx.assertEquals(boss.getHealth(), health, "untouchable all the while he gathers it");
                ctx.assertEquals(boss.blackHoles(), 0, "not thrown yet: five seconds to run");
                ctx.assertEquals(player.getHealth(), player.getMaxHealth(), "nothing hurts while he casts");
                ctx.waitAndRun(40, () -> {
                    ctx.assertFalse(boss.isUntouchable(), "touchable once it's thrown");
                    float lost = player.getMaxHealth() - player.getHealth();
                    ctx.assertTrue(lost >= MugenEntity.HOLE_DAMAGE - 0.01f && lost <= MugenEntity.HOLE_DAMAGE + 6.01f,
                            "its heart hit him once, lost " + lost);
                    ctx.waitAndRun(50, () -> {
                        ctx.assertEquals(boss.blackHoles(), 0, "gone after three seconds");
                        ctx.assertTrue(noDisplaysNear(ctx, boss), "nothing of it left");
                        boss.discard();
                        leave(ctx, player);
                        ctx.complete();
                    });
                });
            });
        });
    }

    /** Full Protection IV netherite and 20 health, against the Black Hole as hard as it hits in phase 2: left on 3.5 hearts or more. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "mugen_hole_armor")
    public void theBlackHoleLeavesFullProtectionStanding(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(0, 1, 3));
        ServerPlayerEntity player = player(ctx, new BlockPos(7, 1, 3), 90f);
        var protection = ctx.getWorld().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENCHANTMENT)
                .entryOf(net.minecraft.enchantment.Enchantments.PROTECTION);
        net.minecraft.item.Item[] armor = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        net.minecraft.entity.EquipmentSlot[] slots = {net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            net.minecraft.item.ItemStack piece = new net.minecraft.item.ItemStack(armor[i]);
            piece.addEnchantment(protection, 4);
            player.equipStack(slots[i], piece);
        }
        boss.hitLikePhaseTwo();
        whenReady(ctx, boss, () -> {
            boss.forceAttack("BLACKHOLE");
            ctx.waitAndRun(MugenEntity.HOLE_CAST + 40, () -> {
                ctx.assertTrue(player.isAlive(), "still standing");
                ctx.assertTrue(player.getHealth() < player.getMaxHealth(), "but it found him");
                // Its heart and the burst together (the burst can't reach him here, but count it anyway)
                float worst = player.getHealth() - net.minecraft.entity.DamageUtil.getInflictedDamage(7, 16);
                ctx.assertTrue(worst >= 7f - 0.01f, "3.5 hearts left even if the burst caught him too: " + player.getHealth() + " now, " + worst + " at worst");
                boss.discard();
                leave(ctx, player);
                ctx.complete();
            });
        });
    }

    /** The Controller: strings to one player for five seconds, then he lets go; and nobody's left on them when he goes. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "mugen_control")
    public void theControllerLetsGo(TestContext ctx) {
        floor(ctx);
        MugenEntity boss = spawn(ctx, new BlockPos(1, 1, 3));
        ServerPlayerEntity player = tough(ctx, new BlockPos(6, 1, 3), 90f);
        whenReady(ctx, boss, () -> {
            boss.forceAttack("CONTROLLER");
            ctx.waitAndRun(10, () -> {
                ctx.assertEquals(boss.controlLeft(player), 0, "not yet: the strings are still reaching for him");
                ctx.waitAndRun(14, () -> {
                    int left = boss.controlLeft(player);
                    ctx.assertTrue(left > 80 && left <= MugenEntity.CONTROL_TICKS, "on his strings, " + left + " left");
                    ctx.waitAndRun(MugenEntity.CONTROL_TICKS, () -> {
                        ctx.assertEquals(boss.controlLeft(player), 0, "and let go after five seconds");
                        boss.forceAttack("CONTROLLER");
                        ctx.waitAndRun(20, () -> {
                            ctx.assertTrue(boss.controlLeft(player) > 0, "on them again");
                            boss.discard();
                            ctx.assertEquals(boss.controlLeft(player), 0, "nobody left on his strings when he goes");
                            leave(ctx, player);
                            ctx.complete();
                        });
                    });
                });
            });
        });
    }
}
