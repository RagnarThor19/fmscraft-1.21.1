package net.ragnar.fmscraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.ragnar.fmscraft.FmsCraft;

import java.util.Comparator;
import java.util.UUID;

/**
 * A Statue of Greed, one of the three that rise from the smooth stone in the floor of the Greed Vault: a gilded
 * figure, its gold leaf flaking off, with arms that hang too long and hands that reach for you. It can't be hurt,
 * pushed, burned or blown up, and it never stops coming: silently and very slowly, along the vault's corridors,
 * toward the nearest player in the vault. Touching it is a terrible idea: {@link #DAMAGE} damage and you're thrown back.
 *
 * Its moves are drawn a few frames at a time on the client (GreedStatueRenderer), like something you only catch
 * moving out of the corner of your eye. Statues are never saved: {@link net.ragnar.fmscraft.structure.greed.GreedVaults}
 * raises them whenever someone comes near the vault and lets them sink back when everyone has gone.
 */
public class GreedStatueEntity extends PathAwareEntity {
    public static final float DAMAGE = 28.8f;
    /**
     * About 0.25 blocks a second: barely moving, but it never stops. Mobs accelerate by the square of this, so it's
     * not linear: 0.23, a zombie's, is about 2.3 blocks a second, and 0.14 would be about 0.85.
     */
    public static final double SPEED = 0.075;
    /** A statue placed by hand hunts players this close; a vault's statues hunt anyone in their vault. */
    public static final double HUNT_RANGE = 24;
    private static final int REPATH_TICKS = 10;
    public static final RegistryKey<DamageType> CLAIMED =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(FmsCraft.MOD_ID, "greed_statue"));

    /** The vault this statue belongs to and the stone it rose from, or null when placed by hand. */
    private BlockBox vault;
    private BlockPos home;
    private UUID targetId;

    // Stop-motion, client side only: the pose currently drawn and when it was last caught up with
    public double shownX, shownY, shownZ;
    public float shownBodyYaw, shownHeadYaw;
    public int shownAge = Integer.MIN_VALUE / 2;

    public GreedStatueEntity(EntityType<? extends GreedStatueEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64);
    }

    /** Ties the statue to its vault: it only hunts players inside it, and is put back on its stone if it ever leaves. */
    public void bind(BlockBox vault, BlockPos home) {
        this.vault = vault;
        this.home = home;
    }

    public BlockPos getHome() {
        return home;
    }

    @Override
    protected void initGoals() {
    }

    // ---- Hunting ----

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (vault != null && home != null && !vault.expand(2).contains(getBlockPos())) {
            refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, getYaw(), 0);
            getNavigation().stop();
        }

        PlayerEntity target = targetId == null ? null : world.getPlayerByUuid(targetId);
        if (age % REPATH_TICKS == 0 || (target != null && !huntable(target))) {
            target = nearestPrey(world);
            targetId = target == null ? null : target.getUuid();
            if (target == null) getNavigation().stop();
            else getNavigation().startMovingTo(target, 1.0);
        }
        if (target != null) getLookControl().lookAt(target, 30f, 30f);
        if (random.nextInt(500) == 0) {
            // The vault's own cave noises, from right where the statue stands
            world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundCategory.HOSTILE, 0.8f, 0.6f);
        }
        crushTouching(world);
    }

    private PlayerEntity nearestPrey(ServerWorld world) {
        return world.getPlayers().stream().filter(this::huntable)
                .min(Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
    }

    private boolean huntable(PlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.getWorld() != getWorld()) return false;
        if (vault != null) return vault.contains(player.getBlockPos());
        return squaredDistanceTo(player) < HUNT_RANGE * HUNT_RANGE;
    }

    private void crushTouching(ServerWorld world) {
        DamageSource source = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(CLAIMED), this);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(0.1),
                e -> e != this && e.isAlive() && !e.isSpectator() && !(e instanceof GreedStatueEntity)
                        && !(e instanceof PlayerEntity player && player.isCreative()))) {
            if (!victim.damage(source, DAMAGE)) continue;
            Vec3d away = victim.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() < 1e-4) away = getRotationVector().multiply(1, 0, 1);
            Vec3d push = away.normalize().multiply(1.3);
            victim.addVelocity(push.x, 0.45, push.z);
            victim.velocityModified = true;
            playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.7f, 0.5f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GOLD_BLOCK.getDefaultState()),
                    victim.getX(), victim.getBodyY(0.5), victim.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
        }
    }

    // ---- It's a statue ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount); // /kill
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity) playSound(SoundEvents.BLOCK_STONE_HIT, 1.0f, 0.6f);
        return false;
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        return false;
    }

    @Override
    public boolean isAffectedBySplashPotions() {
        return false;
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    /** It doesn't shove: what touches it gets thrown instead (see crushTouching). */
    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    /** Cobwebs and berry bushes don't slow it. */
    @Override
    public void slowMovement(BlockState state, Vec3d multiplier) {
    }

    /** Never up the ladder in the pipe: it can't leave the vault. */
    @Override
    public boolean isClimbing() {
        return false;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    /** Raised again by the vault whenever someone comes; nothing to save. */
    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLOCK_STONE_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_STONE_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }
}
