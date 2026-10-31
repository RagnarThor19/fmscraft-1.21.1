package net.ragnar.fmscraft.boss.wrath;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.fmscraft.boss.ModBossEntities;
import org.jetbrains.annotations.Nullable;

/**
 * One of ORNAMIX's ghast fireballs. Looks and sounds like a ghast's, flies straight and fast without speeding up,
 * and bursts on whatever it touches without breaking a single block: it hurts, burns and throws back anyone close.
 * <p>
 * Like a ghast's, a fireball can be hit back (a punch, a sword, an arrow): sent back into ORNAMIX it hurts him a lot.
 */
public class OrnamixFireballEntity extends AbstractFireballEntity {
    public static final double SPEED = 1.1;
    public static final double BLAST_RADIUS = 2.4;
    /** What a fireball sent back into him takes off (before his own damage rules). */
    public static final float RETURNED_DAMAGE = 24f;
    private static final int LIFETIME = 100;

    private float damage = 7f;

    public OrnamixFireballEntity(EntityType<? extends OrnamixFireballEntity> type, World world) {
        super(type, world);
        accelerationPower = 0;
    }

    /** A fireball from {@code owner} at {@code from}, flying along {@code dir} at {@link #SPEED}, hurting for {@code damage}. */
    public static OrnamixFireballEntity shoot(ServerWorld world, LivingEntity owner, Vec3d from, Vec3d dir, float damage) {
        OrnamixFireballEntity ball = new OrnamixFireballEntity(ModBossEntities.ORNAMIX_FIREBALL, world);
        ball.setOwner(owner);
        ball.damage = damage;
        ball.refreshPositionAndAngles(from.x, from.y, from.z, owner.getYaw(), owner.getPitch());
        ball.setVelocity(dir.normalize().multiply(SPEED));
        ball.velocityDirty = true;
        world.spawnEntity(ball);
        return ball;
    }

    @Override
    protected float getDrag() {
        return 1.0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && age > LIFETIME) burst((ServerWorld) getWorld(), null);
    }

    @Override
    protected void onDeflected(@Nullable Entity deflector, boolean fromAttack) {
        super.onDeflected(deflector, fromAttack);
        // Sent back where it came from, at the same speed: no drifting off slowly
        accelerationPower = 0;
        Vec3d v = getVelocity();
        if (v.lengthSquared() > 1e-6) setVelocity(v.normalize().multiply(SPEED));
        velocityDirty = true;
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        // Handled by the burst in onCollision
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (getWorld() instanceof ServerWorld world) {
            burst(world, hit instanceof EntityHitResult e ? e.getEntity() : null);
        }
    }

    private void burst(ServerWorld world, @Nullable Entity direct) {
        if (isRemoved()) return;
        Vec3d c = getPos();
        Entity owner = getOwner();
        world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.4, 0.4, 0.4, 0);
        world.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.12);
        world.spawnParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 6, 0.4, 0.4, 0.4, 0);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.4f, 1.3f);

        if (owner instanceof PlayerEntity player) {
            // Hit back by a player: it's ORNAMIX it's after now
            for (OrnamixEntity boss : world.getEntitiesByClass(OrnamixEntity.class, getBoundingBox().expand(BLAST_RADIUS), b -> true)) {
                boss.takeReturnedFireball(this, player);
            }
        } else {
            for (ServerPlayerEntity p : world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
                double d = p.getBoundingBox().getCenter().distanceTo(c);
                if (d > BLAST_RADIUS && p != direct) continue;
                // A blast, like a ghast's: blast protection helps, fire resistance doesn't
                p.damage(getDamageSources().explosion(this, owner instanceof LivingEntity living ? living : null), damage);
                p.setOnFireFor(4);
                Vec3d away = p.getPos().subtract(c).multiply(1, 0, 1);
                away = away.lengthSquared() > 1e-4 ? away.normalize() : getVelocity().multiply(1, 0, 1).normalize();
                p.addVelocity(away.x * 0.7, 0.35, away.z * 0.7);
                p.velocityModified = true;
            }
        }
        discard();
    }
}
