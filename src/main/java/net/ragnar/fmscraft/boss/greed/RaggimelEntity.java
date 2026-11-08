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
import net.minecraft.entity.decoration.DisplayEntity;
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
import net.minecraft.util.math.AffineTransformation;
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
import org.joml.Quaternionf;
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
 *   <li><b>Chest Rain</b> (both phases): chests spiral up round him into the sky, then rain down on everyone for 4 s,
 *       a gold ring under each; they fall straight through the island. He walks and punches all the while.</li>
 * </ul>
 *
 * <h2>Phase change: see {@link GreedTimeline}</h2>
 *
 * <h2>Phase 2 (70% to 0%): the Roswall manor (GreedManor) under a navy sky full of black holes, Panzerknacker, 3 blocks tall</h2>
 * Faster, hits harder, attacks more often, and below 30% goes into a frenzy (more of everything). Picks from:
 * <ul>
 *   <li><b>Laser</b>: a thin beam follows its target for 1.5 s, freezes, then fires. Break line of sight (pillars,
 *       trees) or step aside. Prefers anyone stuck in a bubble.</li>
 *   <li><b>Laser Sweep</b>: a beam along the ground spins all the way round him: jump it.</li>
 *   <li><b>Bubble Prison</b>: a bubble for every player, lifting them high. Pop each other out.</li>
 *   <li><b>Anvil Slam</b>: an anvil appears high over each player, shaking harder and harder over its shadow (it
 *       follows you a moment, then locks), and slams down. Step out from under it.</li>
 *   <li><b>Cloud Ride</b>: leaps into the sky onto a cloud and flies round over everyone for 7 s, raking the ground
 *       with water bolts like a minigun, then drops off it with a heavy crash and a ring of water.</li>
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
    /** Every hit he deals (melee, darts, beams, rings, anvils, bolts, slams) is multiplied by this, then rounded up. */
    public static final float DAMAGE_DEALT = 1.35f;
    public static final float PHASE_TWO_SCALE = 1.5f;
    /** Ticks between attacks (he walks and punches in between). */
    private static final int REST_ONE = 26, REST_TWO = 18, REST_FRENZY = 10;

    /** A hit he deals, after {@link #DAMAGE_DEALT}, rounded up to a whole number. */
    private static float boosted(float damage) {
        return (float) Math.ceil(damage * DAMAGE_DEALT);
    }

    // ---- Synced to the client for the model and the beams ----
    public static final int POSE_IDLE = 0, POSE_CAST = 1, POSE_AIM = 2, POSE_CROUCH = 3, POSE_STUNNED = 4, POSE_SLAM = 5,
            POSE_SHAKE = 6, POSE_SMASH = 7;
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
        TRANSITION,
        LASER, SWEEP, ANVIL_SLAM, BLINK, TIDAL_SLAM, WATER_SPROUT, DIVE, GIANT_SLAM, CHEST_RAIN, CLOUD_RIDE
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
    /** His water (and anvils, and the cloud): solid pieces that tick on their own until they're done (GreedWater). */
    private final List<GreedWater.Piece> water = new ArrayList<>();
    private GreedWater.Spout spout;
    private GreedWater.Cloud cloud;
    /** Where the phase change and the Cloud Ride are happening: the ground under him, where he flies round. */
    private Vec3d tideBase, rideFrom, flyCenter, dropFrom, dropTo;
    private final Map<UUID, Integer> strayTicks = new HashMap<>();
    private final List<Sprout> sprouts = new ArrayList<>();
    /** Players a Water Sprout shot up: no fall damage until they're down. */
    private final Set<UUID> floating = new HashSet<>();
    private Vec3d diveFrom, diveTo, giantSpot;
    /** His size before a Giant Slam (0 when he isn't in one): he's always put back to it. */
    private float giantBase;
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

    /** For tests: a Water Sprout bursts at {@code at} (the real ones land at random round him). */
    public void sproutAt(Vec3d at) {
        sprouts.add(new Sprout(at));
    }

    public String currentAction() {
        return action.name();
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
    }

    private void setBubbled(boolean bubbled) {
        dataTracker.set(BUBBLED, bubbled);
        setNoGravity(bubbled || action == Action.TRANSITION || action == Action.CLOUD_RIDE);
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
        return action != Action.TRANSITION && action != Action.CLOUD_RIDE && action != Action.DIVE;
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
            case LASER -> tickLaser(world);
            case SWEEP -> tickSweep(world);
            case ANVIL_SLAM -> tickAnvilSlam(world);
            case BLINK -> tickBlink(world);
            case TIDAL_SLAM -> tickTidalSlam(world);
            case WATER_SPROUT -> tickWaterSprout(world);
            case DIVE -> tickDive(world);
            case GIANT_SLAM -> tickGiantSlam(world);
            case CHEST_RAIN -> tickChestRain(world);
            case CLOUD_RIDE -> tickCloudRide(world);
        }
        tickBeams(world);
        tickRings(world);
        tickWater(world);
        tickSprouts(world);
        tickChests(world);
        if (inArena) {
            tickAmbience(world, phase);
            tickVoid(world);
            if (phase == 2 && action != Action.TRANSITION) keepInGarden(world);
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
            Vec3d c = arenaCenter();
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
            offer(options, weights, Action.CHEST_RAIN, 2, 360);
        } else {
            offer(options, weights, Action.LASER, 3, 0);
            offer(options, weights, Action.SWEEP, 2, 300);
            offer(options, weights, Action.BUBBLE_VOLLEY, 2, 360);
            offer(options, weights, Action.ANVIL_SLAM, 2, 240);
            offer(options, weights, Action.CLOUD_RIDE, 2, 480);
            offer(options, weights, Action.BLINK, 2, 200);
            offer(options, weights, Action.TIDAL_SLAM, 2, 200);
            offer(options, weights, Action.DARTS, 2, 0);
            offer(options, weights, Action.DASH, 2, 0);
            offer(options, weights, Action.WATER_SPROUT, 2, 260);
            offer(options, weights, Action.DIVE, 2, 280);
            offer(options, weights, Action.GIANT_SLAM, 2, 400);
            offer(options, weights, Action.CHEST_RAIN, 2, 320);
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
        setInvisible(false);
        shrinkBack();
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
        setPoseState(POSE_CAST);
        getNavigation().stop();
        clearAttacks(world);
        stopSong();
        double ground = groundBelow(getPos().add(0, 1, 0), 20);
        tideBase = new Vec3d(getX(), Double.isNaN(ground) ? getY() : ground, getZ());
        setNoGravity(true);
        for (ServerPlayerEntity p : audience(world)) {
            // Held where they stand, but free to look round: this one's to watch
            Cinematics.freeze(p);
            p.playSoundToPlayer(SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.MASTER, 1.0f, 0.6f);
            p.playSoundToPlayer(SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundCategory.MASTER, 1.0f, 0.6f);
        }
    }

    /** Everyone in the arena, fighting or not: cutscenes are for all of them. */
    private List<ServerPlayerEntity> audience(ServerWorld world) {
        return world.getPlayers(p -> p.squaredDistanceTo(this) < 160 * 160);
    }

    private void clearAttacks(ServerWorld world) {
        beams.clear();
        rings.clear();
        sprouts.clear();
        for (GreedWater.Piece piece : water) piece.remove();
        water.clear();
        spout = null;
        cloud = null;
        clearChests();
        setInvisible(false);
        shrinkBack();
        for (GreedBubbleEntity bubble : world.getEntitiesByClass(GreedBubbleEntity.class, getBoundingBox().expand(96), b -> true)) bubble.pop(world);
        for (Entity dart : world.getOtherEntities(this, getBoundingBox().expand(96),
                e -> e instanceof ProjectileEntity p && p.getOwner() == this)) dart.discard();
        syncBeams();
    }

    private void tickTransition(ServerWorld world) {
        int t = actionTicks;
        boolean arena = Arenas.sinOf(world) == Sin.GREED;
        List<ServerPlayerEntity> audience = audience(world);
        setVelocity(Vec3d.ZERO);
        getNavigation().stop();
        if (tideBase == null) tideBase = getPos();
        if (t < GreedTimeline.SONG_AT) setPosition(tideBase.x, tideBase.y + GreedTimeline.spoutLift(t), tideBase.z);

        if (t == 1) {
            // He lifts his staff and the sea answers: a waterspout rising whirling round him
            spout = new GreedWater.Spout(world, tideBase, 2.4f);
            spout.grow(GreedTimeline.SPOUT_HEIGHT, GreedTimeline.SPOUT_TICKS);
            water.add(spout);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 4.0f, 0.5f);
        }
        if (t == GreedTimeline.SKY_AT) {
            for (ServerPlayerEntity p : audience) GreedIntro.sky(p, 2);
        }
        if (t == GreedTimeline.VOICE_AT) {
            for (ServerPlayerEntity p : audience) p.playSoundToPlayer(ModSounds.RAGGIMEL_VOICELINE, SoundCategory.VOICE, 1.0f, 1.0f);
        }
        if (t < GreedTimeline.WAVE_HIT) {
            // Rain coming down on everyone, thunder rolling in from far off
            if (t % 3 == 0) {
                for (ServerPlayerEntity p : audience) {
                    world.spawnParticles(p, ParticleTypes.FALLING_WATER, true, p.getX(), p.getY() + 8, p.getZ(), 30, 10, 2, 10, 0);
                    world.spawnParticles(p, ParticleTypes.SPLASH, true, p.getX(), p.getY() + 0.1, p.getZ(), 12, 8, 0, 8, 0);
                }
            }
            if (t == 40 || t == 78) {
                for (ServerPlayerEntity p : audience) p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.6f, 0.5f);
            }
            if (t % 20 == 0) {
                for (ServerPlayerEntity p : audience) p.playSoundToPlayer(SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER, 1.0f, 0.7f);
            }
        }
        if (t == GreedTimeline.WAVE_AT) {
            // Behind him a wall of water rises and rolls over the island towards everyone
            Vec3d toward = Vec3d.ZERO;
            double far = 0;
            for (ServerPlayerEntity p : audience) {
                Vec3d d = p.getPos().subtract(tideBase).multiply(1, 0, 1);
                toward = toward.add(d);
                far = Math.max(far, d.length());
            }
            Vec3d dir = toward.lengthSquared() > 1e-4 ? toward.normalize() : new Vec3d(-1, 0, 0);
            far = MathHelper.clamp(far, 6, 45);
            int travel = GreedTimeline.WAVE_HIT - GreedTimeline.WAVE_AT;
            Vec3d from = tideBase.subtract(dir.multiply(24));
            water.add(new GreedWater.Wave(world, from, dir, 64, (24 + far) / travel, travel + 12));
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 6.0f, 0.4f);
            for (ServerPlayerEntity p : audience) p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 1.0f, 0.5f);
        }
        if (t == GreedTimeline.WAVE_HIT) {
            // It breaks over everyone: under the water
            for (ServerPlayerEntity p : audience) {
                Cinematics.screen(p, BossScreenPayload.DEEP, 0, GreedTimeline.UNDER_FADE);
                p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.MASTER, 1.0f, 0.6f);
                p.playSoundToPlayer(SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundCategory.MASTER, 1.0f, 0.8f);
            }
        }
        if (t > GreedTimeline.WAVE_HIT && t < GreedTimeline.SURFACE_AT && t % 12 == 0) {
            for (ServerPlayerEntity p : audience) {
                p.playSoundToPlayer(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE, SoundCategory.MASTER, 1.0f, 0.8f);
                p.playSoundToPlayer(SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.MASTER, 0.8f, 0.6f + random.nextFloat() * 0.3f);
            }
        }
        if (t == GreedTimeline.RESHAPE_AT) {
            // Under the water: he grows, and the tide carries everyone to the manor
            for (GreedWater.Piece piece : water) piece.remove();
            water.clear();
            getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(PHASE_TWO_SCALE);
            getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.32);
            getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(boosted(9));
            equipStack(EquipmentSlot.MAINHAND, MagicSpells.item(MagicSpells.NETHERITE_STAFF));
            if (arena) {
                GreedManor.prepare(world);
                List<Vec3d> spots = GreedManor.arrivals(audience.size());
                for (int i = 0; i < audience.size(); i++) {
                    ServerPlayerEntity p = audience.get(i);
                    Vec3d at = spots.get(i);
                    p.teleport(world, at.x, at.y, at.z, GreedManor.yawToCenter(at), -8f);
                    p.setVelocity(Vec3d.ZERO);
                    p.fallDistance = 0;
                }
                tideBase = GreedManor.CENTER.subtract(0, 1, 0).add(0, 1, 0);
                requestTeleport(tideBase.x, tideBase.y + GreedTimeline.spoutLift(t), tideBase.z);
            }
            setYaw(0);
            setBodyYaw(0);
            setHeadYaw(0);
            spout = new GreedWater.Spout(world, tideBase, 2.8f);
            spout.grow(13f, 12);
            water.add(spout);
        }
        if (t == GreedTimeline.SURFACE_AT) {
            // The water lifts on the manor garden: he's high in the spout between the fountains
            for (ServerPlayerEntity p : audience) {
                Cinematics.screen(p, BossScreenPayload.CLEAR, 0, GreedTimeline.SURFACE_TICKS);
                p.playSoundToPlayer(SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundCategory.MASTER, 1.0f, 0.7f);
                p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.MASTER, 1.0f, 0.8f);
            }
        }
        if (t >= GreedTimeline.SURFACE_AT && t < GreedTimeline.SONG_AT && t % 4 == 0) {
            world.spawnParticles(ParticleTypes.SPLASH, tideBase.x, tideBase.y + 0.2, tideBase.z, 20, 3, 0.1, 3, 0.3);
        }
        if (t == GreedTimeline.SONG_AT - 8) {
            setPoseState(POSE_SLAM);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 4.0f, 0.7f);
        }
        if (t == GreedTimeline.SONG_AT) {
            // The spout bursts and he comes down with a crash: a ring of water rolls out, the song starts
            setPosition(tideBase.x, tideBase.y, tideBase.z);
            setPoseState(POSE_SMASH);
            if (spout != null) spout.end();
            spout = null;
            water.add(new GreedWater.Ring(tideBase, 1.1, 30, 1.8f));
            water.add(new GreedWater.Ring(tideBase, 0.75, 22, 1.1f));
            water.add(new GreedWater.Column(world, tideBase, 3.4f, 9f, 0, 2, 4));
            world.spawnParticles(ParticleTypes.SPLASH, tideBase.x, tideBase.y + 1, tideBase.z, 300, 3, 1.5, 3, 0.8);
            world.spawnParticles(ParticleTypes.CLOUD, tideBase.x, tideBase.y + 0.5, tideBase.z, 60, 3, 0.4, 3, 0.15);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 5.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10000f, 0.7f);
            strikeCosmetic(world, tideBase.add(4, 0, -3));
            for (ServerPlayerEntity p : audience) {
                Cinematics.unfreeze(p);
                Cinematics.flash(p, 0.6f, 20);
                double d = p.getPos().distanceTo(tideBase);
                if (d < 12 && !p.isCreative() && !p.isSpectator()) {
                    Vec3d away = p.getPos().subtract(tideBase).multiply(1, 0, 1);
                    away = away.lengthSquared() > 1e-4 ? away.normalize() : new Vec3d(0, 0, 1);
                    p.setVelocity(away.x * (1.3 - d / 12), 0.5, away.z * (1.3 - d / 12));
                    p.velocityModified = true;
                }
            }
            MagicSpells.shake(world, tideBase, 40, 1.0f, 20);
            playSong(PHASE_TWO, GreedTimeline.SONG_FADE);
        }
        if (t >= GreedTimeline.TRANSITION_END) {
            untouchable = false;
            setNoGravity(false);
            endAttack();
            rest = 30;
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

    /** Anvil Slam: an anvil shaking over each of you, its shadow under you, then down. */
    private void tickAnvilSlam(ServerWorld world) {
        int t = actionTicks;
        setPoseState(POSE_CAST);
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t == 1 || frenzy && t == 40) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0f, 1.2f);
            for (ServerPlayerEntity p : fighters) {
                water.add(new GreedWater.Anvil(world, p, 11f));
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.HOSTILE, 1.0f, 0.8f);
            }
        }
        world.spawnParticles(GOLD, getX(), getY() + getHeight() + 0.3, getZ(), 3, 0.5, 0.2, 0.5, 0);
        if (t >= (frenzy ? 70 : 30)) endAttack();
    }

    /** An anvil's down: whoever's under it is crushed (a helmet helps) and dazed a moment. */
    void anvilLands(ServerWorld world, GreedWater.Anvil anvil) {
        Vec3d at = anvil.landing();
        for (ServerPlayerEntity p : fighters) {
            double dx = p.getX() - at.x, dz = p.getZ() - at.z, dy = p.getY() - at.y;
            if (dx * dx + dz * dz > GreedWater.Anvil.RADIUS * GreedWater.Anvil.RADIUS || dy < -1.5 || dy > 3) continue;
            p.timeUntilRegen = 0;
            p.damage(getDamageSources().fallingAnvil(this), boosted(anvil.damage));
            p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, 30, 2));
            p.setVelocity(p.getVelocity().multiply(0.2, 0, 0.2).add(0, -0.3, 0));
            p.velocityModified = true;
            Cinematics.flash(p, 0.35f, 8);
        }
        MagicSpells.shake(world, at, 10, 0.5f, 8);
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

    // ---- Water Sprout: he drives his staff into the ground and water bursts up out of it all round him ----

    /** One spot about to burst: where, and how long until it does. */
    static final class Sprout {
        static final int CHARGE = 10, GUSH = 16;
        static final double ON_TOP = 1.3, NEAR = 3.2;
        final Vec3d at;
        int age;
        /** The water itself: the ripple, then the column shooting up and falling back. */
        GreedWater.Column column;

        Sprout(Vec3d at) {
            this.at = at;
        }
    }

    private void tickWaterSprout(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_SLAM);
            swingHand(Hand.MAIN_HAND);
            int made = 0;
            for (int tries = 0; tries < 80 && made < 10; tries++) {
                double a = random.nextDouble() * MathHelper.TAU, d = 3 + random.nextDouble() * 13;
                Vec3d spot = standingSpot(getPos().add(Math.cos(a) * d, 0, Math.sin(a) * d));
                if (spot == null) continue;
                boolean crowded = false;
                for (Sprout s : sprouts) crowded |= s.at.squaredDistanceTo(spot) < 2.5 * 2.5;
                if (crowded) continue;
                sprouts.add(new Sprout(spot));
                made++;
            }
            world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 60, 1.0, 0.2, 1.0, 0.3);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.1, getZ(), 40, 1.0, 0.1, 1.0, 0.15);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t >= Sprout.CHARGE + Sprout.GUSH + 6) endAttack();
    }

    private void tickSprouts(ServerWorld world) {
        Iterator<Sprout> it = sprouts.iterator();
        while (it.hasNext()) {
            Sprout s = it.next();
            s.age++;
            Vec3d at = s.at;
            if (s.column == null) {
                // The ground ripples and bubbles where it'll burst; then a column of water shoots up out of it, foam
                // boiling on top, and falls back (GreedWater.Column)
                s.column = new GreedWater.Column(world, at, 2.2f, 8.5f, Sprout.CHARGE - s.age, 3, Sprout.GUSH - 8);
                water.add(s.column);
            }
            if (s.age < Sprout.CHARGE) {
                world.spawnParticles(ParticleTypes.FISHING, at.x, at.y + 0.1, at.z, 2, 0.5, 0, 0.5, 0.02);
                if (s.age == 1) world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.8f);
                continue;
            }
            if (s.age == Sprout.CHARGE) {
                // It bursts: whoever is on it is shot high into the air, whoever is near it not so high. It hurts nobody.
                for (ServerPlayerEntity p : fighters) {
                    double d = Math.sqrt(MathHelper.square(p.getX() - at.x) + MathHelper.square(p.getZ() - at.z));
                    if (d > Sprout.NEAR || p.getY() < at.y - 1 || p.getY() > at.y + 3) continue;
                    double up = d <= Sprout.ON_TOP ? 1.9 : MathHelper.lerp((d - Sprout.ON_TOP) / (Sprout.NEAR - Sprout.ON_TOP), 1.15, 0.7);
                    if (p.getVelocity().y >= up) continue;
                    p.setVelocity(p.getVelocity().x * 0.3, up, p.getVelocity().z * 0.3);
                    p.velocityModified = true;
                    p.fallDistance = 0;
                    floating.add(p.getUuid());
                }
            }
            if (s.age >= Sprout.CHARGE + Sprout.GUSH) it.remove();
        }
        // Shot up by the water, not hurt by the fall back down
        floating.removeIf(id -> {
            ServerPlayerEntity p = fighter(id);
            if (p == null) return true;
            if (p.isOnGround() && p.getVelocity().y <= 0) return true;
            p.fallDistance = 0;
            return false;
        });
    }

    // ---- Dive: he dives into the ground, a wake running under it, and bursts out under one of you ----

    private void tickDive(ServerWorld world) {
        int crouch = 12, under = frenzy ? 26 : 34, lock = 12, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            diveFrom = getPos();
            diveTo = null;
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_PREPARE_RAM, SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t < crouch) {
            world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 10, 0.8, 0.05, 0.8, 0.1);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.2, getZ(), 4, 0.6, 0.05, 0.6, 0.02);
            return;
        }
        if (t == crouch) {
            // Down into the ground with a splash
            untouchable = true;
            setInvisible(true);
            setNoGravity(true);
            world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 120, 0.8, 0.4, 0.8, 0.5);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.2, getZ(), 60, 0.8, 0.2, 0.8, 0.2);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 2.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        int u = t - crouch;
        ServerPlayerEntity target = fighter(focus);
        if (u < under) {
            // Where he'll come up follows you, until a moment before he does
            if (target != null && (diveTo == null || u < under - lock)) {
                Vec3d spot = standingSpot(target.getPos());
                if (spot != null) diveTo = spot;
            }
            if (diveTo == null) diveTo = diveFrom;
            // His wake, running under the ground towards it
            Vec3d wake = diveFrom.lerp(diveTo, Math.min(1, (u + 1) / (double) (under - 4)));
            double ground = groundBelow(wake.add(0, 2, 0), 6);
            if (!Double.isNaN(ground)) {
                world.spawnParticles(ParticleTypes.FISHING, wake.x, ground + 0.1, wake.z, 6, 0.4, 0, 0.4, 0.05);
                world.spawnParticles(ParticleTypes.SPLASH, wake.x, ground + 0.1, wake.z, 4, 0.3, 0, 0.3, 0.1);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, getWorld().getBlockState(BlockPos.ofFloored(wake.x, ground - 0.5, wake.z))),
                        wake.x, ground + 0.1, wake.z, 3, 0.3, 0, 0.3, 0.1);
            }
            if (u % 6 == 0) world.playSound(null, wake.x, wake.y, wake.z, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 1.5f, 0.5f);
            if (u >= under - lock) {
                // The ground churning where he'll burst out
                for (int i = 0; i < 10; i++) {
                    double a = i * MathHelper.TAU / 10 + u * 0.3;
                    world.spawnParticles(ParticleTypes.SPLASH, diveTo.x + Math.cos(a) * 2.8, diveTo.y + 0.1, diveTo.z + Math.sin(a) * 2.8, 1, 0, 0.05, 0, 0);
                }
                world.spawnParticles(ParticleTypes.BUBBLE_POP, diveTo.x, diveTo.y + 0.2, diveTo.z, 6, 1.0, 0.05, 1.0, 0.03);
                if (u % 4 == 0) world.playSound(null, diveTo.x, diveTo.y, diveTo.z, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f + u * 0.01f);
            }
            return;
        }
        if (u == under) {
            // He bursts up out of the ground under you
            setNoGravity(false);
            setInvisible(false);
            untouchable = false;
            requestTeleport(diveTo.x, diveTo.y, diveTo.z);
            setVelocity(0, 1.25, 0);
            velocityModified = true;
            setPoseState(POSE_SLAM);
            for (ServerPlayerEntity p : fighters) {
                double d = p.getPos().distanceTo(diveTo);
                if (d > 2.8) continue;
                p.damage(getDamageSources().mobAttack(this), boosted(14f));
                p.setVelocity(p.getVelocity().x, 1.1, p.getVelocity().z);
                p.velocityModified = true;
            }
            world.spawnParticles(ParticleTypes.SPLASH, diveTo.x, diveTo.y + 0.5, diveTo.z, 150, 0.8, 0.6, 0.8, 0.6);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), diveTo.x, diveTo.y + 0.3, diveTo.z, 80, 1.0, 0.3, 1.0, 0.3);
            world.playSound(null, diveTo.x, diveTo.y, diveTo.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, diveTo.x, diveTo.y, diveTo.z, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0f, 1.2f);
            world.playSound(null, diveTo.x, diveTo.y, diveTo.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.5f, 0.9f);
            return;
        }
        // ...and comes down hard where he came up
        if (u > under + 4 && isOnGround() || u > under + 60) {
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getPos().subtract(getPos());
                if (to.lengthSquared() > 4.5 * 4.5 || Math.abs(to.y) > 2) continue;
                p.damage(getDamageSources().mobAttack(this), boosted(12f));
                Vec3d flat = to.multiply(1, 0, 1);
                if (flat.lengthSquared() > 1e-4) p.takeKnockback(1.2, -flat.x, -flat.z);
            }
            splash(world, 9, 6f);
            world.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 2, 0.5, 0.1, 0.5, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 2.5f, 0.7f);
            for (ServerPlayerEntity p : audience(world)) {
                if (p.squaredDistanceTo(this) < 20 * 20) Cinematics.flash(p, 0.2f, 8);
            }
            endAttack();
        }
    }

    // ---- Giant Slam: he shakes his head, grows four times his size and brings his staff down on one spot ----

    private static final int GIANT_SHAKE = 22, GIANT_GROW = 20, GIANT_HOLD = 10, GIANT_AFTER = 16;
    private static final float GIANT_GROWTH = 4f;
    /** How far round the spot the Giant Slam hits, in his own (pre-slam) sizes: 7.5 blocks in phase 2. */
    public static final float GIANT_REACH = 5f;

    private void tickGiantSlam(ServerWorld world) {
        int t = actionTicks, grown = GIANT_SHAKE + GIANT_GROW, slam = grown + GIANT_HOLD;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            giantBase = (float) getAttributeInstance(EntityAttributes.GENERIC_SCALE).getBaseValue();
            setPoseState(POSE_SHAKE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WOLF_SHAKE, SoundCategory.HOSTILE, 2.5f, 0.6f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        ServerPlayerEntity target = fighter(focus);
        if (t < GIANT_SHAKE) {
            // Shaking his head, water flying off him
            if (target != null) faceYaw(yawTo(target.getPos()));
            Vec3d head = getPos().add(0, getHeight() * 0.9, 0);
            world.spawnParticles(ParticleTypes.SPLASH, head.x, head.y, head.z, 6, 0.4, 0.2, 0.4, 0.2);
            if (t == 12) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WOLF_SHAKE, SoundCategory.HOSTILE, 2.5f, 0.5f);
            return;
        }
        float reach = giantBase * 4.5f, radius = giantBase * GIANT_REACH;
        if (t == GIANT_SHAKE) {
            // Where the staff will come down: set now, right in front of him
            if (target != null) faceYaw(yawTo(target.getPos()));
            Vec3d front = getPos().add(Vec3d.fromPolar(0, getYaw()).multiply(reach));
            giantSpot = standingSpot(front);
            if (giantSpot == null) giantSpot = new Vec3d(front.x, getY(), front.z);
            setPoseState(POSE_SLAM);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t <= slam && giantSpot != null && t % 2 == 0) {
            // The spot, ringed in water and gold, tightening
            double r = radius * (1 + 0.35 * (slam - t) / (double) (slam - GIANT_SHAKE));
            for (int i = 0; i < 28; i++) {
                double a = i * MathHelper.TAU / 28 + t * 0.1;
                world.spawnParticles(i % 2 == 0 ? ParticleTypes.SPLASH : GOLD, giantSpot.x + Math.cos(a) * r, giantSpot.y + 0.15, giantSpot.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
            }
            world.spawnParticles(ParticleTypes.BUBBLE_POP, giantSpot.x, giantSpot.y + 0.2, giantSpot.z, 4, radius * 0.5, 0.05, radius * 0.5, 0.02);
        }
        if (t < grown) {
            // Growing, four times over
            float u = (t - GIANT_SHAKE + 1) / (float) GIANT_GROW;
            float eased = u * u * (3 - 2 * u);
            getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(giantBase * (1 + (GIANT_GROWTH - 1) * eased));
            world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + getHeight() * 0.5, getZ(), 10, getWidth() * 0.6, getHeight() * 0.4, getWidth() * 0.6, 0.1);
            if (t % 5 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 2.0f, 1.0f - u * 0.6f);
            return;
        }
        if (t < slam) return;
        if (t == slam && giantSpot != null) {
            setPoseState(POSE_SMASH);
            swingHand(Hand.MAIN_HAND);
            Vec3d c = giantSpot;
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getPos().subtract(c);
                double flat = Math.sqrt(to.x * to.x + to.z * to.z);
                if (flat > radius || Math.abs(to.y) > 3) continue;
                p.damage(getDamageSources().mobAttack(this), boosted(18f));
                Vec3d away = flat > 0.1 ? new Vec3d(to.x / flat, 0, to.z / flat) : Vec3d.fromPolar(0, getYaw());
                p.setVelocity(away.x * 2.4, 0.95, away.z * 2.4);
                p.velocityModified = true;
            }
            rings.add(new Ring(c, radius + 6, 0.8, 0, Ring.WATER, 0));
            water.add(new GreedWater.Ring(c, 1.0, radius + 4, 1.6f));
            rings.add(new Ring(c, radius + 3, 0.55, 0, Ring.PULSE, 3));
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y + 0.4, c.z, 250, radius * 0.6, 0.4, radius * 0.6, 0.6);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), c.x, c.y + 0.3, c.z, 200, radius * 0.6, 0.3, radius * 0.6, 0.3);
            world.spawnParticles(GOLD, c.x, c.y + 1, c.z, 60, radius * 0.5, 0.6, radius * 0.5, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 3.0f, 0.6f);
            for (ServerPlayerEntity p : audience(world)) {
                if (p.squaredDistanceTo(c) < 30 * 30) Cinematics.flash(p, 0.35f, 12);
            }
        }
        if (t >= slam + GIANT_AFTER) {
            // Poof: back down to his size, and on with the fight
            Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
            world.spawnParticles(ParticleTypes.POOF, c.x, c.y, c.z, 120, getWidth() * 0.6, getHeight() * 0.4, getWidth() * 0.6, 0.08);
            world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 60, getWidth() * 0.5, getHeight() * 0.4, getWidth() * 0.5, 0.05);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 2.5f, 0.8f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 2.0f, 0.6f);
            endAttack();
        }
    }

    private void faceYaw(float yaw) {
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    /** Back to the size he was before a Giant Slam, however it ended. */
    private void shrinkBack() {
        if (giantBase <= 0) return;
        getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(giantBase);
        giantBase = 0;
        giantSpot = null;
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

    // ---- Chest Rain: chests spiral up round him into the sky, then rain down all over the island ----

    /** Ticks: the chests rising round him, then the rain; how high they fall from; how many chests ring him. */
    public static final int CHEST_CAST = 30, CHEST_RAIN = 80, CHEST_RING = 8;
    static final double CHEST_HEIGHT = 22, CHEST_SIZE = 0.875;
    /** A chest that lands on you (before {@link #DAMAGE_DEALT}): phase 1, phase 2. */
    static final float CHEST_HIT_ONE = 10f, CHEST_HIT_TWO = 13f;

    /** One chest falling out of the sky, the gold ring on the ground showing where; it falls through the island. */
    static final class FallingChest {
        final DisplayEntity.BlockDisplayEntity display;
        final double x, z, ground;
        final float yaw, tumble;
        final Set<UUID> hit = new HashSet<>();
        double y, speed = 0.55;
        int age;
        boolean through;

        FallingChest(DisplayEntity.BlockDisplayEntity display, double x, double y, double z, double ground, float yaw, float tumble) {
            this.display = display;
            this.x = x;
            this.y = y;
            this.z = z;
            this.ground = ground;
            this.yaw = yaw;
            this.tumble = tumble;
        }
    }

    private final List<FallingChest> chests = new ArrayList<>();
    private final DisplayEntity.BlockDisplayEntity[] castChests = new DisplayEntity.BlockDisplayEntity[CHEST_RING];
    /** Ticks of rain left (it goes on while he walks and punches). */
    private int chestRain;
    private int chestHits;

    /** For tests: how many chests are falling right now. */
    public int fallingChests() {
        return chests.size();
    }

    public boolean isRainingChests() {
        return chestRain > 0;
    }

    /** For tests: how many times a chest has come down on someone. */
    public int chestHits() {
        return chestHits;
    }

    private DisplayEntity.BlockDisplayEntity chestDisplay(ServerWorld world, Vec3d at, boolean trapped) {
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(world);
        if (d == null) return null;
        d.setBlockState((trapped ? Blocks.TRAPPED_CHEST : Blocks.CHEST).getDefaultState());
        d.setTeleportDuration(1);
        d.setViewRange(5f);
        d.setTransformation(chestTransform(0, 0, 0.01f));
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        world.spawnEntity(d);
        return d;
    }

    /** A chest centred on its display's position, turned {@code yaw} and tipped {@code tumble}, {@code size} big. */
    private static AffineTransformation chestTransform(float yaw, float tumble, float size) {
        Quaternionf rot = new Quaternionf().rotationY(yaw).rotateX(tumble);
        Vector3f half = new Vector3f(-0.5f, -0.5f, -0.5f).mul(size).rotate(rot);
        return new AffineTransformation(half, rot, new Vector3f(size, size, size), new Quaternionf());
    }

    /**
     * The cast: he stamps his staff and eight chests burst up out of the ground round him, circling him faster and
     * faster as they rise in a spiral, gold streaming after them, until they shoot straight up into the sky. Then he's
     * free to walk and punch while the chests come back down on all of you for four seconds.
     */
    private void tickChestRain(ServerWorld world) {
        int t = actionTicks, rise = CHEST_CAST - 8;
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t == 1) {
            setPoseState(POSE_SLAM);
            swingHand(Hand.MAIN_HAND);
            for (int i = 0; i < CHEST_RING; i++) castChests[i] = chestDisplay(world, getPos(), i % 2 == 1);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.1, getZ(), 50, 1.6, 0.1, 1.6, 0.15);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.HOSTILE, 2.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.HOSTILE, 2.0f, 0.7f);
        }
        if (t == 6) setPoseState(POSE_CAST);
        // The ring of chests: out of the ground, round and up, faster and faster, then straight up into the sky
        float u = Math.min(1f, t / (float) rise);
        double spin = 1.2 * t + 0.035 * t * t, radius = 2.8 - 1.4 * u, height = -0.4 + 4.2 * Math.pow(u, 1.4);
        if (t > rise) height += 0.9 * MathHelper.square(t - rise);
        float size = (float) Math.min(1.0, t / 6.0) * (float) CHEST_SIZE;
        for (int i = 0; i < CHEST_RING; i++) {
            DisplayEntity.BlockDisplayEntity d = castChests[i];
            if (d == null) continue;
            double a = i * MathHelper.TAU / CHEST_RING + spin * 0.05;
            Vec3d at = getPos().add(Math.cos(a) * radius, height + (i % 2) * 0.5, Math.sin(a) * radius);
            d.setPosition(at.x, at.y, at.z);
            d.setTransformation(chestTransform((float) -a, t * 0.15f * (i % 2 == 0 ? 1 : -1), size));
            d.setStartInterpolation(0);
            d.setInterpolationDuration(1);
            if (t % 2 == 0) world.spawnParticles(GOLD, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0);
        }
        if (t % 3 == 0 && t <= rise) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.HOSTILE, 1.5f, 0.5f + t / (float) rise);
        }
        world.spawnParticles(GOLD, getX(), getY() + getHeight() + 0.3, getZ(), 2, 0.4, 0.2, 0.4, 0);
        if (t == rise) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.HOSTILE, 2.5f, 0.5f);
        }
        if (t >= CHEST_CAST) {
            for (int i = 0; i < CHEST_RING; i++) {
                if (castChests[i] != null) castChests[i].discard();
                castChests[i] = null;
            }
            chestRain = CHEST_RAIN;
            world.playSound(null, getX(), getY() + 20, getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0f, 1.4f);
            for (ServerPlayerEntity p : fighters) p.playSoundToPlayer(SoundEvents.BLOCK_VAULT_EJECT_ITEM, SoundCategory.HOSTILE, 1.5f, 0.6f);
            endAttack();
            // He walks and punches through it; nothing else until it's over
            rest = Math.max(rest, CHEST_RAIN);
        }
    }

    /** The rain itself: new chests over all of you, and each falling one hitting whoever it lands on. */
    private void tickChests(ServerWorld world) {
        if (chestRain > 0) {
            chestRain--;
            int phase = getPhase();
            // Some over where each of you stands (so keep moving), more at random round all of you
            int aimed = phase == 1 ? 12 : frenzy ? 6 : 8, scatter = phase == 1 ? 2 : 1;
            if ((CHEST_RAIN - chestRain) % aimed == 1) {
                for (ServerPlayerEntity p : fighters) dropChest(world, p.getPos());
            }
            if (chestRain % scatter == 0 && !fighters.isEmpty()) {
                ServerPlayerEntity p = fighters.get(random.nextInt(fighters.size()));
                double a = random.nextDouble() * MathHelper.TAU, d = 1.5 + random.nextDouble() * 8;
                dropChest(world, p.getPos().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            }
        }
        float damage = boosted(getPhase() == 1 ? CHEST_HIT_ONE : CHEST_HIT_TWO);
        Iterator<FallingChest> it = chests.iterator();
        while (it.hasNext()) {
            FallingChest c = it.next();
            c.age++;
            double from = c.y;
            c.speed = Math.min(1.6, c.speed + 0.07);
            c.y -= c.speed;
            // Where it'll land: a gold ring, tightening
            if (from > c.ground + 0.5 && c.age % 2 == 0) {
                double r = 0.45 + 0.5 * Math.min(1, (from - c.ground) / CHEST_HEIGHT);
                for (int i = 0; i < 8; i++) {
                    double a = i * MathHelper.TAU / 8 + c.age * 0.2;
                    world.spawnParticles(GOLD, c.x + Math.cos(a) * r, c.ground + 0.12, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            // Whoever it comes down on, once each
            double half = CHEST_SIZE / 2;
            net.minecraft.util.math.Box swept = new net.minecraft.util.math.Box(c.x - half, c.y - half, c.z - half, c.x + half, from + half, c.z + half);
            for (ServerPlayerEntity p : fighters) {
                if (c.hit.contains(p.getUuid()) || !p.getBoundingBox().intersects(swept)) continue;
                c.hit.add(p.getUuid());
                chestHits++;
                if (p.damage(getDamageSources().fallingBlock(this), damage)) {
                    p.setVelocity(p.getVelocity().x * 0.3, -0.4, p.getVelocity().z * 0.3);
                    p.velocityModified = true;
                }
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 1.5f, 0.8f);
                world.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1.6, p.getZ(), 12, 0.3, 0.2, 0.3, 0.2);
            }
            // Through the island with a thud and a spill of gold, and on down out of sight
            if (!c.through && c.y - half <= c.ground) {
                c.through = true;
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getDefaultState()), c.x, c.ground + 0.2, c.z, 14, 0.35, 0.1, 0.35, 0.1);
                world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET)), c.x, c.ground + 0.4, c.z, 8, 0.3, 0.2, 0.3, 0.15);
                world.playSound(null, c.x, c.ground, c.z, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.HOSTILE, 1.2f, 0.6f + random.nextFloat() * 0.3f);
                world.playSound(null, c.x, c.ground, c.z, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.HOSTILE, 1.0f, 0.7f);
            }
            if (c.display != null) {
                c.display.setPosition(c.x, c.y, c.z);
                c.display.setTransformation(chestTransform(c.yaw, c.tumble + c.age * 0.04f, (float) CHEST_SIZE));
                c.display.setStartInterpolation(0);
                c.display.setInterpolationDuration(1);
            }
            if (c.y < c.ground - 3 || c.age > 80) {
                if (c.display != null) c.display.discard();
                it.remove();
            }
        }
    }

    /** A chest out of the sky over {@code at} (nothing over the void: there's nowhere for it to land). */
    private void dropChest(ServerWorld world, Vec3d at) {
        double ground = groundBelow(at.add(0, 4, 0), 14);
        if (Double.isNaN(ground)) return;
        double y = ground + CHEST_HEIGHT + random.nextDouble() * 4;
        DisplayEntity.BlockDisplayEntity d = chestDisplay(world, new Vec3d(at.x, y, at.z), random.nextInt(4) == 0);
        chests.add(new FallingChest(d, at.x, y, at.z, ground, random.nextFloat() * MathHelper.TAU, (random.nextFloat() - 0.5f) * 0.6f));
    }

    private void clearChests() {
        for (FallingChest c : chests) if (c.display != null) c.display.discard();
        chests.clear();
        for (int i = 0; i < CHEST_RING; i++) {
            if (castChests[i] != null) castChests[i].discard();
            castChests[i] = null;
        }
        chestRain = 0;
    }

    // ---- His water, anvils and cloud ----

    private void tickWater(ServerWorld world) {
        for (GreedWater.Piece piece : new ArrayList<>(water)) {
            if (!piece.tick(world, this)) {
                piece.remove();
                water.remove(piece);
            }
        }
    }

    List<ServerPlayerEntity> fighterList() {
        return fighters;
    }

    ServerPlayerEntity fighterById(UUID id) {
        return fighter(id);
    }

    /** The top of the ground under {@code at}, looking down from a little over {@code near} (or {@code near} if none). */
    double groundUnder(Vec3d at, double near) {
        double g = groundBelow(new Vec3d(at.x, near + 2, at.z), 8);
        return Double.isNaN(g) ? near : g;
    }

    /** A water bolt from the cloud found someone: a projectile hit (a shield stops it), and a little shove. */
    void boltHit(ServerPlayerEntity p, GreedWater.Bolt bolt) {
        // (Exactly what the bolt says, not boosted: 6 a bolt)
        if (p.damage(getDamageSources().mobProjectile(this, this), bolt.damage)) {
            Vec3d push = bolt.vel.multiply(1, 0, 1);
            if (push.lengthSquared() > 1e-4) {
                push = push.normalize().multiply(0.25);
                p.addVelocity(push.x, 0.05, push.z);
                p.velocityModified = true;
            }
        }
    }

    // ---- Cloud Ride: up onto a cloud, round over everyone raking the ground with water, then down with a crash ----

    public static final int RIDE_CROUCH = 6, RIDE_LEAP = 12, RIDE_FLY = 140, RIDE_DROP = 7;
    public static final double RIDE_HEIGHT = 11;
    /** What each of the Cloud Ride's water bolts does (dealt as is). */
    public static final float BOLT_DAMAGE = 6f;

    private void tickCloudRide(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CROUCH);
            getNavigation().stop();
            rideFrom = getPos();
            Vec3d middle = Vec3d.ZERO;
            for (ServerPlayerEntity p : fighters) middle = middle.add(p.getPos());
            flyCenter = fighters.isEmpty() ? getPos() : middle.multiply(1.0 / fighters.size());
            flyCenter = new Vec3d(flyCenter.x, rideFrom.y, flyCenter.z);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_CHARGE, SoundCategory.HOSTILE, 2.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_2.value(), SoundCategory.HOSTILE, 2.0f, 0.7f);
        }
        if (t < RIDE_CROUCH) {
            setVelocity(0, getVelocity().y, 0);
            world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 0.1, getZ(), 8, 0.6, 0, 0.6, 0.1);
            return;
        }
        setNoGravity(true);
        setVelocity(Vec3d.ZERO);
        int leap = t - RIDE_CROUCH;
        if (leap <= RIDE_LEAP) {
            // Up into the sky; the cloud gathers under his feet on the way
            if (leap == 0) {
                setPoseState(POSE_SLAM);
                world.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 0.2, getZ(), 80, 1.0, 0.2, 1.0, 0.5);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_JUMP, SoundCategory.HOSTILE, 3.0f, 0.6f);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 2.0f, 0.8f);
            }
            double u = leap / (double) RIDE_LEAP;
            setPosition(rideFrom.x, rideFrom.y + RIDE_HEIGHT * (1 - (1 - u) * (1 - u)), rideFrom.z);
            if (leap == 5) {
                cloud = new GreedWater.Cloud(world, this);
                water.add(cloud);
            }
            return;
        }
        int fly = leap - RIDE_LEAP;
        if (fly <= RIDE_FLY) {
            // Round and round over everyone, bobbing, the staff pointed down, water pouring out of it like a minigun
            double a = fly * MathHelper.TAU / 110.0 + (rideFrom.x + rideFrom.z) * 0.1;
            double r = 9 + 3 * Math.sin(fly * 0.045);
            Vec3d want = flyCenter.add(Math.cos(a) * r, RIDE_HEIGHT + 1.2 * Math.sin(fly * 0.13), Math.sin(a) * r);
            Vec3d now = getPos().lerp(want, fly < 10 ? 0.15 : 0.3);
            setPosition(now.x, now.y, now.z);
            setPoseState(POSE_AIM);
            if (fly % 24 == 1 || fighter(focus) == null) {
                ServerPlayerEntity next = pick(null);
                focus = next == null ? null : next.getUuid();
            }
            ServerPlayerEntity target = fighter(focus);
            if (target != null) {
                getLookControl().lookAt(target, 90f, 90f);
                float yaw = yawTo(target.getPos());
                setYaw(yaw);
                setBodyYaw(yaw);
                setHeadYaw(yaw);
                if (fly % 2 == 0) shootBolt(world, target, fly);
            }
            if (fly % 12 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundCategory.HOSTILE, 2.0f, 1.4f);
            return;
        }
        int drop = fly - RIDE_FLY;
        if (drop == 1) {
            // Off the cloud and straight down
            if (cloud != null) cloud.burst(world);
            cloud = null;
            setPoseState(POSE_SMASH);
            dropFrom = getPos();
            double ground = groundBelow(getPos(), 40);
            Vec3d under = new Vec3d(getX(), Double.isNaN(ground) ? rideFrom.y : ground, getZ());
            Vec3d spot = standingSpot(under);
            dropTo = spot != null ? spot : rideFrom;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 3.0f, 0.5f);
        }
        if (drop <= RIDE_DROP) {
            double u = drop / (double) RIDE_DROP;
            Vec3d at = dropFrom.lerp(dropTo, u * u);
            setPosition(at.x, at.y, at.z);
            if (drop == RIDE_DROP) rideCrash(world);
            return;
        }
        if (drop >= RIDE_DROP + 16) {
            setNoGravity(false);
            endAttack();
        }
    }

    private void shootBolt(ServerWorld world, ServerPlayerEntity target, int fly) {
        Vec3d from = getPos().add(0, getHeight() * 0.55, 0).add(Vec3d.fromPolar(0, getYaw()).multiply(0.9));
        Vec3d aim = target.getBoundingBox().getCenter().add(target.getVelocity().multiply(6));
        Vec3d spread = new Vec3d(random.nextGaussian(), random.nextGaussian() * 0.4, random.nextGaussian()).multiply(0.9);
        Vec3d vel = aim.add(spread).subtract(from).normalize().multiply(1.5);
        water.add(new GreedWater.Bolt(world, from, vel, BOLT_DAMAGE));
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 1.4f, 1.5f + random.nextFloat() * 0.4f);
        if (fly % 6 == 0) world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_DOLPHIN_SPLASH, SoundCategory.HOSTILE, 1.2f, 1.6f);
        world.spawnParticles(ParticleTypes.SPLASH, from.x, from.y, from.z, 6, 0.2, 0.2, 0.2, 0.2);
    }

    /** Down off the cloud with a heavy crash: whoever's near is hurt and thrown, and a ring of water rolls out. */
    private void rideCrash(ServerWorld world) {
        Vec3d c = getPos();
        for (ServerPlayerEntity p : fighters) {
            double d = p.getPos().distanceTo(c);
            if (d > 5 || Math.abs(p.getY() - c.y) > 3) continue;
            p.timeUntilRegen = 0;
            p.damage(getDamageSources().mobAttack(this), boosted(12f));
            Vec3d away = p.getPos().subtract(c).multiply(1, 0, 1);
            away = away.lengthSquared() > 1e-4 ? away.normalize() : new Vec3d(1, 0, 0);
            p.setVelocity(away.x * 1.4, 0.6, away.z * 1.4);
            p.velocityModified = true;
        }
        water.add(new GreedWater.Ring(c, 0.9, 16, 1.3f));
        rings.add(new Ring(c, 15, 0.45, 6f, Ring.WATER, 2));
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y + 0.5, c.z, 200, 2.5, 0.6, 2.5, 0.6);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), c.x, c.y + 0.2, c.z, 100, 2.5, 0.2, 2.5, 0.2);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0f, 0.4f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 3.0f, 0.6f);
        for (ServerPlayerEntity p : audience(world)) if (p.squaredDistanceTo(c) < 30 * 30) Cinematics.flash(p, 0.35f, 10);
        MagicSpells.shake(world, c, 24, 0.9f, 14);
    }

    // ---- Where the fight is ----

    /** The middle of the fight: the island's plain in phase 1, the manor's garden once the tide's carried everyone there. */
    private Vec3d arenaCenter() {
        return GreedManor.contains(getPos()) ? GreedManor.CENTER : GreedArena.CENTER;
    }

    /** Below this at {@code pos} you've fallen off (the island or the manor's grounds). */
    private static double voidY(Vec3d pos) {
        return pos.x > GreedManor.CORNER.getX() - 60 ? GreedManor.VOID_Y : GreedArena.VOID_Y;
    }

    /** In the manor the fight is the garden and the lawns: wander off for 2 s and the water brings you back. */
    private void keepInGarden(ServerWorld world) {
        if (!GreedManor.contains(getPos())) return;
        for (ServerPlayerEntity p : fighters) {
            if (GreedManor.inBounds(p.getPos()) || p.getY() < GreedManor.FLOOR_Y - 4) {
                strayTicks.remove(p.getUuid());
                continue;
            }
            int out = strayTicks.merge(p.getUuid(), 1, Integer::sum);
            if (out == 10) p.playSoundToPlayer(SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundCategory.HOSTILE, 1.0f, 1.2f);
            if (out < 40) continue;
            strayTicks.remove(p.getUuid());
            Vec3d back = GreedManor.returnSpot(p.getPos());
            world.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.8, 0.4, 0.3);
            p.teleport(world, back.x, back.y, back.z, p.getYaw(), p.getPitch());
            p.fallDistance = 0;
            world.spawnParticles(ParticleTypes.SPLASH, back.x, back.y + 1, back.z, 60, 0.4, 0.8, 0.4, 0.3);
            world.playSound(null, back.x, back.y, back.z, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 1.5f, 0.8f);
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
            Vec3d middle = arenaCenter();
            Vec3d at = middle.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            double ground = groundBelow(at.add(0, 30, 0), 80);
            at = new Vec3d(at.x, Double.isNaN(ground) ? middle.y - 25 : ground, at.z);
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
        for (ServerPlayerEntity p : world.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.getY() < voidY(p.getPos()))) {
            p.damage(world.getDamageSources().outOfWorld(), Float.MAX_VALUE);
        }
        if (getY() < voidY(getPos())) {
            Vec3d back = getPhase() == 2 && getX() > GreedManor.CORNER.getX() - 60 ? GreedManor.CENTER : GreedArena.LANDING;
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

    @Override
    public void remove(RemovalReason reason) {
        // Taken away (the attempt ended, a test is done with him): his solid water, anvils, cloud and chests go with him
        if (!getWorld().isClient && reason.shouldDestroy()) {
            for (GreedWater.Piece piece : water) piece.remove();
            water.clear();
            spout = null;
            cloud = null;
            clearChests();
        }
        super.remove(reason);
    }

    // ---- Helpers ----

    /**
     * Top of the first solid block at or below {@code pos}, looking at most {@code depth} blocks down; NaN over the void.
     * Barriers aren't ground (there are none in his arenas; they wall in the game tests).
     */
    private double groundBelow(Vec3d pos, int depth) {
        BlockPos.Mutable at = BlockPos.ofFloored(pos).mutableCopy();
        for (int i = 0; i < depth; i++) {
            BlockState state = getWorld().getBlockState(at);
            VoxelShape shape = state.getCollisionShape(getWorld(), at);
            if (!shape.isEmpty() && !state.isOf(Blocks.BARRIER)) return at.getY() + shape.getMax(Direction.Axis.Y);
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
