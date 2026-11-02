package net.ragnar.fmscraft.boss.gluttony;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.SinBossEntity;
import net.ragnar.fmscraft.compat.MagicSpells;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LalliLarge, the Sin of Gluttony: Larus, three blocks tall, all ice. The slowest of them and by far the toughest, and
 * he holds nothing: his fists and the cold are enough. He walks after whoever he's picked and punches, and on the beat
 * picks one of:
 * <ul>
 *   <li><b>Ice Punch</b>: draws his fist back while frost gathers on it, then lunges: a huge knock and you're frozen
 *       solid where you land.</li>
 *   <li><b>Ice Beam</b> (like the Tome of Ice Beam): a dotted line marks you, then a thin icy beam lances out and follows
 *       you, slower than you can run; it freezes what it touches.</li>
 *   <li><b>Dash</b>: crouches with a line of frost along the ground showing the way, then charges down it.</li>
 *   <li><b>Tome of the Glacier</b>: points at you a long moment (a chunk of ice growing in his hand, a dotted line where
 *       it's going), then hurls it: it bursts into a block of ice round whoever it reaches.</li>
 *   <li><b>Icicle Crash</b> (like the Tome of Falling Stalactite): a great icicle forms over each of you, drifts after
 *       you, trembles, then drops. The ring on the ground shows where.</li>
 *   <li><b>Frost Breath</b>: a cone of freezing breath, turning after you; stay in it and you freeze.</li>
 *   <li><b>Large Push</b>: arms in while the snow is drawn to him from the ring marking his reach, then a blast that
 *       throws everyone in it right across the pole.</li>
 *   <li><b>Ice Shards</b> and <b>Frost Stomp</b>: the basic ones, fans of ice thrown straight and a ring of frost to jump.</li>
 * </ul>
 * At half health he rises over the pole and SIRENS starts; at its switch-up the fight goes to the snowfield, and his
 * eyes burn blue in a strong freezing aura. There he's worse, adds the <b>Tome of Freezing</b> (a globe of frost that
 * freezes all in it), <b>Jump Slam</b> (leaps high and comes down in a Tome of Earthquake), <b>Rising Ice Spikes</b>
 * (rows of ice shards out of the ground at you), <b>Impaling</b> (an ice spike bursting up under each of you), <b>Ice
 * Blade</b> (a great blade of ice in his hand, cutting at you furiously) followed by <b>Blade Throw</b> (he spins and
 * hurls it), and casts his Ice Beam far more. <b>Ice Cannon</b> (like the Tome of Stone Cannon: three great icicles spin
 * up over his head and fire one by one) he has in both phases. Everything he does is aimed at all of you, or one each, so it holds up for 1 to 10 and more.
 */
public class LalliEntity extends SinBossEntity {
    public static final BossSong PHASE_ONE = new BossSong("fmscraft:music.gluttony.phase_one", LalliTimeline.PIE_BPM, LalliTimeline.PIE_FIRST_BEAT);
    public static final BossSong PHASE_TWO = new BossSong("fmscraft:music.gluttony.phase_two", LalliTimeline.SIRENS_BPM, LalliTimeline.SWITCH_TICK);

    // ---- Tuning knobs ----
    /** Full health, for 5-7 players; {@link LalliTimeline#healthScale} scales it. Over the 1024 cap he takes less damage instead. */
    public static final float MAX_HEALTH = 1400;
    /** Damage he takes is multiplied by this (on top of his armor), more while he's exposed, and capped per hit. */
    public static final float DAMAGE_TAKEN = 0.7f, PHASE_TWO_TAKEN = 0.8f, EXPOSED_BONUS = 1.5f, MAX_HIT = 24f;
    /** Every hit he deals is multiplied by this in phase 2, then rounded up. */
    public static final float PHASE_TWO_DEALT = 1.25f;
    /** Three blocks tall (a player is 1.8). */
    public static final float SCALE = 3.0f / 1.8f;
    private static final double SPEED = 0.2;
    private static final int REST = 28, REST_TWO = 16;
    /** In phase 2 anyone this close to him starts to freeze. */
    public static final double AURA_RADIUS = 6.5;
    private static final float HEALTH_CAP = 1024f;

    // ---- What the client draws ----
    public static final int POSE_IDLE = 0, POSE_FLOAT = 1, POSE_LAND = 2, POSE_PUNCH_CHARGE = 3, POSE_PUNCH = 4, POSE_POINT = 5,
            POSE_RAISE = 6, POSE_BREATH = 7, POSE_PUSH_CHARGE = 8, POSE_PUSH = 9, POSE_DASH = 10, POSE_STOMP = 11, POSE_CAST = 12,
            POSE_JUMP = 13, POSE_BEAM = 14, POSE_ROAR = 15, POSE_SLASH_A = 16, POSE_SLASH_B = 17, POSE_SPIN = 18;
    public static final int BEAM_OFF = 0, BEAM_CHARGE = 1, BEAM_FIRE = 2;
    private static final TrackedData<Integer> POSE = DataTracker.registerData(LalliEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> EYES = DataTracker.registerData(LalliEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> BEAM = DataTracker.registerData(LalliEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> BEAM_END = DataTracker.registerData(LalliEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Boolean> BLADE = DataTracker.registerData(LalliEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    enum Action {
        ENTRANCE, MOVE, EXPOSED, TRANSITION,
        ICE_PUNCH, ICE_BEAM, DASH, GLACIER, ICICLES, FROST_BREATH, LARGE_PUSH, SHARDS, STOMP,
        FREEZING, JUMP_SLAM, RISING_SPIKES, IMPALING, ICE_BLADE, BLADE_THROW, ICE_CANNON
    }

    private Action action = Action.ENTRANCE;
    private int actionTicks, rest, repeats, exposedTicks, meleeCooldown;
    private Action lastAttack;
    private final Map<Action, Integer> lastUsed = new EnumMap<>(Action.class);
    private final Map<UUID, Integer> lastPicked = new HashMap<>();
    private final Map<UUID, Integer> straying = new HashMap<>();
    private final Map<UUID, Integer> exposure = new HashMap<>();
    private final List<GluttonyHazards.Hazard> hazards = new ArrayList<>();
    private List<ServerPlayerEntity> fighters = List.of();
    private UUID focus;
    private int focusTicks;
    private boolean untouchable = true, scaled, heldStill, transitioned, killing, quakeCharging;
    private float toughness = 1f;

    // The arrival
    private record Drop(Vec3d from, Vec3d to) {
    }

    private final Map<UUID, Drop> held = new LinkedHashMap<>();
    private int startTick = -1, landTick, appearTick, bossLandTick;
    private boolean fromSky;

    // The attack in progress
    private Vec3d aim, dashDir, spot;
    private float breathYaw;
    private GluttonyHazards.Halo halo;
    private Vec3d liftoff = GluttonyArena.POLE;

    public LalliEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 1200;
        bossBar().setColor(BossBar.Color.BLUE);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_CAP)
                .add(EntityAttributes.GENERIC_ARMOR, 14)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 8)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.6)
                .add(EntityAttributes.GENERIC_SCALE, SCALE);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_IDLE);
        builder.add(EYES, false);
        builder.add(BEAM, BEAM_OFF);
        builder.add(BEAM_END, new Vector3f());
        builder.add(BLADE, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    protected Text bossBarName() {
        return Text.literal("LalliLarge");
    }

    @Override
    protected BossSong songForPhase(int phase) {
        return phase == 1 ? PHASE_ONE : PHASE_TWO;
    }

    /** SIRENS starts when he rises over the pole, not a moment before ({@link #onPhaseTwo}). */
    @Override
    protected boolean startsPhaseTwoSongAtOnce() {
        return false;
    }

    // ---- What the client and the tests see ----

    public int poseState() {
        return dataTracker.get(POSE);
    }

    /** His eyes burn blue in phase 2. */
    public boolean hasGlowingEyes() {
        return dataTracker.get(EYES);
    }

    /** Whether he's holding his ice blade (Ice Blade, until the Blade Throw lets it go). */
    public boolean hasBlade() {
        return dataTracker.get(BLADE);
    }

    public int beamStage() {
        return dataTracker.get(BEAM);
    }

    public Vec3d beamEnd() {
        Vector3f v = dataTracker.get(BEAM_END);
        return new Vec3d(v.x, v.y, v.z);
    }

    public boolean isUntouchable() {
        return untouchable;
    }

    public String currentAction() {
        return action.name();
    }

    /** True once the fight has gone to the snowfield. */
    public boolean hasTransitioned() {
        return transitioned;
    }

    /** How much damage it takes to beat him from full: his max health, and more past the 1024 cap for a big group. */
    public float effectiveMaxHealth() {
        return getMaxHealth() / toughness;
    }

    /** For tests: between attacks he stands still and starts none of his own, only those he's told to. */
    public void holdStill() {
        heldStill = true;
    }

    /** For tests: starts one of his attacks by name. */
    public void forceAttack(String name) {
        startAttack(Action.valueOf(name));
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
    }

    /** Where his beam comes from: his right hand, held out in front. Works on the client too (for drawing it). */
    public Vec3d handPos(float tickDelta) {
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, prevBodyYaw, bodyYaw);
        Vec3d forward = Vec3d.fromPolar(0, yaw);
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(-0.35 * SCALE);
        return getLerpedPos(tickDelta).add(0, getHeight() * 0.72, 0).add(forward.multiply(0.75 * SCALE)).add(side);
    }

    private Vec3d mouthPos() {
        return getPos().add(0, getHeight() * 0.86, 0).add(Vec3d.fromPolar(0, getBodyYaw()).multiply(0.45 * SCALE));
    }

    // ---- What the hazards use ----

    List<ServerPlayerEntity> fighters() {
        return fighters;
    }

    ServerPlayerEntity fighter(UUID id) {
        if (id == null) return null;
        for (ServerPlayerEntity p : fighters) if (p.getUuid().equals(id)) return p;
        return null;
    }

    void addHazard(GluttonyHazards.Hazard hazard) {
        hazards.add(hazard);
    }

    /** A hit he deals, after phase 2's {@link #PHASE_TWO_DEALT}, rounded up to a whole number. */
    float dealt(float damage) {
        return (float) Math.ceil(damage * (transitioned ? PHASE_TWO_DEALT : 1f));
    }

    /** His fist: a shield held towards him stops it. */
    boolean punch(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobAttack(this), dealt(damage));
    }

