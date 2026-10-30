package net.ragnar.fmscraft.boss.greed;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.fmscraft.boss.ModBossEntities;

import java.util.UUID;

/**
 * One of RAGGIMEL's bubbles. It drifts slowly after the player it was blown at; if it touches them it seals them in,
 * floats them up {@code lift} blocks and holds them there until it pops on its own ({@code holdTicks}) or anyone
 * else hits it, and they drop. The player inside can't pop it themselves. Anyone's hit or arrow pops a free bubble too.
 */
public class GreedBubbleEntity extends Entity {
    /** Big enough to close around a player. */
    public static final float SIZE = 2.0f;
    /** A free bubble pops after this long if it hasn't caught anyone. */
    private static final int LIFE = 200;
    private static final TrackedData<Integer> CAPTIVE = DataTracker.registerData(GreedBubbleEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID targetId;
    private double speed = 0.2, lift = 3.5;
    private int holdTicks = 80;
    private int heldFor;
    private Vec3d heldAt;

    public GreedBubbleEntity(EntityType<? extends GreedBubbleEntity> type, World world) {
        super(type, world);
        noClip = false;
        setNoGravity(true);
    }

    public static GreedBubbleEntity blow(ServerWorld world, Vec3d from, PlayerEntity target, double speed, double lift, int holdTicks) {
        GreedBubbleEntity bubble = new GreedBubbleEntity(ModBossEntities.GREED_BUBBLE, world);
        bubble.setPosition(from.x, from.y - SIZE / 2, from.z);
        bubble.targetId = target.getUuid();
        bubble.speed = speed;
        bubble.lift = lift;
        bubble.holdTicks = holdTicks;
        Vec3d dir = target.getBoundingBox().getCenter().subtract(from).normalize();
        bubble.setVelocity(dir.multiply(speed * 1.6));
        world.spawnEntity(bubble);
        return bubble;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(CAPTIVE, -1);
    }

    /** The entity sealed inside, if any. */
    public Entity captive() {
        int id = dataTracker.get(CAPTIVE);
        return id < 0 ? null : getWorld().getEntityById(id);
    }

    public boolean isHolding(Entity entity) {
        return dataTracker.get(CAPTIVE) == entity.getId();
    }

    /** The bubble {@code entity} is sealed in, or null. */
    public static GreedBubbleEntity bubbleOf(Entity entity) {
        for (GreedBubbleEntity bubble : entity.getWorld().getEntitiesByClass(GreedBubbleEntity.class,
                entity.getBoundingBox().expand(2), b -> b.isHolding(entity))) {
            return bubble;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;

        Entity captive = captive();
        if (captive != null) {
            hold(world, captive);
            return;
        }
        if (dataTracker.get(CAPTIVE) >= 0 || age > LIFE) {
            pop(world);
            return;
        }

        // Drift after the target, lazily, with a little bob
        PlayerEntity target = targetId == null ? null : world.getPlayerByUuid(targetId);
        Vec3d want = getVelocity();
        if (target != null && target.isAlive()) {
            // Aim to end up around them: its middle a little above theirs, so it doesn't drag along the ground
            Vec3d to = target.getPos().add(0, SIZE / 2 + 0.05, 0).subtract(getBoundingBox().getCenter());
            want = to.normalize().multiply(speed);
        }
        want = want.add(0, Math.sin(age * 0.2) * 0.01, 0);
        setVelocity(getVelocity().lerp(want, 0.06));
        move(MovementType.SELF, getVelocity());
        // Bounce softly off whatever it bumps into
        Vec3d v = getVelocity();
        if (horizontalCollision) setVelocity(v.multiply(-0.5, 1, -0.5));
        if (verticalCollision) setVelocity(getVelocity().multiply(1, -0.5, 1));

        for (PlayerEntity player : world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                && p.getBoundingBox().intersects(getBoundingBox()))) {
            if (bubbleOf(player) == null) {
                seal(world, player);
                break;
            }
        }
        if (age % 4 == 0) world.spawnParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + SIZE / 2, getZ(), 1, 0.4, 0.4, 0.4, 0.01);
    }

    private void seal(ServerWorld world, PlayerEntity player) {
        dataTracker.set(CAPTIVE, player.getId());
        heldAt = player.getPos();
        heldFor = 0;
        setVelocity(Vec3d.ZERO);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL, SoundCategory.HOSTILE, 1.2f, 0.6f);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.HOSTILE, 1.5f, 1.3f);
        world.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.6, 0.5, 0.1);
    }

    private void hold(ServerWorld world, Entity captive) {
        heldFor++;
        if (heldFor > holdTicks || !captive.isAlive() || captive.getWorld() != world) {
            pop(world);
            return;
        }
        double rise = lift * Math.min(1.0, heldFor / 25.0);
        Vec3d at = heldAt.add(0, rise + Math.sin(heldFor * 0.15) * 0.15, 0);
        // The bubble sits around the player, the player in the middle of it
        setPosition(at.x, at.y - (SIZE - captive.getHeight()) / 2, at.z);
        captive.fallDistance = 0;
        captive.setVelocity(Vec3d.ZERO);
        if (captive instanceof ServerPlayerEntity player) {
            if (heldFor % 2 == 0 || heldFor < 30) player.networkHandler.requestTeleport(at.x, at.y, at.z, 0, 0, PositionFlag.ROT);
        } else {
            captive.setPosition(at);
        }
        if (heldFor > holdTicks - 30 && heldFor % 6 == 0) {
            // About to pop: it wobbles and squeaks
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL, SoundCategory.HOSTILE, 0.6f, 1.6f);
        }
    }

    /** Bursts the bubble; anyone inside drops. */
    public void pop(ServerWorld world) {
        if (isRemoved()) return;
        Vec3d c = getBoundingBox().getCenter();
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.HOSTILE, 2.0f, 0.8f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.HOSTILE, 0.6f, 1.6f);
        world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 40, 0.6, 0.6, 0.6, 0.2);
        world.spawnParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 20, 0.6, 0.6, 0.6, 0.05);
        dataTracker.set(CAPTIVE, -1);
        discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!(getWorld() instanceof ServerWorld world) || isRemoved()) return false;
        Entity attacker = source.getAttacker();
        // The one inside can't get themselves out, and RAGGIMEL's own attacks don't burst his bubbles
        if (attacker != null && (isHolding(attacker) || attacker instanceof RaggimelEntity)) return false;
        pop(world);
        return true;
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
