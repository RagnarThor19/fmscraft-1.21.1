package net.ragnar.fmscraft.boss.envy;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.fmscraft.compat.MagicSpells;

import java.util.UUID;

/**
 * One of GUMMSS's Mirror Images: exactly like him (his skin, his staff, his aura), keeping its distance and flicking
 * poison darts. It breaks at the first touch, harmlessly. The tell: it casts no shadow (GummssRenderer). They go when
 * their time is up, when he dies, or when the fight ends.
 */
public class GummssIllusionEntity extends HostileEntity implements GummssLook {
    public static final int LIFE = 260;
    private static final TrackedData<Integer> POSE = DataTracker.registerData(GummssIllusionEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID owner;
    private int nextDart = 30, throwTicks;
    private double strafe = 1;

    public GummssIllusionEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        ItemStack staff = MagicSpells.item(MagicSpells.GOLDEN_STAFF);
        equipStack(EquipmentSlot.MAINHAND, staff.isEmpty() ? new ItemStack(Items.BLAZE_ROD) : staff);
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_IDLE);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    public int poseState() {
        return dataTracker.get(POSE);
    }

    void setOwner(GummssEntity boss) {
        owner = boss.getUuid();
        nextDart = 20 + random.nextInt(30);
    }

    private GummssEntity owner(ServerWorld world) {
        return owner != null && world.getEntity(owner) instanceof GummssEntity boss && boss.isAlive() ? boss : null;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        GummssEntity boss = owner(world);
        if (boss == null || age > LIFE) {
            shatter(false);
            return;
        }
        if (throwTicks > 0 && --throwTicks == 0) dataTracker.set(POSE, POSE_IDLE);
        ServerPlayerEntity target = null;
        for (ServerPlayerEntity p : boss.fighters()) if (target == null || p.squaredDistanceTo(this) < target.squaredDistanceTo(this)) target = p;
        if (target == null) return;
        getLookControl().lookAt(target, 30f, 30f);
        if (age % 50 == 0) strafe = random.nextBoolean() ? 1 : -1;
        if (age % 8 == 0 || getNavigation().isIdle()) {
            // Keeps its distance, circling, the way he does
            Vec3d away = getPos().subtract(target.getPos()).multiply(1, 0, 1);
            double d = away.length();
            away = d > 1e-3 ? away.multiply(1 / d) : new Vec3d(1, 0, 0);
            Vec3d side = new Vec3d(-away.z, 0, away.x).multiply(strafe);
            Vec3d dest = getPos().add(side.multiply(4)).add(away.multiply((10 - d) * 0.5));
            getNavigation().startMovingTo(dest.x, dest.y, dest.z, 1.0);
        }
        if (--nextDart <= 0) {
            nextDart = 45 + random.nextInt(30);
            dataTracker.set(POSE, POSE_THROW);
            throwTicks = 8;
            swingHand(Hand.OFF_HAND);
            Vec3d hand = getPos().add(0, getHeight() * 0.72, 0).add(Vec3d.fromPolar(0, getBodyYaw()).multiply(0.6));
            GummssEntity.throwDart(world, boss, hand, target, 0, 1.0, 50, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.2f, 1.3f);
        }
        // His aura, so you can't tell by that
        double w = getWidth() * 0.6, h = getHeight();
        world.spawnParticles(EnvyHazards.DEEP_GREEN, getX(), getY() + h * 0.5, getZ(), 2, w, h * 0.35, w, 0);
        double a = age * 0.25;
        world.spawnParticles(EnvyHazards.DEEP_GREEN, getX() + Math.cos(a) * 0.75, getY() + (age % 30) / 30.0 * h, getZ() + Math.sin(a) * 0.75, 1, 0, 0, 0, 0);
    }

    /** Breaks like glass into green motes; {@code struck}: someone hit it. */
    void shatter(boolean struck) {
        if (getWorld() instanceof ServerWorld world && !isRemoved()) {
            world.spawnParticles(EnvyHazards.DEEP_GREEN, getX(), getY() + 1, getZ(), 40, 0.3, 0.8, 0.3, 0);
            world.spawnParticles(EnvyHazards.PSYCHIC, getX(), getY() + 1, getZ(), 20, 0.3, 0.8, 0.3, 0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.2f, struck ? 0.8f : 1.2f);
            if (struck) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.2f, 1.6f);
        }
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            shatter(false);
            return true;
        }
        if (source.getAttacker() instanceof PlayerEntity) {
            shatter(true);
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
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        // Never one without him (one left over from a crash, say)
        if (!getWorld().isClient && age % 20 == 0 && owner == null) discard();
        if (getWorld().isClient && random.nextInt(4) == 0) {
            getWorld().addParticle(ParticleTypes.WARPED_SPORE, getX(), getY() + 1.2, getZ(), 0, 0, 0);
        }
    }
}