    /** Ice thrown or fired at you: a shield held towards him stops it. */
    boolean strike(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobProjectile(this, this), dealt(damage));
    }

    /** The cold itself, and ice out of the ground: no shield helps. */
    void crush(ServerPlayerEntity player, float damage) {
        player.damage(getDamageSources().indirectMagic(this, this), dealt(damage));
    }

    /** Chilled: slowed a moment, the frost creeping in at the edges of your sight. */
    void chill(ServerPlayerEntity player, int ticks) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 0, false, true, true), this);
        player.setFrozenTicks(Math.min(player.getMinFreezeDamageTicks() + 20, player.getFrozenTicks() + ticks));
    }

    /** Frozen solid for {@code ticks}, starting {@code delay} from now (after a knock lands). Freezing someone frozen holds them longer. */
    void freeze(ServerPlayerEntity player, int ticks, int delay, float shatter) {
        for (GluttonyHazards.Hazard h : hazards) {
            if (h instanceof GluttonyHazards.Frozen f && f.player == player) {
                f.ticks = Math.max(f.ticks, f.age + ticks);
                return;
            }
        }
        hazards.add(new GluttonyHazards.Frozen(player, ticks, delay, shatter));
    }

    boolean isFrozen(ServerPlayerEntity player) {
        for (GluttonyHazards.Hazard h : hazards) if (h instanceof GluttonyHazards.Frozen f && f.player == player && f.shell != null) return true;
        return false;
    }

    /** Throws {@code player} along {@code dir} (flattened), {@code horizontal} fast and {@code up} into the air. */
    void launch(ServerPlayerEntity player, Vec3d dir, double horizontal, double up) {
        Vec3d flat = dir.multiply(1, 0, 1);
        flat = flat.lengthSquared() > 1e-4 ? flat.normalize() : Vec3d.fromPolar(0, getYaw());
        player.setVelocity(flat.x * horizontal, up, flat.z * horizontal);
        player.velocityModified = true;
    }

    /** Everyone in the arena near him, fighting or not. */
    List<ServerPlayerEntity> audience(ServerWorld world) {
        return world.getPlayers(p -> p.squaredDistanceTo(this) < 300 * 300);
    }

    /** Particles everyone near him sees, however far off they are. */
    void emit(ServerWorld world, ParticleEffect particle, Vec3d at, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : audience(world)) world.spawnParticles(p, particle, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
    }

    boolean blocked(Vec3d from, Vec3d to) {
        return getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this))
                .getType() != HitResult.Type.MISS;
    }

    /** The ground under {@code pos} (or {@code pos} itself if there's none close). */
    Vec3d ground(Vec3d pos) {
        double g = groundY(pos);
        return Double.isNaN(g) ? pos : new Vec3d(pos.x, g, pos.z);
    }

    /** Top of the ground under {@code pos} (looking a little up and well down), or {@code pos.y} if there's none. */
    double groundY(Vec3d pos) {
        double g = groundBelow(pos.add(0, 2.5, 0), 14);
        return Double.isNaN(g) ? pos.y : g;
    }

    // ---- Taking damage ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) {
            killing = true;
            try {
                return super.damage(source, amount);
            } finally {
                killing = false;
            }
        }
        Entity attacker = source.getAttacker();
        if (attacker == this) return false;
        if (untouchable) {
            if (attacker instanceof PlayerEntity) {
                getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.4f, 0.6f);
            }
            return false;
        }
        amount *= (transitioned ? PHASE_TWO_TAKEN : DAMAGE_TAKEN) * toughness;
        if (action == Action.EXPOSED) amount *= EXPOSED_BONUS;
        amount = Math.min(amount, MAX_HIT);
        // Phase 1 can't be skipped: his health stops at half (setHealth), and at half nothing more gets through
        if (getPhase() == 1 && getHealth() <= getMaxHealth() * phaseTwoHealth()) amount = 0;
        if (amount <= 0) {
            getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.4f, 0.6f);
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt && getWorld() instanceof ServerWorld world) {
            world.spawnParticles(GluttonyHazards.ICE_CHIPS, getX(), getBodyY(0.6), getZ(), 6, 0.4, 0.6, 0.4, 0.1);
        }
        return hurt;
    }

    /**
     * In phase 1 his health never goes under half, however hard the hit (this is after his armor): it stops exactly at
     * half, and that starts phase 2. Only /kill and the void get past.
     */
    @Override
    public void setHealth(float health) {
        if (!killing && action != null && getPhase() == 1) health = Math.max(health, getMaxHealth() * phaseTwoHealth());
        super.setHealth(health);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return action == Action.MOVE;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        // No slowing, freezing or poisoning a sin
        return false;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world && age % 5 == 0) shrugOffSpells(world);
    }

    /** Ragnars Magic Mod can't put him to sleep or carry him off in a bubble. */
    private void shrugOffSpells(ServerWorld world) {
        if (MagicSpells.isSleepy(this)) MagicSpells.wake(this);
        for (Entity bubble : world.getOtherEntities(this, getBoundingBox().expand(3), e -> MagicSpells.is(e, MagicSpells.BUBBLE))) {
            Vec3d c = bubble.getBoundingBox().getCenter();
            world.spawnParticles(GluttonyHazards.ICE_CHIPS, c.x, c.y, c.z, 30, 0.8, 0.8, 0.8, 0.1);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2f, 0.8f);
            bubble.discard();
        }
    }

    @Override
    protected void tickPhase(int phase, int songTicks) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        fighters = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 240 * 240);
        if (!scaled) scaleHealth(world);
        if (rest > 0) rest--;
        if (meleeCooldown > 0) meleeCooldown--;
        actionTicks++;
        switch (action) {
            case ENTRANCE -> tickEntrance(world, songTicks);
            case MOVE -> tickMove(world);
            case EXPOSED -> tickExposed(world);
            case TRANSITION -> tickTransition(world, songTicks);
            case ICE_PUNCH -> tickPunch(world);
            case ICE_BEAM -> tickBeam(world);
            case DASH -> tickDash(world);
            case GLACIER -> tickGlacier(world);
            case ICICLES -> tickIcicles(world);
            case FROST_BREATH -> tickBreath(world);
            case LARGE_PUSH -> tickPush(world);
            case SHARDS -> tickShards(world);
            case STOMP -> tickStomp(world);
            case FREEZING -> tickFreezing(world);
            case JUMP_SLAM -> tickJumpSlam(world);
            case RISING_SPIKES -> tickSpikes(world);
            case IMPALING -> tickImpaling(world);
            case ICE_BLADE -> tickIceBlade(world);
            case BLADE_THROW -> tickBladeThrow(world);
            case ICE_CANNON -> tickIceCannon(world);
        }
        tickHazards(world);
        if (!isInvisible()) tickAura(world);
        if (Arenas.sinOf(world) == Sin.GLUTTONY) {
            tickAmbience(world);
            if (action != Action.ENTRANCE) catchStrays(world);
        }
    }

    private void scaleHealth(ServerWorld world) {
        scaled = true;
        int players = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 260 * 260).size();
        float wanted = MAX_HEALTH * LalliTimeline.healthScale(players);
        float max = Math.min(wanted, HEALTH_CAP);
        toughness = max / wanted;
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(max);
        setHealth(max);
    }

    @Override
    protected void onBeat(int phase, int beat) {
        if (action == Action.MOVE && rest <= 0 && !fighters.isEmpty() && !heldStill) startAttack(chooseAttack());
    }

    @Override
    protected void onPhaseTwo() {
        startAttack(Action.TRANSITION);
        stopSong(LalliTimeline.PIE_FADE);
    }

    // ---- Choosing attacks ----

    private Action chooseAttack() {
        List<Action> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        double near = nearestDistance();
        int band = near < 6 ? 0 : near > 15 ? 2 : 1;
        int crowd = 0;
        for (ServerPlayerEntity p : fighters) if (p.distanceTo(this) < 8) crowd++;
        if (!transitioned) {
            offer(options, weights, Action.ICE_PUNCH, new int[]{5, 2, 0}[band], 100);
            offer(options, weights, Action.STOMP, new int[]{4, 1, 0}[band], 120);
            offer(options, weights, Action.FROST_BREATH, new int[]{3, 3, 0}[band], 200);
            offer(options, weights, Action.LARGE_PUSH, new int[]{2, 0, 0}[band] + (crowd >= 3 ? 4 : 0), 320);
            offer(options, weights, Action.DASH, new int[]{0, 3, 3}[band], 160);
            offer(options, weights, Action.SHARDS, new int[]{0, 2, 4}[band], 100);
            offer(options, weights, Action.ICE_BEAM, new int[]{1, 3, 3}[band], 240);
            offer(options, weights, Action.GLACIER, new int[]{1, 2, 3}[band], 240);
            offer(options, weights, Action.ICICLES, new int[]{1, 2, 3}[band], 280);
            offer(options, weights, Action.ICE_CANNON, new int[]{0, 2, 3}[band], 320);
        } else {
            offer(options, weights, Action.ICE_PUNCH, new int[]{5, 2, 0}[band], 80);
            offer(options, weights, Action.STOMP, new int[]{2, 1, 0}[band], 120);
            offer(options, weights, Action.FROST_BREATH, new int[]{3, 3, 0}[band], 180);
            offer(options, weights, Action.LARGE_PUSH, new int[]{2, 0, 0}[band] + (crowd >= 3 ? 4 : 0), 280);
            offer(options, weights, Action.DASH, new int[]{0, 2, 3}[band], 140);
            offer(options, weights, Action.SHARDS, new int[]{0, 1, 2}[band], 100);
            offer(options, weights, Action.ICE_BEAM, new int[]{2, 5, 5}[band], 130);
            offer(options, weights, Action.GLACIER, new int[]{1, 2, 3}[band], 220);
            offer(options, weights, Action.ICICLES, new int[]{1, 2, 3}[band], 240);
            offer(options, weights, Action.RISING_SPIKES, new int[]{2, 4, 4}[band], 160);
            offer(options, weights, Action.IMPALING, new int[]{2, 3, 3}[band], 240);
            offer(options, weights, Action.FREEZING, new int[]{4, 2, 1}[band] + (crowd >= 3 ? 2 : 0), 420);
            offer(options, weights, Action.JUMP_SLAM, new int[]{1, 3, 3}[band], 360);
            offer(options, weights, Action.ICE_BLADE, new int[]{5, 3, 1}[band], 300);
            offer(options, weights, Action.ICE_CANNON, new int[]{1, 3, 4}[band], 240);
        }
        if (options.isEmpty()) return near < 6 ? Action.STOMP : Action.SHARDS;
        int total = weights.stream().mapToInt(Integer::intValue).sum(), roll = random.nextInt(total);
        for (int i = 0; i < options.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return options.get(i);
        }
        return options.get(0);
    }

    private void offer(List<Action> options, List<Integer> weights, Action attack, int weight, int minGap) {
        if (weight <= 0 || attack == lastAttack) return;
        Integer used = lastUsed.get(attack);
        if (used != null && age - used < minGap) return;
        options.add(attack);
        weights.add(weight);
    }

    void startAttack(Action attack) {
        action = attack;
        actionTicks = 0;
        repeats = 0;
        if (attack != Action.TRANSITION) {
            lastAttack = attack;
            lastUsed.put(attack, age);
        }
        exposure.clear();
        getNavigation().stop();
    }

    private void endAttack() {
        endAttack(transitioned ? REST_TWO : REST);
    }

    private void endAttack(int restTicks) {
        action = Action.MOVE;
        actionTicks = 0;
        setPoseState(POSE_IDLE);
        setNoGravity(false);
        dataTracker.set(BEAM, BEAM_OFF);
        dataTracker.set(BLADE, false);
        rest = restTicks;
    }

    /** Left open for {@code ticks}, taking extra damage. */
    private void expose(int ticks) {
        action = Action.EXPOSED;
        actionTicks = 0;
        exposedTicks = ticks;
        setPoseState(POSE_LAND);
        setNoGravity(false);
        getNavigation().stop();
    }

    /** The fighter he's picked on least recently (not {@code skip}), or null. */
    private ServerPlayerEntity pick(UUID skip) {
        ServerPlayerEntity best = null;
        int bestTick = Integer.MAX_VALUE;
        for (ServerPlayerEntity p : fighters) {
            if (p.getUuid().equals(skip)) continue;
            int when = lastPicked.getOrDefault(p.getUuid(), Integer.MIN_VALUE + random.nextInt(1000));
            if (when < bestTick) {
                bestTick = when;
                best = p;
            }
        }
        if (best == null && skip != null) return pick(null);
        if (best != null) lastPicked.put(best.getUuid(), age);
        return best;
    }

    /** Up to {@code n} different fighters, least recently picked first. */
    private List<ServerPlayerEntity> picks(int n) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        UUID skip = null;
        for (int i = 0; i < n && out.size() < fighters.size(); i++) {
            ServerPlayerEntity p = pick(skip);
            if (p == null || out.contains(p)) {
                for (ServerPlayerEntity q : fighters) if (!out.contains(q)) { p = q; break; }
            }
            if (p == null || out.contains(p)) break;
            out.add(p);
            skip = p.getUuid();
        }
        return out;
    }

    private ServerPlayerEntity nearestFighter() {
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity p : fighters) if (best == null || p.squaredDistanceTo(this) < best.squaredDistanceTo(this)) best = p;
        return best;
    }

    private double nearestDistance() {
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : fighters) best = Math.min(best, p.distanceTo(this));
        return best;
    }

    // ---- The arrival: everyone floats down to the pole, then he comes down after them ----

    private void tickEntrance(ServerWorld world, int songTicks) {
        if (startTick < 0) {
            startTick = songTicks;
            fromSky = Arenas.sinOf(world) == Sin.GLUTTONY && getY() > GluttonyArena.POLE_Y + 20;
            if (fromSky) {
                landTick = LalliTimeline.landTick(startTick);
                appearTick = landTick - LalliTimeline.APPEAR_BEFORE;
                bossLandTick = landTick + LalliTimeline.LAND_AFTER;
                List<ServerPlayerEntity> all = audience(world);
                double ring = Math.max(LalliTimeline.LANDING_RING, LalliTimeline.ringRadius(all.size()) * 1.6);
                for (int i = 0; i < all.size(); i++) {
                    ServerPlayerEntity p = all.get(i);
                    double[] slot = LalliTimeline.ringSlot(i, all.size());
                    Vec3d to = GluttonyArena.POLE.add(slot[0] * ring, 0, slot[1] * ring);
                    held.put(p.getUuid(), new Drop(p.getPos(), new Vec3d(to.x, groundY(new Vec3d(to.x, GluttonyArena.POLE_Y + 4, to.z)), to.z)));
                    Cinematics.freeze(p);
                }
                setInvisible(true);
                bossBar().setVisible(false);
            } else {
                landTick = appearTick = bossLandTick = songTicks + 1;
            }
            untouchable = true;
            setNoGravity(fromSky);
        }
        int t = songTicks;
        if (fromSky && t < landTick) floatDown(world, t);
        if (fromSky && t == landTick) setDown(world);
        if (t < appearTick) {
            if (fromSky) {
                setVelocity(Vec3d.ZERO);
                setPosition(GluttonyArena.BOSS_SPAWN.x, GluttonyArena.BOSS_SPAWN.y, GluttonyArena.BOSS_SPAWN.z);
            }
            return;
        }
        if (t == appearTick && fromSky) appear(world);
        if (t < bossLandTick) {
            double h = LalliTimeline.bossHeight(t, appearTick, bossLandTick);
            setVelocity(Vec3d.ZERO);
            setPosition(GluttonyArena.POLE.x, GluttonyArena.POLE_Y + h, GluttonyArena.POLE.z);
            setPoseState(POSE_FLOAT);
            faceYaw(getYaw() + 0.8f);
            setPitch(15f);
            descentEffects(world, t);
            return;
        }
        if (t == bossLandTick) land(world);
        int s = t - bossLandTick;
        if (s == 14) {
            // Rises out of the crouch with a roar that shakes the ice
            setPoseState(POSE_ROAR);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.HOSTILE, 4.0f, 0.4f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.5f, 0.6f);
            emit(world, ParticleTypes.SNOWFLAKE, mouthPos(), 60, 0.6, 0.4, 0.6, 0.15);
        }
        if (s >= LalliTimeline.RISE_TICKS) {
            untouchable = false;
            // His great spells aren't ready from the start
            for (Action big : new Action[]{Action.ICICLES, Action.LARGE_PUSH, Action.GLACIER, Action.ICE_BEAM}) lastUsed.put(big, age - 120);
            endAttack(16);
        }
    }

    /** Frozen, floating down out of space towards the pole while the sky turns on the strums. */
    private void floatDown(ServerWorld world, int t) {
        double u = LalliTimeline.floatProgress(t, startTick, landTick);
        double h = LalliTimeline.floatHeight(t, startTick, landTick), next = LalliTimeline.floatHeight(t + 1, startTick, landTick);
        for (ServerPlayerEntity p : audience(world)) {
            Drop drop = held.get(p.getUuid());
            if (drop == null) continue;
            Vec3d at = drop.from.lerp(drop.to, u);
            double y = drop.to.y + h;
            p.setVelocity((at.x - p.getX()) * 0.3, next - h + (y - p.getY()) * 0.4, (at.z - p.getZ()) * 0.3);
            p.velocityModified = true;
            p.fallDistance = 0;
            if (!p.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, landTick - t + 20, 0, false, false, false));
            }
            if (t % 2 == 0) {
                // Stars streaking past, and frost glittering in the dark
                for (int i = 0; i < 2; i++) {
                    double a = random.nextDouble() * MathHelper.TAU, r = 2 + random.nextDouble() * 9;
                    Vec3d s = p.getPos().add(Math.cos(a) * r, -3 - random.nextDouble() * 10, Math.sin(a) * r);
                    world.spawnParticles(p, ParticleTypes.END_ROD, true, s.x, s.y, s.z, 0, 0, 1, 0, 0.6);
                }
                world.spawnParticles(p, ParticleTypes.SNOWFLAKE, true, p.getX(), p.getY() - 2, p.getZ(), 2, 4, 3, 4, 0.01);
            }
        }
        if ((t - startTick) % 60 == 30) {
            for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.12f, 0.5f);
        }
    }

    /** 25 s in: everyone sets down on the pole, softly, and can move again. */
    private void setDown(ServerWorld world) {
        for (ServerPlayerEntity p : audience(world)) {
            Drop drop = held.get(p.getUuid());
            if (drop != null) {
                p.teleport(world, drop.to.x, drop.to.y, drop.to.z, p.getYaw(), p.getPitch());
                p.removeStatusEffect(StatusEffects.SLOW_FALLING);
                p.fallDistance = 0;
                world.spawnParticles(GluttonyHazards.SNOW_PUFF, drop.to.x, drop.to.y + 0.1, drop.to.z, 30, 0.5, 0.1, 0.5, 0.1);
                world.playSound(null, drop.to.x, drop.to.y, drop.to.z, SoundEvents.BLOCK_POWDER_SNOW_FALL, SoundCategory.PLAYERS, 1.0f, 0.8f);
            }
            Cinematics.unfreeze(p);
        }
        held.clear();
    }

    /** 20 s in: a cold light high over the pole, and he's there. */
    private void appear(ServerWorld world) {
        setInvisible(false);
        bossBar().setVisible(true);
        Vec3d c = getPos().add(0, getHeight() / 2, 0);
        emit(world, ParticleTypes.FLASH, c, 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.END_ROD, c, 80, 1.5, 2, 1.5, 0.1);
        emit(world, ParticleTypes.SNOWFLAKE, c, 120, 2, 2.5, 2, 0.08);
        for (ServerPlayerEntity p : audience(world)) {
            p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.5f);
            p.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.5f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 0.8f, 0.5f);
        }
    }

    private void descentEffects(ServerWorld world, int t) {
        Vec3d c = getPos();
        emit(world, ParticleTypes.SNOWFLAKE, c.add(0, 1.5, 0), 4, 0.6, 1.2, 0.6, 0.02);
        emit(world, GluttonyHazards.FROST, c.add(0, 1.5, 0), 3, 0.5, 1.2, 0.5, 0);
        if (t % 3 == 0) emit(world, ParticleTypes.END_ROD, c.add(0, 3.5, 0), 2, 0.3, 0.6, 0.3, 0.05);
        // His shadow on the ice, closing in as he comes
        if (bossLandTick - t <= 80 && t % 2 == 0) {
            double r = 2 + (bossLandTick - t) / 16.0;
            GluttonyHazards.ring(world, GluttonyHazards.FROST, new Vec3d(GluttonyArena.POLE.x, groundY(GluttonyArena.POLE.add(0, 4, 0)), GluttonyArena.POLE.z), r, 36, t * 0.05, 0.1);
        }
        if (t % 40 == 0) {
            for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.0f, 0.5f);
        }
    }

    /** 35 s in: he hits the pole, and a great wave of snow throws everyone back. It hurts nobody. */
    private void land(ServerWorld world) {
        setNoGravity(false);
        Vec3d at = fromSky ? new Vec3d(GluttonyArena.POLE.x, groundY(GluttonyArena.POLE.add(0, 4, 0)), GluttonyArena.POLE.z) : ground(getPos());
        setPosition(at.x, at.y, at.z);
        setVelocity(Vec3d.ZERO);
        setPoseState(POSE_LAND);
        Vec3d c = getPos();
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.unfreeze(p);
            Cinematics.flash(p, (float) (0.7 * Math.max(0.3, 1 - p.getPos().distanceTo(c) / 80)), 26);
        }
        // Out of the sky, a great wave; set down anywhere else (summoned, tests), just a ring of frost
        if (fromSky) hazards.add(new GluttonyHazards.Shockwave(c, 0.9, 40, 0, 2.6, 0.65, 0, true));
        hazards.add(GluttonyHazards.Shockwave.visual(c, 0.6, 24));
        emit(world, ParticleTypes.FLASH, c.add(0, 1, 0), 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.EXPLOSION_EMITTER, c.add(0, 0.5, 0), 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.GUST_EMITTER_LARGE, c.add(0, 0.5, 0), 1, 0, 0, 0, 0);
        emit(world, GluttonyHazards.SNOW_PUFF, c.add(0, 0.5, 0), 300, 4, 0.5, 4, 0.4);
        emit(world, ParticleTypes.SNOWFLAKE, c.add(0, 1, 0), 200, 3, 1.5, 3, 0.4);
        emit(world, GluttonyHazards.ICE_CHIPS, c.add(0, 0.5, 0), 120, 2, 0.5, 2, 0.3);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    // ---- Half health: a mini cutscene, then he flies up into the sky to SIRENS, and on the beat drop the fight goes to the snowfield ----

    private void tickTransition(ServerWorld world, int songTicks) {
        int t = actionTicks;
        if (t <= LalliTimeline.CUTSCENE) {
            tickCutscene(world, t);
            return;
        }
        // From here on, timed by SIRENS itself (started as the cutscene ends)
        int s = songTicks;
        if (s < LalliTimeline.MOVE_AT) {
            double h = LalliTimeline.ascent(s, liftoff.y - GluttonyArena.POLE_Y);
            double drift = LalliTimeline.ease(Math.min(1, s / (double) LalliTimeline.ASCENT_DRIFT));
            Vec3d at = new Vec3d(MathHelper.lerp(drift, liftoff.x, GluttonyArena.POLE.x), GluttonyArena.POLE_Y + h,
                    MathHelper.lerp(drift, liftoff.z, GluttonyArena.POLE.z));
            setVelocity(Vec3d.ZERO);
            setPosition(at.x, at.y, at.z);
            faceYaw(getYaw() + 0.5f);
            setPitch(-20f);
            setPoseState(POSE_FLOAT);
            // Light streaming up past him into the stars, snow rising off the pole after him
            double ground = groundY(GluttonyArena.POLE.add(0, 4, 0));
            if (s % 2 == 0) {
                double y = ground + random.nextDouble() * (at.y - ground + 40);
                emit(world, ParticleTypes.END_ROD, new Vec3d(at.x, y, at.z), 1, 0.3, 0.3, 0.3, 0.02);
                emit(world, ParticleTypes.SNOWFLAKE, at.add(0, 1.5, 0), 3, 1.5, 1.5, 1.5, 0.02);
                emit(world, GluttonyHazards.RIME, at.add(0, -0.5, 0), 2, 0.4, 0.2, 0.4, 0);
            }
            if (s % 40 == 20) {
                hazards.add(GluttonyHazards.Shockwave.visual(new Vec3d(GluttonyArena.POLE.x, ground, GluttonyArena.POLE.z), 0.45, 30));
                for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.2f, 0.5f);
            }
        }
        if (s == LalliTimeline.WHITE_AT) {
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.freeze(p);
                Cinematics.screen(p, BossScreenPayload.WHITE, 0, LalliTimeline.WHITE_FADE);
                p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.5f, 0.5f);
                p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
        }
        if (s == LalliTimeline.MOVE_AT) moveToSnowfield(world);
        if (s == LalliTimeline.SWITCH_TICK) {
            // The beat drop: the white lifts on him in the snow, his eyes burning, and a blast of cold
            setPoseState(POSE_ROAR);
            Vec3d c = getPos();
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.unfreeze(p);
                Cinematics.screen(p, BossScreenPayload.CLEAR, 0, LalliTimeline.CLEAR_TICKS);
            }
            hazards.add(new GluttonyHazards.Shockwave(c, 1.1, 24, 0, 1.4, 0.5, 0, true));
            emit(world, ParticleTypes.SNOWFLAKE, c.add(0, 2, 0), 250, 3, 2, 3, 0.4);
            emit(world, GluttonyHazards.DEEP_ICE, c.add(0, 2, 0), 120, 1.5, 2, 1.5, 0);
            emit(world, ParticleTypes.GUST_EMITTER_LARGE, c.add(0, 0.5, 0), 1, 0, 0, 0, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 4.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        }
        if (s >= LalliTimeline.SWITCH_TICK + 30) {
            untouchable = false;
            for (Action big : new Action[]{Action.FREEZING, Action.JUMP_SLAM, Action.IMPALING}) lastUsed.put(big, age - 200);
            endAttack(6);
        }
    }

    /**
     * The mini cutscene at half health, everyone held still. The ice cracks and breaks off him, he's brought to his
     * knees and Apple Pie fades away; in the quiet he gets up and looks up at the stars, a thread of light comes down to
     * him, he spreads his arms, and as SIRENS begins he lifts off.
     */
    private void tickCutscene(ServerWorld world, int t) {
        Vec3d c = getPos().add(0, getHeight() / 2, 0);
        if (t == 1) {
            untouchable = true;
            clearAttacks(world);
            getNavigation().stop();
            setVelocity(Vec3d.ZERO);
            setPoseState(POSE_LAND);
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.freeze(p);
                Cinematics.flash(p, 0.55f, 24);
            }
            emit(world, GluttonyHazards.ICE_CHIPS, c, 120, 0.7, 1.2, 0.7, 0.25);
            emit(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, GluttonyHazards.BLUE), c, 60, 0.6, 1.0, 0.6, 0.2);
            emit(world, ParticleTypes.SNOWFLAKE, c, 80, 1.0, 1.2, 1.0, 0.15);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_POLAR_BEAR_HURT, SoundCategory.HOSTILE, 3.0f, 0.4f);
        }
        stand();
        if (t < LalliTimeline.STAND_AT) {
            // On his knees, shaking, the ice flaking off him while the song dies away
            if (t % 3 == 0) emit(world, GluttonyHazards.ICE_CHIPS, c, 6, 0.6, 1.0, 0.6, 0.08);
            if (t % 2 == 0) emit(world, ParticleTypes.SNOWFLAKE, getPos().add(0, 0.3, 0), 3, 1.2, 0.1, 1.2, 0.02);
            if (t % 10 == 5) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, SoundCategory.HOSTILE, 1.6f, 0.5f + t * 0.015f);
            if (t % 20 == 10) hazards.add(GluttonyHazards.Shockwave.visual(ground(getPos()), 0.35, 10));
            return;
        }
        if (t == LalliTimeline.STAND_AT) {
            // Quiet. He gets up and looks up into the dark
            setPoseState(POSE_ROAR);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.HOSTILE, 2.5f, 0.4f);
            for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.HOSTILE, 0.15f, 0.5f);
        }
        if (t < LalliTimeline.LIFT_AT) {
            // A thread of light comes down to him out of the stars
            setPitch(-40f);
            for (int i = 0; i < 3; i++) emit(world, ParticleTypes.END_ROD, getPos().add(0, 3 + random.nextDouble() * 40, 0), 1, 0.1, 0.1, 0.1, 0.01);
            return;
        }
        if (t == LalliTimeline.LIFT_AT) {
            setPoseState(POSE_FLOAT);
            halo = new GluttonyHazards.Halo(world, getPos(), 12);
            hazards.add(halo);
            emit(world, ParticleTypes.END_ROD, c, 60, 0.8, 1.2, 0.8, 0.1);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (t < LalliTimeline.CUTSCENE) {
            setPitch(-20f);
            if (t % 2 == 0) emit(world, ParticleTypes.SNOWFLAKE, c, 4, 1.2, 1.2, 1.2, 0.03);
            return;
        }
        // SIRENS: he lifts off; everyone can move again (and watch), but nothing touches him
        liftoff = getPos();
        setNoGravity(true);
        playSong(PHASE_TWO, LalliTimeline.SIRENS_FADE);
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.unfreeze(p);
            p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        emit(world, ParticleTypes.FLASH, c, 1, 0, 0, 0, 0);
        emit(world, GluttonyHazards.SNOW_PUFF, getPos().add(0, 0.2, 0), 60, 1.2, 0.1, 1.2, 0.2);
    }

    /** Under the white: everyone, and he, are taken to the snowfield. */
    private void moveToSnowfield(ServerWorld world) {
        List<ServerPlayerEntity> all = audience(world);
        if (halo != null) {
            halo.burst(world);
            hazards.remove(halo);
            halo = null;
        }
        Vec3d c = GluttonyArena.SNOW_CENTER;
        double ring = Math.max(GluttonyArena.SNOW_RING, LalliTimeline.ringRadius(all.size()) * 1.6);
        for (int i = 0; i < all.size(); i++) {
            ServerPlayerEntity p = all.get(i);
            double[] slot = LalliTimeline.ringSlot(i, all.size());
            double x = c.x + slot[0] * ring, z = c.z + slot[1] * ring;
            float yaw = (float) (MathHelper.atan2(c.z - z, c.x - x) * (180 / Math.PI)) - 90f;
            p.teleport(world, x, c.y, z, yaw, 0f);
            p.fallDistance = 0;
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
        }
        setNoGravity(false);
        getNavigation().stop();
        refreshPositionAndAngles(c.x, c.y, c.z, 180f, 0f);
        faceYaw(180f);
        transitioned = true;
        dataTracker.set(EYES, true);
        getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(SPEED * 1.2);
    }

    // ---- Between attacks: walking after whoever he's picked ----

    private void tickMove(ServerWorld world) {
        if (heldStill) {
            stand();
            return;
        }
        ServerPlayerEntity target = fighter(focus);
        if (target == null || --focusTicks <= 0) {
            target = pick(null);
            focus = target == null ? null : target.getUuid();
            focusTicks = 140;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        setTarget(target);
        getLookControl().lookAt(target, 20f, 20f);
        double reach = 1.8 + getWidth();
        if (age % 10 == 0 || getNavigation().isIdle()) {
            if (squaredDistanceTo(target) > (reach - 0.6) * (reach - 0.6)) getNavigation().startMovingTo(target, 1.0);
            else getNavigation().stop();
        }
        if (meleeCooldown <= 0 && squaredDistanceTo(target) < reach * reach) {
            swingHand(Hand.MAIN_HAND);
            if (punch(target, 9)) launch(target, target.getPos().subtract(getPos()), 1.0, 0.4);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.spawnParticles(GluttonyHazards.ICE_CHIPS, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
            meleeCooldown = transitioned ? 20 : 26;
        }
        // Heavy footfalls that crunch the snow
        if (getVelocity().horizontalLengthSquared() > 0.002 && age % 12 == 0) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_SNOW_STEP, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.spawnParticles(GluttonyHazards.SNOW_PUFF, getX(), getY() + 0.1, getZ(), 6, 0.4, 0.05, 0.4, 0.05);
        }
    }

    private void tickExposed(ServerWorld world) {
        stand();
        if (actionTicks % 4 == 0) world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getHeight() + 0.2, getZ(), 3, 0.4, 0.1, 0.4, 0.02);
        if (actionTicks >= exposedTicks) endAttack();
    }

    // ---- Phase 1 attacks ----

    private void tickPunch(ServerWorld world) {
        int windup = transitioned ? 15 : 22, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = nearestFighter();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_PUNCH_CHARGE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.5f, 1.3f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        Vec3d fist = fistPos();
        if (t < windup) {
            if (target != null) turnTowards(target.getPos(), 14f);
            // Frost gathering on his fist
            for (int i = 0; i < 3; i++) {
                Vec3d off = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().multiply(1.6);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, fist.x + off.x, fist.y + off.y, fist.z + off.z, 0, -off.x, -off.y, -off.z, 0.08);
            }
            world.spawnParticles(GluttonyHazards.FROST, fist.x, fist.y, fist.z, 2, 0.15, 0.15, 0.15, 0);
            if (t % 4 == 0) world.playSound(null, fist.x, fist.y, fist.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.2f, 0.6f + t * 0.05f);
            return;
        }
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        if (t == windup) {
            setPoseState(POSE_PUNCH);
            swingHand(Hand.MAIN_HAND);
            setVelocity(forward.x * (transitioned ? 1.3 : 1.0), 0.1, forward.z * (transitioned ? 1.3 : 1.0));
            velocityModified = true;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t == windup + 2) {
            Vec3d hit = getPos().add(0, 1.5, 0).add(forward.multiply(2.2));
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getBoundingBox().getCenter().subtract(getPos().add(0, 1.5, 0));
                if (p.getBoundingBox().getCenter().distanceTo(hit) > 3.4 || to.normalize().dotProduct(forward) < 0.2) continue;
                if (punch(p, 14)) {
                    launch(p, forward, transitioned ? 2.6 : 2.2, 0.55);
                    freeze(p, transitioned ? 60 : 45, 7, 2);
                }
            }
            world.spawnParticles(GluttonyHazards.ICE_CHIPS, hit.x, hit.y, hit.z, 40, 0.6, 0.6, 0.6, 0.3);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, hit.x, hit.y, hit.z, 40, 0.5, 0.5, 0.5, 0.3);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
            world.playSound(null, hit.x, hit.y, hit.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ITEM_MACE_SMASH_AIR, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (t >= windup + 14) endAttack();
    }

    private void tickBeam(ServerWorld world) {
        int charge = transitioned ? 18 : 24, fire = transitioned ? 56 : 40, t = actionTicks;
        double range = 40, turn = transitioned ? 0.3 : 0.22;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            aim = target.getBoundingBox().getCenter();
            setPoseState(POSE_BEAM);
            dataTracker.set(BEAM, BEAM_CHARGE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 2.0f, 1.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.2f, 1.8f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        Vec3d hand = handPos(1f);
        if (target != null) {
            Vec3d want = target.getBoundingBox().getCenter();
            double speed = t <= charge ? 0.6 : turn;
            Vec3d to = want.subtract(aim);
            aim = to.length() <= speed ? want : aim.add(to.normalize().multiply(speed));
        }
        turnTowards(aim, 8f);
        Vec3d dir = aim.subtract(hand).normalize();
        Vec3d end = hand.add(dir.multiply(range));
        var hit = world.raycast(new RaycastContext(hand, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) end = hit.getPos();
        dataTracker.set(BEAM_END, end.toVector3f());
        if (t <= charge) {
            // Little bits of ice gathering at his hand, and a dotted line where it'll go
            world.spawnParticles(GluttonyHazards.ICE_CHIPS, hand.x, hand.y, hand.z, 2, 0.2, 0.2, 0.2, 0.02);
            if (t % 2 == 0) GluttonyHazards.line(world, ParticleTypes.SNOWFLAKE, hand, end, 0.9);
            if (t % 4 == 0) world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.0f, 0.9f + 1.1f * t / charge);
            if (t == charge) {
                dataTracker.set(BEAM, BEAM_FIRE);
                world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0f, 1.8f);
                world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.5f, 1.6f);
                world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.5f, 1.2f);
            }
            return;
        }
        if (t <= charge + fire) {
            for (ServerPlayerEntity p : fighters) {
                if (p.getBoundingBox().expand(0.3).raycast(hand, end).isEmpty()) continue;
                if (strike(p, 5)) freeze(p, 30, 0, 0);
            }
            if (t % 2 == 0) world.spawnParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.08);
            world.spawnParticles(GluttonyHazards.ICE_CHIPS, end.x, end.y, end.z, 2, 0.2, 0.2, 0.2, 0.1);
            if (t % 10 == 0) world.playSound(null, end.x, end.y, end.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.2f, 1.6f);
            if (t % 6 == 0) world.playSound(null, end.x, end.y, end.z, SoundEvents.BLOCK_POWDER_SNOW_PLACE, SoundCategory.HOSTILE, 0.8f, 1.5f);
            return;
        }
        dataTracker.set(BEAM, BEAM_OFF);
        world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.5f, 1.6f);
        endAttack();
    }

    private void tickDash(ServerWorld world) {
        int windup = transitioned ? 10 : 14, dash = 11, t = actionTicks, dashes = transitioned ? 2 : 1;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            dashDir = to.lengthSquared() > 1e-4 ? to.normalize() : Vec3d.fromPolar(0, getYaw());
            faceTowards(getPos().add(dashDir));
            setPoseState(POSE_DASH);
            exposure.clear();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_PREPARE_RAM, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t < windup) {
            stand();
            if (t % 2 == 0) {
                // The way he'll come, in frost along the ground
                Vec3d from = ground(getPos());
                GluttonyHazards.line(world, GluttonyHazards.FROST, from.add(0, 0.15, 0), from.add(dashDir.multiply(dash * 1.6)).add(0, 0.15, 0), 0.7);
            }
            return;
        }
        if (t < windup + dash) {
            setVelocity(dashDir.x * 1.6, getVelocity().y, dashDir.z * 1.6);
            velocityModified = true;
            world.spawnParticles(GluttonyHazards.SNOW_PUFF, getX(), getY() + 0.2, getZ(), 6, 0.4, 0.1, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1.5, getZ(), 4, 0.4, 0.8, 0.4, 0.05);
            if (t == windup) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_RAM_IMPACT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            for (ServerPlayerEntity p : fighters) {
                if (exposure.containsKey(p.getUuid()) || p.distanceTo(this) > 2.6) continue;
                exposure.put(p.getUuid(), 1);
                if (punch(p, 11)) {
                    Vec3d side = p.getPos().subtract(getPos()).multiply(1, 0, 1);
                    launch(p, dashDir.add(side.normalize().multiply(0.6)), 2.1, 0.6);
                    chill(p, 40);
                }
            }
            if (horizontalCollision && t > windup + 2) actionTicks = windup + dash - 1;
            return;
        }
        stand();
        if (t >= windup + dash + 8) {
            if (++repeats < dashes) {
                actionTicks = 0;
                return;
            }
            endAttack();
        }
    }

    private void tickGlacier(ServerWorld world) {
        int charge = transitioned ? 20 : 26, t = actionTicks, throwsLeft = transitioned ? 2 : 1;
        int start = repeats * (charge + 8);
        int s = t - start;
        if (s == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            aim = target.getPos().add(0, 1, 0);
            setPoseState(POSE_POINT);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.5f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.2f, 0.6f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        Vec3d hand = handPos(1f);
        if (s < charge) {
            // Pointing it at you: set a moment before it's thrown, so you can still get out of the way
            if (target != null && s < charge - 6) aim = target.getPos().add(0, 1, 0);
            turnTowards(aim, 10f);
            Vec3d dir = aim.subtract(hand).normalize();
            if (s % 3 == 0) GluttonyHazards.line(world, ParticleTypes.SNOWFLAKE, hand, hand.add(dir.multiply(GluttonyHazards.Glacier.BURST)), 1.2);
            world.spawnParticles(GluttonyHazards.PACKED_CHIPS, hand.x, hand.y, hand.z, 2, 0.15 + s * 0.01, 0.15 + s * 0.01, 0.15 + s * 0.01, 0);
            if (s % 5 == 0) world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 1.0f, 0.5f + s * 0.03f);
            return;
        }
        if (s == charge) {
            Vec3d dir = aim.subtract(hand).normalize();
            hazards.add(new GluttonyHazards.Glacier(world, hand, dir, 10, transitioned ? 80 : 70));
            swingHand(Hand.MAIN_HAND);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.HOSTILE, 2.0f, 0.4f);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_WIND_CHARGE_THROW, SoundCategory.HOSTILE, 1.5f, 0.6f);
            setPoseState(POSE_PUNCH);
        }
        if (s >= charge + 8) {
            if (++repeats < throwsLeft) return;
            endAttack();
        }
    }

    private void tickIcicles(ServerWorld world) {
        int windup = 14, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_RAISE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_POWDER_SNOW_PLACE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        stand();
        if (t < windup) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getHeight() + 1, getZ(), 4, 0.6, 0.4, 0.6, 0.1);
            return;
        }
        if (t == windup) {
            int max = transitioned ? 10 : 6;
            List<ServerPlayerEntity> targets = picks(LalliTimeline.perPlayer(fighters.size(), max));
            int i = 0;
            float direct = transitioned ? 24 : 20, splash = transitioned ? 9 : 7;
            for (ServerPlayerEntity p : targets) hazards.add(new GluttonyHazards.Icicle(p.getUuid(), p.getPos(), i++ * 3, direct, splash));
            // Alone or in a pair, a couple more round you so there's nowhere simply to stand
            if (transitioned && targets.size() <= 2) {
                for (ServerPlayerEntity p : targets) {
                    for (int k = 0; k < 2; k++) {
                        double a = random.nextDouble() * MathHelper.TAU;
                        hazards.add(new GluttonyHazards.Icicle(null, p.getPos().add(Math.cos(a) * 4, 0, Math.sin(a) * 4), 6 + i++ * 3, direct, splash));
                    }
                }
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t >= windup + 24) endAttack();
    }

    private void tickBreath(ServerWorld world) {
        int inhale = 14, breath = transitioned ? 60 : 50, t = actionTicks;
        double range = transitioned ? 13 : 11, half = Math.toRadians(transitioned ? 28 : 24);
        float turn = transitioned ? 4.5f : 3f;
        if (t == 1) {
            ServerPlayerEntity target = nearestFighter();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            faceTowards(target.getPos());
            breathYaw = getYaw();
            setPoseState(POSE_BREATH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target != null) {
            float want = (float) (MathHelper.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180 / Math.PI)) - 90f;
            breathYaw += MathHelper.clamp(MathHelper.wrapDegrees(want - breathYaw), -turn, turn);
        }
        faceYaw(breathYaw);
        Vec3d mouth = mouthPos();
        if (t < inhale) {
            // Drawing the cold in
            for (int i = 0; i < 2; i++) {
                Vec3d off = new Vec3d(random.nextGaussian(), random.nextGaussian() * 0.5, random.nextGaussian()).multiply(1.5);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, mouth.x + off.x, mouth.y + off.y, mouth.z + off.z, 0, -off.x, -off.y, -off.z, 0.07);
            }
            return;
        }
        if (t < inhale + breath) {
            Vec3d dir = Vec3d.fromPolar(12, breathYaw);
            for (int i = 0; i < 6; i++) {
                Vec3d v = dir.add(random.nextGaussian() * 0.16, random.nextGaussian() * 0.1, random.nextGaussian() * 0.16).normalize();
                world.spawnParticles(i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.SNOWFLAKE, mouth.x, mouth.y, mouth.z, 0, v.x, v.y, v.z, 0.55 + random.nextDouble() * 0.35);
            }
            if (t % 2 == 0) world.spawnParticles(ParticleTypes.ITEM_SNOWBALL, mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z, 0.7);
            if (t % 5 == 0) {
                world.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.BLOCK_POWDER_SNOW_STEP, SoundCategory.HOSTILE, 2.0f, 0.5f);
                world.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.ENTITY_BREEZE_IDLE_GROUND, SoundCategory.HOSTILE, 1.5f, 0.5f);
            }
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getBoundingBox().getCenter().subtract(mouth);
                double d = to.length();
                if (d > range || d < 0.01) continue;
                double angle = Math.acos(MathHelper.clamp(to.multiply(1 / d).dotProduct(dir), -1, 1));
                if (angle > half || blocked(mouth, p.getBoundingBox().getCenter())) continue;
                int n = exposure.merge(p.getUuid(), 1, Integer::sum);
                chill(p, 20);
                if (n % 8 == 0) crush(p, 3);
                if (n >= 16 && !isFrozen(p)) {
                    freeze(p, 40, 0, 0);
                    exposure.put(p.getUuid(), 0);
                }
            }
            return;
        }
        if (t >= inhale + breath + 10) endAttack();
    }

    private void tickPush(ServerWorld world) {
        int charge = transitioned ? 26 : 36, t = actionTicks;
        double radius = 16;
        if (t == 1) {
            setPoseState(POSE_PUSH_CHARGE);
            hazards.add(new GluttonyHazards.Circle(ground(getPos()), radius, charge, GluttonyHazards.FROST));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        stand();
        if (t < charge) {
            // The snow drawn in to him from as far as he'll reach
            Vec3d c = ground(getPos());
            GluttonyHazards.ring(world, ParticleTypes.SNOWFLAKE, c, radius * (1 - t / (double) charge) + 1, 40, t * 0.2, 0.4 + t * 0.04);
            world.spawnParticles(GluttonyHazards.DEEP_ICE, getX(), getY() + 1.5, getZ(), 3, 0.5, 0.8, 0.5, 0);
            if (t % 6 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.5f, 0.5f + t * 0.03f);
            return;
        }
        if (t == charge) {
            setPoseState(POSE_PUSH);
            Vec3d c = getPos();
            for (ServerPlayerEntity p : fighters) {
                double d = GluttonyHazards.flatDistance(p.getPos(), c);
                if (d > radius || Math.abs(p.getY() - c.y) > 6) continue;
                crush(p, 6);
                launch(p, p.getPos().subtract(c), 3.6 * (1 - d / (radius + 4)) + 1.2, 0.9);
                chill(p, 60);
            }
            hazards.add(new GluttonyHazards.Shockwave(c, 1.6, radius + 4, 0, 0, 0, 0, true));
            emit(world, ParticleTypes.GUST_EMITTER_LARGE, c.add(0, 1, 0), 1, 0, 0, 0, 0);
            emit(world, ParticleTypes.SONIC_BOOM, c.add(0, 1.5, 0), 1, 0, 0, 0, 0);
            emit(world, ParticleTypes.SNOWFLAKE, c.add(0, 1.5, 0), 150, 2, 1, 2, 0.6);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 0.8f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t >= charge + 18) endAttack();
    }

    private void tickShards(ServerWorld world) {
        int windup = 10, t = actionTicks, volleys = transitioned ? 2 : 1;
        if (t == 1) {
            setPoseState(POSE_BEAM);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 1.5f, 1.2f);
        }
        stand();
        ServerPlayerEntity first = nearestFighter();
        if (first != null) turnTowards(first.getPos(), 12f);
        int s = t - windup;
        if (s >= 0 && s % 12 == 0 && s / 12 < volleys) {
            Vec3d hand = handPos(1f);
            for (ServerPlayerEntity p : picks(LalliTimeline.perPlayer(fighters.size(), 3))) {
                Vec3d to = p.getBoundingBox().getCenter().add(p.getVelocity().multiply(6, 0, 6)).subtract(hand);
                float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z)), pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalLength()));
                for (int k = -2; k <= 2; k++) {
                    Vec3d dir = Vec3d.fromPolar(pitch, yaw + k * 7f);
                    hazards.add(new GluttonyHazards.Shard(world, hand, dir, 5));
                }
            }
            swingHand(Hand.MAIN_HAND);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.5f, 1.4f);
        }
        if (s >= 12 * volleys) endAttack();
    }

    private void tickStomp(ServerWorld world) {
        int windup = 10, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_STOMP);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        stand();
        if (t == windup) {
            setPoseState(POSE_LAND);
            Vec3d c = ground(getPos());
            hazards.add(new GluttonyHazards.Shockwave(c, 0.55, transitioned ? 15 : 12, 6, 0.6, 0.35, 60, false));
            world.spawnParticles(GluttonyHazards.SNOW_PUFF, c.x, c.y + 0.2, c.z, 60, 1.5, 0.1, 1.5, 0.2);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t >= windup + 14) endAttack();
    }

    // ---- Phase 2 attacks ----

    private void tickFreezing(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            hazards.add(new GluttonyHazards.Nova(world, ground(getPos()), desperate() ? 18 : 16, 12, 70));
        }
        stand();
        if (t >= GluttonyHazards.Nova.GROW + GluttonyHazards.Nova.HOLD + 14) endAttack();
    }

    private void tickJumpSlam(ServerWorld world) {
        int crouch = 10, rise = 18, hover = 14, drop = 6, t = actionTicks;
        double height = 20;
        if (t == 1) {
            setPoseState(POSE_STOMP);
            spot = clusterSpot();
            if (spot == null) {
                endAttack();
                return;
            }
            // The Tome of Earthquake's fissures start cracking out where he's going to land
            quakeCharging = MagicSpells.earthquakeCharge(world, getId(), spot, random.nextLong());
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0f, 0.7f);
        }
        if (t < crouch) {
            stand();
            return;
        }
        if (t == crouch) {
            aim = getPos();
            setNoGravity(true);
            setPoseState(POSE_JUMP);
            hazards.add(new GluttonyHazards.Circle(spot, 4.5, rise + hover + drop, GluttonyHazards.DEEP_ICE));
            hazards.add(new GluttonyHazards.Circle(spot, GluttonyHazards.Quake.RADIUS, rise + hover + drop, ParticleTypes.SNOWFLAKE));
            world.spawnParticles(GluttonyHazards.SNOW_PUFF, getX(), getY() + 0.2, getZ(), 60, 1.2, 0.1, 1.2, 0.2);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t < crouch + rise) {
            double u = (t - crouch) / (double) rise;
            Vec3d xz = aim.lerp(spot, LalliTimeline.ease(u));
            setVelocity(Vec3d.ZERO);
            setPosition(xz.x, MathHelper.lerp(u, aim.y, spot.y) + height * Math.sin(u * Math.PI / 2), xz.z);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 3, 0.5, 0.3, 0.5, 0.02);
            return;
        }
        if (t < crouch + rise + hover) {
            setVelocity(Vec3d.ZERO);
            setPosition(spot.x, spot.y + height, spot.z);
            setPoseState(POSE_RAISE);
            emit(world, GluttonyHazards.DEEP_ICE, getPos().add(0, 1.5, 0), 4, 0.6, 1, 0.6, 0);
            return;
        }
        if (t < crouch + rise + hover + drop) {
            double u = (t - crouch - rise - hover + 1) / (double) drop;
            setVelocity(Vec3d.ZERO);
            setPosition(spot.x, spot.y + height * (1 - u), spot.z);
            setPoseState(POSE_LAND);
            return;
        }
        if (t == crouch + rise + hover + drop) {
            setNoGravity(false);
            setPosition(spot.x, spot.y, spot.z);
            Vec3d c = spot;
            for (ServerPlayerEntity p : fighters) {
                double d = GluttonyHazards.flatDistance(p.getPos(), c);
                if (d > 4.5 || Math.abs(p.getY() - c.y) > 4) continue;
                crush(p, 16);
                launch(p, p.getPos().subtract(c), 1.4, 0.8);
            }
            hazards.add(new GluttonyHazards.Quake(c, groundState(), 14, 7, random.nextLong(), getId(), quakeCharging));
            quakeCharging = false;
            emit(world, ParticleTypes.EXPLOSION_EMITTER, c.add(0, 0.5, 0), 1, 0, 0, 0, 0);
            emit(world, GluttonyHazards.SNOW_PUFF, c.add(0, 0.4, 0), 200, 3, 0.3, 3, 0.3);
            emit(world, GluttonyHazards.ICE_CHIPS, c.add(0, 0.4, 0), 80, 2, 0.3, 2, 0.3);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.5f, 0.7f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.5f, 0.6f);
            for (ServerPlayerEntity p : audience(world)) {
                if (p.distanceTo(this) < 40) Cinematics.flash(p, 0.25f, 10);
            }
            // The landing jars him: open for a moment
            expose(34);
        }
    }

    private void tickSpikes(ServerWorld world) {
        int windup = 12, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_STOMP);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        stand();
        ServerPlayerEntity first = nearestFighter();
        if (first != null && t < windup) turnTowards(first.getPos(), 12f);
        if (t == windup) {
            setPoseState(POSE_LAND);
            Vec3d c = ground(getPos());
            int rows = Math.min(4, 1 + fighters.size() / 3);
            for (ServerPlayerEntity p : picks(rows)) hazards.add(new GluttonyHazards.SpikeRow(c, p.getPos().subtract(c), 12));
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 2.0f, 0.7f);
        }
        if (t >= windup + 22) endAttack();
    }

    private void tickImpaling(ServerWorld world) {
        int windup = 14, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.4f);
        }
        stand();
        if (t < windup) {
            world.spawnParticles(GluttonyHazards.DEEP_ICE, getX(), getY() + 0.2, getZ(), 4, 1, 0.05, 1, 0);
            return;
        }
        if (t == windup) {
            int i = 0;
            for (ServerPlayerEntity p : picks(LalliTimeline.perPlayer(fighters.size(), 10))) {
                hazards.add(new GluttonyHazards.Impale(p.getUuid(), p.getPos(), i++ * 2, 18, 8));
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t >= windup + 20) endAttack();
    }

    /**
     * Ice Blade: a great blade of ice grows in his hand, and he cuts at you, again and again, stepping in with every
     * cut. Then straight into a Blade Throw.
     */
    private void tickIceBlade(ServerWorld world) {
        int form = 14, cuts = 5, every = 8, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = nearestFighter();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            dataTracker.set(BLADE, true);
            setPoseState(POSE_RAISE);
            Vec3d hand = handPos(1f);
            emit(world, GluttonyHazards.ICE_CHIPS, hand, 30, 0.3, 0.6, 0.3, 0.1);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target == null) target = nearestFighter();
        if (t < form) {
            // The blade freezing together out of the air
            Vec3d hand = handPos(1f);
            for (int i = 0; i < 2; i++) {
                Vec3d off = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().multiply(1.4);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, hand.x + off.x, hand.y + off.y, hand.z + off.z, 0, -off.x, -off.y, -off.z, 0.1);
            }
            if (t % 4 == 0) world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.2f, 0.6f + t * 0.06f);
            return;
        }
        int s = t - form, cut = s / every, within = s % every;
        if (cut >= cuts) {
            if (within >= 4) startAttack(Action.BLADE_THROW);
            return;
        }
        if (target != null) turnTowards(target.getPos(), 25f);
        if (within == 0) {
            // Blade raised high, a step in
            setPoseState(cut % 2 == 0 ? POSE_SLASH_A : POSE_SLASH_B);
            Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
            setVelocity(forward.x * 0.55, getVelocity().y, forward.z * 0.55);
            velocityModified = true;
        }
        if (within == 3) {
            // The cut: everyone in a wide arc in front of him
            setPoseState(cut % 2 == 0 ? POSE_SLASH_B : POSE_SLASH_A);
            swingHand(Hand.MAIN_HAND);
            Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
            Vec3d c = getPos();
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getPos().subtract(c).multiply(1, 0, 1);
                double d = to.length();
                if (d > 5.5 || Math.abs(p.getY() - c.y) > 3) continue;
                if (d > 1 && to.multiply(1 / d).dotProduct(forward) < 0.35) continue;
                if (punch(p, 10)) {
                    launch(p, to.lengthSquared() > 1e-4 ? to.add(forward) : forward, 1.3, 0.35);
                    chill(p, 30);
                }
            }
            for (int i = -3; i <= 3; i++) {
                Vec3d dir = Vec3d.fromPolar(0, getBodyYaw() + i * 18f);
                Vec3d at = c.add(0, 1.6 + (cut % 2 == 0 ? -i : i) * 0.15, 0).add(dir.multiply(3.2));
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.05);
            }
            world.spawnParticles(GluttonyHazards.ICE_CHIPS, c.x + forward.x * 3, c.y + 1.6, c.z + forward.z * 3, 10, 0.8, 0.4, 0.8, 0.1);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.55f + cut * 0.05f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }
    }

    /** Blade Throw: he spins round and round with the blade out, then hurls it at one of you. Empty-handed again. */
    private void tickBladeThrow(ServerWorld world) {
        int spin = 20, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            dataTracker.set(BLADE, true);
            setPoseState(POSE_SPIN);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (t < spin) {
            faceYaw(getYaw() + 32f);
            Vec3d c = getPos();
            GluttonyHazards.ring(world, ParticleTypes.SNOWFLAKE, c, 2.6, 10, t * 0.6, 1.6);
            if (t % 5 == 0) world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.6f, 0.5f + t * 0.02f);
            return;
        }
        if (t == spin) {
            if (target != null) faceTowards(target.getPos());
            Vec3d hand = handPos(1f);
            Vec3d aim = target != null ? target.getBoundingBox().getCenter().add(target.getVelocity().multiply(5, 0, 5)) : hand.add(Vec3d.fromPolar(0, getYaw()));
            hazards.add(new GluttonyHazards.ThrownBlade(world, hand, aim.subtract(hand), 22));
            dataTracker.set(BLADE, false);
            setPoseState(POSE_PUNCH);
            swingHand(Hand.MAIN_HAND);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_WIND_CHARGE_THROW, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        if (t >= spin + 12) endAttack();
    }

    /** Ice Cannon (the Tome of Stone Cannon, in ice): three great icicles spin up over his head and fire one by one. */
    private void tickIceCannon(ServerWorld world) {
        int t = actionTicks, gap = 8;
        if (t == 1) {
            List<ServerPlayerEntity> targets = picks(3);
            if (targets.isEmpty()) {
                endAttack();
                return;
            }
            setPoseState(POSE_RAISE);
            float damage = transitioned ? 20 : 16;
            for (int i = 0; i < 3; i++) {
                ServerPlayerEntity p = targets.get(i % targets.size());
                hazards.add(new GluttonyHazards.IceSpike(world, this, i - 1, GluttonyHazards.IceSpike.CHARGE + i * gap, p.getUuid(), damage));
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        stand();
        ServerPlayerEntity first = nearestFighter();
        if (first != null) turnTowards(first.getPos(), 6f);
        if (t >= GluttonyHazards.IceSpike.CHARGE + 2 * gap + 10) endAttack();
    }

    private boolean desperate() {
        return getHealth() < getMaxHealth() * 0.25f;
    }

    // ---- The hazards, his aura, the world round the fight ----

    private void tickHazards(ServerWorld world) {
        for (GluttonyHazards.Hazard h : new ArrayList<>(hazards)) {
            if (!h.tick(world, this)) {
                h.remove();
                hazards.remove(h);
            }
        }
    }

    /** Snow drifting off him, his cold breath; in phase 2 a freezing aura that chills anyone close. */
    private void tickAura(ServerWorld world) {
        double w = getWidth() * 0.6, h = getHeight();
        if (age % 3 == 0) world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + h * 0.6, getZ(), 1, w, h * 0.3, w, 0.01);
        if (age % 50 == 0) {
            Vec3d m = mouthPos(), f = Vec3d.fromPolar(0, getBodyYaw());
            world.spawnParticles(ParticleTypes.CLOUD, m.x, m.y, m.z, 0, f.x, -0.05, f.z, 0.08);
        }
        if (!transitioned || action == Action.TRANSITION) return;
        // Phase 2: a strong ice aura, swirling round him
        for (int i = 0; i < 3; i++) {
            double a = age * 0.18 + i * MathHelper.TAU / 3, y = ((age + i * 10) % 30) / 30.0 * (h + 0.6);
            world.spawnParticles(GluttonyHazards.FROST, getX() + Math.cos(a) * 1.3, getY() + y, getZ() + Math.sin(a) * 1.3, 1, 0, 0, 0, 0);
        }
        world.spawnParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + h * 0.5, getZ(), 2, 1.4, h * 0.4, 1.4, 0.02);
        if (age % 4 == 0) world.spawnParticles(GluttonyHazards.ICE_CHIPS, getX(), getY() + h * 0.5, getZ(), 1, 0.8, h * 0.3, 0.8, 0);
        if (age % 8 == 0) GluttonyHazards.ring(world, GluttonyHazards.RIME, ground(getPos()), AURA_RADIUS, 30, age * 0.02, 0.1);
        if (age % 5 == 0) {
            for (ServerPlayerEntity p : fighters) {
                if (p.distanceTo(this) > AURA_RADIUS) continue;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 15, 0, false, true, true), this);
                p.setFrozenTicks(Math.min(p.getMinFreezeDamageTicks() + 40, p.getFrozenTicks() + 14));
            }
        }
    }

    /** Space round the Earth: frost glittering in the dark. The snowfield: snow blown past on the wind. */
    private void tickAmbience(ServerWorld world) {
        if (age % 6 != 0) return;
        for (ServerPlayerEntity p : fighters) {
            if (GluttonyArena.inSnowfield(p.getPos())) {
                world.spawnParticles(p, ParticleTypes.WHITE_ASH, false, p.getX(), p.getY() + 2, p.getZ(), 6, 10, 3, 10, 0);
            } else {
                world.spawnParticles(p, ParticleTypes.END_ROD, false, p.getX(), p.getY() + 4, p.getZ(), 1, 14, 6, 14, 0);
                world.spawnParticles(p, ParticleTypes.SNOWFLAKE, false, p.getX(), p.getY() + 2, p.getZ(), 2, 10, 3, 10, 0);
            }
        }
    }

    /** Blown off the pole or wandering out into the blizzard: the cold wind carries you back. It bites. */
    private void catchStrays(ServerWorld world) {
        for (ServerPlayerEntity p : fighters) {
            Vec3d pos = p.getPos();
            if (GluttonyArena.inBounds(pos)) {
                straying.remove(p.getUuid());
                continue;
            }
            Vec3d center = GluttonyArena.centerFor(pos);
            boolean falling = pos.y < center.y - (GluttonyArena.inSnowfield(pos) ? 4 : GluttonyArena.EARTH_DEPTH);
            int out = straying.merge(p.getUuid(), 1, Integer::sum);
            if (!falling && out < 12) continue;
            straying.remove(p.getUuid());
            Vec3d from = pos.subtract(center).multiply(1, 0, 1);
            from = from.lengthSquared() > 1e-4 ? from.normalize() : new Vec3d(0, 0, 1);
            double back = GluttonyArena.inSnowfield(pos) ? GluttonyArena.SNOW_RETURN : GluttonyArena.EARTH_RETURN;
            Vec3d to = center.add(from.multiply(back));
            to = new Vec3d(to.x, groundY(new Vec3d(to.x, center.y + 4, to.z)), to.z);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
            p.teleport(world, to.x, to.y, to.z, p.getYaw(), p.getPitch());
            p.fallDistance = 0;
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
            crush(p, 4);
            chill(p, 60);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, to.x, to.y + 1, to.z, 40, 0.4, 0.8, 0.4, 0.1);
            world.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
            p.sendMessage(Text.translatable("message.fmscraft.lalli_caught").formatted(Formatting.AQUA), true);
        }
    }

    // ---- Dying ----

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld world) {
            clearAttacks(world);
            for (ServerPlayerEntity p : audience(world)) Cinematics.flash(p, 0.8f, 30);
            Vec3d c = getPos().add(0, getHeight() / 2, 0);
            emit(world, ParticleTypes.FLASH, c, 1, 0, 0, 0, 0);
            emit(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, GluttonyHazards.BLUE), c, 250, 1.2, 1.6, 1.2, 0.3);
            emit(world, GluttonyHazards.ICE_CHIPS, c, 200, 1.2, 1.6, 1.2, 0.3);
            emit(world, ParticleTypes.SNOWFLAKE, c, 250, 2, 2, 2, 0.4);
            emit(world, ParticleTypes.END_ROD, c, 60, 1, 1.5, 1, 0.2);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 4.0f, 0.4f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ELDER_GUARDIAN_DEATH, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.5f);
            dataTracker.set(EYES, false);
        }
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Taken away mid-fight (the attempt failed): nothing of his stays behind, and nobody is left frozen or hanging
        if (getWorld() instanceof ServerWorld world && reason.shouldDestroy()) {
            clearAttacks(world);
            for (UUID id : held.keySet()) {
                if (world.getEntity(id) instanceof ServerPlayerEntity p) p.removeStatusEffect(StatusEffects.SLOW_FALLING);
            }
            held.clear();
        }
        super.remove(reason);
    }

    private void clearAttacks(ServerWorld world) {
        for (GluttonyHazards.Hazard h : hazards) h.remove();
        hazards.clear();
        halo = null;
        dataTracker.set(BEAM, BEAM_OFF);
        dataTracker.set(BLADE, false);
        // A quake whose fissures had started but never went off: call it off, or they'd stay drawn
        if (quakeCharging) MagicSpells.earthquakeShock(world, getId(), spot != null ? spot : getPos(), -1);
        quakeCharging = false;
    }

    // ---- Helpers ----

    private Vec3d fistPos() {
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(-0.4 * SCALE);
        return getPos().add(0, getHeight() * 0.62, 0).add(forward.multiply(-0.2)).add(side);
    }

    private void stand() {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
    }

    private void faceTowards(Vec3d pos) {
        faceYaw((float) (MathHelper.atan2(pos.z - getZ(), pos.x - getX()) * (180 / Math.PI)) - 90f);
    }

    /** Turns towards {@code pos}, at most {@code step} degrees a tick: he's slow to come round. */
    private void turnTowards(Vec3d pos, float step) {
        float want = (float) (MathHelper.atan2(pos.z - getZ(), pos.x - getX()) * (180 / Math.PI)) - 90f;
        faceYaw(getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - getYaw()), -step, step));
    }

    private void faceYaw(float yaw) {
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    /** Where most of them stand: the one with the most others close by, or null with nobody to fight. */
    private Vec3d clusterSpot() {
        ServerPlayerEntity best = null;
        int bestCount = -1;
        for (ServerPlayerEntity p : fighters) {
            int count = 0;
            for (ServerPlayerEntity o : fighters) if (o.squaredDistanceTo(p) < 8 * 8) count++;
            if (count > bestCount) {
                bestCount = count;
                best = p;
            }
        }
        if (best == null) return null;
        Vec3d mid = Vec3d.ZERO;
        int n = 0;
        for (ServerPlayerEntity o : fighters) {
            if (o.squaredDistanceTo(best) < 8 * 8) {
                mid = mid.add(o.getPos());
                n++;
            }
        }
        return ground(mid.multiply(1.0 / n));
    }

    /** Top of the first solid block at or below {@code pos}, looking at most {@code depth} blocks down; NaN if none. */
    private double groundBelow(Vec3d pos, int depth) {
        BlockPos.Mutable at = BlockPos.ofFloored(pos).mutableCopy();
        for (int i = 0; i < depth; i++) {
            BlockState state = getWorld().getBlockState(at);
            VoxelShape shape = state.getCollisionShape(getWorld(), at);
            if (!shape.isEmpty()) return at.getY() + shape.getMax(Direction.Axis.Y);
            at.move(Direction.DOWN);
        }
        return Double.NaN;
    }

    private BlockState groundState() {
        BlockState state = getWorld().getBlockState(getBlockPos().down());
        return state.isAir() ? GluttonyHazards.SNOW : state;
    }
}
