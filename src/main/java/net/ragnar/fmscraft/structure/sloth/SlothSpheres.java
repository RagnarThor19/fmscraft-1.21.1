package net.ragnar.fmscraft.structure.sloth;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What happens inside a Sphere of Sloth:
 * <ul>
 *   <li><b>Drowsiness</b> ({@link Drowsiness}): a boss bar that fills while you're inside, faster standing still.
 *       Slowness, then heavier Slowness, then darkness, and at the top you fall asleep where you stand for five seconds.
 *       The whole sphere also gives Mining Fatigue, like an ocean monument, so nobody digs to the core.</li>
 *   <li><b>Bells</b>: four along the stair. Ringing one (by hand, arrow or redstone) wakes everyone near it, and lights
 *       the four candles in front of it, which burn down one by one over {@link #BELL_TICKS}. Ring all four while
 *       they're all still burning and the seal on the core crumbles open.</li>
 *   <li><b>The seal</b> closes again once nobody is left in or near the sphere, and the bells go quiet.</li>
 * </ul>
 * Nothing is saved: after a restart the bells start silent and an open seal closes the next time someone comes by.
 */
public final class SlothSpheres {
    public static final int BELL_TICKS = 160 * 20;
    public static final int CANDLE_TICKS = BELL_TICKS / 4;
    /** Ringing a bell wakes everyone this close to it. */
    public static final double WAKE_RADIUS = 24;
    public static final int ASLEEP_TICKS = 5 * 20;
    /** The seal closes when nobody is this close to the center. */
    public static final double RESEAL_DISTANCE = SlothSphereShape.RADIUS + 16;
    private static final int SCAN_CHUNKS = 6;
    public static final RegistryKey<Structure> SPHERE = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "sphere_of_sloth"));

    /** A sphere someone is near, with its bells. */
    public static final class Sphere {
        private final ServerWorld world;
        private final SlothSpherePiece piece;
        private final long[] rung = new long[SlothSphereShape.BELLS.size()];

        Sphere(ServerWorld world, SlothSpherePiece piece) {
            this.world = world;
            this.piece = piece;
            Arrays.fill(rung, Long.MIN_VALUE / 2);
        }

        public long rungAt(int bell) {
            return rung[bell];
        }

        public void setRungAt(int bell, long tick) {
            rung[bell] = tick;
        }

        public boolean ringing(int bell) {
            return world.getTime() - rung[bell] < BELL_TICKS;
        }

        public int bellsRinging() {
            int n = 0;
            for (int i = 0; i < rung.length; i++) if (ringing(i)) n++;
            return n;
        }
    }

    private record Key(RegistryKey<World> world, long center) {
    }

    private static final class Sleeper {
        final Drowsiness drowsiness = new Drowsiness();
        final ServerBossBar bar = new ServerBossBar(Text.translatable("bossbar.fmscraft.drowsiness"), BossBar.Color.WHITE, BossBar.Style.NOTCHED_10);
        Vec3d last;
        long asleepUntil;
    }

    private static final Map<Key, Sphere> SPHERES = new HashMap<>();
    private static final Map<UUID, Sleeper> SLEEPERS = new HashMap<>();

    private SlothSpheres() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SlothSpheres::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Sleeper sleeper = SLEEPERS.remove(handler.player.getUuid());
            if (sleeper != null) sleeper.bar.clearPlayers();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SPHERES.clear();
            SLEEPERS.clear();
        });
    }

    // ---- Finding spheres ----

    private static Structure structure(ServerWorld world) {
        return world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(SPHERE);
    }

    /** The sphere this piece belongs to, tracked from now on. */
    public static Sphere sphere(ServerWorld world, SlothSpherePiece piece) {
        return SPHERES.computeIfAbsent(new Key(world.getRegistryKey(), piece.center().asLong()), k -> new Sphere(world, piece));
    }

    /** The sphere whose structure covers {@code pos}, or null. */
    public static SlothSpherePiece pieceAt(ServerWorld world, BlockPos pos) {
        Structure structure = structure(world);
        if (structure == null) return null;
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, structure);
        return start.hasChildren() && start.getChildren().get(0) instanceof SlothSpherePiece piece ? piece : null;
    }

    /** Looks round every player for spheres. Ones nobody is near any more are forgotten (their bells go quiet). */
    private static void scan(MinecraftServer server) {
        Map<Key, Sphere> found = new HashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) continue;
            ServerWorld world = player.getServerWorld();
            Structure structure = structure(world);
            if (structure == null) continue;
            ChunkPos at = player.getChunkPos();
            for (int cx = at.x - SCAN_CHUNKS; cx <= at.x + SCAN_CHUNKS; cx++) {
                for (int cz = at.z - SCAN_CHUNKS; cz <= at.z + SCAN_CHUNKS; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    for (StructureStart start : world.getStructureAccessor().getStructureStarts(ChunkSectionPos.from(new ChunkPos(cx, cz), 0), structure)) {
                        if (!start.hasChildren() || !(start.getChildren().get(0) instanceof SlothSpherePiece piece)) continue;
                        Key key = new Key(world.getRegistryKey(), piece.center().asLong());
                        found.computeIfAbsent(key, k -> SPHERES.containsKey(k) ? SPHERES.get(k) : new Sphere(world, piece));
                    }
                }
            }
        }
        SPHERES.clear();
        SPHERES.putAll(found);
    }

    private static Sphere sphereAround(ServerPlayerEntity player, double reach) {
        for (Sphere sphere : SPHERES.values()) {
            if (sphere.world == player.getServerWorld() && sphere.piece.distanceFromCenter(player.getPos()) < reach) return sphere;
        }
        return null;
    }

    // ---- Ticking ----

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 == 0) scan(server);
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Sphere sphere = sphereAround(player, SlothSphereShape.RADIUS + 2);
            if (sphere != null && tickPlayer(sphere, player)) inside.add(player.getUuid());
        }
        SLEEPERS.entrySet().removeIf(entry -> {
            if (inside.contains(entry.getKey())) return false;
            entry.getValue().bar.clearPlayers();
            return true;
        });
        if (server.getTicks() % 20 == 0) {
            for (Sphere sphere : List.copyOf(SPHERES.values())) update(sphere);
        }
    }

    /** One player in or right by a sphere. Returns whether they're inside it (and drowsing). */
    private static boolean tickPlayer(Sphere sphere, ServerPlayerEntity player) {
        if (player.isSpectator() || player.isCreative()) return false;
        ServerWorld world = sphere.world;
        long now = world.getTime();
        double r = sphere.piece.distanceFromCenter(player.getPos());
        if (now % 40 == 0) refresh(player, StatusEffects.MINING_FATIGUE, 200, 2);
        if (r >= SlothSphereShape.RADIUS) return false;

        Sleeper sleeper = SLEEPERS.computeIfAbsent(player.getUuid(), id -> new Sleeper());
        if (!sleeper.bar.getPlayers().contains(player)) sleeper.bar.addPlayer(player);
        Vec3d pos = player.getPos();
        boolean still = sleeper.last != null && pos.squaredDistanceTo(sleeper.last) < 1.0E-4;
        sleeper.last = pos;
        boolean resting = r < SlothSphereShape.CORE;
        Drowsiness drowsiness = sleeper.drowsiness;
        boolean asleep = now < sleeper.asleepUntil;
        if (!asleep && drowsiness.tick(still, resting)) fallAsleep(world, player, sleeper, now);

        sleeper.bar.setPercent(drowsiness.fraction());
        sleeper.bar.setColor(drowsiness.value() >= Drowsiness.HEAVY_SLOWNESS ? BossBar.Color.PURPLE : BossBar.Color.WHITE);
        if (now % 20 == 0 && !asleep) {
            if (drowsiness.slowness() >= 0) player.addStatusEffect(effect(StatusEffects.SLOWNESS, 40, drowsiness.slowness()));
            if (drowsiness.darkness()) player.addStatusEffect(effect(StatusEffects.DARKNESS, 60, 0));
        }
        return true;
    }

    private static StatusEffectInstance effect(RegistryEntry<StatusEffect> type, int ticks, int amplifier) {
        return new StatusEffectInstance(type, ticks, amplifier, true, false, true);
    }

    private static void refresh(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, int ticks, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(type);
        if (current == null || current.getDuration() < ticks / 2) player.addStatusEffect(effect(type, ticks, amplifier));
    }

    /** Full drowsiness: you nod off where you stand, blind and unable to move, for five seconds. */
    private static void fallAsleep(ServerWorld world, ServerPlayerEntity player, Sleeper sleeper, long now) {
        sleeper.asleepUntil = now + ASLEEP_TICKS;
        player.addStatusEffect(effect(StatusEffects.SLOWNESS, ASLEEP_TICKS, 6));
        player.addStatusEffect(effect(StatusEffects.BLINDNESS, ASLEEP_TICKS + 20, 0));
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_FOX_SLEEP, SoundCategory.PLAYERS, 1.0f, 0.6f);
        player.sendMessage(Text.translatable("message.fmscraft.sloth_asleep").formatted(Formatting.GRAY), true);
    }

    private static void wake(ServerPlayerEntity player, Sleeper sleeper) {
        sleeper.drowsiness.wake();
        sleeper.asleepUntil = 0;
        for (RegistryEntry<StatusEffect> type : List.of(StatusEffects.SLOWNESS, StatusEffects.DARKNESS, StatusEffects.BLINDNESS)) {
            StatusEffectInstance current = player.getStatusEffect(type);
            if (current != null && current.isAmbient()) player.removeStatusEffect(type);
        }
    }

    /** Once a second: candles burn down, the seal closes behind everyone, and the sphere snores. */
    public static void update(Sphere sphere) {
        ServerWorld world = sphere.world;
        SlothSpherePiece piece = sphere.piece;
        long now = world.getTime();
        List<BlockPos> candles = piece.candles();
        for (int i = 0; i < candles.size(); i++) {
            long left = BELL_TICKS - (now - sphere.rung[i]);
            if (left > 0) setCandles(world, candles.get(i), (int) Math.min(4, (left + CANDLE_TICKS - 1) / CANDLE_TICKS), true);
            else setCandles(world, candles.get(i), 4, false);
        }
        Vec3d center = Vec3d.ofCenter(piece.center());
        if (isOpen(world, piece) && !playerWithin(world, center, RESEAL_DISTANCE)) close(sphere);
        if (world.getRandom().nextInt(7) == 0 && playerWithin(world, center, SlothSphereShape.RADIUS + 40)) {
            // It's asleep, and you can hear it from a long way off
            world.playSound(null, piece.center(), SoundEvents.ENTITY_FOX_SLEEP, SoundCategory.AMBIENT, 5.0f, 0.5f);
        }
    }

    private static boolean playerWithin(ServerWorld world, Vec3d center, double distance) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSpectator() && player.getPos().distanceTo(center) < distance) return true;
        }
        return false;
    }

    private static void setCandles(ServerWorld world, BlockPos pos, int candles, boolean lit) {
        if (!world.isChunkLoaded(pos)) return;
        BlockState current = world.getBlockState(pos);
        if (!current.isOf(Blocks.GRAY_CANDLE)) return; // taken by someone
        if (current.get(CandleBlock.CANDLES) == candles && current.get(CandleBlock.LIT) == lit) return;
        if (current.get(CandleBlock.LIT) && (!lit || candles < current.get(CandleBlock.CANDLES))) {
            world.playSound(null, pos, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        world.setBlockState(pos, current.with(CandleBlock.CANDLES, candles).with(CandleBlock.LIT, lit), Block.NOTIFY_ALL);
    }

    // ---- Bells and the seal ----

    /** Called whenever any bell rings anywhere. */
    public static void onBellRung(ServerWorld world, BlockPos pos) {
        SlothSpherePiece piece = pieceAt(world, pos);
        if (piece == null) return;
        int bell = piece.bellIndex(pos);
        if (bell >= 0) ring(world, piece, bell);
    }

    public static void ring(ServerWorld world, SlothSpherePiece piece, int bell) {
        Sphere sphere = sphere(world, piece);
        sphere.rung[bell] = world.getTime();
        setCandles(world, piece.candles().get(bell), 4, true);
        BlockPos at = piece.bells().get(bell);
        world.playSound(null, at, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 2.0f, 0.8f);

        Vec3d bellPos = Vec3d.ofCenter(at), center = Vec3d.ofCenter(piece.center());
        for (ServerPlayerEntity player : world.getPlayers()) {
            Sleeper sleeper = SLEEPERS.get(player.getUuid());
            if (sleeper != null && player.getPos().distanceTo(bellPos) < WAKE_RADIUS) wake(player, sleeper);
        }
        int ringing = sphere.bellsRinging();
        boolean opening = ringing == SlothSphereShape.BELLS.size() && !isOpen(world, piece);
        Text message = opening ? Text.translatable("message.fmscraft.sloth_seal_open").withColor(0xB7A0D8)
                : Text.translatable("message.fmscraft.sloth_bells", ringing, SlothSphereShape.BELLS.size()).formatted(Formatting.GRAY);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getPos().distanceTo(center) < SlothSphereShape.RADIUS + 8) player.sendMessage(message, true);
        }
        if (opening) open(world, piece);
    }

    public static boolean isOpen(ServerWorld world, SlothSpherePiece piece) {
        BlockPos probe = piece.sealProbe();
        return world.isChunkLoaded(probe) && !world.getBlockState(probe).isOf(Blocks.CRYING_OBSIDIAN);
    }

    /** The seal crumbles away, leaving the shaft down into the core. */
    private static void open(ServerWorld world, SlothSpherePiece piece) {
        BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.getDefaultState());
        for (BlockPos pos : piece.sealBlocks()) {
            if (!world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN)) continue;
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.spawnParticles(dust, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
        }
        BlockPos top = piece.toWorld(0, SlothSphereShape.TOP, 0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, 80, 1.5, 0.5, 1.5, 0.05);
        world.playSound(null, top, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 1.0f, 0.6f);
        world.playSound(null, top, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.BLOCKS, 2.0f, 0.5f);
    }

    /** Nobody's left near: the seal grows back and the bells go quiet. */
    private static void close(Sphere sphere) {
        for (BlockPos pos : sphere.piece.sealBlocks()) {
            if (sphere.world.getBlockState(pos).isAir()) sphere.world.setBlockState(pos, Blocks.CRYING_OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
        }
        Arrays.fill(sphere.rung, Long.MIN_VALUE / 2);
        List<BlockPos> candles = sphere.piece.candles();
        for (BlockPos candle : candles) setCandles(sphere.world, candle, 4, false);
    }

    // ---- For tests ----

    /** The drowsiness of a player who's in a sphere right now, or null. */
    public static Drowsiness drowsiness(ServerPlayerEntity player) {
        Sleeper sleeper = SLEEPERS.get(player.getUuid());
        return sleeper == null ? null : sleeper.drowsiness;
    }

    public static boolean showsDrowsiness(ServerPlayerEntity player) {
        Sleeper sleeper = SLEEPERS.get(player.getUuid());
        return sleeper != null && sleeper.bar.getPlayers().contains(player);
    }
}
