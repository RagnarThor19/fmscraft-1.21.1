package net.ragnar.fmscraft.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A Statue of Lust: a weeping woman carved in tuff, veiled, roses in her hair, one hand at her breast, mouth open in
 * a scream, eyes shut. Three blocks tall, they stand on chiseled tuff plinths all through Lust's Garden.
 *
 * <ul>
 *   <li><b>Asleep</b> on its plinth it never moves. Look one in the face (close enough to see it through the fog,
 *       nothing in the way, standing in front of it) and its eyes open: it's awake. That's the only thing about it
 *       that ever visibly changes.</li>
 *   <li><b>Awake</b>, it comes for the nearest player in the garden, silently and quickly, but only while nobody at
 *       all can see it ({@link #isObserved}). The moment anyone has it in view, it's stone again, mid-step. Touch
 *       it while nobody's looking and it embraces you: {@link #DAMAGE} damage and you're thrown back.</li>
 *   <li>Brought near one of the four empty plinths round the fountain, an awake statue takes its place there for
 *       good ({@link #settle}): {@link net.ragnar.fmscraft.structure.lust.LustGardens} counts them.</li>
 * </ul>
 * In the twilight's fog anything further than {@link #SEEN_IN_FOG} blocks can't be seen, so they creep up through
 * it. The client never draws one moving either (LustStatueRenderer): it only catches up while off screen.
 * Statues are never saved: the garden raises them on their plinths whenever someone comes.
 */
public class LustStatueEntity extends PathAwareEntity {
    public static final float DAMAGE = 24f;
    /** About 6 blocks a second (mobs move by the square of this): faster than you can sprint, even without Slowness. */
    public static final double SPEED = 0.367;
    /** How far you can see in the twilight's fog, and anywhere else. */
    public static final double SEEN_IN_FOG = 11, SEEN_IN_CLEAR = 48;
    /** Anything within this angle of where you're looking counts as seen: more than any field of view shows. */
    public static final double VIEW_COS = Math.cos(Math.toRadians(80));
    /** Looking it in the face: its head within this angle of the middle of your view, and you in front of it. */
    public static final double FACE_COS = Math.cos(Math.toRadians(10)), FRONT_COS = Math.cos(Math.toRadians(75));
    public static final int STRIKE_COOLDOWN = 40;
    private static final int REPATH_TICKS = 10;
    public static final RegistryKey<DamageType> EMBRACE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(FmsCraft.MOD_ID, "lust_statue"));
    private static final TrackedData<Boolean> EYES_OPEN = DataTracker.registerData(LustStatueEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Where it may go and who it hunts: the inside of its garden. Null when placed by hand (anywhere, anyone near). */
    private Predicate<BlockPos> garden;
    private BlockPos home;
    private float homeYaw;
    private boolean awake, calm, settled, observed, frozen = true;
    private int strikeCooldown;
    private UUID targetId;

    // Client side only: what's drawn, caught up only while it can't be seen (LustStatueRenderer)
    public double shownX, shownY, shownZ;
    public float shownYaw;
    public boolean shownSet;
    public int eyesOpenedAge = Integer.MIN_VALUE / 2;
    private boolean eyesWereOpen;

    public LustStatueEntity(EntityType<? extends LustStatueEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        setPersistent();
        setPathfindingPenalty(PathNodeType.WATER, -1f);
        setPathfindingPenalty(PathNodeType.WATER_BORDER, 4f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(EYES_OPEN, false);
    }

    @Override
    protected void initGoals() {
    }

    /** Ties the statue to its garden and plinth. */
    public void bind(Predicate<BlockPos> garden, BlockPos home, float homeYaw) {
        this.garden = garden;
        this.home = home;
        this.homeYaw = homeYaw;
    }

    public BlockPos getHome() {
        return home;
    }

    public boolean eyesOpen() {
        return dataTracker.get(EYES_OPEN);
    }

    public boolean isAwake() {
        return awake;
    }

    public boolean isSettled() {
        return settled;
    }

    /** Whether anyone could see it this tick (as of its last tick). */
    public boolean isObserved() {
        return observed;
    }

    /** Its eyes open and it's free to move. */
    public void wake() {
        if (awake || calm) return;
        awake = true;
        dataTracker.set(EYES_OPEN, true);
        // Barely a sound: a little grit of stone as the lids part
        getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_TUFF_BRICKS_HIT, SoundCategory.HOSTILE, 0.4f, 0.5f);
    }

    /** Stone for good, eyes shut: the garden is at peace. */
    public void calm() {
        calm = true;
        dataTracker.set(EYES_OPEN, false);
        getNavigation().stop();
    }

    /** Steps up onto one of the fountain's plinths, turns to the fountain and closes its eyes, for good. */
    public void settle(BlockPos top, float yaw) {
        calm();
        settled = true;
        refreshPositionAndAngles(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, yaw, 0);
        setHeadYaw(yaw);
        setBodyYaw(yaw);
        setVelocity(Vec3d.ZERO);
    }

    // ---- Server ----

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            boolean open = eyesOpen();
            if (open && !eyesWereOpen && age > 1) eyesOpenedAge = age;
            eyesWereOpen = open;
        }
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (strikeCooldown > 0) strikeCooldown--;
        if (garden != null && home != null && !settled && !garden.test(getBlockPos())) goHome();

        observed = computeObserved(world);
        if (calm || !awake) {
            frozen = true;
            getNavigation().stop();
            if (!calm && age % 2 == 0 && lookedInTheFace(world)) wake();
            return;
        }
        if (observed) {
            frozen = true;
            getNavigation().stop();
            return;
        }
        frozen = false;
        PlayerEntity target = targetId == null ? null : world.getPlayerByUuid(targetId);
        if (target != null && !huntable(target)) target = null;
        if (age % REPATH_TICKS == 0 || target == null || getNavigation().isIdle()) {
            target = nearestPrey(world);
            targetId = target == null ? null : target.getUuid();
            if (target == null) getNavigation().stop();
            else getNavigation().startMovingTo(target, 1.0);
        }
        embraceTouching(world);
    }

    private void goHome() {
        refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, homeYaw, 0);
        setHeadYaw(homeYaw);
        setBodyYaw(homeYaw);
        getNavigation().stop();
    }

    /** Frozen (asleep, calm, or being watched): not a step, not a turn. Gravity still holds it to the ground. */
    @Override
    public void travel(Vec3d movementInput) {
        if (frozen && !getWorld().isClient) {
            Vec3d v = getVelocity();
            setVelocity(0, v.y, 0);
            super.travel(Vec3d.ZERO);
            return;
        }
        super.travel(movementInput);
    }

    private double seenRange(World world) {
        return world.getRegistryKey().equals(Sin.SinWorlds.TWILIGHT) ? SEEN_IN_FOG : SEEN_IN_CLEAR;
    }

    private boolean canSee(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && player.getWorld() == getWorld() && !player.hasStatusEffect(StatusEffects.BLINDNESS);
    }

    private boolean clearLine(World world, Vec3d from, Vec3d to, PlayerEntity player) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /** Whether any player could see any part of it right now: within sight in the fog, in front of them, nothing between. */
    public boolean computeObserved(ServerWorld world) {
        double range = seenRange(world);
        Box box = getBoundingBox();
        Vec3d[] points = {
                new Vec3d(getX(), box.maxY - 0.2, getZ()), new Vec3d(getX(), box.minY + box.getLengthY() * 0.5, getZ()),
                new Vec3d(getX(), box.minY + 0.2, getZ()),
                new Vec3d(box.minX, box.minY + 1, box.minZ), new Vec3d(box.maxX, box.minY + 1, box.maxZ),
                new Vec3d(box.minX, box.minY + 1, box.maxZ), new Vec3d(box.maxX, box.minY + 1, box.minZ)};
        for (PlayerEntity player : world.getPlayers()) {
            if (!canSee(player)) continue;
            Vec3d eye = player.getEyePos();
            if (eye.squaredDistanceTo(getPos()) > (range + 2) * (range + 2)) continue;
            Vec3d look = player.getRotationVec(1f);
            for (Vec3d point : points) {
                Vec3d to = point.subtract(eye);
                double distance = to.length();
                if (distance > range) continue;
                if (distance > 0.5 && look.dotProduct(to.multiply(1 / distance)) < VIEW_COS) continue;
                if (clearLine(world, eye, point, player)) return true;
            }
        }
        return false;
    }

    /** Someone close enough, in front of it, looking right at its face with nothing in the way. */
    private boolean lookedInTheFace(ServerWorld world) {
        double range = seenRange(world);
        Vec3d face = new Vec3d(getX(), getEyeY(), getZ());
        Vec3d facing = Vec3d.fromPolar(0, bodyYaw);
        for (PlayerEntity player : world.getPlayers()) {
            if (!canSee(player)) continue;
            Vec3d eye = player.getEyePos();
            Vec3d to = face.subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 0.3) continue;
            if (player.getRotationVec(1f).dotProduct(to.multiply(1 / distance)) < FACE_COS) continue;
            Vec3d toPlayer = eye.subtract(face).multiply(1, 0, 1);
            if (toPlayer.lengthSquared() > 1e-4 && facing.dotProduct(toPlayer.normalize()) < FRONT_COS) continue;
            if (clearLine(world, eye, face, player)) return true;
        }
        return false;
    }

    private PlayerEntity nearestPrey(ServerWorld world) {
        return world.getPlayers().stream().filter(this::huntable)
                .min(Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
    }

    private boolean huntable(PlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.getWorld() != getWorld()) return false;
        if (garden != null) return garden.test(player.getBlockPos());
        return squaredDistanceTo(player) < 32 * 32;
    }

    /** Whoever it reaches while nobody's watching. */
    private void embraceTouching(ServerWorld world) {
        if (strikeCooldown > 0) return;
        DamageSource source = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(EMBRACE), this);
        for (PlayerEntity victim : world.getEntitiesByClass(PlayerEntity.class, getBoundingBox().expand(0.15),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            if (!victim.damage(source, DAMAGE)) continue;
            strikeCooldown = STRIKE_COOLDOWN;
            Vec3d away = victim.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (away.lengthSquared() < 1e-4) away = getRotationVector().multiply(1, 0, 1);
            Vec3d push = away.normalize().multiply(1.2);
            victim.addVelocity(push.x, 0.4, push.z);
            victim.velocityModified = true;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.HOSTILE, 0.8f, 0.5f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.TUFF.getDefaultState()),
                    victim.getX(), victim.getBodyY(0.5), victim.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
        }
    }

    // ---- It's a statue ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount); // /kill
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity) playSound(SoundEvents.BLOCK_TUFF_HIT, 1.0f, 0.6f);
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

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    public void slowMovement(BlockState state, Vec3d multiplier) {
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

    /** Raised again by the garden whenever someone comes; nothing to save. */
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
        return SoundEvents.BLOCK_TUFF_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_TUFF_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }
}
