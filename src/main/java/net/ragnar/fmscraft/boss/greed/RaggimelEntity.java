package net.ragnar.fmscraft.boss.greed;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
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
import net.ragnar.fmscraft.sound.ModSounds;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RAGGIMEL, the Sin of Greed. Two phases, each on its own song, attacks starting on the beat.
 *
 * <h2>Phase 1 (100% to 70%): light and bubbly, Just The Way You Are</h2>
 * Floats down in a bubble to start. Walks after people and punches, and between punches picks one of:
 * <ul>
 *   <li><b>Bubble Ride</b>: seals himself in a bubble and drifts above the players, dropping sleep darts.
 *       Any hit or arrow pops it: he drops and is stunned, taking extra damage. Otherwise he floats down after ~12 s.</li>
 *   <li><b>Bubble Volley</b>: blows slow bubbles at two players. One that touches you lifts you up until it pops
 *       (or a teammate pops it) and drops you.</li>
 *   <li><b>Sleep Darts</b>: three darts at three players (Ragnars Magic Mod's: drowsy, then asleep until hurt).</li>
 *   <li><b>Dash Punch</b>: crouches with the goat's ram sound, then charges.</li>
 *   <li><b>Splash</b>: hops and lands with a ring of water to jump over.</li>
 * </ul>
 *
 * <h2>Phase change: see {@link GreedTimeline}</h2>
 *
 * <h2>Phase 2 (70% to 0%): night, Panzerknacker, 3 blocks tall</h2>
 * Faster, hits harder, attacks more often, and below 30% goes into a frenzy (more of everything). Picks from:
 * <ul>
 *   <li><b>Laser</b>: a thin beam follows its target for 1.5 s, freezes, then fires. Break line of sight (pillars,
 *       trees) or step aside. Prefers anyone stuck in a bubble.</li>
 *   <li><b>Laser Sweep</b>: a beam along the ground spins all the way round him: jump it.</li>
 *   <li><b>Bubble Prison</b>: a bubble for every player, lifting them high. Pop each other out.</li>
 *   <li><b>Greed's Due</b>: a gold circle marks where every player stands; coins rain into it 1.5 s later.</li>
 *   <li><b>Blink Strike</b>: teleports behind three players in turn (portal particles warn where) and strikes.</li>
 *   <li><b>Tidal Slam</b>: leaps and lands with three shockwave rings to jump.</li>
 *   <li><b>Dart Fan</b> and a double <b>Dash Punch</b>.</li>
 * </ul>
 * The night itself: snow and souls drifting, the shrine pulsing on the beat, lightning around the island.
 * Falling off the island kills you.
 */
public class RaggimelEntity extends SinBossEntity {
    public static final BossSong PHASE_ONE = new BossSong("fmscraft:music.greed.phase_one", 130, 15);
    /** Measures ~172 BPM; attacks go on every other beat. */
    public static final BossSong PHASE_TWO = new BossSong("fmscraft:music.greed.phase_two", 86, 7);

    // ---- Tuning knobs ----
    public static final float MAX_HEALTH = 1000;
    public static final float PHASE_TWO_AT = 0.7f;
    public static final float FRENZY_AT = 0.3f;
    /** Damage he takes is multiplied by this per phase (on top of his armor), more while stunned, and capped per hit. */
    public static final float DAMAGE_TAKEN_ONE = 0.75f, DAMAGE_TAKEN_TWO = 0.85f, STUNNED_BONUS = 1.5f, MAX_HIT = 30f;
    /** Every hit he deals (melee, darts, beams, rings, marks, slams) is multiplied by this, then rounded up. */
    public static final float DAMAGE_DEALT = 1.35f;
    public static final float PHASE_TWO_SCALE = 1.5f;
    /** Ticks between attacks (he walks and punches in between). */
    private static final int REST_ONE = 26, REST_TWO = 18, REST_FRENZY = 10;

    /** A hit he deals, after {@link #DAMAGE_DEALT}, rounded up to a whole number. */
    private static float boosted(float damage) {
        return (float) Math.ceil(damage * DAMAGE_DEALT);
    }

    // ---- Synced to the client for the model and the beams ----
    public static final int POSE_IDLE = 0, POSE_CAST = 1, POSE_AIM = 2, POSE_CROUCH = 3, POSE_STUNNED = 4, POSE_SLAM = 5;
    public static final int BEAM_SLOTS = 3;
    /** Beam stages, 2 bits per slot (plus a "low" bit: fired along the ground from his feet). */
    public static final int BEAM_OFF = 0, BEAM_CHARGE = 1, BEAM_LOCK = 2, BEAM_FIRE = 3;
    private static final TrackedData<Integer> POSE = DataTracker.registerData(RaggimelEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> BUBBLED = DataTracker.registerData(RaggimelEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> BEAMS = DataTracker.registerData(RaggimelEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @SuppressWarnings("unchecked")
    private static final TrackedData<Vector3f>[] BEAM_ENDS = new TrackedData[]{
            DataTracker.registerData(RaggimelEntity.class, TrackedDataHandlerRegistry.VECTOR3F),
            DataTracker.registerData(RaggimelEntity.class, TrackedDataHandlerRegistry.VECTOR3F),
            DataTracker.registerData(RaggimelEntity.class, TrackedDataHandlerRegistry.VECTOR3F)};

    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.82f, 0.2f), 1.4f);
    private static final DustParticleEffect LAVENDER = new DustParticleEffect(new Vector3f(0.73f, 0.64f, 1.0f), 1.0f);
    private static final DustParticleEffect PALE_BLUE = new DustParticleEffect(new Vector3f(0.6f, 0.9f, 1.0f), 1.2f);

    enum Action {
        ENTRANCE, CHASE, FALLING, STUNNED,
        BUBBLE_RIDE, BUBBLE_VOLLEY, DARTS, DASH, SPLASH,
        TRANSITION, PERCH, LEAP,
        LASER, SWEEP, COIN_STORM, BLINK, TIDAL_SLAM
    }

    private Action action = Action.ENTRANCE;
    private int actionTicks;
    private int rest;
    private Action lastAttack;
    private final Map<Action, Integer> lastUsed = new EnumMap<>(Action.class);
    private int meleeCooldown;
    private int repeats;
    private UUID focus;
    private int focusTicks;
    private Vec3d dashDir = Vec3d.ZERO, blinkTo;
    private final Set<UUID> struck = new HashSet<>();
    private boolean untouchable, frenzy;
    private int nextLightning = 160;
    private final Map<UUID, Integer> lastPicked = new HashMap<>();
    private final List<Beam> beams = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    private List<ServerPlayerEntity> fighters = List.of();

    public RaggimelEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 1000;
        bossBar().setColor(BossBar.Color.BLUE);
        setPersistent();
        equipStack(EquipmentSlot.MAINHAND, MagicSpells.item(MagicSpells.DIAMOND_STAFF));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ARMOR, 10)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 6)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, boosted(5))
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_IDLE);
        builder.add(BUBBLED, false);
        builder.add(BEAMS, 0);
        for (TrackedData<Vector3f> end : BEAM_ENDS) builder.add(end, new Vector3f());
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    protected Text bossBarName() {
        return Text.literal("RAGGIMEL");
    }

    @Override
    protected float phaseTwoHealth() {
        return PHASE_TWO_AT;
    }

    @Override
    protected boolean startsPhaseTwoSongAtOnce() {
        return false;
    }

    @Override
    protected BossSong songForPhase(int phase) {
        return phase == 1 ? PHASE_ONE : PHASE_TWO;
    }

    // ---- Client accessors ----

    public int poseState() {
        return dataTracker.get(POSE);
    }

    public boolean isBubbled() {
        return dataTracker.get(BUBBLED);
    }

    public int beamStage(int slot) {
        return (dataTracker.get(BEAMS) >> (slot * 3)) & 3;
    }

    public boolean beamLow(int slot) {
        return ((dataTracker.get(BEAMS) >> (slot * 3 + 2)) & 1) != 0;
    }

    public Vec3d beamEnd(int slot) {
        Vector3f v = dataTracker.get(BEAM_ENDS[slot]);
        return new Vec3d(v.x, v.y, v.z);
    }

    public boolean hasBeams() {
        return dataTracker.get(BEAMS) != 0;
    }

    /** Where a beam leaves him: his chest, or just above his feet for a beam along the ground. */
    public Vec3d beamOrigin(boolean low, float tickDelta) {
        Vec3d pos = getLerpedPos(tickDelta);
        return pos.add(0, low ? 0.35 : getHeight() * 0.78, 0);
    }

    public boolean isUntouchable() {
        return untouchable;
    }

    public boolean isFrenzied() {
        return frenzy;
    }

    public String currentAction() {
        return action.name();
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
    }

    private void setBubbled(boolean bubbled) {
        dataTracker.set(BUBBLED, bubbled);
        setNoGravity(bubbled || action == Action.PERCH || action == Action.LEAP);
    }

    // ---- Taking damage ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        Entity attacker = source.getAttacker();
        if (untouchable || action == Action.ENTRANCE && !isBubbled()) {
            if (attacker instanceof PlayerEntity) {
                getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
            return false;
        }
        if (isBubbled()) {
            // Anything that reaches the bubble pops it
            if (attacker != null || source.getSource() instanceof ProjectileEntity) popBubble();
            return false;
        }
        amount *= getPhase() == 1 ? DAMAGE_TAKEN_ONE : DAMAGE_TAKEN_TWO;
        if (action == Action.STUNNED) amount *= STUNNED_BONUS;
        amount = Math.min(amount, MAX_HIT);
        // Phase 1 can't be skipped by one big hit
        if (getPhase() == 1) amount = Math.min(amount, getHealth() - getMaxHealth() * PHASE_TWO_AT + 0.01f);
        return super.damage(source, Math.max(0.01f, amount));
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return action != Action.PERCH && action != Action.LEAP;
    }

    @Override
    public boolean canHaveStatusEffect(net.minecraft.entity.effect.StatusEffectInstance effect) {
        // No slowing, levitating or poisoning a sin
        return false;
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world && age % 5 == 0) shrugOffSpells(world);
    }

    /** Ragnars Magic Mod can't put him to sleep or carry him off in a bubble: he wakes / bursts out at once. */
    private void shrugOffSpells(ServerWorld world) {
        if (MagicSpells.isSleepy(this)) {
            MagicSpells.wake(this);
            world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.2f, 0.7f);
        }
        for (Entity bubble : world.getOtherEntities(this, getBoundingBox().expand(3), e -> MagicSpells.is(e, MagicSpells.BUBBLE))) {
            Vec3d c = bubble.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 30, 0.8, 0.8, 0.8, 0.2);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.HOSTILE, 2f, 0.6f);
            bubble.discard();
        }
    }

    @Override
    protected void tickPhase(int phase, int songTicks) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        fighters = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 96 * 96);
        boolean inArena = Arenas.sinOf(world) == Sin.GREED;

        if (rest > 0) rest--;
        if (meleeCooldown > 0) meleeCooldown--;
        actionTicks++;
        switch (action) {
            case ENTRANCE -> tickEntrance(world);
            case CHASE -> tickChase();
            case FALLING -> tickFalling(world);
            case STUNNED -> tickStunned(world);
            case BUBBLE_RIDE -> tickBubbleRide(world);
            case BUBBLE_VOLLEY -> tickBubbleVolley(world);
            case DARTS -> tickDarts(world);
            case DASH -> tickDash(world);
            case SPLASH -> tickSplash(world);
            case TRANSITION -> tickTransition(world);
            case PERCH -> tickPerch(world);
            case LEAP -> tickLeap(world);
            case LASER -> tickLaser(world);
            case SWEEP -> tickSweep(world);
            case COIN_STORM -> tickCoinStorm(world);
            case BLINK -> tickBlink(world);
            case TIDAL_SLAM -> tickTidalSlam(world);
        }
        tickBeams(world);
        tickRings(world);
        tickMarks(world);
        if (inArena) {
            tickAmbience(world, phase);
            tickVoid(world);
        }
    }

    @Override
    protected void onBeat(int phase, int beat) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (action == Action.CHASE && rest <= 0 && !fighters.isEmpty()) {
            startAttack(chooseAttack(phase));
        }
        if (phase == 2 && beat % 8 == 0 && Arenas.sinOf(world) == Sin.GREED) {
            // The island pulses with the song
            Vec3d c = GreedArena.CENTER;
            rings.add(new Ring(c, 26, 0.9, 0, Ring.PULSE, 0));
            world.playSound(null, c.x, c.y + 4, c.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 2.0f, 0.5f);
        }
        if (phase == 1 && beat % 4 == 0 && action == Action.CHASE) {
            world.spawnParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 1, getZ(), 6, 0.6, 0.6, 0.6, 0.05);
        }
    }

    // ---- Choosing attacks ----

    private Action chooseAttack(int phase) {
        List<Action> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        if (phase == 1) {
            offer(options, weights, Action.BUBBLE_RIDE, 2, 400);
            offer(options, weights, Action.BUBBLE_VOLLEY, 3, 160);
            offer(options, weights, Action.DARTS, 3, 0);
            offer(options, weights, Action.DASH, 3, 0);
            if (nearestFighterDistance() < 7) offer(options, weights, Action.SPLASH, 2, 0);
        } else {
            offer(options, weights, Action.LASER, 3, 0);
            offer(options, weights, Action.SWEEP, 2, 300);
            offer(options, weights, Action.BUBBLE_VOLLEY, 2, 360);
            offer(options, weights, Action.COIN_STORM, 2, 240);
            offer(options, weights, Action.BLINK, 2, 200);
            offer(options, weights, Action.TIDAL_SLAM, 2, 200);
            offer(options, weights, Action.DARTS, 2, 0);
            offer(options, weights, Action.DASH, 2, 0);
        }
        if (options.isEmpty()) return phase == 1 ? Action.DASH : Action.LASER;
        int total = weights.stream().mapToInt(Integer::intValue).sum(), roll = random.nextInt(total);
        for (int i = 0; i < options.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return options.get(i);
        }
        return options.get(0);
    }

    private void offer(List<Action> options, List<Integer> weights, Action attack, int weight, int minGap) {
        if (attack == lastAttack) return;
        Integer used = lastUsed.get(attack);
        if (used != null && age - used < minGap) return;
        options.add(attack);
        weights.add(weight);
    }

    private void startAttack(Action attack) {
        action = attack;
        actionTicks = 0;
        lastAttack = attack;
        lastUsed.put(attack, age);
        repeats = 0;
        struck.clear();
        getNavigation().stop();
    }

    private void endAttack() {
        action = Action.CHASE;
        actionTicks = 0;
        setPoseState(POSE_IDLE);
        rest = getPhase() == 1 ? REST_ONE : frenzy ? REST_FRENZY : REST_TWO;
    }

    /**
     * Spreads the attention around: the fighter he's picked on least recently (not in {@code skip}), or null if
     * there's nobody left. Never hands back someone in {@code skip}: callers looping for "N different players" rely on
     * getting null once everyone's taken (a solo fight in frenzy asks for two laser targets).
     */
    private ServerPlayerEntity pick(Set<UUID> skip) {
        ServerPlayerEntity best = null;
        int bestTick = Integer.MAX_VALUE;
        for (ServerPlayerEntity p : fighters) {
            if (skip != null && skip.contains(p.getUuid())) continue;
            int when = lastPicked.getOrDefault(p.getUuid(), Integer.MIN_VALUE + random.nextInt(1000));
            if (when < bestTick) {
                bestTick = when;
                best = p;
            }
        }
        if (best != null) lastPicked.put(best.getUuid(), age);
        return best;
    }

    private ServerPlayerEntity fighter(UUID id) {
        if (id == null) return null;
        for (ServerPlayerEntity p : fighters) if (p.getUuid().equals(id)) return p;
        return null;
    }

    private double nearestFighterDistance() {
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : fighters) best = Math.min(best, p.distanceTo(this));
        return best;
    }

    // ---- Phase 1 ----

    private void tickEntrance(ServerWorld world) {
        if (actionTicks == 1) setBubbled(true);
        if (!isBubbled()) return;
        setVelocity(0, -0.09, 0);
        double ground = groundBelow(getPos(), 40);
        if (!Double.isNaN(ground) && getY() - ground < 1.0 || actionTicks > 400) {
            setBubbled(false);
            popEffects(world, getBoundingBox().getCenter());
            endAttack();
            rest = 30;
        }
    }

    private void tickChase() {
        ServerPlayerEntity target = fighter(focus);
        if (target == null || --focusTicks <= 0) {
            target = pick(null);
            focus = target == null ? null : target.getUuid();
            focusTicks = 120;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        setTarget(target);
        getLookControl().lookAt(target, 30f, 30f);
        double speed = getPhase() == 1 ? 1.0 : 1.2;
        if (age % 10 == 0 || getNavigation().isIdle()) getNavigation().startMovingTo(target, speed);
        double reach = 1.4 + getWidth();
        if (meleeCooldown <= 0 && squaredDistanceTo(target) < reach * reach) {
            swingHand(Hand.MAIN_HAND);
            tryAttack(target);
            meleeCooldown = getPhase() == 1 ? 20 : 14;
        }
    }

    private void popBubble() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        setBubbled(false);
        popEffects(world, getBoundingBox().getCenter());
        if (action == Action.BUBBLE_RIDE || action == Action.ENTRANCE) {
            action = Action.FALLING;
            actionTicks = 0;
            setPoseState(POSE_IDLE);
        }
    }

    private void popEffects(ServerWorld world, Vec3d c) {
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.HOSTILE, 2.5f, 0.7f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.HOSTILE, 1.0f, 1.4f);
        world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 60, 0.9, 1.0, 0.9, 0.3);
        world.spawnParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 30, 0.9, 1.0, 0.9, 0.05);
    }

    private void tickFalling(ServerWorld world) {
        if (actionTicks > 2 && isOnGround() || actionTicks > 100) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_BIG_FALL, SoundCategory.HOSTILE, 1.5f, 0.8f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.1, getZ(), 30, 0.6, 0.1, 0.6, 0.1);
            action = Action.STUNNED;
            actionTicks = 0;
            setPoseState(POSE_STUNNED);
        }
    }

    private void tickStunned(ServerWorld world) {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (actionTicks % 4 == 0) {
            // Stars circling his head
            for (int i = 0; i < 3; i++) {
                double a = actionTicks * 0.3 + i * MathHelper.TAU / 3;
                world.spawnParticles(ParticleTypes.CRIT, getX() + Math.cos(a) * 0.6, getY() + getHeight() + 0.2, getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks > 70) endAttack();
    }

    private void tickBubbleRide(ServerWorld world) {
        int t = actionTicks;
        if (t < 15) {
            setPoseState(POSE_CAST);
            if (t == 1) {
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.HOSTILE, 2.0f, 0.8f);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.2f, 0.6f);
            }
            world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 6, 0.6, 0.8, 0.6, 0.1);
            return;
        }
        if (t == 15) {
            setBubbled(true);
            setPoseState(POSE_IDLE);
        }
        if (!isBubbled()) return;
        ServerPlayerEntity target = fighter(focus);
        if (target == null) {
            target = pick(null);
            focus = target == null ? null : target.getUuid();
        }
        boolean landing = t > 15 + 240;
        Vec3d want;
        double ground = groundBelow(getPos(), 30);
        if (landing || target == null) {
            want = new Vec3d(0, -0.1, 0);
        } else {
            double targetGround = target.getY();
            Vec3d to = new Vec3d(target.getX() - getX(), targetGround + 6 - getY(), target.getZ() - getZ());
            Vec3d flat = new Vec3d(to.x, 0, to.z);
            Vec3d horizontal = flat.length() > 2 ? flat.normalize().multiply(0.12) : Vec3d.ZERO;
            want = horizontal.add(0, MathHelper.clamp(to.y * 0.05, -0.08, 0.08), 0);
            getLookControl().lookAt(target, 30f, 30f);
        }
        setVelocity(getVelocity().lerp(want, 0.1));
        if (!landing && (t - 15) % 30 == 20 && !fighters.isEmpty()) {
            ServerPlayerEntity victim = pick(null);
            setPoseState(POSE_AIM);
            shootDart(world, victim.getBoundingBox().getCenter(), 1.0f, 2f);
        } else if ((t - 15) % 30 == 26) {
            setPoseState(POSE_IDLE);
        }
        if (landing && (!Double.isNaN(ground) && getY() - ground < 1.0 || t > 15 + 340)) {
            // He comes down on his own and makes a splash of it
            setBubbled(false);
            popEffects(world, getBoundingBox().getCenter());
            splash(world, 7, 5f);
            endAttack();
        }
    }

    private void tickBubbleVolley(ServerWorld world) {
        int t = actionTicks;
        boolean one = getPhase() == 1;
        setPoseState(POSE_CAST);
        Vec3d hands = getPos().add(0, getHeight() * 0.7, 0);
        if (t < 20) {
            if (t % 5 == 1) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.HOSTILE, 2.0f, 1.0f);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, hands.x, hands.y + 0.6, hands.z, 3, 0.5, 0.3, 0.5, 0.02);
            world.spawnParticles(ParticleTypes.SPLASH, hands.x, hands.y + 0.6, hands.z, 3, 0.5, 0.3, 0.5, 0.05);
        } else if (t == 20) {
            int count = Math.min(one ? 2 : 5, fighters.size());
            Set<UUID> chosen = new HashSet<>();
            for (int i = 0; i < count; i++) {
                ServerPlayerEntity target = pick(chosen);
                if (target == null) break;
                chosen.add(target.getUuid());
                GreedBubbleEntity.blow(world, hands.add(0, 0.8, 0), target, one ? 0.17 : 0.26, one ? 3.5 : 7.5, one ? 70 : 110);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 1.5f, 1.2f);
        } else if (t >= 32) {
            endAttack();
        }
    }

    private void tickDarts(ServerWorld world) {
        int t = actionTicks;
        setPoseState(POSE_AIM);
        getNavigation().stop();
        if (t == 1) {
            ServerPlayerEntity first = pick(null);
            focus = first == null ? null : first.getUuid();
            world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 2.0f, 0.8f);
            world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE, SoundCategory.HOSTILE, 1.5f, 1.4f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (target != null) getLookControl().lookAt(target, 60f, 60f);
        Vec3d hand = getPos().add(0, getHeight() * 0.75, 0);
        if (t < 14) {
            world.spawnParticles(LAVENDER, hand.x, hand.y, hand.z, 2, 0.2, 0.2, 0.2, 0);
            return;
        }
        if (getPhase() == 1) {
            if ((t == 14 || t == 22 || t == 30) && target != null) {
                shootDart(world, target.getBoundingBox().getCenter(), 1.2f, 1f);
                ServerPlayerEntity next = pick(null);
                if (next != null) focus = next.getUuid();
            }
            if (t >= 36) endAttack();
        } else {
            if (t == 16 && target != null) {
                // A fan of seven
                Vec3d to = target.getBoundingBox().getCenter().subtract(hand);
                for (int i = -3; i <= 3; i++) {
                    Vec3d dir = to.rotateY((float) Math.toRadians(i * 10));
                    shootDart(world, hand.add(dir), 1.4f, 0.5f);
                }
            }
            if (t >= 24) endAttack();
        }
    }

    private void shootDart(ServerWorld world, Vec3d at, float speed, float inaccuracy) {
        Vec3d from = getPos().add(0, getHeight() * 0.75, 0);
        Vec3d dir = at.subtract(from);
        Entity dart = MagicSpells.entity(MagicSpells.SLEEP_DART).map(t -> t.create(world)).orElse(null);
        ProjectileEntity projectile;
        if (dart instanceof ProjectileEntity sleepDart) {
            projectile = sleepDart;
        } else {
            // Without the magic mod: a plain arrow
            ArrowEntity arrow = new ArrowEntity(world, this, new ItemStack(Items.ARROW), null);
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
            projectile = arrow;
        }
        projectile.setOwner(this);
        projectile.setPosition(from.x, from.y, from.z);
        projectile.setVelocity(dir.x, dir.y, dir.z, speed, inaccuracy);
        if (projectile instanceof PersistentProjectileEntity p) p.setDamage(Math.ceil(p.getDamage() * DAMAGE_DEALT));
        world.spawnEntity(projectile);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.6f);
    }

    private void tickDash(ServerWorld world) {
        boolean one = getPhase() == 1;
        int windup = one ? 16 : 12, t = actionTicks;
        if (t == 1) {
            if (repeats == 0) repeats = one ? 1 : 2;
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            struck.clear();
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_PREPARE_RAM, SoundCategory.HOSTILE, 2.5f, 0.8f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t <= windup) {
            setVelocity(0, getVelocity().y, 0);
            if (target != null) {
                getLookControl().lookAt(target, 90f, 90f);
                setYaw(yawTo(target.getPos()));
                setBodyYaw(getYaw());
                setHeadYaw(getYaw());
                Vec3d to = target.getPos().subtract(getPos());
                dashDir = new Vec3d(to.x, 0, to.z).normalize();
            }
            if (t % 2 == 0) world.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 2, 0.3, 0, 0.3, 0.02);
            return;
        }
        if (t == windup + 1) {
            setPoseState(POSE_IDLE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        if (t <= windup + 10) {
            Vec3d ahead = getPos().add(dashDir.multiply(1.5)).add(0, 1, 0);
            if (Double.isNaN(groundBelow(ahead, 5))) {
                // Not charging off the edge of the island
                setVelocity(0, getVelocity().y, 0);
            } else {
                double speed = one ? 1.0 : 1.35;
                setVelocity(dashDir.x * speed, getVelocity().y, dashDir.z * speed);
            }
            world.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0.0);
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid()) || !p.getBoundingBox().intersects(getBoundingBox().expand(0.5))) continue;
                struck.add(p.getUuid());
                p.damage(getDamageSources().mobAttack(this), boosted(one ? 7f : 11f));
                p.takeKnockback(0.6, -dashDir.x, -dashDir.z);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GOAT_RAM_IMPACT, SoundCategory.HOSTILE, 1.5f, 0.9f);
            }
            return;
        }
        setVelocity(0, getVelocity().y, 0);
        if (--repeats > 0) actionTicks = 0;
        else endAttack();
    }

    private void tickSplash(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_SLAM);
            setVelocity(0, 0.75, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.HOSTILE, 2.0f, 1.0f);
        } else if (t > 4 && isOnGround() || t > 60) {
            splash(world, 7, 5f);
            endAttack();
        }
    }

    /** Lands with a splash: a ring of water rolling out to {@code radius}. */
    private void splash(ServerWorld world, double radius, float damage) {
        rings.add(new Ring(getPos(), radius, 0.5, damage, Ring.WATER, 0));
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 2.0f, 0.8f);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.0f, 0.8f);
        world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 80, 1.2, 0.2, 1.2, 0.4);
    }

    // ---- The phase change ----

    @Override
    protected void onPhaseTwo() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        action = Action.TRANSITION;
        actionTicks = 0;
        untouchable = true;
        setBubbled(false);
        setPoseState(POSE_IDLE);
        getNavigation().stop();
        clearAttacks(world);
        stopSong();
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.freeze(p);
            Cinematics.screen(p, BossScreenPayload.BLACK, 0, GreedTimeline.BLACKOUT_FADE);
            p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.0f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.0f, 0.6f);
        }
    }

    /** Everyone in the arena, fighting or not: cutscenes are for all of them. */
    private List<ServerPlayerEntity> audience(ServerWorld world) {
        return world.getPlayers(p -> p.squaredDistanceTo(this) < 160 * 160);
    }

    private void clearAttacks(ServerWorld world) {
        beams.clear();
        rings.clear();
        marks.clear();
        for (GreedBubbleEntity bubble : world.getEntitiesByClass(GreedBubbleEntity.class, getBoundingBox().expand(96), b -> true)) bubble.pop(world);
        for (Entity dart : world.getOtherEntities(this, getBoundingBox().expand(96),
                e -> e instanceof ProjectileEntity p && p.getOwner() == this)) dart.discard();
        syncBeams();
    }

    private void tickTransition(ServerWorld world) {
        int t = actionTicks;
        setVelocity(0, getVelocity().y, 0);
        List<ServerPlayerEntity> audience = audience(world);
        // Five seconds of dark and something wrong
        if (t < GreedTimeline.VOICE_AT) {
            if (t % 20 == 0) for (ServerPlayerEntity p : audience) p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.0f, 0.6f);
            SoundEvent eerie = switch (t) {
                case 18 -> SoundEvents.AMBIENT_CAVE.value();
                case 40 -> SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value();
                case 62 -> SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST;
                case 82 -> SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK;
                default -> null;
            };
            if (eerie != null) for (ServerPlayerEntity p : audience) p.playSoundToPlayer(eerie, SoundCategory.MASTER, t == 82 ? 0.4f : 1.0f, 0.5f);
        }
        if (t == GreedTimeline.VOICE_AT) {
            for (ServerPlayerEntity p : audience) p.playSoundToPlayer(ModSounds.RAGGIMEL_VOICELINE, SoundCategory.VOICE, 1.0f, 1.0f);
        }
        if (t == GreedTimeline.RESHAPE_AT) {
            // Under the black: he grows, takes his perch, and night falls
            getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(PHASE_TWO_SCALE);
            getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.32);
            getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(boosted(9));
            equipStack(EquipmentSlot.MAINHAND, MagicSpells.item(MagicSpells.NETHERITE_STAFF));
            Vec3d perch = Arenas.sinOf(world) == Sin.GREED ? GreedArena.PERCH : getPos().add(0, 10, 0);
            setNoGravity(true);
            requestTeleport(perch.x, perch.y, perch.z);
            for (ServerPlayerEntity p : audience) GreedIntro.sky(p, 2);
        }
        if (t == GreedTimeline.REVEAL_AT) {
            for (ServerPlayerEntity p : audience) Cinematics.screen(p, BossScreenPayload.CLEAR, 0, GreedTimeline.REVEAL_TICKS);
        }
        if (t == GreedTimeline.SONG_AT) {
            for (ServerPlayerEntity p : audience) {
                Cinematics.unfreeze(p);
                Cinematics.flash(p, 0.7f, 25);
            }
            strikeCosmetic(world, getPos().add(3, 0, 2));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10000f, 0.7f);
            playSong(PHASE_TWO, GreedTimeline.SONG_FADE);
            action = Action.PERCH;
            actionTicks = 0;
            setNoGravity(true);
        }
    }

    private void tickPerch(ServerWorld world) {
        int t = actionTicks;
        Vec3d perch = Arenas.sinOf(world) == Sin.GREED ? GreedArena.PERCH : getPos();
        setVelocity(Vec3d.ZERO);
        setPosition(perch.x, perch.y + Math.sin(t * 0.08) * 0.4, perch.z);
        setPoseState(beams.isEmpty() ? POSE_CAST : POSE_AIM);
        if (t % 3 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getHeight() / 2, getZ(), 4, 0.6, 1.0, 0.6, 0.1);
        if (t >= 10 && t % 50 == 10 && t < GreedTimeline.PERCH_TICKS - 40) {
            Set<UUID> chosen = new HashSet<>();
            for (int i = 0; i < 2; i++) {
                ServerPlayerEntity target = pick(chosen);
                if (target == null) break;
                chosen.add(target.getUuid());
                fireLaser(world, target, 90, 7f);
                getLookControl().lookAt(target, 90f, 90f);
            }
        }
        if (t >= GreedTimeline.PERCH_TICKS) {
            action = Action.LEAP;
            actionTicks = 0;
            noClip = true;
            setPoseState(POSE_SLAM);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 4.0f, 0.7f);
        }
    }

    private void tickLeap(ServerWorld world) {
        int t = actionTicks;
        boolean arena = Arenas.sinOf(world) == Sin.GREED;
        Vec3d from = arena ? GreedArena.PERCH : getPos(), to = arena ? GreedArena.LANDING : getPos();
        double u = Math.min(1.0, t / (double) GreedTimeline.LEAP_TICKS);
        Vec3d at = from.lerp(to, u).add(0, GreedTimeline.leapHeight(t), 0);
        setVelocity(Vec3d.ZERO);
        setPosition(at.x, at.y, at.z);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 4, 0.4, 0.6, 0.4, 0.01);
        if (t >= GreedTimeline.LEAP_TICKS) {
            noClip = false;
            setNoGravity(false);
            untouchable = false;
            tidalImpact(world);
            endAttack();
            rest = 40;
        }
    }

    // ---- Phase 2 ----

    private void tickLaser(ServerWorld world) {
        int t = actionTicks;
        setPoseState(POSE_AIM);
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t == 1) {
            // Anyone stuck in a bubble can't dodge: he goes for them first
            Set<UUID> chosen = new HashSet<>();
            int count = Math.min(frenzy ? 2 : 1, fighters.size());
            for (ServerPlayerEntity p : fighters) {
                if (chosen.size() < count && GreedBubbleEntity.bubbleOf(p) != null) chosen.add(p.getUuid());
            }
            while (chosen.size() < count) {
                ServerPlayerEntity p = pick(chosen);
                if (p == null) break;
                chosen.add(p.getUuid());
            }
            for (UUID id : chosen) {
                ServerPlayerEntity p = fighter(id);
                if (p != null) fireLaser(world, p, 48, 8f);
            }
        }
        if (!beams.isEmpty()) {
            ServerPlayerEntity target = fighter(beams.get(0).target);
            if (target != null) getLookControl().lookAt(target, 60f, 60f);
        }
        if (t >= Beam.CHARGE + Beam.LOCK + Beam.FIRE + 2) endAttack();
    }

    private void fireLaser(ServerWorld world, ServerPlayerEntity target, double range, float damage) {
        int slot = freeSlot();
        if (slot < 0) return;
        Beam beam = new Beam(slot, target.getUuid(), range, damage, false);
        beams.add(beam);
        world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 3.0f, 1.3f);
        target.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.HOSTILE, 1.0f, 1.8f);
    }

    private void tickSweep(ServerWorld world) {
        int t = actionTicks;
        setPoseState(POSE_CAST);
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t == 1) {
            int slot = freeSlot();
            if (slot < 0) {
                endAttack();
                return;
            }
            Beam sweep = new Beam(slot, null, 24, 8f, true);
            sweep.angle = random.nextFloat() * MathHelper.TAU;
            sweep.spin = (random.nextBoolean() ? 1 : -1) * MathHelper.TAU / Beam.SWEEP_TICKS;
            beams.add(sweep);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 3.0f, 0.8f);
        }
        if (t >= Beam.SWEEP_CHARGE + Beam.SWEEP_TICKS + 4) endAttack();
    }

    private void tickCoinStorm(ServerWorld world) {
        int t = actionTicks;
        setPoseState(POSE_CAST);
        getNavigation().stop();
        if (t == 1) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0f, 1.2f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (t == 10 || frenzy && t == 50) {
            for (ServerPlayerEntity p : fighters) {
                marks.add(new Mark(p.getPos(), 8f));
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.HOSTILE, 1.0f, 1.2f);
            }
        }
        world.spawnParticles(GOLD, getX(), getY() + getHeight() + 0.3, getZ(), 3, 0.5, 0.2, 0.5, 0);
        if (t >= (frenzy ? 60 : 22)) endAttack();
    }

    private void tickBlink(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            if (repeats == 0) repeats = frenzy ? 4 : 3;
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            struck.clear();
            Vec3d look = target.getRotationVector().multiply(1, 0, 1).normalize();
            blinkTo = standingSpot(target.getPos().subtract(look.multiply(2.2)));
            if (blinkTo == null) blinkTo = standingSpot(target.getPos().add(look.rotateY((float) Math.PI / 2).multiply(2.2)));
            if (blinkTo == null) blinkTo = target.getPos();
            world.playSound(null, blinkTo.x, blinkTo.y, blinkTo.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            setPoseState(POSE_IDLE);
        }
        if (blinkTo == null) {
            endAttack();
            return;
        }
        if (t < 12) {
            // Where he'll appear
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, blinkTo.x, blinkTo.y + 1.2, blinkTo.z, 8, 0.3, 0.8, 0.3, 0.05);
            if (t % 2 == 0) world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, blinkTo.x, blinkTo.y + 0.1, blinkTo.z, 3, 0.4, 0, 0.4, 0.01);
            return;
        }
        ServerPlayerEntity target = fighter(focus);
        if (t == 12) {
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.2, getZ(), 40, 0.4, 1.0, 0.4, 0.1);
            requestTeleport(blinkTo.x, blinkTo.y, blinkTo.z);
            if (target != null) {
                setYaw(yawTo(target.getPos()));
                setBodyYaw(getYaw());
                setHeadYaw(getYaw());
            }
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        if (t == 18) {
            setPoseState(POSE_IDLE);
            swingHand(Hand.MAIN_HAND);
            Vec3d facing = Vec3d.fromPolar(0, getYaw());
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, getX() + facing.x * 1.5, getY() + 1.2, getZ() + facing.z * 1.5, 3, 0.6, 0.2, 0.6, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 2.0f, 0.7f);
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getPos().subtract(getPos());
                if (to.lengthSquared() > 3.6 * 3.6 || to.normalize().dotProduct(facing) < 0.2) continue;
                p.damage(getDamageSources().mobAttack(this), boosted(10f));
                p.takeKnockback(0.5, -facing.x, -facing.z);
            }
        }
        if (t >= 24) {
            if (--repeats > 0) actionTicks = 0;
            else endAttack();
        }
    }

    private void tickTidalSlam(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_SLAM);
            getNavigation().stop();
            setVelocity(0, 1.15, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0f, 1.1f);
        } else if (t > 8 && getVelocity().y < 0) {
            setVelocity(getVelocity().add(0, -0.15, 0));
        }
        if (t > 6 && isOnGround() || t > 80) {
            tidalImpact(world);
            endAttack();
        }
    }

    /** Lands hard: three rings of soul fire roll out, a flash, the ground shakes. */
    private void tidalImpact(ServerWorld world) {
        for (int i = 0; i < 3; i++) rings.add(new Ring(getPos(), 15, 0.45, 8f, Ring.SOUL, i * 10));
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.7f);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 3.0f, 0.6f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.2, getZ(), 120, 2.5, 0.2, 2.5, 0.2);
        for (ServerPlayerEntity p : audience(world)) {
            if (p.squaredDistanceTo(this) < 24 * 24) Cinematics.flash(p, 0.3f, 10);
        }
    }

    // ---- Beams ----

    /** One laser. Aimed ones follow their target while charging, freeze, then fire; sweeps spin along the ground. */
    static final class Beam {
        static final int CHARGE = 30, LOCK = 6, FIRE = 10;
        static final int SWEEP_CHARGE = 24, SWEEP_TICKS = 60;
        final int slot;
        final UUID target;
        final double range;
        final float damage;
        final boolean sweep;
        final Set<UUID> hit = new HashSet<>();
        Vec3d end;
        float angle, spin;
        int age;

        Beam(int slot, UUID target, double range, float damage, boolean sweep) {
            this.slot = slot;
            this.target = target;
            this.range = range;
            this.damage = damage;
            this.sweep = sweep;
        }

        int stage() {
            if (sweep) return age <= SWEEP_CHARGE ? BEAM_CHARGE : BEAM_FIRE;
            return age <= CHARGE ? BEAM_CHARGE : age <= CHARGE + LOCK ? BEAM_LOCK : BEAM_FIRE;
        }

        boolean done() {
            return age > (sweep ? SWEEP_CHARGE + SWEEP_TICKS : CHARGE + LOCK + FIRE);
        }
    }

    private int freeSlot() {
        for (int slot = 0; slot < BEAM_SLOTS; slot++) {
            final int s = slot;
            if (beams.stream().noneMatch(b -> b.slot == s)) return slot;
        }
        return -1;
    }

    private void tickBeams(ServerWorld world) {
        Iterator<Beam> it = beams.iterator();
        while (it.hasNext()) {
            Beam b = it.next();
            b.age++;
            if (b.done()) {
                it.remove();
                continue;
            }
            Vec3d origin = beamOrigin(b.sweep, 1f);
            if (b.sweep) {
                if (b.stage() == BEAM_FIRE) b.angle += b.spin;
                Vec3d dir = new Vec3d(Math.cos(b.angle), 0, Math.sin(b.angle));
                b.end = reach(origin, origin.add(dir.multiply(b.range)));
                setBodyYaw((float) Math.toDegrees(b.angle) - 90f);
                if (b.age % 10 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 3.0f, 1.6f);
            } else if (b.stage() == BEAM_CHARGE) {
                ServerPlayerEntity target = fighter(b.target);
                Vec3d aim = target != null ? target.getBoundingBox().getCenter() : b.end != null ? b.end : origin.add(getRotationVector());
                b.end = reach(origin, aim);
            } else if (b.age == Beam.CHARGE + 1) {
                // Locked: from here it's a straight line past where they were
                Vec3d dir = b.end.subtract(origin).normalize();
                b.end = reach(origin, origin.add(dir.multiply(b.range)));
                ServerPlayerEntity target = fighter(b.target);
                if (target != null) target.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.HOSTILE, 1.0f, 2.0f);
            }
            if (b.stage() == BEAM_FIRE) {
                if (b.sweep ? b.age == Beam.SWEEP_CHARGE + 1 : b.age == Beam.CHARGE + Beam.LOCK + 1) {
                    world.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3.0f, 2.0f);
                    world.playSound(null, b.end.x, b.end.y, b.end.z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 3.0f, 0.6f);
                }
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, b.end.x, b.end.y, b.end.z, 6, 0.2, 0.2, 0.2, 0.2);
                if (b.age % 2 == 0) world.spawnParticles(ParticleTypes.END_ROD, b.end.x, b.end.y, b.end.z, 2, 0.1, 0.1, 0.1, 0.05);
                for (ServerPlayerEntity p : fighters) {
                    if (b.hit.contains(p.getUuid())) continue;
                    var box = p.getBoundingBox().expand(b.sweep ? 0.1 : 0.35);
                    if (box.contains(origin) || box.raycast(origin, b.end).isPresent()) {
                        b.hit.add(p.getUuid());
                        p.damage(getDamageSources().indirectMagic(this, this), boosted(b.damage));
                        Cinematics.flash(p, 0.25f, 6);
                    }
                }
            }
        }
        syncBeams();
    }

    /** The point where a beam from {@code from} towards {@code to} stops: the first block in the way, or {@code to}. */
    private Vec3d reach(Vec3d from, Vec3d to) {
        var hit = getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getPos();
    }

    private void syncBeams() {
        int flags = 0;
        for (Beam b : beams) {
            if (b.end == null) continue;
            flags |= b.stage() << (b.slot * 3);
            if (b.sweep) flags |= 1 << (b.slot * 3 + 2);
            Vector3f end = new Vector3f((float) b.end.x, (float) b.end.y, (float) b.end.z);
            if (!end.equals(dataTracker.get(BEAM_ENDS[b.slot]))) dataTracker.set(BEAM_ENDS[b.slot], end);
        }
        if (flags != dataTracker.get(BEAMS)) dataTracker.set(BEAMS, flags);
    }

    // ---- Shockwave rings ----

    /** A ring rolling out along the ground. Jump it: it only catches you while you're on the ground. */
    static final class Ring {
        static final int WATER = 0, SOUL = 1, PULSE = 2;
        final Vec3d center;
        final double max, speed;
        final float damage;
        final int style;
        final Set<UUID> hit = new HashSet<>();
        double radius;
        int delay;

        Ring(Vec3d center, double max, double speed, float damage, int style, int delay) {
            this.center = center;
            this.max = max;
            this.speed = speed;
            this.damage = damage;
            this.style = style;
            this.delay = delay;
        }
    }

    private void tickRings(ServerWorld world) {
        Iterator<Ring> it = rings.iterator();
        while (it.hasNext()) {
            Ring ring = it.next();
            if (ring.delay > 0) {
                ring.delay--;
                continue;
            }
            ring.radius += ring.speed;
            if (ring.radius > ring.max) {
                it.remove();
                continue;
            }
            int points = (int) Math.min(40, 6 + ring.radius * 2.5);
            ParticleEffect particle = switch (ring.style) {
                case Ring.WATER -> ParticleTypes.SPLASH;
                case Ring.SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
                default -> ParticleTypes.END_ROD;
            };
            double offset = random.nextDouble() * MathHelper.TAU;
            for (int i = 0; i < points; i++) {
                double a = offset + i * MathHelper.TAU / points;
                double x = ring.center.x + Math.cos(a) * ring.radius, z = ring.center.z + Math.sin(a) * ring.radius;
                world.spawnParticles(particle, x, ring.center.y + 0.15, z, 1, 0.05, ring.style == Ring.PULSE ? 0 : 0.1, 0.05, 0.0);
                if (ring.style == Ring.SOUL && i % 3 == 0) world.spawnParticles(ParticleTypes.SNOWFLAKE, x, ring.center.y + 0.4, z, 1, 0.1, 0.2, 0.1, 0.02);
            }
            if (ring.damage <= 0) continue;
            for (ServerPlayerEntity p : fighters) {
                if (ring.hit.contains(p.getUuid())) continue;
                double d = Math.sqrt(MathHelper.square(p.getX() - ring.center.x) + MathHelper.square(p.getZ() - ring.center.z));
                double dy = p.getY() - ring.center.y;
                if (Math.abs(d - ring.radius) > 0.8 || dy < -1.5 || dy > 1.5) continue;
                if (!p.isOnGround()) continue;
                ring.hit.add(p.getUuid());
                p.damage(getDamageSources().mobAttack(this), boosted(ring.damage));
                p.addVelocity(0, 0.35, 0);
                p.velocityModified = true;
            }
        }
    }

    // ---- Greed's Due (gold circles) ----

    static final class Mark {
        static final int WARN = 32;
        static final double RADIUS = 2.6;
        final Vec3d center;
        final float damage;
        int age;

        Mark(Vec3d center, float damage) {
            this.center = center;
            this.damage = damage;
        }
    }

    private void tickMarks(ServerWorld world) {
        Iterator<Mark> it = marks.iterator();
        while (it.hasNext()) {
            Mark m = it.next();
            m.age++;
            if (m.age < Mark.WARN) {
                if (m.age % 2 == 0) {
                    for (int i = 0; i < 18; i++) {
                        double a = i * MathHelper.TAU / 18 + m.age * 0.05;
                        world.spawnParticles(GOLD, m.center.x + Math.cos(a) * Mark.RADIUS, m.center.y + 0.15, m.center.z + Math.sin(a) * Mark.RADIUS, 1, 0, 0, 0, 0);
                    }
                }
                if (m.age % 4 == 0) {
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.GOLD_BLOCK.getDefaultState()),
                            m.center.x, m.center.y + 6, m.center.z, 4, 1.2, 0.5, 1.2, 0);
                }
                if (m.age % 10 == 0) {
                    world.playSound(null, m.center.x, m.center.y, m.center.z, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.HOSTILE, 1.0f, 1.5f + m.age / 40f);
                }
                continue;
            }
            // The coins come down
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)),
                    m.center.x, m.center.y + 3, m.center.z, 50, 1.4, 1.5, 1.4, 0.15);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GOLD_BLOCK.getDefaultState()),
                    m.center.x, m.center.y + 0.2, m.center.z, 40, 1.2, 0.1, 1.2, 0.2);
            world.spawnParticles(ParticleTypes.CRIT, m.center.x, m.center.y + 0.5, m.center.z, 20, 1.2, 0.3, 1.2, 0.3);
            world.playSound(null, m.center.x, m.center.y, m.center.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.2f, 1.5f);
            world.playSound(null, m.center.x, m.center.y, m.center.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.HOSTILE, 2.0f, 0.6f);
            for (ServerPlayerEntity p : fighters) {
                double dx = p.getX() - m.center.x, dz = p.getZ() - m.center.z, dy = p.getY() - m.center.y;
                if (dx * dx + dz * dz <= Mark.RADIUS * Mark.RADIUS && dy > -2 && dy < 3) {
                    p.damage(getDamageSources().mobAttack(this), boosted(m.damage));
                }
            }
            it.remove();
        }
    }

    // ---- The arena around him ----

    private void tickAmbience(ServerWorld world, int phase) {
        boolean night = phase == 2 && action != Action.TRANSITION;
        if (!night) {
            // A light, dreamy drift
            if (age % 8 == 0) {
                for (ServerPlayerEntity p : fighters) world.spawnParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 3, p.getZ(), 6, 8, 3, 8, 0);
            }
            return;
        }
        if (age % 4 == 0) {
            for (ServerPlayerEntity p : fighters) {
                world.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 4, p.getZ(), 3, 9, 3, 9, 0.01);
                world.spawnParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1, p.getZ(), 1, 9, 2, 9, 0.02);
            }
        }
        if (age % 10 == 0) {
            for (ServerPlayerEntity p : fighters) world.spawnParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 1, 7, 0.1, 7, 0.02);
        }
        // His aura
        if (age % 2 == 0) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getHeight() * 0.6, getZ(), 2, getWidth() * 0.6, getHeight() * 0.35, getWidth() * 0.6, 0.05);
            world.spawnParticles(PALE_BLUE, getX(), getY() + 0.2, getZ(), 1, 0.4, 0.05, 0.4, 0);
        }
        // Lightning around the island
        if (--nextLightning <= 0) {
            nextLightning = frenzy ? 60 + random.nextInt(80) : 120 + random.nextInt(140);
            double a = random.nextDouble() * MathHelper.TAU, d = 22 + random.nextDouble() * 40;
            Vec3d at = GreedArena.CENTER.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            double ground = groundBelow(at.add(0, 30, 0), 80);
            at = new Vec3d(at.x, Double.isNaN(ground) ? GreedArena.CENTER.y - 25 : ground, at.z);
            strikeCosmetic(world, at);
        }
        // Frenzy starts below 30%
        if (!frenzy && getHealth() <= getMaxHealth() * FRENZY_AT && !untouchable) {
            frenzy = true;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 4.0f, 0.6f);
            strikeCosmetic(world, getPos());
            for (ServerPlayerEntity p : audience(world)) Cinematics.flash(p, 0.6f, 20);
        }
    }

    private void strikeCosmetic(ServerWorld world, Vec3d at) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) return;
        bolt.setCosmetic(true);
        bolt.refreshPositionAfterTeleport(at);
        world.spawnEntity(bolt);
        for (ServerPlayerEntity p : audience(world)) {
            double d = Math.sqrt(p.squaredDistanceTo(at));
            if (d < 90) Cinematics.flash(p, (float) (0.4 * (1 - d / 90)), 8);
        }
    }

    /** The island is all there is: fall off and you're gone (and so is he, back to the ring). */
    private void tickVoid(ServerWorld world) {
        for (ServerPlayerEntity p : world.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.getY() < GreedArena.VOID_Y)) {
            p.damage(world.getDamageSources().outOfWorld(), Float.MAX_VALUE);
        }
        if (getY() < GreedArena.VOID_Y) {
            Vec3d back = GreedArena.LANDING;
            requestTeleport(back.x, back.y, back.z);
            setVelocity(Vec3d.ZERO);
        }
    }

    // ---- Dying ----

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld world) {
            clearAttacks(world);
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.flash(p, 0.8f, 30);
                GreedIntro.sky(p, 1);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 120, 1.5, 1.5, 1.5, 0.4);
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), getX(), getY() + 1.5, getZ(), 80, 1, 1, 1, 0.3);
        }
        super.onDeath(source);
    }

    // ---- Helpers ----

    /** Top of the first solid block at or below {@code pos}, looking at most {@code depth} blocks down; NaN over the void. */
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

    /** Somewhere to stand near {@code pos} (solid below, room for him above), or null. */
    private Vec3d standingSpot(Vec3d pos) {
        double ground = groundBelow(pos.add(0, 2, 0), 6);
        if (Double.isNaN(ground)) return null;
        Vec3d spot = new Vec3d(pos.x, ground, pos.z);
        return getWorld().isSpaceEmpty(this, getDimensions(getPose()).getBoxAt(spot)) ? spot : null;
    }

    private BlockState groundState() {
        BlockState state = getWorld().getBlockState(getBlockPos().down());
        return state.isAir() ? Blocks.STONE.getDefaultState() : state;
    }

    private float yawTo(Vec3d pos) {
        return (float) (MathHelper.atan2(pos.z - getZ(), pos.x - getX()) * (180 / Math.PI)) - 90f;
    }
}
