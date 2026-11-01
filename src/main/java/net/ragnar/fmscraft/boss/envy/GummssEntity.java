package net.ragnar.fmscraft.boss.envy;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.EntityEffectParticleEffect;
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
import net.minecraft.world.WorldEvents;
import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.SinBossEntity;
import net.ragnar.fmscraft.compat.MagicSpells;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GUMMSS, the Sin of Envy. A caster who fights from the middle distance with a golden staff: the mind and poison.
 * He never needs a second phase; he has too much he can do. He keeps his distance (backing off, circling, pearling
 * away or vanishing when you get close) and picks attacks on the beat of Test &amp; Recognise. The big ones have a cast:
 * he raises his staff and the tome he's casting from floats open before him.
 * <ul>
 *   <li><b>Force Push</b>: palms out; everyone near is thrown back.</li>
 *   <li><b>Force Pull</b>: threads of his mind reach for everyone he can see and drag them in, then a sweep of the
 *       staff. Get behind a pillar before they pull.</li>
 *   <li><b>Telekinetic Slam</b>: one of you is marked, lifted high, shaken and dashed against the floor. Break line of
 *       sight before he lifts, or hit him hard enough while he holds them and his hold breaks.</li>
 *   <li><b>Tome of Reckoning</b>: a silent ripple across the floor; whoever is standing on it as it passes is
 *       caught, lifted and slammed. Jump as it passes under you.</li>
 *   <li><b>Ender Pearl</b>: thrown far from everyone, and he goes with it.</li>
 *   <li><b>Vanish</b>: gone in a puff, walking unseen (a faint trail at his feet), to come out behind one of you with a
 *       poison slash or somewhere far to start casting. A hit while he's unseen knocks him back into sight.</li>
 *   <li><b>Arrow catch</b>: arrows shot at him are sometimes caught in the air, spun round him and sent back.</li>
 *   <li><b>Tome of the Moon</b>: a pale moon sinks over where most of you stand; get out from under it before it
 *       lifts you, it slams down and a ring of force runs out from it (jump it).</li>
 *   <li><b>Arrow Rain</b>: looks up, and arrows rain over the whole arena. He takes far less damage while it does,
 *       but nothing falls close round him.</li>
 *   <li><b>Tome of the Quiver</b>: seven spectral arrows over his head, loosed one by one, dead straight.</li>
 *   <li><b>Poison Darts</b>: a quick fan of poison-tipped darts.</li>
 *   <li><b>Tome of Swords</b>: five iron swords fan out behind him and are thrown one by one, straight at where you
 *       were: no aiming after they leave.</li>
 *   <li><b>Tome of Swapping</b>: trades places with whoever is furthest away (a pillar between you breaks it).</li>
 *   <li><b>Psychic Slash</b>: crescents of mind cut loose across the arena, waist high.</li>
 *   <li><b>Poison Slash</b>: rushes in, three poisoned cuts, leaves a poison cloud and flips away.</li>
 *   <li><b>Tome of Judgement</b>: a giant iron sword drops on each of you, marked by a beam of light.</li>
 *   <li><b>Mirror Images</b>: three more of him. They break at a touch; the real one is the only one with a shadow.</li>
 *   <li><b>Evil Eye</b>: every eye in the void turns green. Don't be looking at him when it ends.</li>
 *   <li><b>Venom Flasks</b>: lingering poison thrown where you're heading.</li>
 * </ul>
 */
public class GummssEntity extends SinBossEntity implements GummssLook {
    public static final BossSong SONG = new BossSong("fmscraft:music.envy.theme", GummssTimeline.BPM, GummssTimeline.FIRST_BEAT_TICK);
    public static final String ILLUSION_TAG = "fmscraft.gummss_illusion";

    // ---- Tuning knobs ----
    /** Full health, for 5-7 players; {@link GummssTimeline#healthScale} takes it down for smaller groups. */
    public static final float MAX_HEALTH = 950;
    /** Damage he takes is multiplied by this (on top of his armor), less during Arrow Rain, more while exposed, and capped. */
    public static final float DAMAGE_TAKEN = 0.8f, RAIN_TAKEN = 0.35f, EXPOSED_BONUS = 1.5f, MAX_HIT = 30f;
    /** Every hit he deals is multiplied by this, then rounded up (his arrows and darts too). */
    public static final float DAMAGE_DEALT = 1.2f;
    /** Chance he catches an arrow shot at him (and how many he can hold at once). */
    public static final float CATCH_CHANCE = 0.35f;
    private static final int MAX_CAUGHT = 3;
    /** Damage to him while he holds someone up that breaks his hold. */
    public static final float LIFT_BREAK = 24f;
    /** Below this share of health he grows desperate: shorter rests, his big spells come round sooner, more of them. */
    public static final float DESPERATE_AT = 0.5f;
    private static final int REST = 16, REST_DESPERATE = 10;
    /** How far he likes to keep from whoever he's fighting. */
    private static final double TOO_CLOSE = 7, TOO_FAR = 16;
    private static final double SPEED = 0.3;

