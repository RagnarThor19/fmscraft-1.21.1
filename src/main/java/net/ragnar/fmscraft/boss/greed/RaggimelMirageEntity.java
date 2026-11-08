package net.ragnar.fmscraft.boss.greed;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * One of the three false RAGGIMELs of his Deluge: exactly like him (his skin, his staff, his size, his burning eyes, his
 * pose, his aura), holding up an orb of its own. Nothing hurts it: a blow just splashes off. He moves it about himself
 * (RaggimelEntity.tickDeluge); it goes back into the water when the throw is done, when he's gone, or when the fight is.
 */
public class RaggimelMirageEntity extends HostileEntity implements RaggimelLook {
    private static final TrackedData<Integer> POSE = DataTracker.registerData(RaggimelMirageEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> EYES = DataTracker.registerData(RaggimelMirageEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private RaggimelEntity owner;
    private int raiseTicks;

    public RaggimelMirageEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, RaggimelEntity.MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ARMOR, 10)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, RaggimelEntity.POSE_IDLE);
        builder.add(EYES, false);
    }

    @Override
    protected void initGoals() {
    }

    @Override
    public int poseState() {
        return dataTracker.get(POSE);
    }

    @Override
    public float raiseTime(float tickDelta) {
        return raiseTicks <= 0 ? 0 : raiseTicks + tickDelta;
    }

    @Override
    public boolean hasGlowingEyes() {
        return dataTracker.get(EYES);
    }

    void setOwner(RaggimelEntity boss) {
        owner = boss;
        equipStack(EquipmentSlot.MAINHAND, boss.getMainHandStack().copy());
    }

    void setLook(int pose, boolean eyes) {
        if (poseState() != pose) dataTracker.set(POSE, pose);
        if (hasGlowingEyes() != eyes) dataTracker.set(EYES, eyes);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            raiseTicks = poseState() == RaggimelEntity.POSE_RAISE ? raiseTicks + 1 : 0;
        } else if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            // Never one without him
            vanish();
        }
    }

    /** Back into the water it came out of. */
    void vanish() {
        if (getWorld() instanceof ServerWorld world && !isRemoved()) {
            Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
            world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 150, getWidth() * 0.6, getHeight() * 0.4, getWidth() * 0.6, 0.4);
            world.spawnParticles(ParticleTypes.FALLING_WATER, c.x, c.y, c.z, 60, getWidth() * 0.5, getHeight() * 0.4, getWidth() * 0.5, 0);
            world.spawnParticles(ParticleTypes.POOF, c.x, c.y, c.z, 30, getWidth() * 0.5, getHeight() * 0.4, getWidth() * 0.5, 0.05);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            discard();
            return true;
        }
        if (source.getAttacker() instanceof PlayerEntity && getWorld() instanceof ServerWorld world) {
            // It's only water: the blow splashes straight through
            Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
            world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 20, getWidth() * 0.4, getHeight() * 0.3, getWidth() * 0.4, 0.2);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 0.8f, 1.4f);
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
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }
}
