package net.ragnar.fmscraft.structure.wrath;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.VibrationParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.GameEventTags;
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
import net.minecraft.world.event.EntityPositionSource;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.structure.Structure;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.effect.ModEffects;
import net.ragnar.fmscraft.entity.ModEntities;
import net.ragnar.fmscraft.entity.MotherWardenEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What happens in and around a Den of Wrath:
 * <ul>
 *   <li><b>The Mother Warden</b> ({@link MotherWardenEntity}) is put in her nest, asleep, once someone comes within
 *       {@link #ACTIVE_MARGIN} blocks of the den, and taken away again when everyone has gone.</li>
 *   <li><b>The hall's rules</b>: anyone in the hall itself (not the tunnels or the portal room) has Mining Fatigue X,
 *       Block Barrier (no placing or breaking blocks) and Item Barrier (nothing but food can be used, so no pearls or
 *       rockets across the hall).</li>
 *   <li><b>Noise</b>: anything a warden could hear, anywhere in the hall, stirs her: the same rules as a sculk
 *       sensor, so sneaking is silent and wool muffles, but chests, pressure plates, tripwires, shriekers, a fall or a
 *       hurt all count. Creative players and stray mobs don't. A vibration flies from the noise to her, her tendrils
 *       twitch, and everyone in the hall is told she stirs (n/3). {@link Stirring} counts them; the third wakes her.</li>
 *   <li><b>Waking</b>: three stirs, or anything that hurts her. She goes for whoever woke her (or whoever is nearest), and
 *       a boss bar shows her health to everyone in the hall. She only hunts in her hall: anyone who gets out of it is
 *       forgotten.</li>
 *   <li><b>Settling</b>: once there's nobody left in the hall for her to hunt, she walks back to her nest after
 *       {@link #CALM_TICKS} and goes back to sleep, every stir forgotten.</li>
 * </ul>
 * Nothing is saved: she's never saved, and the stirs start from nothing after a restart.
 */
public final class WrathDens {
    public static final int ACTIVE_MARGIN = 24;
    public static final int RESET_MARGIN = 64;
    /** Mining Fatigue X (amplifiers are one less). */
    public static final int FATIGUE = 9;
    /** How long she waits, with nobody left to hunt, before heading home to sleep. */
    public static final int CALM_TICKS = 15 * 20;
    /** How long she may take to walk home before she's simply back in her nest. */
    private static final int HOMEWARD_TICKS = 30 * 20;
    private static final int UPDATE_TICKS = 10;
    private static final int SCAN_TICKS = 20;
    private static final int SCAN_CHUNKS = 7;
    public static final RegistryKey<Structure> DEN = RegistryKey.of(RegistryKeys.STRUCTURE, Identifier.of(FmsCraft.MOD_ID, "den_of_wrath"));
    private static final int WRATH_RED = 0xC8463A;

    /** A den someone is near. */
    public static final class Den {
        final ServerWorld world;
        final WrathDenPiece piece;
        final Stirring stirring = new Stirring();
        final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.fmscraft.mother_warden"), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        UUID mother;
        /** When she last had someone to hunt (or woke), and when she set off home; -1 when she isn't going. */
        long lastHunted, homeward = -1;
        boolean wasNearby;
        /** She was killed this visit: she stays dead until everyone has gone. */
        boolean killed;

        Den(ServerWorld world, WrathDenPiece piece) {
            this.world = world;
            this.piece = piece;
        }

        public WrathDenPiece piece() {
            return piece;
        }

        public Stirring stirring() {
            return stirring;
        }

        public BlockBox box() {
            return piece.getBoundingBox();
        }

        public boolean showsBar(ServerPlayerEntity player) {
            return bar.getPlayers().contains(player);
        }
    }

    private record Key(RegistryKey<World> world, long center) {
    }

    private static final Map<Key, Den> DENS = new HashMap<>();

    private WrathDens() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(WrathDens::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (Den den : DENS.values()) den.bar.clearPlayers();
            DENS.clear();
        });
        MotherWardenEntity.onDisturbed = WrathDens::onHurt;
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof MotherWardenEntity)) return;
            for (Den den : DENS.values()) {
                if (entity.getUuid().equals(den.mother)) {
                    den.killed = true;
                    den.bar.clearPlayers();
                }
            }
        });
    }

    // ---- Finding dens ----

    private static Structure structure(ServerWorld world) {
        return world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(DEN);
    }

    /** The den whose structure covers {@code pos}, or null. */
    public static WrathDenPiece pieceAt(ServerWorld world, BlockPos pos) {
        Structure structure = structure(world);
        if (structure == null) return null;
        StructureStart start = world.getStructureAccessor().getStructureContaining(pos, structure);
        return start.hasChildren() && start.getChildren().get(0) instanceof WrathDenPiece piece ? piece : null;
    }

    public static Den den(ServerWorld world, WrathDenPiece piece) {
        return DENS.computeIfAbsent(new Key(world.getRegistryKey(), piece.center().asLong()), k -> new Den(world, piece));
    }

    private static void scan(MinecraftServer server) {
        Map<Key, Den> found = new HashMap<>();
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
                        if (!start.hasChildren() || !(start.getChildren().get(0) instanceof WrathDenPiece piece)) continue;
                        Key key = new Key(world.getRegistryKey(), piece.center().asLong());
                        found.computeIfAbsent(key, k -> DENS.containsKey(k) ? DENS.get(k) : new Den(world, piece));
                    }
                }
            }
        }
        // Dens nobody is near any more are let go; anything left of them is cleared up first
        for (Map.Entry<Key, Den> entry : DENS.entrySet()) {
            if (!found.containsKey(entry.getKey())) leave(entry.getValue());
        }
        DENS.clear();
        DENS.putAll(found);
    }

    // ---- Ticking ----

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % SCAN_TICKS == 0) scan(server);
        if (server.getTicks() % UPDATE_TICKS != 0) return;
        for (Den den : List.copyOf(DENS.values())) update(den);
    }

    /** Every half second, for each den someone is near. */
    public static void update(Den den) {
        ServerWorld world = den.world;
        BlockBox box = den.box();
        long now = world.getTime();
        boolean active = false, nearby = false;
        List<ServerPlayerEntity> inHall = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            BlockPos at = player.getBlockPos();
            if (box.expand(RESET_MARGIN).contains(at)) nearby = true;
            if (box.expand(ACTIVE_MARGIN).contains(at)) active = true;
            if (!den.piece.inHall(at) || player.isCreative()) continue;
            inHall.add(player);
            refresh(player, StatusEffects.MINING_FATIGUE, FATIGUE);
            refresh(player, ModEffects.BLOCK_BARRIER, 0);
            refresh(player, ModEffects.ITEM_BARRIER, 0);
        }
        if (!nearby) {
            if (den.wasNearby) leave(den);
            den.wasNearby = false;
            return;
        }
        den.wasNearby = true;
        MotherWardenEntity mother = active ? ensureMother(den) : null;
        if (mother == null || mother.isDozing()) {
            // No bar unless she's up and about
            den.bar.clearPlayers();
            if (mother != null) den.stirring.tick(now);
            return;
        }
        hunt(den, mother, inHall, now);
    }

    private static void refresh(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, int amplifier) {
        StatusEffectInstance current = player.getStatusEffect(type);
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 40) {
            player.addStatusEffect(new StatusEffectInstance(type, 80, amplifier, true, false, true));
        }
    }

    // ---- The Mother Warden ----

    public static MotherWardenEntity mother(Den den) {
        return den.mother != null && den.world.getEntity(den.mother) instanceof MotherWardenEntity mother && mother.isAlive() ? mother : null;
    }

    /**
     * She's in her nest, asleep, unless she's already about (or lying dead: then the hall is quiet until everyone goes).
     * If she's gone any other way (the game took her), she's back in her nest.
     */
    private static MotherWardenEntity ensureMother(Den den) {
        MotherWardenEntity mother = mother(den);
        if (mother != null) return mother;
        if (den.mother != null && den.world.getEntity(den.mother) != null) return null; // dying
        BlockPos nest = den.piece.nest();
        if (!den.world.isChunkLoaded(nest)) return null;
        List<MotherWardenEntity> left = den.world.getEntitiesByClass(MotherWardenEntity.class, Box.from(den.box()), MotherWardenEntity::isAlive);
        if (!left.isEmpty()) {
            mother = left.get(0);
            for (MotherWardenEntity extra : left.subList(1, left.size())) extra.discard();
        } else {
            if (den.killed) return null; // killed this visit: she stays dead until everyone leaves
            mother = ModEntities.MOTHER_WARDEN.create(den.world);
            if (mother == null) return null;
            float yaw = den.piece.nestYaw();
            mother.refreshPositionAndAngles(nest.getX() + 0.5, nest.getY(), nest.getZ() + 0.5, yaw, 0);
            mother.setHeadYaw(yaw);
            mother.setBodyYaw(yaw);
            mother.putToSleep();
            den.world.spawnEntity(mother);
            den.stirring.reset();
        }
        den.mother = mother.getUuid();
        return mother;
    }

    /** Awake: she hunts whoever is still in her hall, forgets anyone who got out, and goes home when it's empty. */
    private static void hunt(Den den, MotherWardenEntity mother, List<ServerPlayerEntity> inHall, long now) {
        den.bar.setPercent(MathHelper.clamp(mother.getHealth() / mother.getMaxHealth(), 0, 1));
        for (ServerPlayerEntity player : List.copyOf(den.bar.getPlayers())) {
            if (!inHall.contains(player)) den.bar.removePlayer(player);
        }
        for (ServerPlayerEntity player : inHall) den.bar.addPlayer(player);

        LivingEntity target = mother.getTarget();
        if (target != null && !(target instanceof PlayerEntity p && inHall.contains(p))) {
            mother.getBrain().forget(MemoryModuleType.ATTACK_TARGET);
            mother.removeSuspect(target);
        }
        for (ServerPlayerEntity player : den.world.getPlayers()) {
            if (!inHall.contains(player)) mother.removeSuspect(player);
        }
        if (!inHall.isEmpty()) {
            den.lastHunted = now;
            den.homeward = -1;
            if (!mother.getAngriness().isAngry()) {
                // Her rage never cools while there's anyone left in her hall: she goes for the nearest of them
                inHall.stream().filter(mother::isValidTarget)
                        .min((a, b) -> Double.compare(a.squaredDistanceTo(mother), b.squaredDistanceTo(mother)))
                        .ifPresent(nearest -> mother.increaseAngerAt(nearest, 80, false));
            }
            return;
        }
        if (now - den.lastHunted < CALM_TICKS) return;
        BlockPos nest = den.piece.nest();
        if (den.homeward < 0) den.homeward = now;
        double home = mother.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(nest));
        if (home > 9 && now - den.homeward < HOMEWARD_TICKS) {
            mother.getNavigation().startMovingTo(nest.getX() + 0.5, nest.getY(), nest.getZ() + 0.5, 0.6);
            return;
        }
        settle(den, mother);
    }

    /** Back in her nest, she curls up and nods off, every stir forgotten. */
    public static void settle(Den den, MotherWardenEntity mother) {
        BlockPos nest = den.piece.nest();
        float yaw = den.piece.nestYaw();
        mother.getNavigation().stop();
        mother.refreshPositionAndAngles(nest.getX() + 0.5, nest.getY(), nest.getZ() + 0.5, yaw, 0);
        mother.setHeadYaw(yaw);
        mother.setBodyYaw(yaw);
        mother.nodOff();
        den.stirring.reset();
        den.homeward = -1;
        den.bar.clearPlayers();
        tell(den, Text.translatable("message.fmscraft.mother_warden_sleeps").formatted(Formatting.GRAY), ACTIVE_MARGIN);
    }

    /** Everyone has gone: she goes too (she'll be in her nest, asleep, next time), and the stirs are forgotten. */
    private static void leave(Den den) {
        MotherWardenEntity mother = mother(den);
        if (mother != null) mother.discard();
        for (MotherWardenEntity stray : den.world.getEntitiesByClass(MotherWardenEntity.class, Box.from(den.box()), e -> true)) stray.discard();
        den.mother = null;
        den.killed = false;
        den.stirring.reset();
        den.homeward = -1;
        den.bar.clearPlayers();
    }

    // ---- Noise ----

    /** Called for every game event in a server world (ServerWorldMixin). Cheap when there's no den about. */
    public static void onGameEvent(ServerWorld world, RegistryEntry<GameEvent> event, Vec3d pos, GameEvent.Emitter emitter) {
        if (DENS.isEmpty() || !event.isIn(GameEventTags.WARDEN_CAN_LISTEN)) return;
        BlockPos at = BlockPos.ofFloored(pos);
        for (Den den : DENS.values()) {
            if (den.world != world || !den.box().contains(at) || !den.piece.inHall(at)) continue;
            if (!audible(event, emitter)) return;
            hear(den, pos, culprit(emitter.sourceEntity()));
            return;
        }
    }

    /**
     * Whether she'd hear this: the vanilla rules for what a sculk sensor picks up (sneaking muffles footsteps and
     * landings, wool muffles everything), except that only players (and what they throw or shoot) count. Creative and
     * spectating players don't, nor do mobs that wander in.
     */
    public static boolean audible(RegistryEntry<GameEvent> event, GameEvent.Emitter emitter) {
        Entity source = emitter.sourceEntity();
        if (source != null) {
            if (source instanceof MotherWardenEntity) return false;
            Entity culprit = culprit(source);
            if (culprit instanceof PlayerEntity player) {
                if (player.isSpectator() || player.isCreative()) return false;
            } else if (culprit instanceof LivingEntity) {
                return false;
            }
            if (source.bypassesSteppingEffects() && event.isIn(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) return false;
            if (source.occludeVibrationSignals()) return false;
        }
        return emitter.affectedState() == null || !emitter.affectedState().isIn(BlockTags.DAMPENS_VIBRATIONS);
    }

    /** Who's to blame for something: the one who threw or shot it, if it's a projectile. */
    private static Entity culprit(Entity source) {
        if (source instanceof ProjectileEntity projectile && projectile.getOwner() != null) return projectile.getOwner();
        return source;
    }

    /** A noise in her hall. */
    public static void hear(Den den, Vec3d from, Entity culprit) {
        MotherWardenEntity mother = mother(den);
        if (mother == null || !mother.isDozing()) return;
        if (!den.stirring.hear(den.world.getTime())) return;
        ServerWorld world = den.world;
        int stirs = den.stirring.stirs();
        // The noise flies to her like a vibration to a sculk sensor, and her tendrils twitch
        int travel = MathHelper.floor(from.distanceTo(mother.getEyePos()));
        world.spawnParticles(new VibrationParticleEffect(new EntityPositionSource(mother, mother.getStandingEyeHeight()), travel),
                from.x, from.y, from.z, 1, 0, 0, 0, 0);
        world.sendEntityStatus(mother, net.minecraft.entity.EntityStatuses.EARS_TWITCH);
        mother.playSound(SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, 5.0f, mother.getSoundPitch());
        mother.listen(Stirring.LISTEN_TICKS);
        if (den.stirring.awake()) {
            wake(den, mother, culprit);
            return;
        }
        mother.playSound(stirs >= 2 ? SoundEvents.ENTITY_WARDEN_LISTENING_ANGRY : SoundEvents.ENTITY_WARDEN_LISTENING, 6.0f, mother.getSoundPitch());
        tell(den, Text.translatable("message.fmscraft.mother_warden_stirs", stirs, Stirring.WAKE_AT).withColor(WRATH_RED), 0);
    }

    /** Something hurt her in her sleep. */
    private static void onHurt(MotherWardenEntity mother, DamageSource source) {
        for (Den den : DENS.values()) {
            if (mother.getUuid().equals(den.mother)) {
                wake(den, mother, source.getAttacker());
                return;
            }
        }
        mother.wakeUp();
    }

    /** She wakes, and roars, and comes for whoever woke her (or whoever is nearest, if it wasn't anyone). */
    public static void wake(Den den, MotherWardenEntity mother, Entity culprit) {
        if (!mother.isDozing()) return;
        mother.wakeUp();
        den.lastHunted = den.world.getTime();
        den.homeward = -1;
        mother.playSound(SoundEvents.ENTITY_WARDEN_AGITATED, 8.0f, mother.getSoundPitch());
        if (culprit instanceof PlayerEntity player && mother.isValidTarget(player)) {
            mother.increaseAngerAt(player, 150, false);
        } else {
            den.world.getPlayers().stream()
                    .filter(p -> den.piece.inHall(p.getBlockPos()) && mother.isValidTarget(p))
                    .min((a, b) -> Double.compare(a.squaredDistanceTo(mother), b.squaredDistanceTo(mother)))
                    .ifPresent(p -> mother.increaseAngerAt(p, 150, false));
        }
        tell(den, Text.translatable("message.fmscraft.mother_warden_wakes").formatted(Formatting.DARK_RED, Formatting.BOLD), ACTIVE_MARGIN);
    }

    /** An action bar message for everyone in the hall, and everyone within {@code margin} of the den. */
    private static void tell(Den den, Text message, int margin) {
        for (ServerPlayerEntity player : den.world.getPlayers()) {
            BlockPos at = player.getBlockPos();
            if (den.piece.inHall(at) || (margin > 0 && den.box().expand(margin).contains(at))) player.sendMessage(message, true);
        }
    }
}
