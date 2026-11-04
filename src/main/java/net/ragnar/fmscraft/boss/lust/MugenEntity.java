package net.ragnar.fmscraft.boss.lust;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.ModBossEntities;
import net.ragnar.fmscraft.boss.SinBossEntity;
import net.ragnar.fmscraft.compat.MagicSpells;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.network.BossScreenPayload;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.network.LustSkyPayload;
import net.ragnar.fmscraft.network.MugenControlPayload;
import net.ragnar.fmscraft.sin.Sin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MUGEN, the Sin of Lust: Karl, all in black, a blade in each hand. A ninja: the fastest of them and not the toughest,
 * never where you swung, always somewhere behind you. He runs whoever he's picked down and cuts, slips out of hits
 * (<b>Phase</b>: a few blocks aside in a blur of afterimages; or the <b>Tome of Logs</b>: a log takes your blow and he's
 * behind you), and on the beat picks one of:
 * <ul>
 *   <li><b>Poof</b>: gone in a puff of smoke, out of another beside you.</li>
 *   <li><b>Kunai</b>: eight thrown one after another, very fast, not too carefully aimed. Four hearts each.</li>
 *   <li><b>Shuriken</b>: a fan of them, whirling.</li>
 *   <li><b>Spin Kick</b>: crouches, then a spinning kick that throws you a long way.</li>
 *   <li><b>Get Over Here!</b>: a chain with a kunai on the end; it hooks you, drags you in, and he stabs you.</li>
 *   <li><b>Blade Spin</b>: both blades held out, spinning like a top, coming at you.</li>
 *   <li><b>Sneak Attack</b>: gone in smoke, a mark at your feet, and he drops on you from above, blade down.</li>
 *   <li><b>Shadow Clones</b>: three more of him (they break at a touch; only he casts a shadow).</li>
 *   <li><b>Black Orb</b>: slow, but it bursts for 16 and puts out your sight.</li>
 *   <li><b>Sword Rain</b>: netherite swords out of the night sky, one on each of you.</li>
 *   <li><b>Blade Throw</b>: one of his blades thrown, tumbling; it vanishes in smoke and is back in his hand.</li>
 *   <li><b>Double Jump</b>, the <b>Tome of Swapping</b>, <b>Wind Charges</b> and a <b>Smoke Bomb</b> you can't see in.</li>
 * </ul>
 * At half health the world goes black and Toxic starts; the black lifts on a red eclipse with him hanging in the sky in
 * a black aura, and he lands on the drop. Then it's all faster and harder, and he adds the <b>Tome of Unseen Hands</b>,
 * the <b>Grapple Slam</b>, the <b>Slide Tackle</b>, <b>Black Blaze</b>, <b>Shadow Step</b>, the <b>Blackout</b> (the
 * world goes black round everyone, four blocks of sight, while he hunts) and, rarely, his <b>Taunt</b>.
 */
public class MugenEntity extends SinBossEntity implements MugenLook {
    public static final BossSong PHASE_ONE = new BossSong("fmscraft:music.lust.phase_one", MugenTimeline.COMENGO_BPM, MugenTimeline.COMENGO_FIRST_BEAT);
    public static final BossSong PHASE_TWO = new BossSong("fmscraft:music.lust.phase_two", MugenTimeline.TOXIC_BPM, MugenTimeline.TOXIC_DROP);

    // ---- Tuning knobs ----
    /** Full health, for 5-7 players; {@link MugenTimeline#healthScale} scales it. Over the 1024 cap he takes less damage instead. */
    public static final float MAX_HEALTH = 900;
    /** Damage he takes is multiplied by this (on top of his armor), and capped per hit. */
    public static final float DAMAGE_TAKEN = 0.85f, PHASE_TWO_TAKEN = 0.95f, MAX_HIT = 28f;
    /** Every hit he deals is multiplied by this in phase 2, then rounded up. */
    public static final float PHASE_TWO_DEALT = 1.15f;
    private static final double SPEED = 0.36;
    private static final int REST = 16, REST_TWO = 9;
    /** A hit on him while he's free: this chance he Phases out of it, or swaps out with a log (with a cooldown). */
    public static final float DODGE = 0.2f, DODGE_TWO = 0.3f, LOG_CHANCE = 0.12f;
    private static final int LOG_COOLDOWN = 300, LOG_COOLDOWN_TWO = 200, DODGE_COOLDOWN = 40;
    private static final float HEALTH_CAP = 1024f;
    /** Purple glow on his phase 1 netherite swords from the sky, red in phase 2. */
    private static final int GLOW_ONE = 0x7A2BB5, GLOW_TWO = 0xB0102A;

