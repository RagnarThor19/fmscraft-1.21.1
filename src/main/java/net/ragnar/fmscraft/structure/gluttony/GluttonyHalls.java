package net.ragnar.fmscraft.structure.gluttony;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.GorgedBruteEntity;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHallShape.Spot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What happens in and around a Hall of Gluttony:
 * <ul>
 *   <li><b>The feast</b>: when someone comes within {@link #ACTIVE_MARGIN} blocks, {@link #PIGLINS} piglins and
 *       {@link #BRUTES} piglin brutes are put at the banquet (real vanilla ones, armed as a bastion's are), and the
 *       Gorged Brute ({@link GorgedBruteEntity}) on his dais in front of the gate. While anyone is in the hall, more
 *       come in every {@link #REINFORCE_TICKS} out of sight down the aisles, up to as many as there were.</li>
 *   <li><b>The hall's rules</b>: anyone in the hall itself has Block Barrier, so nobody walls themselves in. Not
 *       Mining Fatigue, as the other structures give: it slows your swing to nothing, and this hall is a fight.</li>
 *   <li><b>Feeding him</b>: any food used on him, one mouthful a second. A boss bar shows everyone in the hall how full
 *       he is. At {@link GorgedBruteEntity#FOOD_TO_BURST} he bursts: the fanfare, the rules lift, no more piglins come,
 *       and the gilded gate sinks into the floor a row at a time.</li>
 *   <li><b>The chapel is safe</b>: nothing can target a player in the chapel behind the gate (LivingEntityMixin), and
 *       any of the feast's piglins that wander in are turned back.</li>
 *   <li><b>Resetting</b>: once nobody is within {@link #RESET_MARGIN} blocks (and no Gluttony fight is on), the feast
 *       is cleared away, the gate closes and he'll be back, hungry, next time. Anyone in the chapel while the gate is
 *       shut (back from the fight after a reset) gets it opened, so nobody is ever shut in.</li>
 * </ul>
 * The gate is read back from the world, so a restart changes nothing; what he's eaten is forgotten.
 */
public final class GluttonyHalls {
    public static final int ACTIVE_MARGIN = 32;
    public static final int RESET_MARGIN = 64;
    public static final int PIGLINS = 10, BRUTES = 4;
    public static final int REINFORCE_TICKS = 10 * 20;
    /** New piglins never come in this close to anyone. */
    public static final double REINFORCE_CLEARANCE = 12;
    /** The feast's piglins carry this tag, so they're known after a restart and cleared away with the hall. */
    public static final String FEAST_TAG = "fmscraft.gluttony_feast";
    private static final int UPDATE_TICKS = 10;
    private static final int SHELTER_TICKS = 5;
    private static final int SCAN_TICKS = 20;
    private static final int SCAN_CHUNKS = 8;
    public static final RegistryKey<Structure> HALL = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "hall_of_gluttony"));
    private static final int GLUTTONY_GOLD = 0xE3B34B;

    /** A hall someone is near. */
    public static final class Hall {
        final ServerWorld world;
        final GluttonyHallPiece piece;
        final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.fmscraft.gorged_brute"), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);
        UUID brute;
        /** What he's eaten this visit, kept in case he has to be put back. */
        int eaten;
        /** The next gate row to sink (0 = top), or -1 while the gate isn't opening. */
        int opening = -1;
        /** He's burst (the gate was opened): no rules, no more piglins, until the hall resets. Null until first checked. */
        Boolean sated;
        /** The feast was laid this visit. */
        boolean laid;
        long lastReinforced;
        boolean wasNearby;

        Hall(ServerWorld world, GluttonyHallPiece piece) {
            this.world = world;
            this.piece = piece;
        }

        public GluttonyHallPiece piece() {
            return piece;
        }

        public BlockBox box() {
            return piece.getBoundingBox();
        }

        public boolean sated() {
            return Boolean.TRUE.equals(sated);
        }

        public int eaten() {
            return eaten;
        }

        public boolean showsBar(ServerPlayerEntity player) {
            return bar.getPlayers().contains(player);
        }
    }

    private record Key(RegistryKey<World> world, long center) {
    }

    private static final Map<Key, Hall> HALLS = new HashMap<>();

    private GluttonyHalls() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(GluttonyHalls::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (Hall hall : HALLS.values()) hall.bar.clearPlayers();
            HALLS.clear();
        });
        GorgedBruteEntity.onFed = GluttonyHalls::onFed;
    }

    // ---- Finding halls ----

    private static Structure structure(ServerWorld world) {
        return world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(HALL);
    }

    /** The hall whose structure covers {@code pos}, or null. */
    public static GluttonyHallPiece pieceAt(ServerWorld world, BlockPos pos) {
        Structure structure = structure(world);
        if (structure == null) return null;
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, structure);
        return start.hasChildren() && start.getChildren().get(0) instanceof GluttonyHallPiece piece ? piece : null;
    }

    public static Hall hall(ServerWorld world, GluttonyHallPiece piece) {
        return HALLS.computeIfAbsent(new Key(world.getRegistryKey(), piece.center().asLong()), k -> new Hall(world, piece));
    }

    private static void scan(MinecraftServer server) {
        Map<Key, Hall> found = new HashMap<>();
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
                        if (!start.hasChildren() || !(start.getChildren().get(0) instanceof GluttonyHallPiece piece)) continue;
                        Key key = new Key(world.getRegistryKey(), piece.center().asLong());
                        found.computeIfAbsent(key, k -> HALLS.containsKey(k) ? HALLS.get(k) : new Hall(world, piece));
                    }
                }
            }
        }
        // Halls nobody is near any more are let go; anything left of them is cleared up first
        for (Map.Entry<Key, Hall> entry : HALLS.entrySet()) {
            if (!found.containsKey(entry.getKey())) leave(entry.getValue());
        }
        HALLS.clear();
        HALLS.putAll(found);
    }

    // ---- Ticking ----

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % SCAN_TICKS == 0) scan(server);
        if (server.getTicks() % SHELTER_TICKS == 0) {
            for (Hall hall : List.copyOf(HALLS.values())) shelter(hall);
        }
        if (server.getTicks() % UPDATE_TICKS != 0) return;
        for (Hall hall : List.copyOf(HALLS.values())) update(hall);
    }

    /** Every half second, for each hall someone is near. */
    public static void update(Hall hall) {
        ServerWorld world = hall.world;
        BlockBox box = hall.box();
        if (hall.sated == null && loaded(hall, hall.piece.gate())) hall.sated = gateOpen(hall); // after a restart
        boolean sated = hall.sated();
        boolean active = false, nearby = false;
        List<ServerPlayerEntity> inHall = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            BlockPos at = player.getBlockPos();
            if (box.expand(RESET_MARGIN).contains(at)) nearby = true;
            if (box.expand(ACTIVE_MARGIN).contains(at)) active = true;
            if (!hall.piece.inHall(at) || player.isCreative()) continue;
            inHall.add(player);
            if (sated) continue;
            refresh(player, ModEffects.BLOCK_BARRIER, 0);
        }
        if (!nearby) {
            if (hall.wasNearby) leave(hall);
            hall.wasNearby = false;
            if (BossFights.session(Sin.GLUTTONY) == null) reset(hall);
            return;
        }
        hall.wasNearby = true;
        if (active && !sated) {
            GorgedBruteEntity brute = ensureBrute(hall);
            if (!hall.laid) lay(hall);
            else if (!inHall.isEmpty() && world.getTime() - hall.lastReinforced >= REINFORCE_TICKS) reinforce(hall);
            showBar(hall, brute, inHall);
            if (hall.opening < 0 && gateShut(hall) && someoneInSanctum(hall)) sate(hall, false);
        } else {
            hall.bar.clearPlayers();
        }
        if (hall.opening >= 0) sinkGateRow(hall);
    }

    private static void refresh(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(type);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 40) {
            player.addStatusEffect(new StatusEffectInstance(type, 80, amplifier, true, false, true));
        }
    }

    private static boolean loaded(Hall hall, List<BlockPos> blocks) {
        for (BlockPos pos : blocks) if (!hall.world.isChunkLoaded(pos)) return false;
        return true;
    }

    // ---- The Gorged Brute ----

    public static GorgedBruteEntity brute(Hall hall) {
        return hall.brute != null && hall.world.getEntity(hall.brute) instanceof GorgedBruteEntity brute && brute.isAlive() ? brute : null;
    }

    /** He's on his dais, as hungry as he was, unless he's already there. */
    private static GorgedBruteEntity ensureBrute(Hall hall) {
        GorgedBruteEntity brute = brute(hall);
        if (brute != null) {
            if (brute.squaredDistanceTo(Vec3d.ofBottomCenter(hall.piece.gorged())) > 4) place(hall, brute);
            return brute;
        }
        BlockPos at = hall.piece.gorged();
        // Not until entities can live there, or he'd be lost (and another put in his place)
        if (!hall.world.shouldTickEntity(at)) return null;
        List<GorgedBruteEntity> left = hall.world.getEntitiesByClass(GorgedBruteEntity.class, Box.from(hall.box()), GorgedBruteEntity::isAlive);
        if (!left.isEmpty()) {
            brute = left.get(0);
            for (GorgedBruteEntity extra : left.subList(1, left.size())) extra.discard();
        } else {
            brute = ModEntities.GORGED_BRUTE.create(hall.world);
            if (brute == null) return null;
            brute.setEaten(hall.eaten);
            place(hall, brute);
            hall.world.spawnEntity(brute);
        }
        hall.brute = brute.getUuid();
        return brute;
    }

    private static void place(Hall hall, GorgedBruteEntity brute) {
        BlockPos at = hall.piece.gorged();
        float yaw = hall.piece.gorgedYaw();
        brute.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        brute.setHeadYaw(yaw);
        brute.setBodyYaw(yaw);
        brute.setVelocity(Vec3d.ZERO);
    }

    private static Hall hallOf(GorgedBruteEntity brute) {
        for (Hall hall : HALLS.values()) if (brute.getUuid().equals(hall.brute)) return hall;
        return null;
    }

    /** Another mouthful: everyone in the hall sees how full he's getting, and at the last one he bursts. */
    private static void onFed(GorgedBruteEntity brute, PlayerEntity feeder) {
        Hall hall = hallOf(brute);
        if (hall == null) {
            if (brute.full()) brute.burst();
            return;
        }
        hall.eaten = brute.eaten();
        hall.bar.setPercent(hall.eaten / (float) GorgedBruteEntity.FOOD_TO_BURST);
        if (brute.full()) {
            brute.burst();
            hall.brute = null;
            sate(hall, true);
            return;
        }
        tell(hall, Text.translatable("message.fmscraft.gorged_brute_eats", hall.eaten, GorgedBruteEntity.FOOD_TO_BURST).withColor(GLUTTONY_GOLD), 0);
    }

    private static void showBar(Hall hall, GorgedBruteEntity brute, List<ServerPlayerEntity> inHall) {
        if (brute == null) {
            hall.bar.clearPlayers();
            return;
        }
        hall.bar.setPercent(MathHelper.clamp(brute.eaten() / (float) GorgedBruteEntity.FOOD_TO_BURST, 0, 1));
        for (ServerPlayerEntity player : List.copyOf(hall.bar.getPlayers())) {
            if (!inHall.contains(player)) hall.bar.removePlayer(player);
        }
        for (ServerPlayerEntity player : inHall) hall.bar.addPlayer(player);
    }

    // ---- The feast ----

    /** The feast's piglins and brutes still about in the hall. */
    public static List<AbstractPiglinEntity> feasters(Hall hall) {
        return hall.world.getEntitiesByClass(AbstractPiglinEntity.class, Box.from(hall.box()).expand(8),
                e -> e.isAlive() && e.getCommandTags().contains(FEAST_TAG));
    }

    /** The banquet is laid: piglins at the table and brutes about the hall, as many as are missing. */
    private static void lay(Hall hall) {
        List<AbstractPiglinEntity> feasters = feasters(hall);
        long piglins = feasters.stream().filter(e -> e instanceof PiglinEntity).count();
        long brutes = feasters.size() - piglins;
        List<BlockPos> piglinSpots = hall.piece.spots(GluttonyHallShape.PIGLIN_SPOTS), bruteSpots = hall.piece.spots(GluttonyHallShape.BRUTE_SPOTS);
        for (int i = (int) piglins; i < PIGLINS; i++) {
            if (!feast(hall, EntityType.PIGLIN, piglinSpots.get(i % piglinSpots.size()))) return;
        }
        for (int i = (int) brutes; i < BRUTES; i++) {
            if (!feast(hall, EntityType.PIGLIN_BRUTE, bruteSpots.get(i % bruteSpots.size()))) return;
        }
        hall.laid = true;
        hall.lastReinforced = hall.world.getTime();
    }

    /** One more comes in, somewhere down the aisles nobody is close to, if there are fewer than the feast began with. */
    private static void reinforce(Hall hall) {
        hall.lastReinforced = hall.world.getTime();
        List<AbstractPiglinEntity> feasters = feasters(hall);
        long piglins = feasters.stream().filter(e -> e instanceof PiglinEntity).count();
        long brutes = feasters.size() - piglins;
        EntityType<? extends AbstractPiglinEntity> type;
        if (brutes < BRUTES && (piglins >= PIGLINS || hall.world.getRandom().nextInt(3) == 0)) type = EntityType.PIGLIN_BRUTE;
        else if (piglins < PIGLINS) type = EntityType.PIGLIN;
        else return;
        List<BlockPos> spots = new ArrayList<>(hall.piece.spots(GluttonyHallShape.REINFORCE_SPOTS));
        spots.removeIf(spot -> hall.world.getPlayers().stream()
                .anyMatch(p -> !p.isSpectator() && p.squaredDistanceTo(Vec3d.ofBottomCenter(spot)) < REINFORCE_CLEARANCE * REINFORCE_CLEARANCE));
        if (spots.isEmpty()) return;
        feast(hall, type, spots.get(hall.world.getRandom().nextInt(spots.size())));
    }

    /** A real piglin (or brute), armed the way they are in a bastion, that won't turn into a zombie anywhere. */
    private static boolean feast(Hall hall, EntityType<? extends AbstractPiglinEntity> type, BlockPos at) {
        ServerWorld world = hall.world;
        if (!world.shouldTickEntity(at)) return false;
        AbstractPiglinEntity piglin = type.create(world);
        if (piglin == null) return false;
        float yaw = world.getRandom().nextFloat() * 360f;
        piglin.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        piglin.setHeadYaw(yaw);
        piglin.setBodyYaw(yaw);
        piglin.initialize(world, world.getLocalDifficulty(at), SpawnReason.EVENT, null);
        if (piglin instanceof PiglinEntity p && p.isBaby()) p.setBaby(false);
        if (piglin.getMainHandStack().isEmpty()) {
            piglin.equipStack(EquipmentSlot.MAINHAND, new ItemStack(world.getRandom().nextBoolean() ? Items.CROSSBOW : Items.GOLDEN_SWORD));
        }
        piglin.setImmuneToZombification(true);
        piglin.setPersistent();
        piglin.addCommandTag(FEAST_TAG);
        world.spawnEntityAndPassengers(piglin);
        return true;
    }

    // ---- The chapel ----

    /** Whether {@code target} is a player sheltering in a hall's chapel, where nothing can go for them. */
    public static boolean sheltered(LivingEntity target) {
        if (HALLS.isEmpty() || !(target instanceof PlayerEntity) || !(target.getWorld() instanceof ServerWorld world)) return false;
        BlockPos at = target.getBlockPos();
        for (Hall hall : HALLS.values()) {
            if (hall.world == world && hall.box().contains(at) && hall.piece.inSanctum(at)) return true;
        }
        return false;
    }

    /** Any of the feast that strays into the chapel is turned round and sent back into the hall. */
    private static void shelter(Hall hall) {
        if (!hall.world.isChunkLoaded(hall.piece.lock())) return;
        BlockPos back = hall.piece.beforeGate();
        for (MobEntity mob : hall.world.getEntitiesByClass(MobEntity.class, Box.from(hall.box()), e -> e.isAlive() && e.getCommandTags().contains(FEAST_TAG))) {
            if (!hall.piece.inSanctum(mob.getBlockPos())) continue;
            mob.getBrain().forget(MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().forget(MemoryModuleType.ANGRY_AT);
            mob.setTarget(null);
            Vec3d out = Vec3d.ofBottomCenter(back).subtract(mob.getPos()).multiply(1, 0, 1).normalize().multiply(0.4);
            mob.setVelocity(out.x, mob.getVelocity().y, out.z);
            mob.velocityModified = true;
            mob.getNavigation().startMovingTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5, 1.0);
        }
    }

    // ---- The gate ----

    public static boolean gateShut(Hall hall) {
        if (!loaded(hall, hall.piece.gate())) return false;
        for (BlockPos pos : hall.piece.gate()) if (hall.world.getBlockState(pos).isOf(Blocks.GILDED_BLACKSTONE)) return true;
        return false;
    }

    public static boolean gateOpen(Hall hall) {
        if (!loaded(hall, hall.piece.gate())) return false;
        for (BlockPos pos : hall.piece.gate()) if (!hall.world.getBlockState(pos).isAir()) return false;
        return true;
    }

    /** Someone behind the gate while it's shut, who'd otherwise be stuck in there. */
    private static boolean someoneInSanctum(Hall hall) {
        for (ServerPlayerEntity player : hall.world.getPlayers()) {
            if (!player.isSpectator() && hall.box().contains(player.getBlockPos()) && hall.piece.inSanctum(player.getBlockPos())) return true;
        }
        return false;
    }

    /**
     * The hall gives in: the rules lift, no more of the feast comes, and the gate starts to sink. With {@code earned}
     * (he was fed until he burst, rather than someone being let out) everyone near hears the fanfare.
     */
    private static void sate(Hall hall, boolean earned) {
        hall.sated = true;
        hall.bar.clearPlayers();
        GorgedBruteEntity brute = brute(hall);
        if (brute != null) brute.discard();
        hall.brute = null;
        for (ServerPlayerEntity player : hall.world.getPlayers()) {
            if (!hall.box().expand(ACTIVE_MARGIN).contains(player.getBlockPos())) continue;
            StatusEffectInstance barrier = player.getStatusEffect(ModEffects.BLOCK_BARRIER);
            if (barrier != null && barrier.isAmbient()) player.removeStatusEffect(ModEffects.BLOCK_BARRIER);
            if (earned) player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        hall.opening = 0;
        BlockPos middle = middleOfGate(hall);
        hall.world.playSound(null, middle, SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.BLOCKS, 2.0f, 0.5f);
        if (earned) tell(hall, Text.translatable("message.fmscraft.gorged_brute_bursts").formatted(Formatting.GOLD, Formatting.BOLD), ACTIVE_MARGIN);
    }

    /** The gate sinks into the floor one row at a time, top row first. */
    private static void sinkGateRow(Hall hall) {
        BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GILDED_BLACKSTONE.getDefaultState());
        List<List<BlockPos>> rows = hall.piece.gateRows();
        for (BlockPos pos : rows.get(hall.opening)) {
            if (!hall.world.getBlockState(pos).isOf(Blocks.GILDED_BLACKSTONE)) continue;
            hall.world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            hall.world.spawnParticles(dust, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
        hall.world.playSound(null, middleOfGate(hall), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.2f, 0.5f);
        hall.opening++;
        if (hall.opening >= rows.size()) {
            hall.opening = -1;
            hall.world.playSound(null, middleOfGate(hall), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.5f, 0.4f);
        }
    }

    private static BlockPos middleOfGate(Hall hall) {
        List<BlockPos> gate = hall.piece.gate();
        return gate.get(gate.size() / 2);
    }

    // ---- Resetting ----

    /** Everyone has gone: he goes, the feast is cleared away, and the hall is quiet until someone comes again. */
    private static void leave(Hall hall) {
        GorgedBruteEntity brute = brute(hall);
        if (brute != null) brute.discard();
        for (GorgedBruteEntity stray : hall.world.getEntitiesByClass(GorgedBruteEntity.class, Box.from(hall.box()), e -> true)) stray.discard();
        hall.brute = null;
        for (AbstractPiglinEntity piglin : feasters(hall)) piglin.discard();
        hall.laid = false;
        hall.bar.clearPlayers();
    }

    /** Puts the hall back the way it was built: the gate shut, and him hungry again. Cheap to run often. */
    public static void reset(Hall hall) {
        hall.opening = -1;
        hall.sated = false;
        hall.eaten = 0;
        if (!loaded(hall, hall.piece.gate())) return;
        for (BlockPos pos : hall.piece.gate()) {
            if (hall.world.getBlockState(pos).isAir() && hall.world.getOtherEntities(null, new Box(pos)).isEmpty()) {
                hall.world.setBlockState(pos, Blocks.GILDED_BLACKSTONE.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    /** An action bar message for everyone in the hall, and everyone within {@code margin} of it. */
    private static void tell(Hall hall, Text message, int margin) {
        for (ServerPlayerEntity player : hall.world.getPlayers()) {
            BlockPos at = player.getBlockPos();
            if (hall.piece.inHall(at) || (margin > 0 && hall.box().expand(margin).contains(at))) player.sendMessage(message, true);
        }
    }
}
