package net.ragnar.fmscraft.boss.pride;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
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
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.ragnar.fmscraft.boss.BossSong;
import net.ragnar.fmscraft.boss.SinBossEntity;
import net.ragnar.fmscraft.compat.MagicSpells;
import net.ragnar.fmscraft.fight.Arenas;
import net.ragnar.fmscraft.fight.BossFights;
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.network.PrideSkyPayload;
import net.ragnar.fmscraft.sin.Sin;
import org.joml.Quaternionf;
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
 * LEONARD, the Sin of Pride: Leo, in his crown and shades, a great lightning bolt in his hand. Fast and showy, lightning
 * crackling off him all the time. He runs you down and jabs with the bolt, slips hits (and every arrow) in a blur of
 * afterimages (<b>Phase</b>), and on the beat picks one of:
 * <ul>
 *   <li><b>Discharge</b>: stands still, hands raised, and three rings of lightning roll out along the floor to 30 blocks.
 *       Jump them, or be shocked and paralysed.</li>
 *   <li><b>Zap</b>: Tome of Zap bolts flung every which way, very fast, hitting hard where they land.</li>
 *   <li><b>Lightning Strike</b>: fifteen bolts from the sky, one every 0.2 s, each marked a moment before.</li>
 *   <li><b>Almighty Pull</b>: lifts off the ground arms spread, gathering, drags everyone in from all round, then
 *       throws them all far away.</li>
 *   <li><b>Lightning Throw</b>: throws his bolt; where it lands the sky answers. It's back in his hand after.</li>
 *   <li><b>Zoom</b>: fifteen dashes across the plaza too fast to see, each line flickering a moment before, an
 *       electric trail behind; get in the way and you're thrown.</li>
 *   <li><b>Boomerang</b>: his bolt whirls out in a loop and comes back to him.</li>
 *   <li><b>Chain Lightning</b> (jumping from one of you to the next) and the <b>Thunder Clap</b> (up close).</li>
 * </ul>
 * Munch, the giant looking in over the rim, sometimes stops nodding and burns a ring into the plaza, then fires a
 * great beam into it. At half health the music cuts, Munch topples back into the clouds, LEONARD soars over the plaza
 * and grows to five blocks as the orchestra fades in, and a traffic cone falls out of the sky onto his head: he's
 * CONEMAN. Slow, far tougher, a storm round him, no more Zoom and no more slipping hits, but: the <b>Cone Slam</b>,
 * the <b>Storm Grid</b>, <b>Tesla Rods</b>, <b>Ball Lightning</b>, the <b>Cone Beam</b> and the <b>Stomp</b>.
 */
public class LeonardEntity extends SinBossEntity implements LeonardLook {
    public static final BossSong PHASE_ONE = new BossSong("fmscraft:music.pride.phase_one", LeonardTimeline.GROVE_BPM, LeonardTimeline.GROVE_FIRST_BEAT);
    public static final BossSong PHASE_TWO = new BossSong("fmscraft:music.pride.phase_two", LeonardTimeline.EPIC_BPM, LeonardTimeline.EPIC_FIRST_BEAT);

    // ---- Tuning knobs ----
    /** Full health, for 5-7 players; {@link LeonardTimeline#healthScale} scales it. LEONARD is its top half; CONEMAN gets all of it again. */
    public static final float MAX_HEALTH = 800;
    /** Damage he takes is multiplied by this (on top of his armor), and capped per hit. CONEMAN is much tougher. */
    public static final float DAMAGE_TAKEN = 0.85f, CONEMAN_TAKEN = 0.75f, MAX_HIT = 26f, CONEMAN_MAX_HIT = 22f;
    /** Every hit he deals is this share of its number (LEONARD's and CONEMAN's alike), rounded: cut by 10% twice. */
    public static final float DAMAGE_DEALT = 0.81f;
    /** CONEMAN's hits are this much harder again. */
    public static final float CONEMAN_DEALT = 1.1f;
    private static final double SPEED = 0.36, CONEMAN_SPEED = 0.22;
    private static final int REST = 14, CONEMAN_REST = 18;
    /** A hit on LEONARD while he's free: this chance he Phases out of it. Arrows he always slips. */
    public static final float DODGE = 0.22f;
    private static final int DODGE_COOLDOWN = 30;
    private static final float HEALTH_CAP = 1024f;
    /** Munch's beam: how long he gathers it (a long, fair warning), how long it burns, how wide, how hard. */
    public static final int BEAM_CHARGE = 80, BEAM_FIRE = 24;
    public static final double BEAM_RADIUS = 6;
    public static final float BEAM_DAMAGE = 32;
    /** Ticks between Munch's beams (random within). */
    private static final int BEAM_GAP_MIN = 420, BEAM_GAP_MAX = 620;
    /** CONEMAN's storm: anyone this close takes a jolt every so often. */
    private static final double AURA_RADIUS = 4.5;

    private static final TrackedData<Integer> POSE = DataTracker.registerData(LeonardEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SHADES = DataTracker.registerData(LeonardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> AFTERIMAGES = DataTracker.registerData(LeonardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CONED = DataTracker.registerData(LeonardEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    enum Action {
        ENTRANCE, MOVE, TRANSITION, PHASE, DASH,
        DISCHARGE, ZAP, STRIKES, PULL, THROW, ZOOM, BOOMERANG, CHAIN, CLAP,
        SLAM, GRID, RODS, BALLS, SWEEP, STOMP, DYING
    }

    private Action action = Action.ENTRANCE;
    private int actionTicks, rest, meleeCooldown, dodgeCooldown, step, songTicksNow, coneAge;
    private Action lastAttack;
    private final Map<Action, Integer> lastUsed = new EnumMap<>(Action.class);
    private final Map<UUID, Integer> lastPicked = new HashMap<>();
    private final Map<UUID, Integer> straying = new HashMap<>();
    private final Map<UUID, Integer> auraHit = new HashMap<>();
    private final Set<UUID> struck = new HashSet<>();
    private final List<LeonardHazards.Hazard> hazards = new ArrayList<>();
    private List<ServerPlayerEntity> fighters = List.of();
    private UUID focus;
    private int focusTicks;
    private boolean untouchable = true, scaled, heldStill, transitioned, killing, fromArena, songOn, epicOn, boltOut;
    private float toughness = 1f;
    private int startTick = -1;
    private Vec3d home = PrideArena.CENTER;

    // Munch
    private int munchCooldown = Integer.MAX_VALUE, munchQueued, munchState = PrideSkyPayload.MUNCH_HIDDEN, beamsFired;
    private LeonardHazards.MunchBeam beam;

    // The move in progress
    private Vec3d spot, dir, from;
    private List<ServerPlayerEntity> targets = List.of();
    private final List<Vec3d> spots = new ArrayList<>();
    private DisplayEntity.ItemDisplayEntity fallingCone;
    /** For tests: he hits as hard as CONEMAN. */
    private boolean hitsLikeConeman;

    /** Where he was the last few ticks, on the client: his afterimages are drawn there. */
    public static final int TRAIL = 12;
    private final double[][] trail = new double[TRAIL][4];
    private int trailHead, trailCount;

    public LeonardEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 1200;
        bossBar().setColor(BossBar.Color.YELLOW);
        setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LIGHTNING_BOLT));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_CAP)
                .add(EntityAttributes.GENERIC_ARMOR, 8)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8)
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
        builder.add(SHADES, false);
        builder.add(AFTERIMAGES, false);
        builder.add(CONED, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    protected Text bossBarName() {
        return Text.literal(transitioned ? "CONEMAN" : "LEONARD");
    }

    @Override
    protected BossSong songForPhase(int phase) {
        return phase == 1 ? PHASE_ONE : PHASE_TWO;
    }

    /** Grove St. Party waits for ten seconds of silence first ({@link #tickEntrance}). */
    @Override
    protected boolean startsSongAtOnce() {
        return false;
    }

    /** The orchestra fades in during the cone's fall ({@link #tickTransition}). */
    @Override
    protected boolean startsPhaseTwoSongAtOnce() {
        return false;
    }

    /** LEONARD's bar empties at half health; CONEMAN's fills up as he's crowned, then it's all his. */
    @Override
    protected float barPercent() {
        float max = getMaxHealth();
        if (action == Action.TRANSITION) return epicOn ? LeonardTimeline.barFill(songTicksNow) : 0f;
        if (!transitioned) return (getHealth() - max * 0.5f) / (max * 0.5f);
        return getHealth() / max;
    }

    // ---- What the client and the tests see ----

    @Override
    public int poseState() {
        return dataTracker.get(POSE);
    }

    @Override
    public boolean shadesLit() {
        return dataTracker.get(SHADES);
    }

    @Override
    public boolean afterimages() {
        return dataTracker.get(AFTERIMAGES);
    }

    @Override
    public boolean coned() {
        return dataTracker.get(CONED);
    }

    public boolean isUntouchable() {
        return untouchable || isInvisible() || action == Action.DYING;
    }

    public String currentAction() {
        return action.name();
    }

    /** For tests: what he's doing and for how long, and where. */
    public String describe() {
        return action + " for " + actionTicks + " ticks, age " + age + ", at " + getPos() + (isAlive() ? "" : ", dead") + (isRemoved() ? ", removed" : "");
    }

    /** True once the cone is on and CONEMAN is loose. */
    public boolean hasTransitioned() {
        return transitioned;
    }

    public float effectiveMaxHealth() {
        return getMaxHealth() / toughness;
    }

    /** For tests: what Munch is doing (a PrideSkyPayload.MUNCH_ state). */
    public int munchState() {
        return munchState;
    }

    /** For tests: whether the bolt is out of his hand. */
    public boolean boltThrown() {
        return boltOut;
    }

    /** For tests: between attacks he stands still, never slips a hit, and starts none of his own. */
    public void holdStill() {
        heldStill = true;
    }

    /** For tests: starts one of his attacks by name. */
    public void forceAttack(String name) {
        startAttack(Action.valueOf(name));
    }

    /** For tests: Munch fires his beam at {@code at} now. */
    public void munchBeamAt(Vec3d at) {
        if (getWorld() instanceof ServerWorld world) startBeam(world, at);
    }

    /** For tests: he's CONEMAN now (the cone, his size), without the transition. */
    public void goConeman() {
        dataTracker.set(CONED, true);
        dataTracker.set(SHADES, false);
        setScale(LeonardTimeline.CONE_SCALE);
        transitioned = true;
    }

    /** For tests: he hits as hard as CONEMAN does, without the transition. */
    public void hitLikeConeman() {
        hitsLikeConeman = true;
    }

    /** For tests: the players held paralysed right now. */
    public List<ServerPlayerEntity> paralysed() {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (LeonardHazards.Hazard h : hazards) if (h instanceof LeonardHazards.Paralysis p) out.add(p.player);
        return out;
    }

    /** For tests: how many hazards of his are out (bolts, rods, balls, beams...). */
    public int hazardCount() {
        return hazards.size();
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
    }

    private void setAfterimages(boolean on) {
        dataTracker.set(AFTERIMAGES, on);
    }

    private float scale() {
        return (float) getAttributeValue(EntityAttributes.GENERIC_SCALE);
    }

    private void setScale(float s) {
        var a = getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (a != null && Math.abs(a.getBaseValue() - s) > 1e-4) a.setBaseValue(s);
    }

    /** His hand, held a little out in front. */
    public Vec3d handPos(boolean right) {
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply((right ? -0.38 : 0.38) * scale());
        return getPos().add(0, getHeight() * 0.72, 0).add(forward.multiply(0.55 * scale())).add(side);
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

    void addHazard(LeonardHazards.Hazard hazard) {
        hazards.add(hazard);
    }

    /** A hit he deals: {@link #DAMAGE_DEALT} of its number (CONEMAN's {@link #CONEMAN_DEALT} harder), rounded, at least 1. */
    float dealt(float damage) {
        return dealt(damage, transitioned || hitsLikeConeman);
    }

    public static float dealt(float damage, boolean coneman) {
        return Math.max(1, Math.round(damage * DAMAGE_DEALT * (coneman ? CONEMAN_DEALT : 1f)));
    }

    /** Lightning: no shield stops it. */
    boolean shock(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().indirectMagic(this, this), dealt(damage));
    }

    /** His bolt, thrown: a shield held towards him stops it. */
    boolean strike(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobProjectile(this, this), dealt(damage));
    }

