package net.ragnar.fmscraft.structure.lust;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.LustStatueEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.network.GardenFogPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What happens in and around a Lust's Garden:
 * <ul>
 *   <li>Anyone inside the hedge has Slowness II, Mining Fatigue X, Block Barrier and Item Barrier (nothing but food can
 *       be used), and the fog takes on a faint rose tint ({@link GardenFogPayload}).</li>
 *   <li><b>The Statues of Lust</b> ({@link LustStatueEntity}): one on every plinth, raised once someone is within
 *       {@link #ACTIVE_MARGIN} blocks of the garden and kept until everyone is {@link #RESET_MARGIN} away. Asleep
 *       until looked in the face.</li>
 *   <li><b>The challenge</b>: four empty plinths stand round the fountain. Lure an awake statue to within
 *       {@link #CAPTURE} blocks of one while nobody is looking at it, and it steps up onto it, turns to the fountain
 *       and closes its eyes; the two candles at the plinth's foot light. With all four home, the garden is at peace:
 *       a fanfare, the effects lift, every statue closes its eyes, the fountain runs dry, and the basin's floor sinks
 *       away row by row over the stair down to the crypt and the Lust lock.</li>
 *   <li><b>Resetting</b>: once nobody is within {@link #RESET_MARGIN} blocks (and no Lust fight is on), the statues go,
 *       the basin closes and fills again, the fountain runs and the candles go out.</li>
 * </ul>
 * Nothing is saved: whether it's been solved is read back from the world (is the basin's floor still there?).
 */
public final class LustGardens {
    public static final int ACTIVE_MARGIN = 24;
    public static final int RESET_MARGIN = 40;
    /** Mining Fatigue X, Slowness II (amplifiers are one less). */
    public static final int FATIGUE = 9, SLOWNESS = 1;
    /** An awake statue this close (flat) to an empty fountain plinth, unwatched, takes its place on it. */
    public static final double CAPTURE = 3.0;
    private static final int UPDATE_TICKS = 10;
    private static final int SCAN_TICKS = 20;
    private static final int SCAN_CHUNKS = 5;
    /** New statues won't appear on a plinth someone is this close to. */
    private static final double HOME_CLEARANCE = 16;
    public static final RegistryKey<Structure> GARDEN = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "lusts_garden"));
    private static final int LUST_COLOR = 0xC39BD3;

    /** A garden someone is near. */
    public static final class Garden {
        final ServerWorld world;
        final LustGardenPiece piece;
        final UUID[] statues;
        /** The statue standing on each fountain plinth, or null. */
        final UUID[] settled = new UUID[4];
        /** Steps of the fountain draining and the stair opening, or -1. */
        int opening = -1;
        boolean wasNearby;
        /** At peace (the stair is open). Null until first checked. */
        Boolean solved;

        Garden(ServerWorld world, LustGardenPiece piece) {
            this.world = world;
            this.piece = piece;
            this.statues = new UUID[piece.plan().plinths().size()];
        }

        public LustGardenPiece piece() {
            return piece;
        }

        public BlockBox box() {
            return piece.getBoundingBox();
        }

        public int settledCount() {
            int n = 0;
            for (UUID id : settled) if (id != null) n++;
            return n;
        }
    }

    private record Key(RegistryKey<World> world, long id) {
    }

    private static final Map<Key, Garden> GARDENS = new HashMap<>();
    private static final Set<UUID> IN_GARDEN = new HashSet<>();
    private static final Set<UUID> TOLD_IN = new HashSet<>();

    private LustGardens() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LustGardens::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            GARDENS.clear();
            IN_GARDEN.clear();
            TOLD_IN.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD_IN.remove(handler.player.getUuid()));
    }

    // ---- Finding gardens ----

    private static Structure structure(ServerWorld world) {
        return world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(GARDEN);
    }

    /** The garden whose structure covers {@code pos}, or null. */
    public static LustGardenPiece pieceAt(ServerWorld world, BlockPos pos) {
        Structure structure = structure(world);
        if (structure == null) return null;
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, structure);
        return start.hasChildren() && start.getChildren().get(0) instanceof LustGardenPiece piece ? piece : null;
    }

    public static Garden garden(ServerWorld world, LustGardenPiece piece) {
        return GARDENS.computeIfAbsent(new Key(world.getRegistryKey(), piece.id()), k -> new Garden(world, piece));
    }

    private static void scan(MinecraftServer server) {
        Map<Key, Garden> found = new HashMap<>();
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
                        if (!start.hasChildren() || !(start.getChildren().get(0) instanceof LustGardenPiece piece)) continue;
                        Key key = new Key(world.getRegistryKey(), piece.id());
                        found.computeIfAbsent(key, k -> GARDENS.containsKey(k) ? GARDENS.get(k) : new Garden(world, piece));
                    }
                }
            }
        }
        for (Map.Entry<Key, Garden> entry : GARDENS.entrySet()) {
            if (!found.containsKey(entry.getKey())) removeStatues(entry.getValue());
        }
        GARDENS.clear();
        GARDENS.putAll(found);
    }

    // ---- Ticking ----

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % SCAN_TICKS == 0) scan(server);
        if (server.getTicks() % UPDATE_TICKS != 0) return;
        IN_GARDEN.clear();
        for (Garden garden : List.copyOf(GARDENS.values())) update(garden);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean in = IN_GARDEN.contains(player.getUuid());
            if (in == TOLD_IN.contains(player.getUuid())) continue;
            if (in) TOLD_IN.add(player.getUuid());
            else TOLD_IN.remove(player.getUuid());
            if (ServerPlayNetworking.canSend(player, GardenFogPayload.ID)) ServerPlayNetworking.send(player, new GardenFogPayload(in));
        }
    }

    public static boolean inGarden(ServerPlayerEntity player) {
        return IN_GARDEN.contains(player.getUuid());
    }

    /** Every half second, for each garden someone is near. */
    public static void update(Garden garden) {
        ServerWorld world = garden.world;
        LustGardenPiece piece = garden.piece;
        BlockBox box = garden.box();
        if (garden.solved == null && loaded(garden, piece.capBlocks())) {
            garden.solved = capOpen(garden); // first look since a restart
            if (!garden.solved) for (int i = 0; i < 4; i++) setCandles(garden, i, false);
        }
        boolean solved = Boolean.TRUE.equals(garden.solved);
        boolean active = false, nearby = false;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            BlockPos at = player.getBlockPos();
            if (box.expand(RESET_MARGIN).contains(at)) nearby = true;
            if (box.expand(ACTIVE_MARGIN).contains(at)) active = true;
            if (!piece.inGarden(at)) continue;
            IN_GARDEN.add(player.getUuid());
            if (!player.isCreative() && !solved) {
                refresh(player, StatusEffects.MINING_FATIGUE, FATIGUE);
                refresh(player, StatusEffects.SLOWNESS, SLOWNESS);
                refresh(player, ModEffects.BLOCK_BARRIER, 0);
                refresh(player, ModEffects.ITEM_BARRIER, 0);
            }
        }

        if (active && !solved) {
            ensureStatues(garden);
            settleStatues(garden);
            if (garden.opening < 0 && someoneInCrypt(garden)) solve(garden, false);
        }
        // Statues stay (and stay where they were lured) until everyone has gone right away
        if (!nearby && garden.wasNearby) removeStatues(garden);
        garden.wasNearby = nearby;
        if (garden.opening >= 0) openStep(garden);
        if (!nearby && BossFights.session(Sin.LUST) == null) reset(garden);
    }

    private static void refresh(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(type);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 40) {
            player.addStatusEffect(new StatusEffectInstance(type, 80, amplifier, true, false, true));
        }
    }

    private static boolean loaded(Garden garden, List<BlockPos> blocks) {
        for (BlockPos pos : blocks) if (!garden.world.isChunkLoaded(pos)) return false;
        return true;
    }

    public static boolean capOpen(Garden garden) {
        if (!loaded(garden, garden.piece.capBlocks())) return false;
        for (BlockPos pos : garden.piece.capBlocks()) if (!garden.world.getBlockState(pos).isAir()) return false;
        return true;
    }

    public static boolean capShut(Garden garden) {
        if (!loaded(garden, garden.piece.capBlocks())) return false;
        for (BlockPos pos : garden.piece.capBlocks()) if (!garden.world.getBlockState(pos).isAir()) return true;
        return false;
    }

    /** Someone down the stair or in the crypt while the way up is shut, who'd otherwise be stuck down there. */
    private static boolean someoneInCrypt(Garden garden) {
        if (!capShut(garden)) return false;
        for (ServerPlayerEntity player : garden.world.getPlayers()) {
            if (!player.isSpectator() && garden.piece.inCrypt(player.getBlockPos())) return true;
        }
        return false;
    }

    // ---- Statues ----

    private static LustStatueEntity statue(Garden garden, UUID id) {
        return id != null && garden.world.getEntity(id) instanceof LustStatueEntity statue && statue.isAlive() ? statue : null;
    }

    public static List<LustStatueEntity> statues(Garden garden) {
        List<LustStatueEntity> statues = new ArrayList<>();
        for (UUID id : garden.statues) {
            LustStatueEntity statue = statue(garden, id);
            if (statue != null) statues.add(statue);
        }
        return statues;
    }

    /** Any statue that's missing appears on its plinth, unless someone is near enough to see it happen. */
    private static void ensureStatues(Garden garden) {
        List<BlockPos> homes = garden.piece.statueHomes();
        Box area = Box.from(garden.box()).expand(4);
        for (int i = 0; i < homes.size(); i++) {
            if (statue(garden, garden.statues[i]) != null) continue;
            BlockPos home = homes.get(i);
            if (!garden.world.isChunkLoaded(home)) continue;
            List<LustStatueEntity> left = garden.world.getEntitiesByClass(LustStatueEntity.class, area, s -> s.isAlive() && home.equals(s.getHome()));
            if (!left.isEmpty()) {
                garden.statues[i] = left.get(0).getUuid();
                for (LustStatueEntity extra : left.subList(1, left.size())) extra.discard();
                continue;
            }
            boolean watched = garden.world.getPlayers().stream()
                    .anyMatch(p -> !p.isSpectator() && p.squaredDistanceTo(home.toCenterPos()) < HOME_CLEARANCE * HOME_CLEARANCE);
            if (watched) continue;
            LustStatueEntity statue = ModEntities.LUST_STATUE.create(garden.world);
            if (statue == null) continue;
            float yaw = garden.piece.statueYaw(i);
            statue.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, yaw, 0);
            statue.setHeadYaw(yaw);
            statue.setBodyYaw(yaw);
            statue.bind(garden.piece::inGarden, home, yaw);
            garden.world.spawnEntity(statue);
            garden.statues[i] = statue.getUuid();
        }
    }

    /** Every statue of this garden goes, the ones it tracks and any it lost track of. */
    private static void removeStatues(Garden garden) {
        for (int i = 0; i < garden.statues.length; i++) {
            LustStatueEntity statue = statue(garden, garden.statues[i]);
            if (statue != null) statue.discard();
            garden.statues[i] = null;
        }
        java.util.Arrays.fill(garden.settled, null);
        List<BlockPos> homes = garden.piece.statueHomes();
        for (LustStatueEntity stray : garden.world.getEntitiesByClass(LustStatueEntity.class, Box.from(garden.box()).expand(4),
                s -> homes.contains(s.getHome()))) {
            stray.discard();
        }
    }

    /** An awake statue near an empty fountain plinth, that nobody is watching, steps up onto it. */
    private static void settleStatues(Garden garden) {
        List<BlockPos> tops = garden.piece.fountainHomes();
        for (LustStatueEntity statue : statues(garden)) {
            if (!statue.isAwake() || statue.isSettled() || statue.isObserved()) continue;
            for (int i = 0; i < tops.size(); i++) {
                if (statue(garden, garden.settled[i]) != null) continue;
                BlockPos top = tops.get(i);
                double dx = statue.getX() - (top.getX() + 0.5), dz = statue.getZ() - (top.getZ() + 0.5);
                if (dx * dx + dz * dz > CAPTURE * CAPTURE || Math.abs(statue.getY() - top.getY()) > 2.5) continue;
                if (!garden.world.getOtherEntities(statue, new Box(top).stretch(0, 1.5, 0)).isEmpty()) continue;
                settle(garden, statue, i);
                break;
            }
        }
    }

    public static void settle(Garden garden, LustStatueEntity statue, int plinth) {
        BlockPos top = garden.piece.fountainHomes().get(plinth);
        statue.settle(top, garden.piece.fountainYaw(plinth));
        garden.settled[plinth] = statue.getUuid();
        setCandles(garden, plinth, true);
        garden.world.playSound(null, top, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 1.0f, 0.4f);
        garden.world.playSound(null, top, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 2.0f, 0.6f);
        int home = garden.settledCount();
        Text message = Text.translatable("message.fmscraft.lust_statue_home", home, 4).withColor(LUST_COLOR);
        for (ServerPlayerEntity player : garden.world.getPlayers()) {
            if (garden.box().expand(ACTIVE_MARGIN).contains(player.getBlockPos())) player.sendMessage(message, true);
        }
        if (home >= 4) solve(garden, true);
    }

    private static void setCandles(Garden garden, int plinth, boolean lit) {
        for (BlockPos pos : garden.piece.fountainCandles(plinth)) {
            if (!garden.world.isChunkLoaded(pos)) continue;
            BlockState state = garden.world.getBlockState(pos);
            if (state.getBlock() instanceof CandleBlock && state.get(CandleBlock.LIT) != lit) {
                garden.world.setBlockState(pos, state.with(CandleBlock.LIT, lit), Block.NOTIFY_ALL);
            }
        }
    }

    // ---- Peace ----

    /**
     * The garden is at peace: the effects lift, every statue closes its eyes, and the fountain starts to run dry over
     * the stair. With {@code earned} (four statues home, rather than someone being let out) everyone hears the fanfare.
     */
    private static void solve(Garden garden, boolean earned) {
        garden.solved = true;
        for (LustStatueEntity statue : statues(garden)) statue.calm();
        for (ServerPlayerEntity player : garden.world.getPlayers()) {
            if (!garden.box().expand(ACTIVE_MARGIN).contains(player.getBlockPos())) continue;
            for (RegistryEntry<StatusEffect> type : List.of(StatusEffects.MINING_FATIGUE, StatusEffects.SLOWNESS, ModEffects.BLOCK_BARRIER, ModEffects.ITEM_BARRIER)) {
                StatusEffectInstance current = player.getStatusEffect(type);
                if (current != null && current.isAmbient()) player.removeStatusEffect(type);
            }
            if (earned) {
                player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
                player.sendMessage(Text.translatable("message.fmscraft.lust_fountain_dry").withColor(LUST_COLOR), true);
            }
        }
        garden.opening = 0;
    }

    /**
     * One step of opening, every half second: the spouts stop; a second later the basin drains; then the floor over
     * the stair sinks away a row at a time.
     */
    private static void openStep(Garden garden) {
        ServerWorld world = garden.world;
        LustGardenPiece piece = garden.piece;
        BlockPos middle = piece.toWorld(0, -1, -4);
        int step = garden.opening++;
        if (step == 0) {
            for (BlockPos spout : piece.spouts()) {
                if (world.getBlockState(spout).isOf(Blocks.WATER)) world.setBlockState(spout, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
            world.playSound(null, piece.toWorld(0, 3, 0), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.6f);
        } else if (step == 3) {
            drain(garden);
            world.playSound(null, middle, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 2.0f, 0.5f);
            world.playSound(null, middle, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.BLOCKS, 1.5f, 0.6f);
        } else if (step >= 5 && step < 5 + LustGardenPiece.CAP_ROWS) {
            drain(garden);
            BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.TUFF_BRICKS.getDefaultState());
            for (BlockPos pos : piece.capRow(step - 5)) {
                if (world.getBlockState(pos).isAir()) continue;
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.spawnParticles(dust, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
            }
            world.playSound(null, middle, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.2f, 0.5f);
        } else if (step >= 5 + LustGardenPiece.CAP_ROWS) {
            drain(garden);
            world.playSound(null, middle, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 1.5f, 0.6f);
            garden.opening = -1;
        }
    }

    /**
     * Every drop of water in the fountain and down the stair goes, flowing water too: whatever was still falling from
     * the spouts would otherwise slosh about the basin and run down the stair.
     */
    private static void drain(Garden garden) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos o = garden.piece.origin();
        int r = (int) Math.ceil(LustGardenPlan.RIM);
        for (int x = -r; x <= r; x++) {
            for (int z = LustGardenPlan.CRYPT_NEAR; z <= r; z++) {
                for (int y = LustGardenPlan.CRYPT_FLOOR; y <= LustGardenPlan.SPOUT_Y; y++) {
                    pos.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    if (garden.world.getBlockState(pos).isOf(Blocks.WATER)) garden.world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    // ---- Resetting ----

    /** Puts the garden back the way it grew. Only touches what has changed, so it's cheap to run often. */
    public static void reset(Garden garden) {
        ServerWorld world = garden.world;
        LustGardenPiece piece = garden.piece;
        garden.opening = -1;
        garden.solved = false;
        java.util.Arrays.fill(garden.settled, null);
        if (loaded(garden, piece.capBlocks())) {
            for (BlockPos pos : piece.capBlocks()) {
                if (world.getBlockState(pos).isAir() && world.getOtherEntities(null, new Box(pos)).isEmpty()) {
                    world.setBlockState(pos, piece.capState(pos), Block.NOTIFY_ALL);
                }
            }
        }
        for (BlockPos pos : piece.basinWater()) {
            if (!world.isChunkLoaded(pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || (state.isOf(Blocks.WATER) && !state.getFluidState().isStill())) {
                world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        for (BlockPos spout : piece.spouts()) {
            if (world.isChunkLoaded(spout) && world.getBlockState(spout).isAir()) world.setBlockState(spout, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
        }
        for (int i = 0; i < 4; i++) setCandles(garden, i, false);
    }

    // ---- For tests ----

    public static Garden tracked(ServerWorld world, LustGardenPiece piece) {
        return GARDENS.get(new Key(world.getRegistryKey(), piece.id()));
    }

    public static boolean solved(Garden garden) {
        return Boolean.TRUE.equals(garden.solved);
    }
}
