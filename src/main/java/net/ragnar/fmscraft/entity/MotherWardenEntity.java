package net.ragnar.fmscraft.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.WardenBrain;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.ragnar.fmscraft.sound.ModSounds;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Mother Warden: a warden six times the size of any other, blood red where they're blue, with a faint red haze
 * always rising off her. She is a real warden in every way once she's up (the same brain, roar, sniffing, darkness and
 * sonic boom), only bigger, deeper-voiced and much harder to bring down. Her sonic boom carries
 * {@link #BOOM_REACH} times as far as a warden's and hits {@link #BOOM_DAMAGE} times as hard (LivingEntityMixin); like
 * any warden's, it's for whoever she can't get her hands on (each blow she lands puts it off). And she never digs back
 * down into the ground: she lives here.
 *
 * She spends her life asleep in her nest in the Den of Wrath, which {@code WrathDens} runs: it decides when she wakes
 * (three noises, or anything at all that hurts her) and when she settles back down. Asleep she doesn't think at all
 * (no AI), slumps over the way anything does under the magic mod's sleep darts, with Z's drifting up off her (drawn by
 * the client, see SleepZs), and snores, loud and deep, every few seconds. Going back to sleep she nods off for two
 * seconds first. She is never saved: she's put back in her nest, asleep, on every visit.
 */
public class MotherWardenEntity extends WardenEntity {
    public static final float SCALE = 6.0f;
    /** How much further her sonic boom (and her sniffing) reaches than a warden's. */
    public static final double BOOM_REACH = 2.5;
    /** How much harder her sonic boom hits. */
    public static final float BOOM_DAMAGE = 1.5f;
    public static final int AWAKE = 0, DROWSY = 1, ASLEEP = 2;
    public static final int DROWSY_TICKS = 40;
    /** Ticks between snores. */
    public static final int SNORE_TICKS = 120;
    /** How far her head hangs while she sleeps, in degrees of pitch. */
    private static final float HEAD_DROOP = 40f;
    public static final DustParticleEffect RED_HAZE = new DustParticleEffect(new Vector3f(0.72f, 0.07f, 0.05f), 1.6f);
    private static final TrackedData<Integer> SLEEP = DataTracker.registerData(MotherWardenEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Called (on the server) whenever she's hurt while asleep, before the damage is dealt, to wake her. */
    public static BiConsumer<MotherWardenEntity, DamageSource> onDisturbed = (mother, source) -> mother.wakeUp();

    /** Ticks in her current sleep phase. Counted on both sides (the client uses it for the slump and the Z's). */
    private int phaseAge;
    /** No snoring before this (she's listening). */
    private long quietUntil;

    public MotherWardenEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        // Her scale is 6 from the start, so it never changes, and nothing else would resize her box for it
        calculateDimensions();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return WardenEntity.addAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1000.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 45.0)
                .add(EntityAttributes.GENERIC_ARMOR, 10.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_SCALE, SCALE)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.5);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SLEEP, AWAKE);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if (SLEEP.equals(data)) phaseAge = 0;
        super.onTrackedDataSet(data);
    }

    // ---- Sleep ----

    public int sleepPhase() {
        return dataTracker.get(SLEEP);
    }

    /** Drowsy or asleep: either way, not thinking. */
    public boolean isDozing() {
        return sleepPhase() != AWAKE;
    }

    public int phaseAge() {
        return phaseAge;
    }

    /** Straight to sleep, without nodding off first (for when she's put in her nest). */
    public void putToSleep() {
        setPhase(ASLEEP);
    }

    /** She nods off where she stands, and is asleep two seconds later. */
    public void nodOff() {
        if (isDozing()) return;
        setPhase(DROWSY);
        playSound(SoundEvents.ENTITY_WARDEN_SNIFF, 4.0f, getSoundPitch());
    }

    public void wakeUp() {
        if (!isDozing()) return;
        setPhase(AWAKE);
        setPitch(0);
    }

    private void setPhase(int phase) {
        dataTracker.set(SLEEP, phase);
        phaseAge = 0;
        setAiDisabled(phase != AWAKE);
        if (phase != AWAKE && !getWorld().isClient) {
            // She forgets everything she was after: nobody is still a suspect when she wakes again
            getNavigation().stop();
            for (MemoryModuleType<?> memory : List.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ROAR_TARGET,
                    MemoryModuleType.DISTURBANCE_LOCATION, MemoryModuleType.WALK_TARGET, MemoryModuleType.LOOK_TARGET)) {
                getBrain().forget(memory);
            }
            for (PlayerEntity player : getWorld().getPlayers()) removeSuspect(player);
            setVelocity(0, Math.min(0, getVelocity().y), 0);
        }
    }

    /** She lies listening for a while: no snoring until she settles again. */
    public void listen(int ticks) {
        quietUntil = getWorld().getTime() + ticks;
    }

    @Override
    public void tick() {
        super.tick();
        phaseAge++;
        World world = getWorld();
        if (world.isClient) {
            // A faint red haze always rising off her, thicker while she's up
            if (random.nextInt(isDozing() ? 3 : 1) == 0) {
                double x = getX() + (random.nextDouble() - 0.5) * getWidth();
                double y = getY() + random.nextDouble() * getHeight() * 0.9;
                double z = getZ() + (random.nextDouble() - 0.5) * getWidth();
                world.addParticle(RED_HAZE, x, y, z, 0, 0.02, 0);
            }
            return;
        }
        if (sleepPhase() == DROWSY && phaseAge >= DROWSY_TICKS) setPhase(ASLEEP);
        if (sleepPhase() == ASLEEP) {
            setPitch(MathHelper.lerp(0.25f, getPitch(), HEAD_DROOP));
            if (world.getTime() >= quietUntil && phaseAge % SNORE_TICKS == SNORE_TICKS / 2) snore((ServerWorld) world);
        }
    }

    @Override
    protected void mobTick() {
        // A warden left with nothing to do digs back into the ground and is gone; she never does. (A warden summoned
        // by a shrieker starts with this, but she isn't summoned, and without it she'd dig, or stand stuck trying to.)
        getBrain().remember(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, WardenBrain.DIG_COOLDOWN);
        super.mobTick();
    }

    /** A long, deep snore that carries right across the hall, and the rumble of her breath under it. */
    private void snore(ServerWorld world) {
        double y = getY() + getHeight() * 0.8;
        world.playSound(null, getX(), y, getZ(), ModSounds.MOTHER_WARDEN_SNORE, SoundCategory.HOSTILE, 4.0f, 0.68f + random.nextFloat() * 0.08f);
        world.playSound(null, getX(), y, getZ(), SoundEvents.ENTITY_SNIFFER_IDLE, SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Anything that hurts her wakes her first, so she knows who did it (an asleep warden ignores the attacker)
        if (!getWorld().isClient && isDozing() && !isInvulnerableTo(source)) onDisturbed.accept(this, source);
        return super.damage(source, amount);
    }

    // ---- A bigger warden ----

    /**
     * Nobody she couldn't actually hurt (a player who can't take damage, or anyone at all on Peaceful): a warden would
     * roar at them forever, over and over, without ever going for them.
     */
    @Override
    public boolean isValidTarget(@Nullable Entity entity) {
        return super.isValidTarget(entity) && canTarget((net.minecraft.entity.LivingEntity) entity);
    }

    /** Every range a warden's brain measures out (the sonic boom's, the sniff's) is that much longer for her. */
    @Override
    public boolean isInRange(Entity entity, double horizontalRadius, double verticalRadius) {
        return super.isInRange(entity, horizontalRadius * BOOM_REACH, verticalRadius * BOOM_REACH);
    }

    @Override
    public float getSoundPitch() {
        return 0.55f + (random.nextFloat() - 0.5f) * 0.08f;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isDozing() ? null : super.getAmbientSound();
    }

    @Override
    public boolean isPushable() {
        return !isDozing() && super.isPushable();
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    /** Never saved: put back in her nest, asleep, on every visit. */
    @Override
    public boolean shouldSave() {
        return false;
    }
}