    /** His bolt in his hand, his fists, his feet: a shield stops them. */
    boolean smack(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobAttack(this), dealt(damage));
    }

    /** Throws {@code player} along {@code dir} (flattened), {@code horizontal} fast and {@code up} into the air. */
    void launch(ServerPlayerEntity player, Vec3d dir, double horizontal, double up) {
        Vec3d flat = dir.multiply(1, 0, 1);
        flat = flat.lengthSquared() > 1e-4 ? flat.normalize() : Vec3d.fromPolar(0, getYaw());
        player.setVelocity(flat.x * horizontal, up, flat.z * horizontal);
        player.velocityModified = true;
    }

    /** Paralysed for {@code ticks}: held where they stand. Paralysing someone already held holds them longer. */
    void paralyze(ServerPlayerEntity player, int ticks) {
        for (LeonardHazards.Hazard h : hazards) {
            if (h instanceof LeonardHazards.Paralysis p && p.player == player) {
                p.ticks = Math.max(p.ticks, p.age + ticks);
                return;
            }
        }
        hazards.add(new LeonardHazards.Paralysis(player, ticks));
        player.sendMessage(Text.translatable("message.fmscraft.leonard_paralysed").formatted(Formatting.YELLOW), true);
    }

    /** Everyone in the arena near him, fighting or not. */
    List<ServerPlayerEntity> audience(ServerWorld world) {
        return world.getPlayers(p -> p.squaredDistanceTo(this) < 200 * 200);
    }

    void audienceSound(SoundEvent sound, float volume, float pitch) {
        if (getWorld() instanceof ServerWorld world) {
            for (ServerPlayerEntity p : audience(world)) p.playSoundToPlayer(sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    /** The bolt is back in his hand, in a crackle. */
    void boltReturns() {
        if (!boltOut) return;
        boolean stillOut = false;
        for (LeonardHazards.Hazard h : hazards) stillOut |= h instanceof LeonardHazards.Bolt b && b.returns || h instanceof LeonardHazards.Boomerang;
        if (stillOut) return;
        boltOut = false;
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.LIGHTNING_BOLT));
        if (getWorld() instanceof ServerWorld world) {
            Vec3d h = handPos(true);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 20, 0.2, 0.2, 0.2, 0.2);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.HOSTILE, 1.4f, 1.4f);
        }
    }

    private void throwBolt() {
        boltOut = true;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    Vec3d ground(Vec3d pos) {
        double g = groundBelow(pos.add(0, 2.5, 0), 16);
        return Double.isNaN(g) ? pos : new Vec3d(pos.x, g, pos.z);
    }

    /** The sky and Munch, for everyone near. */
    private void sky(ServerWorld world, int munch, int ticks, Vec3d at) {
        munchState = munch;
        for (ServerPlayerEntity p : audience(world)) sky(p, transitioned ? 2 : 1, munch, ticks, at);
    }

    static void sky(ServerPlayerEntity player, int phase, int munch, int ticks, Vec3d at) {
        if (ServerPlayNetworking.canSend(player, PrideSkyPayload.ID)) {
            ServerPlayNetworking.send(player, new PrideSkyPayload(phase, munch, ticks, (float) at.x, (float) at.y, (float) at.z));
        }
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
        if (isUntouchable()) {
            if (attacker instanceof PlayerEntity) clink();
            return false;
        }
        if (getWorld() instanceof ServerWorld world && !transitioned && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
            // Every arrow: he's not where it was going
            if (action == Action.MOVE && !heldStill) dodge(world, attacker instanceof ServerPlayerEntity p ? p : null);
            else spark(world);
            return false;
        }
        if (getWorld() instanceof ServerWorld world && attacker instanceof ServerPlayerEntity player && !transitioned
                && action == Action.MOVE && !heldStill && dodgeCooldown <= 0 && random.nextFloat() < DODGE) {
            dodge(world, player);
            return false;
        }
        amount *= (transitioned ? CONEMAN_TAKEN : DAMAGE_TAKEN) * toughness;
        amount = Math.min(amount, transitioned ? CONEMAN_MAX_HIT : MAX_HIT);
        // Phase 1 can't be skipped: his health stops at half (setHealth), and at half nothing more gets through
        if (getPhase() == 1 && getHealth() <= getMaxHealth() * phaseTwoHealth()) amount = 0;
        if (amount <= 0) {
            clink();
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt && getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getBodyY(0.6), getZ(), 12, 0.3 * scale(), 0.5 * scale(), 0.3 * scale(), 0.2);
        }
        // The blow that would have finished CONEMAN left him on his last breath instead (setHealth)
        if (pendingDeath && getWorld() instanceof ServerWorld world) startDying(world);
        return hurt;
    }

    private void clink() {
        getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0f, 1.5f);
    }

    /** A flicker aside, too small to call a Phase: the arrow passes through where he was. */
    private void spark(ServerWorld world) {
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getBodyY(0.5), getZ(), 14, 0.4, 0.6, 0.4, 0.2);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.HOSTILE, 0.6f, 2.0f);
    }

    @Override
    public void setHealth(float health) {
        if (!killing && action != null && getPhase() == 1) health = Math.max(health, getMaxHealth() * phaseTwoHealth());
        if (!killing && action != null && getPhase() == 2 && action != Action.DYING && health <= 0) {
            health = 1f;
            pendingDeath = true;
        }
        super.setHealth(health);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return action == Action.MOVE && !transitioned;
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
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, bubble.getX(), bubble.getY(), bubble.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
            bubble.discard();
        }
    }

    @Override
    protected void tickPhase(int phase, int songTicks) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        songTicksNow = songTicks;
        double reach = Arenas.sinOf(world) == Sin.PRIDE ? 160 : 24;
        fighters = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < reach * reach);
        if (!scaled) scaleHealth(world);
        if (rest > 0) rest--;
        if (meleeCooldown > 0) meleeCooldown--;
        if (dodgeCooldown > 0) dodgeCooldown--;
        actionTicks++;
        switch (action) {
            case ENTRANCE -> tickEntrance(world, songTicks);
            case MOVE -> tickMove(world);
            case TRANSITION -> tickTransition(world, songTicks);
            case PHASE -> tickPhaseStep(world);
            case DASH -> tickDash(world);
            case DISCHARGE -> tickDischarge(world);
            case ZAP -> tickZap(world);
            case STRIKES -> tickStrikes(world);
            case PULL -> tickPull(world);
            case THROW -> tickThrow(world);
            case ZOOM -> tickZoom(world);
            case BOOMERANG -> tickBoomerang(world);
            case CHAIN -> tickChain(world);
            case CLAP -> tickClap(world);
            case SLAM -> tickSlam(world);
            case GRID -> tickGrid(world);
            case RODS -> tickRods(world);
            case BALLS -> tickBalls(world);
            case SWEEP -> tickSweep(world);
            case STOMP -> tickStomp(world);
            case DYING -> tickDying(world);
        }
        if (action == Action.DYING) return;
        tickMunch(world);
        tickHazards(world);
        if (!isInvisible()) tickAura(world);
        if (Arenas.sinOf(world) == Sin.PRIDE && action != Action.ENTRANCE) {
            tickAmbience(world);
            catchStrays(world);
        }
    }

    private void scaleHealth(ServerWorld world) {
        scaled = true;
        int players = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 200 * 200).size();
        float wanted = MAX_HEALTH * LeonardTimeline.healthScale(players);
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
        double near = nearestDistance();
        int band = near < 5.5 ? 0 : near > 15 ? 2 : 1;
        int crowd = 0;
        for (ServerPlayerEntity p : fighters) if (p.distanceTo(this) < 7 * scale()) crowd++;
        if (!transitioned) {
            offer(options, weights, Action.DISCHARGE, new int[]{4, 3, 2}[band] + (crowd >= 3 ? 2 : 0), 140);
            offer(options, weights, Action.ZAP, new int[]{1, 3, 4}[band], 120);
            offer(options, weights, Action.STRIKES, new int[]{2, 3, 3}[band], 180);
            offer(options, weights, Action.PULL, new int[]{1, 2, 4}[band], 360);
            offer(options, weights, Action.THROW, new int[]{1, 3, 4}[band], 100);
            if (munchState != PrideSkyPayload.MUNCH_CHARGING && munchState != PrideSkyPayload.MUNCH_FIRING) {
                offer(options, weights, Action.ZOOM, new int[]{2, 3, 3}[band], 380);
            }
            offer(options, weights, Action.BOOMERANG, new int[]{1, 3, 3}[band], 110);
            offer(options, weights, Action.CHAIN, fighters.size() > 1 ? 3 : 1, 160);
            offer(options, weights, Action.CLAP, new int[]{5, 1, 0}[band] + (crowd >= 2 ? 2 : 0), 70);
        } else {
            offer(options, weights, Action.DISCHARGE, new int[]{3, 3, 2}[band] + (crowd >= 3 ? 2 : 0), 200);
            offer(options, weights, Action.ZAP, new int[]{1, 2, 3}[band], 180);
            offer(options, weights, Action.STRIKES, new int[]{2, 3, 3}[band], 220);
            offer(options, weights, Action.PULL, new int[]{1, 2, 3}[band], 420);
            offer(options, weights, Action.THROW, new int[]{1, 3, 4}[band], 140);
            offer(options, weights, Action.BOOMERANG, new int[]{1, 2, 3}[band], 160);
            offer(options, weights, Action.CHAIN, fighters.size() > 1 ? 3 : 1, 200);
            offer(options, weights, Action.SLAM, new int[]{1, 3, 4}[band], 200);
            offer(options, weights, Action.GRID, 3, 420);
            offer(options, weights, Action.RODS, new int[]{2, 3, 2}[band], 460);
            offer(options, weights, Action.BALLS, new int[]{1, 2, 3}[band], 380);
            offer(options, weights, Action.SWEEP, new int[]{3, 3, 1}[band], 240);
            offer(options, weights, Action.STOMP, new int[]{6, 1, 0}[band] + (crowd >= 2 ? 2 : 0), 60);
        }
        if (options.isEmpty()) return near < 5 ? (transitioned ? Action.STOMP : Action.CLAP) : Action.THROW;
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
        spots.clear();
        if (attack != Action.TRANSITION && attack != Action.PHASE) {
            lastAttack = attack;
            lastUsed.put(attack, age);
        }
        getNavigation().stop();
    }

    private void endAttack() {
        endAttack(transitioned ? CONEMAN_REST : REST);
    }

    private void endAttack(int restTicks) {
        action = Action.MOVE;
        actionTicks = 0;
        setPoseState(POSE_IDLE);
        setNoGravity(false);
        noClip = false;
        setAfterimages(false);
        if (isInvisible()) setInvisible(false);
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

    private Vec3d arenaCenter(ServerWorld world) {
        return Arenas.sinOf(world) == Sin.PRIDE ? PrideArena.CENTER : home;
    }

    // ---- The arrival: the wheel fades, ten seconds of nothing, the song, Munch rises, and on the drop he strikes ----

    private void tickEntrance(ServerWorld world, int songTicks) {
        if (startTick < 0) {
            startTick = age;
            fromArena = Arenas.sinOf(world) == Sin.PRIDE && getY() > PrideArena.FLOOR_Y + 10;
            home = fromArena ? PrideArena.CENTER : ground(getPos());
            untouchable = true;
            if (fromArena) {
                setInvisible(true);
                setNoGravity(true);
                noClip = true;
                bossBar().setVisible(false);
            } else {
                // Summoned anywhere else (tests): the song at once, and he strikes down straight away
                playSong(PHASE_ONE, 0);
                songOn = true;
                return;
            }
        }
        if (!songOn) {
            setVelocity(Vec3d.ZERO);
            int t = actionTicks;
            if (t == LeonardTimeline.REVEAL_TICKS) for (ServerPlayerEntity p : audience(world)) Cinematics.unfreeze(p);
            // Nothing is happening. Only the wind up here
            if (t == LeonardTimeline.REVEAL_TICKS + 20 || t == LeonardTimeline.REVEAL_TICKS + 120) {
                audienceSound(SoundEvents.ITEM_ELYTRA_FLYING, 0.08f, 0.5f);
            }
            if (t >= LeonardTimeline.SILENCE) {
                playSong(PHASE_ONE, 0);
                songOn = true;
                // Munch comes up out of the clouds with the song (the client times his rise from it)
                sky(world, PrideSkyPayload.MUNCH_WATCHING, 0, Vec3d.ZERO);
            }
            return;
        }
        int s = songTicks;
        int strikeAt = fromArena ? LeonardTimeline.STRIKE : 1;
        if (s < strikeAt) {
            setVelocity(Vec3d.ZERO);
            return;
        }
        if (s == strikeAt) strikeDown(world, fromArena);
        if (s >= strikeAt + LeonardTimeline.READY_AFTER) ready();
    }

    /** On the drop: out of nowhere, a bolt from the sky, and he's standing in it. */
    private void strikeDown(ServerWorld world, boolean big) {
        setInvisible(false);
        setNoGravity(false);
        noClip = false;
        refreshPositionAndAngles(home.x, home.y, home.z, getYaw(), 0f);
        ServerPlayerEntity near = nearestFighter();
        if (near != null) faceTowards(near.getPos());
        else faceYaw(90f);
        setVelocity(Vec3d.ZERO);
        setPoseState(POSE_LAND);
        dataTracker.set(SHADES, true);
        bossBar().setVisible(true);
        Vec3d c = getPos();
        LeonardHazards.lightning(world, c);
        if (big) {
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * MathHelper.TAU;
                LeonardHazards.lightning(world, c.add(Math.cos(a) * 3, 0, Math.sin(a) * 3));
            }
        }
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.unfreeze(p);
            if (big) Cinematics.flash(p, 0.95f, 34);
            double d = p.getPos().distanceTo(c);
            if (d < 14 && fighters.contains(p)) launch(p, p.getPos().subtract(c), 1.6 * (1 - d / 16), 0.55);
            p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.8f);
            p.playSoundToPlayer(SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.5f, 0.9f);
            p.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.6f, 0.7f);
        }
        addHazard(new LeonardHazards.Ring(c, 1.2, 16, 0, 0));
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 300, 2, 1.5, 2, 0.8);
        world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 2, 0, 0, 0, 0);
        world.spawnParticles(LeonardHazards.WHITE, c.x, c.y + 1, c.z, 120, 2.5, 1.5, 2.5, 0);
    }

    private void ready() {
        untouchable = false;
        bossBar().setVisible(true);
        // His bigger moves aren't ready from the start
        for (Action big : new Action[]{Action.ZOOM, Action.PULL, Action.STRIKES}) lastUsed.put(big, age - 140);
        munchCooldown = 320;
        endAttack(8);
    }

    // ---- Half health: the music cuts, Munch falls, he soars and grows, and the cone comes down on the orchestra ----

    private void tickTransition(ServerWorld world, int songTicks) {
        int t = actionTicks;
        if (t == 1) {
            untouchable = true;
            clearAttacks(world);
            getNavigation().stop();
            setVelocity(Vec3d.ZERO);
            setNoGravity(true);
            setPoseState(POSE_HOVER);
            home = Arenas.sinOf(world) == Sin.PRIDE ? PrideArena.CENTER : ground(getPos());
            sky(world, PrideSkyPayload.MUNCH_FALLING, LeonardTimeline.MUNCH_FALL, Vec3d.ZERO);
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.freeze(p);
                Cinematics.flash(p, 0.6f, 20);
                p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0f, 0.5f);
            }
        }
        if (t == 30 && munchState == PrideSkyPayload.MUNCH_FALLING) {
            // Somewhere far below, a great splash of cloud
            audienceSound(SoundEvents.ENTITY_GENERIC_SPLASH, 1.2f, 0.3f);
            audienceSound(SoundEvents.ENTITY_WARDEN_DEATH, 0.6f, 0.4f);
        }
        if (!epicOn) {
            hover(world, home.add(0, 0.1, 0), false);
            if (t >= LeonardTimeline.EPIC_AT) {
                epicOn = true;
                playSong(PHASE_TWO, LeonardTimeline.EPIC_FADE);
            }
            return;
        }
        // From here on, timed by the orchestra itself
        int s = songTicks;
        if (s < LeonardTimeline.CONE_HIT) {
            Vec3d at = home.add(0, LeonardTimeline.soarHeight(s), 0);
            setScale(LeonardTimeline.growScale(s));
            hover(world, at, s > LeonardTimeline.GROW_START);
            if (s % 18 == 5) {
                double a = random.nextDouble() * MathHelper.TAU, r = 5 + random.nextDouble() * 10;
                LeonardHazards.lightning(world, ground(home.add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            }
            if (s == LeonardTimeline.GROW_START) {
                audienceSound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.6f, 0.5f);
                audienceSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0f, 0.5f);
            }
            if (s > LeonardTimeline.GROW_START && s < LeonardTimeline.GROW_END && s % 12 == 0) {
                audienceSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.6f + (s - LeonardTimeline.GROW_START) * 0.008f);
            }
            if (s == LeonardTimeline.CONE_APPEAR) {
                fallingCone = LeonardHazards.itemDisplay(world, new ItemStack(ModItems.TRAFFIC_CONE), conePos(s));
                if (fallingCone != null) {
                    fallingCone.setViewRange(10f);
                    fallingCone.setTransformation(coneShape());
                }
                audienceSound(SoundEvents.ENTITY_PHANTOM_SWOOP, 2.0f, 0.4f);
                audienceSound(SoundEvents.ITEM_ELYTRA_FLYING, 0.6f, 0.6f);
            }
            if (fallingCone != null) {
                LeonardHazards.place(fallingCone, conePos(s), coneShape());
                Vec3d p = conePos(s);
                world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 2, 0.5, 0.5, 0.5, 0.02);
            }
            return;
        }
        if (s == LeonardTimeline.CONE_HIT) crown(world);
        if (s == LeonardTimeline.CONE_HIT + LeonardTimeline.FREE_AFTER) for (ServerPlayerEntity p : audience(world)) Cinematics.unfreeze(p);
        stand();
        if (s >= LeonardTimeline.CONE_HIT + LeonardTimeline.BAR_FILL) {
            untouchable = false;
            for (Action big : new Action[]{Action.GRID, Action.RODS, Action.BALLS}) lastUsed.put(big, age - 260);
            endAttack(10);
        }
    }

    /** Where the falling cone hangs, {@code s} ticks into the orchestra: over his neck, coming down. */
    private Vec3d conePos(int s) {
        float k = scale();
        // The cone's model is centred on the display: lift it half its height so its rim sits on his neck
        return getPos().add(0, 1.5 * k - 0.02 * k + 0.5 * k + LeonardTimeline.coneHeight(s), 0);
    }

    private AffineTransformation coneShape() {
        float k = scale();
        Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(-getBodyYaw()));
        return new AffineTransformation(new Vector3f(), q, new Vector3f(k, k, k), new Quaternionf());
    }

    /** Hanging in the air over the plaza, lightning crackling round him. */
    private void hover(ServerWorld world, Vec3d at, boolean growing) {
        setVelocity(Vec3d.ZERO);
        setPosition(at.x, at.y, at.z);
        faceYaw(getYaw() + 0.8f);
        setPoseState(POSE_HOVER);
        Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, growing ? 10 : 5, getWidth(), getHeight() * 0.4, getWidth(), 0.2);
        if (age % 3 == 0) world.spawnParticles(LeonardHazards.YELLOW, c.x, c.y, c.z, 3, getWidth(), getHeight() * 0.4, getWidth(), 0);
        if (age % 6 == 0) LeonardHazards.arc(world, c, ground(c.add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8)), ParticleTypes.ELECTRIC_SPARK, 0.6, 0.6);
    }

    /** The cone lands on his head with a great boom, and he's CONEMAN. */
    private void crown(ServerWorld world) {
        if (fallingCone != null) {
            fallingCone.discard();
            fallingCone = null;
        }
        setScale(LeonardTimeline.CONE_SCALE);
        dataTracker.set(CONED, true);
        dataTracker.set(SHADES, false);
        setNoGravity(false);
        refreshPositionAndAngles(home.x, home.y, home.z, getYaw(), 0f);
        setVelocity(Vec3d.ZERO);
        setPoseState(POSE_SLAM);
        transitioned = true;
        getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(CONEMAN_SPEED);
        getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(14);
        getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).setBaseValue(8);
        setHealth(getMaxHealth());
        bossBar().setName(Text.literal("CONEMAN"));
        bossBar().setColor(BossBar.Color.RED);
        bossBar().setVisible(true);
        sky(world, PrideSkyPayload.MUNCH_HIDDEN, 0, Vec3d.ZERO);
        Vec3d c = getPos();
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.flash(p, 1f, 40);
            double d = p.getPos().distanceTo(c);
            if (d < 18 && fighters.contains(p)) launch(p, p.getPos().subtract(c), 1.8 * (1 - d / 20), 0.6);
            p.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.5f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.5f);
        }
        LeonardHazards.lightning(world, c);
        addHazard(new LeonardHazards.Ring(c, 1.0, 22, 0, 0));
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 3, 2, 0.3, 2, 0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y + 0.5, c.z, 120, 4, 0.5, 4, 0.3);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 2, c.z, 200, 3, 2, 3, 0.6);
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
        double reach = transitioned ? 4.4 : 2.6;
        double d = distanceTo(target);
        if (age % 6 == 0 || getNavigation().isIdle()) {
            if (d > reach - 0.4) getNavigation().startMovingTo(target, 1.0);
            else getNavigation().stop();
        }
        // Too far, or can't get there: LEONARD doesn't walk, he's there
        if (!transitioned && (d > 20 || d > 6 && getNavigation().isIdle()) && notUsedFor(Action.DASH, 50)) {
            startAttack(Action.DASH);
            return;
        }
        if (transitioned && d > 24 && notUsedFor(Action.SLAM, 120)) {
            startAttack(Action.SLAM);
            return;
        }
        if (meleeCooldown <= 0 && d < reach && Math.abs(target.getY() - getY()) < 2.5 * scale()) {
            boolean left = random.nextBoolean();
            setPoseState(left ? POSE_PUNCH_B : POSE_PUNCH_A);
            swingHand(left ? Hand.OFF_HAND : Hand.MAIN_HAND);
            if (smack(target, transitioned ? 14 : 8)) {
                launch(target, target.getPos().subtract(getPos()), transitioned ? 1.2 : 0.6, transitioned ? 0.5 : 0.3);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
            }
            world.playSound(null, getX(), getY(), getZ(), transitioned ? SoundEvents.ENTITY_IRON_GOLEM_ATTACK : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                    SoundCategory.HOSTILE, 1.3f, transitioned ? 0.6f : 1.2f);
            meleeCooldown = transitioned ? 24 : 13;
        } else if (meleeCooldown < (transitioned ? 14 : 8) && poseState() != POSE_IDLE) {
            setPoseState(POSE_IDLE);
        }
        if (!transitioned && age % 2 == 0) dodgeArrows(world);
    }

    /** An arrow on its way to him and close: he's gone from in front of it. */
    private void dodgeArrows(ServerWorld world) {
        if (dodgeCooldown > 0) return;
        for (PersistentProjectileEntity arrow : world.getEntitiesByClass(PersistentProjectileEntity.class, getBoundingBox().expand(9),
                a -> a.getOwner() instanceof PlayerEntity && a.getVelocity().lengthSquared() > 0.09)) {
            Vec3d to = getBoundingBox().getCenter().subtract(arrow.getPos());
            if (to.normalize().dotProduct(arrow.getVelocity().normalize()) > 0.85) {
                dodge(world, arrow.getOwner() instanceof ServerPlayerEntity p ? p : null);
                return;
            }
        }
    }

    // ---- Phase: a few blocks aside in a blur of afterimages; it hurts nobody ----

    private void dodge(ServerWorld world, ServerPlayerEntity attacker) {
        dodgeCooldown = DODGE_COOLDOWN;
        if (attacker != null) focus = attacker.getUuid();
        startAttack(Action.PHASE);
        tickPhaseStep(world);
    }

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
                    if (at != null) {
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
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.HOSTILE, 0.8f, 2.0f);
            if (target != null) faceTowards(target.getPos());
        }
        if (spot != null && t <= 4) {
            Vec3d at = from.lerp(spot, Math.min(1, t / 4.0));
            setVelocity(Vec3d.ZERO);
            setPosition(at.x, at.y, at.z);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 4, 0.2, 0.5, 0.2, 0.05);
        }
        if (t == 10) setAfterimages(false);
        if (t >= 10) endAttack(5);
    }

    // ---- Dash: a single Zoom to whoever he's after, when they're far ----

    private void tickDash(ServerWorld world) {
        int t = actionTicks;
        ServerPlayerEntity target = fighter(focus);
        if (t == 1) {
            if (target == null) target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            Vec3d toward = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            Vec3d at = safeSpot(target.getPos().subtract(toward.normalize().multiply(2.5)));
            if (at == null) {
                endAttack();
                return;
            }
            from = getPos();
            spot = at;
            setPoseState(POSE_DASH);
            faceTowards(spot);
            LeonardHazards.arc(world, from.add(0, 0.4, 0), spot.add(0, 0.4, 0), ParticleTypes.ELECTRIC_SPARK, 0.8, 0.1);
        }
        if (t >= 4 && t <= 6 && spot != null) {
            setAfterimages(true);
            Vec3d at = from.lerp(spot, (t - 3) / 3.0);
            setPosition(at.x, at.y, at.z);
            setVelocity(Vec3d.ZERO);
            if (t == 4) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_2.value(), SoundCategory.HOSTILE, 1.0f, 1.6f);
            if (t == 6) addHazard(new LeonardHazards.Trail(from, spot, 26));
        }
        if (t >= 12) endAttack(4);
    }

    // ---- Discharge: hands raised, rings of lightning rolling out along the floor. Jump them ----

    private void tickDischarge(ServerWorld world) {
        int t = actionTicks;
        int waves = transitioned ? 5 : 3, gap = transitioned ? 14 : 18, first = transitioned ? 20 : 16;
        stand();
        if (t == 1) {
            setPoseState(POSE_RAISE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 1.4f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 1.0f, 1.6f);
        }
        if (t < first) {
            // Sparks drawn up into his hands
            Vec3d h = getPos().add(0, getHeight() + 0.4 * scale(), 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 6, 0.4 * scale(), 0.3, 0.4 * scale(), 0.1);
            if (t % 4 == 0) LeonardHazards.ring(world, LeonardHazards.YELLOW, ground(getPos()), 2.5 * scale() * (1 - t / (double) first) + 0.5, 20, t * 0.3);
        }
        for (int w = 0; w < waves; w++) {
            if (t == first + w * gap) {
                Vec3d c = ground(getPos());
                addHazard(new LeonardHazards.Ring(c, transitioned ? 0.85 : 0.75, 30, transitioned ? 8 : 6, 30));
                setPoseState(w % 2 == 0 ? POSE_SLAM : POSE_RAISE);
                world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 2.0f, 0.8f);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, 1.5f, 1.4f);
            }
        }
        if (t >= first + (waves - 1) * gap + 10) endAttack();
    }

    // ---- Zap: Tome of Zap bolts flung every which way, very fast ----

    private void tickZap(ServerWorld world) {
        int t = actionTicks, count = transitioned ? 26 : 18, every = transitioned ? 1 : 2, first = 14;
        stand();
        if (t == 1) {
            setPoseState(POSE_CHANNEL);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.4f, 2.0f);
        }
        if (t < first) {
            Vec3d h = handPos(true);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 5, 0.2, 0.2, 0.2, 0.2);
            ServerPlayerEntity near = nearestFighter();
            if (near != null) turnTowards(near.getPos(), 12f);
            return;
        }
        int k = (t - first) / every;
        if ((t - first) % every == 0 && k < count) {
            setPoseState(k % 2 == 0 ? POSE_CHANNEL : POSE_THROW);
            Vec3d h = handPos(true);
            Vec3d aim;
            if (!fighters.isEmpty() && random.nextFloat() < 0.55f) {
                ServerPlayerEntity p = fighters.get(random.nextInt(fighters.size()));
                aim = p.getBoundingBox().getCenter().subtract(h).normalize();
                float yaw = (float) (random.nextGaussian() * 6), pitch = (float) (random.nextGaussian() * 4);
                aim = aim.rotateY((float) Math.toRadians(yaw)).add(0, Math.sin(Math.toRadians(pitch)), 0).normalize();
            } else {
                double a = random.nextDouble() * MathHelper.TAU, p = Math.toRadians(-20 + random.nextDouble() * 22);
                aim = new Vec3d(Math.cos(a) * Math.cos(p), Math.sin(p), Math.sin(a) * Math.cos(p));
            }
            faceTowards(h.add(aim));
            zap(world, h, aim, 34, transitioned ? 10 : 9);
        }
        if (t >= first + count * every + 8) endAttack();
    }

    /** One zap from {@code from} along {@code aim}: whatever's first in its way is struck. */
    private void zap(ServerWorld world, Vec3d from, Vec3d aim, double range, float damage) {
        Vec3d end = from.add(aim.multiply(range));
        BlockHitResult block = LeonardHazards.wall(world, from, end);
        boolean hitBlock = block.getType() == HitResult.Type.BLOCK;
        if (hitBlock) end = block.getPos();
        ServerPlayerEntity hit = LeonardHazards.firstHit(this, from, end, 0.25, null);
        Vec3d to = hit != null ? hit.getBoundingBox().getCenter() : end;
        int kind = hit != null ? MagicSpells.ZAP_ENTITY : hitBlock ? MagicSpells.ZAP_BLOCK : MagicSpells.ZAP_MISS;
        Vec3d normal = kind == MagicSpells.ZAP_BLOCK ? Vec3d.of(block.getSide().getVector()) : aim.multiply(-1);
        if (!MagicSpells.zap(world, getId(), from, to, kind, normal)) {
            LeonardHazards.arc(world, from, to, ParticleTypes.ELECTRIC_SPARK, 0.4, 0.25);
            LeonardHazards.arc(world, from, to, LeonardHazards.YELLOW, 0.8, 0.15);
        }
        SoundEvent cast = MagicSpells.zapSound();
        world.playSound(null, from.x, from.y, from.z, cast != null ? cast : SoundEvents.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.HOSTILE, 0.9f, 1.0f + random.nextFloat() * 0.3f);
        world.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 0.7f, 1.7f);
        if (hit != null && shock(hit, damage)) {
            launch(hit, aim, 0.5, 0.2);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 20, 0.3, 0.5, 0.3, 0.3);
        }
    }

    // ---- Lightning Strike: bolts out of the sky one after another, each marked a moment before ----

    private void tickStrikes(ServerWorld world) {
        int t = actionTicks;
        int n = LeonardTimeline.strikes(fighters.size(), transitioned);
        int gap = transitioned ? 3 : LeonardTimeline.STRIKE_GAP;
        stand();
        if (t == 1) {
            setPoseState(POSE_RAISE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.0f, 0.8f);
            for (ServerPlayerEntity p : fighters) p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 0.8f, 1.4f);
        }
        int first = 6;
        if (t >= first && (t - first) % gap == 0) {
            int i = (t - first) / gap;
            if (i < n) {
                Vec3d at;
                if (i % 3 != 2 && !fighters.isEmpty()) {
                    ServerPlayerEntity p = fighters.get(i % fighters.size());
                    // Where they are now: keep moving
                    at = p.getPos().add((random.nextDouble() - 0.5) * 2.5, 0, (random.nextDouble() - 0.5) * 2.5);
                } else {
                    double a = random.nextDouble() * MathHelper.TAU, r = random.nextDouble() * 26;
                    at = arenaCenter(world).add(Math.cos(a) * r, 0, Math.sin(a) * r);
                }
                addHazard(new LeonardHazards.Strike(ground(at), LeonardTimeline.STRIKE_MARK, transitioned ? 2.8 : 2.4, transitioned ? 13 : 12, true));
            }
        }
        if (t >= first + n * gap + LeonardTimeline.STRIKE_MARK + 4) endAttack();
    }

    // ---- Almighty Pull: up off the ground gathering, everyone dragged in, then thrown far ----

    private void tickPull(ServerWorld world) {
        int t = actionTicks;
        int charge = 30, pull = transitioned ? 44 : 38, hold = 8;
        Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
        if (t == 1) {
            setPoseState(POSE_SPREAD);
            setNoGravity(true);
            home = ground(getPos());
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 1.2f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (t <= charge + pull + hold) {
            double lift = Math.min(1, t / 20.0) * 1.6 * scale();
            setVelocity(Vec3d.ZERO);
            setPosition(home.x, home.y + lift, home.z);
        }
        if (t <= charge) {
            // Light drawn in to him from all round, the ring closing
            double r = 26 * (1 - t / (double) charge) + 3;
            LeonardHazards.ring(world, LeonardHazards.YELLOW, home, r, (int) (20 + r * 3), t * 0.15);
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * MathHelper.TAU;
                Vec3d p = home.add(Math.cos(a) * 20, 0.5 + random.nextDouble() * 3, Math.sin(a) * 20);
                Vec3d in = c.subtract(p);
                world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, in.x, in.y, in.z, 0.09);
            }
            return;
        }
        if (t <= charge + pull) {
            if (t == charge + 1) {
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_INHALE, SoundCategory.HOSTILE, 3.0f, 0.5f);
                setPoseState(POSE_CLAP_WIND);
            }
            for (ServerPlayerEntity p : fighters) {
                Vec3d to = home.subtract(p.getPos());
                double d = to.horizontalLength();
                if (d > 34 || d < 2.2) continue;
                Vec3d flat = to.multiply(1, 0, 1).normalize();
                double drag = Math.min(transitioned ? 0.62 : 0.52, 0.14 + d * 0.016);
                p.setVelocity(flat.x * drag, Math.max(p.getVelocity().y, 0.02), flat.z * drag);
                p.velocityModified = true;
                if (t % 3 == 0) LeonardHazards.arc(world, c, p.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 0.9, 0.5);
            }
            if (t % 8 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 2.0f, 1.6f);
            return;
        }
        if (t <= charge + pull + hold) {
            setPoseState(POSE_CLAP);
            world.spawnParticles(LeonardHazards.WHITE, c.x, c.y, c.z, 10, 0.6, 0.6, 0.6, 0);
            return;
        }
        if (t == charge + pull + hold + 1) {
            setPoseState(POSE_SPREAD);
            setNoGravity(false);
            Vec3d g = home;
            for (ServerPlayerEntity p : fighters) {
                double d = p.getPos().distanceTo(g);
                if (d > 32) continue;
                if (d < 7.5) shock(p, transitioned ? 12 : 8);
                launch(p, p.getPos().subtract(g), Math.max(0.8, (transitioned ? 3.4 : 3.0) - d * 0.06), 0.75);
            }
            for (ServerPlayerEntity p : audience(world)) {
                if (p.getPos().distanceTo(g) < 30) Cinematics.flash(p, 0.45f, 12);
            }
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, g.x, g.y + 1, g.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, g.x, g.y + 1, g.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, g.x, g.y + 1, g.z, 200, 3, 1.5, 3, 1.2);
            addHazard(new LeonardHazards.Ring(g, 1.4, 30, 0, 0));
            world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.8f);
            world.playSound(null, g.x, g.y, g.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.5f, 1.0f);
        }
        if (t >= charge + pull + hold + 16) endAttack();
    }

    // ---- Lightning Throw: the bolt, thrown hard; where it lands the sky answers ----

    private void tickThrow(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            targets = new ArrayList<>(List.of());
            ServerPlayerEntity p = pick(null);
            if (p == null || boltOut) {
                endAttack();
                return;
            }
            focus = p.getUuid();
            setPoseState(POSE_THROW_BACK);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 1.2f, 1.4f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (target != null && t < 14) {
            faceTowards(target.getPos());
            Vec3d h = handPos(true).add(0, 0.5 * scale(), 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 4, 0.2, 0.3, 0.2, 0.1);
        }
        if (t == 14) {
            setPoseState(POSE_THROW);
            swingHand(Hand.MAIN_HAND);
            Vec3d h = handPos(true);
            Vec3d aimAt = target != null ? target.getBoundingBox().getCenter().add(target.getVelocity().multiply(6)) : h.add(Vec3d.fromPolar(0, getYaw()).multiply(10));
            Vec3d d = aimAt.subtract(h).normalize();
            throwBolt();
            int bolts = transitioned ? 3 : 1;
            for (int i = 0; i < bolts; i++) {
                Vec3d v = d.rotateY((float) Math.toRadians((i - (bolts - 1) / 2.0) * 16)).multiply(transitioned ? 1.5 : 1.7);
                addHazard(new LeonardHazards.Bolt(world, h, v, transitioned ? 15 : 14, 46, transitioned ? 3.4f : 2.6f, true));
            }
            world.playSound(null, h.x, h.y, h.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 1.0f, 1.8f);
        }
        if (t >= 26) endAttack();
    }

    // ---- Zoom: fifteen dashes across the plaza, too fast to see, each line flickering a moment first ----

    private void tickZoom(ServerWorld world) {
        int dashes = LeonardTimeline.ZOOM_DASHES, warn = 5, travel = 3, pause = 1, each = warn + travel + pause;
        int t = actionTicks - 1;
        if (t == 0) {
            setPoseState(POSE_DASH);
            setAfterimages(true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0f, 2.0f);
        }
        int k = t / each, phase = t % each;
        if (t > dashes * each + 30) {
            // Never left mid-dash, whatever happened
            noClip = false;
            setNoGravity(false);
            endAttack(10);
            return;
        }
        if (k >= dashes) {
            setAfterimages(false);
            if (t >= dashes * each + 6) endAttack(10);
            return;
        }
        if (phase == 0) {
            from = getPos();
            spot = zoomEnd(world);
            faceTowards(spot);
            struck.clear();
        }
        if (spot == null) return;
        if (phase < warn) {
            // The line he's about to take flickers on the floor
            if (phase % 2 == 0) LeonardHazards.arc(world, from.add(0, 0.2, 0), spot.add(0, 0.2, 0), LeonardHazards.YELLOW, 1.2, 0.05);
            if (phase == 1) world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.HOSTILE, 1.2f, 1.8f);
            setVelocity(Vec3d.ZERO);
            return;
        }
        if (phase < warn + travel) {
            Vec3d a = getPos();
            Vec3d at = from.lerp(spot, (phase - warn + 1) / (double) travel);
            noClip = true;
            setNoGravity(true);
            setPosition(at.x, at.y, at.z);
            setVelocity(Vec3d.ZERO);
            if (phase == warn) world.playSound(null, from.x, from.y, from.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 1.4f, 1.7f);
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid())) continue;
                if (p.getBoundingBox().expand(0.6).raycast(a.add(0, 1, 0), at.add(0, 1, 0)).isPresent()
                        || LeonardHazards.flatToSegment(p.getPos(), a, at) < 1.0 && Math.abs(p.getY() - at.y) < 2) {
                    struck.add(p.getUuid());
                    if (shock(p, 12)) {
                        Vec3d along = at.subtract(a);
                        Vec3d side = new Vec3d(-along.z, 0, along.x);
                        if (side.dotProduct(p.getPos().subtract(a)) < 0) side = side.multiply(-1);
                        launch(p, side.add(along.normalize().multiply(0.5)), 2.0, 0.7);
                    }
                    world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.4f, 1.3f);
                }
            }
            return;
        }
        if (phase == warn + travel) {
            noClip = false;
            setNoGravity(false);
            addHazard(new LeonardHazards.Trail(from, spot, 30));
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 20, 0.4, 0.6, 0.4, 0.3);
        }
    }

    /** Where his next dash ends: across the plaza from him, often straight through one of you. */
    private Vec3d zoomEnd(ServerWorld world) {
        Vec3d c = arenaCenter(world);
        // Anywhere else (tests): close round where he started, so he never dashes off somewhere that isn't ticking
        double radius = Arenas.sinOf(world) == Sin.PRIDE ? 30 : 3;
        for (int tries = 0; tries < 8; tries++) {
            Vec3d want;
            if (!fighters.isEmpty() && random.nextFloat() < 0.4f) {
                ServerPlayerEntity p = fighters.get(random.nextInt(fighters.size()));
                Vec3d through = p.getPos().subtract(getPos()).multiply(1, 0, 1);
                if (through.lengthSquared() < 1) continue;
                want = p.getPos().add(through.normalize().multiply(6 + random.nextDouble() * 8));
            } else {
                double a = Math.atan2(getZ() - c.z, getX() - c.x) + Math.PI + (random.nextDouble() - 0.5) * 2.2;
                double r = radius * (0.55 + random.nextDouble() * 0.4);
                want = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            if (MathHelper.square(want.x - c.x) + MathHelper.square(want.z - c.z) > radius * radius) {
                Vec3d off = want.subtract(c).multiply(1, 0, 1).normalize().multiply(radius);
                want = c.add(off.x, 0, off.z);
            }
            Vec3d at = safeSpot(want);
            if (at != null && at.distanceTo(getPos()) > Math.min(6, radius * 0.7)) return at;
        }
        return safeSpot(c);
    }

    // ---- Boomerang: his bolt whirls out in a loop and back to his hand ----

    private void tickBoomerang(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            ServerPlayerEntity p = pick(null);
            if (p == null || boltOut) {
                endAttack();
                return;
            }
            focus = p.getUuid();
            setPoseState(POSE_SIDE_THROW);
        }
        ServerPlayerEntity target = fighter(focus);
        if (target != null && t < 10) faceTowards(target.getPos());
        if (t == 10) {
            Vec3d h = handPos(true);
            Vec3d d = target != null ? target.getPos().subtract(getPos()) : Vec3d.fromPolar(0, getYaw());
            double reach = target != null ? MathHelper.clamp(target.distanceTo(this) + 6, 14, 30) : 20;
            throwBolt();
            int sign = random.nextBoolean() ? 1 : -1;
            addHazard(new LeonardHazards.Boomerang(world, h, d, sign, reach, transitioned ? 12 : 10));
            if (transitioned) addHazard(new LeonardHazards.Boomerang(world, h, d, -sign, reach * 0.85, 12));
            setPoseState(POSE_THROW);
            swingHand(Hand.MAIN_HAND);
            world.playSound(null, h.x, h.y, h.z, SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.HOSTILE, 1.6f, 1.2f);
        }
        if (t >= 22) endAttack(6);
    }

    // ---- Chain Lightning: an arc to one of you, then on to the next, and the next. Keep apart ----

    private void tickChain(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            ServerPlayerEntity p = pick(null);
            if (p == null) {
                endAttack();
                return;
            }
            focus = p.getUuid();
            targets = new ArrayList<>();
            setPoseState(POSE_POINT);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.4f, 1.8f);
        }
        ServerPlayerEntity first = fighter(focus);
        if (t < 16) {
            if (first != null) {
                faceTowards(first.getPos());
                LeonardHazards.ring(world, ParticleTypes.ELECTRIC_SPARK, first.getPos(), 1.4, 10, t * 0.4);
            }
            Vec3d h = handPos(true);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 4, 0.15, 0.15, 0.15, 0.1);
            return;
        }
        int jumps = transitioned ? 7 : 4, every = 4;
        double hop = transitioned ? 11 : 9;
        if ((t - 16) % every == 0) {
            int k = (t - 16) / every;
            if (k == 0) {
                if (first == null) {
                    endAttack();
                    return;
                }
                arcTo(world, handPos(true), getId(), first);
            } else if (k <= jumps && !targets.isEmpty()) {
                ServerPlayerEntity last = targets.get(targets.size() - 1);
                ServerPlayerEntity next = null;
                for (ServerPlayerEntity p : fighters) {
                    if (targets.contains(p) || p.distanceTo(last) > hop) continue;
                    if (next == null || p.squaredDistanceTo(last) < next.squaredDistanceTo(last)) next = p;
                }
                if (next == null) {
                    endAttack();
                    return;
                }
                arcTo(world, last.getBoundingBox().getCenter(), -1, next);
            } else {
                endAttack();
            }
        }
    }

    private void arcTo(ServerWorld world, Vec3d from, int casterId, ServerPlayerEntity p) {
        Vec3d to = p.getBoundingBox().getCenter();
        if (!MagicSpells.zap(world, casterId, from, to, MagicSpells.ZAP_ENTITY, from.subtract(to).normalize())) {
            LeonardHazards.arc(world, from, to, ParticleTypes.ELECTRIC_SPARK, 0.35, 0.3);
        }
        targets.add(p);
        p.timeUntilRegen = 0;
        if (shock(p, transitioned ? 8 : 7)) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 16, 0.3, 0.5, 0.3, 0.3);
        world.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.2f, 1.5f);
        SoundEvent cast = MagicSpells.zapSound();
        if (cast != null) world.playSound(null, from.x, from.y, from.z, cast, SoundCategory.HOSTILE, 1.0f, 0.8f);
    }

    // ---- Thunder Clap: arms wide, then his hands come together and the air bursts ----

    private void tickClap(ServerWorld world) {
        int t = actionTicks;
        stand();
        ServerPlayerEntity near = nearestFighter();
        if (t == 1) {
            setPoseState(POSE_CLAP_WIND);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        if (t < 9) {
            if (near != null) faceTowards(near.getPos());
            Vec3d c = getPos().add(0, 1.3, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 4, 0.8, 0.3, 0.8, 0.05);
        }
        if (t == 9) {
            setPoseState(POSE_CLAP);
            Vec3d c = getPos().add(0, 1.3, 0).add(Vec3d.fromPolar(0, getYaw()).multiply(0.6));
            for (ServerPlayerEntity p : fighters) {
                if (p.distanceTo(this) > 5.5) continue;
                if (shock(p, 8)) launch(p, p.getPos().subtract(getPos()), 1.5, 0.6);
            }
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 80, 1, 0.6, 1, 0.8);
            world.spawnParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            LeonardHazards.ring(world, LeonardHazards.YELLOW, ground(getPos()), 5.5, 40, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 2.0f, 0.9f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 2.0f, 0.8f);
        }
        if (t >= 18) endAttack();
    }

    // ---- CONEMAN: the Cone Slam. A heavy leap, and two rings of lightning to jump ----

    private void tickSlam(ServerWorld world) {
        int t = actionTicks, crouch = 14, air = 26;
        if (t == 1) {
            Vec3d target = clusterSpot();
            if (target == null) {
                endAttack();
                return;
            }
            spot = target;
            from = getPos();
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }
        if (t < crouch) {
            stand();
            if (t % 2 == 0) LeonardHazards.ring(world, LeonardHazards.YELLOW, spot, 4.5, 30, t * 0.2);
            return;
        }
        if (t < crouch + air) {
            double u = (t - crouch) / (double) air;
            Vec3d at = from.lerp(spot, u).add(0, Math.sin(Math.PI * u) * 12, 0);
            setNoGravity(true);
            setPosition(at.x, at.y, at.z);
            setVelocity(Vec3d.ZERO);
            setPoseState(u < 0.5 ? POSE_HOVER : POSE_SLAM);
            if (t % 2 == 0) LeonardHazards.ring(world, LeonardHazards.YELLOW, spot, 4.5, 30, t * 0.2);
            if (t == crouch) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 1.5f, 0.5f);
            return;
        }
        if (t == crouch + air) {
            setNoGravity(false);
            setPosition(spot.x, spot.y, spot.z);
            setVelocity(Vec3d.ZERO);
            for (ServerPlayerEntity p : fighters) {
                if (LeonardHazards.flatDistance(p.getPos(), spot) < 4.5 && Math.abs(p.getY() - spot.y) < 3) {
                    if (shock(p, 16)) launch(p, p.getPos().subtract(spot), 1.6, 0.8);
                }
            }
            LeonardHazards.lightning(world, spot);
            addHazard(new LeonardHazards.Ring(spot, 0.9, 22, 10, 20));
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, spot.x, spot.y + 0.3, spot.z, 1, 0, 0, 0, 0);
            BlockState under = getWorld().getBlockState(BlockPos.ofFloored(spot.x, spot.y - 0.5, spot.z));
            if (!under.isAir()) world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, under), spot.x, spot.y + 0.2, spot.z, 120, 3, 0.3, 3, 0.4);
            world.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.4f, 0.5f);
        }
        if (t == crouch + air + 12) addHazard(new LeonardHazards.Ring(spot, 0.9, 22, 10, 20));
        stand();
        if (t >= crouch + air + 26) endAttack();
    }

    // ---- CONEMAN: the Storm Grid. The plaza is a chessboard: the black squares are struck, then the white ----

    private static final int GRID_CELL = 6, GRID_MARK = 34;

    private void tickGrid(ServerWorld world) {
        int t = actionTicks;
        stand();
        Vec3d c = arenaCenter(world);
        double radius = Arenas.sinOf(world) == Sin.PRIDE ? 36 : 10;
        if (t == 1) {
            setPoseState(POSE_RAISE);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.5f, 0.6f);
            for (ServerPlayerEntity p : fighters) p.sendMessage(Text.translatable("message.fmscraft.leonard_grid").formatted(Formatting.YELLOW), true);
        }
        for (int round = 0; round < 2; round++) {
            int start = 4 + round * (GRID_MARK + 6), strikeAt = start + GRID_MARK;
            if (t >= start && t < strikeAt && t % 3 == 0) gridCells(world, c, radius, round, false);
            if (t == strikeAt) gridCells(world, c, radius, round, true);
        }
        if (t >= 4 + 2 * (GRID_MARK + 6) + 8) endAttack();
    }

    /** The squares of one colour: marked ({@code strike} false), or struck. */
    private void gridCells(ServerWorld world, Vec3d c, double radius, int parity, boolean strike) {
        int n = (int) Math.ceil(radius / GRID_CELL);
        int bolts = 0;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                if (Math.floorMod(i + j, 2) != parity) continue;
                Vec3d cell = new Vec3d(c.x + i * GRID_CELL, c.y, c.z + j * GRID_CELL);
                if (MathHelper.square(cell.x - c.x) + MathHelper.square(cell.z - c.z) > radius * radius) continue;
                double h = GRID_CELL / 2.0;
                Vec3d g = ground(cell);
                if (!strike) {
                    // The square's edges, in yellow
                    for (double k = -h; k <= h; k += 1.0) {
                        world.spawnParticles(LeonardHazards.YELLOW, g.x + k, g.y + 0.15, g.z - h, 1, 0, 0, 0, 0);
                        world.spawnParticles(LeonardHazards.YELLOW, g.x + k, g.y + 0.15, g.z + h, 1, 0, 0, 0, 0);
                        world.spawnParticles(LeonardHazards.YELLOW, g.x - h, g.y + 0.15, g.z + k, 1, 0, 0, 0, 0);
                        world.spawnParticles(LeonardHazards.YELLOW, g.x + h, g.y + 0.15, g.z + k, 1, 0, 0, 0, 0);
                    }
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, g.x, g.y + 0.2, g.z, 1, h * 0.6, 0.05, h * 0.6, 0);
                    continue;
                }
                if (bolts++ % 4 == 0) LeonardHazards.lightning(world, g);
                world.spawnParticles(ParticleTypes.END_ROD, g.x, g.y + 3, g.z, 20, h * 0.5, 3, h * 0.5, 0.05);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, g.x, g.y + 0.5, g.z, 20, h * 0.5, 0.3, h * 0.5, 0.3);
                for (ServerPlayerEntity p : fighters) {
                    if (Math.abs(p.getX() - g.x) <= h && Math.abs(p.getZ() - g.z) <= h && Math.abs(p.getY() - g.y) < 6) {
                        if (shock(p, 14)) launch(p, new Vec3d(random.nextGaussian(), 0, random.nextGaussian()), 0.4, 0.6);
                    }
                }
            }
        }
        if (strike) {
            audienceSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f, 0.8f);
            audienceSound(SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 0.7f);
        }
    }

    // ---- CONEMAN: Tesla Rods. Three great rods driven into the plaza, lightning arcing between them ----

    private void tickRods(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            setPoseState(POSE_RAISE);
            Vec3d around = clusterSpot();
            if (around == null) around = getPos();
            List<Vec3d> rods = new ArrayList<>();
            double r = 7 + random.nextDouble() * 4, turn = random.nextDouble() * MathHelper.TAU;
            for (int i = 0; i < 3; i++) {
                double a = turn + i * MathHelper.TAU / 3;
                Vec3d at = safeSpot(around.add(Math.cos(a) * r, 0, Math.sin(a) * r));
                rods.add(at != null ? at : ground(around.add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            }
            addHazard(new LeonardHazards.Rods(world, rods, 9));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t == 8) setPoseState(POSE_SLAM);
        if (t >= 22) endAttack();
    }

    // ---- CONEMAN: Ball Lightning. Slow, crackling balls of light, each drifting after one of you ----

    private void tickBalls(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            setPoseState(POSE_SPREAD);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0f, 1.4f);
        }
        int n = LeonardTimeline.balls(fighters.size());
        if (t >= 8 && t < 8 + n * 6 && (t - 8) % 6 == 0 && !fighters.isEmpty()) {
            int i = (t - 8) / 6;
            ServerPlayerEntity p = fighters.get(i % fighters.size());
            double a = i * MathHelper.TAU / n;
            Vec3d at = getPos().add(Math.cos(a) * 2.5 * scale() * 0.5, getHeight() * 0.9, Math.sin(a) * 2.5 * scale() * 0.5);
            addHazard(new LeonardHazards.Ball(world, at, p.getUuid(), 12, 0.24));
            world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.0f, 2.0f);
        }
        if (t >= 8 + n * 6 + 6) endAttack();
    }

    // ---- CONEMAN: the Cone Beam. A beam along the floor, sweeping all the way round him. Jump it ----

    private static final double SWEEP_REACH = 22;

    private void tickSweep(ServerWorld world) {
        int t = actionTicks, charge = 30, sweep = 56;
        stand();
        Vec3d c = ground(getPos());
        if (t == 1) {
            setPoseState(POSE_CROUCH);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 1.0f);
            from = Vec3d.fromPolar(0, getYaw());
            spot = Vec3d.ZERO;
        }
        if (t <= charge) {
            Vec3d tip = getPos().add(0, getHeight() + 0.6 * scale(), 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 6, 0.3, 0.3, 0.3, 0.1);
            if (t % 3 == 0) LeonardHazards.ring(world, LeonardHazards.YELLOW, c, SWEEP_REACH, 80, t * 0.05);
            return;
        }
        int s = t - charge;
        boolean twin = getHealth() < getMaxHealth() * 0.5f;
        if (s <= sweep) {
            setPoseState(POSE_CHANNEL);
            double a0 = Math.atan2(from.z, from.x), angle = a0 + MathHelper.TAU * s / sweep, prev = a0 + MathHelper.TAU * (s - 1) / sweep;
            faceYaw((float) Math.toDegrees(angle) - 90f);
            for (int beam = 0; beam < (twin ? 2 : 1); beam++) {
                double a = angle + beam * Math.PI, ap = prev + beam * Math.PI;
                Vec3d end = c.add(Math.cos(a) * SWEEP_REACH, 0.5, Math.sin(a) * SWEEP_REACH);
                LeonardHazards.arc(world, c.add(0, 0.5, 0), end, LeonardHazards.YELLOW, 0.7, 0.1);
                LeonardHazards.arc(world, c.add(0, 0.5, 0), end, ParticleTypes.ELECTRIC_SPARK, 1.0, 0.25);
                for (ServerPlayerEntity p : fighters) {
                    if (struck.contains(p.getUuid())) continue;
                    double d = LeonardHazards.flatDistance(p.getPos(), c);
                    if (d > SWEEP_REACH || d < 1) continue;
                    double pa = Math.atan2(p.getZ() - c.z, p.getX() - c.x);
                    double rel = MathHelper.wrapDegrees(Math.toDegrees(pa - ap)), span = Math.toDegrees(a - ap);
                    if (rel < -4 || rel > span + 4) continue;
                    if (!p.isOnGround() || p.getY() > c.y + 1.4) continue;
                    struck.add(p.getUuid());
                    if (shock(p, 12)) launch(p, p.getPos().subtract(c), 1.0, 0.6);
                }
            }
            if (s % 5 == 0) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 2.0f, 2.0f);
            return;
        }
        if (s >= sweep + 10) endAttack();
    }

    // ---- CONEMAN: the Stomp. Up close, a foot comes down and the floor jumps ----

    private void tickStomp(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (t == 1) {
            setPoseState(POSE_STOMP);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        if (t < 12) LeonardHazards.ring(world, LeonardHazards.YELLOW, ground(getPos()), 6.5, 30, t * 0.3);
        if (t == 12) {
            setPoseState(POSE_SLAM);
            Vec3d c = ground(getPos());
            for (ServerPlayerEntity p : fighters) {
                if (LeonardHazards.flatDistance(p.getPos(), c) > 6.5 || Math.abs(p.getY() - c.y) > 2.5) continue;
                if (shock(p, 12)) launch(p, p.getPos().subtract(c), 1.4, 0.75);
            }
            addHazard(new LeonardHazards.Ring(c, 0.8, 9, 0, 0));
            world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 6, 2, 0.2, 2, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.3, c.z, 60, 3, 0.3, 3, 0.4);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 2.5f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        if (t >= 24) endAttack();
    }

    // ---- Munch's beam (phase 1): he stops nodding, burns a ring into the plaza, then fires ----

    private void tickMunch(ServerWorld world) {
        if (beam != null) {
            if (beam.age == beam.charge) sky(world, PrideSkyPayload.MUNCH_FIRING, BEAM_FIRE, beam.target);
            if (beam.age >= beam.charge + beam.fire) {
                beam = null;
                sky(world, PrideSkyPayload.MUNCH_WATCHING, 0, Vec3d.ZERO);
                if (munchQueued > 0) munchCooldown = 30;
            }
            return;
        }
        if (transitioned || action == Action.TRANSITION || action == Action.ENTRANCE || heldStill || Arenas.sinOf(world) != Sin.PRIDE) return;
        if (munchCooldown != Integer.MAX_VALUE && --munchCooldown > 0) return;
        if (munchCooldown == Integer.MAX_VALUE || fighters.isEmpty() || action == Action.ZOOM) return;
        ServerPlayerEntity p = pick(null);
        if (p == null) return;
        startBeam(world, ground(p.getPos()));
        if (munchQueued > 0) {
            munchQueued--;
            munchCooldown = BEAM_GAP_MIN + random.nextInt(BEAM_GAP_MAX - BEAM_GAP_MIN);
        } else {
            // Once he's hurt, Munch fires twice in a row
            munchQueued = barPercent() < 0.6f && fighters.size() > 1 ? 1 : 0;
            munchCooldown = BEAM_GAP_MIN + random.nextInt(BEAM_GAP_MAX - BEAM_GAP_MIN);
        }
    }

    private void startBeam(ServerWorld world, Vec3d at) {
        beam = new LeonardHazards.MunchBeam(at, BEAM_CHARGE, BEAM_FIRE, BEAM_RADIUS, BEAM_DAMAGE);
        addHazard(beam);
        beamsFired++;
        sky(world, PrideSkyPayload.MUNCH_CHARGING, BEAM_CHARGE, at);
        for (ServerPlayerEntity p : fighters) {
            if (LeonardHazards.flatDistance(p.getPos(), at) < BEAM_RADIUS + 4) {
                p.sendMessage(Text.translatable("message.fmscraft.munch_beam").formatted(Formatting.GOLD), true);
            }
        }
    }

    // ---- The hazards, his aura, the world round the fight ----

    private void tickHazards(ServerWorld world) {
        for (LeonardHazards.Hazard h : new ArrayList<>(hazards)) {
            if (!h.tick(world, this)) {
                h.remove();
                hazards.remove(h);
            }
        }
        if (boltOut && action == Action.MOVE) {
            boolean out = false;
            for (LeonardHazards.Hazard h : hazards) out |= h instanceof LeonardHazards.Bolt || h instanceof LeonardHazards.Boomerang;
            if (!out) boltReturns();
        }
    }

    /** Sparks crackling off him; as CONEMAN a storm round him that jolts whoever's too close. */
    private void tickAura(ServerWorld world) {
        if (action == Action.ENTRANCE) return;
        double w = getWidth() * 0.6, h = getHeight();
        if (age % 2 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + h * 0.5, getZ(), 2, w, h * 0.35, w, 0.08);
        if (age % 5 == 0) world.spawnParticles(LeonardHazards.YELLOW, getX(), getY() + h * 0.5, getZ(), 1, w, h * 0.35, w, 0);
        if (!transitioned) return;
        for (int i = 0; i < 3; i++) {
            double a = age * 0.18 + i * MathHelper.TAU / 3, y = ((age + i * 12) % 36) / 36.0 * (h + 1);
            world.spawnParticles(LeonardHazards.STORM, getX() + Math.cos(a) * 2.2, getY() + y, getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
        }
        if (age % 2 == 0) world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + h * 0.7, getZ(), 1, w, h * 0.3, w, 0.01);
        if (age % 9 == 0) {
            Vec3d c = getPos().add(0, h * 0.6, 0);
            double a = random.nextDouble() * MathHelper.TAU;
            LeonardHazards.arc(world, c, ground(getPos().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5)), ParticleTypes.ELECTRIC_SPARK, 0.5, 0.5);
        }
        if (action == Action.TRANSITION || untouchable) return;
        for (ServerPlayerEntity p : fighters) {
            if (p.distanceTo(this) > AURA_RADIUS * 0.8 + getWidth()) continue;
            if (age - auraHit.getOrDefault(p.getUuid(), -100) < 30) continue;
            auraHit.put(p.getUuid(), age);
            shock(p, 3);
            LeonardHazards.arc(world, getPos().add(0, h * 0.6, 0), p.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 0.4, 0.3);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_COPPER_BULB_TURN_OFF, SoundCategory.HOSTILE, 1.0f, 1.6f);
        }
    }

    /** Wisps of cloud drifting by in the day; in CONEMAN's storm, thunder far off and lightning out over the clouds. */
    private void tickAmbience(ServerWorld world) {
        if (age % 6 == 0) {
            for (ServerPlayerEntity p : fighters) world.spawnParticles(p, ParticleTypes.CLOUD, false, p.getX(), p.getY() + 1, p.getZ(), 1, 12, 3, 12, 0.01);
        }
        if (!transitioned) return;
        if (age % 4 == 0) {
            for (ServerPlayerEntity p : fighters) world.spawnParticles(p, ParticleTypes.ASH, false, p.getX(), p.getY() + 3, p.getZ(), 4, 10, 3, 10, 0);
        }
        if (age % 90 == 45) {
            double a = random.nextDouble() * MathHelper.TAU, r = 50 + random.nextDouble() * 40;
            LeonardHazards.lightning(world, PrideArena.CENTER.add(Math.cos(a) * r, -18, Math.sin(a) * r));
        }
    }

    /** Off the plaza, falling into the clouds: the lightning carries you back. It stings. */
    private void catchStrays(ServerWorld world) {
        for (ServerPlayerEntity p : fighters) {
            Vec3d pos = p.getPos();
            if (PrideArena.inBounds(pos)) {
                straying.remove(p.getUuid());
                continue;
            }
            int out = straying.merge(p.getUuid(), 1, Integer::sum);
            if (pos.y >= PrideArena.MIN_Y && out < 10) continue;
            straying.remove(p.getUuid());
            Vec3d to = ground(PrideArena.returnSpot(pos));
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
            p.teleport(world, to.x, to.y, to.z, p.getYaw(), p.getPitch());
            p.fallDistance = 0;
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
            LeonardHazards.lightning(world, to);
            shock(p, 4);
            p.sendMessage(Text.translatable("message.fmscraft.leonard_caught").formatted(Formatting.YELLOW), true);
        }
    }

    // ---- Dying: the cone comes off, and LEONARD would very much like to not be here ----

    /**
     * Ticks from the last blow: CONEMAN stands there a moment, then poof, the cone flies off, and it's just LEONARD
     * ({@link #DEATH_POOF}); he looks round (6 s), fidgeting, whistling, sweating, glancing at you and quickly away;
     * he's caught ({@link #DEATH_CAUGHT}: he jumps); and he runs for it, sprint-jumping, arms in the air, towards the
     * middle (3 s), and is gone in a poof mid-jump (10 s). The portal and the wheel come 2 s after.
     */
    public static final int DEATH_POOF = 12, DEATH_HONOUR_AT = 40, DEATH_CAUGHT = 132, DEATH_FLEE = 142, DEATH_TICKS = 202,
            DEATH_REWARDS_AFTER = 40;
    /** How fast he runs (blocks a tick) and how far from the middle he'll go (in the plaza; off it, from where he was). */
    public static final double DEATH_SPEED = 0.42, DEATH_RUN = 12, DEATH_RUN_OFF = 2.5;
    /** The tune he whistles while trying very hard to look like he wasn't doing anything (flute notes, pitches). */
    private static final float[] WHISTLE = {1.0f, 1.26f, 1.5f, 1.26f, 1.0f, 0.84f, 1.0f};
    private boolean pendingDeath;
    private int deathTicks = -1, nextGlance;
    private Vec3d deathFrom, runTo, glanceAt;
    private boolean glancingAtYou;

    /** Whether he's dying (beaten, not yet gone), how far into it, and where he's running to (for tests). */
    public boolean isDying() {
        return action == Action.DYING;
    }

    public int deathTicks() {
        return Math.max(0, deathTicks);
    }

    public Vec3d runTo() {
        return runTo;
    }

    private void startDying(ServerWorld world) {
        pendingDeath = false;
        clearAttacks(world);
        action = Action.DYING;
        actionTicks = 0;
        deathTicks = 0;
        untouchable = true;
        heldStill = true;
        setNoGravity(false);
        getNavigation().stop();
        setTarget(null);
        setVelocity(0, Math.min(0, getVelocity().y), 0);
        deathFrom = getPos();
        // The music stops dead
        stopSong(0);
        setPoseState(POSE_IDLE);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
        // The fight is won from here (nobody can lose it now); the portal and the wheel come after
        BossFights.onBossDefeated(this, DEATH_TICKS + DEATH_REWARDS_AFTER);
    }

    private void tickDying(ServerWorld world) {
        int t = ++deathTicks;
        getNavigation().stop();
        if (t < DEATH_POOF) {
            // CONEMAN, standing very still, his storm gone out
            setVelocity(0, getVelocity().y, 0);
            if (t % 4 == 0) world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + getHeight(), getZ(), 2, 0.3, 0.1, 0.3, 0.01);
            return;
        }
        if (t == DEATH_POOF) unCone(world);
        if (t == DEATH_HONOUR_AT) {
            for (ServerPlayerEntity p : audience(world)) if (!p.isSpectator()) SinBossEntity.grantDefeated(p, Sin.PRIDE);
        }
        if (t < DEATH_CAUGHT) {
            lookAround(world, t);
            return;
        }
        if (t < DEATH_FLEE) {
            if (t == DEATH_CAUGHT) {
                // Caught: he jumps out of his skin
                setPoseState(POSE_CAUGHT);
                ServerPlayerEntity near = nearestFighter();
                if (near != null) getLookControl().lookAt(near, 60f, 60f);
                setVelocity(0, 0.38, 0);
                velocityDirty = true;
                world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.HOSTILE, 1.5f, 2.0f);
                world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
                world.spawnParticles(ParticleTypes.SPLASH, getX(), getEyeY() + 0.3, getZ(), 14, 0.3, 0.1, 0.3, 0.2);
                runTo = fleeTarget(world);
            } else {
                ServerPlayerEntity near = nearestFighter();
                if (near != null) getLookControl().lookAt(near, 60f, 60f);
                setVelocity(0, getVelocity().y, 0);
            }
            return;
        }
        if (t < DEATH_TICKS) {
            flee(world, t);
            return;
        }
        // Mid-jump: poof, and he's gone
        Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
        world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 45, 0.4, 0.6, 0.4, 0.06);
        world.spawnParticles(ParticleTypes.POOF, c.x, c.y, c.z, 25, 0.3, 0.5, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.3, 0.5, 0.3, 0.2);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.0f, 1.6f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.HOSTILE, 1.2f, 0.5f);
        setInvisible(true);
        setSilent(true);
        killing = true;
        try {
            kill();
        } finally {
            killing = false;
        }
    }

    /** Poof: the cone flies off his head (yours to keep), and he's just LEONARD again, shades and all. */
    private void unCone(ServerWorld world) {
        Vec3d top = getPos().add(0, getHeight() * 0.9, 0), mid = getPos().add(0, getHeight() * 0.5, 0);
        world.spawnParticles(ParticleTypes.CLOUD, mid.x, mid.y, mid.z, 90, getWidth() * 0.5, getHeight() * 0.4, getWidth() * 0.5, 0.08);
        world.spawnParticles(ParticleTypes.POOF, mid.x, mid.y, mid.z, 40, getWidth() * 0.4, getHeight() * 0.4, getWidth() * 0.4, 0.06);
        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 2.0f, 1.0f);
        world.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.HOSTILE, 2.0f, 0.5f);
        if (dataTracker.get(CONED)) {
            net.minecraft.entity.ItemEntity cone = new net.minecraft.entity.ItemEntity(world, top.x, top.y, top.z, new ItemStack(ModItems.TRAFFIC_CONE));
            double a = random.nextDouble() * MathHelper.TAU;
            cone.setVelocity(Math.cos(a) * 0.18, 0.55, Math.sin(a) * 0.18);
            cone.setPickupDelay(60);
            world.spawnEntity(cone);
        }
        dataTracker.set(CONED, false);
        dataTracker.set(SHADES, true);
        setScale(1f);
        transitioned = false;
        bossBar().setName(Text.literal("LEONARD"));
        // The storm breaks up: back to the plain blue sky
        sky(world, PrideSkyPayload.MUNCH_HIDDEN, 0, Vec3d.ZERO);
        setPoseState(POSE_GUILTY);
        nextGlance = DEATH_POOF + 14;
        glanceAt = null;
    }

    /** Looking round (anywhere but at you, mostly), fidgeting, sweating, whistling a little tune. */
    private void lookAround(ServerWorld world, int t) {
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        if (t >= nextGlance) {
            ServerPlayerEntity near = nearestFighter();
            // One glance in three is at you, quickly, and away again
            glancingAtYou = near != null && random.nextInt(3) == 0;
            if (glancingAtYou) {
                glanceAt = near.getEyePos();
                nextGlance = t + 8 + random.nextInt(5);
            } else {
                float side = (random.nextBoolean() ? 1 : -1) * (40f + random.nextFloat() * 70f);
                Vec3d dir = Vec3d.fromPolar(0, getBodyYaw() + side);
                glanceAt = getEyePos().add(dir.multiply(6)).add(0, -1 + random.nextDouble() * 3, 0);
                nextGlance = t + 14 + random.nextInt(14);
                // Now and then a nervous little shuffle
                if (random.nextInt(3) == 0 && isOnGround()) {
                    Vec3d step = Vec3d.fromPolar(0, getBodyYaw() + (random.nextBoolean() ? 90 : -90)).multiply(0.12);
                    setVelocity(step.x, getVelocity().y, step.z);
                }
            }
        }
        if (glanceAt != null) getLookControl().lookAt(glanceAt.x, glanceAt.y, glanceAt.z, glancingAtYou ? 40f : 12f, 20f);
        if (!(random.nextInt(3) == 0)) setVelocity(getVelocity().x * 0.6, getVelocity().y, getVelocity().z * 0.6);
        // Whistling innocently, twice
        int w = t - 40, w2 = t - 92;
        int note = w >= 0 && w % 5 == 0 && w / 5 < WHISTLE.length ? w / 5 : w2 >= 0 && w2 % 5 == 0 && w2 / 5 < WHISTLE.length ? w2 / 5 : -1;
        if (note >= 0) {
            world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(), SoundCategory.HOSTILE, 0.9f, WHISTLE[note]);
            world.spawnParticles(ParticleTypes.NOTE, getX() + forward.x * 0.4, getEyeY() + 0.4, getZ() + forward.z * 0.4, 0, (note % 7) / 24.0, 0, 0, 1);
        }
        // Sweating
        if (t % 7 == 0) {
            Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(random.nextBoolean() ? 0.3 : -0.3);
            world.spawnParticles(ParticleTypes.FALLING_WATER, getX() + side.x, getEyeY() + 0.15, getZ() + side.z, 1, 0, 0, 0, 0);
        }
        if (t % 31 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 0.5f, 1.4f);
    }

    /** Where he runs: towards the middle of the plaza (never off it), away from you. Off it, a little way from where he was. */
    private Vec3d fleeTarget(ServerWorld world) {
        boolean plaza = Arenas.sinOf(world) == Sin.PRIDE;
        Vec3d center = plaza ? PrideArena.CENTER : deathFrom;
        double run = plaza ? DEATH_RUN : DEATH_RUN_OFF;
        ServerPlayerEntity near = nearestFighter();
        Vec3d away = near == null ? Vec3d.fromPolar(0, getBodyYaw()) : getPos().subtract(near.getPos()).multiply(1, 0, 1);
        if (away.lengthSquared() < 1e-4) away = new Vec3d(1, 0, 0);
        away = away.normalize();
        // In the plaza: straight for the middle if he's out from it, and on past it away from you; never further out than DEATH_RUN
        if (plaza && getPos().subtract(center).horizontalLength() > 4) {
            Vec3d in = center.subtract(getPos()).multiply(1, 0, 1).normalize();
            away = in.add(away.multiply(0.4)).normalize();
        }
        Vec3d to = getPos().add(away.multiply(run * 1.2));
        Vec3d off = to.subtract(center).multiply(1, 0, 1);
        if (off.horizontalLength() > run) to = center.add(off.normalize().multiply(run));
        return new Vec3d(to.x, getY(), to.z);
    }

    /** Running for it: sprint-jumping, arms in the air, straight for where he's going, a little hop each time he lands. */
    private void flee(ServerWorld world, int t) {
        if (t == DEATH_FLEE) {
            setPoseState(POSE_FLEE);
            setSprinting(true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 1.8f);
        }
        if (runTo == null) runTo = fleeTarget(world);
        Vec3d to = runTo.subtract(getPos()).multiply(1, 0, 1);
        double d = to.horizontalLength();
        Vec3d dir = d > 1e-3 ? to.multiply(1 / d) : Vec3d.fromPolar(0, getBodyYaw());
        double speed = d > 1.2 ? DEATH_SPEED : d * 0.3;
        float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90f;
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        double vy = getVelocity().y;
        if (isOnGround() && t > DEATH_FLEE) {
            vy = 0.48;
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_SMALL_FALL, SoundCategory.HOSTILE, 0.8f, 1.2f);
            var ground = world.getBlockState(getBlockPos().down());
            if (!ground.isAir()) world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, ground), getX(), getY() + 0.1, getZ(), 8, 0.3, 0.05, 0.3, 0.1);
        }
        setVelocity(dir.x * speed, vy, dir.z * speed);
        velocityDirty = true;
        if (t % 3 == 0) world.spawnParticles(ParticleTypes.CLOUD, getX() - dir.x * 0.5, getY() + 0.2, getZ() - dir.z * 0.5, 1, 0.1, 0.05, 0.1, 0.01);
        if (t % 9 == 0) world.spawnParticles(ParticleTypes.FALLING_WATER, getX(), getEyeY() + 0.2, getZ(), 2, 0.3, 0.05, 0.3, 0);
        if (t % 14 == 4) world.playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.HOSTILE, 0.8f, 1.8f);
    }

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld world && deathTicks >= 0) {
            // All that was his own exit: nothing more
            clearAttacks(world);
            transitioned = false;
            sky(world, PrideSkyPayload.MUNCH_HIDDEN, 0, Vec3d.ZERO);
            setSprinting(false);
        } else if (getWorld() instanceof ServerWorld world) {
            clearAttacks(world);
            transitioned = false;
            sky(world, PrideSkyPayload.MUNCH_HIDDEN, 0, Vec3d.ZERO);
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.unfreeze(p);
                Cinematics.flash(p, 0.8f, 30);
            }
            Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
            LeonardHazards.lightning(world, getPos());
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 300, 1.5, 2, 1.5, 0.8);
            world.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 80, 2, 2, 2, 0.1);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 4.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.5f);
            if (coned()) dropStack(new ItemStack(ModItems.TRAFFIC_CONE));
        }
        super.onDeath(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Taken away mid-fight (the attempt failed): nothing of his stays behind, and nobody is left held
        if (getWorld() instanceof ServerWorld world && reason.shouldDestroy()) clearAttacks(world);
        super.remove(reason);
    }

    private void clearAttacks(ServerWorld world) {
        for (LeonardHazards.Hazard h : hazards) h.remove();
        hazards.clear();
        beam = null;
        if (fallingCone != null) {
            fallingCone.discard();
            fallingCone = null;
        }
        if (boltOut) boltReturns();
        noClip = false;
        setAfterimages(false);
        if (isInvisible() && action != Action.ENTRANCE) setInvisible(false);
    }

    // ---- Helpers ----

    /** A spot near {@code want} he fits, standing on something (looking a little up and down). Null if there's none. */
    private Vec3d safeSpot(Vec3d want) {
        BlockPos base = BlockPos.ofFloored(want);
        for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5}) {
            BlockPos at = base.up(dy);
            double floor = groundBelow(Vec3d.ofBottomCenter(at).add(0, 0.2, 0), 2);
            if (Double.isNaN(floor)) continue;
            Vec3d feet = new Vec3d(want.x, floor, want.z);
            Box box = getDimensions(getPose()).getBoxAt(feet).contract(1.0E-6);
            if (!getWorld().isSpaceEmpty(this, box)) continue;
            if (Arenas.sinOf(getWorld()) == Sin.PRIDE && !PrideArena.inBounds(feet)) continue;
            return feet;
        }
        return null;
    }

    private void stand() {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
    }

    private void faceTowards(Vec3d pos) {
        if (LeonardHazards.flatDistance(pos, getPos()) < 1e-3) return;
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
