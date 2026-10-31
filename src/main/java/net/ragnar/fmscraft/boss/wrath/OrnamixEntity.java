package net.ragnar.fmscraft.boss.wrath;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
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
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
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
import net.ragnar.fmscraft.network.WrathSkyPayload;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.sound.ModSounds;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ORNAMIX, the Sin of Wrath. Fast, hard to pin down and not as tough as the others, but everything he does hurts. He
 * fights with a burning iron sword, always wreathed in fire. Two phases, each on its own song, attacks starting on the
 * beat.
 *
 * <h2>Phase 1 (100% to 40%): the red sky, N***as in Paris</h2>
 * Runs people down and cuts them (and sets them alight), sidesteps blows now and then, and between cuts picks one of:
 * <ul>
 *   <li><b>Stab and Leap</b>: a lunging stab at whoever is closest, then a leap back out of reach.</li>
 *   <li><b>Fire Wave</b>: raises his sword and cuts a crescent wall of flame across the pit. Too tall to jump: get
 *       out of its way.</li>
 *   <li><b>Flame Spin</b>: a whirl of fire round him as he spins towards people, throwing them far.</li>
 *   <li><b>Flamethrower</b>: a cone of fire from his hand that turns after its target; outrun it round him.</li>
 *   <li><b>Ignite</b> (Ragnars Magic Mod's spell): a ring of fire bursts out to 10 blocks. A circle shows how far.</li>
 *   <li><b>Sun</b>: leaps far back, then casts the Tome of the Sun: a growing sun drifts across the pit.</li>
 *   <li><b>Flaming Arrows</b>: draws a bow and looses a stream of burning arrows. Shields stop them.</li>
 *   <li><b>Flurry</b>: rushes in and cuts again and again, very fast.</li>
 *   <li><b>Dash Stab</b>: crouches (a line of flame shows the way), then charges straight through. If he runs into
 *       the wall his sword sticks: he's stuck there and takes extra damage.</li>
 *   <li><b>Floor Waves</b>: slams his sword down and rings of fire roll out along the floor. Jump each one, or be
 *       stuck in the flames for 5 seconds, burning.</li>
 *   <li><b>Haymaker</b>: rushes someone and punches them clean across the pit.</li>
 * </ul>
 *
 * <h2>Ragemode: see {@link OrnamixTimeline}</h2>
 *
 * <h2>Phase 2 (40% to 0%): Ragemode, Return to Zero</h2>
 * The sky goes almost black red, his eyes burn, the world blinks to the bass. Faster, shorter windups, harder hits, more
 * dodging. Everything from phase 1 (bigger: three fire waves, more rings, two dashes, two punches...) and:
 * <ul>
 *   <li><b>Meteors</b> (Tome of Meteor): charges up; a burning ring marks where each player stood, then a meteor
 *       crashes into each. It breaks nothing.</li>
 *   <li><b>Wither Skeletons</b> in armor climb out of the flames.</li>
 *   <li><b>Fireballs</b>: a stream of ghast fireballs. They break nothing; hit one back into him and it hurts.</li>
 *   <li><b>Inferno</b>: rises into the air over a huge circle of fire, then crashes down spinning in a firestorm that
 *       fills it. Afterwards he's dizzy and takes extra damage.</li>
 * </ul>
 * Anyone who leaves the pit (over the wall, into the stands) is thrown back in by the flames after 2 seconds.
 */
public class OrnamixEntity extends SinBossEntity {
    public static final BossSong PHASE_ONE = new BossSong("fmscraft:music.wrath.phase_one", 140, 1);
    /** 100 BPM: 0.6 s, the bass the world blinks to. The grid lines up with the bass hit at 19.25 s. */
    public static final BossSong PHASE_TWO = new BossSong("fmscraft:music.wrath.phase_two", 100, 1);
    public static final String SUMMON_TAG = "fmscraft.ornamix_summon";

    // ---- Tuning knobs ----
    /** Full health, for 5-7 players; {@link OrnamixTimeline#healthScale} takes it down for smaller groups. */
    public static final float MAX_HEALTH = 900;
    public static final float RAGE_AT = 0.4f;
    /** Damage he takes is multiplied by this per phase (on top of his armor), more while exposed, and capped per hit. */
    public static final float DAMAGE_TAKEN_ONE = 0.8f, DAMAGE_TAKEN_TWO = 0.9f, EXPOSED_BONUS = 1.5f, MAX_HIT = 30f;
    /** Every hit he deals is multiplied by this (and again by {@link #RAGE_DAMAGE} in Ragemode), then rounded up. */
    public static final float DAMAGE_DEALT = 1.35f, RAGE_DAMAGE = 1.2f;
    /** Chance he sidesteps a blow while he isn't attacking, and how long before he can again. */
    public static final float DODGE_ONE = 0.2f, DODGE_TWO = 0.35f;
    private static final int DODGE_COOLDOWN_ONE = 60, DODGE_COOLDOWN_TWO = 35;
    /** Ticks between attacks (he chases and cuts in between). */
    private static final int REST_ONE = 18, REST_TWO = 10;
    private static final double SPEED_ONE = 0.34, SPEED_TWO = 0.4;
    /** How long anyone can stay out of the pit before the flames throw them back in. */
    private static final int OUT_OF_PIT_TICKS = 40;
    private static final int MAX_SUMMONS = 6;

    // ---- Synced to the client for the model ----
    public static final int POSE_IDLE = 0, POSE_DRAW = 1, POSE_STAB = 2, POSE_RAISE = 3, POSE_CAST = 4, POSE_AIM = 5,
            POSE_BOW = 6, POSE_CROUCH = 7, POSE_SPIN_READY = 8, POSE_SPIN = 9, POSE_PUNCH_READY = 10, POSE_PUNCH = 11,
            POSE_STUNNED = 12, POSE_KNEEL = 13, POSE_ROAR = 14, POSE_SLAM = 15, POSE_STUCK = 16;
    private static final TrackedData<Integer> POSE = DataTracker.registerData(OrnamixEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> RAGE = DataTracker.registerData(OrnamixEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(1.0f, 0.35f, 0.08f), 1.2f);
    private static final DustParticleEffect DARK_EMBER = new DustParticleEffect(new Vector3f(0.55f, 0.04f, 0.02f), 1.5f);

    enum Action {
        ENTRANCE, CHASE, EXPOSED, TRANSITION,
        STAB_HOP, FIRE_WAVE, FLAME_SPIN, FLAMETHROWER, IGNITE, SUN, ARROWS, FLURRY, DASH_STAB, FLOOR_WAVE, HAYMAKER,
        METEOR, SUMMON, FIREBALLS, INFERNO
    }

    private Action action = Action.ENTRANCE;
    private int actionTicks;
    /** A second clock inside an attack, for the ones that close in first and only then start their windup. */
    private int stageTicks = -1;
    private int rest;
    private Action lastAttack;
    private final Map<Action, Integer> lastUsed = new EnumMap<>(Action.class);
    private int meleeCooldown, dodgeCooldown, exposedTicks;
    private int repeats;
    private UUID focus;
    private int focusTicks;
    private Vec3d dashDir = Vec3d.ZERO, spinCenter, entranceGround;
    private float aimYaw;
    private final Set<UUID> struck = new HashSet<>();
    private final Map<UUID, Integer> lastHit = new HashMap<>();
    private final Map<UUID, Integer> outOfPit = new HashMap<>();
    private final Map<UUID, Integer> lastPicked = new HashMap<>();
    private final List<WrathHazards.Hazard> hazards = new ArrayList<>();
    private final List<UUID> summons = new ArrayList<>();
    private final List<Vec3d> summonSpots = new ArrayList<>();
    private boolean untouchable = true, scaled, returnedHit;
    private int nextGeyser = 60;
    private List<ServerPlayerEntity> fighters = List.of();
    /** Client only: the tick his sword last gave off flames (OrnamixRenderer), so it does once a tick, not once a frame. */
    public int clientFlameAge = -1;

    public OrnamixEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 1000;
        bossBar().setColor(BossBar.Color.RED);
        setPersistent();
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        setEquipmentDropChance(EquipmentSlot.OFFHAND, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ARMOR, 8)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 4)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, Math.ceil(8 * DAMAGE_DEALT))
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED_ONE)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    /** His skeletons never take damage from him (a stray arrow of his would turn them on him). */
    public static void registerEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity.getCommandTags().contains(SUMMON_TAG) && source.getAttacker() instanceof OrnamixEntity));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_IDLE);
        builder.add(RAGE, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    protected Text bossBarName() {
        return Text.literal("ORNAMIX");
    }

    @Override
    protected float phaseTwoHealth() {
        return RAGE_AT;
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

    /** Ragemode: his eyes burn. */
    public boolean isRaging() {
        return dataTracker.get(RAGE);
    }

    public boolean isUntouchable() {
        return untouchable;
    }

    public String currentAction() {
        return action.name();
    }

    public int summonCount() {
        return summons.size();
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
    }

    // ---- What the hazards use ----

    List<ServerPlayerEntity> fighters() {
        return fighters;
    }

    /** A hit he deals, after {@link #DAMAGE_DEALT} (and Ragemode), rounded up to a whole number. */
    float dealt(float damage) {
        return (float) Math.ceil(damage * DAMAGE_DEALT * (isRaging() ? RAGE_DAMAGE : 1f));
    }

    /** Hurts {@code player} for {@code damage} (before {@link #dealt}) and sets them alight for {@code burnSeconds}. */
    void hurt(ServerPlayerEntity player, float damage, float burnSeconds) {
        player.damage(getDamageSources().mobAttack(this), dealt(damage));
        if (burnSeconds > 0) player.setOnFireFor(burnSeconds);
    }

    /** Throws {@code player} along {@code dir} (flattened), {@code horizontal} fast and {@code up} into the air. */
    void launch(ServerPlayerEntity player, Vec3d dir, double horizontal, double up) {
        Vec3d flat = dir.multiply(1, 0, 1);
        flat = flat.lengthSquared() > 1e-4 ? flat.normalize() : Vec3d.fromPolar(0, getYaw());
        player.setVelocity(flat.x * horizontal, up, flat.z * horizontal);
        player.velocityModified = true;
    }

    /** Everyone in the arena, fighting or not: cutscenes and the big effects are for all of them. */
    List<ServerPlayerEntity> audience(ServerWorld world) {
        return world.getPlayers(p -> p.squaredDistanceTo(this) < 160 * 160);
    }

    /** Particles everyone in the arena sees, however far across it they are. */
    void emit(ServerWorld world, ParticleEffect particle, Vec3d at, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : audience(world)) {
            world.spawnParticles(p, particle, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
        }
    }

    /** A flash of firelight over the screen. */
    void flash(ServerPlayerEntity player, float strength, int ticks) {
        Cinematics.screen(player, BossScreenPayload.FLASH_FIRE, Math.round(Math.min(1f, strength) * 100), ticks);
    }

    ServerPlayerEntity fighter(UUID id) {
        if (id == null) return null;
        for (ServerPlayerEntity p : fighters) if (p.getUuid().equals(id)) return p;
        return null;
    }

    static void sky(ServerPlayerEntity player, int phase) {
        if (ServerPlayNetworking.canSend(player, WrathSkyPayload.ID)) ServerPlayNetworking.send(player, new WrathSkyPayload(phase));
    }

    // ---- Taking damage ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        Entity attacker = source.getAttacker();
        if (attacker == this || attacker != null && attacker.getCommandTags().contains(SUMMON_TAG)) return false;
        if (untouchable) {
            if (attacker instanceof PlayerEntity) {
                getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.0f, 1.6f);
            }
            return false;
        }
        if (!returnedHit && attacker instanceof PlayerEntity player && tryDodge(player)) return false;
        amount *= getPhase() == 1 ? DAMAGE_TAKEN_ONE : DAMAGE_TAKEN_TWO;
        if (action == Action.EXPOSED) amount *= EXPOSED_BONUS;
        amount = Math.min(amount, MAX_HIT);
        // Phase 1 can't be skipped by one big hit
        if (getPhase() == 1) amount = Math.min(amount, getHealth() - getMaxHealth() * RAGE_AT + 0.01f);
        return super.damage(source, Math.max(0.01f, amount));
    }

    /** One of his own fireballs, hit back into him by {@code player}. */
    void takeReturnedFireball(OrnamixFireballEntity fireball, PlayerEntity player) {
        if (untouchable || isDead()) return;
        returnedHit = true;
        timeUntilRegen = 0;
        damage(getDamageSources().explosion(fireball, player), OrnamixFireballEntity.RETURNED_DAMAGE);
        returnedHit = false;
        getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 2.0f, 0.6f);
    }

    /** While he isn't busy attacking, he sometimes slips a blow: a quick sidestep, gone in a puff of flame. */
    private boolean tryDodge(PlayerEntity attacker) {
        if (action != Action.CHASE || dodgeCooldown > 0 || !isOnGround()) return false;
        if (random.nextFloat() >= (getPhase() == 1 ? DODGE_ONE : DODGE_TWO)) return false;
        Vec3d from = attacker.getPos().subtract(getPos()).multiply(1, 0, 1);
        if (from.lengthSquared() < 1e-4) from = getRotationVector().multiply(1, 0, 1);
        Vec3d side = new Vec3d(-from.z, 0, from.x).normalize();
        if (random.nextBoolean()) side = side.multiply(-1);
        if (standingSpot(getPos().add(side.multiply(3.5))) == null) {
            side = side.multiply(-1);
            if (standingSpot(getPos().add(side.multiply(3.5))) == null) return false;
        }
        dodgeCooldown = getPhase() == 1 ? DODGE_COOLDOWN_ONE : DODGE_COOLDOWN_TWO;
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 25, 0.3, 0.8, 0.3, 0.02);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 8, 0.3, 0.6, 0.3, 0.01);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_SLIDE, SoundCategory.HOSTILE, 1.5f, 1.2f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
        setVelocity(side.x * 1.1, 0.25, side.z * 1.1);
        velocityDirty = true;
        return true;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return action != Action.ENTRANCE && action != Action.TRANSITION && action != Action.INFERNO;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
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
            world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 1.2f, 0.6f);
        }
        for (Entity bubble : world.getOtherEntities(this, getBoundingBox().expand(3), e -> MagicSpells.is(e, MagicSpells.BUBBLE))) {
            Vec3d c = bubble.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 30, 0.8, 0.8, 0.8, 0.1);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 2f, 0.8f);
            bubble.discard();
        }
    }

    @Override
    protected void tickPhase(int phase, int songTicks) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        fighters = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 96 * 96);
        if (!scaled) scaleHealth(world);
        boolean inArena = Arenas.sinOf(world) == Sin.WRATH;

        if (rest > 0) rest--;
        if (meleeCooldown > 0) meleeCooldown--;
        if (dodgeCooldown > 0) dodgeCooldown--;
        actionTicks++;
        switch (action) {
            case ENTRANCE -> tickEntrance(world);
            case CHASE -> tickChase();
            case EXPOSED -> tickExposed(world);
            case TRANSITION -> tickTransition(world);
            case STAB_HOP -> tickStabHop(world);
            case FIRE_WAVE -> tickFireWave(world);
            case FLAME_SPIN -> tickFlameSpin(world);
            case FLAMETHROWER -> tickFlamethrower(world);
            case IGNITE -> tickIgnite(world);
            case SUN -> tickSun(world);
            case ARROWS -> tickArrows(world);
            case FLURRY -> tickFlurry(world);
            case DASH_STAB -> tickDashStab(world);
            case FLOOR_WAVE -> tickFloorWave(world);
            case HAYMAKER -> tickHaymaker(world);
            case METEOR -> tickMeteor(world);
            case SUMMON -> tickSummon(world);
            case FIREBALLS -> tickFireballs(world);
            case INFERNO -> tickInferno(world);
        }
        tickHazards(world);
        summons.removeIf(id -> !(world.getEntity(id) instanceof WitherSkeletonEntity s) || !s.isAlive());
        tickAura(world);
        if (inArena) {
            tickAmbience(world);
            keepInPit(world);
        }
    }

    /** Fewer players, less health: see {@link OrnamixTimeline#healthScale}. Done once, when he arrives. */
    private void scaleHealth(ServerWorld world) {
        scaled = true;
        int players = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 160 * 160).size();
        float max = MAX_HEALTH * OrnamixTimeline.healthScale(players);
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(max);
        setHealth(max);
    }

    @Override
    protected void onBeat(int phase, int beat) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (action == Action.CHASE && rest <= 0 && !fighters.isEmpty()) {
            startAttack(chooseAttack(phase));
        }
        // The fires flare with the song
        if (phase == 1 && beat % 4 == 0 && action != Action.ENTRANCE) {
            world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), 12, 0.5, 0.05, 0.5, 0.08);
        }
        if (phase == 2 && beat % 2 == 0 && action != Action.TRANSITION) {
            world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 1, getZ(), 3, 0.3, 0.5, 0.3, 0);
        }
    }

    // ---- Choosing attacks ----

    private Action chooseAttack(int phase) {
        List<Action> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        double near = nearestFighterDistance();
        boolean two = phase == 2;
        if (near < 8) offer(options, weights, Action.STAB_HOP, 3, 0);
        offer(options, weights, Action.FIRE_WAVE, 3, two ? 100 : 140);
        offer(options, weights, Action.FLAME_SPIN, 2, two ? 140 : 180);
        offer(options, weights, Action.FLAMETHROWER, 2, two ? 180 : 220);
        if (near < 9) offer(options, weights, Action.IGNITE, 2, two ? 160 : 220);
        offer(options, weights, Action.SUN, 2, two ? 340 : 420);
        offer(options, weights, Action.ARROWS, 2, two ? 220 : 260);
        offer(options, weights, Action.FLURRY, 3, 0);
        offer(options, weights, Action.DASH_STAB, 3, two ? 60 : 80);
        offer(options, weights, Action.FLOOR_WAVE, 2, two ? 180 : 220);
        offer(options, weights, Action.HAYMAKER, 2, two ? 100 : 140);
        if (two) {
            offer(options, weights, Action.METEOR, 3, 320);
            if (summons.size() < 3) offer(options, weights, Action.SUMMON, 2, 600);
            offer(options, weights, Action.FIREBALLS, 3, 220);
            offer(options, weights, Action.INFERNO, 2, 460);
        }
        if (options.isEmpty()) return Action.DASH_STAB;
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

    void startAttack(Action attack) {
        action = attack;
        actionTicks = 0;
        stageTicks = -1;
        lastAttack = attack;
        lastUsed.put(attack, age);
        repeats = 0;
        struck.clear();
        lastHit.clear();
        getNavigation().stop();
    }

    private void endAttack() {
        action = Action.CHASE;
        actionTicks = 0;
        stageTicks = -1;
        setPoseState(POSE_IDLE);
        setNoGravity(false);
        rest = getPhase() == 1 ? REST_ONE : REST_TWO;
    }

    /** Left stuck or dizzy for {@code ticks}, taking extra damage. */
    private void expose(int ticks, int pose) {
        action = Action.EXPOSED;
        actionTicks = 0;
        exposedTicks = ticks;
        setPoseState(pose);
        setNoGravity(false);
        getNavigation().stop();
    }

    /** The fighter he's picked on least recently (not in {@code skip}), or null if there's nobody left. */
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

    private ServerPlayerEntity nearest() {
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity p : fighters) if (best == null || p.squaredDistanceTo(this) < best.squaredDistanceTo(this)) best = p;
        if (best != null) lastPicked.put(best.getUuid(), age);
        return best;
    }

    private double nearestFighterDistance() {
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : fighters) best = Math.min(best, p.distanceTo(this));
        return best;
    }

    private boolean raging() {
        return getPhase() == 2;
    }

    // ---- Entrance: down out of the sky like a meteor ----

    private void tickEntrance(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            untouchable = true;
            setNoGravity(true);
            double ground = groundBelow(getPos(), 80);
            entranceGround = new Vec3d(getX(), Double.isNaN(ground) ? getY() : ground, getZ());
            world.playSound(null, entranceGround.x, entranceGround.y, entranceGround.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 4.0f, 0.4f);
        }
        int fall = OrnamixTimeline.ENTRANCE_FALL;
        if (t <= fall) {
            double from = Math.max(0, WrathArena.BOSS_SPAWN.y - WrathArena.CENTER.y);
            if (Arenas.sinOf(world) != Sin.WRATH) from = 0;
            setVelocity(Vec3d.ZERO);
            setPosition(entranceGround.x, entranceGround.y + OrnamixTimeline.entranceHeight(t, from), entranceGround.z);
            setPoseState(POSE_KNEEL);
            Vec3d at = getPos().add(0, 1, 0);
            if (from > 0) {
                emit(world, ParticleTypes.FLAME, at, 18, 0.5, 0.9, 0.5, 0.04);
                emit(world, ParticleTypes.LARGE_SMOKE, at.add(0, 1.5, 0), 5, 0.4, 1.2, 0.4, 0.02);
                emit(world, ParticleTypes.LAVA, at, 2, 0.4, 0.4, 0.4, 0);
            }
            if (t % 2 == 0) {
                double r = 6 * (1 - t / (double) fall) + 1.5;
                for (int i = 0; i < 24; i++) {
                    double a = i * MathHelper.TAU / 24 + t * 0.1;
                    world.spawnParticles(ParticleTypes.FLAME, entranceGround.x + Math.cos(a) * r, entranceGround.y + 0.1, entranceGround.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
                }
            }
            if (t % 6 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            if (t == fall) landWithABang(world);
            return;
        }
        int kneel = fall + OrnamixTimeline.ENTRANCE_KNEEL;
        if (t <= kneel) {
            setPoseState(POSE_KNEEL);
            world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 6, 1.2, 0.1, 1.2, 0.04);
            world.spawnParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 1.0, getZ(), 3, 0.4, 0.6, 0.4, 0.02);
            return;
        }
        if (t == kneel + 1) {
            setPoseState(POSE_ROAR);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 4.0f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 3.0f, 0.5f);
        }
        firePillar(world, 1.0);
        if (t >= OrnamixTimeline.ENTRANCE_END) {
            untouchable = false;
            setNoGravity(false);
            endAttack();
            rest = 20;
        }
    }

    /** Hits the ground: a blast of fire, a ring of flame rushing out, everyone near thrown back. Hurts nobody. */
    private void landWithABang(ServerWorld world) {
        setNoGravity(false);
        setPosition(entranceGround.x, entranceGround.y, entranceGround.z);
        Vec3d c = getPos();
        emit(world, ParticleTypes.EXPLOSION_EMITTER, c.add(0, 0.5, 0), 2, 1, 0.2, 1, 0);
        emit(world, ParticleTypes.FLASH, c.add(0, 1, 0), 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.FLAME, c.add(0, 0.5, 0), 200, 1.5, 0.6, 1.5, 0.45);
        emit(world, ParticleTypes.LAVA, c.add(0, 0.5, 0), 50, 2, 0.5, 2, 0);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), c.x, c.y + 0.2, c.z, 120, 2.5, 0.2, 2.5, 0.2);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 3.0f, 0.6f);
        hazards.add(new WrathHazards.Nova(c, 14, 12, 0));
        for (ServerPlayerEntity p : audience(world)) {
            double d = p.getPos().distanceTo(c);
            flash(p, (float) (0.7 * Math.max(0.3, 1 - d / 60)), 20);
            if (d < 12 && !p.isCreative() && !p.isSpectator()) launch(p, p.getPos().subtract(c), 1.2 * (1 - d / 12) + 0.3, 0.45);
        }
    }

    /** A column of fire roaring up round him. */
    private void firePillar(ServerWorld world, double strength) {
        emit(world, ParticleTypes.FLAME, getPos().add(0, 1.5, 0), (int) (14 * strength), 0.5, 1.6, 0.5, 0.06);
        if (age % 2 == 0) emit(world, ParticleTypes.LAVA, getPos().add(0, 1, 0), 1, 0.4, 0.6, 0.4, 0);
        if (age % 3 == 0) emit(world, ParticleTypes.LARGE_SMOKE, getPos().add(0, 3.5, 0), 2, 0.4, 0.6, 0.4, 0.03);
    }

    // ---- Between attacks ----

    private void tickChase() {
        ServerPlayerEntity target = fighter(focus);
        if (target == null || --focusTicks <= 0) {
            target = pick(null);
            focus = target == null ? null : target.getUuid();
            focusTicks = 100;
        }
        if (target == null) {
            getNavigation().stop();
            return;
        }
        setTarget(target);
        getLookControl().lookAt(target, 40f, 40f);
        double speed = raging() ? 1.3 : 1.15;
        if (age % 8 == 0 || getNavigation().isIdle()) getNavigation().startMovingTo(target, speed);
        double reach = 1.6 + getWidth();
        if (meleeCooldown <= 0 && squaredDistanceTo(target) < reach * reach) {
            swingHand(Hand.MAIN_HAND);
            if (tryAttack(target)) target.setOnFireFor(3);
            meleeCooldown = raging() ? 10 : 15;
        }
    }

    private void tickExposed(ServerWorld world) {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (actionTicks % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = actionTicks * 0.3 + i * MathHelper.TAU / 3;
                world.spawnParticles(ParticleTypes.CRIT, getX() + Math.cos(a) * 0.6, getY() + getHeight() + 0.2, getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
            }
            world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + getHeight(), getZ(), 2, 0.2, 0.1, 0.2, 0.01);
        }
        if (actionTicks >= exposedTicks) endAttack();
    }

    // ---- Phase 1 attacks (all of them carry on into Ragemode, faster and harder) ----

    private void tickStabHop(ServerWorld world) {
        int windup = raging() ? 7 : 11, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = nearest();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_DRAW);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.HOSTILE, 1.5f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 1.0f, 1.2f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t <= windup) {
            setVelocity(0, getVelocity().y, 0);
            if (target != null) faceTowards(target.getPos());
            dashDir = Vec3d.fromPolar(0, getYaw());
            Vec3d hand = handPos(true);
            world.spawnParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 3, 0.2, 0.2, 0.2, 0.02);
            return;
        }
        if (t == windup + 1) {
            setPoseState(POSE_STAB);
            setVelocity(dashDir.x * 1.0, 0.1, dashDir.z * 1.0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 2.0f, 0.8f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.2f, 1.2f);
        }
        if (t <= windup + 4) {
            Vec3d tip = getPos().add(dashDir.multiply(2.2)).add(0, 1.2, 0);
            world.spawnParticles(ParticleTypes.FLAME, tip.x, tip.y, tip.z, 6, 0.3, 0.15, 0.3, 0.05);
            hitInFront(world, 3.2, 0.55, raging() ? 12 : 11, 3, 0.8, 0.25, false);
            return;
        }
        if (t == windup + 6) {
            setPoseState(POSE_IDLE);
            setVelocity(-dashDir.x * 1.25, 0.5, -dashDir.z * 1.25);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.HOSTILE, 2.0f, 0.9f);
        }
        if (t > windup + 6 && t < windup + 14) world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 3, 0.2, 0.3, 0.2, 0.01);
        if (t >= windup + 20) endAttack();
    }

    private void tickFireWave(ServerWorld world) {
        int windup = raging() ? 9 : 14, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            setPoseState(POSE_RAISE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.7f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t < windup) {
            setVelocity(0, getVelocity().y, 0);
            if (target != null) faceTowards(target.getPos());
            Vec3d tip = getPos().add(0, getHeight() + 0.8, 0);
            world.spawnParticles(ParticleTypes.FLAME, tip.x, tip.y, tip.z, 5, 0.25, 0.4, 0.25, 0.02);
            return;
        }
        if (t == windup) {
            swingHand(Hand.MAIN_HAND);
            setPoseState(POSE_SLAM);
            Vec3d dir = target != null ? target.getPos().subtract(getPos()).multiply(1, 0, 1) : Vec3d.fromPolar(0, getYaw());
            if (dir.lengthSquared() < 1e-4) dir = Vec3d.fromPolar(0, getYaw());
            dir = dir.normalize();
            int count = raging() ? 3 : 1;
            for (int i = 0; i < count; i++) {
                float spread = (float) Math.toRadians((i - (count - 1) / 2.0) * 25);
                Vec3d d = dir.rotateY(spread);
                hazards.add(new WrathHazards.FireWave(getPos().add(d.multiply(1.2)), d, raging() ? 1.05 : 0.85, 34, 3.5, 10));
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (t >= windup + 10) endAttack();
    }

    private void tickFlameSpin(ServerWorld world) {
        int windup = raging() ? 8 : 12, spin = raging() ? 40 : 30, t = actionTicks;
        double radius = 3.6;
        if (t == 1) {
            setPoseState(POSE_SPIN_READY);
            hazards.add(new WrathHazards.Circle(getPos(), radius, windup, ParticleTypes.FLAME));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.8f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        if (t <= windup) {
            setVelocity(0, getVelocity().y, 0);
            return;
        }
        if (t <= windup + spin) {
            setPoseState(POSE_SPIN);
            ServerPlayerEntity target = nearest();
            if (target != null) {
                Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1);
                if (to.lengthSquared() > 1) to = to.normalize().multiply(raging() ? 0.32 : 0.26);
                setVelocity(to.x, getVelocity().y, to.z);
            }
            int points = 14;
            for (int i = 0; i < points; i++) {
                double a = i * MathHelper.TAU / points + t * 0.7;
                world.spawnParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * radius, getY() + 1.0, getZ() + Math.sin(a) * radius, 1, 0.05, 0.25, 0.05, 0.01);
            }
            if (t % 3 == 0) world.spawnParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.1, getZ(), 2, 1.6, 0.2, 1.6, 0);
            if (t % 4 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.6f, 0.7f);
            if (t % 8 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.HOSTILE, 1.5f, 0.8f);
            for (ServerPlayerEntity p : fighters) {
                if (WrathHazards.flatDistance(p.getPos(), getPos()) > radius || Math.abs(p.getY() - getY()) > 2.5) continue;
                Integer last = lastHit.get(p.getUuid());
                if (last != null && t - last < 10) continue;
                lastHit.put(p.getUuid(), t);
                hurt(p, raging() ? 9 : 8, 3);
                launch(p, p.getPos().subtract(getPos()), raging() ? 2.2 : 1.9, 0.6);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.5f, 0.8f);
            }
            return;
        }
        endAttack();
    }

    private void tickFlamethrower(ServerWorld world) {
        int windup = raging() ? 10 : 16, burn = raging() ? 60 : 50, t = actionTicks;
        double range = 11, halfAngle = Math.toRadians(22), turn = raging() ? 3.5 : 2.5;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            setPoseState(POSE_AIM);
            if (target != null) aimYaw = yawTo(target.getPos());
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        ServerPlayerEntity target = fighter(focus);
        Vec3d hand = handPos(false);
        if (t <= windup) {
            if (target != null) aimYaw = yawTo(target.getPos());
            faceYaw(aimYaw);
            world.spawnParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 2, 0.1, 0.1, 0.1, 0.01);
            world.spawnParticles(ParticleTypes.SMALL_FLAME, hand.x, hand.y, hand.z, 1, 0.1, 0.1, 0.1, 0.01);
            return;
        }
        if (t > windup + burn) {
            if (t >= windup + burn + 6) endAttack();
            return;
        }
        // The flames turn after their target, but only so fast: run round him
        if (target != null) {
            float want = yawTo(target.getPos());
            aimYaw += (float) MathHelper.clamp(MathHelper.wrapDegrees(want - aimYaw), -turn, turn);
        }
        faceYaw(aimYaw);
        Vec3d dir = Vec3d.fromPolar(5, aimYaw);
        for (int i = 0; i < 10; i++) {
            Vec3d d = dir.rotateY((float) ((random.nextDouble() * 2 - 1) * halfAngle * 0.8))
                    .add(0, (random.nextDouble() - 0.5) * 0.2, 0).normalize().multiply(0.45 + random.nextDouble() * 0.35);
            world.spawnParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 0, d.x, d.y, d.z, 1.0);
        }
        Vec3d end = hand.add(dir.normalize().multiply(range * 0.8));
        if (t % 2 == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, end.x, end.y, end.z, 2, 1.2, 0.5, 1.2, 0.02);
        if (t % 4 == 0) world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2.5f, 0.6f);
        if (t % 6 == 0) world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.2f, 0.5f);
        Vec3d flat = new Vec3d(dir.x, 0, dir.z).normalize();
        for (ServerPlayerEntity p : fighters) {
            Vec3d to = p.getBoundingBox().getCenter().subtract(hand);
            Vec3d toFlat = new Vec3d(to.x, 0, to.z);
            if (toFlat.length() > range || Math.abs(to.y) > 3 || toFlat.lengthSquared() < 1e-4) continue;
            if (Math.acos(MathHelper.clamp(toFlat.normalize().dotProduct(flat), -1, 1)) > halfAngle) continue;
            if (!canSee(hand, p.getBoundingBox().getCenter())) continue;
            Integer last = lastHit.get(p.getUuid());
            if (last != null && t - last < 8) continue;
            lastHit.put(p.getUuid(), t);
            p.timeUntilRegen = 0;
            hurt(p, 4, 4);
        }
    }

    private void tickIgnite(ServerWorld world) {
        int windup = raging() ? 10 : 16, t = actionTicks;
        double radius = 10;
        if (t == 1) {
            setPoseState(POSE_CAST);
            hazards.add(new WrathHazards.Circle(getPos(), radius, windup, ParticleTypes.SMALL_FLAME));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t < windup) {
            // Flame drawn in to his feet
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * MathHelper.TAU, d = 2 + random.nextDouble() * 1.5;
                world.spawnParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * d, getY() + 0.2, getZ() + Math.sin(a) * d, 0,
                        -Math.cos(a) * d / 10, 0.02, -Math.sin(a) * d / 10, 1.0);
            }
            return;
        }
        if (t == windup) {
            Vec3d c = getPos();
            swingHand(Hand.MAIN_HAND);
            hazards.add(new WrathHazards.Nova(c, radius, raging() ? 8 : 10, raging() ? 7 : 6));
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            for (int i = 0; i < 40; i++) {
                double a = random.nextDouble() * MathHelper.TAU, up = 0.2 + random.nextDouble() * 0.5;
                world.spawnParticles(ParticleTypes.FLAME, c.x, c.y + 0.2, c.z, 0, Math.cos(a) * 0.15, up, Math.sin(a) * 0.15, 1.0);
            }
            world.spawnParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 12, 0.4, 0.2, 0.4, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        if (t >= windup + 14) endAttack();
    }

    private void tickSun(ServerWorld world) {
        int t = actionTicks, ignite = raging() ? 22 : 30;
        if (t == 1) {
            // A leap far back, away from everyone (but not over the pit wall)
            Vec3d away = Vec3d.ZERO;
            for (ServerPlayerEntity p : fighters) away = away.add(getPos().subtract(p.getPos()).multiply(1, 0, 1));
            away = away.lengthSquared() > 1e-4 ? away.normalize() : Vec3d.fromPolar(0, getYaw() + 180);
            if (Arenas.sinOf(world) == Sin.WRATH && WrathHazards.flatDistance(getPos().add(away.multiply(12)), WrathArena.CENTER) > WrathArena.PIT_RADIUS - 6) {
                away = WrathArena.CENTER.subtract(getPos()).multiply(1, 0, 1).normalize();
            }
            setVelocity(away.x * 1.15, 0.75, away.z * 1.15);
            setPoseState(POSE_IDLE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.HOSTILE, 2.0f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.8f);
            return;
        }
        if (stageTicks < 0) {
            world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 3, 0.2, 0.3, 0.2, 0.01);
            if (t > 3 && isOnGround() || t > 40) {
                stageTicks = 0;
                setVelocity(0, getVelocity().y, 0);
            }
            return;
        }
        int s = ++stageTicks;
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (s == 1) {
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            setPoseState(POSE_CAST);
            Vec3d forward = target != null ? target.getPos().subtract(getPos()).multiply(1, 0, 1) : Vec3d.fromPolar(0, getYaw());
            forward = forward.lengthSquared() > 1e-4 ? forward.normalize() : new Vec3d(1, 0, 0);
            hazards.add(new WrathHazards.Sun(getPos().add(0, 1.5, 0).add(forward.multiply(2.2)), forward, focus, ignite,
                    raging() ? 0.3 : 0.22, 0.025, raging() ? 6 : 5, 8));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.2f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 0.7f, 1.5f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (s <= ignite && target != null) faceTowards(target.getPos());
        if (s == ignite) setPoseState(POSE_AIM);
        if (s >= ignite + 12) endAttack();
    }

    private void tickArrows(ServerWorld world) {
        int windup = raging() ? 8 : 12, interval = 2, shots = raging() ? 26 : 20, t = actionTicks;
        if (t == 1) {
            equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            setPoseState(POSE_BOW);
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE.value(), SoundCategory.HOSTILE, 1.5f, 0.8f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0f, 1.4f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        ServerPlayerEntity target = fighter(focus);
        if (target != null) faceTowards(target.getPos());
        if (t <= windup) {
            Vec3d hand = handPos(false);
            world.spawnParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 2, 0.1, 0.1, 0.1, 0.01);
            return;
        }
        int n = (t - windup - 1) / interval;
        if ((t - windup - 1) % interval == 0 && n < shots) {
            if (n % 6 == 5 || target == null) {
                // Sweeps on to the next one
                ServerPlayerEntity next = pick(null);
                if (next != null) {
                    focus = next.getUuid();
                    target = next;
                }
            }
            if (target != null) shootArrow(world, target, raging() ? 2.5f : 3.5f);
        }
        if (t >= windup + shots * interval + 8) {
            equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            endAttack();
        }
    }

    private void shootArrow(ServerWorld world, ServerPlayerEntity target, float inaccuracy) {
        Vec3d from = getEyePos().add(Vec3d.fromPolar(0, getYaw()).multiply(0.6));
        // Leads a little where they're running
        Vec3d aim = target.getBoundingBox().getCenter().add(target.getVelocity().multiply(1, 0, 1).multiply(4));
        Vec3d dir = aim.subtract(from);
        ArrowEntity arrow = new ArrowEntity(world, this, new ItemStack(Items.ARROW), null) {
            @Override
            public void tick() {
                super.tick();
                // Don't litter the pit with them
                if (inGround && inGroundTime > 30) discard();
            }
        };
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        arrow.setPosition(from.x, from.y, from.z);
        arrow.setVelocity(dir.x, dir.y + dir.horizontalLength() * 0.04, dir.z, 2.6f, inaccuracy);
        arrow.setDamage(2.0 * DAMAGE_DEALT);
        arrow.setOnFireFor(100);
        world.spawnEntity(arrow);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.2f + random.nextFloat() * 0.4f);
    }

    private void tickFlurry(ServerWorld world) {
        int swings = raging() ? 10 : 7, interval = raging() ? 2 : 3, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = nearest();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_IDLE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.5f, 1.3f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (stageTicks < 0) {
            // Closing in
            if (target == null || t > 18 || squaredDistanceTo(target) < 2.6 * 2.6) {
                stageTicks = 0;
                setVelocity(0, getVelocity().y, 0);
            } else {
                faceTowards(target.getPos());
                Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1).normalize().multiply(raging() ? 0.85 : 0.75);
                setVelocity(to.x, getVelocity().y, to.z);
                world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0.01);
                return;
            }
        }
        int s = ++stageTicks;
        if (target != null) {
            faceTowards(target.getPos());
            Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (to.lengthSquared() > 4) {
                to = to.normalize().multiply(0.18);
                setVelocity(to.x, getVelocity().y, to.z);
            } else {
                setVelocity(0, getVelocity().y, 0);
            }
        }
        if (s % interval == 1 && s / interval < swings) {
            swingHand(Hand.MAIN_HAND);
            Vec3d f = Vec3d.fromPolar(0, getYaw());
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, getX() + f.x * 1.6, getY() + 1.1 + random.nextDouble() * 0.4, getZ() + f.z * 1.6, 1, 0.3, 0.2, 0.3, 0);
            world.spawnParticles(ParticleTypes.FLAME, getX() + f.x * 1.6, getY() + 1.1, getZ() + f.z * 1.6, 4, 0.5, 0.3, 0.5, 0.03);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.4f, 1.2f + random.nextFloat() * 0.4f);
            struck.clear();
            hitInFront(world, 3.2, 0.25, 3, 2, 0.15, 0.05, true);
        }
        if (s >= swings * interval + 6) endAttack();
    }

    private void tickDashStab(ServerWorld world) {
        boolean two = raging();
        int windup = two ? 10 : 16, dash = 12, t = actionTicks;
        double speed = two ? 2.0 : 1.6;
        if (t == 1) {
            if (repeats == 0) repeats = two ? 2 : 1;
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            struck.clear();
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_HOGLIN_ANGRY, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t <= windup) {
            setVelocity(0, getVelocity().y, 0);
            if (target != null) {
                faceTowards(target.getPos());
                Vec3d to = target.getPos().subtract(getPos());
                dashDir = new Vec3d(to.x, 0, to.z).normalize();
            }
            // The way he'll come, marked in flame along the floor
            if (t % 2 == 0) {
                double length = speed * dash;
                for (double d = 1.5; d < length; d += 1.2) {
                    Vec3d p = getPos().add(dashDir.multiply(d));
                    world.spawnParticles(ParticleTypes.SMALL_FLAME, p.x, p.y + 0.1, p.z, 1, 0.05, 0, 0.05, 0);
                }
            }
            return;
        }
        if (t == windup + 1) {
            setPoseState(POSE_STAB);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 2.0f, 0.9f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (t <= windup + dash) {
            if (t > windup + 2 && horizontalCollision) {
                // Straight into the wall: the sword sticks
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.5f, 0.6f);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 2.0f, 0.6f);
                Vec3d wall = getPos().add(dashDir.multiply(0.8)).add(0, 1.2, 0);
                BlockState hit = getWorld().getBlockState(BlockPos.ofFloored(wall));
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, hit.isAir() ? Blocks.STONE.getDefaultState() : hit),
                        wall.x, wall.y, wall.z, 30, 0.4, 0.4, 0.4, 0.1);
                setVelocity(Vec3d.ZERO);
                expose(two ? 40 : 50, POSE_STUCK);
                return;
            }
            setVelocity(dashDir.x * speed, getVelocity().y, dashDir.z * speed);
            world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.8, getZ(), 5, 0.3, 0.4, 0.3, 0.01);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.8, getZ(), 1, 0.2, 0.3, 0.2, 0.0);
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid()) || !p.getBoundingBox().intersects(getBoundingBox().expand(0.7))) continue;
                struck.add(p.getUuid());
                hurt(p, 14, 4);
                launch(p, dashDir, 1.3, 0.45);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.5f, 0.8f);
            }
            return;
        }
        setVelocity(0, getVelocity().y, 0);
        setPoseState(POSE_IDLE);
        if (--repeats > 0) actionTicks = 0;
        else endAttack();
    }

    private void tickFloorWave(ServerWorld world) {
        boolean two = raging();
        int windup = two ? 12 : 18, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_RAISE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t < windup) {
            // The ground starts to glow round him
            world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 0.1, getZ(), 1, 0.8, 0, 0.8, 0);
            for (int i = 0; i < 8; i++) {
                double a = i * MathHelper.TAU / 8 + t * 0.2;
                world.spawnParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * 1.5, getY() + 0.1, getZ() + Math.sin(a) * 1.5, 1, 0, 0.02, 0, 0);
            }
            return;
        }
        if (t == windup) {
            swingHand(Hand.MAIN_HAND);
            setPoseState(POSE_SLAM);
            int rings = two ? 4 : 3, gap = two ? 10 : 14;
            for (int i = 0; i < rings; i++) {
                hazards.add(new WrathHazards.FloorRing(getPos(), two ? 0.65 : 0.55, two ? 28 : 24, 3, i * gap));
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.5f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), getX(), getY() + 0.2, getZ(), 40, 1.2, 0.1, 1.2, 0.15);
        }
        if (t >= windup + 12) endAttack();
    }

    private void tickHaymaker(ServerWorld world) {
        boolean two = raging();
        int windup = two ? 6 : 10, t = actionTicks;
        if (t == 1) {
            boolean first = repeats == 0;
            if (first) repeats = two ? 2 : 1;
            // The first punch goes to whoever is closest, the next to someone else
            ServerPlayerEntity target = first || focus == null ? nearest() : pick(Set.of(focus));
            if (target == null) target = nearest();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            struck.clear();
            stageTicks = -1;
            setPoseState(POSE_IDLE);
        }
        ServerPlayerEntity target = fighter(focus);
        if (stageTicks < 0) {
            if (target == null || t > 20 || squaredDistanceTo(target) < 2.4 * 2.4) {
                stageTicks = 0;
                setVelocity(0, getVelocity().y, 0);
                setPoseState(POSE_PUNCH_READY);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.5f);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0f, 0.8f);
            } else {
                faceTowards(target.getPos());
                Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1).normalize().multiply(two ? 0.8 : 0.7);
                setVelocity(to.x, getVelocity().y, to.z);
                return;
            }
        }
        int s = ++stageTicks;
        if (s <= windup) {
            setVelocity(0, getVelocity().y, 0);
            if (target != null) faceTowards(target.getPos());
            Vec3d fist = handPos(false);
            world.spawnParticles(ParticleTypes.FLAME, fist.x, fist.y, fist.z, 4, 0.15, 0.15, 0.15, 0.03);
            return;
        }
        if (s == windup + 1) {
            setPoseState(POSE_PUNCH);
            swingHand(Hand.OFF_HAND);
            Vec3d f = Vec3d.fromPolar(0, getYaw());
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getPos().subtract(getPos());
                if (to.lengthSquared() > 3.4 * 3.4 || to.multiply(1, 0, 1).normalize().dotProduct(f) < 0.35 || Math.abs(to.y) > 2) continue;
                hurt(p, two ? 10 : 8, 3);
                launch(p, f, two ? 3.8 : 3.2, two ? 1.0 : 0.9);
                Vec3d c = p.getBoundingBox().getCenter();
                world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.15);
                flash(p, 0.3f, 8);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.2f, 1.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 1.0f, 0.7f);
        }
        if (s >= windup + 10) {
            setPoseState(POSE_IDLE);
            if (--repeats > 0) actionTicks = 0;
            else endAttack();
        }
    }

    // ---- Ragemode's own attacks ----

    private void tickMeteor(ServerWorld world) {
        int charge = 30, fall = 50, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            // A meteor for each of them, where they stand now (five at most)
            List<ServerPlayerEntity> targets = new ArrayList<>(fighters);
            java.util.Collections.shuffle(targets, new java.util.Random(random.nextLong()));
            for (int i = 0; i < Math.min(5, targets.size()); i++) {
                Vec3d spot = targets.get(i).getPos();
                double ground = groundBelow(spot.add(0, 0.5, 0), 8);
                if (!Double.isNaN(ground)) spot = new Vec3d(spot.x, ground, spot.z);
                hazards.add(new WrathHazards.Meteor(spot, getPos(), charge, fall, 5.5, 16));
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.GOAT_HORN_SOUNDS.get(7).value(), SoundCategory.HOSTILE, 3.0f, 0.55f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t < charge) {
            // Flame spiralling up round him into the sky
            for (int i = 0; i < 3; i++) {
                double a = t * 0.4 + i * MathHelper.TAU / 3, h = (t % 15) / 15.0 * 3;
                world.spawnParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * 0.9, getY() + h, getZ() + Math.sin(a) * 0.9, 1, 0, 0.1, 0, 0.02);
            }
            if (t % 5 == 0) world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + getHeight() + 0.5, getZ(), 2, 0.3, 0.3, 0.3, 0);
            return;
        }
        if (t == charge) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.4f);
        if (t >= charge + 6) endAttack();
    }

    private void tickSummon(ServerWorld world) {
        int rise = 24, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            summonSpots.clear();
            int count = Math.min(MAX_SUMMONS - summons.size(), MathHelper.clamp(1 + fighters.size() / 2, 2, 4));
            for (int i = 0; i < count * 4 && summonSpots.size() < count; i++) {
                ServerPlayerEntity near = fighters.isEmpty() ? null : fighters.get(random.nextInt(fighters.size()));
                Vec3d around = near != null ? near.getPos() : getPos();
                double a = random.nextDouble() * MathHelper.TAU, d = 6 + random.nextDouble() * 4;
                Vec3d spot = standingSpot(around.add(Math.cos(a) * d, 0, Math.sin(a) * d));
                if (spot == null) continue;
                if (Arenas.sinOf(world) == Sin.WRATH && !WrathArena.inPit(spot)) continue;
                summonSpots.add(spot);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (t < rise) {
            // Where they'll climb out: the ground smoulders, then bursts into flame
            for (Vec3d s : summonSpots) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, s.x, s.y + 0.2, s.z, 2, 0.3, 0.1, 0.3, 0.02);
                world.spawnParticles(ParticleTypes.FLAME, s.x, s.y + 0.1, s.z, 3, 0.4, 0.05 + t * 0.04, 0.4, 0.02);
                if (t % 6 == 0) world.playSound(null, s.x, s.y, s.z, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
            return;
        }
        if (t == rise) {
            for (Vec3d s : summonSpots) summonSkeleton(world, s);
            summonSpots.clear();
        }
        if (t >= rise + 10) endAttack();
    }

    private void summonSkeleton(ServerWorld world, Vec3d at) {
        WitherSkeletonEntity skeleton = EntityType.WITHER_SKELETON.create(world);
        if (skeleton == null) return;
        // Nothing to take home from them
        NbtCompound nbt = skeleton.writeNbt(new NbtCompound());
        nbt.putString("DeathLootTable", "minecraft:empty");
        skeleton.readNbt(nbt);
        skeleton.refreshPositionAndAngles(at.x, at.y, at.z, random.nextFloat() * 360f, 0f);
        skeleton.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(at)), SpawnReason.MOB_SUMMONED, null);
        skeleton.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        skeleton.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        skeleton.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        skeleton.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        for (EquipmentSlot slot : EquipmentSlot.values()) skeleton.setEquipmentDropChance(slot, 0f);
        skeleton.addCommandTag(SUMMON_TAG);
        skeleton.setPersistent();
        ServerPlayerEntity near = null;
        for (ServerPlayerEntity p : fighters) if (near == null || p.squaredDistanceTo(at) < near.squaredDistanceTo(at)) near = p;
        if (near != null) skeleton.setTarget(near);
        world.spawnEntity(skeleton);
        summons.add(skeleton.getUuid());
        world.spawnParticles(ParticleTypes.FLAME, at.x, at.y + 1, at.z, 40, 0.4, 0.9, 0.4, 0.05);
        world.spawnParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 8, 0.3, 0.3, 0.3, 0);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 12, 0.3, 0.8, 0.3, 0.02);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.8f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.5f, 0.7f);
    }

    private void tickFireballs(ServerWorld world) {
        int windup = 14, interval = 3, shots = 14, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_AIM);
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GHAST_WARN, SoundCategory.HOSTILE, 2.5f, 0.8f);
        }
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        ServerPlayerEntity target = fighter(focus);
        if (target != null) faceTowards(target.getPos());
        Vec3d hand = handPos(false);
        if (t <= windup) {
            world.spawnParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 3, 0.15, 0.15, 0.15, 0.02);
            return;
        }
        int n = (t - windup - 1) / interval;
        if ((t - windup - 1) % interval == 0 && n < shots) {
            if (n % 3 == 2 || target == null) {
                ServerPlayerEntity next = pick(null);
                if (next != null) {
                    focus = next.getUuid();
                    target = next;
                }
            }
            if (target != null) {
                Vec3d aim = target.getBoundingBox().getCenter().add(target.getVelocity().multiply(1, 0, 1).multiply(3));
                Vec3d dir = aim.subtract(hand).add(random.nextGaussian() * 0.6, random.nextGaussian() * 0.3, random.nextGaussian() * 0.6);
                OrnamixFireballEntity.shoot(world, this, hand.add(dir.normalize().multiply(0.8)), dir, dealt(6));
                world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.9f + random.nextFloat() * 0.4f);
            }
        }
        if (t >= windup + shots * interval + 8) endAttack();
    }

    private void tickInferno(ServerWorld world) {
        int rise = 16, hover = 26, spin = 50, t = actionTicks;
        double radius = 8;
        if (t == 1) {
            spinCenter = getPos();
            setPoseState(POSE_SPIN_READY);
            setVelocity(0, 0.9, 0);
            hazards.add(new WrathHazards.Circle(spinCenter, radius, rise + hover + 4, ParticleTypes.FLAME));
            hazards.add(new WrathHazards.Circle(spinCenter, radius - 0.6, rise + hover + 4, ParticleTypes.LAVA));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.4f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (t <= rise) {
            world.spawnParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 6, 0.3, 0.2, 0.3, 0.03);
            return;
        }
        if (t <= rise + hover) {
            // Hangs in the air, spinning up, the fire gathering
            setNoGravity(true);
            setVelocity(Vec3d.ZERO);
            setPoseState(POSE_SPIN);
            for (int i = 0; i < 4; i++) {
                double a = t * 0.5 + i * MathHelper.TAU / 4;
                double r = 2 + (t - rise) / (double) hover * (radius - 2);
                world.spawnParticles(ParticleTypes.FLAME, spinCenter.x + Math.cos(a) * r, spinCenter.y + 0.5, spinCenter.z + Math.sin(a) * r, 1, 0, 0.2, 0, 0.02);
            }
            if (t % 5 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            return;
        }
        if (t == rise + hover + 1) {
            setNoGravity(false);
            setVelocity(0, -1.6, 0);
        }
        if (stageTicks < 0) {
            if (isOnGround() || t > rise + hover + 12) {
                stageTicks = 0;
                emit(world, ParticleTypes.EXPLOSION_EMITTER, getPos().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0f, 0.5f);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
                for (ServerPlayerEntity p : audience(world)) if (p.squaredDistanceTo(this) < 30 * 30) flash(p, 0.4f, 12);
            }
            return;
        }
        int s = ++stageTicks;
        if (s <= spin) {
            setPoseState(POSE_SPIN);
            getNavigation().stop();
            setVelocity(0, getVelocity().y, 0);
            // A firestorm filling the circle
            for (int arm = 0; arm < 5; arm++) {
                for (int k = 0; k < 6; k++) {
                    double r = 1 + k * (radius - 1) / 5.0;
                    double a = s * 0.45 + arm * MathHelper.TAU / 5 + r * 0.25;
                    double h = (s * 0.3 + k) % 6;
                    emit(world, ParticleTypes.FLAME, new Vec3d(getX() + Math.cos(a) * r, getY() + 0.2 + h, getZ() + Math.sin(a) * r), 1, 0.1, 0.2, 0.1, 0.02);
                }
            }
            if (s % 3 == 0) emit(world, ParticleTypes.LAVA, getPos().add(0, 1, 0), 3, radius * 0.4, 1, radius * 0.4, 0);
            if (s % 4 == 0) emit(world, ParticleTypes.LARGE_SMOKE, getPos().add(0, 5, 0), 3, radius * 0.4, 1, radius * 0.4, 0.02);
            if (s % 5 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.5f, 0.5f);
            if (s % 10 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.HOSTILE, 3.0f, 0.5f);
            for (ServerPlayerEntity p : fighters) {
                if (WrathHazards.flatDistance(p.getPos(), getPos()) > radius || Math.abs(p.getY() - getY()) > 6) continue;
                Integer last = lastHit.get(p.getUuid());
                if (last != null && s - last < 20) continue;
                lastHit.put(p.getUuid(), s);
                p.timeUntilRegen = 0;
                hurt(p, 18, 6);
                launch(p, p.getPos().subtract(getPos()), 2.6, 1.0);
                flash(p, 0.35f, 10);
            }
            return;
        }
        // Spun himself dizzy
        expose(40, POSE_STUNNED);
    }

    // ---- Ragemode ----

    @Override
    protected void onPhaseTwo() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        action = Action.TRANSITION;
        actionTicks = 0;
        untouchable = true;
        setNoGravity(false);
        setPoseState(POSE_IDLE);
        getNavigation().stop();
        if (getMainHandStack().isOf(Items.BOW)) equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        clearAttacks(world);
        stopSong();
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.freeze(p);
            Cinematics.screen(p, BossScreenPayload.BLACK, 0, OrnamixTimeline.BLACKOUT_FADE);
            p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.0f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.0f, 0.6f);
        }
    }

    private void clearAttacks(ServerWorld world) {
        for (WrathHazards.Hazard h : hazards) h.remove();
        hazards.clear();
        for (UUID id : summons) {
            Entity skeleton = world.getEntity(id);
            if (skeleton != null) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, skeleton.getX(), skeleton.getY() + 1, skeleton.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
                world.spawnParticles(ParticleTypes.FLAME, skeleton.getX(), skeleton.getY() + 1, skeleton.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
                skeleton.discard();
            }
        }
        summons.clear();
        for (Entity shot : world.getOtherEntities(this, getBoundingBox().expand(96),
                e -> e instanceof ProjectileEntity p && p.getOwner() == this)) shot.discard();
    }

    private void tickTransition(ServerWorld world) {
        int t = actionTicks;
        setVelocity(0, getVelocity().y, 0);
        List<ServerPlayerEntity> audience = audience(world);
        // In the dark: a heartbeat, and the fires growing louder all round
        if (t < OrnamixTimeline.VOICE_AT) {
            if (t % 20 == 0) for (ServerPlayerEntity p : audience) p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.0f, 0.55f);
            if (t > 20 && t % 4 == 0) {
                float loud = Math.min(1f, (t - 20) / 70f);
                for (ServerPlayerEntity p : audience) p.playSoundToPlayer(SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.MASTER, 0.3f + loud, 0.5f + random.nextFloat() * 0.3f);
            }
            SoundEvent eerie = switch (t) {
                case 14 -> SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value();
                case 34 -> SoundEvents.ENTITY_GHAST_HURT;
                case 52 -> SoundEvents.AMBIENT_NETHER_WASTES_MOOD.value();
                case 70 -> SoundEvents.ENTITY_BLAZE_AMBIENT;
                case 86 -> SoundEvents.ENTITY_WARDEN_ROAR;
                default -> null;
            };
            if (eerie != null) for (ServerPlayerEntity p : audience) p.playSoundToPlayer(eerie, SoundCategory.MASTER, t == 86 ? 0.5f : 1.0f, 0.5f);
        }
        if (t == OrnamixTimeline.RESHAPE_AT) {
            // Under the black: the sky turns, his eyes catch fire, and he takes the middle of the pit
            dataTracker.set(RAGE, true);
            getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(SPEED_TWO);
            getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(dealt(10));
            if (Arenas.sinOf(world) == Sin.WRATH) {
                Vec3d c = WrathArena.CENTER;
                requestTeleport(c.x, c.y, c.z);
            }
            for (ServerPlayerEntity p : audience) sky(p, 2);
        }
        if (t == OrnamixTimeline.VOICE_AT) {
            for (ServerPlayerEntity p : audience) p.playSoundToPlayer(ModSounds.ORNAMIX_RAGEMODE, SoundCategory.VOICE, 1.0f, 1.0f);
        }
        if (t == OrnamixTimeline.REVEAL_AT) {
            for (ServerPlayerEntity p : audience) {
                Cinematics.screen(p, BossScreenPayload.CLEAR, 0, OrnamixTimeline.REVEAL_TICKS);
                Cinematics.unfreeze(p);
                flash(p, 0.6f, 25);
            }
            setPoseState(POSE_ROAR);
            Vec3d c = getPos();
            emit(world, ParticleTypes.EXPLOSION_EMITTER, c.add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0);
            emit(world, ParticleTypes.FLAME, c.add(0, 1, 0), 220, 1.2, 1.5, 1.2, 0.5);
            hazards.add(new WrathHazards.Nova(c, 16, 14, 0));
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 10000f, 0.6f);
            // The blast throws everyone back from him; it doesn't hurt
            for (ServerPlayerEntity p : fighters) {
                double d = p.getPos().distanceTo(c);
                if (d < 14) launch(p, p.getPos().subtract(c), 1.4 * (1 - d / 14) + 0.3, 0.5);
            }
            playSong(PHASE_TWO, OrnamixTimeline.SONG_FADE);
        }
        if (t > OrnamixTimeline.REVEAL_AT) firePillar(world, 1.6);
        if (t >= OrnamixTimeline.TRANSITION_END) {
            untouchable = false;
            endAttack();
            rest = 16;
        }
    }

    // ---- Hazards, the arena and his aura ----

    private void tickHazards(ServerWorld world) {
        for (WrathHazards.Hazard h : new ArrayList<>(hazards)) {
            if (!h.tick(world, this)) {
                h.remove();
                hazards.remove(h);
            }
        }
    }

    /** Always burning: flames curling up round him, embers, smoke; far more of it in Ragemode. */
    private void tickAura(ServerWorld world) {
        if (action == Action.TRANSITION && actionTicks < OrnamixTimeline.REVEAL_AT) return;
        boolean rage = isRaging();
        double w = getWidth() * 0.5, h = getHeight();
        world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + h * 0.5, getZ(), rage ? 3 : 1, w, h * 0.35, w, 0.01);
        int arms = rage ? 3 : 2;
        for (int i = 0; i < arms; i++) {
            double a = age * 0.35 + i * MathHelper.TAU / arms, y = ((age + i * 7) % 24) / 24.0 * h;
            double r = rage ? 0.85 : 0.7;
            world.spawnParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * r, getY() + y, getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
        }
        if (age % 3 == 0) world.spawnParticles(rage ? DARK_EMBER : EMBER, getX(), getY() + h * 0.6, getZ(), 1, w, h * 0.3, w, 0);
        if (age % (rage ? 2 : 5) == 0) world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + h, getZ(), 1, 0.2, 0.1, 0.2, 0.01);
        if (rage) {
            if (age % 4 == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + h, getZ(), 1, 0.2, 0.2, 0.2, 0.01);
            if (age % 6 == 0) world.spawnParticles(ParticleTypes.LAVA, getX(), getY() + h * 0.5, getZ(), 1, w, 0.3, w, 0);
            if (age % 2 == 0) world.spawnParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.05, getZ(), 2, 0.5, 0, 0.5, 0.01);
        }
    }

    /** The colosseum burns: embers and ash on the air, and in Ragemode far thicker, with geysers of flame from the sand. */
    private void tickAmbience(ServerWorld world) {
        boolean rage = isRaging() && action != Action.TRANSITION;
        if (action == Action.TRANSITION && actionTicks < OrnamixTimeline.REVEAL_AT) return;
        if (age % (rage ? 3 : 6) == 0) {
            for (ServerPlayerEntity p : fighters) {
                world.spawnParticles(ParticleTypes.ASH, p.getX(), p.getY() + 3, p.getZ(), rage ? 8 : 4, 10, 3, 10, 0);
                world.spawnParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 2, p.getZ(), rage ? 3 : 1, 10, 2.5, 10, 0.01);
                if (rage) {
                    world.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 2, 10, 2, 10, 0.02);
                    world.spawnParticles(DARK_EMBER, p.getX(), p.getY() + 2, p.getZ(), 3, 10, 3, 10, 0);
                } else {
                    world.spawnParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 3, p.getZ(), 3, 10, 3, 10, 0);
                }
            }
        }
        if (rage && age % 8 == 0) {
            for (ServerPlayerEntity p : fighters) world.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 0.2, p.getZ(), 1, 8, 0.1, 8, 0);
        }
        // Flame bursting up out of the sand here and there: harmless, but it looks like the pit is about to give way
        if (rage && --nextGeyser <= 0) {
            nextGeyser = 30 + random.nextInt(50);
            for (int tries = 0; tries < 6; tries++) {
                double a = random.nextDouble() * MathHelper.TAU, d = 6 + random.nextDouble() * (WrathArena.PIT_RADIUS - 9);
                Vec3d at = WrathArena.CENTER.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                boolean clear = fighters.stream().noneMatch(p -> WrathHazards.flatDistance(p.getPos(), at) < 4);
                if (!clear) continue;
                emit(world, ParticleTypes.FLAME, at.add(0, 1.5, 0), 40, 0.3, 1.5, 0.3, 0.08);
                emit(world, ParticleTypes.LAVA, at.add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, 0);
                world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.AMBIENT, 1.5f, 0.5f);
                break;
            }
        }
    }

    /** Over the wall or up in the stands for 2 seconds: the flames throw you back down into the pit. */
    private void keepInPit(ServerWorld world) {
        if (action == Action.TRANSITION || action == Action.ENTRANCE) return;
        for (ServerPlayerEntity p : fighters) {
            if (WrathArena.inPit(p.getPos())) {
                outOfPit.remove(p.getUuid());
                continue;
            }
            int out = outOfPit.merge(p.getUuid(), 1, Integer::sum);
            if (out == 10) p.sendMessage(Text.translatable("message.fmscraft.ornamix_pit").formatted(Formatting.GOLD), true);
            if (out < OUT_OF_PIT_TICKS) continue;
            outOfPit.remove(p.getUuid());
            Vec3d from = p.getPos().subtract(WrathArena.CENTER).multiply(1, 0, 1);
            from = from.lengthSquared() > 1e-4 ? from.normalize() : new Vec3d(0, 0, 1);
            Vec3d back = WrathArena.CENTER.add(from.multiply(WrathArena.PIT_RADIUS - 5));
            world.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
            p.teleport(world, back.x, back.y, back.z, p.getYaw(), p.getPitch());
            p.setOnFireFor(3);
            world.spawnParticles(ParticleTypes.FLAME, back.x, back.y + 1, back.z, 30, 0.3, 0.8, 0.3, 0.05);
            world.playSound(null, back.x, back.y, back.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
    }

    // ---- Dying ----

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld world) {
            clearAttacks(world);
            if (getMainHandStack().isOf(Items.BOW)) equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            for (ServerPlayerEntity p : audience(world)) {
                flash(p, 0.8f, 30);
                sky(p, 1);
            }
            Vec3d c = getPos().add(0, 1, 0);
            emit(world, ParticleTypes.EXPLOSION_EMITTER, c, 2, 0.5, 0.5, 0.5, 0);
            emit(world, ParticleTypes.FLAME, c, 200, 1.0, 1.2, 1.0, 0.4);
            emit(world, ParticleTypes.LARGE_SMOKE, c, 60, 1.0, 1.5, 1.0, 0.08);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.5f);
        }
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Taken away mid-fight (the attempt failed): nothing of his stays behind
        if (getWorld() instanceof ServerWorld world && reason.shouldDestroy()) clearAttacks(world);
        super.remove(reason);
    }

    // ---- Helpers ----

    /** Roughly where his hand is: his right (the sword) or his left (spells, the bow, the fist). */
    private Vec3d handPos(boolean right) {
        Vec3d forward = Vec3d.fromPolar(0, getYaw());
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(right ? -0.4 : 0.4);
        return getPos().add(0, getHeight() * 0.72, 0).add(forward.multiply(0.6)).add(side);
    }

    private void faceTowards(Vec3d pos) {
        faceYaw(yawTo(pos));
    }

    private void faceYaw(float yaw) {
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    private float yawTo(Vec3d pos) {
        return (float) (MathHelper.atan2(pos.z - getZ(), pos.x - getX()) * (180 / Math.PI)) - 90f;
    }

    private boolean canSee(Vec3d from, Vec3d to) {
        return getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /**
     * Everyone in front of him within {@code reach} (and {@code cone} as a dot product), once per attack unless
     * {@code everySwing}: hurt, set alight and knocked along his facing.
     */
    private void hitInFront(ServerWorld world, double reach, double cone, float damage, float burn, double push, double up, boolean everySwing) {
        Vec3d f = Vec3d.fromPolar(0, getYaw());
        for (ServerPlayerEntity p : fighters) {
            if (!everySwing && struck.contains(p.getUuid())) continue;
            Vec3d to = p.getPos().subtract(getPos());
            Vec3d flat = to.multiply(1, 0, 1);
            if (flat.lengthSquared() > reach * reach || Math.abs(to.y) > 2) continue;
            if (flat.lengthSquared() > 0.25 && flat.normalize().dotProduct(f) < cone) continue;
            struck.add(p.getUuid());
            if (everySwing) p.timeUntilRegen = 0;
            hurt(p, damage, burn);
            if (push > 0) {
                p.addVelocity(f.x * push, up, f.z * push);
                p.velocityModified = true;
            }
            world.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.4, 0.3, 0.1);
        }
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

    /** Somewhere to stand near {@code pos} (solid below, room for him above), or null. */
    private Vec3d standingSpot(Vec3d pos) {
        double ground = groundBelow(pos.add(0, 2, 0), 6);
        if (Double.isNaN(ground)) return null;
        Vec3d spot = new Vec3d(pos.x, ground, pos.z);
        return getWorld().isSpaceEmpty(this, getDimensions(getPose()).getBoxAt(spot)) ? spot : null;
    }

    private BlockState groundState() {
        BlockState state = getWorld().getBlockState(getBlockPos().down());
        return state.isAir() ? Blocks.SAND.getDefaultState() : state;
    }
}
