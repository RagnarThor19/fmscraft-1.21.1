package net.ragnar.fmscraft.fight;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.block.ModBlocks;
import net.ragnar.fmscraft.block.SinPortalBlock;
import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.SinBossEntity;
import net.ragnar.fmscraft.network.BossMusicPayload;
import net.ragnar.fmscraft.reward.TomeWheel;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs the boss fights. A structure (or {@code /fmscraft fight}) calls {@link #open} once the right key is used:
 * <ol>
 *   <li>A 2x3 portal opens for 30 seconds. Everyone who steps through waits in the sin's arena.</li>
 *   <li>The portal closes and the boss spawns. If nobody went in, the attempt is wasted.</li>
 *   <li>Any participant dying or disconnecting ends it for everyone: they're sent back, and the fallen
 *       player's items are put in chests by the portal.</li>
 *   <li>Killing the boss marks the sin defeated, drops its soul and opens an exit portal home.</li>
 * </ol>
 * Fights don't survive a server restart; anyone found in an arena afterwards is sent back to their portal.
 */
public final class BossFights {
    public static final int JOIN_TICKS = 30 * 20;
    /** The portal warns this long before it closes. */
    public static final int WARNING_TICKS = 5 * 20;

    private static final Map<Sin, FightSession> SESSIONS = new EnumMap<>(Sin.class);
    // Teleports are deferred to the end of the tick so they never happen mid-collision or mid-death
    private static final Map<UUID, Consumer<ServerPlayerEntity>> PENDING = new LinkedHashMap<>();

    private BossFights() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BossFights::tick);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) onPlayerDeath(player);
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (Sin sin : Sin.values()) {
                if (Arenas.world(server, sin) == null) FmsCraft.LOGGER.warn("Arena dimension {} is missing", Arenas.key(sin).getValue());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SESSIONS.clear();
            PENDING.clear();
        });
    }

    public static FightSession session(Sin sin) {
        return SESSIONS.get(sin);
    }

    // ---- Opening ----

    public static boolean open(Sin sin, ServerWorld world, BlockPos base, Direction.Axis axis) {
        return open(sin, world, base, axis, JOIN_TICKS);
    }

    public static boolean open(Sin sin, ServerWorld world, BlockPos base, Direction.Axis axis, int joinTicks) {
        return open(sin, world, base, axis, joinTicks, 2, 3);
    }

    /**
     * Opens a {@code width} x {@code height} portal for {@code sin} with its bottom-left block at {@code base},
     * running along {@code axis}. Returns false (and the caller should keep the key) if that sin's fight is
     * already running.
     */
    public static boolean open(Sin sin, ServerWorld world, BlockPos base, Direction.Axis axis, int joinTicks, int width, int height) {
        if (SESSIONS.containsKey(sin)) return false;
        MinecraftServer server = world.getServer();
        ServerWorld arena = Arenas.world(server, sin);
        if (arena == null) {
            FmsCraft.LOGGER.error("Arena dimension {} is missing", Arenas.key(sin).getValue());
            return false;
        }

        Arenas.reset(arena, sin);
        placePortal(world, base, axis, sin, width, height);
        SESSIONS.put(sin, new FightSession(sin, GlobalPos.create(world.getRegistryKey(), base), axis, width, height, server.getTicks(), joinTicks));
        world.playSound(null, base, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.0f, 0.8f);
        return true;
    }

    public static void placePortal(ServerWorld world, BlockPos base, Direction.Axis axis, Sin sin, int width, int height) {
        BlockState portal = ModBlocks.SIN_PORTAL.getDefaultState().with(SinPortalBlock.AXIS, axis).with(SinPortalBlock.SIN, sin);
        for (BlockPos pos : portalBlocks(base, axis, width, height)) world.setBlockState(pos, portal);
    }

    public static void removePortal(ServerWorld world, BlockPos base, Direction.Axis axis, int width, int height) {
        for (BlockPos pos : portalBlocks(base, axis, width, height)) {
            if (world.getBlockState(pos).isOf(ModBlocks.SIN_PORTAL)) world.removeBlock(pos, false);
        }
    }

    /** The direction a portal along {@code axis} extends in from its base block. */
    public static Direction along(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
    }

    private static List<BlockPos> portalBlocks(BlockPos base, Direction.Axis axis, int width, int height) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) blocks.add(base.offset(along(axis), w).up(h));
        }
        return blocks;
    }

    // ---- Entering and leaving ----

    public static void onPortalTouched(ServerPlayerEntity player, Sin sin) {
        FightSession session = SESSIONS.get(sin);
        if (session == null || PENDING.containsKey(player.getUuid())) return;

        boolean inArena = player.getWorld().getRegistryKey().equals(Arenas.key(sin));
        if (!inArena && session.stage == FightSession.Stage.JOINING) {
            enter(player, sin);
        } else if (inArena && session.stage == FightSession.Stage.WON) {
            session.participants.remove(player.getUuid());
            PENDING.put(player.getUuid(), BossFights::returnHome);
        }
    }

    /** Adds a player to the fight and sends them into the arena. Public for tests. */
    public static void enter(ServerPlayerEntity player, Sin sin) {
        FightSession session = SESSIONS.get(sin);
        if (session == null || session.stage != FightSession.Stage.JOINING) return;

        if (session.participants.add(player.getUuid())) {
            FmsWorldState.get(player.getServer()).setReturn(player.getUuid(), session.portal);
            session.countdown.addPlayer(player);
        }
        PENDING.put(player.getUuid(), p -> {
            ServerWorld arena = Arenas.world(p.getServer(), sin);
            p.teleport(arena, Arenas.PLAYER_SPAWN.x, Arenas.PLAYER_SPAWN.y, Arenas.PLAYER_SPAWN.z, 180f, 0f);
            p.playSoundToPlayer(SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.4f, 1.2f);
        });
    }

    /** Sends a player back to the portal they came in through (or world spawn if that's unknown). */
    private static void returnHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        FmsWorldState state = FmsWorldState.get(server);
        GlobalPos target = state.getReturn(player.getUuid()).orElse(null);
        ServerWorld world = target != null ? server.getWorld(target.dimension()) : null;
        if (world == null) {
            world = server.getOverworld();
            target = GlobalPos.create(world.getRegistryKey(), world.getSpawnPos());
        }
        BlockPos pos = target.pos();
        player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
        state.clearReturn(player.getUuid());
    }

    // ---- Ticking ----

    private static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            List<Map.Entry<UUID, Consumer<ServerPlayerEntity>>> todo = new ArrayList<>(PENDING.entrySet());
            PENDING.clear();
            for (var entry : todo) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null) entry.getValue().accept(player);
            }
        }

        long now = server.getTicks();
        for (FightSession session : new ArrayList<>(SESSIONS.values())) {
            switch (session.stage) {
                case JOINING -> {
                    long left = session.openedTick + session.joinTicks - now;
                    if (left > 0) {
                        warnIfClosing(server, session, left);
                        long seconds = (left + 19) / 20;
                        session.countdown.setName(Text.translatable("bossbar.fmscraft.fight_starts", seconds));
                        session.countdown.setPercent((float) left / session.joinTicks);
                    } else {
                        closeEntry(server, session);
                        if (session.participants.isEmpty()) SESSIONS.remove(session.sin);
                        else startFight(server, session);
                    }
                }
                case WON -> {
                    ServerWorld arena = Arenas.world(server, session.sin);
                    session.participants.removeIf(id -> arena.getPlayerByUuid(id) == null);
                    if (session.participants.isEmpty()) SESSIONS.remove(session.sin);
                }
                case ACTIVE -> {
                }
            }
        }
    }

    /** A bell tolls 5 seconds before the portal closes, then rising chimes on 3, 2, 1. */
    private static void warnIfClosing(MinecraftServer server, FightSession session, long left) {
        if (left > WARNING_TICKS || left % 20 != 0) return;
        int seconds = (int) (left / 20);
        if (seconds != 5 && seconds > 3) return;

        ServerWorld world = server.getWorld(session.portal.dimension());
        Vec3d center = portalCenter(session);
        if (world != null) {
            if (seconds == 5) {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 3.0f, 0.6f);
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 0.6f, 1.4f);
                for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(center) < 32 * 32)) {
                    player.sendMessage(Text.translatable("message.fmscraft.portal_closing").formatted(Formatting.GOLD), true);
                }
            } else {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                        SoundCategory.BLOCKS, 2.0f, 0.5f + (3 - seconds) * 0.25f);
            }
        }
        // Players already waiting inside hear it too, so they know the fight is about to start
        for (ServerPlayerEntity player : online(server, session)) {
            if (player.getWorld() == world) continue;
            if (seconds == 5) player.playSoundToPlayer(SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.0f, 0.6f);
            else player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 1.0f, 0.5f + (3 - seconds) * 0.25f);
        }
    }

    private static Vec3d portalCenter(FightSession session) {
        return Vec3d.of(session.portal.pos()).add(0.5, session.height / 2.0, 0.5).offset(along(session.axis), (session.width - 1) / 2.0);
    }

    private static void closeEntry(MinecraftServer server, FightSession session) {
        session.countdown.clearPlayers();
        ServerWorld world = server.getWorld(session.portal.dimension());
        if (world == null) return;
        removePortal(world, session.portal.pos(), session.axis, session.width, session.height);
        Vec3d center = portalCenter(session);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.7f);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 60, 0.6, 1.2, 0.6, 0.05);
    }

    private static void startFight(MinecraftServer server, FightSession session) {
        ServerWorld arena = Arenas.world(server, session.sin);
        SinBossEntity boss = ModBossEntities.forSin(session.sin).create(arena);
        if (boss == null) {
            fail(server, session, null);
            return;
        }
        boss.setSin(session.sin);
        boss.refreshPositionAndAngles(Arenas.BOSS_SPAWN.x, Arenas.BOSS_SPAWN.y, Arenas.BOSS_SPAWN.z, 0f, 0f);
        arena.spawnEntity(boss);
        session.bossId = boss.getUuid();
        session.stage = FightSession.Stage.ACTIVE;
        for (ServerPlayerEntity player : online(server, session)) {
            player.playSoundToPlayer(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 0.8f);
        }
    }

    // ---- Music ----

    /** Called by a boss when its song should start (fight start and phase change). */
    public static void onSongChange(SinBossEntity boss, BossSong song) {
        FightSession session = SESSIONS.get(boss.getSin());
        if (session == null || !boss.getUuid().equals(session.bossId)) return;
        sendMusic(boss.getServer(), session, new BossMusicPayload(song.soundId()));
    }

    private static void sendMusic(MinecraftServer server, FightSession session, BossMusicPayload payload) {
        for (ServerPlayerEntity player : online(server, session)) {
            if (ServerPlayNetworking.canSend(player, BossMusicPayload.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    // ---- Winning and losing ----

    public static void onBossDied(SinBossEntity boss) {
        FightSession session = SESSIONS.get(boss.getSin());
        if (session == null || session.stage != FightSession.Stage.ACTIVE || !boss.getUuid().equals(session.bossId)) return;

        MinecraftServer server = boss.getServer();
        session.stage = FightSession.Stage.WON;
        FmsWorldState.get(server).markDefeated(session.sin);
        sendMusic(server, session, BossMusicPayload.STOP);
        server.getPlayerManager().broadcast(Text.translatable("message.fmscraft.boss_defeated",
                session.sin.bossName(), session.sin.displayName()).formatted(Formatting.GOLD), false);
        for (ServerPlayerEntity player : online(server, session)) {
            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        placePortal(Arenas.world(server, session.sin), Arenas.EXIT_PORTAL, Direction.Axis.X, session.sin, 2, 3);
        for (ServerPlayerEntity player : online(server, session)) TomeWheel.spin(player, session.sin);
    }

    private static void onPlayerDeath(ServerPlayerEntity player) {
        FightSession session = liveSessionOf(player);
        if (session == null || holdsTotem(player)) return;

        stashInventory(player, session);
        FmsWorldState.get(player.getServer()).clearReturn(player.getUuid()); // they respawn at their own spawn point
        fail(player.getServer(), session, player);
    }

    private static void onDisconnect(ServerPlayerEntity player) {
        // A server shutdown disconnects everyone; that isn't a loss. They're sent back when they next log in.
        if (!player.getServer().isRunning()) return;
        FightSession session = liveSessionOf(player);
        if (session == null) return;

        // Counts as a death, so logging out can't save a losing fight. Their return point is kept for when they log back in.
        stashInventory(player, session);
        fail(player.getServer(), session, player);
    }

    private static void onJoin(ServerPlayerEntity player) {
        Sin sin = Arenas.sinOf(player.getWorld());
        if (sin == null) return;
        FightSession session = SESSIONS.get(sin);
        if (session == null || !session.participants.contains(player.getUuid())) {
            PENDING.put(player.getUuid(), BossFights::returnHome);
        }
    }

    private static void stashInventory(ServerPlayerEntity player, FightSession session) {
        if (player.getServerWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;
        ServerWorld world = player.getServer().getWorld(session.portal.dimension());
        List<ItemStack> items = DeathChests.takeInventory(player);
        if (world != null && !items.isEmpty()) DeathChests.store(world, session.portal.pos(), items);
    }

    /** Ends the attempt: boss removed, music stopped, everyone still inside sent back. */
    private static void fail(MinecraftServer server, FightSession session, ServerPlayerEntity fallen) {
        SESSIONS.remove(session.sin);
        if (session.stage == FightSession.Stage.JOINING) closeEntry(server, session);
        sendMusic(server, session, BossMusicPayload.STOP);

        ServerWorld arena = Arenas.world(server, session.sin);
        if (session.bossId != null) {
            Entity boss = arena.getEntity(session.bossId);
            if (boss != null) boss.discard();
        }

        Text message = fallen != null
                ? Text.translatable("message.fmscraft.fight_failed", fallen.getDisplayName(), session.sin.displayName())
                : Text.translatable("message.fmscraft.fight_ended", session.sin.displayName());
        for (ServerPlayerEntity player : online(server, session)) {
            player.sendMessage(message.copy().formatted(Formatting.DARK_RED));
            if (player != fallen && player.getWorld() == arena) PENDING.put(player.getUuid(), BossFights::returnHome);
        }
    }

    /** Ends a fight without anyone dying (command / tests). Everyone inside is sent back. */
    public static void forceEnd(MinecraftServer server, Sin sin) {
        FightSession session = SESSIONS.get(sin);
        if (session == null) return;
        if (session.stage == FightSession.Stage.WON) {
            SESSIONS.remove(sin);
            for (ServerPlayerEntity player : online(server, session)) PENDING.put(player.getUuid(), BossFights::returnHome);
        } else {
            fail(server, session, null);
        }
    }

    // ---- Helpers ----

    private static FightSession liveSessionOf(ServerPlayerEntity player) {
        for (FightSession session : SESSIONS.values()) {
            if (session.isLive() && session.participants.contains(player.getUuid())) return session;
        }
        return null;
    }

    private static boolean holdsTotem(ServerPlayerEntity player) {
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).isOf(Items.TOTEM_OF_UNDYING)) return true;
        }
        return false;
    }

    private static List<ServerPlayerEntity> online(MinecraftServer server, FightSession session) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID id : session.participants) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) players.add(player);
        }
        return players;
    }
}