    private static final TrackedData<Integer> POSE = DataTracker.registerData(GummssEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> STARING = DataTracker.registerData(GummssEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    enum Action {
        ENTRANCE, MOVE, EXPOSED,
        FORCE_PUSH, FORCE_PULL, TK_SLAM, RECKONING, PEARL, VANISH, MOON, ARROW_RAIN, QUIVER, POISON_DARTS, SWORDS, SWAP,
        PSYCHIC_SLASH, POISON_SLASH, SWORD_RAIN, ILLUSIONS, EVIL_EYE, VENOM_FLASKS, LEVITATION, JUDGEMENT, UPSIDE_DOWN
    }

    private Action action = Action.ENTRANCE;
    private int actionTicks, stageTicks = -1, rest, exposedTicks, repeats;
    private Action lastAttack;
    private final Map<Action, Integer> lastUsed = new EnumMap<>(Action.class);
    private int meleeCooldown, closeTicks, catchPose, strafeTicks;
    private double strafe = 1;
    private UUID focus;
    private int focusTicks;
    private final List<EnvyHazards.Hazard> hazards = new ArrayList<>();
    private final List<UUID> illusions = new ArrayList<>();
    private final Map<UUID, Integer> lastPicked = new HashMap<>();
    private final Map<UUID, Integer> straying = new HashMap<>();
    private List<ServerPlayerEntity> fighters = List.of();
    private boolean untouchable = true, scaled, forceCatch, heldStill;
    /** Minecraft caps max health at 1024: for a group big enough to need more, he takes this much less damage instead. */
    private float toughness = 1f;
    private static final float HEALTH_CAP = 1024f;

    // The fall
    private final Map<UUID, Vec3d> held = new LinkedHashMap<>();
    private int fallLength, landAt;
    private boolean fallFromSky;

    // The attack in progress
    private EnvyHazards.SwordFan fan;
    private EnvyHazards.QuiverRing quiver;
    private EnvyHazards.Lift lift;
    private float liftDamage;
    private List<UUID> pulled = List.of();
    private Vec3d spot;
    private boolean ambush, pearlDone, keepFocus;

    public GummssEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 1000;
        bossBar().setColor(BossBar.Color.GREEN);
        setPersistent();
        ItemStack staff = MagicSpells.item(MagicSpells.GOLDEN_STAFF);
        equipStack(EquipmentSlot.MAINHAND, staff.isEmpty() ? new ItemStack(Items.BLAZE_ROD) : staff);
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        setEquipmentDropChance(EquipmentSlot.OFFHAND, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HEALTH)
                .add(EntityAttributes.GENERIC_ARMOR, 8)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 4)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, Math.ceil(8 * DAMAGE_DEALT))
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    /** His Mirror Images never take damage from him. */
    public static void registerEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity.getCommandTags().contains(ILLUSION_TAG) && source.getAttacker() instanceof GummssEntity));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_IDLE);
        builder.add(STARING, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    protected Text bossBarName() {
        return Text.literal("GUMMSS");
    }

    /** No second phase: health never gets this low while he's alive. */
    @Override
    protected float phaseTwoHealth() {
        return -1f;
    }

    @Override
    protected BossSong songForPhase(int phase) {
        return SONG;
    }

    // ---- What the client and the tests see ----

    @Override
    public int poseState() {
        return dataTracker.get(POSE);
    }

    /** The Evil Eye: every eye in the void turns on you (EnvySky). */
    public boolean isStaring() {
        return dataTracker.get(STARING);
    }

    public boolean isUntouchable() {
        return untouchable;
    }

    /** How much damage it takes to beat him from full: his max health, and more past the 1024 cap for a big group. */
    public float effectiveMaxHealth() {
        return getMaxHealth() / toughness;
    }

    public String currentAction() {
        return action.name();
    }

    public int illusionCount() {
        return illusions.size();
    }

    /** For tests: the next arrow shot at him is caught. */
    public void forceNextCatch() {
        forceCatch = true;
    }

    /** For tests: between attacks he stands still and starts none of his own, only those he's told to. */
    public void holdStill() {
        heldStill = true;
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
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

    void addHazard(EnvyHazards.Hazard hazard) {
        hazards.add(hazard);
    }

    /** A hit he deals, after {@link #DAMAGE_DEALT}, rounded up to a whole number. */
    static float dealt(float damage) {
        return (float) Math.ceil(damage * DAMAGE_DEALT);
    }

    /** A hit from his staff or anything he throws: a shield held towards him (or {@code from}) stops it. */
    boolean strike(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobProjectile(this, this), dealt(damage));
    }

    boolean strike(ServerPlayerEntity player, float damage, Entity from) {
        return player.damage(getDamageSources().mobProjectile(from, this), dealt(damage));
    }

    /** Being dashed against the floor by his mind: no shield helps. */
    void crush(ServerPlayerEntity player, float damage) {
        player.damage(getDamageSources().indirectMagic(this, this), dealt(damage));
    }

    void poison(ServerPlayerEntity player, int ticks, int amplifier) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, ticks, amplifier), this);
    }

    /** A cut of his mind: a moment slowed, the world swimming. */
    void daze(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 1), this);
    }

    /** Throws {@code player} along {@code dir} (flattened), {@code horizontal} fast and {@code up} into the air. */
    void launch(ServerPlayerEntity player, Vec3d dir, double horizontal, double up) {
        Vec3d flat = dir.multiply(1, 0, 1);
        flat = flat.lengthSquared() > 1e-4 ? flat.normalize() : Vec3d.fromPolar(0, getYaw());
        player.setVelocity(flat.x * horizontal, up, flat.z * horizontal);
        player.velocityModified = true;
    }

    void flash(ServerPlayerEntity player, float strength, int ticks) {
        Cinematics.flash(player, strength, ticks);
    }

    /** Everyone in the arena, fighting or not. */
    List<ServerPlayerEntity> audience(ServerWorld world) {
        return world.getPlayers(p -> p.squaredDistanceTo(this) < 200 * 200);
    }

    /** Particles everyone in the arena sees, however far across it they are. */
    void emit(ServerWorld world, ParticleEffect particle, Vec3d at, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : audience(world)) world.spawnParticles(p, particle, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
    }

    ServerPlayerEntity nearestFighter() {
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity p : fighters) if (best == null || p.squaredDistanceTo(this) < best.squaredDistanceTo(this)) best = p;
        return best;
    }

    /** An arrow of his that doesn't litter the arena. */
    ArrowEntity newArrow(ServerWorld world, ItemStack stack) {
        return newArrow(world, stack, 20);
    }

    /** An arrow of his, gone {@code groundTicks} after it lands. */
    ArrowEntity newArrow(ServerWorld world, ItemStack stack, int groundTicks) {
        ArrowEntity arrow = new ArrowEntity(world, this, stack, null) {
            @Override
            public void tick() {
                super.tick();
                if (inGround && inGroundTime > groundTicks || age > 200) discard();
            }
        };
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        return arrow;
    }

    private SpectralArrowEntity newSpectralArrow(ServerWorld world) {
        SpectralArrowEntity arrow = new SpectralArrowEntity(world, this, new ItemStack(Items.SPECTRAL_ARROW), null) {
            @Override
            public void tick() {
                super.tick();
                if (inGround && inGroundTime > 20 || age > 200) discard();
            }
        };
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        return arrow;
    }

    /** His pearl landed: he's there. */
    void pearlLanded(Vec3d to) {
        if (!(getWorld() instanceof ServerWorld world) || isDead()) return;
        Vec3d from = getPos();
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 40, 0.3, 0.8, 0.3, 0.05);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.8f);
        getNavigation().stop();
        refreshPositionAndAngles(to.x, to.y, to.z, getYaw(), getPitch());
        setVelocity(Vec3d.ZERO);
        fallDistance = 0;
        world.spawnParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 60, 0.4, 0.8, 0.4, 0.4);
        world.spawnParticles(EnvyHazards.DEEP_GREEN, to.x, to.y + 1, to.z, 20, 0.4, 0.8, 0.4, 0);
        world.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.8f);
        pearlDone = true;
    }

    // ---- Taking damage ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return false;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        Entity attacker = source.getAttacker();
        if (attacker == this || attacker instanceof GummssIllusionEntity) return false;
        if (!untouchable && action != Action.ENTRANCE && source.getSource() instanceof PersistentProjectileEntity arrow
                && !(arrow instanceof TridentEntity) && attacker instanceof ServerPlayerEntity shooter && caught() < MAX_CAUGHT
                && (forceCatch || random.nextFloat() < CATCH_CHANCE)) {
            forceCatch = false;
            catchArrow(arrow, shooter);
            return false;
        }
        if (untouchable) {
            if (attacker instanceof PlayerEntity) {
                getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.HOSTILE, 1.2f, 0.6f);
            }
            return false;
        }
        if (isInvisible() && attacker instanceof PlayerEntity) reveal(true);
        amount *= DAMAGE_TAKEN * toughness;
        if (rainActive()) amount *= RAIN_TAKEN;
        if (action == Action.EXPOSED) amount *= EXPOSED_BONUS;
        amount = Math.min(amount, MAX_HIT);
        boolean hurt = super.damage(source, Math.max(0.01f, amount));
        if (hurt && lift != null && lift.holding()) {
            liftDamage += amount;
            if (liftDamage >= LIFT_BREAK) {
                // Hit hard enough while he's holding someone up: his focus breaks
                lift.drop();
                lift = null;
                getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_HURT, SoundCategory.HOSTILE, 2.0f, 0.6f);
                expose(30, POSE_STUNNED);
            }
        }
        return hurt;
    }

    private boolean rainActive() {
        return action == Action.ARROW_RAIN && actionTicks > 24 && actionTicks <= 24 + 100;
    }

    private int caught() {
        int n = 0;
        for (EnvyHazards.Hazard h : hazards) if (h instanceof EnvyHazards.CaughtArrow) n++;
        return n;
    }

    /** Stops an arrow dead in the air with a flick of his hand. */
    private void catchArrow(PersistentProjectileEntity arrow, ServerPlayerEntity shooter) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        Vec3d at = arrow.getPos();
        ItemStack stack = new ItemStack(arrow instanceof SpectralArrowEntity ? Items.SPECTRAL_ARROW : Items.ARROW);
        arrow.discard();
        hazards.add(new EnvyHazards.CaughtArrow(world, this, at, shooter.getUuid(), stack));
        world.spawnParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 12, 0.1, 0.1, 0.1, 0.2);
        world.spawnParticles(EnvyHazards.PSYCHIC, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.5f, 1.4f);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.0f, 0.8f);
        catchPose = 16;
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
        // No slowing, levitating or poisoning a sin
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
            world.spawnParticles(EnvyHazards.PSYCHIC, c.x, c.y, c.z, 30, 0.8, 0.8, 0.8, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 2f, 0.8f);
            bubble.discard();
        }
    }

    @Override
    protected void tickPhase(int phase, int songTicks) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        fighters = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 120 * 120);
        if (!scaled) scaleHealth(world);
        boolean inArena = Arenas.sinOf(world) == Sin.ENVY;

        if (rest > 0) rest--;
        if (meleeCooldown > 0) meleeCooldown--;
        if (catchPose > 0 && --catchPose == 0 && action == Action.MOVE) setPoseState(POSE_IDLE);
        actionTicks++;
        switch (action) {
            case ENTRANCE -> tickEntrance(world, songTicks);
            case MOVE -> tickMove(world);
            case EXPOSED -> tickExposed(world);
            case FORCE_PUSH -> tickForcePush(world);
            case FORCE_PULL -> tickForcePull(world);
            case TK_SLAM -> tickSlam(world);
            case RECKONING -> tickReckoning(world);
            case PEARL -> tickPearl(world);
            case VANISH -> tickVanish(world);
            case MOON -> tickMoon(world);
            case ARROW_RAIN -> tickArrowRain(world);
            case QUIVER -> tickQuiver(world);
            case POISON_DARTS -> tickDarts(world);
            case SWORDS -> tickSwords(world);
            case SWAP -> tickSwap(world);
            case PSYCHIC_SLASH -> tickPsychicSlash(world);
            case POISON_SLASH -> tickPoisonSlash(world);
            case SWORD_RAIN -> tickSwordRain(world);
            case ILLUSIONS -> tickIllusions(world);
            case EVIL_EYE -> tickEvilEye(world);
            case VENOM_FLASKS -> tickFlasks(world);
            case LEVITATION -> tickLevitation(world);
            case JUDGEMENT -> tickJudgement(world);
            case UPSIDE_DOWN -> tickUpsideDown(world);
        }
        tickHazards(world);
        illusions.removeIf(id -> !(world.getEntity(id) instanceof GummssIllusionEntity i) || !i.isAlive());
        tickAura(world);
        if (inArena) {
            tickAmbience(world);
            if (action != Action.ENTRANCE) catchStrays(world);
        }
    }

    private void scaleHealth(ServerWorld world) {
        scaled = true;
        int players = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 160 * 160).size();
        float wanted = MAX_HEALTH * GummssTimeline.healthScale(players);
        float max = Math.min(wanted, HEALTH_CAP);
        toughness = max / wanted;
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(max);
        setHealth(max);
    }

    @Override
    protected void onBeat(int phase, int beat) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (action == Action.MOVE && rest <= 0 && !fighters.isEmpty() && !heldStill) startAttack(chooseAttack());
        // The eye in the floor pulses with the bass
        if (action != Action.ENTRANCE && beat % 4 == 0 && Arenas.sinOf(world) == Sin.ENVY) {
            hazards.add(new EnvyHazards.Shockwave(EnvyArena.CENTER, 0.5, beat % 16 == 0 ? 9 : 6, 0, 0));
            world.spawnParticles(ParticleTypes.SCULK_SOUL, EnvyArena.CENTER.x, EnvyArena.CENTER.y + 0.2, EnvyArena.CENTER.z, 4, 1.2, 0.1, 1.2, 0.02);
        }
    }

    private boolean desperate() {
        return getHealth() < getMaxHealth() * DESPERATE_AT;
    }

    // ---- Choosing attacks ----

    private Action chooseAttack() {
        List<Action> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        double near = nearestDistance();
        int band = near < 6 ? 0 : near > 15 ? 2 : 1;
        ServerPlayerEntity far = farthest();
        offer(options, weights, Action.FORCE_PUSH, new int[]{5, 1, 0}[band], 100);
        if (near < 9) offer(options, weights, Action.POISON_SLASH, new int[]{4, 2, 0}[band], 110);
        offer(options, weights, Action.VANISH, new int[]{3, 1, 1}[band], 300);
        offer(options, weights, Action.PEARL, new int[]{3, 1, 0}[band], 140);
        offer(options, weights, Action.PSYCHIC_SLASH, new int[]{2, 4, 2}[band], 90);
        offer(options, weights, Action.POISON_DARTS, new int[]{1, 3, 3}[band], 80);
        offer(options, weights, Action.TK_SLAM, new int[]{1, 3, 3}[band], 240);
        offer(options, weights, Action.FORCE_PULL, new int[]{0, 2, 4}[band], 260);
        if (far != null && far.distanceTo(this) > 10) offer(options, weights, Action.SWAP, new int[]{0, 1, 4}[band], 300);
        offer(options, weights, Action.SWORDS, new int[]{1, 3, 3}[band], 260);
        offer(options, weights, Action.QUIVER, new int[]{1, 3, 3}[band], 220);
        offer(options, weights, Action.VENOM_FLASKS, new int[]{1, 2, 2}[band], 260);
        offer(options, weights, Action.RECKONING, new int[]{3, 2, 1}[band], 420);
        offer(options, weights, Action.MOON, new int[]{1, 2, 2}[band], 520);
        offer(options, weights, Action.ARROW_RAIN, new int[]{1, 2, 2}[band], 560);
        offer(options, weights, Action.SWORD_RAIN, new int[]{1, 2, 2}[band], 460);
        if (illusions.isEmpty()) offer(options, weights, Action.ILLUSIONS, new int[]{1, 2, 1}[band], 760);
        offer(options, weights, Action.EVIL_EYE, new int[]{1, 2, 2}[band], 640);
        // The rare ones
        offer(options, weights, Action.LEVITATION, 1, 760);
        offer(options, weights, Action.JUDGEMENT, 1, 900);
        offer(options, weights, Action.UPSIDE_DOWN, 1, 820);
        if (options.isEmpty()) return Action.PSYCHIC_SLASH;
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
        int gap = desperate() ? minGap * 3 / 4 : minGap;
        if (used != null && age - used < gap) return;
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
        getNavigation().stop();
    }

    private void endAttack() {
        endAttack(desperate() ? REST_DESPERATE : REST);
    }

    private void endAttack(int restTicks) {
        action = Action.MOVE;
        actionTicks = 0;
        stageTicks = -1;
        setPoseState(POSE_IDLE);
        setNoGravity(false);
        fan = null;
        quiver = null;
        rest = restTicks;
    }

    /** Left reeling for {@code ticks}, taking extra damage. */
    private void expose(int ticks, int pose) {
        action = Action.EXPOSED;
        actionTicks = 0;
        exposedTicks = ticks;
        setPoseState(pose);
        setNoGravity(false);
        getNavigation().stop();
        dataTracker.set(STARING, false);
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

    private ServerPlayerEntity farthest() {
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity p : fighters) if (best == null || p.squaredDistanceTo(this) > best.squaredDistanceTo(this)) best = p;
        return best;
    }

    private double nearestDistance() {
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : fighters) best = Math.min(best, p.distanceTo(this));
        return best;
    }

    // ---- The fall ----

    private void tickEntrance(ServerWorld world, int songTicks) {
        int t = actionTicks;
        if (t == 1) {
            untouchable = true;
            fallFromSky = Arenas.sinOf(world) == Sin.ENVY && getY() > EnvyArena.FLOOR_Y + 10;
            fallLength = fallFromSky ? GummssTimeline.fallTicks(songTicks) : 0;
            landAt = fallFromSky ? GummssTimeline.HOVER_TICKS + fallLength : 1;
            if (fallFromSky) {
                for (ServerPlayerEntity p : fighters) {
                    held.put(p.getUuid(), new Vec3d(p.getX(), 0, p.getZ()));
                    Cinematics.freeze(p);
                }
            }
        }
        if (t < landAt) {
            double h = fallHeight(t);
            double next = fallHeight(t + 1);
            setNoGravity(true);
            setVelocity(Vec3d.ZERO);
            setPosition(EnvyArena.CENTER.x, EnvyArena.FLOOR_Y + h, EnvyArena.CENTER.z);
            setPoseState(POSE_FALL);
            // Turning slowly in the middle of the ring, looking at each of them in turn
            faceYaw(getYaw() + 1.1f);
            setPitch(10f);
            for (ServerPlayerEntity p : fighters) {
                Vec3d slot = held.get(p.getUuid());
                if (slot == null) continue;
                double targetY = EnvyArena.FLOOR_Y + h;
                p.setVelocity((slot.x - p.getX()) * 0.2, next - h + (targetY - p.getY()) * 0.2, (slot.z - p.getZ()) * 0.2);
                p.velocityModified = true;
                p.fallDistance = 0;
                if (!p.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, landAt - t + 20, 0, false, false, false));
                }
            }
            fallEffects(world, t);
            return;
        }
        if (t == landAt) land(world);
        int s = t - landAt;
        if (s == 14) {
            // Rises from the crouch, staff high: the show begins
            setPoseState(POSE_CAST);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 3.0f, 0.5f);
            emit(world, EnvyHazards.PSYCHIC, getPos().add(0, 2.2, 0), 60, 0.4, 0.6, 0.4, 0);
        }
        if (s >= GummssTimeline.LAND_TICKS) {
            untouchable = false;
            // His great spells aren't ready from the start
            for (Action big : new Action[]{Action.MOON, Action.ARROW_RAIN, Action.SWORD_RAIN, Action.ILLUSIONS, Action.EVIL_EYE, Action.RECKONING,
                    Action.LEVITATION, Action.JUDGEMENT, Action.UPSIDE_DOWN}) {
                lastUsed.put(big, age - 300);
            }
            endAttack(12);
        }
    }

    private double fallHeight(int t) {
        if (!fallFromSky) return 0;
        if (t <= GummssTimeline.HOVER_TICKS) return GummssTimeline.FALL_HEIGHT;
        return GummssTimeline.fallHeight(t - GummssTimeline.HOVER_TICKS, fallLength, GummssTimeline.FALL_HEIGHT);
    }

    /** Falling: his aura streaming up off him, the air rushing past, the arena coming up out of the fog. */
    private void fallEffects(ServerWorld world, int t) {
        Vec3d c = getPos();
        emit(world, EnvyHazards.DEEP_GREEN, c.add(0, 1, 0), 6, 0.5, 0.9, 0.5, 0);
        emit(world, EnvyHazards.PSYCHIC, c.add(0, 1, 0), 2, 0.4, 0.8, 0.4, 0);
        if (t > GummssTimeline.HOVER_TICKS) {
            // Specks hanging in the air that everyone falls past
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * MathHelper.TAU, r = random.nextDouble() * 14;
                Vec3d p = c.add(Math.cos(a) * r, -4 - random.nextDouble() * 14, Math.sin(a) * r);
                emit(world, i % 3 == 0 ? ParticleTypes.WARPED_SPORE : EnvyHazards.DEEP_GREEN, p, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * MathHelper.TAU, r = 3 + random.nextDouble() * 9;
                Vec3d p = c.add(Math.cos(a) * r, -2 - random.nextDouble() * 6, Math.sin(a) * r);
                for (ServerPlayerEntity v : audience(world)) world.spawnParticles(v, ParticleTypes.END_ROD, true, p.x, p.y, p.z, 0, 0, 1, 0, 0.9);
            }
        }
        if (t == GummssTimeline.HOVER_TICKS) {
            for (ServerPlayerEntity p : audience(world)) {
                p.playSoundToPlayer(SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.MASTER, 0.5f, 0.8f);
                p.playSoundToPlayer(SoundEvents.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundCategory.MASTER, 0.8f, 0.5f);
            }
        }
        if (t % 40 == 20) for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 0.7f, 0.5f);
    }

    /** Everyone hits the ground as the bass drops: a blast of his mind throws them back. Hurts nobody. */
    private void land(ServerWorld world) {
        setNoGravity(false);
        double ground = groundBelow(getPos().add(0, 0.5, 0), 80);
        if (fallFromSky) setPosition(EnvyArena.CENTER.x, EnvyArena.FLOOR_Y, EnvyArena.CENTER.z);
        else if (!Double.isNaN(ground)) setPosition(getX(), ground, getZ());
        setVelocity(Vec3d.ZERO);
        setPoseState(POSE_LAND);
        Vec3d c = getPos();
        for (ServerPlayerEntity p : audience(world)) {
            Vec3d slot = held.get(p.getUuid());
            if (slot != null) {
                p.removeStatusEffect(StatusEffects.SLOW_FALLING);
                p.teleport(world, slot.x, EnvyArena.FLOOR_Y, slot.z, p.getYaw(), p.getPitch());
                p.fallDistance = 0;
            }
            // Everyone the intro froze, watchers in creative too
            Cinematics.unfreeze(p);
            double d = p.getPos().distanceTo(c);
            flash(p, (float) (0.75 * Math.max(0.3, 1 - d / 60)), 22);
            if (d < 16 && !p.isCreative() && !p.isSpectator()) launch(p, p.getPos().subtract(c), 1.5 * (1 - d / 16) + 0.4, 0.45);
        }
        held.clear();
        emit(world, ParticleTypes.FLASH, c.add(0, 1, 0), 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.SONIC_BOOM, c.add(0, 1, 0), 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.GUST_EMITTER_LARGE, c.add(0, 0.5, 0), 1, 0, 0, 0, 0);
        emit(world, EnvyHazards.DEEP_GREEN, c.add(0, 0.6, 0), 200, 2.5, 0.4, 2.5, 0);
        emit(world, EnvyHazards.PSYCHIC, c.add(0, 1, 0), 120, 1.5, 1.2, 1.5, 0);
        emit(world, ParticleTypes.ENCHANT, c.add(0, 1, 0), 120, 2, 1, 2, 1.5);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState()), c.x, c.y + 0.2, c.z, 160, 3, 0.2, 3, 0.25);
        hazards.add(new EnvyHazards.Shockwave(c, 1.3, 30, 0, 0));
        hazards.add(new EnvyHazards.Shockwave(c, 0.9, 20, 0, 0));
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 4.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.5f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    // ---- Between attacks: keeping his distance ----

    private void tickMove(ServerWorld world) {
        if (heldStill) {
            stand();
            return;
        }
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
        if (catchPose > 0) setPoseState(POSE_CATCH);
        if (--strafeTicks <= 0 || horizontalCollision) {
            strafe = random.nextBoolean() ? 1 : -1;
            strafeTicks = 60 + random.nextInt(50);
        }
        double near = nearestDistance();
        closeTicks = near < 4 ? closeTicks + 1 : 0;
        if (closeTicks > 30 && rest <= 4) {
            // Cornered: he gets out, one way or another
            closeTicks = 0;
            Action[] outs = {Action.VANISH, Action.PEARL, Action.FORCE_PUSH, Action.POISON_SLASH};
            startAttack(outs[random.nextInt(outs.length)]);
            return;
        }
        if (age % 8 == 0 || getNavigation().isIdle()) {
            Vec3d to = getPos().subtract(target.getPos()).multiply(1, 0, 1);
            double d = to.length();
            Vec3d away = d > 1e-3 ? to.multiply(1 / d) : Vec3d.fromPolar(0, getYaw() + 180);
            Vec3d side = new Vec3d(-away.z, 0, away.x).multiply(strafe);
            Vec3d dest;
            if (d < TOO_CLOSE) dest = getPos().add(away.multiply(6)).add(side.multiply(2));
            else if (d > TOO_FAR) dest = getPos().subtract(away.multiply(6)).add(side.multiply(2));
            else dest = getPos().add(side.multiply(5)).add(away.multiply((11 - d) * 0.4));
            dest = inside(dest, 44);
            getNavigation().startMovingTo(dest.x, dest.y, dest.z, d < TOO_CLOSE ? 1.25 : 1.0);
        }
        double reach = 1.6 + getWidth();
        if (meleeCooldown <= 0 && squaredDistanceTo(target) < reach * reach) {
            swingHand(Hand.MAIN_HAND);
            tryAttack(target);
            meleeCooldown = 20;
        }
        if (age % 6 == 0) {
            // Sounds of him, now and then: whispers of the mind
            if (random.nextInt(30) == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_AMBIENT, SoundCategory.HOSTILE, 1.2f, 0.7f);
        }
    }

    /** {@code pos}, pulled in to at most {@code radius} from the middle of the arena (if he's in it). */
    private Vec3d inside(Vec3d pos, double radius) {
        if (Arenas.sinOf(getWorld()) != Sin.ENVY) return pos;
        Vec3d from = pos.subtract(EnvyArena.CENTER).multiply(1, 0, 1);
        if (from.length() <= radius) return pos;
        Vec3d in = EnvyArena.CENTER.add(from.normalize().multiply(radius));
        return new Vec3d(in.x, pos.y, in.z);
    }

    private void tickExposed(ServerWorld world) {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
        if (actionTicks % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = actionTicks * 0.3 + i * MathHelper.TAU / 3;
                world.spawnParticles(EnvyHazards.PSYCHIC, getX() + Math.cos(a) * 0.6, getY() + getHeight() + 0.2, getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
            }
        }
        if (actionTicks >= exposedTicks) endAttack();
    }

    // ---- Attacks ----

    private void tickForcePush(ServerWorld world) {
        int windup = desperate() ? 9 : 12, t = actionTicks;
        double radius = 9;
        if (t == 1) {
            setPoseState(POSE_PUSH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, SoundCategory.HOSTILE, 2.0f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        stand();
        if (t < windup) {
            // His mind gathering in: a ring drawn in to him
            EnvyHazards.ring(world, EnvyHazards.PSYCHIC, getPos(), radius * (1 - t / (double) windup) + 1, 32, t * 0.2);
            Vec3d hand = handPos(false);
            world.spawnParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 4, 0.3, 0.3, 0.3, 0.6);
            return;
        }
        if (t == windup) {
            Vec3d c = getPos();
            for (ServerPlayerEntity p : fighters) {
                double d = EnvyHazards.flatDistance(p.getPos(), c);
                if (d > radius || Math.abs(p.getY() - c.y) > 3) continue;
                strike(p, 7);
                launch(p, p.getPos().subtract(c), 2.0 * (1 - d / 12) + 0.6, 0.55);
            }
            hazards.add(new EnvyHazards.Shockwave(c, 1.1, radius, 0, 0));
            world.spawnParticles(ParticleTypes.GUST_EMITTER_LARGE, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            world.spawnParticles(EnvyHazards.PSYCHIC, c.x, c.y + 1, c.z, 60, 1.5, 0.6, 1.5, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.HOSTILE, 2.5f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 0.8f, 1.5f);
        }
        if (t >= windup + 10) endAttack();
    }

    private void tickForcePull(ServerWorld world) {
        int windup = desperate() ? 12 : 16, pull = 12, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_PULL);
            List<UUID> ids = new ArrayList<>();
            for (ServerPlayerEntity p : fighters) if (p.distanceTo(this) < 26 && canSee(getEyePos(), p.getBoundingBox().getCenter())) ids.add(p.getUuid());
            pulled = ids;
            if (pulled.isEmpty()) {
                endAttack();
                return;
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.0f, 1.6f);
        }
        stand();
        Vec3d hand = handPos(false);
        if (t <= windup + pull) {
            // The threads of his mind reaching for them; a pillar between you cuts yours
            List<UUID> kept = new ArrayList<>();
            for (UUID id : pulled) {
                ServerPlayerEntity p = fighter(id);
                if (p == null || !canSee(getEyePos(), p.getBoundingBox().getCenter())) continue;
                kept.add(id);
                if (t % 2 == 0) {
                    Vec3d to = p.getBoundingBox().getCenter();
                    for (double s = 0; s < 1; s += 0.05) {
                        Vec3d q = hand.lerp(to, s);
                        world.spawnParticles(t < windup ? EnvyHazards.VIOLET : EnvyHazards.PSYCHIC, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
                    }
                }
                if (t >= windup) {
                    Vec3d dir = getPos().subtract(p.getPos());
                    if (dir.horizontalLength() > 2.8) {
                        Vec3d v = dir.multiply(1, 0, 1).normalize().multiply(1.15);
                        p.setVelocity(v.x, 0.12, v.z);
                        p.velocityModified = true;
                    }
                    p.fallDistance = 0;
                }
            }
            pulled = kept;
            if (t == windup) {
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_2.value(), SoundCategory.HOSTILE, 2.0f, 0.7f);
                setPoseState(POSE_SLAM);
            }
            return;
        }
        if (t == windup + pull + 1) {
            // Reeled in: a full sweep of the staff
            setPoseState(POSE_SLASH_A);
            swingHand(Hand.MAIN_HAND);
            for (ServerPlayerEntity p : fighters) {
                if (EnvyHazards.flatDistance(p.getPos(), getPos()) > 4 || Math.abs(p.getY() - getY()) > 2.5) continue;
                strike(p, 8);
                daze(p);
                launch(p, p.getPos().subtract(getPos()), 1.4, 0.4);
            }
            for (int i = 0; i < 12; i++) {
                double a = i * MathHelper.TAU / 12;
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, getX() + Math.cos(a) * 2.2, getY() + 1.1, getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.5f, 1.4f);
        }
        if (t >= windup + pull + 10) endAttack();
    }

    private void tickSlam(ServerWorld world) {
        int windup = desperate() ? 14 : 18, t = actionTicks;
        if (t == 1) {
            // Prefers whoever is furthest off, shooting from safety
            ServerPlayerEntity target = random.nextBoolean() ? farthest() : pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            lift = null;
            setPoseState(POSE_LIFT);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 2.0f, 0.6f);
            target.playSoundToPlayer(SoundEvents.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target != null && t <= windup) faceTowards(target.getPos());
        if (t < windup) {
            // Marked: a ring of his colour closing round your feet
            if (target != null) EnvyHazards.ring(world, EnvyHazards.PSYCHIC, target.getPos(), 1.6 - t * 0.05, 16, t * 0.3);
            return;
        }
        if (t == windup) {
            if (target == null || target.distanceTo(this) > 32 || !canSee(getEyePos(), target.getBoundingBox().getCenter())) {
                // Out of sight: the hold slips
                Vec3d hand = handPos(false);
                world.spawnParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 10, 0.2, 0.2, 0.2, 0.02);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.0f, 1.4f);
                endAttack();
                return;
            }
            lift = new EnvyHazards.Lift(target.getUuid(), 15);
            liftDamage = 0;
            hazards.add(lift);
        }
        if (lift == null || !hazards.contains(lift)) {
            if (stageTicks < 0) stageTicks = 0;
            if (++stageTicks >= 8) {
                lift = null;
                endAttack();
            }
            return;
        }
        setPoseState(lift.holding() ? POSE_LIFT : POSE_SLAM);
        if (target != null) faceTowards(target.getPos());
    }

    private void tickReckoning(ServerWorld world) {
        int windup = desperate() ? 20 : 26, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_reckoning", Items.ENCHANTED_BOOK), windup + 4));
            hazards.add(new EnvyHazards.Circle(getPos(), 2.4, windup, ParticleTypes.SCULK_SOUL));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.EVENT_MOB_EFFECT_TRIAL_OMEN, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CONDUIT_DEACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        stand();
        if (t < windup) return;
        if (t == windup) {
            setPoseState(POSE_SLAM);
            swingHand(Hand.MAIN_HAND);
            hazards.add(new EnvyHazards.Reckoning(getPos()));
            world.spawnParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.2, getZ(), 16, 0.4, 0.1, 0.4, 0.04);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (t >= windup + 24) endAttack();
    }

    private void tickPearl(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            Vec3d dest = farSpot(12, 22);
            if (dest == null) {
                endAttack();
                return;
            }
            pearlDone = false;
            setPoseState(POSE_THROW);
            faceTowards(dest);
            swingHand(Hand.OFF_HAND);
            hazards.add(new EnvyHazards.Pearl(world, handPos(false), dest));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDER_PEARL_THROW, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        stand();
        if (t == 6) setPoseState(POSE_IDLE);
        if (pearlDone || t > 40) endAttack(6);
    }

    private void tickVanish(ServerWorld world) {
        int t = actionTicks, limit = 60;
        if (t == 1) {
            puff(world, getPos());
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 2.0f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.5f, 0.6f);
            setInvisible(true);
            ambush = random.nextFloat() < 0.55f;
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            spot = ambush ? null : farSpot(14, 26);
            if (!ambush && spot == null) ambush = true;
        }
        if (ambush) {
            ServerPlayerEntity target = fighter(focus);
            if (target != null && (t % 10 == 2 || spot == null)) {
                Vec3d behind = target.getPos().subtract(Vec3d.fromPolar(0, target.getYaw()).multiply(2.5));
                Vec3d stand = standingSpot(behind);
                spot = stand != null ? stand : target.getPos();
            }
        }
        if (spot != null && (t % 6 == 2 || getNavigation().isIdle())) getNavigation().startMovingTo(spot.x, spot.y, spot.z, 1.6);
        // The faintest trail where he walks unseen
        if (t % 5 == 0) world.spawnParticles(EnvyHazards.DEEP_GREEN, getX(), getY() + 0.05, getZ(), 1, 0.1, 0, 0.1, 0);
        boolean there = spot != null && getPos().squaredDistanceTo(spot) < 2.2 * 2.2;
        if (t >= limit || there && t > 18) {
            if (spot != null && !there) refreshPositionAndAngles(spot.x, spot.y, spot.z, getYaw(), getPitch());
            reveal(false);
            if (ambush) {
                ServerPlayerEntity target = fighter(focus);
                if (target != null) faceTowards(target.getPos());
                startAttack(Action.POISON_SLASH);
            } else {
                Action[] casts = {Action.QUIVER, Action.SWORDS, Action.POISON_DARTS};
                startAttack(casts[random.nextInt(casts.length)]);
            }
        }
    }

    /** Back into sight: a puff of green smoke. Knocked out by a hit, he reels for a moment. */
    private void reveal(boolean knocked) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        setInvisible(false);
        puff(world, getPos());
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 2.0f, 1.3f);
        if (knocked && action == Action.VANISH) expose(20, POSE_STUNNED);
    }

    private void puff(ServerWorld world, Vec3d at) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0.02);
        world.spawnParticles(EnvyHazards.DEEP_GREEN, at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 30, 0.3, 0.6, 0.3, 0.05);
    }

    private void tickMoon(ServerWorld world) {
        int cast = 14, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            spot = clusterSpot();
            if (spot == null) {
                endAttack();
                return;
            }
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_the_moon", Items.ENCHANTED_BOOK), cast + 30));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        stand();
        if (spot != null) faceTowards(spot);
        if (t == cast) {
            hazards.add(new EnvyHazards.Moon(spot));
            // A big group spread out gets a second moon over the other crowd
            if (fighters.size() >= 6) {
                Vec3d other = clusterSpot(spot, 16);
                if (other != null) hazards.add(new EnvyHazards.Moon(other));
            }
            setPoseState(POSE_SKY);
        }
        // He holds it up the whole way down: a long time standing still
        if (t >= cast + EnvyHazards.Moon.SINK + 6) endAttack();
    }

    private void tickArrowRain(ServerWorld world) {
        int windup = 24, rain = 100, t = actionTicks;
        double safe = 5;
        if (t == 1) {
            setPoseState(POSE_SKY);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_START.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
            for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 0.8f, 0.5f);
            hazards.add(new EnvyHazards.Circle(getPos(), safe, windup + rain, EnvyHazards.PSYCHIC));
        }
        stand();
        setPitch(-60f);
        // A shell of his mind round him while it rains
        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                Vec3d d = new Vec3d(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian()).normalize().multiply(1.4);
                world.spawnParticles(ParticleTypes.END_ROD, getX() + d.x, getY() + 1 + d.y, getZ() + d.z, 1, 0, 0, 0, 0);
            }
        }
        if (t < windup) {
            // The arrows gathering high over everyone
            for (ServerPlayerEntity p : fighters) world.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 22, p.getZ(), 6, 6, 1, 6, 0.05);
            return;
        }
        if (t <= windup + rain) {
            for (ServerPlayerEntity p : fighters) {
                rainArrow(world, p.getPos().add(random.nextGaussian() * 1.6, 0, random.nextGaussian() * 1.6), safe, p.getY());
                double a = random.nextDouble() * MathHelper.TAU, r = random.nextDouble() * 8;
                rainArrow(world, p.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r), safe, p.getY());
            }
            if (Arenas.sinOf(world) == Sin.ENVY) {
                for (int i = 0; i < 2; i++) {
                    double a = random.nextDouble() * MathHelper.TAU, r = Math.sqrt(random.nextDouble()) * (EnvyArena.RADIUS - 2);
                    rainArrow(world, EnvyArena.CENTER.add(Math.cos(a) * r, 0, Math.sin(a) * r), safe, EnvyArena.FLOOR_Y);
                }
            }
            if (t % 4 == 0) for (ServerPlayerEntity p : fighters) {
                p.playSoundToPlayer(SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 0.6f, 0.6f + random.nextFloat() * 0.6f);
            }
            return;
        }
        if (t >= windup + rain + 10) endAttack();
    }

    private void rainArrow(ServerWorld world, Vec3d at, double safe, double groundY) {
        if (EnvyHazards.flatDistance(at, getPos()) < safe) return;
        // So many fall that they can't be left lying about
        ArrowEntity arrow = newArrow(world, new ItemStack(Items.ARROW), 4);
        arrow.setPosition(at.x, groundY + 22 + random.nextDouble() * 6, at.z);
        arrow.setVelocity(random.nextGaussian() * 0.03, -2.4, random.nextGaussian() * 0.03);
        arrow.setDamage(1.6 * DAMAGE_DEALT);
        world.spawnEntity(arrow);
    }

    private void tickQuiver(ServerWorld world) {
        int windup = 16, interval = desperate() ? 4 : 6, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            quiver = new EnvyHazards.QuiverRing(world, this);
            hazards.add(quiver);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_the_quiver", Items.ENCHANTED_BOOK), windup + 4));
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_END.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target != null) {
            faceTowards(target.getPos());
            if (quiver != null) quiver.aim = target.getEyePos();
        }
        if (t < windup) return;
        int n = (t - windup) / interval;
        if ((t - windup) % interval == 0 && quiver != null) {
            if (n % 2 == 1 || target == null) {
                ServerPlayerEntity next = pick(focus);
                if (next != null) {
                    focus = next.getUuid();
                    target = next;
                }
            }
            Vec3d from = quiver.take();
            if (from == null || target == null) {
                endAttack();
                return;
            }
            setPoseState(POSE_POINT);
            Vec3d to = target.getBoundingBox().getCenter().subtract(from);
            SpectralArrowEntity arrow = newSpectralArrow(world);
            arrow.setPosition(from.x, from.y, from.z);
            arrow.setNoGravity(true);
            arrow.setVelocity(to.x, to.y, to.z, 3.2f, 0f);
            arrow.setDamage(3.0 * DAMAGE_DEALT);
            world.spawnEntity(arrow);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.2f, 1.4f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 1.0f, 1.2f);
        }
        if (t > windup + interval * EnvyHazards.QuiverRing.COUNT + 10) endAttack();
    }

    private void tickDarts(ServerWorld world) {
        int windup = desperate() ? 6 : 9, t = actionTicks;
        if (t == 1) {
            if (repeats == 0) repeats = 2;
            ServerPlayerEntity target = keepFocus ? fighter(focus) : pick(focus);
            keepFocus = false;
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_IDLE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITCH_AMBIENT, SoundCategory.HOSTILE, 1.0f, 1.4f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target != null) faceTowards(target.getPos());
        if (t == windup) {
            setPoseState(POSE_THROW);
            swingHand(Hand.OFF_HAND);
            if (target != null) {
                for (int i = -1; i <= 1; i++) throwDart(world, target, i * 9f);
                if (desperate()) {
                    ServerPlayerEntity other = pick(focus);
                    if (other != null && other != target) throwDart(world, other, 0);
                }
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.5f, 1.2f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.0f, 1.8f);
        }
        if (t >= windup + 10) {
            setPoseState(POSE_IDLE);
            if (--repeats > 0) actionTicks = 0;
            else endAttack();
        }
    }

    /** A dart tipped with poison, thrown {@code spread} degrees off straight at {@code target}. */
    void throwDart(ServerWorld world, ServerPlayerEntity target, float spread) {
        throwDart(world, this, handPos(false), target, spread, 1.2, 100, 1);
    }

    static void throwDart(ServerWorld world, net.minecraft.entity.LivingEntity owner, Vec3d from, ServerPlayerEntity target, float spread,
                          double damage, int poisonTicks, int amplifier) {
        ItemStack stack = new ItemStack(Items.TIPPED_ARROW);
        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(0x3E8A2E),
                List.of(new StatusEffectInstance(StatusEffects.POISON, poisonTicks, amplifier))));
        ArrowEntity dart = new ArrowEntity(world, owner, stack, null) {
            @Override
            public void tick() {
                super.tick();
                if (inGround && inGroundTime > 20 || age > 200) discard();
            }
        };
        dart.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        Vec3d aim = target.getBoundingBox().getCenter().add(target.getVelocity().multiply(1, 0, 1).multiply(3));
        Vec3d dir = aim.subtract(from).rotateY((float) Math.toRadians(spread));
        dart.setPosition(from.x, from.y, from.z);
        dart.setVelocity(dir.x, dir.y + dir.horizontalLength() * 0.03, dir.z, 2.6f, 0.5f);
        dart.setDamage(damage * DAMAGE_DEALT);
        world.spawnEntity(dart);
    }

    private void tickSwords(ServerWorld world) {
        int windup = 24, interval = desperate() ? 6 : 8, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            fan = new EnvyHazards.SwordFan(world, this);
            hazards.add(fan);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_swords", Items.ENCHANTED_BOOK), windup));
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.HOSTILE, 1.5f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_VAULT_OPEN_SHUTTER, SoundCategory.HOSTILE, 1.2f, 0.8f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target != null) {
            faceTowards(target.getPos());
            if (fan != null) fan.aim = target.getBoundingBox().getCenter();
        }
        if (t < windup) return;
        if ((t - windup) % interval == 0 && fan != null) {
            ServerPlayerEntity next = pick(null);
            if (next != null) {
                focus = next.getUuid();
                target = next;
            }
            if (target == null || fan.remaining() == 0) {
                endAttack();
                return;
            }
            setPoseState(POSE_POINT);
            faceTowards(target.getPos());
            EnvyHazards.FlyingSword sword = fan.fire(world, target.getBoundingBox().getCenter(), 14);
            if (sword != null) hazards.add(sword);
        }
        if (fan == null || fan.remaining() == 0 && (t - windup) % interval == interval - 1) endAttack();
    }

    private void tickSwap(ServerWorld world) {
        int windup = desperate() ? 13 : 18, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = null;
            for (ServerPlayerEntity p : fighters) {
                if (!canSee(getEyePos(), p.getEyePos())) continue;
                if (target == null || p.squaredDistanceTo(this) > target.squaredDistanceTo(this)) target = p;
            }
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_POINT);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_swapping", Items.ENCHANTED_BOOK), windup));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 2.0f, 0.8f);
            target.playSoundToPlayer(SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target == null) {
            endAttack();
            return;
        }
        faceTowards(target.getPos());
        if (t < windup) {
            // A thread between you: break it with a pillar
            if (t % 2 == 0) {
                Vec3d a = getPos().add(0, 1.2, 0), b = target.getPos().add(0, 1.2, 0);
                for (double s = 0; s <= 1; s += 0.04) {
                    Vec3d q = a.lerp(b, s);
                    world.spawnParticles(s * 25 % 2 < 1 ? EnvyHazards.VIOLET : EnvyHazards.PSYCHIC, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
                }
                EnvyHazards.ring(world, EnvyHazards.VIOLET, target.getPos(), 1.0, 12, t * 0.2);
            }
            return;
        }
        if (t == windup) {
            if (!canSee(getEyePos(), target.getEyePos())) {
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 1.0f, 1.4f);
                endAttack();
                return;
            }
            Vec3d mine = getPos(), theirs = target.getPos();
            refreshPositionAndAngles(theirs.x, theirs.y, theirs.z, getYaw(), getPitch());
            getNavigation().stop();
            target.teleport(world, mine.x, mine.y, mine.z, target.getYaw(), target.getPitch());
            target.fallDistance = 0;
            for (Vec3d at : new Vec3d[]{mine, theirs}) {
                world.spawnParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 12, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticles(ParticleTypes.TRIAL_SPAWNER_DETECTION, at.x, at.y + 1, at.z, 6, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticles(EnvyHazards.VIOLET, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0);
                world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.HOSTILE, 1.5f, 1.0f);
                world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 1.2f);
            }
            // And straight into something nasty for whoever he's landed among
            boolean company = false;
            for (ServerPlayerEntity p : fighters) if (p != target && p.squaredDistanceTo(this) < 7 * 7) company = true;
            if (company) startAttack(Action.FORCE_PUSH);
            else {
                startAttack(Action.POISON_DARTS);
                focus = target.getUuid();
                keepFocus = true;
            }
        }
    }

    private void tickPsychicSlash(ServerWorld world) {
        int windup = desperate() ? 6 : 8, gap = desperate() ? 9 : 12, slashes = 3, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.5f, 1.5f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target == null) {
            endAttack();
            return;
        }
        faceTowards(target.getPos());
        int s = (t - 1) % gap, n = (t - 1) / gap;
        if (n >= slashes) {
            if (s >= 6) endAttack();
            return;
        }
        if (s == 0) setPoseState(n % 2 == 0 ? POSE_SLASH_B : POSE_SLASH_A);
        if (s < windup) {
            Vec3d tip = handPos(true).add(0, 0.6, 0);
            world.spawnParticles(EnvyHazards.PSYCHIC, tip.x, tip.y, tip.z, 2, 0.15, 0.15, 0.15, 0);
            return;
        }
        if (s == windup) {
            setPoseState(n % 2 == 0 ? POSE_SLASH_A : POSE_SLASH_B);
            swingHand(Hand.MAIN_HAND);
            Vec3d dir = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (dir.lengthSquared() < 1e-4) dir = Vec3d.fromPolar(0, getYaw());
            dir = dir.normalize();
            hazards.add(new EnvyHazards.Crescent(getPos().add(dir.multiply(1.0)), dir, desperate() ? 1.1 : 0.95, 24, 2.6, 8, false));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 1.2f, 0.7f);
            // Every other one goes to someone else
            if (n == 1) {
                ServerPlayerEntity other = pick(focus);
                if (other != null) focus = other.getUuid();
            }
        }
    }

    private void tickPoisonSlash(ServerWorld world) {
        int interval = 7, swings = 3, t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = fighter(focus);
            if (target == null || target.distanceTo(this) > 9) target = nearestFighter();
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
            if (target == null || t > 16 || squaredDistanceTo(target) < 2.6 * 2.6) {
                stageTicks = 0;
                setVelocity(0, getVelocity().y, 0);
            } else {
                faceTowards(target.getPos());
                Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1).normalize().multiply(0.75);
                setVelocity(to.x, getVelocity().y, to.z);
                world.spawnParticles(EnvyHazards.TOXIC, getX(), getY() + 0.3, getZ(), 2, 0.2, 0.1, 0.2, 0);
                return;
            }
        }
        int s = ++stageTicks;
        if (target != null) faceTowards(target.getPos());
        setVelocity(0, getVelocity().y, 0);
        if (s % interval == 1 && s / interval < swings) {
            int n = s / interval;
            setPoseState(n % 2 == 0 ? POSE_SLASH_A : POSE_SLASH_B);
            swingHand(Hand.MAIN_HAND);
            Vec3d f = Vec3d.fromPolar(0, getYaw());
            Vec3d at = getPos().add(f.multiply(1.6)).add(0, 1.1, 0);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.2, 0.1, 0.2, 0);
            world.spawnParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 8, 0.6, 0.3, 0.6, 0.05);
            world.spawnParticles(EnvyHazards.TOXIC, at.x, at.y, at.z, 10, 0.6, 0.3, 0.6, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.9f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PUFFER_FISH_STING, SoundCategory.HOSTILE, 1.2f, 0.8f);
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = p.getPos().subtract(getPos());
                Vec3d flat = to.multiply(1, 0, 1);
                if (flat.lengthSquared() > 3.4 * 3.4 || Math.abs(to.y) > 2) continue;
                if (flat.lengthSquared() > 0.25 && flat.normalize().dotProduct(f) < 0.2) continue;
                p.timeUntilRegen = 0;
                if (strike(p, 6)) poison(p, 70, n == swings - 1 ? 1 : 0);
                p.addVelocity(f.x * 0.3, 0.1, f.z * 0.3);
                p.velocityModified = true;
            }
        }
        if (s == swings * interval + 2) {
            // A cloud of poison where he stood, and a flip away out of it
            poisonCloud(world, getPos(), 3.0f, 120);
            Vec3d away = target != null ? getPos().subtract(target.getPos()).multiply(1, 0, 1) : Vec3d.fromPolar(0, getYaw() + 180);
            away = away.lengthSquared() > 1e-4 ? away.normalize() : new Vec3d(1, 0, 0);
            if (standingSpot(inside(getPos().add(away.multiply(6)), 44)) != null) {
                setVelocity(away.x * 1.1, 0.55, away.z * 1.1);
                velocityDirty = true;
            }
            setPoseState(POSE_IDLE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.HOSTILE, 1.5f, 1.1f);
        }
        if (s >= swings * interval + 14) endAttack();
    }

    void poisonCloud(ServerWorld world, Vec3d at, float radius, int duration) {
        AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(world, at.x, at.y, at.z);
        cloud.setOwner(this);
        cloud.setRadius(radius);
        cloud.setDuration(duration);
        cloud.setWaitTime(4);
        cloud.setRadiusGrowth(-radius / duration * 0.5f);
        cloud.setParticleType(EntityEffectParticleEffect.create(ParticleTypes.ENTITY_EFFECT, 0x4E9A3A));
        cloud.setPotionContents(new PotionContentsComponent(Optional.empty(), Optional.of(0x4E9A3A),
                List.of(new StatusEffectInstance(StatusEffects.POISON, 60, 0))));
        world.spawnEntity(cloud);
    }

    private void tickSwordRain(ServerWorld world) {
        int cast = 22, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_SKY);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_judgement", Items.ENCHANTED_BOOK), cast + 6));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        stand();
        setPitch(-50f);
        if (t == cast) {
            List<ServerPlayerEntity> targets = new ArrayList<>(fighters);
            Collections.shuffle(targets, new java.util.Random(random.nextLong()));
            // One on each of them (a dozen at most), quicker one after another the more there are
            int i = 0, stagger = targets.size() > 6 ? 5 : 8;
            for (ServerPlayerEntity p : targets) {
                if (i >= 12) break;
                Vec3d at = ground(p.getPos());
                hazards.add(new EnvyHazards.GiantSword(at, getPos(), i * stagger));
                i++;
            }
            if (desperate()) {
                for (int k = 0; k < 3 && !targets.isEmpty(); k++) {
                    ServerPlayerEntity p = targets.get(random.nextInt(targets.size()));
                    double a = random.nextDouble() * MathHelper.TAU;
                    Vec3d at = ground(inside(p.getPos().add(Math.cos(a) * 5, 0, Math.sin(a) * 5), 46));
                    hazards.add(new EnvyHazards.GiantSword(at, getPos(), 34 + k * 8));
                }
            }
            swingHand(Hand.MAIN_HAND);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (t >= cast + 14) endAttack();
    }

    private void tickIllusions(ServerWorld world) {
        int cast = 20, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_illusion", Items.ENCHANTED_BOOK), cast));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        stand();
        if (t < cast) {
            EnvyHazards.ring(world, EnvyHazards.VIOLET, getPos(), 1.2 + t * 0.1, 20, t * 0.3);
            return;
        }
        if (t == cast) {
            // Four of him in a ring; he's one of them
            List<Vec3d> spots = new ArrayList<>();
            double start = random.nextDouble() * MathHelper.TAU;
            for (int i = 0; i < 4; i++) {
                // A pillar (or anything) in the way: a little round or nearer or further instead
                for (int tries = 0; tries < 8; tries++) {
                    double a = start + i * MathHelper.TAU / 4 + (tries == 0 ? 0 : (random.nextDouble() - 0.5) * 1.2);
                    double d = tries == 0 ? 8 : 5 + random.nextDouble() * 6;
                    Vec3d s = standingSpot(inside(getPos().add(Math.cos(a) * d, 0, Math.sin(a) * d), 44));
                    if (s == null) continue;
                    spots.add(s);
                    break;
                }
            }
            puff(world, getPos());
            if (!spots.isEmpty()) {
                int real = random.nextInt(spots.size());
                for (int i = 0; i < spots.size(); i++) {
                    Vec3d s = spots.get(i);
                    puff(world, s);
                    if (i == real) continue;
                    GummssIllusionEntity image = ModBossEntities.GUMMSS_ILLUSION.create(world);
                    if (image == null) continue;
                    image.refreshPositionAndAngles(s.x, s.y, s.z, random.nextFloat() * 360, 0);
                    image.setOwner(this);
                    image.addCommandTag(ILLUSION_TAG);
                    world.spawnEntity(image);
                    illusions.add(image.getUuid());
                }
                Vec3d mine = spots.get(real);
                refreshPositionAndAngles(mine.x, mine.y, mine.z, random.nextFloat() * 360, 0);
                getNavigation().stop();
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 2.5f, 0.8f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (t >= cast + 6) endAttack();
    }

    private void tickEvilEye(ServerWorld world) {
        int stare = desperate() ? 46 : 54, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_STARE);
            dataTracker.set(STARING, true);
            for (ServerPlayerEntity p : audience(world)) {
                p.playSoundToPlayer(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.0f, 0.7f);
                p.sendMessage(Text.translatable("message.fmscraft.gummss_stare").formatted(Formatting.DARK_GREEN, Formatting.ITALIC), true);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        stand();
        if (t <= stare) {
            // He turns slowly, taking you all in; a great eye opens over him
            faceYaw(getYaw() + 360f / stare);
            drawEye(world, t / (double) stare);
            if (t % 12 == 0) for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.0f, 0.5f + t / (float) stare * 0.3f);
        }
        if (t == stare) {
            dataTracker.set(STARING, false);
            Vec3d eye = getEyePos();
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = eye.subtract(p.getEyePos());
                // Where they're looking (getRotationVector: their own yaw, not their head's)
                boolean looking = to.lengthSquared() > 1e-4 && p.getRotationVector().dotProduct(to.normalize()) > 0.8 && canSee(p.getEyePos(), eye);
                if (!looking) {
                    p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 0.6f, 1.2f);
                    continue;
                }
                p.timeUntilRegen = 0;
                crush(p, 10);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0), this);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 70, 0), this);
                flash(p, 0.7f, 20);
                p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.0f, 0.8f);
                world.spawnParticles(EnvyHazards.PSYCHIC, p.getX(), p.getY() + 1.6, p.getZ(), 20, 0.3, 0.3, 0.3, 0);
            }
        }
        if (t >= stare + 8) endAttack();
    }

    /** The Evil Eye's eye over his head, {@code open} 0-1: lids, a green iris, a black pupil, facing where he faces. */
    private void drawEye(ServerWorld world, double open) {
        Vec3d c = getPos().add(0, getHeight() + 1.6, 0);
        Vec3d right = Vec3d.fromPolar(0, getYaw() + 90);
        double a = 2.6, b = 1.3 * Math.min(1, open * 1.6);
        for (int i = 0; i < 36; i++) {
            double x = -a + i * 2 * a / 35, half = b * (1 - (x / a) * (x / a));
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3d p = c.add(right.multiply(x)).add(0, sgn * half, 0);
                emit(world, EnvyHazards.PSYCHIC, p, 1, 0, 0, 0, 0);
            }
        }
        if (b > 0.5) {
            for (int i = 0; i < 16; i++) {
                double ang = i * MathHelper.TAU / 16;
                Vec3d p = c.add(right.multiply(Math.cos(ang) * 0.8)).add(0, Math.sin(ang) * 0.8, 0);
                emit(world, EnvyHazards.TOXIC, p, 1, 0, 0, 0, 0);
            }
            emit(world, EnvyHazards.SHADOW, c, 4, 0.12, 0.12, 0.12, 0);
        }
    }

    private void tickFlasks(ServerWorld world) {
        int windup = 10, gap = 6, flasks = MathHelper.clamp(fighters.size() / 2, 3, 6), t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_IDLE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITCH_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        stand();
        if (t >= windup && (t - windup) % gap == 0 && (t - windup) / gap < flasks) {
            ServerPlayerEntity target = pick(null);
            if (target != null) {
                faceTowards(target.getPos());
                setPoseState(POSE_THROW);
                swingHand(Hand.OFF_HAND);
                throwFlask(world, target);
            }
        }
        if (t == windup + 2 || t == windup + gap + 2) setPoseState(POSE_IDLE);
        if (t >= windup + gap * flasks + 8) endAttack();
    }

    /** Tome of Levitation (rare): everyone floats up, helpless, for 5 seconds, and he looses the Quiver at them. */
    private void tickLevitation(ServerWorld world) {
        int windup = 24, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_levitation", Items.ENCHANTED_BOOK), windup + 4));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.7f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.5f, 1.4f);
        }
        stand();
        if (t < windup) {
            // Motes rising round everyone's feet
            for (ServerPlayerEntity p : fighters) {
                world.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.4, 0.05, 0.4, 0.02);
                if (t % 3 == 0) EnvyHazards.ring(world, EnvyHazards.PSYCHIC, p.getPos(), 0.9, 10, t * 0.3);
            }
            return;
        }
        if (t == windup) {
            setPoseState(POSE_SKY);
            for (ServerPlayerEntity p : fighters) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 1), this);
                world.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.5, p.getZ(), 16, 0.3, 0.3, 0.3, 0.08);
                p.playSoundToPlayer(SoundEvents.ENTITY_SHULKER_BULLET_HIT, SoundCategory.HOSTILE, 1.0f, 0.7f);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_SHULKER_SHOOT, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        // And while they hang there, the arrows
        if (t >= windup + 8) startAttack(Action.QUIVER);
    }

    /**
     * The full Tome of Judgement (rare): a colossal netherite sword forms high over where most of them stand, the gold
     * beam marking it for 2 seconds, then drops and buries itself, throwing everyone near into the air.
     */
    private void tickJudgement(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            spot = clusterSpot();
            if (spot == null) {
                endAttack();
                return;
            }
            setPoseState(POSE_SKY);
            EnvyHazards.GiantSword sword = new EnvyHazards.GiantSword(spot, getPos(), 0, EnvyHazards.GiantSword.JUDGEMENT);
            hazards.add(sword);
            stageTicks = sword.impactIn();
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_judgement", Items.ENCHANTED_BOOK), stageTicks));
            for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
        }
        stand();
        if (spot != null) faceTowards(spot);
        setPitch(-45f);
        if (t == stageTicks) {
            setPoseState(POSE_SLAM);
            swingHand(Hand.MAIN_HAND);
        }
        if (t >= stageTicks + 10) endAttack();
    }

    /** Tome of Upside Down (rare): everyone falls up into the dark for a second, then back down to the floor. */
    private void tickUpsideDown(ServerWorld world) {
        int windup = 20, flipped = 20, t = actionTicks;
        if (t == 1) {
            setPoseState(POSE_CAST);
            hazards.add(new EnvyHazards.Tome(world, this, MagicSpells.itemOr("tome_of_upside_down", Items.ENCHANTED_BOOK), windup + 4));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        stand();
        if (t < windup) {
            // The ground lets go of them: motes falling upward off everyone
            for (ServerPlayerEntity p : fighters) world.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 0.2, p.getZ(), 3, 0.4, 0.1, 0.4, 0.04);
            return;
        }
        if (t == windup) {
            setPoseState(POSE_SKY);
            List<UUID> ids = new ArrayList<>();
            for (ServerPlayerEntity p : fighters) ids.add(p.getUuid());
            hazards.add(new EnvyHazards.Flip(ids, flipped));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t >= windup + flipped + 10) endAttack();
    }

    /** A lingering poison flask, thrown to land where {@code target} is heading. Its cloud is short-lived. */
    private void throwFlask(ServerWorld world, ServerPlayerEntity target) {
        Vec3d from = handPos(false);
        Vec3d aim = target.getPos().add(target.getVelocity().multiply(1, 0, 1).multiply(10));
        Vec3d to = aim.subtract(from);
        double flat = to.horizontalLength(), g = 0.05;
        int ticks = (int) MathHelper.clamp(flat / 0.8, 10, 30);
        Vec3d v = new Vec3d(to.x / ticks * 1.1, to.y / ticks + 0.5 * g * ticks, to.z / ticks * 1.1);
        ItemStack stack = new ItemStack(Items.LINGERING_POTION);
        stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of(0x4E9A3A), List.of()));
        PotionEntity flask = new PotionEntity(world, this) {
            @Override
            protected void onCollision(HitResult hit) {
                if (getWorld() instanceof ServerWorld sw && !isRemoved()) {
                    Vec3d at = hit.getPos();
                    getWorld().syncWorldEvent(WorldEvents.SPLASH_POTION_SPLASHED, getBlockPos(), 0x4E9A3A);
                    poisonCloud(sw, new Vec3d(at.x, groundOr(at), at.z), 3.4f, 160);
                    discard();
                }
            }
        };
        flask.setItem(stack);
        flask.setPosition(from.x, from.y, from.z);
        flask.setVelocity(v);
        world.spawnEntity(flask);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.5f, 0.8f);
    }

    private double groundOr(Vec3d at) {
        double g = groundBelow(at.add(0, 0.5, 0), 6);
        return Double.isNaN(g) ? at.y : g;
    }

    // ---- The arena, the hazards and his aura ----

    private void tickHazards(ServerWorld world) {
        for (EnvyHazards.Hazard h : new ArrayList<>(hazards)) {
            if (!h.tick(world, this)) {
                h.remove();
                hazards.remove(h);
            }
        }
    }

    /** Dark green, always round him, curling up off him; thicker as he falls. Unseen, there's nothing. */
    private void tickAura(ServerWorld world) {
        if (isInvisible()) return;
        double w = getWidth() * 0.6, h = getHeight();
        world.spawnParticles(EnvyHazards.DEEP_GREEN, getX(), getY() + h * 0.5, getZ(), 2, w, h * 0.35, w, 0);
        for (int i = 0; i < 2; i++) {
            double a = age * 0.25 + i * Math.PI, y = ((age + i * 12) % 30) / 30.0 * (h + 0.4);
            world.spawnParticles(EnvyHazards.DEEP_GREEN, getX() + Math.cos(a) * 0.75, getY() + y, getZ() + Math.sin(a) * 0.75, 1, 0, 0, 0, 0);
        }
        if (age % 4 == 0) world.spawnParticles(ParticleTypes.WARPED_SPORE, getX(), getY() + h * 0.6, getZ(), 1, w, h * 0.3, w, 0);
        if (age % 6 == 0) {
            Vec3d tip = handPos(true).add(0, 0.7, 0);
            world.spawnParticles(EnvyHazards.PSYCHIC, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    /** The fog-filled void: spores and green motes drifting round everyone. */
    private void tickAmbience(ServerWorld world) {
        if (age % 6 != 0) return;
        for (ServerPlayerEntity p : fighters) {
            world.spawnParticles(ParticleTypes.WARPED_SPORE, p.getX(), p.getY() + 2, p.getZ(), 5, 10, 3, 10, 0);
            world.spawnParticles(EnvyHazards.DEEP_GREEN, p.getX(), p.getY() + 2, p.getZ(), 2, 10, 3, 10, 0);
            if (random.nextInt(3) == 0) world.spawnParticles(ParticleTypes.MYCELIUM, p.getX(), p.getY() + 1, p.getZ(), 4, 8, 2, 8, 0);
        }
    }

    /** Over the rim or falling into the void: his mind catches you and drags you back. It isn't gentle. */
    private void catchStrays(ServerWorld world) {
        for (ServerPlayerEntity p : fighters) {
            Vec3d pos = p.getPos();
            if (EnvyArena.onFloor(pos)) {
                straying.remove(p.getUuid());
                continue;
            }
            boolean falling = pos.y < EnvyArena.FLOOR_Y - 1;
            int out = straying.merge(p.getUuid(), 1, Integer::sum);
            if (!falling && out < 12) continue;
            straying.remove(p.getUuid());
            Vec3d from = pos.subtract(EnvyArena.CENTER).multiply(1, 0, 1);
            from = from.lengthSquared() > 1e-4 ? from.normalize() : new Vec3d(0, 0, 1);
            Vec3d back = EnvyArena.CENTER.add(from.multiply(EnvyArena.RADIUS - 6));
            world.spawnParticles(EnvyHazards.PSYCHIC, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.8, 0.3, 0);
            p.teleport(world, back.x, back.y, back.z, p.getYaw(), p.getPitch());
            p.fallDistance = 0;
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
            crush(p, 4);
            world.spawnParticles(EnvyHazards.PSYCHIC, back.x, back.y + 1, back.z, 30, 0.3, 0.8, 0.3, 0);
            world.playSound(null, back.x, back.y, back.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.5f, 0.6f);
            p.sendMessage(Text.translatable("message.fmscraft.gummss_caught").formatted(Formatting.DARK_GREEN), true);
        }
    }

    // ---- Dying ----

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld world) {
            clearAttacks(world);
            dataTracker.set(STARING, false);
            setInvisible(false);
            for (ServerPlayerEntity p : audience(world)) flash(p, 0.8f, 30);
            Vec3d c = getPos().add(0, 1, 0);
            emit(world, ParticleTypes.FLASH, c, 1, 0, 0, 0, 0);
            emit(world, ParticleTypes.SONIC_BOOM, c, 1, 0, 0, 0, 0);
            emit(world, EnvyHazards.DEEP_GREEN, c, 250, 1.2, 1.4, 1.2, 0);
            emit(world, EnvyHazards.PSYCHIC, c, 120, 1.5, 1.5, 1.5, 0);
            emit(world, ParticleTypes.ENCHANT, c, 200, 2, 2, 2, 2);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ILLUSIONER_DEATH, SoundCategory.HOSTILE, 4.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ELDER_GUARDIAN_DEATH, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.5f);
        }
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Taken away mid-fight (the attempt failed): nothing of his stays behind, and nobody is left hanging
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
        for (EnvyHazards.Hazard h : hazards) h.remove();
        hazards.clear();
        lift = null;
        for (UUID id : illusions) {
            Entity image = world.getEntity(id);
            if (image instanceof GummssIllusionEntity i) i.shatter(false);
        }
        illusions.clear();
        for (Entity e : world.getOtherEntities(this, getBoundingBox().expand(120),
                e -> e instanceof ProjectileEntity p && p.getOwner() == this || e instanceof AreaEffectCloudEntity c && c.getOwner() == this)) {
            e.discard();
        }
    }

    // ---- Helpers ----

    /** Roughly where his hand is: right (the staff) or left (his casting hand). */
    Vec3d handPos(boolean right) {
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(right ? -0.4 : 0.4);
        return getPos().add(0, getHeight() * 0.72, 0).add(forward.multiply(0.6)).add(side);
    }

    private void stand() {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
    }

    private void faceTowards(Vec3d pos) {
        faceYaw((float) (MathHelper.atan2(pos.z - getZ(), pos.x - getX()) * (180 / Math.PI)) - 90f);
    }

    private void faceYaw(float yaw) {
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    private boolean canSee(Vec3d from, Vec3d to) {
        return getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** The floor under {@code pos} (or {@code pos} itself if there's none close). */
    private Vec3d ground(Vec3d pos) {
        double g = groundBelow(pos.add(0, 1, 0), 10);
        return Double.isNaN(g) ? pos : new Vec3d(pos.x, g, pos.z);
    }

    /** Where most of them stand: the one with the most others close by. */
    private Vec3d clusterSpot() {
        return clusterSpot(null, 0);
    }

    /** The same, among those at least {@code apart} from {@code away} (null: everyone), or null if there are none. */
    private Vec3d clusterSpot(Vec3d away, double apart) {
        ServerPlayerEntity best = null;
        int bestCount = -1;
        for (ServerPlayerEntity p : fighters) {
            if (away != null && EnvyHazards.flatDistance(p.getPos(), away) < apart) continue;
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
            if (away != null && EnvyHazards.flatDistance(o.getPos(), away) < apart) continue;
            if (o.squaredDistanceTo(best) < 8 * 8) {
                mid = mid.add(o.getPos());
                n++;
            }
        }
        return ground(inside(mid.multiply(1.0 / n), 44));
    }

    /** Somewhere {@code min}-{@code max} blocks from him, as far from all of them as he can find. */
    private Vec3d farSpot(double min, double max) {
        Vec3d best = null;
        double bestScore = -1;
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * MathHelper.TAU, d = min + random.nextDouble() * (max - min);
            Vec3d at = standingSpot(inside(getPos().add(Math.cos(a) * d, 0, Math.sin(a) * d), 44));
            if (at == null) continue;
            double score = Double.MAX_VALUE;
            for (ServerPlayerEntity p : fighters) score = Math.min(score, p.getPos().distanceTo(at));
            if (score > bestScore) {
                bestScore = score;
                best = at;
            }
        }
        return best;
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

    /** Somewhere to stand near {@code pos} (solid below, room above), or null. */
    private Vec3d standingSpot(Vec3d pos) {
        double ground = groundBelow(pos.add(0, 3, 0), 8);
        if (Double.isNaN(ground)) return null;
        Vec3d at = new Vec3d(pos.x, ground, pos.z);
        return getWorld().isSpaceEmpty(this, getDimensions(getPose()).getBoxAt(at)) ? at : null;
    }

    private BlockState groundState() {
        BlockState state = getWorld().getBlockState(getBlockPos().down());
        return state.isAir() ? Blocks.POLISHED_DEEPSLATE.getDefaultState() : state;
    }
}
