package net.ragnar.fmscraft.boss.lust;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * One of MUGEN's shadow clones: exactly like him (his skin, his blades, his eyes, his scarf), fast, cutting at whoever's
 * nearest and flicking kunai. It goes up in smoke at the first touch, harmlessly. The tell: it casts no shadow
 * (MugenRenderer). They go when their time is up, when he dies, or when the fight ends.
 * <p>
 * An <b>echo</b> is what his Taunt leaves behind: a copy of him standing perfectly still where he just was, staring at
 * you, until it crumbles into smoke a moment later.
 */
public class MugenCloneEntity extends HostileEntity implements MugenLook {
    public static final int LIFE = 240, LIFE_TWO = 300, ECHO_LIFE = 34;
    private static final TrackedData<Integer> POSE = DataTracker.registerData(MugenCloneEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EYES = DataTracker.registerData(MugenCloneEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID owner;
    private boolean echo;
    private int life = LIFE, nextKunai = 40, swingTicks, meleeCooldown;

    public MugenCloneEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.NETHERITE_SWORD));
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.34)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_IDLE);
        builder.add(EYES, EYES_VIOLET);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    public int poseState() {
        return dataTracker.get(POSE);
    }

    @Override
    public int eyes() {
        return dataTracker.get(EYES);
    }

    @Override
    public boolean afterimages() {
        return false;
    }

    @Override
    public boolean isReal() {
        return false;
    }

    public boolean isEcho() {
        return echo;
    }

    void setOwner(MugenEntity boss, boolean phaseTwo) {
        owner = boss.getUuid();
        life = phaseTwo ? LIFE_TWO : LIFE;
        nextKunai = 30 + random.nextInt(40);
        dataTracker.set(EYES, boss.eyes());
    }

    /** Makes this an echo: frozen in {@code pose}, staring, for {@link #ECHO_LIFE} ticks. */
    void makeEcho(MugenEntity boss, int pose) {
        setOwner(boss, true);
        echo = true;
        life = ECHO_LIFE;
        setNoGravity(true);
        setAiDisabled(true);
        dataTracker.set(POSE, pose);
    }

    private MugenEntity owner(ServerWorld world) {
        return owner != null && world.getEntity(owner) instanceof MugenEntity boss && boss.isAlive() ? boss : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world) {
            // Never one without him (one left over from a crash, say)
            if (age % 20 == 0 && owner == null) discard();
            if (echo) tickEcho(world);
        } else if (random.nextInt(3) == 0) {
            getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
        }
    }

    private void tickEcho(ServerWorld world) {
        MugenEntity boss = owner(world);
        if (boss == null || age > life) {
            vanish(false);
            return;
        }
        ServerPlayerEntity near = null;
        for (ServerPlayerEntity p : boss.fighters()) if (near == null || p.squaredDistanceTo(this) < near.squaredDistanceTo(this)) near = p;
        if (near != null) {
            float yaw = (float) (MathHelper.atan2(near.getZ() - getZ(), near.getX() - getX()) * (180 / Math.PI)) - 90f;
            setYaw(yaw);
            setBodyYaw(yaw);
            setHeadYaw(yaw);
        }
        if (age % 3 == 0) world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 1, getZ(), 2, 0.3, 0.7, 0.3, 0);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (echo || !(getWorld() instanceof ServerWorld world)) return;
        MugenEntity boss = owner(world);
        if (boss == null || age > life) {
            vanish(false);
            return;
        }
        if (swingTicks > 0 && --swingTicks == 0) dataTracker.set(POSE, POSE_IDLE);
        if (meleeCooldown > 0) meleeCooldown--;
        ServerPlayerEntity target = null;
        for (ServerPlayerEntity p : boss.fighters()) if (target == null || p.squaredDistanceTo(this) < target.squaredDistanceTo(this)) target = p;
        if (target == null) return;
        getLookControl().lookAt(target, 40f, 40f);
        if (age % 6 == 0 || getNavigation().isIdle()) getNavigation().startMovingTo(target, 1.0);
        if (meleeCooldown <= 0 && squaredDistanceTo(target) < 2.6 * 2.6) {
            boolean left = random.nextBoolean();
            dataTracker.set(POSE, left ? POSE_SLASH_B : POSE_SLASH_A);
            swingTicks = 6;
            swingHand(left ? Hand.OFF_HAND : Hand.MAIN_HAND);
            if (boss.cut(target, 4)) boss.launch(target, target.getPos().subtract(getPos()), 0.5, 0.25);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.8f, 1.4f);
            meleeCooldown = 22;
        }
        if (--nextKunai <= 0 && squaredDistanceTo(target) > 5 * 5) {
            nextKunai = 50 + random.nextInt(40);
            dataTracker.set(POSE, POSE_THROW);
            swingTicks = 8;
            swingHand(Hand.MAIN_HAND);
            Vec3d hand = getPos().add(0, getHeight() * 0.75, 0);
            Vec3d dir = target.getBoundingBox().getCenter().subtract(hand).normalize();
            boss.addHazard(new MugenHazards.Thrown(world, MugenHazards.Kind.KUNAI, hand, dir.multiply(1.5), 5, 32));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 0.6f, 1.8f);
        }
        if (age % 2 == 0) world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 1, getZ(), 1, 0.25, 0.6, 0.25, 0);
    }

    /** Goes up in smoke; {@code struck}: someone hit it. */
    void vanish(boolean struck) {
        if (getWorld() instanceof ServerWorld world && !isRemoved()) {
            Vec3d c = getPos().add(0, 1, 0);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, struck ? 30 : 14, 0.35, 0.7, 0.35, 0.04);
            world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, struck ? 20 : 8, 0.35, 0.7, 0.35, 0.04);
            world.spawnParticles(MugenHazards.SHADOW, c.x, c.y, c.z, 16, 0.3, 0.7, 0.3, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, struck ? 1.0f : 0.5f, 1.6f);
            if (struck) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.6f, 1.8f);
        }
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            vanish(false);
            return true;
        }
        if (source.getAttacker() instanceof PlayerEntity) {
            vanish(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        return false;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !echo;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }
}