    private static final TrackedData<Integer> POSE = DataTracker.registerData(MugenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EYES = DataTracker.registerData(MugenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> AFTERIMAGES = DataTracker.registerData(MugenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    enum Action {
        ENTRANCE, MOVE, TRANSITION, LOG_STRIKE,
        PHASE, POOF, KUNAI, SHURIKEN, SPIN_KICK, CHAIN, BLADE_SPIN, SNEAK, CLONES, ORB, SWORD_RAIN, BLADE_THROW,
        DOUBLE_JUMP, SWAPPING, WIND, SMOKE_BOMB,
        VOID_SPIKES, IAIDO, SHADOW_BIND,
        UNSEEN_HANDS, GRAPPLE_SLAM, SLIDE, BLAZE, TAUNT, BLACKOUT, SHADOW_STEP, SHADOW_SNEAK, CONTROLLER, BLACKHOLE
    }

    private Action action = Action.ENTRANCE;
    private int actionTicks, rest, meleeCooldown, logCooldown, dodgeCooldown, stuckTicks, blackoutTicks, phaseTwoAge;
    private Action lastAttack;
    private final Map<Action, Integer> lastUsed = new EnumMap<>(Action.class);
    private final Map<UUID, Integer> lastPicked = new HashMap<>();
    private final Map<UUID, Integer> straying = new HashMap<>();
    private final Set<UUID> struck = new HashSet<>();
    private final List<MugenHazards.Hazard> hazards = new ArrayList<>();
    private final List<MugenCloneEntity> clones = new ArrayList<>();
    private List<ServerPlayerEntity> fighters = List.of();
    private UUID focus;
    private int focusTicks;
    private boolean untouchable = true, scaled, heldStill, transitioned, killing, fromArena, toxic, bladeOut;
    /** For tests: the next hit he takes, he slips this way (a log, or a Phase) whatever else is going on. */
    private String evadeNext;
    private float toughness = 1f;
    private int startTick = -1, bossLandTick;
    private Vec3d home = LustArena.CENTER;

    // The move in progress
    private Vec3d spot, dir, from;
    private List<ServerPlayerEntity> targets = List.of();
    private MugenHazards.Chain chain;
    private MugenHazards.Smoke smoke;
    private MugenHazards.Shade shade;
    private net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity holeCore;
    /** Who he has on his strings (the Controller), and for how much longer. */
    private final Map<UUID, Integer> controlled = new HashMap<>();
    /** Who he's already cut down in this Iaido stance. */
    private final Set<UUID> countered = new HashSet<>();
    private boolean iaidoOpen, sheathed;
    /** For tests: he hits as hard as in phase 2. */
    private boolean hitsLikePhaseTwo;
    private int step, mark;

    /** Where he was the last few ticks, on the client: his afterimages are drawn there. */
    public static final int TRAIL = 12;
    private final double[][] trail = new double[TRAIL][4];
    private int trailHead, trailCount;

    public MugenEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 1100;
        bossBar().setColor(BossBar.Color.PURPLE);
        setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_CAP)
                .add(EntityAttributes.GENERIC_ARMOR, 6)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 2)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1)
                .add(EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, 64);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_IDLE);
        builder.add(EYES, EYES_NONE);
        builder.add(AFTERIMAGES, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    protected Text bossBarName() {
        return Text.literal("MUGEN");
    }

    @Override
    protected BossSong songForPhase(int phase) {
        return phase == 1 ? PHASE_ONE : PHASE_TWO;
    }

    /** Toxic starts under the black, once the eerie sounds have had their time ({@link #tickTransition}). */
    @Override
    protected boolean startsPhaseTwoSongAtOnce() {
        return false;
    }

    // ---- What the client and the tests see ----

    @Override
    public int poseState() {
        return dataTracker.get(POSE);
    }

    @Override
    public int eyes() {
        return dataTracker.get(EYES);
    }

    @Override
    public boolean afterimages() {
        return dataTracker.get(AFTERIMAGES);
    }

    @Override
    public boolean isReal() {
        return true;
    }

    public boolean isUntouchable() {
        return untouchable || isInvisible();
    }

    public String currentAction() {
        return action.name();
    }

    /** True once he's landed in the eclipse and phase 2 is under way. */
    public boolean hasTransitioned() {
        return transitioned;
    }

    public float effectiveMaxHealth() {
        return getMaxHealth() / toughness;
    }

    /** How many of his shadow clones are out (not counting the Taunt's echoes). */
    public int cloneCount() {
        clones.removeIf(c -> c.isRemoved() || !c.isAlive());
        int n = 0;
        for (MugenCloneEntity c : clones) if (!c.isEcho()) n++;
        return n;
    }

    /** For tests: who his Tome of Unseen Hands is holding right now. */
    public List<Entity> heldByHands() {
        List<Entity> out = new ArrayList<>();
        for (MugenHazards.Hazard h : hazards) if (h instanceof MugenHazards.Pool p) out.addAll(p.held());
        return out;
    }

    /** For tests: whether a log is lying where he was (the Tome of Logs went off). */
    public boolean leftALog() {
        for (MugenHazards.Hazard h : hazards) if (h instanceof MugenHazards.Log) return true;
        return false;
    }

    /** For tests: whether his chain has someone hooked. */
    public boolean chainHooked() {
        return chain != null && chain.hooked != null;
    }

    /** For tests: whether a blade is out of his hand (thrown). */
    public boolean bladeThrown() {
        return bladeOut;
    }

    /** For tests: how long the Blackout has left. */
    public int blackoutLeft() {
        return blackoutTicks;
    }

    /** For tests: between attacks he stands still, never slips a hit, and starts none of his own, only those he's told to. */
    public void holdStill() {
        heldStill = true;
    }

    /** For tests: the next hit on him is swapped for a log ({@code log}) or Phased out of. */
    public void evadeNext(boolean log) {
        evadeNext = log ? "LOG" : "PHASE";
    }

    /** For tests: starts one of his attacks by name. */
    public void forceAttack(String name) {
        startAttack(Action.valueOf(name));
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
    }

    private void setAfterimages(boolean on) {
        dataTracker.set(AFTERIMAGES, on);
    }

    boolean inPhaseTwo() {
        return transitioned;
    }

    /** One of his hands, held a little out in front. */
    public Vec3d handPos(boolean right) {
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(right ? -0.38 : 0.38);
        return getPos().add(0, getHeight() * 0.72, 0).add(forward.multiply(0.55)).add(side);
    }

    // ---- What the hazards and clones use ----

    List<ServerPlayerEntity> fighters() {
        return fighters;
    }

    ServerPlayerEntity fighter(UUID id) {
        if (id == null) return null;
        for (ServerPlayerEntity p : fighters) if (p.getUuid().equals(id)) return p;
        return null;
    }

    void addHazard(MugenHazards.Hazard hazard) {
        hazards.add(hazard);
    }

    /** A hit he deals, after phase 2's {@link #PHASE_TWO_DEALT}, rounded up to a whole number. */
    float dealt(float damage) {
        return (float) Math.ceil(damage * (transitioned || hitsLikePhaseTwo ? PHASE_TWO_DEALT : 1f));
    }

    /** His blades: a shield held towards him stops them. */
    boolean cut(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobAttack(this), dealt(damage));
    }

    /** Things he throws: a shield held towards him stops them. */
    boolean strike(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobProjectile(this, this), dealt(damage));
    }

    /** The shadow itself, and everything that crashes down: no shield helps. */
    void crush(ServerPlayerEntity player, float damage) {
        player.damage(getDamageSources().indirectMagic(this, this), dealt(damage));
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
        return world.getPlayers(p -> p.squaredDistanceTo(this) < 200 * 200);
    }

    void emit(ServerWorld world, ParticleEffect particle, Vec3d at, int count, double dx, double dy, double dz, double speed) {
        for (ServerPlayerEntity p : audience(world)) world.spawnParticles(p, particle, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
    }

    /** The sky for everyone near: {@code phase} 1 or 2, and the Blackout ({@code blackout} ticks, 0 to lift it, -1 leave it). */
    void sky(ServerWorld world, int phase, int blackout) {
        for (ServerPlayerEntity p : audience(world)) sky(p, phase, blackout);
    }

    static void sky(ServerPlayerEntity player, int phase, int blackout) {
        if (ServerPlayNetworking.canSend(player, LustSkyPayload.ID)) ServerPlayNetworking.send(player, new LustSkyPayload(phase, blackout));
    }

    /** The thrown blade is back in his hand. */
    void bladeReturns() {
        if (!bladeOut) return;
        bladeOut = false;
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.NETHERITE_SWORD));
        if (getWorld() instanceof ServerWorld world) {
            Vec3d h = handPos(true);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, h.x, h.y, h.z, 8, 0.15, 0.15, 0.15, 0.02);
            world.spawnParticles(MugenHazards.SHADOW, h.x, h.y, h.z, 6, 0.15, 0.15, 0.15, 0);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 1.2f, 1.2f);
        }
    }

    private void drawBlades() {
        sheathed = false;
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.NETHERITE_SWORD));
    }

    boolean blocked(Vec3d from, Vec3d to) {
        return getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this))
                .getType() != HitResult.Type.MISS;
    }

    Vec3d ground(Vec3d pos) {
        double g = groundBelow(pos.add(0, 2.5, 0), 14);
        return Double.isNaN(g) ? pos : new Vec3d(pos.x, g, pos.z);
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
        if (attacker == this || attacker instanceof MugenCloneEntity) return false;
        if (action == Action.IAIDO && iaidoOpen && attacker instanceof ServerPlayerEntity player && getWorld() instanceof ServerWorld world) {
            counter(world, player);
            return false;
        }
        if (isUntouchable()) {
            if (attacker instanceof PlayerEntity) clink();
            return false;
        }
        if (getWorld() instanceof ServerWorld world && attacker instanceof ServerPlayerEntity player && evadeNext != null) {
            boolean log = evadeNext.equals("LOG");
            evadeNext = null;
            if (log) substitute(world, player);
            else dodge(world, player);
            return false;
        }
        if (getWorld() instanceof ServerWorld world && attacker instanceof ServerPlayerEntity player && evasive()) {
            // A ninja is never quite where you swung
            if (logCooldown <= 0 && random.nextFloat() < LOG_CHANCE) {
                substitute(world, player);
                return false;
            }
            if (dodgeCooldown <= 0 && random.nextFloat() < (transitioned ? DODGE_TWO : DODGE)) {
                dodge(world, player);
                return false;
            }
        }
        amount *= (transitioned ? PHASE_TWO_TAKEN : DAMAGE_TAKEN) * toughness;
        amount = Math.min(amount, MAX_HIT);
        // Phase 1 can't be skipped: his health stops at half (setHealth), and at half nothing more gets through
        if (getPhase() == 1 && getHealth() <= getMaxHealth() * phaseTwoHealth()) amount = 0;
        if (amount <= 0) {
            clink();
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt && getWorld() instanceof ServerWorld world) {
            world.spawnParticles(MugenHazards.SHADOW, getX(), getBodyY(0.6), getZ(), 8, 0.3, 0.5, 0.3, 0);
            world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getBodyY(0.6), getZ(), 3, 0.2, 0.3, 0.2, 0.05);
        }
        return hurt;
    }

    private void clink() {
        getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0f, 1.5f);
    }

    /** Free to slip a hit: between moves, not held for a test. */
    private boolean evasive() {
        return action == Action.MOVE && !heldStill;
    }

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
        return false;
    }

    // ---- Ticking ----

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world && age % 5 == 0) shrugOffSpells(world);
        if (getWorld().isClient) {
            double[] at = trail[trailHead];
            at[0] = getX();
            at[1] = getY();
            at[2] = getZ();
            at[3] = bodyYaw;
            trailHead = (trailHead + 1) % TRAIL;
            trailCount = Math.min(TRAIL, trailCount + 1);
        }
    }

    /** Where he was {@code back} ticks ago (1 is last tick), on the client, or null if that's too far back. */
    public Vec3d trailPos(int back) {
        if (back < 1 || back > trailCount) return null;
        double[] at = trail[Math.floorMod(trailHead - back, TRAIL)];
        return new Vec3d(at[0], at[1], at[2]);
    }

    public float trailYaw(int back) {
        if (back < 1 || back > trailCount) return bodyYaw;
        return (float) trail[Math.floorMod(trailHead - back, TRAIL)][3];
    }

    /** Ragnars Magic Mod can't put him to sleep or carry him off in a bubble. */
    private void shrugOffSpells(ServerWorld world) {
        if (MagicSpells.isSleepy(this)) MagicSpells.wake(this);
        for (Entity bubble : world.getOtherEntities(this, getBoundingBox().expand(3), e -> MagicSpells.is(e, MagicSpells.BUBBLE))) {
            MugenHazards.poof(world, bubble.getBoundingBox().getCenter(), false);
            bubble.discard();
        }
    }

    @Override
    protected void tickPhase(int phase, int songTicks) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        // Anyone in his compound; anywhere else (summoned, tests) only those close by
        double reach = Arenas.sinOf(world) == Sin.LUST ? 160 : 24;
        fighters = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < reach * reach);
        if (!scaled) scaleHealth(world);
        if (rest > 0) rest--;
        if (meleeCooldown > 0) meleeCooldown--;
        if (logCooldown > 0) logCooldown--;
        if (dodgeCooldown > 0) dodgeCooldown--;
        if (blackoutTicks > 0) blackoutTicks--;
        if (transitioned) phaseTwoAge++;
        actionTicks++;
        switch (action) {
            case ENTRANCE -> tickEntrance(world, songTicks);
            case MOVE -> tickMove(world);
            case TRANSITION -> tickTransition(world, songTicks);
            case LOG_STRIKE -> tickLogStrike(world);
            case PHASE -> tickPhaseStep(world);
            case POOF -> tickPoof(world);
            case KUNAI -> tickKunai(world);
            case SHURIKEN -> tickShuriken(world);
            case SPIN_KICK -> tickSpinKick(world);
            case CHAIN -> tickChain(world);
            case BLADE_SPIN -> tickBladeSpin(world);
            case SNEAK -> tickSneak(world);
            case CLONES -> tickClones(world);
            case ORB -> tickOrb(world);
            case SWORD_RAIN -> tickSwordRain(world);
            case BLADE_THROW -> tickBladeThrow(world);
            case DOUBLE_JUMP -> tickDoubleJump(world);
            case SWAPPING -> tickSwapping(world);
            case WIND -> tickWind(world);
            case SMOKE_BOMB -> tickSmokeBomb(world);
            case UNSEEN_HANDS -> tickUnseenHands(world);
            case GRAPPLE_SLAM -> tickGrappleSlam(world);
            case SLIDE -> tickSlide(world);
            case BLAZE -> tickBlaze(world);
            case TAUNT -> tickTaunt(world);
            case BLACKOUT -> tickBlackout(world);
            case SHADOW_STEP -> tickShadowStep(world);
            case VOID_SPIKES -> tickVoidSpikes(world);
            case IAIDO -> tickIaido(world);
            case SHADOW_BIND -> tickShadowBind(world);
            case SHADOW_SNEAK -> tickShadowSneak(world);
            case CONTROLLER -> tickController(world);
            case BLACKHOLE -> tickBlackHole(world);
        }
        tickControl(world);
        tickHazards(world);
        if (!isInvisible()) tickAura(world);
        if (Arenas.sinOf(world) == Sin.LUST) {
            tickAmbience(world);
            if (action != Action.ENTRANCE) catchStrays(world);
        }
    }

    private void scaleHealth(ServerWorld world) {
        scaled = true;
        int players = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 200 * 200).size();
        float wanted = MAX_HEALTH * MugenTimeline.healthScale(players);
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
        stopSong(0);
    }

    // ---- Choosing attacks ----

    private Action chooseAttack() {
        List<Action> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        ServerPlayerEntity target = fighter(focus);
        double near = nearestDistance();
        int band = near < 5 ? 0 : near > 14 ? 2 : 1;
        int crowd = 0;
        for (ServerPlayerEntity p : fighters) if (p.distanceTo(this) < 7) crowd++;
        // Someone up on a roof: he goes up after them
        if (target != null && target.getY() > getY() + 3 && flat(target.getPos()) < 14 && notUsedFor(Action.DOUBLE_JUMP, 60)) return Action.DOUBLE_JUMP;
        int g = transitioned ? 3 : 4;
        offer(options, weights, Action.SPIN_KICK, new int[]{5, 1, 0}[band], 30 * g);
        offer(options, weights, Action.BLADE_SPIN, new int[]{4, 2, 0}[band] + (crowd >= 3 ? 2 : 0), 50 * g);
        offer(options, weights, Action.PHASE, new int[]{3, 2, 1}[band], 15 * g);
        offer(options, weights, Action.POOF, new int[]{0, 2, 4}[band], 20 * g);
        offer(options, weights, Action.KUNAI, new int[]{1, 4, 4}[band], 30 * g);
        offer(options, weights, Action.SHURIKEN, new int[]{1, 3, 3}[band], 35 * g);
        offer(options, weights, Action.CHAIN, new int[]{0, 3, 5}[band], 55 * g);
        offer(options, weights, Action.SNEAK, new int[]{1, 3, 3}[band], 45 * g);
        offer(options, weights, Action.CLONES, new int[]{1, 2, 2}[band], 150 * g);
        offer(options, weights, Action.ORB, new int[]{0, 2, 3}[band], 50 * g);
        offer(options, weights, Action.SWORD_RAIN, new int[]{1, 2, 3}[band], 90 * g);
        offer(options, weights, Action.BLADE_THROW, new int[]{1, 3, 3}[band], 40 * g);
        offer(options, weights, Action.DOUBLE_JUMP, new int[]{1, 1, 1}[band], 50 * g);
        offer(options, weights, Action.SWAPPING, new int[]{0, 1, 4}[band], 75 * g);
        offer(options, weights, Action.WIND, new int[]{2, 2, 1}[band], 50 * g);
        offer(options, weights, Action.SMOKE_BOMB, new int[]{3, 1, 0}[band] + (crowd >= 3 ? 1 : 0), 90 * g);
        offer(options, weights, Action.VOID_SPIKES, new int[]{3, 2, 1}[band] + (crowd >= 3 ? 1 : 0), 45 * g);
        offer(options, weights, Action.IAIDO, new int[]{3, 2, 0}[band], 80 * g);
        offer(options, weights, Action.SHADOW_BIND, new int[]{1, 3, 3}[band], 60 * g);
        if (transitioned) {
            offer(options, weights, Action.UNSEEN_HANDS, new int[]{2, 3, 3}[band] + (crowd >= 3 ? 2 : 0), 420);
            offer(options, weights, Action.GRAPPLE_SLAM, new int[]{1, 3, 3}[band], 300);
            offer(options, weights, Action.SLIDE, new int[]{2, 3, 1}[band], 140);
            offer(options, weights, Action.BLAZE, new int[]{0, 3, 4}[band], 180);
            offer(options, weights, Action.SHADOW_STEP, new int[]{2, 3, 2}[band], 240);
            if (phaseTwoAge > 400) offer(options, weights, Action.BLACKOUT, 2, 800);
            if (phaseTwoAge > 600) offer(options, weights, Action.TAUNT, 1, 1000);
            offer(options, weights, Action.SHADOW_SNEAK, new int[]{1, 3, 3}[band], 200);
            if (phaseTwoAge > 200) offer(options, weights, Action.BLACKHOLE, new int[]{1, 2, 2}[band], 700);
            if (phaseTwoAge > 300) offer(options, weights, Action.CONTROLLER, 1, 1000);
        }
        if (options.isEmpty()) return near < 5 ? Action.SPIN_KICK : Action.KUNAI;
        int total = weights.stream().mapToInt(Integer::intValue).sum(), roll = random.nextInt(total);
        for (int i = 0; i < options.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return options.get(i);
        }
        return options.get(0);
    }

    private boolean notUsedFor(Action attack, int ticks) {
        Integer used = lastUsed.get(attack);
        return used == null || age - used >= ticks;
    }

    private void offer(List<Action> options, List<Integer> weights, Action attack, int weight, int minGap) {
        if (weight <= 0 || attack == lastAttack) return;
        if (!notUsedFor(attack, minGap)) return;
        options.add(attack);
        weights.add(weight);
    }

    void startAttack(Action attack) {
        action = attack;
        actionTicks = 0;
        step = 0;
        struck.clear();
        if (attack != Action.TRANSITION && attack != Action.LOG_STRIKE) {
            lastAttack = attack;
            lastUsed.put(attack, age);
        }
        getNavigation().stop();
    }

    private void endAttack() {
        endAttack(transitioned ? REST_TWO : REST);
    }

    private void endAttack(int restTicks) {
        if (action == Action.BLACKHOLE) untouchable = false;
        if (holeCore != null) {
            holeCore.discard();
            holeCore = null;
        }
        if (sheathed && !bladeOut) drawBlades();
        iaidoOpen = false;
        action = Action.MOVE;
        actionTicks = 0;
        setPoseState(POSE_IDLE);
        setNoGravity(false);
        noClip = false;
        setAfterimages(false);
        if (isInvisible()) setInvisible(false);
        if (chain != null) {
            chain.release();
            chain = null;
        }
        rest = restTicks;
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

    private ServerPlayerEntity farthestSeen() {
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity p : fighters) {
            if (p.distanceTo(this) > 40 || blocked(getEyePos(), p.getEyePos())) continue;
            if (best == null || p.squaredDistanceTo(this) > best.squaredDistanceTo(this)) best = p;
        }
        return best;
    }

    private double nearestDistance() {
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : fighters) best = Math.min(best, p.distanceTo(this));
        return best;
    }

    private double flat(Vec3d pos) {
        return MugenHazards.flatDistance(getPos(), pos);
    }

    // ---- The entrance: the shadow spreads, the hands drag him up out of the ground, and on the hit he's loose ----

    private void tickEntrance(ServerWorld world, int songTicks) {
        if (startTick < 0) {
            startTick = songTicks;
            fromArena = Arenas.sinOf(world) == Sin.LUST && getY() < LustArena.FLOOR_Y - 1;
            home = fromArena ? LustArena.CENTER : ground(getPos());
            untouchable = true;
            if (fromArena) {
                setNoGravity(true);
                noClip = true;
                bossBar().setVisible(false);
                setPoseState(POSE_RISE);
                faceYaw(-90f);
                bossLandTick = MugenTimeline.BOOM;
            } else {
                // Summoned anywhere else (tests): blades out, eyes lit, and a quick landing
                drawBlades();
                dataTracker.set(EYES, EYES_VIOLET);
                bossLandTick = songTicks + 1;
            }
        }
        int t = songTicks;
        if (!fromArena) {
            if (t == bossLandTick) land(world, false);
            if (t >= bossLandTick + MugenTimeline.READY_AFTER) ready();
            return;
        }
        Vec3d c = LustArena.CENTER;
        if (t < MugenTimeline.RISEN) {
            setVelocity(Vec3d.ZERO);
            setPosition(c.x, c.y - MugenTimeline.riseDepth(t), c.z);
        }
        for (int i = 0; i < MugenTimeline.POOLS.length; i++) {
            if (t == MugenTimeline.POOLS[i]) hazards.add(new MugenHazards.Pool(world, c, 7f - i * 0.5f, 0, this));
        }
        if (t == MugenTimeline.RISE_START) {
            emit(world, ParticleTypes.SQUID_INK, c.add(0, 0.2, 0), 40, 0.6, 0.1, 0.6, 0.12);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 3.0f, 0.6f);
        }
        if (t > MugenTimeline.RISE_START && t < MugenTimeline.RISEN) {
            // Shadow dripping off him as he comes up
            if (t % 2 == 0) emit(world, ParticleTypes.SQUID_INK, getPos().add(0, getHeight() * 0.6, 0), 1, 0.25, 0.4, 0.25, 0.01);
            if (t % 3 == 0) emit(world, MugenHazards.SHADOW, c.add(0, 0.3, 0), 3, 0.8, 0.1, 0.8, 0);
            if (t % 25 == 0) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        if (t == MugenTimeline.RISEN) {
            setPosition(c.x, c.y, c.z);
            noClip = false;
            setNoGravity(false);
        }
        if (t == MugenTimeline.LOOK_UP) {
            // He lifts his head: the eyes light, violet, and the bar comes up
            setPoseState(POSE_LOOK);
            dataTracker.set(EYES, EYES_VIOLET);
            bossBar().setVisible(true);
            Vec3d eyes = getEyePos();
            emit(world, MugenHazards.VIOLET, eyes, 30, 0.3, 0.1, 0.3, 0);
            for (ServerPlayerEntity p : audience(world)) {
                p.playSoundToPlayer(SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 1.2f, 0.5f);
                p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            }
        }
        if (t == MugenTimeline.DRAW) setPoseState(POSE_DRAW);
        if (t == MugenTimeline.DRAW + 10 || t == MugenTimeline.DRAW + 16) {
            // Two blades, drawn one after the other
            if (t == MugenTimeline.DRAW + 10) equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            else equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.NETHERITE_SWORD));
            Vec3d h = handPos(t == MugenTimeline.DRAW + 10);
            emit(world, ParticleTypes.CRIT, h, 14, 0.2, 0.2, 0.2, 0.3);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 1.5f, 0.9f);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 1.7f);
        }
        int flicker = MugenTimeline.flicker(t);
        if (flicker >= 0 && (t - MugenTimeline.FLICKER) % MugenTimeline.FLICKER_STEP == 0) flickerTo(world, flicker);
        if (t == MugenTimeline.FLICKER + MugenTimeline.FLICKERS * MugenTimeline.FLICKER_STEP) {
            // Back in the middle, as if he'd never moved
            MugenHazards.poof(world, getPos().add(0, 1, 0), false);
            refreshPositionAndAngles(c.x, c.y, c.z, -90f, 0f);
            faceYaw(-90f);
            setPoseState(POSE_LOOK);
            setAfterimages(false);
        }
        if (t == MugenTimeline.STANCE) {
            setPoseState(POSE_STANCE);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t > MugenTimeline.STANCE && t < bossLandTick) {
            // The dark drawn in to him, faster and faster
            double r = 6 * (bossLandTick - t) / (double) (bossLandTick - MugenTimeline.STANCE) + 0.6;
            for (int i = 0; i < 4; i++) {
                double a = random.nextDouble() * MathHelper.TAU;
                Vec3d p = c.add(Math.cos(a) * r, 0.3 + random.nextDouble() * 2, Math.sin(a) * r);
                Vec3d in = c.add(0, 1, 0).subtract(p);
                world.spawnParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 0, in.x, in.y, in.z, 0.12);
            }
            if (t == bossLandTick - 20) world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.5f, 0.8f);
        }
        if (t == bossLandTick) land(world, true);
        if (t >= bossLandTick + MugenTimeline.READY_AFTER) ready();
    }

    /** One of his flickers in the entrance: there, a moment, in front of one of them, then gone again. */
    private void flickerTo(ServerWorld world, int i) {
        List<ServerPlayerEntity> all = audience(world);
        setAfterimages(true);
        setPoseState(i % 2 == 0 ? POSE_SLASH_A : POSE_TAUNT);
        Vec3d at;
        if (all.isEmpty()) {
            double a = i * MathHelper.HALF_PI;
            at = LustArena.CENTER.add(Math.cos(a) * 4, 0, Math.sin(a) * 4);
        } else {
            ServerPlayerEntity p = all.get(i % all.size());
            Vec3d toBoss = LustArena.CENTER.subtract(p.getPos()).multiply(1, 0, 1);
            toBoss = toBoss.lengthSquared() > 1e-3 ? toBoss.normalize() : new Vec3d(-1, 0, 0);
            at = new Vec3d(p.getX(), LustArena.FLOOR_Y, p.getZ()).add(toBoss.multiply(2.2));
        }
        MugenHazards.poof(world, getPos().add(0, 1, 0), false);
        refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
        ServerPlayerEntity near = null;
        for (ServerPlayerEntity p : all) if (near == null || p.squaredDistanceTo(at) < near.squaredDistanceTo(at)) near = p;
        if (near != null) faceTowards(near.getPos());
        emit(world, MugenHazards.SHADOW, at.add(0, 1, 0), 20, 0.3, 0.7, 0.3, 0);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.8f, 0.6f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 0.7f);
    }

    /** The hit at the end of the reversed intro: a blast of shadow throws everyone back (it hurts nobody). */
    private void land(ServerWorld world, boolean big) {
        setNoGravity(false);
        noClip = false;
        Vec3d at = big ? home : ground(getPos());
        setPosition(at.x, at.y, at.z);
        setVelocity(Vec3d.ZERO);
        setPoseState(POSE_LAND);
        Vec3d c = getPos();
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.unfreeze(p);
            if (big) Cinematics.flash(p, (float) (0.6 * Math.max(0.3, 1 - p.getPos().distanceTo(c) / 60)), 22);
        }
        if (big) hazards.add(new MugenHazards.Shockwave(c, 0.9, 30, 0, 2.2, 0.6, MugenHazards.SHADOW, true));
        hazards.add(MugenHazards.Shockwave.visual(c, 0.6, 18, MugenHazards.VIOLET));
        emit(world, ParticleTypes.FLASH, c.add(0, 1, 0), 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.EXPLOSION_EMITTER, c.add(0, 0.5, 0), 1, 0, 0, 0, 0);
        emit(world, ParticleTypes.SQUID_INK, c.add(0, 0.5, 0), 200, 3, 0.6, 3, 0.4);
        emit(world, MugenHazards.SHADOW, c.add(0, 1, 0), 150, 2.5, 1.2, 2.5, 0);
        emit(world, ParticleTypes.CHERRY_LEAVES, c.add(0, 2, 0), 80, 5, 2, 5, 0);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.7f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.0f, 1.2f);
    }

    private void ready() {
        untouchable = false;
        bossBar().setVisible(true);
        // His bigger moves aren't ready from the start
        for (Action big : new Action[]{Action.CLONES, Action.SWORD_RAIN, Action.SWAPPING, Action.SMOKE_BOMB}) lastUsed.put(big, age - 100);
        endAttack(10);
    }

    // ---- Half health: black, eerie, Toxic under the black, the eclipse, and he lands on the drop ----

    private void tickTransition(ServerWorld world, int songTicks) {
        int t = actionTicks;
        if (!toxic) {
            if (t == 1) {
                untouchable = true;
                clearAttacks(world);
                getNavigation().stop();
                setVelocity(Vec3d.ZERO);
                setPoseState(POSE_LAND);
                for (ServerPlayerEntity p : audience(world)) {
                    Cinematics.freeze(p);
                    Cinematics.screen(p, BossScreenPayload.BLACK, 0, MugenTimeline.BLACK_FADE);
                }
                home = Arenas.sinOf(world) == Sin.LUST ? LustArena.CENTER : ground(getPos());
            }
            eerie(world, t);
            if (t == MugenTimeline.SKY_AT) {
                // Under the black: the eclipse, his eyes burning red, and he's up in the sky over the courtyard
                sky(world, 2, 0);
                dataTracker.set(EYES, EYES_RED);
                setNoGravity(true);
                setPoseState(POSE_HANG);
            }
            if (t >= MugenTimeline.SKY_AT) hang(world, home.add(0, MugenTimeline.HANG_HEIGHT, 0));
            if (t >= MugenTimeline.EERIE_TICKS) {
                toxic = true;
                playSong(PHASE_TWO, 0);
            }
            return;
        }
        // From here on, timed by Toxic itself
        int s = songTicks;
        if (s == MugenTimeline.REVEAL_AT) {
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.unfreeze(p);
                Cinematics.screen(p, BossScreenPayload.CLEAR, 0, MugenTimeline.REVEAL_FADE);
                p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 0.6f, 0.5f);
                p.playSoundToPlayer(SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.5f, 0.4f);
            }
        }
        if (s < MugenTimeline.DIVE_AT) {
            hang(world, home.add(0, MugenTimeline.hangHeight(s), 0));
            if (s % 20 == 0) {
                // The darkness radiating off him, ring after ring, and a column of it down to the ground
                hazards.add(MugenHazards.Shockwave.visual(home, 0.5, 14, MugenHazards.SHADOW));
                for (double y = 0; y < MugenTimeline.hangHeight(s); y += 1.2) emit(world, MugenHazards.SHADOW, home.add(0, y, 0), 1, 0.2, 0.3, 0.2, 0);
            }
            if (s % 40 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0f, 0.5f);
            return;
        }
        if (s == MugenTimeline.DIVE_AT) {
            setPoseState(POSE_DIVE);
            setAfterimages(true);
            MugenHazards.poof(world, getPos().add(0, 1, 0), true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 2.5f, 0.7f);
        }
        if (s < MugenTimeline.TOXIC_DROP) {
            setVelocity(Vec3d.ZERO);
            setPosition(home.x, home.y + MugenTimeline.diveHeight(s), home.z);
            emit(world, ParticleTypes.LARGE_SMOKE, getPos().add(0, 1.5, 0), 4, 0.2, 0.6, 0.2, 0.02);
            emit(world, MugenHazards.SHADOW, getPos().add(0, 1.5, 0), 6, 0.3, 0.8, 0.3, 0);
            return;
        }
        if (s == MugenTimeline.TOXIC_DROP) {
            land(world, true);
            setAfterimages(false);
            transitioned = true;
            getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(SPEED * 1.15);
            Vec3d c = getPos();
            BlockState under = getWorld().getBlockState(BlockPos.ofFloored(c.x, c.y - 0.5, c.z));
            emit(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, under.isAir() ? Blocks.COBBLESTONE.getDefaultState() : under), c.add(0, 0.3, 0), 160, 2.5, 0.4, 2.5, 0.4);
            emit(world, MugenHazards.CRIMSON, c.add(0, 1, 0), 120, 3, 1.2, 3, 0);
            emit(world, ParticleTypes.SONIC_BOOM, c.add(0, 1, 0), 1, 0, 0, 0, 0);
            emit(world, ParticleTypes.GUST_EMITTER_LARGE, c.add(0, 0.5, 0), 1, 0, 0, 0, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 4.0f, 0.6f);
        }
        if (s >= MugenTimeline.TOXIC_DROP + MugenTimeline.READY_AFTER) {
            untouchable = false;
            for (Action big : new Action[]{Action.UNSEEN_HANDS, Action.GRAPPLE_SLAM, Action.SHADOW_STEP}) lastUsed.put(big, age - 200);
            endAttack(4);
        }
    }

    /** Hanging in the air over the courtyard in his black aura, turning slowly. */
    private void hang(ServerWorld world, Vec3d at) {
        setVelocity(Vec3d.ZERO);
        setPosition(at.x, at.y, at.z);
        faceYaw(getYaw() + 1.2f);
        setPoseState(POSE_HANG);
        Vec3d c = getPos().add(0, 1, 0);
        emit(world, MugenHazards.SHADOW, c, 5, 0.6, 0.9, 0.6, 0);
        if (age % 2 == 0) emit(world, ParticleTypes.SQUID_INK, c, 1, 0.4, 0.6, 0.4, 0.02);
        if (age % 3 == 0) emit(world, MugenHazards.CRIMSON, c, 2, 0.8, 1.0, 0.8, 0);
    }

    /** What you hear in the black at half health. */
    private void eerie(ServerWorld world, int t) {
        List<ServerPlayerEntity> all = audience(world);
        for (ServerPlayerEntity p : all) {
            if (t % 16 == 2) p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.6f, 0.5f);
            if (t == 8) p.playSoundToPlayer(SoundEvents.AMBIENT_CAVE.value(), SoundCategory.HOSTILE, 1.0f, 0.5f);
            if (t == 22) p.playSoundToPlayer(SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.4f, 0.4f);
            if (t == 36) p.playSoundToPlayer(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundCategory.HOSTILE, 1.4f, 0.5f);
            if (t == 50) {
                p.playSoundToPlayer(SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.2f, 0.5f);
                p.playSoundToPlayer(SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
            if (t == 66) p.playSoundToPlayer(SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 0.8f, 0.5f);
            if (t == 78) p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.HOSTILE, 1.4f, 0.6f);
        }
    }

    // ---- Between attacks: running whoever he's picked down ----

    private void tickMove(ServerWorld world) {
        if (heldStill) {
            stand();
            return;
        }
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
        double reach = 2.4;
        double d = distanceTo(target);
        if (age % 6 == 0 || getNavigation().isIdle()) {
            if (d > reach - 0.4) getNavigation().startMovingTo(target, 1.0);
            else getNavigation().stop();
        }
        // Can't get there, or too far: he doesn't walk, he's there
        if (d > 5 && getNavigation().isIdle()) stuckTicks++;
        else stuckTicks = 0;
        if ((stuckTicks > 20 || d > 22) && notUsedFor(Action.POOF, 40)) {
            stuckTicks = 0;
            startAttack(Action.POOF);
            return;
        }
        if (meleeCooldown <= 0 && d < reach && Math.abs(target.getY() - getY()) < 2.5) {
            boolean left = random.nextBoolean();
            setPoseState(left ? POSE_SLASH_B : POSE_SLASH_A);
            swingHand(left ? Hand.OFF_HAND : Hand.MAIN_HAND);
            if (cut(target, transitioned ? 9 : 7)) launch(target, target.getPos().subtract(getPos()), 0.6, 0.3);
            Vec3d at = target.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.2f, 1.2f);
            meleeCooldown = transitioned ? 10 : 14;
        } else if (meleeCooldown < 8 && poseState() != POSE_IDLE) {
            setPoseState(POSE_IDLE);
        }
        if (getVelocity().horizontalLengthSquared() > 0.01 && age % 4 == 0) {
            world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 0.2, getZ(), 1, 0.2, 0.05, 0.2, 0);
        }
    }

    // ---- Slipping hits: Phase, and the Tome of Logs ----

    private void dodge(ServerWorld world, ServerPlayerEntity attacker) {
        dodgeCooldown = DODGE_COOLDOWN;
        focus = attacker.getUuid();
        startAttack(Action.PHASE);
        lastUsed.remove(Action.PHASE);
        tickPhaseStep(world);
    }

    /**
     * The Tome of Logs, his way: the hit never lands. He's gone in a puff of white smoke, a log drops where he stood and
     * takes the blow, and he's behind whoever hit him, blade already moving.
     */
    private void substitute(ServerWorld world, ServerPlayerEntity attacker) {
        logCooldown = transitioned ? LOG_COOLDOWN_TWO : LOG_COOLDOWN;
        hazards.add(new MugenHazards.Log(world, getPos(), getPos().subtract(attacker.getPos())));
        Vec3d back = Vec3d.fromPolar(0, attacker.getYaw()).multiply(-1);
        Vec3d at = safeSpot(attacker.getPos().add(back.multiply(1.7)));
        if (at == null) at = safeSpot(attacker.getPos().add(back.multiply(-1.7)));
        if (at != null) {
            refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
            faceTowards(attacker.getPos());
            Vec3d c = at.add(0, 1, 0);
            world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 18, 0.35, 0.55, 0.35, 0.04);
            world.spawnParticles(ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0.05);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 0.6f, 1.5f);
        }
        focus = attacker.getUuid();
        startAttack(Action.LOG_STRIKE);
    }

    private void tickLogStrike(ServerWorld world) {
        int t = actionTicks;
        ServerPlayerEntity target = fighter(focus);
        stand();
        if (target == null) {
            endAttack();
            return;
        }
        faceTowards(target.getPos());
        if (t == 1) setPoseState(POSE_SLASH_A);
        if (t == 6) {
            setPoseState(POSE_SLASH_B);
            swingHand(Hand.MAIN_HAND);
            if (distanceTo(target) < 3.6 && cut(target, transitioned ? 12 : 10)) launch(target, target.getPos().subtract(getPos()), 1.0, 0.4);
            Vec3d at = target.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.4f, 1.0f);
        }
        if (t >= 14) endAttack(6);
    }

    /** Phase: a few blocks aside in a blur, leaving afterimages; it hurts nobody. */
    private void tickPhaseStep(ServerWorld world) {
        int t = actionTicks;
        if (step == 0) {
            step = 1;
            ServerPlayerEntity target = fighter(focus);
            Vec3d toward = target != null ? target.getPos().subtract(getPos()).multiply(1, 0, 1) : Vec3d.fromPolar(0, getYaw());
            toward = toward.lengthSquared() > 1e-3 ? toward.normalize() : new Vec3d(1, 0, 0);
            Vec3d side = new Vec3d(-toward.z, 0, toward.x).multiply(random.nextBoolean() ? 1 : -1);
            spot = null;
            for (double dist : new double[]{5, 4, 3}) {
                for (Vec3d way : new Vec3d[]{side, side.multiply(-1), side.add(toward.multiply(-0.7)).normalize()}) {
                    Vec3d at = safeSpot(getPos().add(way.multiply(dist)));
                    if (at != null && !blocked(getPos().add(0, 1, 0), at.add(0, 1, 0))) {
                        spot = at;
                        break;
                    }
                }
                if (spot != null) break;
            }
            from = getPos();
            setAfterimages(true);
            setPoseState(POSE_DASH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 0.8f, 1.8f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.6f, 2.0f);
            if (target != null) faceTowards(target.getPos());
        }
        if (spot != null && t <= 4) {
            Vec3d at = from.lerp(spot, Math.min(1, t / 4.0));
            setVelocity(Vec3d.ZERO);
            setPosition(at.x, at.y, at.z);
            world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 1, getZ(), 3, 0.2, 0.5, 0.2, 0);
        }
        if (t == 10) setAfterimages(false);
        if (t >= 10) endAttack(6);
    }

    // ---- Phase 1 moves ----

    private void tickPoof(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = fighter(focus);
            if (target == null) target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_SEAL);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.HOSTILE, 1.0f, 1.6f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (t == 6) {
            MugenHazards.poof(world, getPos().add(0, 1, 0), true);
            setInvisible(true);
        }
        if (t == 10) {
            Vec3d at = null;
            if (target != null) {
                Vec3d back = Vec3d.fromPolar(0, target.getYaw()).multiply(-1);
                Vec3d side = new Vec3d(-back.z, 0, back.x).multiply(random.nextBoolean() ? 1 : -1);
                for (Vec3d way : new Vec3d[]{back.multiply(3).add(side.multiply(1.5)), side.multiply(3.5), back.multiply(-3.5).add(side.multiply(2))}) {
                    at = safeSpot(target.getPos().add(way));
                    if (at != null) break;
                }
            }
            if (at != null) refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
            if (target != null) faceTowards(target.getPos());
        }
        if (t == 12) {
            setInvisible(false);
            MugenHazards.poof(world, getPos().add(0, 1, 0), false);
            setPoseState(POSE_SLASH_A);
        }
        if (t == 15 && target != null) {
            faceTowards(target.getPos());
            setPoseState(POSE_SLASH_B);
            swingHand(Hand.MAIN_HAND);
            if (distanceTo(target) < 3.4 && cut(target, transitioned ? 10 : 8)) launch(target, target.getPos().subtract(getPos()), 0.8, 0.35);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.3f, 1.0f);
        }
        if (t >= 20) endAttack();
    }

    private void tickKunai(ServerWorld world) {
        int t = actionTicks, count = transitioned ? 10 : 8, every = transitioned ? 2 : 3, first = transitioned ? 5 : 7;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_THROW_BACK);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), SoundCategory.HOSTILE, 1.2f, 1.5f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target != null) faceTowards(target.getPos());
        if (t >= first && (t - first) % every == 0 && (t - first) / every < count) {
            int k = (t - first) / every;
            boolean right = k % 2 == 0;
            setPoseState(right ? POSE_THROW : POSE_THROW_BACK);
            swingHand(right ? Hand.MAIN_HAND : Hand.OFF_HAND);
            if (target != null) {
                Vec3d hand = handPos(right);
                Vec3d aim = target.getBoundingBox().getCenter().add(target.getVelocity().multiply(4));
                Vec3d d = aim.subtract(hand).normalize();
                // Fast, but not too careful: a little off each time
                float yaw = (float) Math.toRadians((random.nextFloat() - 0.5f) * 12f), pitch = (float) Math.toRadians((random.nextFloat() - 0.5f) * 7f);
                d = d.rotateY(yaw).add(0, pitch, 0).normalize();
                hazards.add(new MugenHazards.Thrown(world, MugenHazards.Kind.KUNAI, hand, d.multiply(1.75), 8, 40));
                world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 0.7f, 1.9f);
                world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 0.6f, 1.7f);
            }
        }
        if (t >= first + count * every + 6) endAttack();
    }

    private void tickShuriken(ServerWorld world) {
        int t = actionTicks, fans = transitioned ? 2 : 1, n = transitioned ? 7 : 5;
        if (t == 1) {
            targets = picks(fans);
            if (targets.isEmpty()) {
                endAttack();
                return;
            }
            setPoseState(POSE_THROW_BACK);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), SoundCategory.HOSTILE, 1.0f, 1.8f);
        }
        stand();
        for (int f = 0; f < fans; f++) {
            if (t != 8 + f * 10 || f >= targets.size()) continue;
            ServerPlayerEntity target = targets.get(f);
            if (!target.isAlive()) continue;
            faceTowards(target.getPos());
            setPoseState(POSE_THROW);
            swingHand(f % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND);
            Vec3d hand = handPos(f % 2 == 0);
            Vec3d d = target.getBoundingBox().getCenter().subtract(hand).normalize();
            for (int i = 0; i < n; i++) {
                double spread = Math.toRadians(-40 + 80.0 * i / (n - 1));
                hazards.add(new MugenHazards.Thrown(world, MugenHazards.Kind.SHURIKEN, hand, d.rotateY((float) spread).multiply(1.2), 6, 30));
            }
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 1.8f);
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 1.0f, 2.0f);
        }
        if (t >= 14 + fans * 10) endAttack();
    }

    private void tickSpinKick(ServerWorld world) {
        int t = actionTicks, windup = transitioned ? 7 : 10;
        if (t == 1) {
            ServerPlayerEntity target = nearestFighter();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_KICK_WIND);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_INHALE, SoundCategory.HOSTILE, 1.5f, 1.2f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t < windup) {
            stand();
            if (target != null) turnTowards(target.getPos(), 30f);
            return;
        }
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        if (t == windup) {
            setPoseState(POSE_KICK);
            setVelocity(forward.x * 0.75, 0.25, forward.z * 0.75);
            velocityModified = true;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.5f, 1.0f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }
        if (t < windup + 9) {
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 0.9, getZ(), 1, 0.6, 0.1, 0.6, 0);
            world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 0.8, getZ(), 4, 1.2, 0.2, 1.2, 0);
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid()) || p.distanceTo(this) > 2.9) continue;
                struck.add(p.getUuid());
                if (cut(p, 10)) {
                    // Heavy: off your feet and a long way back
                    launch(p, p.getPos().subtract(getPos()), transitioned ? 2.9 : 2.5, 0.75);
                    Vec3d c = p.getBoundingBox().getCenter();
                    world.spawnParticles(ParticleTypes.GUST, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.0f, 0.6f);
                    world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_AIR, SoundCategory.HOSTILE, 1.2f, 1.2f);
                }
            }
            return;
        }
        stand();
        if (t == windup + 9) setPoseState(POSE_LAND);
        if (t >= windup + 16) endAttack();
    }

    private void tickChain(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = farthestSeen();
            if (target == null) target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_THROW_BACK);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (t < 10) {
            if (target != null) faceTowards(target.getPos());
            // The chain swung round once, rattling
            Vec3d h = handPos(true);
            world.spawnParticles(MugenHazards.STEEL, h.x, h.y + 0.3, h.z, 2, 0.3, 0.3, 0.3, 0);
            return;
        }
        if (t == 10) {
            if (target == null) {
                endAttack();
                return;
            }
            setPoseState(POSE_CHAIN);
            swingHand(Hand.MAIN_HAND);
            Vec3d h = handPos(true);
            chain = new MugenHazards.Chain(world, h, target.getBoundingBox().getCenter().subtract(h), 24, true);
            hazards.add(chain);
            return;
        }
        if (step == 2) {
            stand();
            if (t - mark >= 10) endAttack();
            return;
        }
        if (chain == null || (!hazards.contains(chain) && chain.hooked == null) || t > 80) {
            endAttack();
            return;
        }
        ServerPlayerEntity hooked = chain.hooked;
        if (hooked == null) {
            if (chain.missed && t > 14) setPoseState(POSE_IDLE);
            return;
        }
        if (step == 0) {
            step = 1;
            hooked.sendMessage(Text.translatable("message.fmscraft.mugen_get_over_here").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        faceTowards(hooked.getPos());
        Vec3d toHim = getPos().add(Vec3d.fromPolar(0, getBodyYaw()).multiply(1.2)).subtract(hooked.getPos());
        if (toHim.length() > 1.6 && step == 1) {
            // Dragged in along the ground, hard
            Vec3d pull = toHim.normalize().multiply(Math.min(1.5, toHim.length() - 1.2));
            Vec3d next = hooked.getPos().add(pull.x, 0, pull.z);
            double floor = ground(next.add(0, 1, 0)).y;
            if (Math.abs(floor - hooked.getY()) < 1.6) next = new Vec3d(next.x, floor, next.z);
            hooked.setVelocity(Vec3d.ZERO);
            hooked.networkHandler.requestTeleport(next.x, next.y, next.z, hooked.getYaw(), hooked.getPitch());
            hooked.fallDistance = 0;
            if (t % 3 == 0) world.playSound(null, hooked.getX(), hooked.getY(), hooked.getZ(), SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.HOSTILE, 1.5f, 0.8f);
            if (t > 60) step = 2;
            return;
        }
        if (step == 1) {
            step = 2;
            mark = t;
            setPoseState(POSE_STAB);
            swingHand(Hand.MAIN_HAND);
            hooked.setVelocity(Vec3d.ZERO);
            hooked.velocityModified = true;
            if (cut(hooked, 20)) launch(hooked, hooked.getPos().subtract(getPos()), 1.3, 0.45);
            Vec3d c = hooked.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 30, 0.3, 0.4, 0.3, 0.4);
            world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 12, 0.2, 0.3, 0.2, 0.08);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 2.0f, 0.7f);
            chain.release();
            chain = null;
        }
    }

    private void tickBladeSpin(ServerWorld world) {
        int t = actionTicks, windup = 8, spin = transitioned ? 50 : 40;
        if (t == 1) {
            ServerPlayerEntity target = nearestFighter();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_SPIN);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t < windup) {
            stand();
            return;
        }
        if (t < windup + spin) {
            if (target != null) {
                Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1);
                double speed = transitioned ? 0.4 : 0.32;
                if (to.lengthSquared() > 1) to = to.normalize().multiply(speed);
                setVelocity(to.x, getVelocity().y, to.z);
                velocityModified = true;
            }
            if ((t - windup) % 6 == 0) struck.clear();
            for (int i = 0; i < 2; i++) {
                double a = (age * 1.2 + i * Math.PI);
                world.spawnParticles(MugenHazards.SHADOW, getX() + Math.cos(a) * 1.6, getY() + 1.1, getZ() + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
            }
            if (t % 4 == 0) {
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.1, getZ(), 2, 1.0, 0.1, 1.0, 0);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 1.4f + random.nextFloat() * 0.3f);
            }
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid()) || p.distanceTo(this) > 2.8) continue;
                struck.add(p.getUuid());
                if (cut(p, 5)) launch(p, p.getPos().subtract(getPos()), 0.7, 0.3);
            }
            return;
        }
        stand();
        if (t == windup + spin) setPoseState(POSE_STANCE);
        if (t >= windup + spin + 10) endAttack();
    }

    private void tickSneak(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_SEAL);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t <= 22) stand();
        if (t == 4) {
            MugenHazards.poof(world, getPos().add(0, 1, 0), true);
            setInvisible(true);
        }
        if (t >= 4 && t < 22 && target != null) {
            // A dark mark at their feet, following them
            MugenHazards.ring(world, t > 16 ? MugenHazards.CRIMSON : MugenHazards.VIOLET, ground(target.getPos()), 1.4, 14, t * 0.2);
            if (t % 6 == 4) world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
        if (t == 22) {
            spot = target != null ? ground(target.getPos()) : ground(getPos());
            Vec3d above = spot.add(0, 5, 0);
            if (blocked(spot.add(0, 1, 0), above.add(0, 1.8, 0))) above = spot.add(0, 2.5, 0);
            setNoGravity(true);
            refreshPositionAndAngles(above.x, above.y, above.z, getYaw(), 0f);
            setInvisible(false);
            setPoseState(POSE_PLUNGE);
            MugenHazards.poof(world, above.add(0, 1, 0), false);
            world.playSound(null, above.x, above.y, above.z, SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        if (t > 22 && t < 26) {
            setVelocity(Vec3d.ZERO);
            MugenHazards.ring(world, MugenHazards.CRIMSON, spot, 1.8, 16, t * 0.3);
        }
        if (t == 26) {
            setNoGravity(false);
            setVelocity(0, -2.4, 0);
            velocityModified = true;
            setAfterimages(true);
        }
        if (t > 26 && step == 0 && (isOnGround() || t > 40)) {
            step = 1;
            setAfterimages(false);
            setPoseState(POSE_LAND);
            Vec3d c = getPos();
            for (ServerPlayerEntity p : fighters) {
                if (MugenHazards.flatDistance(p.getPos(), c) > 2.0 || Math.abs(p.getY() - c.y) > 2.5) continue;
                if (cut(p, 16)) launch(p, p.getPos().subtract(c), 1.0, 0.5);
            }
            hazards.add(MugenHazards.Shockwave.visual(c, 0.5, 4, MugenHazards.SHADOW));
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 0.6, c.z, 3, 0.8, 0.1, 0.8, 0);
            world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 30, 1.0, 0.1, 1.0, 0.1);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 2.0f, 0.8f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 1.5f, 0.6f);
            mark = t;
        }
        if (step == 1 && t - mark >= 12) endAttack();
    }

    private void tickClones(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            setPoseState(POSE_SEAL);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 1.5f, 0.8f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.HOSTILE, 1.0f, 1.4f);
        }
        if (t < 12) {
            world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 1, getZ(), 4, 0.8, 0.8, 0.8, 0);
            return;
        }
        if (t == 12) {
            int n = MugenTimeline.clones(fighters.size(), transitioned);
            List<Vec3d> spots = new ArrayList<>();
            spots.add(getPos());
            for (int i = 0; i < n; i++) {
                double a = getYaw() * Math.PI / 180 + i * MathHelper.TAU / n;
                Vec3d at = safeSpot(getPos().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6));
                if (at != null) spots.add(at);
            }
            // He takes one of the spots himself: which one is he?
            int mine = random.nextInt(spots.size());
            for (int i = 0; i < spots.size(); i++) {
                Vec3d at = spots.get(i);
                if (i == mine) continue;
                MugenCloneEntity clone = ModBossEntities.MUGEN_CLONE.create(world);
                if (clone == null) continue;
                clone.refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
                clone.setOwner(this, transitioned);
                world.spawnEntity(clone);
                clones.add(clone);
                MugenHazards.poof(world, at.add(0, 1, 0), false);
            }
            Vec3d me = spots.get(mine);
            refreshPositionAndAngles(me.x, me.y, me.z, getYaw(), 0f);
            MugenHazards.poof(world, me.add(0, 1, 0), true);
        }
        if (t >= 18) endAttack();
    }

    private void tickOrb(ServerWorld world) {
        int t = actionTicks, orbs = transitioned ? 2 : 1;
        if (t == 1) {
            targets = picks(orbs);
            if (targets.isEmpty()) {
                endAttack();
                return;
            }
            setPoseState(POSE_ORB);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }
        stand();
        Vec3d hands = getPos().add(0, getHeight() * 0.65, 0).add(Vec3d.fromPolar(0, getBodyYaw()).multiply(0.8));
        int fireAt = transitioned ? 16 : 22;
        for (int k = 0; k < orbs && k < targets.size(); k++) {
            int at = fireAt + k * 14;
            ServerPlayerEntity target = targets.get(k);
            if (t < at && t > at - 14) {
                faceTowards(target.getPos());
                // The dark drawn into his cupped hands
                for (int i = 0; i < 3; i++) {
                    Vec3d o = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().multiply(1.5);
                    world.spawnParticles(ParticleTypes.SQUID_INK, hands.x + o.x, hands.y + o.y, hands.z + o.z, 0, -o.x, -o.y, -o.z, 0.1);
                }
                world.spawnParticles(MugenHazards.SHADOW, hands.x, hands.y, hands.z, 2, 0.15, 0.15, 0.15, 0);
            }
            if (t == at) {
                Vec3d d = target.getBoundingBox().getCenter().subtract(hands).normalize();
                hazards.add(new MugenHazards.Orb(world, hands.add(d.multiply(0.6)), d.multiply(transitioned ? 0.45 : 0.38), 16));
                swingHand(Hand.MAIN_HAND);
            }
        }
        if (t >= fireAt + orbs * 14 + 4) endAttack();
    }

    private void tickSwordRain(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            setPoseState(POSE_RAISE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }
        if (t == 8) {
            int glow = transitioned ? GLOW_TWO : GLOW_ONE, k = 0;
            List<ServerPlayerEntity> all = new ArrayList<>(fighters);
            for (ServerPlayerEntity p : all) {
                if (k >= 10) break;
                hazards.add(new MugenHazards.GiantSword(ground(p.getPos()), getPos(), k * 4, 18, 8, glow));
                k++;
            }
            if (transitioned) {
                // A second wave on where they'll run to
                for (ServerPlayerEntity p : all) {
                    if (k >= 14) break;
                    Vec3d ahead = p.getPos().add(p.getVelocity().multiply(1, 0, 1).multiply(14));
                    hazards.add(new MugenHazards.GiantSword(ground(ahead), getPos(), 20 + k * 3, 18, 8, glow));
                    k++;
                }
            }
        }
        if (t >= 26) endAttack();
    }

    private void tickBladeThrow(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null || bladeOut) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_THROW_BACK);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 1.2f, 1.4f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target != null) faceTowards(target.getPos());
        int throwAt = transitioned ? 7 : 10;
        if (t == throwAt && target != null) throwBlade(world, target, true);
        if (transitioned && t == throwAt + 8) {
            ServerPlayerEntity second = pick(focus);
            if (second != null) throwBlade(world, second, false);
        }
        if (t >= throwAt + (transitioned ? 16 : 8)) endAttack();
    }

    private void throwBlade(ServerWorld world, ServerPlayerEntity target, boolean right) {
        bladeOut = true;
        equipStack(right ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        setPoseState(POSE_THROW);
        swingHand(right ? Hand.MAIN_HAND : Hand.OFF_HAND);
        Vec3d h = handPos(right);
        Vec3d d = target.getBoundingBox().getCenter().add(target.getVelocity().multiply(3)).subtract(h).normalize();
        hazards.add(new MugenHazards.Thrown(world, MugenHazards.Kind.BLADE, h, d.multiply(1.5), 12, 30));
        world.playSound(null, h.x, h.y, h.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 1.5f, 0.8f);
    }

    private void tickDoubleJump(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = fighter(focus);
            if (target == null) target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            // Up to them on a roof, or over their head to land behind them
            Vec3d back = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            back = back.lengthSquared() > 1e-3 ? back.normalize() : new Vec3d(1, 0, 0);
            spot = target.getY() > getY() + 3 ? target.getPos() : target.getPos().add(back.multiply(2.5));
            setPoseState(POSE_CROUCH);
        }
        if (t < 5) {
            stand();
            return;
        }
        if (t == 5) {
            Vec3d to = spot.subtract(getPos());
            Vec3d flatTo = to.multiply(1, 0, 1);
            double h = Math.min(1.2, flatTo.length() / 14.0);
            Vec3d v = flatTo.lengthSquared() > 1e-3 ? flatTo.normalize().multiply(h) : Vec3d.ZERO;
            setVelocity(v.x, 0.7 + Math.max(0, to.y) * 0.04, v.z);
            velocityModified = true;
            setPoseState(POSE_JUMP);
            setAfterimages(true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_JUMP, SoundCategory.HOSTILE, 1.5f, 1.2f);
            world.spawnParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 8, 0.3, 0.05, 0.3, 0.03);
        }
        if (t > 6 && step == 0 && getVelocity().y < 0.15) {
            // The second jump, off the air itself: a burst of wind under his feet
            step = 1;
            Vec3d to = spot.subtract(getPos());
            Vec3d flatTo = to.multiply(1, 0, 1);
            Vec3d v = flatTo.lengthSquared() > 1e-3 ? flatTo.normalize().multiply(Math.min(1.0, flatTo.length() / 8.0)) : Vec3d.ZERO;
            setVelocity(v.x, 0.75 + Math.max(0, to.y) * 0.05, v.z);
            velocityModified = true;
            world.spawnParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 14, 0.4, 0.05, 0.4, 0.05);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.5f, 1.2f);
        }
        if (step == 1 && t > 10 && isOnGround()) {
            step = 2;
            setAfterimages(false);
            setPoseState(POSE_LAND);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOL_FALL, SoundCategory.HOSTILE, 1.5f, 0.6f);
            ServerPlayerEntity target = fighter(focus);
            if (target != null && distanceTo(target) < 3) {
                faceTowards(target.getPos());
                swingHand(Hand.MAIN_HAND);
                if (cut(target, 8)) launch(target, target.getPos().subtract(getPos()), 0.7, 0.3);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.3f, 1.1f);
            }
            mark = t;
        }
        if (step == 2 && t - mark >= 6) endAttack();
        if (t > 70) endAttack();
    }

    private void tickSwapping(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = farthestSeen();
            if (target == null || target.distanceTo(this) < 8) {
                endAttack(2);
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_POINT);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.HOSTILE, 1.0f, 1.0f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (target == null) {
            endAttack();
            return;
        }
        faceTowards(target.getPos());
        if (t < 16) {
            // The tome's glint at both ends, and between them
            if (t % 2 == 0) {
                Vec3d a = getPos().add(0, 1, 0), b = target.getPos().add(0, 1, 0);
                for (int i = 1; i < 12; i++) {
                    Vec3d p = a.lerp(b, i / 12.0);
                    world.spawnParticles(ParticleTypes.TRIAL_SPAWNER_DETECTION, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                }
            }
            return;
        }
        if (t == 16) {
            if (blocked(getEyePos(), target.getEyePos())) {
                endAttack();
                return;
            }
            Vec3d mine = getPos(), theirs = target.getPos();
            refreshPositionAndAngles(theirs.x, theirs.y, theirs.z, getYaw(), getPitch());
            target.teleport(world, mine.x, mine.y, mine.z, target.getYaw(), target.getPitch());
            target.fallDistance = 0;
            float pitch = 1.0f + random.nextFloat() * 0.2f;
            world.playSound(null, mine.x, mine.y, mine.z, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.HOSTILE, 1.3f, pitch);
            world.playSound(null, theirs.x, theirs.y, theirs.z, SoundEvents.BLOCK_DECORATED_POT_INSERT, SoundCategory.HOSTILE, 1.3f, pitch);
            for (Vec3d p : new Vec3d[]{mine, theirs}) {
                world.spawnParticles(ParticleTypes.POOF, p.x, p.y + 1, p.z, 10, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticles(ParticleTypes.TRIAL_SPAWNER_DETECTION, p.x, p.y + 1, p.z, 5, 0.3, 0.5, 0.3, 0.05);
            }
        }
        if (t == 20) {
            // Straight into whoever's where he's landed
            int near = 0;
            for (ServerPlayerEntity p : fighters) if (p.distanceTo(this) < 6) near++;
            startAttack(near >= 1 ? (near >= 2 ? Action.BLADE_SPIN : Action.SPIN_KICK) : Action.KUNAI);
        }
    }

    private void tickWind(ServerWorld world) {
        int t = actionTicks, n = transitioned ? 5 : 3;
        if (t == 1) setPoseState(POSE_THROW_BACK);
        stand();
        if (t >= 6 && (t - 6) % 6 == 0 && (t - 6) / 6 < n) {
            ServerPlayerEntity target = pick(null);
            if (target != null) {
                faceTowards(target.getPos());
                setPoseState(POSE_THROW);
                swingHand(Hand.MAIN_HAND);
                Vec3d h = handPos(true);
                Vec3d d = target.getBoundingBox().getCenter().add(target.getVelocity().multiply(5)).subtract(h).normalize();
                WindChargeEntity charge = new WindChargeEntity(world, h.x, h.y, h.z, d.multiply(1.3));
                charge.setOwner(this);
                world.spawnEntity(charge);
                world.playSound(null, h.x, h.y, h.z, SoundEvents.ENTITY_WIND_CHARGE_THROW, SoundCategory.HOSTILE, 1.0f, 0.9f);
            }
        }
        if (t >= 6 + n * 6 + 4) endAttack();
    }

    private void tickSmokeBomb(ServerWorld world) {
        int t = actionTicks, strikes = transitioned ? 2 : 1;
        if (t == 1) setPoseState(POSE_THROW);
        if (t < 5) {
            stand();
            return;
        }
        if (t == 5) {
            smoke = new MugenHazards.Smoke(world, ground(getPos()), 5.5, 120);
            hazards.add(smoke);
            MugenHazards.poof(world, getPos().add(0, 1, 0), true);
            setInvisible(true);
        }
        for (int k = 0; k < strikes; k++) {
            int at = 22 + k * 18;
            if (t == at) {
                // Out of the smoke behind whoever's nearest it
                ServerPlayerEntity target = null;
                for (ServerPlayerEntity p : fighters) {
                    if (target == null || p.squaredDistanceTo(smoke.center) < target.squaredDistanceTo(smoke.center)) target = p;
                }
                if (target == null) continue;
                focus = target.getUuid();
                Vec3d back = Vec3d.fromPolar(0, target.getYaw()).multiply(-1.6);
                Vec3d spotAt = safeSpot(target.getPos().add(back));
                if (spotAt == null) spotAt = safeSpot(target.getPos().add(back.multiply(-1)));
                if (spotAt != null) refreshPositionAndAngles(spotAt.x, spotAt.y, spotAt.z, getYaw(), 0f);
                faceTowards(target.getPos());
                setInvisible(false);
                setPoseState(POSE_SLASH_A);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.6f, 0.5f);
            }
            if (t == at + 3) {
                ServerPlayerEntity target = fighter(focus);
                setPoseState(POSE_SLASH_B);
                swingHand(Hand.MAIN_HAND);
                if (target != null && distanceTo(target) < 3.4 && cut(target, 12)) launch(target, target.getPos().subtract(getPos()), 0.9, 0.4);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.3f, 1.1f);
                if (k < strikes - 1) {
                    MugenHazards.poof(world, getPos().add(0, 1, 0), false);
                    setInvisible(true);
                }
            }
        }
        if (t >= 22 + strikes * 18) endAttack();
    }

    // ---- Phase 2 moves ----

    private void tickUnseenHands(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            setPoseState(POSE_SEAL);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.2f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.HOSTILE, 1.0f, 0.8f);
        }
        if (t == 8) {
            Vec3d at = clusterSpot();
            if (at == null) {
                endAttack();
                return;
            }
            hazards.add(new MugenHazards.Pool(world, at, 8f, 5, null));
            // A big group: a second shadow under whoever's furthest from the first
            if (fighters.size() >= 6) {
                ServerPlayerEntity far = null;
                for (ServerPlayerEntity p : fighters) if (far == null || p.squaredDistanceTo(at) > far.squaredDistanceTo(at)) far = p;
                if (far != null && far.getPos().distanceTo(at) > 12) hazards.add(new MugenHazards.Pool(world, ground(far.getPos()), 6f, 5, null));
            }
        }
        if (t > 8 && t < 8 + MugenTimeline.POOL_CREEP) {
            world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 1, getZ(), 3, 0.5, 0.8, 0.5, 0);
            return;
        }
        if (t == 8 + MugenTimeline.POOL_CREEP + 2) {
            // Kunai for whoever the hands are holding
            setPoseState(POSE_THROW);
            for (Entity e : heldByHands()) {
                if (!(e instanceof ServerPlayerEntity p)) continue;
                Vec3d h = handPos(true);
                Vec3d d = p.getBoundingBox().getCenter().subtract(h).normalize();
                hazards.add(new MugenHazards.Thrown(world, MugenHazards.Kind.KUNAI, h, d.multiply(1.8), 6, 40));
                swingHand(Hand.MAIN_HAND);
            }
        }
        if (t >= 8 + MugenTimeline.POOL_CREEP + 10) endAttack();
    }

    private void tickGrappleSlam(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_INHALE, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t < 8) {
            stand();
            return;
        }
        if (t == 8) {
            setVelocity(0, 1.3, 0);
            velocityModified = true;
            setPoseState(POSE_JUMP);
            setAfterimages(true);
            world.spawnParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value(), SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        if (t == 20) {
            setNoGravity(true);
            setVelocity(Vec3d.ZERO);
            setPoseState(POSE_GRAPPLE);
            if (target != null && !blocked(handPos(true), target.getBoundingBox().getCenter())) {
                Vec3d h = handPos(true);
                chain = new MugenHazards.Chain(world, h, target.getBoundingBox().getCenter().subtract(h), 30, false);
                hazards.add(chain);
            }
        }
        if (t > 20 && step == 0) {
            setVelocity(Vec3d.ZERO);
            if (target != null) faceTowards(target.getPos());
            boolean hooked = chain != null && chain.hooked != null;
            boolean failed = chain == null || chain.missed || t > 44;
            if (hooked) {
                step = 1;
                setPoseState(POSE_DIVE);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 2.5f, 0.6f);
                if (chain.hooked != null) {
                    chain.hooked.sendMessage(Text.translatable("message.fmscraft.mugen_grappled").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
                }
            } else if (failed) {
                step = 1;
                if (chain != null) chain.release();
                chain = null;
                setPoseState(POSE_DIVE);
            }
            return;
        }
        if (step == 1) {
            // Down the chain onto them, or straight down if it missed
            ServerPlayerEntity hooked = chain != null ? chain.hooked : null;
            Vec3d to = hooked != null ? hooked.getPos() : ground(getPos());
            Vec3d d = to.subtract(getPos());
            double len = d.length();
            emit(world, MugenHazards.SHADOW, getPos().add(0, 1, 0), 5, 0.3, 0.6, 0.3, 0);
            if (len > 1.6 && t < 70 && !(hooked == null && isOnGround())) {
                Vec3d v = d.multiply(Math.min(1.8, len) / len);
                setVelocity(Vec3d.ZERO);
                Vec3d next = getPos().add(v);
                setPosition(next.x, next.y, next.z);
                if (hooked != null) {
                    hooked.setVelocity(Vec3d.ZERO);
                    hooked.velocityModified = true;
                }
                return;
            }
            step = 2;
            setNoGravity(false);
            setAfterimages(false);
            setPoseState(POSE_LAND);
            Vec3d c = ground(getPos());
            setPosition(c.x, c.y, c.z);
            if (hooked != null) {
                hooked.timeUntilRegen = 0;
                crush(hooked, 26);
                launch(hooked, hooked.getPos().subtract(c), 0.4, 0.9);
            }
            for (ServerPlayerEntity p : fighters) {
                if (p == hooked || p.getPos().distanceTo(c) > 4.5) continue;
                crush(p, 10);
                launch(p, p.getPos().subtract(c), 1.4, 0.6);
            }
            if (chain != null) chain.release();
            chain = null;
            hazards.add(MugenHazards.Shockwave.visual(c, 0.6, 7, MugenHazards.CRIMSON));
            BlockState under = getWorld().getBlockState(BlockPos.ofFloored(c.x, c.y - 0.5, c.z));
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under.isAir() ? Blocks.COBBLESTONE.getDefaultState() : under), c.x, c.y + 0.2, c.z, 60, 1.6, 0.3, 1.6, 0.3);
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.5, c.z, 60, 1.5, 0.4, 1.5, 0.2);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 4.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.7f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 1.5f, 1.0f);
            for (ServerPlayerEntity p : audience(world)) if (p.squaredDistanceTo(c) < 20 * 20) Cinematics.flash(p, 0.3f, 8);
            mark = t;
        }
        if (step == 2) {
            stand();
            if (t - mark >= 14) endAttack();
        }
    }

    private void tickSlide(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_CROUCH);
            dir = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            dir = dir.lengthSquared() > 1e-3 ? dir.normalize() : Vec3d.fromPolar(0, getYaw());
            faceTowards(getPos().add(dir));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GRAVEL_STEP, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (t < 9) {
            stand();
            // A streak of dust along the ground where he'll go
            for (double d = 1; d < 14; d += 1.5) {
                Vec3d p = getPos().add(dir.multiply(d));
                world.spawnParticles(MugenHazards.CRIMSON, p.x, p.y + 0.1, p.z, 1, 0.1, 0, 0.1, 0);
            }
            return;
        }
        if (t == 9) {
            setPoseState(POSE_SLIDE);
            setAfterimages(true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_SLIDE, SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        if (t < 25) {
            double speed = 1.15 * Math.pow(0.93, t - 9);
            setVelocity(dir.x * speed, getVelocity().y, dir.z * speed);
            velocityModified = true;
            BlockState under = getWorld().getBlockState(getBlockPos().down());
            if (!under.isAir()) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), getX(), getY() + 0.1, getZ(), 6, 0.4, 0.05, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 2, 0.3, 0.05, 0.3, 0.02);
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid()) || p.distanceTo(this) > 1.9) continue;
                struck.add(p.getUuid());
                if (cut(p, 10)) {
                    // Swept off their feet, up into the air
                    p.setVelocity(dir.x * 0.4, 1.15, dir.z * 0.4);
                    p.velocityModified = true;
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.8f, 0.8f);
                }
            }
            return;
        }
        stand();
        if (t == 25) {
            setAfterimages(false);
            setPoseState(POSE_LAND);
        }
        if (t >= 32) endAttack();
    }

    private void tickBlaze(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            setPoseState(POSE_ORB);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        if (t < 10) {
            Vec3d h = handPos(true);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, h.x, h.y, h.z, 2, 0.15, 0.15, 0.15, 0.01);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, h.x, h.y, h.z, 1, 0.15, 0.15, 0.15, 0.01);
        }
        // Three volleys, like a blaze, at up to three of you
        if (t == 10 || t == 16 || t == 22) {
            List<ServerPlayerEntity> at = picks(3);
            for (int i = 0; i < at.size(); i++) {
                ServerPlayerEntity p = at.get(i);
                Vec3d h = handPos(i % 2 == 0);
                Vec3d d = p.getBoundingBox().getCenter().add(p.getVelocity().multiply(3)).subtract(h).normalize();
                d = d.rotateY((float) Math.toRadians((random.nextFloat() - 0.5f) * 6));
                hazards.add(new MugenHazards.Thrown(world, MugenHazards.Kind.CHARGE, h, d.multiply(1.1), 7, 40));
                if (i == 0) faceTowards(p.getPos());
            }
            swingHand(t == 16 ? Hand.OFF_HAND : Hand.MAIN_HAND);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (t >= 30) endAttack();
    }

    /** The Taunt: everywhere at once round you, staring, a copy left standing wherever he was. It hurts nobody. */
    private void tickTaunt(ServerWorld world) {
        int t = actionTicks, blinks = 12;
        stand();
        if (t == 1) {
            untouchable = true;
            setPoseState(POSE_TAUNT);
            setAfterimages(true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 2.0f, 0.4f);
        }
        if (t >= 4 && (t - 4) % 5 == 0 && (t - 4) / 5 < blinks && !fighters.isEmpty()) {
            int k = (t - 4) / 5;
            ServerPlayerEntity p = fighters.get(k % fighters.size());
            // An echo of him stays where he was, staring
            MugenCloneEntity echo = ModBossEntities.MUGEN_CLONE.create(world);
            if (echo != null) {
                echo.refreshPositionAndAngles(getX(), getY(), getZ(), getYaw(), 0f);
                echo.makeEcho(this, k % 2 == 0 ? POSE_TAUNT : POSE_STANCE);
                world.spawnEntity(echo);
                clones.add(echo);
            }
            double a = random.nextDouble() * MathHelper.TAU, r = 2.5 + random.nextDouble();
            Vec3d at = safeSpot(p.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (at != null) refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
            faceTowards(p.getPos());
            setPoseState(k % 3 == 0 ? POSE_TAUNT : k % 3 == 1 ? POSE_STANCE : POSE_LOOK);
            world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 1, getZ(), 20, 0.3, 0.8, 0.3, 0);
            world.spawnParticles(MugenHazards.CRIMSON, getX(), getY() + 1.6, getZ(), 6, 0.2, 0.2, 0.2, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.7f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_NEARBY_CLOSER, SoundCategory.HOSTILE, 0.8f, 1.2f);
            if (k % 4 == 1) p.playSoundToPlayer(SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 0.8f, 0.4f);
        }
        int end = 4 + blinks * 5 + 6;
        if (t == end) {
            Vec3d c = home;
            if (Arenas.sinOf(world) != Sin.LUST) c = getPos();
            MugenHazards.poof(world, getPos().add(0, 1, 0), false);
            refreshPositionAndAngles(c.x, c.y, c.z, getYaw(), 0f);
            setPoseState(POSE_TAUNT);
            setAfterimages(false);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_EVOKER_CELEBRATE, SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 2.5f, 0.35f);
            hazards.add(MugenHazards.Shockwave.visual(c, 0.7, 20, MugenHazards.SHADOW));
            for (ServerPlayerEntity p : fighters) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 70, 0, false, false, true), this);
        }
        if (t >= end + 14) {
            untouchable = false;
            endAttack();
        }
    }

    /** The Blackout: the world goes black round everyone, seeing four blocks, and he hunts them in it. */
    private void tickBlackout(ServerWorld world) {
        int t = actionTicks, strikes = 4, start = 14;
        if (t == 1) {
            setPoseState(POSE_SEAL);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t < start) {
            stand();
            world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 6, 1.0, 1.0, 1.0, 0.1);
            return;
        }
        if (t == start) {
            blackoutTicks = MugenTimeline.BLACKOUT_TICKS;
            sky(world, 2, MugenTimeline.BLACKOUT_TICKS);
            for (ServerPlayerEntity p : audience(world)) {
                p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.0f, 0.5f);
                p.playSoundToPlayer(SoundEvents.AMBIENT_CAVE.value(), SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
            MugenHazards.poof(world, getPos().add(0, 1, 0), true);
            setInvisible(true);
        }
        for (int k = 0; k < strikes; k++) {
            int at = start + 22 + k * 28;
            if (t == at - 14) {
                // A whisper behind someone, and a faint shimmer at their feet: he's coming
                ServerPlayerEntity p = pick(null);
                if (p == null) continue;
                focus = p.getUuid();
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.HOSTILE, 1.5f, 1.4f);
                world.spawnParticles(MugenHazards.VIOLET, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.5, 0.05, 0.5, 0);
            }
            if (t == at) {
                ServerPlayerEntity p = fighter(focus);
                if (p == null) continue;
                Vec3d back = Vec3d.fromPolar(0, p.getYaw()).multiply(-1.6);
                Vec3d spotAt = safeSpot(p.getPos().add(back));
                if (spotAt != null) refreshPositionAndAngles(spotAt.x, spotAt.y, spotAt.z, getYaw(), 0f);
                faceTowards(p.getPos());
                setInvisible(false);
                setPoseState(POSE_SLASH_A);
            }
            if (t == at + 3) {
                ServerPlayerEntity p = fighter(focus);
                setPoseState(POSE_SLASH_B);
                swingHand(Hand.MAIN_HAND);
                if (p != null && distanceTo(p) < 3.4 && cut(p, 10)) launch(p, p.getPos().subtract(getPos()), 0.9, 0.35);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5f, 0.8f);
            }
            if (t == at + 8) {
                MugenHazards.poof(world, getPos().add(0, 1, 0), false);
                setInvisible(true);
            }
        }
        if (t >= start + MugenTimeline.BLACKOUT_TICKS) {
            setInvisible(false);
            MugenHazards.poof(world, getPos().add(0, 1, 0), false);
            endAttack();
        }
    }

    /** Shadow Step: through each of you in turn, too fast to see; the cuts he leaves hang in the air, then fall. */
    private void tickShadowStep(ServerWorld world) {
        int t = actionTicks, every = 6;
        if (t == 1) {
            targets = picks(3);
            if (targets.isEmpty()) {
                endAttack();
                return;
            }
            setPoseState(POSE_STANCE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        stand();
        int n = targets.size(), cutAt = 8 + n * every + 12;
        if (t >= 8 && (t - 8) % every == 0 && (t - 8) / every < n) {
            ServerPlayerEntity p = targets.get((t - 8) / every);
            Vec3d a = getPos();
            Vec3d through = p.getPos().subtract(a).multiply(1, 0, 1);
            through = through.lengthSquared() > 1e-3 ? through.normalize() : Vec3d.fromPolar(0, getYaw());
            Vec3d b = safeSpot(p.getPos().add(through.multiply(3)));
            if (b == null) b = safeSpot(p.getPos().add(through.multiply(-2)));
            if (b == null) b = a;
            setAfterimages(true);
            setPoseState(POSE_DASH);
            refreshPositionAndAngles(b.x, b.y, b.z, getYaw(), 0f);
            faceTowards(b.add(through));
            hazards.add(new MugenHazards.Slash(a, b, cutAt - t, 13));
            world.spawnParticles(MugenHazards.SHADOW, b.x, b.y + 1, b.z, 12, 0.3, 0.6, 0.3, 0);
            world.playSound(null, b.x, b.y, b.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.2f, 1.6f);
        }
        if (t == 8 + n * every + 2) {
            // Blades away, slowly
            setAfterimages(false);
            setPoseState(POSE_STANCE);
        }
        if (t == cutAt) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 2.0f, 1.6f);
            setPoseState(POSE_IDLE);
        }
        if (t >= cutAt + 6) endAttack();
    }

    // ---- Void Spikes (both phases): the ground goes black round him, then spikes of the void burst up out of it ----

    private void tickVoidSpikes(ServerWorld world) {
        int t = actionTicks, n = transitioned ? 9 : 7;
        stand();
        if (t == 1) {
            setPoseState(POSE_SEAL);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, 1.5f, 0.8f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
        if (t == 6) {
            setPoseState(POSE_LAND);
            swingHand(Hand.OFF_HAND);
            world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2, getZ(), 30, 1.2, 0.1, 1.2, 0.1);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 1.4f, 0.5f);
            List<Vec3d> spots = new ArrayList<>();
            // In phase 2 one comes up under each of two of you, if you're close
            if (transitioned) for (ServerPlayerEntity p : picks(2)) if (p.distanceTo(this) < 14) spots.add(ground(p.getPos().add(0, 0.5, 0)));
            for (int tries = 0; spots.size() < n && tries < 80; tries++) {
                double a = random.nextDouble() * MathHelper.TAU, r = 2.5 + random.nextDouble() * 6;
                Vec3d at = ground(getPos().add(Math.cos(a) * r, 1.5, Math.sin(a) * r));
                if (Math.abs(at.y - getY()) > 3) continue;
                boolean apart = true;
                for (Vec3d o : spots) if (MugenHazards.flatDistance(o, at) < 1.7) apart = false;
                if (apart) spots.add(at);
            }
            for (int i = 0; i < spots.size(); i++) hazards.add(new MugenHazards.VoidSpike(spots.get(i), i * 3, 22));
        }
        if (t >= 30) endAttack();
    }

    // ---- Iaido (both phases): blades sheathed, perfectly still. Hit him and his blade gets you first. Leave him be and he cuts along a line ----

    private static final double IAIDO_REACH = 13;

    private void tickIaido(ServerWorld world) {
        int t = actionTicks, stance = transitioned ? 30 : 40, warn = 16;
        if (t == 1) {
            ServerPlayerEntity target = nearestFighter();
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            dir = null;
            countered.clear();
            sheathe();
            setPoseState(POSE_IAIDO);
            iaidoOpen = true;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.2f, 0.5f);
        }
        stand();
        if (step == 1) {
            // Countered someone: a moment, then on
            if (t - mark == 6) setAfterimages(false);
            if (t - mark >= 12) {
                iaidoOpen = false;
                endAttack();
            }
            return;
        }
        ServerPlayerEntity target = fighter(focus);
        if (t < stance) {
            if (t % 4 == 0) world.spawnParticles(MugenHazards.VIOLET, getX(), getY() + 1, getZ(), 3, 0.4, 0.6, 0.4, 0);
            if (t % 7 == 0) world.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 1, 0.4, 0.6, 0.4, 0.005);
            if (t < stance - warn || dir == null) {
                if (target != null) {
                    faceTowards(target.getPos());
                    Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1);
                    dir = to.lengthSquared() > 1e-3 ? to.normalize() : Vec3d.fromPolar(0, getYaw());
                } else if (dir == null) {
                    dir = Vec3d.fromPolar(0, getYaw());
                }
            } else {
                // The line he'll cut along, a breath before he does
                for (double d = 1; d < IAIDO_REACH; d += 0.7) {
                    Vec3d p = ground(getPos().add(dir.multiply(d)).add(0, 1.5, 0));
                    world.spawnParticles(MugenHazards.CRIMSON, p.x, p.y + 0.1, p.z, 1, 0.05, 0, 0.05, 0);
                }
                if (t == stance - warn) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.2f, 1.6f);
            }
            return;
        }
        if (t == stance) {
            iaidoOpen = false;
            Vec3d a = getPos(), b = a;
            for (double d = IAIDO_REACH; d >= 2; d -= 1) {
                Vec3d at = safeSpot(a.add(dir.multiply(d)));
                if (at != null && !blocked(a.add(0, 1, 0), at.add(0, 1, 0))) {
                    b = at;
                    break;
                }
            }
            drawBlades();
            setAfterimages(true);
            setPoseState(POSE_SLASH_B);
            refreshPositionAndAngles(b.x, b.y, b.z, getYaw(), 0f);
            faceTowards(b.add(dir));
            hazards.add(new MugenHazards.Slash(a, b, 3, 16));
            world.playSound(null, b.x, b.y, b.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.5f, 1.8f);
            world.playSound(null, b.x, b.y, b.z, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 2.0f, 1.8f);
        }
        if (t == stance + 6) setAfterimages(false);
        if (t >= stance + 14) endAttack();
    }

    /** Iaido: a hit while he stands sheathed never lands; his blade does, first. Each of you once. */
    private void counter(ServerWorld world, ServerPlayerEntity p) {
        if (!countered.add(p.getUuid())) return;
        Vec3d front = p.getPos().add(Vec3d.fromPolar(0, p.getYaw()).multiply(1.3));
        Vec3d at = p.distanceTo(this) < 10 ? safeSpot(front) : null;
        if (at != null) refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
        faceTowards(p.getPos());
        drawBlades();
        setPoseState(POSE_SLASH_B);
        setAfterimages(true);
        swingHand(Hand.MAIN_HAND);
        p.timeUntilRegen = 0;
        if (cut(p, 18)) launch(p, p.getPos().subtract(getPos()), 1.6, 0.5);
        Cinematics.flash(p, 0.45f, 8);
        Vec3d c = p.getBoundingBox().getCenter();
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 3, 0.4, 0.3, 0.4, 0);
        world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 24, 0.3, 0.4, 0.3, 0.4);
        world.spawnParticles(MugenHazards.CRIMSON, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 2.0f, 0.7f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.HOSTILE, 2.0f, 2.0f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 1.2f, 1.6f);
        step = 1;
        mark = actionTicks;
    }

    private void sheathe() {
        sheathed = true;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    // ---- Shadow Bind (both phases): a band of shadow sweeps across the ground; whoever's standing in it is held ----

    private void tickShadowBind(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, 1.4f, 1.0f);
        }
        stand();
        ServerPlayerEntity target = fighter(focus);
        if (t < 10) {
            if (target != null) {
                faceTowards(target.getPos());
                Vec3d to = target.getPos().subtract(getPos()).multiply(1, 0, 1);
                dir = to.lengthSquared() > 1e-3 ? to.normalize() : Vec3d.fromPolar(0, getYaw());
            }
            world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.1, getZ(), 3, 0.5, 0.05, 0.5, 0.03);
            return;
        }
        if (t == 10) {
            setPoseState(POSE_LAND);
            swingHand(Hand.OFF_HAND);
            if (dir == null) dir = Vec3d.fromPolar(0, getYaw());
            hazards.add(new MugenHazards.Band(ground(getPos().add(0, 0.5, 0)), dir, transitioned ? 1.1 : 0.9));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.0f, 1.4f);
        }
        if (t >= 26) endAttack();
    }

    /** Held fast by the shadow for {@code ticks}: pinned where they stand. Binding someone bound holds them longer. */
    void bind(ServerPlayerEntity p, int ticks) {
        for (MugenHazards.Hazard h : hazards) {
            if (h instanceof MugenHazards.Bound b && b.player == p) {
                b.ticks = Math.max(b.ticks, b.age + ticks);
                return;
            }
        }
        hazards.add(new MugenHazards.Bound(p, ticks));
        p.sendMessage(Text.translatable("message.fmscraft.mugen_bound").formatted(Formatting.DARK_PURPLE), true);
    }

    void flash(ServerPlayerEntity p, float strength, int ticks) {
        Cinematics.flash(p, strength, ticks);
    }

    // ---- Shadow Sneak (phase 2): he sinks into the ground; his shadow crosses it to you, and he bursts up out of it spinning ----

    private void tickShadowSneak(ServerWorld world) {
        int t = actionTicks, sink = 16;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_CROUCH);
            setNoGravity(true);
            noClip = true;
            from = getPos();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.HOSTILE, 1.5f, 1.2f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t <= sink) {
            // Down into the ground, slowly, the shadow pooling round him
            setVelocity(Vec3d.ZERO);
            setPosition(from.x, from.y - 2.0 * t / sink, from.z);
            world.spawnParticles(ParticleTypes.SQUID_INK, from.x, from.y + 0.1, from.z, 3, 0.5, 0.05, 0.5, 0.03);
            world.spawnParticles(MugenHazards.SHADOW, from.x, from.y + 0.2, from.z, 4, 0.6, 0.05, 0.6, 0);
            return;
        }
        if (shade == null && step == 0) {
            setInvisible(true);
            shade = new MugenHazards.Shade(world, ground(from.add(0, 1.5, 0)));
            hazards.add(shade);
        }
        if (step == 0) {
            if (target != null) {
                Vec3d to = target.getPos().subtract(shade.pos).multiply(1, 0, 1);
                double d = to.length();
                if (d > 0.4) shade.pos = ground(shade.pos.add(to.multiply(Math.min(0.5, d) / d)).add(0, 1.5, 0));
                if (d < 0.7) step = 1;
            }
            if (t > sink + 60 || target == null) step = 1;
            setVelocity(Vec3d.ZERO);
            setPosition(shade.pos.x, shade.pos.y - 2.1, shade.pos.z);
            if (step == 1) {
                mark = t;
                shade.trembling = true;
                world.playSound(null, shade.pos.x, shade.pos.y, shade.pos.z, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.HOSTILE, 1.5f, 1.4f);
            }
            return;
        }
        if (step == 1 && t - mark < 12) {
            setVelocity(Vec3d.ZERO);
            return;
        }
        if (step == 1) {
            // Out of it, spinning, up into the air
            step = 2;
            mark = t;
            Vec3d c = shade != null ? shade.pos : ground(getPos().add(0, 3, 0));
            if (shade != null) shade.done = true;
            shade = null;
            setInvisible(false);
            noClip = false;
            setNoGravity(false);
            setPosition(c.x, c.y, c.z);
            setVelocity(0, 0.95, 0);
            velocityModified = true;
            setPoseState(POSE_SPIN);
            setAfterimages(true);
            for (ServerPlayerEntity p : fighters) {
                if (MugenHazards.flatDistance(p.getPos(), c) > 3.2 || Math.abs(p.getY() - c.y) > 3) continue;
                p.timeUntilRegen = 0;
                if (cut(p, 14)) launch(p, p.getPos().subtract(c), 0.6, 1.15);
            }
            world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.5, c.z, 60, 1.0, 0.6, 1.0, 0.25);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 1, c.z, 4, 1.2, 0.3, 1.2, 0);
            world.spawnParticles(ParticleTypes.POOF, c.x, c.y + 0.5, c.z, 20, 0.8, 0.3, 0.8, 0.08);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.5f, 1.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 2.0f, 0.8f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.7f);
            return;
        }
        int s = t - mark;
        if (s < 12 && s % 3 == 0) world.spawnParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1, getZ(), 1, 0.8, 0.2, 0.8, 0);
        if (s > 6 && isOnGround() && poseState() == POSE_SPIN) {
            setPoseState(POSE_LAND);
            setAfterimages(false);
        }
        if (s >= 26) endAttack();
    }

    // ---- Controller (phase 2, rare): strings from his hands to one of you, and your feet stop doing what you tell them ----

    public static final int CONTROL_TICKS = 100;

    private void tickController(ServerWorld world) {
        int t = actionTicks, reach = 16;
        stand();
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_PUPPET);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, SoundCategory.HOSTILE, 1.2f, 0.5f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (target == null) {
            endAttack();
            return;
        }
        faceTowards(target.getPos());
        if (t < reach) {
            strings(world, target, t / (float) reach);
            return;
        }
        if (t == reach) control(world, target, CONTROL_TICKS);
        if (t >= reach + 30) endAttack();
    }

    private void control(ServerWorld world, ServerPlayerEntity p, int ticks) {
        controlled.put(p.getUuid(), ticks);
        sendControl(p, ticks);
        p.sendMessage(Text.translatable("message.fmscraft.mugen_strings").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), true);
        p.playSoundToPlayer(SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 0.6f, 0.8f);
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1.2f, 0.5f);
    }

    private void sendControl(ServerPlayerEntity p, int ticks) {
        if (ServerPlayNetworking.canSend(p, MugenControlPayload.ID)) ServerPlayNetworking.send(p, new MugenControlPayload(ticks, random.nextLong()));
    }

    /** Puppet strings from his hands to whoever he has, {@code reach} of the way there. */
    private void strings(ServerWorld world, ServerPlayerEntity p, float reach) {
        Vec3d[] ends = {p.getEyePos().add(0, 0.35, 0), p.getPos().add(0, 1.2, 0).add(new Vec3d(-p.getRotationVector().z, 0, p.getRotationVector().x).multiply(0.35)),
                p.getPos().add(0, 1.2, 0).add(new Vec3d(p.getRotationVector().z, 0, -p.getRotationVector().x).multiply(0.35))};
        for (int s = 0; s < ends.length; s++) {
            Vec3d a = handPos(s != 1).add(0, 0.25, 0), b = ends[s];
            int n = (int) Math.max(6, a.distanceTo(b) * 2);
            for (int i = 0; i <= n; i++) {
                double u = i / (double) n;
                if (u > reach) break;
                Vec3d q = a.lerp(b, u).add(0, -Math.sin(u * Math.PI) * 0.4, 0);
                world.spawnParticles(i % 4 == 0 ? ParticleTypes.END_ROD : MugenHazards.VIOLET, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void tickControl(ServerWorld world) {
        if (controlled.isEmpty()) return;
        var it = controlled.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayerEntity p = fighter(e.getKey());
            int left = e.getValue() - 1;
            if (p == null || left <= 0) {
                it.remove();
                if (p != null) sendControl(p, 0);
                continue;
            }
            e.setValue(left);
            if (age % 2 == 0 && !isInvisible()) strings(world, p, 1f);
            if (left % 20 == 0) world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 0.6f, 1.6f);
        }
    }

    private void releaseControl(ServerWorld world) {
        for (UUID id : controlled.keySet()) if (world.getEntity(id) instanceof ServerPlayerEntity p) sendControl(p, 0);
        controlled.clear();
    }

    // ---- Black Hole (phase 2): five seconds of gathering the dark over his head, untouchable. Long enough to run ----

    /** The cast: a block of pure black forming over his head, growing to 2x2, spinning faster and faster. */
    public static final int HOLE_CAST = 100;
    /** Its heart hits once for this (then x1.15 in phase 2), the burst after for 6: shadow, so armour doesn't help but
     *  Protection does. Full Protection IV netherite takes about 12.6 of it in phase 2, left on 3.5 hearts. */
    public static final float HOLE_DAMAGE = 24;
    private float holeSpin;

    private void tickBlackHole(ServerWorld world) {
        int t = actionTicks, cast = HOLE_CAST;
        stand();
        Vec3d over = getPos().add(0, getHeight() + 1.7, 0);
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            untouchable = true;
            holeSpin = 0;
            setPoseState(POSE_HOLE);
            holeCore = MugenHazards.blockDisplay(world, Blocks.BLACK_CONCRETE.getDefaultState(), over);
            if (holeCore != null) {
                holeCore.setBrightness(new net.minecraft.entity.decoration.Brightness(0, 0));
                holeCore.setViewRange(10f);
                holeCore.setTransformation(MugenHazards.BlackHole.spinning(0, 0.05f));
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            for (ServerPlayerEntity p : audience(world)) {
                p.playSoundToPlayer(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundCategory.HOSTILE, 1.5f, 0.5f);
                p.sendMessage(Text.translatable("message.fmscraft.mugen_black_hole").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
            }
        }
        ServerPlayerEntity target = fighter(focus);
        if (target != null) faceTowards(target.getPos());
        if (t < cast) {
            float f = t / (float) cast, grow = f * f * (3 - 2 * f);
            holeSpin += 0.12f + 0.5f * f;
            float size = 0.1f + 1.9f * grow;
            if (holeCore != null) MugenHazards.place(holeCore, over, MugenHazards.BlackHole.spinning(holeSpin, size), 1);
            // Purple light whirling round it, faster and faster, and the dark drawn in to it from all round
            double r = size * 0.8 + 0.7;
            for (int i = 0; i < 10; i++) {
                double a = holeSpin * 1.5 + i * MathHelper.TAU / 10;
                world.spawnParticles(MugenHazards.VIOLET, over.x + Math.cos(a) * r, over.y + Math.sin(a * 2) * 0.4, over.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 4 + (int) (6 * f); i++) {
                Vec3d o = new Vec3d(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize().multiply(10 - 5 * f);
                Vec3d p = over.add(o);
                world.spawnParticles(i % 3 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SQUID_INK, p.x, p.y, p.z, 0, -o.x, -o.y, -o.z, 0.08 + 0.12 * f);
            }
            if (t % 3 == 0) world.spawnParticles(ParticleTypes.WITCH, over.x, over.y, over.z, 3, size * 0.6, size * 0.6, size * 0.6, 0.05);
            world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + 1, getZ(), 4, 0.6, 1.0, 0.6, 0);
            // How far it'll reach, marked on the ground round him, throbbing
            if (t % 4 == 0) MugenHazards.ring(world, MugenHazards.VIOLET, ground(getPos().add(0, 0.5, 0)), MugenHazards.BlackHole.PULL_RADIUS * (0.9 + 0.1 * MathHelper.sin(t * 0.3f)), 60, t * 0.05);
            // Getting stronger: it starts to tug at you even before it's thrown
            if (t > 50 && t % 10 == 0) {
                for (ServerPlayerEntity p : fighters) {
                    Vec3d in = over.subtract(p.getPos()).multiply(1, 0, 1);
                    double d = in.length();
                    if (d < 2 || d > 14) continue;
                    Vec3d v = in.multiply(0.12 / d);
                    p.setVelocity(v.x, 0.02, v.z);
                    p.velocityModified = true;
                }
            }
            int beat = Math.max(4, 16 - (int) (12 * f));
            if (t % beat == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0f, 0.5f + f * 0.6f);
            if (t == 40) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2.5f, 0.5f);
            if (t == 70) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2f, 0.6f);
            if (t == cast - 12) {
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.5f, 0.5f);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
            }
            return;
        }
        if (t == cast) {
            if (holeCore != null) holeCore.discard();
            holeCore = null;
            Vec3d from = getPos().add(0, 1.8, 0);
            Vec3d d = target != null ? target.getBoundingBox().getCenter().subtract(from) : Vec3d.fromPolar(0, getYaw());
            Vec3d flatDir = d.multiply(1, 0, 1).lengthSquared() > 1e-3 ? d.multiply(1, 0, 1).normalize() : Vec3d.fromPolar(0, getYaw());
            Vec3d aim = new Vec3d(d.x, d.y * 0.3, d.z).normalize();
            hazards.add(new MugenHazards.BlackHole(world, from.add(flatDir.multiply(2.4)), aim.multiply(0.38), HOLE_DAMAGE, holeSpin));
            untouchable = false;
            setPoseState(POSE_ORB);
            swingHand(Hand.MAIN_HAND);
            for (ServerPlayerEntity p : audience(world)) if (p.squaredDistanceTo(this) < 40 * 40) Cinematics.flash(p, 0.3f, 10);
        }
        if (t >= cast + 14) endAttack();
    }

    /** For tests: the players held fast by his Shadow Bind right now. */
    public List<ServerPlayerEntity> bound() {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (MugenHazards.Hazard h : hazards) if (h instanceof MugenHazards.Bound b) out.add(b.player);
        return out;
    }

    /** For tests: how long {@code player} is still on his strings (0: not). */
    public int controlLeft(ServerPlayerEntity player) {
        return controlled.getOrDefault(player.getUuid(), 0);
    }

    /** For tests: how many of his black holes and void spikes are out. */
    public int blackHoles() {
        int n = 0;
        for (MugenHazards.Hazard h : hazards) if (h instanceof MugenHazards.BlackHole) n++;
        return n;
    }

    public int voidSpikes() {
        int n = 0;
        for (MugenHazards.Hazard h : hazards) if (h instanceof MugenHazards.VoidSpike) n++;
        return n;
    }

    /** For tests: he hits as hard as he does in phase 2, without going through the transition. */
    public void hitLikePhaseTwo() {
        hitsLikePhaseTwo = true;
    }

    /** For tests: a void spike comes up under {@code at} (his own spots are random). */
    public void voidSpikeUnder(Vec3d at) {
        hazards.add(new MugenHazards.VoidSpike(ground(at.add(0, 0.5, 0)), 0, 22));
    }

    /** For tests: whether he's standing sheathed in his Iaido stance, waiting for you to hit him. */
    public boolean inIaido() {
        return action == Action.IAIDO && iaidoOpen;
    }

    // ---- The hazards, his aura, the world round the fight ----

    private void tickHazards(ServerWorld world) {
        for (MugenHazards.Hazard h : new ArrayList<>(hazards)) {
            if (!h.tick(world, this)) {
                h.remove();
                hazards.remove(h);
            }
        }
        if (bladeOut && action == Action.MOVE && !hasThrownBlade()) bladeReturns();
    }

    private boolean hasThrownBlade() {
        for (MugenHazards.Hazard h : hazards) if (h instanceof MugenHazards.Thrown th && th.kind == MugenHazards.Kind.BLADE) return true;
        return false;
    }

    /** Shadow drifting off him; in phase 2 a black aura, radiating out in rings. */
    private void tickAura(ServerWorld world) {
        double w = getWidth() * 0.6, h = getHeight();
        if (action == Action.ENTRANCE && actionTicks < MugenTimeline.RISEN) return;
        if (age % 3 == 0) world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + h * 0.5, getZ(), 1, w, h * 0.35, w, 0);
        if (!transitioned && action != Action.TRANSITION) return;
        for (int i = 0; i < 3; i++) {
            double a = age * 0.22 + i * MathHelper.TAU / 3, y = ((age + i * 10) % 30) / 30.0 * (h + 0.4);
            world.spawnParticles(MugenHazards.SHADOW, getX() + Math.cos(a) * 0.9, getY() + y, getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
        }
        world.spawnParticles(MugenHazards.SHADOW, getX(), getY() + h * 0.5, getZ(), 3, 0.7, h * 0.4, 0.7, 0);
        if (age % 2 == 0) world.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + h * 0.6, getZ(), 1, 0.3, 0.5, 0.3, 0.01);
        if (age % 4 == 0) world.spawnParticles(MugenHazards.CRIMSON, getX(), getY() + h * 0.9, getZ(), 1, 0.3, 0.3, 0.3, 0);
        if (age % 24 == 0 && isOnGround()) hazards.add(MugenHazards.Shockwave.visual(getPos(), 0.35, 4.5, MugenHazards.SHADOW));
    }

    /** Petals drifting down round everyone under the moon; in the eclipse, ash and red spores. */
    private void tickAmbience(ServerWorld world) {
        if (age % 5 != 0) return;
        for (ServerPlayerEntity p : fighters) {
            if (transitioned || action == Action.TRANSITION) {
                world.spawnParticles(p, ParticleTypes.ASH, false, p.getX(), p.getY() + 3, p.getZ(), 6, 10, 3, 10, 0);
                world.spawnParticles(p, ParticleTypes.CRIMSON_SPORE, false, p.getX(), p.getY() + 2, p.getZ(), 3, 10, 3, 10, 0);
            } else {
                world.spawnParticles(p, ParticleTypes.CHERRY_LEAVES, false, p.getX(), p.getY() + 6, p.getZ(), 2, 10, 2, 10, 0);
            }
        }
    }

    /** Out over the wall, or fallen through: the shadows drag you back. They bite. */
    private void catchStrays(ServerWorld world) {
        for (ServerPlayerEntity p : fighters) {
            Vec3d pos = p.getPos();
            if (LustArena.inBounds(pos)) {
                straying.remove(p.getUuid());
                continue;
            }
            int out = straying.merge(p.getUuid(), 1, Integer::sum);
            if (pos.y >= LustArena.MIN_Y && out < 10) continue;
            straying.remove(p.getUuid());
            Vec3d to = LustArena.returnSpot(pos);
            world.spawnParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
            p.teleport(world, to.x, to.y, to.z, p.getYaw(), p.getPitch());
            p.fallDistance = 0;
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
            crush(p, 4);
            world.spawnParticles(ParticleTypes.SQUID_INK, to.x, to.y + 1, to.z, 30, 0.4, 0.8, 0.4, 0.1);
            world.spawnParticles(MugenHazards.SHADOW, to.x, to.y + 0.2, to.z, 20, 0.6, 0.1, 0.6, 0);
            world.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 1.0f, 0.6f);
            p.sendMessage(Text.translatable("message.fmscraft.mugen_caught").formatted(Formatting.DARK_PURPLE), true);
        }
    }

    // ---- Dying ----

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld world) {
            clearAttacks(world);
            sky(world, 1, 0);
            for (ServerPlayerEntity p : audience(world)) Cinematics.flash(p, 0.7f, 30);
            Vec3d c = getPos().add(0, 1, 0);
            emit(world, ParticleTypes.FLASH, c, 1, 0, 0, 0, 0);
            emit(world, ParticleTypes.SQUID_INK, c, 200, 1.0, 1.4, 1.0, 0.3);
            emit(world, MugenHazards.SHADOW, c, 200, 1.5, 1.6, 1.5, 0);
            emit(world, ParticleTypes.LARGE_SMOKE, c, 80, 1.0, 1.5, 1.0, 0.1);
            emit(world, ParticleTypes.CHERRY_LEAVES, c.add(0, 2, 0), 120, 6, 3, 6, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_DEATH, SoundCategory.HOSTILE, 3.0f, 0.8f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_DEATH, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.5f);
            dataTracker.set(EYES, EYES_NONE);
        }
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Taken away mid-fight (the attempt failed): nothing of his stays behind, and nobody is left in the dark
        if (getWorld() instanceof ServerWorld world && reason.shouldDestroy()) {
            clearAttacks(world);
            if (blackoutTicks > 0) sky(world, transitioned ? 2 : 1, 0);
        }
        super.remove(reason);
    }

    private void clearAttacks(ServerWorld world) {
        for (MugenHazards.Hazard h : hazards) h.remove();
        hazards.clear();
        for (MugenCloneEntity c : clones) c.vanish(false);
        clones.clear();
        chain = null;
        smoke = null;
        if (blackoutTicks > 0) {
            blackoutTicks = 0;
            sky(world, transitioned ? 2 : 1, 0);
        }
        if (bladeOut) bladeReturns();
        if (sheathed) {
            sheathed = false;
            drawBlades();
        }
        iaidoOpen = false;
        releaseControl(world);
        if (holeCore != null) holeCore.discard();
        holeCore = null;
        shade = null;
        noClip = false;
        setAfterimages(false);
        if (isInvisible()) setInvisible(false);
    }

    // ---- Helpers ----

    /** A spot near {@code want} he fits, standing on something (looking a little up and down). Null if there's none. */
    private Vec3d safeSpot(Vec3d want) {
        BlockPos base = BlockPos.ofFloored(want);
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
            BlockPos at = base.up(dy);
            double floor = groundBelow(Vec3d.ofBottomCenter(at).add(0, 0.2, 0), 2);
            if (Double.isNaN(floor)) continue;
            Vec3d feet = new Vec3d(want.x, floor, want.z);
            Box box = getDimensions(getPose()).getBoxAt(feet).contract(1.0E-6);
            if (!getWorld().isSpaceEmpty(this, box)) continue;
            if (Arenas.sinOf(getWorld()) == Sin.LUST && !LustArena.inBounds(feet)) continue;
            return feet;
        }
        return null;
    }

    private void stand() {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
    }

    private void faceTowards(Vec3d pos) {
        if (MugenHazards.flatDistance(pos, getPos()) < 1e-3) return;
        faceYaw((float) (MathHelper.atan2(pos.z - getZ(), pos.x - getX()) * (180 / Math.PI)) - 90f);
    }

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
}
