package net.ragnar.fmscraft.boss.sloth;

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
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
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
import net.ragnar.fmscraft.fight.Cinematics;
import net.ragnar.fmscraft.network.SlothSkyPayload;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.sound.ModSounds;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RICKY, the Sin of Sloth: Patrik, in his hoodie, asleep in the ruins of a flooded city at sunset. Awake, he's an earth
 * mage who won't stand still and won't come close: he walks backwards away from you, keeping his distance, always
 * facing you, and fights with the Ragnars Magic Mod's earth tomes (each held in his hand as he casts it):
 * <ul>
 *   <li><b>Tome of Rocks</b> (the basic one, the mod's own rocks), <b>Tome of Boulders</b> (the mod's own boulder),</li>
 *   <li><b>Tome of Rising Spikes</b>: two seconds crouched with a palm to the ground while three lines of cracks creep
 *       out, then three waves of spikes, one along each,</li>
 *   <li><b>Tome of Impaling</b> under you, the <b>Tome of the Stone Cannon</b> (fired fast, again and again: where each
 *       spike lands it beeps three times and blows), the <b>Tome of Smashing</b> three times in a row,</li>
 *   <li><b>Snake Spikes</b> (three seconds' charge, then four rows of spikes snake out over the ground, turning this way
 *       and that), the <b>Hands</b> (huge hands of shadow rise out of the ground and slam down flat) and the
 *       <b>Dashpunch</b> (he crouches, then he's on you, fist first, and you're thrown across the city).</li>
 * </ul>
 * At half health he goes Super Saiyan, timed by Kills: the sky drains to black and fills with red lights, rubble lifts
 * round him, his hair flickers gold, and on the drop it bursts. From then on he flies, teleports, and fights like an evil
 * Saiyan: the <b>black beam</b>, the <b>spirit bomb</b>, the <b>ki barrage</b>, the <b>rush</b>, the <b>meteor dive</b>,
 * the <b>rubble storm</b>, the <b>big bang</b>, and when he's desperate the <b>final beam</b>, swept all the way round:
 * get behind something. His snakes, hands and dashpunch stay, bigger.
 */
public class RickyEntity extends SinBossEntity implements RickyLook {
    public static final BossSong PHASE_ONE = new BossSong("fmscraft:music.sloth.phase_one", RickyTimeline.OVERSEAS_BPM, RickyTimeline.OVERSEAS_FIRST_BEAT);
    public static final BossSong PHASE_TWO = new BossSong("fmscraft:music.sloth.phase_two", RickyTimeline.KILLS_BPM, RickyTimeline.KILLS_FIRST_BEAT);

    // ---- Tuning knobs ----
    /** Full health, for 5-7 players; {@link RickyTimeline#healthScale} scales it. */
    public static final float MAX_HEALTH = 1000;
    /** Damage he takes is multiplied by this (on top of his armor), and capped per hit. */
    public static final float DAMAGE_TAKEN = 0.85f, SAIYAN_TAKEN = 0.9f, MAX_HIT = 26f;
    /** Every hit he deals is this much harder once he's Super Saiyan (rounded up). */
    public static final float SAIYAN_DEALT = 1.15f;
    private static final double SPEED = 0.3;
    /** He backs off when you're closer than the first, comes in when you're further than the second. */
    private static final double KEEP_NEAR = 6.5, KEEP_FAR = 12;
    /** How fast he walks backwards away from you, edges in, and sidesteps (blocks a tick). */
    private static final double BACKPEDAL = 0.15, APPROACH = 0.13, SIDESTEP = 0.08;
    /** Super Saiyan: how fast he flies at most (blocks a tick). */
    private static final double FLY_SPEED = 0.85;
    private static final int REST = 16, SAIYAN_REST = 12, DESPERATE_REST = 8;
    /** Super Saiyan: a hit while he's free is teleported out of this often (then not again for a while). */
    public static final float TELEPORT_DODGE = 0.2f;
    private static final int DODGE_COOLDOWN = 50;
    /** Below this share of his health he's desperate: shorter rests, and the final beam. */
    public static final float DESPERATE_AT = 0.25f;
    private static final float HEALTH_CAP = 1024f;

    private static final TrackedData<Integer> POSE = DataTracker.registerData(RickyEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SAIYAN = DataTracker.registerData(RickyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> AURA = DataTracker.registerData(RickyEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> AFTERIMAGES = DataTracker.registerData(RickyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    enum Action {
        ENTRANCE, MOVE, TRANSITION, TELEPORT,
        ROCKS, BOULDER, SPIKES, IMPALE, CANNON, SMASH, SNAKES, HANDS, DASH_PUNCH,
        BEAM, SPIRIT_BOMB, BARRAGE, RUSH, DIVE, RUBBLE, BIG_BANG, FINAL_BEAM, STRIKE
    }

    private Action action = Action.ENTRANCE;
    private int actionTicks, rest, meleeCooldown, dodgeCooldown, step, strafeTicks;
    private float strafeSide = 1;
    private Action lastAttack;
    private final Map<Action, Integer> lastUsed = new EnumMap<>(Action.class);
    private final Map<UUID, Integer> lastPicked = new HashMap<>();
    private final Map<UUID, Integer> straying = new HashMap<>();
    private final Set<UUID> struck = new HashSet<>();
    private final List<RickyHazards.Hazard> hazards = new ArrayList<>();
    private List<ServerPlayerEntity> fighters = List.of();
    private UUID focus;
    private int focusTicks;
    private boolean untouchable = true, scaled, heldStill, transitioned, killing, fromArena, songOn, killsOn;
    private float toughness = 1f;
    private int startTick = -1;
    private Vec3d home = SlothArena.CENTER;

    // The move in progress
    private Vec3d spot, dir, from, hover;
    private int hoverTicks;
    private final List<Vec3d> spots = new ArrayList<>();
    private final List<ServerPlayerEntity> targets = new ArrayList<>();
    private Entity heldBoulder;
    private RickyHazards.Boulder heldStone;
    private RickyHazards.Cannon heldCannon;
    private RickyHazards.Beam beam;
    private final List<RickyHazards.Rubble> rubble = new ArrayList<>();
    private final List<DisplayEntity.BlockDisplayEntity> lifted = new ArrayList<>();
    private DisplayEntity.BlockDisplayEntity bombCore, bombShell, bombHalo, cupBall;
    private float bombSpin;
    /** For tests: he hits as hard as the Super Saiyan does. */
    private boolean hitsLikeSaiyan;

    /** Where he was the last few ticks, on the client: his afterimages are drawn there. */
    public static final int TRAIL = 12;
    private final double[][] trail = new double[TRAIL][4];
    private int trailHead, trailCount;

    public RickyEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 1200;
        bossBar().setColor(BossBar.Color.RED);
        setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, HEALTH_CAP)
                .add(EntityAttributes.GENERIC_ARMOR, 8)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 4)
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
        builder.add(SAIYAN, false);
        builder.add(AURA, AURA_NONE);
        builder.add(AFTERIMAGES, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
    }

    @Override
    protected Text bossBarName() {
        return Text.literal("RICKY");
    }

    @Override
    protected BossSong songForPhase(int phase) {
        return phase == 1 ? PHASE_ONE : PHASE_TWO;
    }

    /** overseas starts with his entrance ({@link #tickEntrance}). */
    @Override
    protected boolean startsSongAtOnce() {
        return false;
    }

    /** Kills starts a moment into the transformation ({@link #tickTransition}). */
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
    public boolean saiyan() {
        return dataTracker.get(SAIYAN);
    }

    @Override
    public int aura() {
        return dataTracker.get(AURA);
    }

    @Override
    public boolean afterimages() {
        return dataTracker.get(AFTERIMAGES);
    }

    public boolean isUntouchable() {
        return untouchable || isInvisible();
    }

    public String currentAction() {
        return action.name();
    }

    /** For tests: what he's doing and for how long, and where. */
    public String describe() {
        return action + " for " + actionTicks + " ticks, age " + age + ", at " + getPos() + (isAlive() ? "" : ", dead") + (isRemoved() ? ", removed" : "");
    }

    /** True once he's gone Super Saiyan. */
    public boolean hasTransitioned() {
        return transitioned;
    }

    public float effectiveMaxHealth() {
        return getMaxHealth() / toughness;
    }

    /** For tests: between attacks he stands still, never dodges, and starts none of his own. */
    public void holdStill() {
        heldStill = true;
    }

    /** For tests: starts one of his attacks by name. */
    public void forceAttack(String name) {
        startAttack(Action.valueOf(name));
    }

    /** For tests: he hits as hard as he does once he's Super Saiyan, without the transformation. */
    public void hitLikeSaiyan() {
        hitsLikeSaiyan = true;
    }

    /** For tests: he's a Super Saiyan now (his look, his flight, his moves), without the transformation. */
    public void goSaiyan() {
        transitioned = true;
        setSaiyan(true);
        setAura(AURA_SAIYAN);
        setNoGravity(true);
        bossBar().setColor(BossBar.Color.YELLOW);
    }

    /** For tests: whether {@code player} can be seen (for his final beam) from where his beam comes out. */
    public boolean coveredFromBeam(ServerPlayerEntity player) {
        return RickyHazards.blocked((ServerWorld) getWorld(), getPos().add(0, 1.0, 0), player.getBoundingBox().getCenter());
    }

    /** For tests: how many of his hazards are out (spikes, hands, beams, rubble...). */
    public int hazardCount() {
        return hazards.size();
    }

    /** For tests: how many hazards of this kind ("Hand", "Snake", "Cannon"...) are out. */
    public int hazards(String kind) {
        int n = 0;
        for (RickyHazards.Hazard h : hazards) if (h.getClass().getSimpleName().equals(kind)) n++;
        return n;
    }

    private void setPoseState(int pose) {
        dataTracker.set(POSE, pose);
    }

    private void setAfterimages(boolean on) {
        dataTracker.set(AFTERIMAGES, on);
    }

    private void setSaiyan(boolean on) {
        if (dataTracker.get(SAIYAN) != on) dataTracker.set(SAIYAN, on);
    }

    private void setAura(int aura) {
        if (dataTracker.get(AURA) != aura) dataTracker.set(AURA, aura);
    }

    /** His hand, held a little out in front. */
    public Vec3d handPos(boolean right) {
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        Vec3d side = new Vec3d(-forward.z, 0, forward.x).multiply(right ? -0.38 : 0.38);
        return getPos().add(0, getHeight() * 0.72, 0).add(forward.multiply(0.55)).add(side);
    }

    /** The tome (or other thing) in his hand while he casts it; empty-handed otherwise. */
    private void hold(String tome) {
        equipStack(EquipmentSlot.MAINHAND, tome == null ? ItemStack.EMPTY : MagicSpells.itemOr(tome, Items.BOOK));
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

    /** A hit he deals: Super Saiyan's {@link #SAIYAN_DEALT} harder, rounded up. */
    float dealt(float damage) {
        return (float) Math.ceil(damage * (transitioned || hitsLikeSaiyan ? SAIYAN_DEALT : 1f));
    }

    /** The earth itself, the shadow, his ki: no shield helps (and armour doesn't, Protection does). */
    boolean crush(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().indirectMagic(this, this), dealt(damage));
    }

    /** Things he throws: a shield held towards him stops them. */
    boolean strike(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().mobProjectile(this, this), dealt(damage));
    }

    /** Something going off: Blast Protection helps. */
    boolean blast(ServerPlayerEntity player, float damage) {
        return player.damage(getDamageSources().explosion(this, this), dealt(damage));
    }

    /** His fists: a shield stops them. */
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

    void flash(ServerPlayerEntity player, float strength, int ticks) {
        Cinematics.flash(player, strength, ticks);
    }

    /** The magic mod's camera shake for everyone near {@code at} (nothing without it). */
    void shake(Vec3d at, double radius, float strength, int ticks) {
        if (getWorld() instanceof ServerWorld world) MagicSpells.shake(world, at, radius, strength, ticks);
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

    /** The sky over the city, for everyone near: 1 the sunset, 2 black with red lights. */
    private void sky(ServerWorld world, int phase) {
        for (ServerPlayerEntity p : audience(world)) sky(p, phase);
    }

    static void sky(ServerPlayerEntity player, int phase) {
        if (ServerPlayNetworking.canSend(player, SlothSkyPayload.ID)) ServerPlayNetworking.send(player, new SlothSkyPayload(phase));
    }

    Vec3d ground(Vec3d pos) {
        double g = groundBelow(pos.add(0, 2.5, 0), 16);
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
        if (attacker == this) return false;
        if (isUntouchable()) {
            if (attacker instanceof PlayerEntity) clink();
            return false;
        }
        // Super Saiyan: now and then he's simply not there when the blow lands
        if (getWorld() instanceof ServerWorld world && transitioned && attacker instanceof ServerPlayerEntity player
                && action == Action.MOVE && !heldStill && dodgeCooldown <= 0 && random.nextFloat() < TELEPORT_DODGE) {
            dodgeCooldown = DODGE_COOLDOWN;
            focus = player.getUuid();
            vanish(world, getPos());
            startAttack(Action.TELEPORT);
            return false;
        }
        amount *= (transitioned ? SAIYAN_TAKEN : DAMAGE_TAKEN) * toughness;
        amount = Math.min(amount, MAX_HIT);
        // Phase 1 can't be skipped: his health stops at half (setHealth), and at half nothing more gets through
        if (getPhase() == 1 && getHealth() <= getMaxHealth() * phaseTwoHealth()) amount = 0;
        if (amount <= 0) {
            clink();
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt && getWorld() instanceof ServerWorld world) {
            world.spawnParticles(transitioned ? RickyHazards.GOLD : RickyHazards.DARK_RED, getX(), getBodyY(0.6), getZ(), 10, 0.3, 0.5, 0.3, 0);
        }
        return hurt;
    }

    private void clink() {
        getWorld().playSound(null, getX(), getEyeY(), getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0f, 1.5f);
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
            world.spawnParticles(RickyHazards.DARK_RED, bubble.getX(), bubble.getY(), bubble.getZ(), 20, 0.5, 0.5, 0.5, 0);
            bubble.discard();
        }
    }

    @Override
    protected void tickPhase(int phase, int songTicks) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        // Anywhere but his city (a test world), only who's close: a neighbouring test's players are none of his business
        double reach = Arenas.sinOf(world) == Sin.SLOTH ? 160 : 12;
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
            case TELEPORT -> tickTeleport(world);
            case ROCKS -> tickRocks(world);
            case BOULDER -> tickBoulder(world);
            case SPIKES -> tickSpikes(world);
            case IMPALE -> tickImpale(world);
            case CANNON -> tickCannon(world);
            case SMASH -> tickSmash(world);
            case SNAKES -> tickSnakes(world);
            case HANDS -> tickHands(world);
            case DASH_PUNCH -> tickDashPunch(world);
            case BEAM -> tickBeam(world);
            case SPIRIT_BOMB -> tickSpiritBomb(world);
            case BARRAGE -> tickBarrage(world);
            case RUSH -> tickRush(world);
            case DIVE -> tickDive(world);
            case RUBBLE -> tickRubble(world);
            case BIG_BANG -> tickBigBang(world);
            case FINAL_BEAM -> tickFinalBeam(world);
            case STRIKE -> tickStrike(world);
        }
        tickHazards(world);
        if (!isInvisible()) tickAura(world);
        if (Arenas.sinOf(world) == Sin.SLOTH && action != Action.ENTRANCE) {
            tickAmbience(world);
            catchStrays(world);
        }
    }

    private void scaleHealth(ServerWorld world) {
        scaled = true;
        int players = world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 200 * 200).size();
        float wanted = MAX_HEALTH * RickyTimeline.healthScale(players);
        float max = Math.min(wanted, HEALTH_CAP);
        toughness = max / wanted;
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(max);
        setHealth(max);
    }

    @Override
    protected void onBeat(int phase, int beat) {
        // overseas is fast: he moves on every other beat of it; Kills is slow, every beat
        if (phase == 1 && beat % 2 != 0) return;
        if (action == Action.MOVE && rest <= 0 && !fighters.isEmpty() && !heldStill) startAttack(chooseAttack());
    }

    @Override
    protected void onPhaseTwo() {
        startAttack(Action.TRANSITION);
        stopSong(0);
    }

    // ---- Choosing attacks ----

    private boolean desperate() {
        return transitioned && getHealth() < getMaxHealth() * DESPERATE_AT;
    }

    private Action chooseAttack() {
        List<Action> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        double near = nearestDistance();
        int band = near < 5 ? 0 : near > 16 ? 2 : 1;
        int crowd = 0;
        for (ServerPlayerEntity p : fighters) if (p.distanceTo(this) < 7) crowd++;
        if (!transitioned) {
            offer(options, weights, Action.ROCKS, new int[]{2, 4, 5}[band], 40);
            offer(options, weights, Action.BOULDER, new int[]{1, 3, 4}[band], 140);
            offer(options, weights, Action.SPIKES, new int[]{3, 4, 3}[band], 180);
            offer(options, weights, Action.IMPALE, new int[]{1, 3, 4}[band], 120);
            offer(options, weights, Action.CANNON, new int[]{1, 3, 4}[band], 220);
            offer(options, weights, Action.SMASH, new int[]{3, 3, 2}[band] + (crowd >= 2 ? 2 : 0), 260);
            offer(options, weights, Action.SNAKES, new int[]{3, 3, 2}[band], 420);
            offer(options, weights, Action.HANDS, 3, 380);
            offer(options, weights, Action.DASH_PUNCH, new int[]{4, 3, 2}[band], 160);
        } else {
            offer(options, weights, Action.BEAM, new int[]{1, 3, 4}[band], 200);
            offer(options, weights, Action.BARRAGE, new int[]{1, 3, 4}[band], 140);
            offer(options, weights, Action.RUSH, fighters.size() > 1 ? 3 : 2, 240);
            offer(options, weights, Action.DIVE, new int[]{2, 3, 3}[band], 240);
            offer(options, weights, Action.RUBBLE, new int[]{2, 3, 3}[band], 260);
            offer(options, weights, Action.BIG_BANG, new int[]{6, 1, 0}[band] + (crowd >= 2 ? 3 : 0), 200);
            offer(options, weights, Action.SNAKES, 3, 440);
            offer(options, weights, Action.HANDS, 3, 400);
            offer(options, weights, Action.DASH_PUNCH, new int[]{3, 3, 3}[band], 160);
            offer(options, weights, Action.STRIKE, new int[]{4, 3, 2}[band], 90);
            offer(options, weights, Action.SPIRIT_BOMB, 2, 640);
            if (desperate()) offer(options, weights, Action.FINAL_BEAM, 4, 900);
        }
        if (options.isEmpty()) return transitioned ? Action.BARRAGE : Action.ROCKS;
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
        targets.clear();
        if (attack != Action.TRANSITION && attack != Action.TELEPORT) {
            lastAttack = attack;
            lastUsed.put(attack, age);
        }
        getNavigation().stop();
    }

    private void endAttack() {
        endAttack(desperate() ? DESPERATE_REST : transitioned ? SAIYAN_REST : REST);
    }

    private void endAttack(int restTicks) {
        action = Action.MOVE;
        actionTicks = 0;
        setPoseState(transitioned ? POSE_FLY : POSE_IDLE);
        if (!transitioned) setNoGravity(false);
        noClip = false;
        setAfterimages(false);
        if (isInvisible()) setInvisible(false);
        hold(null);
        rest = restTicks;
        hover = null;
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
        return Arenas.sinOf(world) == Sin.SLOTH ? SlothArena.CENTER : home;
    }

    /**
     * Where he can go: anywhere in the fight in his city; anywhere else (a test world) no further than 4 blocks from where
     * he started, so his leaps and dashes never carry him off onto ground that isn't ticking.
     */
    private Vec3d keepNear(ServerWorld world, Vec3d want) {
        if (Arenas.sinOf(world) == Sin.SLOTH) return SlothArena.inBounds(want) ? want : ground(SlothArena.returnSpot(want));
        double d = RickyHazards.flatDistance(want, home);
        if (d <= 4) return want;
        Vec3d flat = want.subtract(home).multiply(1, 0, 1).normalize().multiply(4);
        return ground(home.add(flat).add(0, want.y - home.y, 0));
    }

    /** Where {@code p} will be in {@code ticks}, if they keep going as they are (on the ground). */
    private static Vec3d lead(ServerPlayerEntity p, double ticks) {
        Vec3d v = p.getVelocity().multiply(1, 0, 1);
        if (v.lengthSquared() > 0.25) v = v.normalize().multiply(0.5);
        return p.getPos().add(v.multiply(ticks));
    }

    // ---- The arrival: asleep in the ruins; he wakes with the song and is loose on the drop ----

    private void tickEntrance(ServerWorld world, int songTicks) {
        if (startTick < 0) {
            startTick = age;
            fromArena = Arenas.sinOf(world) == Sin.SLOTH && SlothArena.flatDistance(getPos(), SlothArena.BOSS_SPAWN) < 3;
            home = fromArena ? SlothArena.CENTER : ground(getPos());
            untouchable = true;
            playSong(PHASE_ONE, 0);
            songOn = true;
            if (fromArena) {
                Vec3d at = ground(getPos());
                refreshPositionAndAngles(at.x, at.y, at.z, -90f, 0f);
                faceYaw(-90f);
                setPoseState(POSE_SLEEP);
                bossBar().setVisible(false);
                sky(world, 1);
            }
        }
        int s = songTicks;
        int wake = fromArena ? RickyTimeline.DROP : 1;
        setVelocity(0, getVelocity().y, 0);
        if (fromArena && s < wake) {
            if (s == RickyTimeline.REVEAL_TICKS) for (ServerPlayerEntity p : audience(world)) Cinematics.unfreeze(p);
            Vec3d head = getPos().add(0, 0.5, 0).add(Vec3d.fromPolar(0, getYaw()).multiply(0.9));
            if (s < RickyTimeline.SIT) {
                // Fast asleep: a slow breath, a snore now and then
                if (s % 64 == 20) world.playSound(null, getX(), getY(), getZ(), ModSounds.MOTHER_WARDEN_SNORE, SoundCategory.HOSTILE, 0.7f, 1.7f);
                if (s % 16 == 0) world.spawnParticles(RickyHazards.PALE_GOLD, head.x, head.y + 0.4, head.z, 1, 0.1, 0.1, 0.1, 0);
                if (s == RickyTimeline.STIR) {
                    faceYaw(getYaw() + 25f);
                    world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.HOSTILE, 0.6f, 0.6f);
                    world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_GRASS_STEP, SoundCategory.HOSTILE, 1.0f, 0.8f);
                }
            } else if (s < RickyTimeline.STAND) {
                if (s == RickyTimeline.SIT) {
                    setPoseState(POSE_SIT);
                    world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_POLAR_BEAR_AMBIENT_BABY, SoundCategory.HOSTILE, 1.0f, 0.6f);
                }
                ServerPlayerEntity near = nearestFighter();
                if (near != null) turnTowards(near.getPos(), 2f);
            } else if (s < RickyTimeline.GATHER) {
                if (s == RickyTimeline.STAND) {
                    setPoseState(POSE_STRETCH);
                    world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.HOSTILE, 0.5f, 1.6f);
                    world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PANDA_SNEEZE, SoundCategory.HOSTILE, 0.4f, 0.5f);
                }
                ServerPlayerEntity near = nearestFighter();
                if (near != null) turnTowards(near.getPos(), 4f);
            } else {
                // The dark gathers round him, and the ground starts to tremble
                if (s == RickyTimeline.GATHER) {
                    setPoseState(POSE_POWER);
                    setAura(AURA_DARK);
                    world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
                }
                double k = (s - RickyTimeline.GATHER) / (double) (wake - RickyTimeline.GATHER);
                for (int i = 0; i < 3 + (int) (k * 8); i++) {
                    Vec3d o = new Vec3d(random.nextGaussian(), random.nextDouble() * 0.6, random.nextGaussian()).normalize().multiply(5 - 3.5 * k);
                    Vec3d p = getPos().add(0, 1, 0).add(o);
                    world.spawnParticles(RickyHazards.DARK_RED, p.x, p.y, p.z, 0, -o.x, -o.y, -o.z, 0.15);
                }
                if (s % 6 == 0) {
                    BlockState under = RickyHazards.groundState(world, getBlockPos().down());
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), getX(), getY() + 0.1, getZ(), 12, 2.5, 0.05, 2.5, 0.1);
                }
            }
            return;
        }
        if (s == wake) awaken(world, fromArena);
        if (s >= wake + RickyTimeline.READY_AFTER) ready();
    }

    /** On the drop: he stamps, and the dark goes out of him in a wave that throws everyone back. */
    private void awaken(ServerWorld world, boolean big) {
        setPoseState(POSE_SMASH);
        setAura(AURA_DARK);
        bossBar().setVisible(true);
        Vec3d c = ground(getPos());
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.unfreeze(p);
            if (big) Cinematics.flash(p, 0.5f, 18);
            double d = p.getPos().distanceTo(c);
            if (d < 16 && fighters.contains(p)) launch(p, p.getPos().subtract(c), 1.8 * (1 - d / 18), 0.55);
            p.playSoundToPlayer(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.8f, 0.6f);
            p.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.4f, 0.6f);
        }
        hazards.add(new RickyHazards.Quake(c, 1, 1.3, 20, 0, false));
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(RickyHazards.DARK_RED, c.x, c.y + 1, c.z, 200, 3, 1.5, 3, 0);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, 60, 3, 0.4, 3, 0.1);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, RickyHazards.groundState(world, BlockPos.ofFloored(c).down())), c.x, c.y + 0.3, c.z, 120, 3, 0.3, 3, 0.3);
        shake(c, 20, 0.8f, 16);
    }

    private void ready() {
        untouchable = false;
        bossBar().setVisible(true);
        for (Action big : new Action[]{Action.SNAKES, Action.HANDS, Action.CANNON}) lastUsed.put(big, age - 200);
        endAttack(10);
    }

    // ---- Half health: the sky goes black, he powers up, and on Kills' drop he's a Super Saiyan ----

    private void tickTransition(ServerWorld world, int songTicks) {
        int t = actionTicks;
        if (t == 1) {
            untouchable = true;
            clearAttacks(world);
            getNavigation().stop();
            setVelocity(Vec3d.ZERO);
            setNoGravity(true);
            setPoseState(POSE_POWER);
            setAura(AURA_DARK);
            home = Arenas.sinOf(world) == Sin.SLOTH ? ground(getPos()) : ground(getPos());
            sky(world, 2);
            bossBar().setColor(BossBar.Color.YELLOW);
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.freeze(p);
                Cinematics.flash(p, 0.5f, 20);
                p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.5f, 0.5f);
                p.playSoundToPlayer(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD.value(), SoundCategory.HOSTILE, 1.5f, 0.6f);
            }
        }
        if (!killsOn) {
            stay(home);
            if (t >= RickyTimeline.KILLS_AT) {
                killsOn = true;
                playSong(PHASE_TWO, 0);
            }
            return;
        }
        int s = songTicks;
        double strain = RickyTimeline.strain(s);
        if (s == RickyTimeline.FREE_AT) for (ServerPlayerEntity p : audience(world)) Cinematics.unfreeze(p);
        if (s < RickyTimeline.KILLS_DROP) {
            Vec3d at = home.add(0, RickyTimeline.riseHeight(s), 0);
            if (strain > 0.6) at = at.add((random.nextDouble() - 0.5) * 0.12 * strain, 0, (random.nextDouble() - 0.5) * 0.12 * strain);
            stay(at);
            faceYaw(getYaw() + 0.6f);
            setPoseState(POSE_POWER);
            boolean gold = RickyTimeline.flicker(s);
            setSaiyan(gold);
            setAura(gold ? AURA_SAIYAN : AURA_DARK);
            powerUp(world, s, strain);
            if (s == RickyTimeline.POWERUP_AT) {
                world.playSound(null, getX(), getY(), getZ(), ModSounds.RICKY_POWERUP, SoundCategory.HOSTILE, 4.0f, 1.0f);
                for (ServerPlayerEntity p : audience(world)) {
                    p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.6f, 0.6f);
                }
            }
            return;
        }
        if (s == RickyTimeline.KILLS_DROP) burst(world);
        stay(home);
        if (s >= RickyTimeline.KILLS_DROP + RickyTimeline.LOOSE_AFTER) {
            untouchable = false;
            for (Action big : new Action[]{Action.SPIRIT_BOMB, Action.FINAL_BEAM}) lastUsed.put(big, age);
            for (Action big : new Action[]{Action.SNAKES, Action.HANDS, Action.RUBBLE}) lastUsed.put(big, age - 200);
            endAttack(10);
        }
    }

    /** Holds him at {@code at}, out of the way of anything that would move him. */
    private void stay(Vec3d at) {
        setVelocity(Vec3d.ZERO);
        setPosition(at.x, at.y, at.z);
    }

    /** Powering up: the dark drawn into him, rubble lifting off the ground all round, sparks, the ground shaking. */
    private void powerUp(ServerWorld world, int s, double strain) {
        Vec3d c = getPos().add(0, 1, 0);
        int n = 3 + (int) (strain * 10);
        for (int i = 0; i < n; i++) {
            Vec3d o = new Vec3d(random.nextGaussian(), random.nextGaussian() * 0.5, random.nextGaussian()).normalize().multiply(7 - 4 * strain);
            Vec3d p = c.add(o);
            world.spawnParticles(saiyan() ? RickyHazards.GOLD : RickyHazards.DARK_RED, p.x, p.y, p.z, 0, -o.x, -o.y, -o.z, 0.12 + 0.1 * strain);
        }
        if (s % 3 == 0 && strain > 0.2) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, (int) (strain * 12), 0.6, 1.0, 0.6, 0.3);
        // Rubble breaks loose from the ground round him and rises
        if (s % Math.max(2, 10 - (int) (strain * 8)) == 0 && lifted.size() < 40) {
            double a = random.nextDouble() * MathHelper.TAU, r = 3 + random.nextDouble() * 9;
            Vec3d g = ground(home.add(Math.cos(a) * r, 2, Math.sin(a) * r));
            BlockState under = RickyHazards.groundState(world, BlockPos.ofFloored(g).down());
            DisplayEntity.BlockDisplayEntity chunk = RickyHazards.blockDisplay(world, under, g.add(0, 0.3, 0));
            if (chunk != null) {
                chunk.setViewRange(6f);
                chunk.setTransformation(RickyHazards.spinning(random.nextFloat() * 6, 0.3f + random.nextFloat() * 0.6f));
                lifted.add(chunk);
            }
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), g.x, g.y + 0.1, g.z, 10, 0.3, 0.05, 0.3, 0.1);
        }
        for (DisplayEntity.BlockDisplayEntity chunk : lifted) {
            Vec3d p = chunk.getPos().add(0, 0.04 + 0.12 * strain, 0);
            RickyHazards.place(chunk, p, RickyHazards.spinning((chunk.age + chunk.getId()) * 0.1f, 0.3f + (chunk.getId() % 7) * 0.08f), 1);
        }
        if (s % 8 == 0) {
            BlockState under = RickyHazards.groundState(world, BlockPos.ofFloored(home).down());
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), home.x, home.y + 0.1, home.z, (int) (10 + strain * 40), 4, 0.05, 4, 0.2);
            if (strain > 0.3) shake(home, 14, (float) (0.15 + 0.4 * strain), 10);
        }
        if (s % 20 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0f, 0.6f + (float) strain * 0.6f);
        if (s % 9 == 0 && strain > 0.4) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.4f, 0.5f + (float) strain);
    }

    /** The drop: Super Saiyan. A pillar of gold, a flash, a blast that throws everyone back, the rubble flung away. */
    private void burst(ServerWorld world) {
        transitioned = true;
        setSaiyan(true);
        setAura(AURA_SAIYAN);
        setPoseState(POSE_SPREAD);
        Vec3d c = home;
        for (DisplayEntity.BlockDisplayEntity chunk : lifted) {
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, chunk.getBlockState()), chunk.getX(), chunk.getY(), chunk.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
            chunk.discard();
        }
        lifted.clear();
        for (ServerPlayerEntity p : audience(world)) {
            Cinematics.unfreeze(p);
            Cinematics.flash(p, 1f, 30);
            double d = p.getPos().distanceTo(c);
            if (d < 20 && fighters.contains(p)) launch(p, p.getPos().subtract(c), 2.2 * (1 - d / 22), 0.7);
            p.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.5f);
            p.playSoundToPlayer(SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
            p.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.5f, 0.8f);
        }
        hazards.add(new RickyHazards.Quake(c, 1, 1.4, 24, 0, true));
        for (int y = 0; y < 30; y += 2) world.spawnParticles(RickyHazards.GOLD, c.x, c.y + y, c.z, 20, 0.8, 1, 0.8, 0);
        world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 200, 1.5, 3, 1.5, 0.4);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 2, 1, 0.5, 1, 0);
        world.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 3, 0.5, 0.5, 0.5, 0);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 200, 3, 2, 3, 0.8);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, RickyHazards.groundState(world, BlockPos.ofFloored(c).down())), c.x, c.y + 0.3, c.z, 200, 4, 0.4, 4, 0.4);
        shake(c, 30, 1f, 30);
    }

    // ---- Between attacks ----

    private void tickMove(ServerWorld world) {
        if (heldStill) {
            stand();
            if (transitioned) stay(getPos());
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
            if (transitioned) stay(getPos());
            return;
        }
        setTarget(target);
        if (transitioned) {
            fly(world, target);
            return;
        }
        walkBackwards(world, target);
    }

    /**
     * Phase 1: always facing whoever he's after, he walks backwards away from them when they come close and edges in
     * when they're far, sidestepping in between. Cornered, he punches and hops back.
     */
    private void walkBackwards(ServerWorld world, ServerPlayerEntity target) {
        faceTowards(target.getPos());
        getLookControl().lookAt(target, 40f, 40f);
        double d = distanceTo(target);
        if (--strafeTicks <= 0) {
            strafeTicks = 30 + random.nextInt(40);
            strafeSide = random.nextBoolean() ? 1 : -1;
        }
        boolean nearEdge = Arenas.sinOf(world) == Sin.SLOTH && SlothArena.flatDistance(getPos(), SlothArena.CENTER) > SlothArena.RADIUS - 8;
        if (nearEdge) {
            getNavigation().startMovingTo(SlothArena.CENTER.x, SlothArena.CENTER.y, SlothArena.CENTER.z, 1.1);
        } else if (d > KEEP_FAR + 6) {
            if (age % 6 == 0 || getNavigation().isIdle()) getNavigation().startMovingTo(target, 1.0);
        } else {
            // Walking backwards (or edging in), sidestepping, never taking his eyes off them
            getNavigation().stop();
            double forward = d < KEEP_NEAR ? -BACKPEDAL : d > KEEP_FAR ? APPROACH : 0;
            Vec3d ahead = Vec3d.fromPolar(0, getYaw()), right = new Vec3d(-ahead.z, 0, ahead.x);
            Vec3d v = ahead.multiply(forward).add(right.multiply(strafeSide * SIDESTEP));
            setVelocity(v.x, getVelocity().y, v.z);
            if (horizontalCollision && age % 10 == 0) {
                strafeSide = -strafeSide;
                if (isOnGround()) setVelocity(getVelocity().add(0, 0.42, 0));
            }
        }
        if (meleeCooldown <= 0 && d < 2.8 && Math.abs(target.getY() - getY()) < 2.5) {
            boolean left = random.nextBoolean();
            setPoseState(left ? POSE_JAB_B : POSE_JAB_A);
            swingHand(left ? Hand.OFF_HAND : Hand.MAIN_HAND);
            if (smack(target, 9)) launch(target, target.getPos().subtract(getPos()), 0.8, 0.35);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.HOSTILE, 1.3f, 0.9f);
            world.spawnParticles(RickyHazards.DARK_RED, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.4, 0.3, 0);
            // And a hop back out of reach
            Vec3d back = getPos().subtract(target.getPos()).multiply(1, 0, 1);
            if (back.lengthSquared() > 1e-4 && isOnGround()) setVelocity(back.normalize().multiply(0.7).add(0, 0.38, 0));
            meleeCooldown = 22;
        } else if (meleeCooldown < 14 && poseState() != POSE_IDLE) {
            setPoseState(POSE_IDLE);
        }
    }

    /**
     * Super Saiyan: he flies, zipping from one spot to the next round whoever he's after, a few blocks up, hard to pin
     * down; now and then he swoops in low to punch.
     */
    private void fly(ServerWorld world, ServerPlayerEntity target) {
        setNoGravity(true);
        faceTowards(target.getPos());
        getLookControl().lookAt(target, 40f, 40f);
        double d = distanceTo(target);
        if (hover == null || --hoverTicks <= 0 || getPos().squaredDistanceTo(hover) < 1.0) {
            boolean swoop = meleeCooldown <= 0 && random.nextFloat() < 0.35f;
            double a = random.nextDouble() * MathHelper.TAU, r = swoop ? 1.6 : 6 + random.nextDouble() * 6;
            Vec3d want = target.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Vec3d g = ground(want);
            hover = keepNear(world, g).add(0, swoop ? 0.6 : 1.8 + random.nextDouble() * 3, 0);
            hoverTicks = 20 + random.nextInt(25);
        }
        Vec3d to = hover.subtract(getPos());
        double len = to.length();
        Vec3d v = len < 1e-3 ? Vec3d.ZERO : to.multiply(Math.min(FLY_SPEED, 0.18 + len * 0.12) / len);
        // Eased: he swings into each new heading rather than snapping to it
        setVelocity(getVelocity().multiply(0.6).add(v.multiply(0.4)));
        velocityDirty = true;
        setPoseState(len > 3 ? POSE_FLY : POSE_IDLE);
        if (meleeCooldown <= 0 && d < 3.2) {
            boolean left = random.nextBoolean();
            setPoseState(left ? POSE_JAB_B : POSE_JAB_A);
            swingHand(left ? Hand.OFF_HAND : Hand.MAIN_HAND);
            if (smack(target, 12)) launch(target, target.getPos().subtract(getPos()), 1.4, 0.5);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.4f, 0.8f);
            world.spawnParticles(RickyHazards.GOLD, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.4, 0.3, 0);
            meleeCooldown = 26;
            hover = null;
        }
    }

    // ---- Teleport: gone with a flicker, and he's behind you ----

    private void vanish(ServerWorld world, Vec3d at) {
        world.playSound(null, at.x, at.y, at.z, ModSounds.RICKY_TELEPORT, SoundCategory.HOSTILE, 2.0f, 1.0f);
        world.spawnParticles(RickyHazards.GOLD, at.x, at.y + 1, at.z, 20, 0.3, 0.8, 0.3, 0);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 10, 0.2, 0.6, 0.2, 0.05);
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
    }

    /** The teleport he dodges with: gone, then out of nowhere behind whoever hit him, punching. */
    private void tickTeleport(ServerWorld world) {
        int t = actionTicks;
        if (t == 1) {
            setInvisible(true);
            setVelocity(Vec3d.ZERO);
        }
        stay(getPos());
        if (t < 7) return;
        ServerPlayerEntity target = fighter(focus);
        if (target == null) target = nearestFighter();
        if (t == 7) {
            Vec3d at = target != null ? behind(target, 1.8) : getPos();
            at = keepNear(world, at == null ? getPos() : at);
            refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
            setInvisible(false);
            vanish(world, at);
            if (target != null) faceTowards(target.getPos());
        }
        if (t == 9 && target != null && distanceTo(target) < 3.6) {
            setPoseState(POSE_JAB_A);
            swingHand(Hand.MAIN_HAND);
            if (smack(target, 12)) launch(target, target.getPos().subtract(getPos()), 1.6, 0.55);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.5f, 0.7f);
        }
        if (t >= 16) endAttack(6);
    }

    /** A spot {@code back} blocks behind {@code p} (from where they look), at their height, that he fits; null if none. */
    private Vec3d behind(ServerPlayerEntity p, double back) {
        Vec3d look = Vec3d.fromPolar(0, p.getYaw());
        for (double k : new double[]{back, back * 0.6, -back}) {
            Vec3d at = safeSpot(p.getPos().subtract(look.multiply(k)));
            if (at != null) return at;
        }
        return null;
    }

    // ---- Tome of Rocks: the basic one. Three rocks, one after another ----

    private void tickRocks(ServerWorld world) {
        int t = actionTicks, count = 3;
        stand();
        if (t == 1) {
            hold("tome_of_rocks");
            setPoseState(POSE_THROW);
        }
        for (int i = 0; i < count; i++) {
            if (t != 6 + i * 7) continue;
            ServerPlayerEntity target = i == 0 ? fighter(focus) : pick(null);
            if (target == null) target = nearestFighter();
            if (target == null) break;
            faceTowards(target.getPos());
            swingHand(Hand.MAIN_HAND);
            Vec3d from = getEyePos().add(Vec3d.fromPolar(0, getYaw()).multiply(0.6));
            Vec3d to = lead(target, from.distanceTo(target.getPos()) / 1.6).add(0, 1.0, 0);
            Vec3d v = lob(from, to, 1.6, 0.05);
            if (MagicSpells.rock(world, this, from, v) == null) {
                RickyHazards.Boulder rock = new RickyHazards.Boulder(world, from, 0.45f, 7, 3);
                rock.launch(v);
                hazards.add(rock);
            }
            world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 0.8f, 1.2f);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (target != null && t < 6 + count * 7) turnTowards(target.getPos(), 15f);
        if (t >= 6 + count * 7 + 6) endAttack();
    }

    /** A throw from {@code from} that comes down on {@code to}, at about {@code speed}, under {@code gravity} a tick. */
    static Vec3d lob(Vec3d from, Vec3d to, double speed, double gravity) {
        Vec3d d = to.subtract(from);
        double ticks = Math.max(d.length() / speed, 1.0);
        return d.multiply(1.0 / ticks).add(0, gravity * ticks * 0.5, 0);
    }

    // ---- Tome of Boulders: a boulder forms over his head and he hurls it ----

    private void tickBoulder(ServerWorld world) {
        int t = actionTicks, form = 16;
        stand();
        Vec3d over = getPos().add(0, getHeight() + 1.4, 0).add(Vec3d.fromPolar(0, getYaw()).multiply(0.8));
        ServerPlayerEntity target = fighter(focus);
        if (t == 1) {
            target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            hold("tome_of_boulders");
            setPoseState(POSE_RAISE);
            heldBoulder = MagicSpells.boulder(world, getUuid(), over.subtract(0, 0.9, 0));
            if (heldBoulder == null) {
                heldStone = new RickyHazards.Boulder(world, over, 1.0f, 12, 9);
                hazards.add(heldStone);
            }
            world.playSound(null, over.x, over.y, over.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 1.5f, 0.5f);
            world.playSound(null, over.x, over.y, over.z, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.HOSTILE, 1.2f, 0.5f);
            world.playSound(null, over.x, over.y, over.z, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 0.6f, 0.6f);
        }
        if (target != null) faceTowards(target.getPos());
        if (t < form) {
            if (heldBoulder != null) heldBoulder.setPosition(over.subtract(0, 0.9, 0));
            if (heldStone != null) heldStone.hold(over, Math.min(1f, 0.15f + t / (float) form));
            for (int i = 0; i < 4; i++) {
                Vec3d off = new Vec3d(random.nextGaussian(), Math.abs(random.nextGaussian()) + 0.3, random.nextGaussian()).normalize().multiply(2.2);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.STONE.getDefaultState()),
                        over.x + off.x, over.y + off.y, over.z + off.z, 0, -off.x, -off.y, -off.z, 0.2);
            }
            if (t % 2 == 1) world.playSound(null, over.x, over.y, over.z, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 1.0f, 0.5f + t * 0.03f);
            return;
        }
        if (t == form) {
            setPoseState(POSE_THROW);
            swingHand(Hand.MAIN_HAND);
            Vec3d aim = target != null ? lead(target, 14) : getPos().add(Vec3d.fromPolar(0, getYaw()).multiply(12));
            if (heldBoulder != null) {
                Vec3d v = lob(heldBoulder.getPos(), aim, 1.3, 0.05);
                if (!MagicSpells.throwBoulder(heldBoulder, v)) heldBoulder.discard();
            } else if (heldStone != null) {
                heldStone.launch(lob(over, aim, 1.3, 0.05));
            }
            heldBoulder = null;
            heldStone = null;
            world.playSound(null, over.x, over.y, over.z, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.55f);
            world.playSound(null, over.x, over.y, over.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.2f, 0.5f);
            world.playSound(null, over.x, over.y, over.z, SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.5f, 0.4f);
        }
        if (t >= form + 14) endAttack();
    }

    // ---- Tome of Rising Spikes: two seconds' charge, the cracks creeping out three ways, then three waves of spikes ----

    private void tickSpikes(ServerWorld world) {
        int t = actionTicks, charge = 40;
        stand();
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            faceTowards(target.getPos());
            hold("tome_rising_spikes");
            setPoseState(POSE_GROUND);
            Vec3d at = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            Vec3d d = at.lengthSquared() > 1e-3 ? at.normalize() : Vec3d.fromPolar(0, getYaw());
            spots.add(d.rotateY((float) Math.toRadians(-40)));
            spots.add(d);
            spots.add(d.rotateY((float) Math.toRadians(40)));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (t < charge) {
            // The ground cracks open along the three lines, further and further out, glowing dark red
            Vec3d g = ground(getPos());
            double reach = 2 + RickyHazards.SpikeWave.LENGTH * (t / (double) charge);
            BlockState under = RickyHazards.groundState(world, getBlockPos().down());
            for (Vec3d d : spots) {
                for (int i = 0; i < 3; i++) {
                    double along = 1.5 + random.nextDouble() * reach;
                    Vec3d p = g.add(d.multiply(along));
                    world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), p.x, p.y + 0.05, p.z, 1, 0.15, 0.02, 0.15, 0.02);
                }
                Vec3d tip = g.add(d.multiply(reach));
                world.spawnParticles(RickyHazards.DARK_RED, tip.x, tip.y + 0.1, tip.z, 2, 0.2, 0.05, 0.2, 0);
                world.spawnParticles(RickyHazards.DARK_RED, g.x + d.x * reach * 0.5, g.y + 0.1, g.z + d.z * reach * 0.5, 1, 0.3, 0.02, 0.3, 0);
            }
            Vec3d h = handPos(true);
            world.spawnParticles(RickyHazards.DARK_RED, h.x, h.y - 0.6, h.z, 3, 0.3, 0.1, 0.3, 0);
            if (t % 5 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_POINTED_DRIPSTONE_BREAK, SoundCategory.HOSTILE, 1.4f, 0.5f + t * 0.02f);
            if (t == charge - 12) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.4f, 0.7f);
            return;
        }
        if (t == charge) {
            Vec3d g = ground(getPos());
            for (Vec3d d : spots) hazards.add(new RickyHazards.SpikeWave(g, d, 12));
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 1.5f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.0f, 0.7f);
            world.spawnParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.3, g.z, 1, 0, 0, 0, 0);
            shake(g, 10, 0.3f, 8);
        }
        if (t >= charge + 18) endAttack();
    }

    // ---- Tome of Impaling: under two or three of you, one after another ----

    private void tickImpale(ServerWorld world) {
        int t = actionTicks, count = Math.min(fighters.size(), fighters.size() >= 4 ? 3 : 2);
        stand();
        if (t == 1) {
            hold("tome_impaling");
            setPoseState(POSE_POINT);
        }
        for (int i = 0; i < Math.max(1, count); i++) {
            if (t != 4 + i * 8) continue;
            ServerPlayerEntity target = pick(null);
            if (target == null) break;
            faceTowards(target.getPos());
            swingHand(Hand.MAIN_HAND);
            Vec3d at = ground(lead(target, 4));
            hazards.add(new RickyHazards.Impale(world, at, 18, 8));
        }
        if (t >= 4 + Math.max(1, count) * 8 + 10) endAttack();
    }

    // ---- Tome of the Stone Cannon: fired fast, again and again. Where each lands: beep, beep, beep, boom ----

    private void tickCannon(ServerWorld world) {
        int shots = 7, first = 16, every = 6;
        int t = actionTicks;
        stand();
        if (t == 1) {
            hold("tome_of_the_stone_cannon");
            setPoseState(POSE_CAST);
            ServerPlayerEntity target = pick(null);
            focus = target == null ? null : target.getUuid();
        }
        ServerPlayerEntity target = fighter(focus);
        if (target == null) target = nearestFighter();
        if (target == null) {
            endAttack();
            return;
        }
        turnTowards(target.getPos(), 14f);
        Vec3d holdAt = cannonHold();
        int k = t <= first ? 0 : (t - first) / every + 1;
        // A new spike forms beside him as soon as the last one goes
        if (heldCannon == null && k < shots && (t == 1 || (t - first) % every == 1)) {
            heldCannon = new RickyHazards.Cannon(world, holdAt, Vec3d.fromPolar(0, getYaw()), t == 1 ? first : every - 1, 10, 12);
            hazards.add(heldCannon);
        }
        if (heldCannon != null) {
            // Aimed at whoever's turn it is: everyone in turn, with a little spread and lead
            ServerPlayerEntity aimAt = fighters.isEmpty() ? target : fighters.get(k % fighters.size());
            Vec3d aim = lead(aimAt, holdAt.distanceTo(aimAt.getPos()) / RickyHazards.Cannon.SPEED).add(0, 0.9, 0)
                    .add(random.nextGaussian() * 0.6, random.nextGaussian() * 0.3, random.nextGaussian() * 0.6);
            heldCannon.hold(holdAt, aim.subtract(holdAt));
            if (heldCannon.stage != RickyHazards.Cannon.Stage.CHARGING || heldCannon.age >= heldCannon.charge - 1) {
                setPoseState(k % 2 == 0 ? POSE_CAST : POSE_THROW);
                swingHand(Hand.MAIN_HAND);
                heldCannon = null;
            }
        }
        if (t >= first + shots * every + 8) {
            heldCannon = null;
            endAttack();
        }
    }

    private Vec3d cannonHold() {
        double yaw = Math.toRadians(getYaw());
        Vec3d forward = Vec3d.fromPolar(0, getYaw());
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        return getEyePos().add(forward.multiply(1.1)).add(right.multiply(0.6)).add(0, -0.3, 0);
    }

    // ---- Tome of Smashing, three times: up into the air, and down on you ----

    private void tickSmash(ServerWorld world) {
        int rise = 14, hang = 5, fall = 4, pause = 7, each = rise + hang + fall + pause, smashes = transitioned ? 4 : 3;
        int t = actionTicks - 1, k = t / each, s = t % each;
        if (k >= smashes) {
            setNoGravity(false);
            endAttack();
            return;
        }
        if (s == 0) {
            ServerPlayerEntity target = k == 0 ? pick(null) : pick(focus);
            if (target == null) {
                setNoGravity(false);
                endAttack();
                return;
            }
            focus = target.getUuid();
            hold("tome_of_smashing");
            from = getPos();
            spot = keepNear(world, ground(lead(target, rise + hang)));
            setNoGravity(true);
            setPoseState(POSE_SMASH);
            faceTowards(spot);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WIND_CHARGE_THROW, SoundCategory.HOSTILE, 1.4f, 0.8f);
            world.spawnParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 12, 0.3, 0.1, 0.3, 0.1);
        }
        if (spot == null || from == null) return;
        double peak = 9;
        if (s < rise) {
            double u = (s + 1) / (double) rise;
            Vec3d flat = from.lerp(spot, u * 0.85);
            stay(new Vec3d(flat.x, MathHelper.lerp(1 - (1 - u) * (1 - u), from.y, Math.max(from.y, spot.y) + peak), flat.z));
            if (s % 2 == 0) world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        } else if (s < rise + hang) {
            stay(getPos());
            if (s == rise) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_IDLE_AIR, SoundCategory.HOSTILE, 1.5f, 0.5f);
        } else if (s < rise + hang + fall) {
            double u = (s - rise - hang + 1) / (double) fall;
            Vec3d top = new Vec3d(from.lerp(spot, 0.85).x, Math.max(from.y, spot.y) + peak, from.lerp(spot, 0.85).z);
            stay(top.lerp(spot, u * u));
            world.spawnParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            if (s == rise + hang + fall - 1) smashDown(world, k == smashes - 1);
        }
        // Where he'll come down, marked from the moment he leaves the ground
        if (s < rise + hang + fall) RickyHazards.ring(world, RickyHazards.DARK_RED, spot, 5 * (1 - 0.3 * s / (double) (rise + hang + fall)), 28, s * 0.2);
    }

    private void smashDown(ServerWorld world, boolean last) {
        Vec3d c = spot;
        stay(c);
        setPoseState(POSE_GROUND);
        for (ServerPlayerEntity p : fighters) {
            double d = RickyHazards.flatDistance(p.getPos(), c);
            if (d > 5 || Math.abs(p.getY() - c.y) > 2.5) continue;
            if (crush(p, 12)) launch(p, p.getPos().subtract(c), 1.5, 0.8);
        }
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND, SoundCategory.HOSTILE, 1.5f, 1.0f);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 1.2f, 0.8f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y, c.z, 20, 1.5, 0.2, 1.5, 0.1);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y + 0.5, c.z, 8, 1.0, 0.1, 1.0, 0.5);
        world.spawnParticles(RickyHazards.DARK_RED, c.x, c.y + 0.5, c.z, 30, 2, 0.3, 2, 0);
        shake(c, 10, 0.6f, 10);
        // The last one shakes the ground for further: a ring to jump
        if (last) hazards.add(new RickyHazards.Quake(c, 2, 0.9, 16, 7, transitioned));
    }

    // ---- Snake Spikes: three seconds' charge, then rows of spikes snake out over the ground ----

    private void tickSnakes(ServerWorld world) {
        int t = actionTicks, charge = 60;
        stand();
        if (transitioned) stay(getPos());
        if (t == 1) {
            setPoseState(POSE_GROUND);
            hold(null);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        Vec3d g = ground(getPos());
        if (t < charge) {
            double k = t / (double) charge;
            RickyHazards.ring(world, RickyHazards.DARK_RED, g, 1.2 + 3 * k, 18, t * 0.2);
            if (t % 3 == 0) {
                BlockState under = RickyHazards.groundState(world, BlockPos.ofFloored(g).down());
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, under), g.x, g.y + 0.05, g.z, (int) (6 + 20 * k), 1.5 + 2 * k, 0.02, 1.5 + 2 * k, 0.05);
            }
            int beat = Math.max(4, 16 - (int) (12 * k));
            if (t % beat == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.5f, 0.6f + (float) k * 0.5f);
            if (t % 10 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_POINTED_DRIPSTONE_BREAK, SoundCategory.HOSTILE, 1.5f, 0.5f + (float) k * 0.4f);
            if (t > charge - 20 && t % 4 == 0) shake(g, 12, 0.2f, 6);
            return;
        }
        if (t == charge) {
            int n = RickyTimeline.snakes(transitioned);
            Vec3d center = arenaCenter(world);
            double reach = Arenas.sinOf(world) == Sin.SLOTH ? SlothArena.RADIUS - 2 : 3;
            for (int i = 0; i < n; i++) {
                // Some straight at you, the rest every which way
                Vec3d d;
                if (i < fighters.size() && i % 2 == 0) d = fighters.get(i).getPos().subtract(g).multiply(1, 0, 1);
                else d = Vec3d.fromPolar(0, i * 360f / n + random.nextFloat() * 30f);
                if (d.lengthSquared() < 1e-3) d = Vec3d.fromPolar(0, i * 90f);
                hazards.add(new RickyHazards.Snake(g.add(d.normalize().multiply(1.5)), d, center, reach, transitioned ? 100 : 90, 20));
            }
            setPoseState(POSE_SPREAD);
            world.spawnParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.3, g.z, 2, 0.5, 0, 0.5, 0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 2.0f, 0.4f);
            shake(g, 14, 0.5f, 10);
        }
        if (t >= charge + 14) endAttack();
    }

    // ---- The Hands: huge hands of shadow rise out of the ground under you, loom, and slam down flat ----

    private void tickHands(ServerWorld world) {
        int t = actionTicks;
        stand();
        if (transitioned) stay(getPos());
        int n = Math.min(RickyTimeline.hands(fighters.size(), transitioned), Math.max(1, fighters.size() + (transitioned ? 1 : 0)));
        if (t == 1) {
            setPoseState(POSE_RAISE);
            hold(null);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.2f, 1.4f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundCategory.HOSTILE, 2.0f, 0.5f);
            for (ServerPlayerEntity p : fighters) p.sendMessage(Text.translatable("message.fmscraft.ricky_hand").formatted(Formatting.DARK_RED), true);
        }
        Vec3d h = getPos().add(0, getHeight() + 0.3, 0);
        world.spawnParticles(RickyHazards.SHADOW, h.x, h.y, h.z, 3, 0.4, 0.3, 0.4, 0);
        for (int i = 0; i < n; i++) {
            if (t != 4 + i * 6) continue;
            ServerPlayerEntity target = i < fighters.size() ? fighters.get(i) : pick(null);
            if (target == null) continue;
            // From beside them, falling across where they stand (an extra one falls across where they'd run)
            Vec3d d = target.getPos().subtract(getPos()).multiply(1, 0, 1);
            d = d.lengthSquared() > 1e-3 ? d.normalize() : Vec3d.fromPolar(0, getYaw());
            if (i >= fighters.size()) d = d.rotateY((float) Math.toRadians(90));
            Vec3d base = ground(target.getPos().subtract(d.multiply(4)));
            hazards.add(new RickyHazards.Hand(base, d, 20));
        }
        if (t >= 4 + n * 6 + 12) endAttack();
    }

    // ---- Dashpunch: a crouch, then he's on you, fist first, and you're thrown across the city ----

    private void tickDashPunch(ServerWorld world) {
        int t = actionTicks, windup = transitioned ? 10 : 13, dash = 16, punches = transitioned ? 2 : 1;
        int each = windup + dash + 6;
        int k = (t - 1) / each, s = (t - 1) % each;
        if (k >= punches) {
            setNoGravity(transitioned);
            endAttack();
            return;
        }
        ServerPlayerEntity target = fighter(focus);
        if (s == 0) {
            target = k == 0 ? pick(null) : pick(focus);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            hold(null);
            setPoseState(POSE_CROUCH);
            struck.clear();
            if (transitioned && k > 0) {
                // The second one: from out of nowhere, teleported to the side of them
                Vec3d side = Vec3d.fromPolar(0, target.getYaw() + (random.nextBoolean() ? 90 : -90));
                Vec3d at = safeSpot(target.getPos().add(side.multiply(9)));
                if (at != null) at = keepNear(world, at);
                if (at != null) {
                    vanish(world, getPos());
                    refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0f);
                    vanish(world, at);
                }
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.6f, 1.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 1.2f, 0.7f);
        }
        if (target == null) {
            endAttack();
            return;
        }
        if (s < windup) {
            stand();
            if (transitioned) stay(getPos());
            faceTowards(target.getPos());
            // Power gathering in his fist, and the line he'll take flickering on the ground
            Vec3d h = handPos(true);
            world.spawnParticles(transitioned ? RickyHazards.GOLD : RickyHazards.DARK_RED, h.x, h.y, h.z, 4, 0.15, 0.15, 0.15, 0);
            if (s % 3 == 0) {
                Vec3d a = ground(getPos()), b = ground(target.getPos());
                for (int i = 0; i <= 12; i++) {
                    Vec3d p = a.lerp(b, i / 12.0);
                    world.spawnParticles(RickyHazards.BLOOD, p.x, p.y + 0.1, p.z, 1, 0.05, 0, 0.05, 0);
                }
            }
            return;
        }
        int d = s - windup;
        if (d == 0) {
            setPoseState(POSE_PUNCH);
            setAfterimages(true);
            dir = target.getEyePos().subtract(getEyePos()).normalize();
            setNoGravity(true);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.HOSTILE, 2.0f, 1.2f);
            world.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        }
        if (d < dash && struck.isEmpty()) {
            // Homing, but it can only turn so fast: get out of its way at the last moment
            Vec3d want = target.getEyePos().subtract(getEyePos()).normalize();
            dir = dir.lerp(want, 0.22).normalize();
            Vec3d a = getPos();
            Vec3d to = a.add(dir.multiply(2.0));
            if (RickyHazards.blocked(world, a.add(0, 1, 0), to.add(0, 1, 0)) || keepNear(world, to).squaredDistanceTo(to) > 0.01) {
                // Into a wall: he stops dead, stunned for a moment
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 1.5f, 0.5f);
                world.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
                struck.add(getUuid());
                setAfterimages(false);
                return;
            }
            faceTowards(to);
            setVelocity(Vec3d.ZERO);
            setPosition(to.x, Math.max(to.y, ground(to).y), to.z);
            world.spawnParticles(transitioned ? RickyHazards.GOLD : RickyHazards.DARK_RED, getX(), getY() + 1, getZ(), 6, 0.3, 0.5, 0.3, 0);
            world.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 2, 0.2, 0.05, 0.2, 0.02);
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid())) continue;
                if (p.getBoundingBox().expand(0.9).raycast(a.add(0, 1, 0), getPos().add(0, 1, 0)).isEmpty() && p.distanceTo(this) > 1.8) continue;
                struck.add(p.getUuid());
                punch(world, p);
            }
            return;
        }
        if (d == dash || !struck.isEmpty() && d < dash) {
            setAfterimages(false);
            setPoseState(POSE_IDLE);
            setVelocity(Vec3d.ZERO);
        }
    }

    /** The dashpunch lands: a crack like thunder, a shockwave off his fist, and they're gone across the city. */
    private void punch(ServerWorld world, ServerPlayerEntity p) {
        Vec3d c = p.getBoundingBox().getCenter();
        p.timeUntilRegen = 0;
        if (smack(p, transitioned ? 20 : 22)) launch(p, dir, transitioned ? 3.4 : 3.8, 1.15);
        else launch(p, dir, 2.2, 0.8);
        for (ServerPlayerEntity a : audience(world)) {
            if (a.squaredDistanceTo(c) > 40 * 40) continue;
            a.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2.0f, 0.6f);
            a.playSoundToPlayer(SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 2.5f, 0.7f);
            a.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.4f, 1.4f);
            a.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            a.playSoundToPlayer(SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 1.2f, 1.6f);
        }
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.4, 0.4, 0.4, 0);
        world.spawnParticles(transitioned ? RickyHazards.GOLD : RickyHazards.DARK_RED, c.x, c.y, c.z, 60, 0.6, 0.6, 0.6, 0);
        world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 40, 0.3, 0.3, 0.3, 0.8);
        Cinematics.flash(p, 0.6f, 10);
        shake(c, 8, 0.9f, 12);
    }

    // ---- Super Saiyan: the black beam ----

    private void tickBeam(ServerWorld world) {
        int t = actionTicks, charge = 38, fire = 34;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            setPoseState(POSE_CUP);
            spot = getPos();
            cupBall = RickyHazards.blockDisplay(world, net.minecraft.block.Blocks.BLACK_CONCRETE.getDefaultState(), cupPos());
            if (cupBall != null) {
                cupBall.setBrightness(new Brightness(0, 15));
                cupBall.setViewRange(8f);
                cupBall.setTransformation(RickyHazards.spinning(0, 0.05f));
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.5f, 0.5f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.5f, 0.6f);
        }
        ServerPlayerEntity target = fighter(focus);
        stay(spot != null ? spot : getPos());
        if (t < charge) {
            if (target != null) faceTowards(target.getPos());
            float f = t / (float) charge;
            Vec3d c = cupPos();
            if (cupBall != null) RickyHazards.place(cupBall, c, RickyHazards.spinning(t * 0.5f, 0.15f + 0.85f * f), 1);
            for (int i = 0; i < 4 + (int) (6 * f); i++) {
                Vec3d o = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().multiply(3 - 2 * f);
                Vec3d p = c.add(o);
                world.spawnParticles(i % 2 == 0 ? RickyHazards.DARK_RED : ParticleTypes.SQUID_INK, p.x, p.y, p.z, 0, -o.x, -o.y, -o.z, 0.12);
            }
            if (t % 6 == 0) world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.4f, 0.5f + f);
            return;
        }
        if (t == charge) {
            if (cupBall != null) cupBall.discard();
            cupBall = null;
            setPoseState(POSE_BEAM);
            Vec3d from = handFront();
            Vec3d aim = target != null ? target.getBoundingBox().getCenter().subtract(from) : Vec3d.fromPolar(getPitch(), getYaw());
            beam = new RickyHazards.Beam(world, from, aim, fire, 6, 4, false);
            hazards.add(beam);
            shake(from, 20, 0.6f, fire);
        }
        if (beam != null && t < charge + fire) {
            // It follows them, slowly: keep moving across it
            Vec3d from = handFront();
            Vec3d want = target != null ? target.getBoundingBox().getCenter().subtract(from).normalize() : beam.dir;
            Vec3d d = beam.dir;
            double angle = Math.acos(MathHelper.clamp(d.dotProduct(want), -1, 1)), max = Math.toRadians(1.4);
            Vec3d turned = angle <= max ? want : d.lerp(want, max / angle).normalize();
            beam.aim(from, turned);
            faceTowards(from.add(turned));
        }
        if (t >= charge + fire + 8) {
            beam = null;
            endAttack();
        }
    }

    private Vec3d cupPos() {
        Vec3d forward = Vec3d.fromPolar(0, getBodyYaw());
        Vec3d right = new Vec3d(-forward.z, 0, forward.x).multiply(-0.6);
        return getPos().add(0, 1.0, 0).add(right).add(forward.multiply(-0.2));
    }

    private Vec3d handFront() {
        return getPos().add(0, 1.3, 0).add(Vec3d.fromPolar(0, getBodyYaw()).multiply(1.0));
    }

    // ---- Super Saiyan: the spirit bomb, a great ball of white light gathered over his head and thrown ----

    private void tickSpiritBomb(ServerWorld world) {
        int t = actionTicks, cast = 96;
        Vec3d over = getPos().add(0, getHeight() + 3.4, 0);
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            untouchable = true;
            spot = getPos();
            bombSpin = 0;
            setPoseState(POSE_RAISE);
            bombCore = RickyHazards.blockDisplay(world, net.minecraft.block.Blocks.WHITE_CONCRETE.getDefaultState(), over);
            bombShell = RickyHazards.blockDisplay(world, net.minecraft.block.Blocks.WHITE_STAINED_GLASS.getDefaultState(), over);
            bombHalo = RickyHazards.blockDisplay(world, net.minecraft.block.Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(), over);
            for (DisplayEntity.BlockDisplayEntity d : new DisplayEntity.BlockDisplayEntity[]{bombCore, bombShell, bombHalo}) {
                if (d == null) continue;
                d.setBrightness(new Brightness(15, 15));
                d.setViewRange(12f);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.5f, 1.4f);
            for (ServerPlayerEntity p : audience(world)) {
                p.sendMessage(Text.translatable("message.fmscraft.ricky_spirit_bomb").formatted(Formatting.WHITE, Formatting.BOLD), true);
                p.playSoundToPlayer(SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 2.0f, 1.8f);
            }
        }
        stay(spot != null ? spot : getPos());
        ServerPlayerEntity target = fighter(focus);
        if (target != null) faceTowards(target.getPos());
        if (t < cast) {
            float f = t / (float) cast, grow = f * f * (3 - 2 * f);
            bombSpin += 0.1f + 0.4f * f;
            RickyHazards.SpiritBomb.shape(bombCore, bombShell, bombHalo, over, bombSpin, 0.1f + (RickyHazards.SpiritBomb.SIZE - 0.1f) * grow);
            // Light drawn in to it from all over the city
            for (int i = 0; i < 4 + (int) (8 * f); i++) {
                Vec3d o = new Vec3d(random.nextGaussian(), random.nextGaussian() * 0.6 + 0.4, random.nextGaussian()).normalize().multiply(14 - 8 * f);
                Vec3d p = over.add(o);
                world.spawnParticles(i % 3 == 0 ? ParticleTypes.FIREWORK : ParticleTypes.END_ROD, p.x, p.y, p.z, 0, -o.x, -o.y, -o.z, 0.1 + 0.12 * f);
            }
            if (t % 4 == 0) RickyHazards.ring(world, RickyHazards.SKY, ground(getPos()), RickyHazards.SpiritBomb.PULL_RADIUS * (0.9 + 0.1 * MathHelper.sin(t * 0.3f)), 60, t * 0.05);
            if (t > 60 && t % 10 == 0) {
                for (ServerPlayerEntity p : fighters) {
                    Vec3d in = over.subtract(p.getPos()).multiply(1, 0, 1);
                    double d = in.length();
                    if (d < 2 || d > 16) continue;
                    Vec3d v = in.multiply(0.12 / d);
                    p.setVelocity(v.x, 0.02, v.z);
                    p.velocityModified = true;
                }
            }
            int beat = Math.max(4, 16 - (int) (12 * f));
            if (t % beat == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, 3.0f, 0.5f + f);
            if (t == 50) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2.0f, 1.2f);
            if (t == cast - 12) {
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.5f, 0.8f);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.0f, 0.8f);
            }
            return;
        }
        if (t == cast) {
            discardBomb();
            Vec3d d = target != null ? target.getBoundingBox().getCenter().subtract(over) : Vec3d.fromPolar(30, getYaw());
            Vec3d aim = d.normalize();
            hazards.add(new RickyHazards.SpiritBomb(world, over, aim.multiply(0.36), 26, 8, bombSpin));
            untouchable = false;
            setPoseState(POSE_THROW);
            swingHand(Hand.MAIN_HAND);
            for (ServerPlayerEntity p : audience(world)) if (p.squaredDistanceTo(this) < 40 * 40) Cinematics.flash(p, 0.35f, 10);
        }
        if (t >= cast + 14) endAttack();
    }

    private void discardBomb() {
        for (DisplayEntity.BlockDisplayEntity d : new DisplayEntity.BlockDisplayEntity[]{bombCore, bombShell, bombHalo}) if (d != null) d.discard();
        bombCore = bombShell = bombHalo = null;
    }

    // ---- Super Saiyan: the ki barrage, a storm of blasts from both hands ----

    private void tickBarrage(ServerWorld world) {
        int t = actionTicks, count = desperate() ? 32 : 24, first = 10;
        if (t == 1) {
            spot = getPos();
            setPoseState(POSE_BARRAGE_A);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.4f, 0.6f);
        }
        stay(spot != null ? spot : getPos());
        ServerPlayerEntity near = nearestFighter();
        if (near != null) turnTowards(near.getPos(), 10f);
        if (t < first) {
            Vec3d h = handPos(true);
            world.spawnParticles(RickyHazards.GOLD, h.x, h.y, h.z, 3, 0.2, 0.2, 0.2, 0);
            return;
        }
        int k = (t - first) / 2;
        if ((t - first) % 2 == 0 && k < count && !fighters.isEmpty()) {
            boolean right = k % 2 == 0;
            setPoseState(right ? POSE_BARRAGE_A : POSE_BARRAGE_B);
            swingHand(right ? Hand.MAIN_HAND : Hand.OFF_HAND);
            ServerPlayerEntity p = fighters.get(random.nextInt(fighters.size()));
            Vec3d from = handPos(right);
            Vec3d aim = lead(p, from.distanceTo(p.getPos()) / 1.3).add(0, 0.9, 0).subtract(from).normalize();
            aim = aim.rotateY((float) Math.toRadians(random.nextGaussian() * 5)).add(0, random.nextGaussian() * 0.04, 0).normalize();
            hazards.add(new RickyHazards.KiBlast(world, from, aim.multiply(1.3), 5, k % 5 == 4));
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 0.8f, 1.6f + random.nextFloat() * 0.3f);
        }
        if (t >= first + count * 2 + 8) endAttack();
    }

    // ---- Super Saiyan: the rush, from one of you to the next, too fast to see ----

    private void tickRush(ServerWorld world) {
        int warn = 5, travel = 3, pause = 3, each = warn + travel + pause, hits = Math.min(5, Math.max(3, fighters.size() + 1));
        int t = actionTicks - 1, k = t / each, s = t % each;
        if (t == 0) {
            setAfterimages(true);
            world.playSound(null, getX(), getY(), getZ(), ModSounds.RICKY_TELEPORT, SoundCategory.HOSTILE, 1.6f, 1.2f);
        }
        if (k >= hits || t > hits * each + 20) {
            noClip = false;
            endAttack(12);
            return;
        }
        if (s == 0) {
            ServerPlayerEntity target = pick(focus);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            from = getPos();
            Vec3d through = target.getPos().subtract(from).multiply(1, 0, 1);
            through = through.lengthSquared() > 1e-3 ? through.normalize() : Vec3d.fromPolar(0, getYaw());
            Vec3d end = safeSpot(target.getPos().add(through.multiply(3)));
            spot = keepNear(world, end != null ? end : target.getPos());
            faceTowards(spot);
            setPoseState(POSE_FLY);
            struck.clear();
        }
        if (spot == null) return;
        if (s < warn) {
            stay(getPos());
            if (s % 2 == 0) {
                for (int i = 0; i <= 16; i++) {
                    Vec3d p = from.lerp(spot, i / 16.0);
                    world.spawnParticles(RickyHazards.GOLD, p.x, p.y + 1, p.z, 1, 0.05, 0.05, 0.05, 0);
                }
            }
            return;
        }
        if (s < warn + travel) {
            Vec3d a = getPos();
            Vec3d at = from.lerp(spot, (s - warn + 1) / (double) travel);
            noClip = true;
            stay(at);
            setPoseState(POSE_PUNCH);
            if (s == warn) world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.HOSTILE, 1.6f, 1.4f);
            for (ServerPlayerEntity p : fighters) {
                if (struck.contains(p.getUuid())) continue;
                if (p.getBoundingBox().expand(0.7).raycast(a.add(0, 1, 0), at.add(0, 1, 0)).isEmpty()) continue;
                struck.add(p.getUuid());
                if (smack(p, 12)) launch(p, at.subtract(a), 1.4, 0.6);
                world.spawnParticles(ParticleTypes.EXPLOSION, p.getX(), p.getBodyY(0.5), p.getZ(), 1, 0, 0, 0, 0);
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.6f, 0.6f);
            }
            return;
        }
        noClip = false;
        stay(getPos());
    }

    // ---- Super Saiyan: the meteor dive. Up into the sky, and down on you like a meteor ----

    private void tickDive(ServerWorld world) {
        int t = actionTicks, rise = 16, hang = 14, fall = 6;
        if (t == 1) {
            ServerPlayerEntity target = pick(null);
            if (target == null) {
                endAttack();
                return;
            }
            focus = target.getUuid();
            from = getPos();
            setPoseState(POSE_FLY);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WIND_CHARGE_THROW, SoundCategory.HOSTILE, 1.6f, 0.6f);
        }
        ServerPlayerEntity target = fighter(focus);
        if (t <= rise) {
            stay(from.add(0, 15 * Math.sin(t / (double) rise * MathHelper.HALF_PI), 0));
            world.spawnParticles(RickyHazards.GOLD, getX(), getY(), getZ(), 4, 0.3, 0.3, 0.3, 0);
            return;
        }
        if (t <= rise + hang) {
            stay(getPos());
            setPoseState(POSE_DIVE);
            // He picks his spot, and a ring of gold marks it; it settles a moment before he comes down
            if (target != null && t < rise + hang - 4) spot = keepNear(world, ground(lead(target, 6)));
            if (spot != null) {
                RickyHazards.ring(world, RickyHazards.GOLD, spot, 4, 30, t * 0.2);
                RickyHazards.ring(world, RickyHazards.GOLD, spot, 1.5, 12, -t * 0.3);
                faceTowards(spot);
            }
            if (t == rise + 1) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.HOSTILE, 2.0f, 0.8f);
            return;
        }
        if (spot == null) {
            endAttack();
            return;
        }
        if (t <= rise + hang + fall) {
            Vec3d top = new Vec3d(getX(), getY(), getZ());
            double u = (t - rise - hang) / (double) fall;
            stay(top.lerp(spot, u * u));
            world.spawnParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 6, 0.3, 0.6, 0.3, 0.05);
            if (t == rise + hang + fall) {
                Vec3d c = spot;
                stay(c);
                setPoseState(POSE_GROUND);
                for (ServerPlayerEntity p : fighters) {
                    double d = RickyHazards.flatDistance(p.getPos(), c);
                    if (d > 4 || Math.abs(p.getY() - c.y) > 3) continue;
                    if (crush(p, 18)) launch(p, p.getPos().subtract(c), 1.6, 0.9);
                }
                hazards.add(new RickyHazards.Quake(c, 2, 0.8, 20, 8, true));
                hazards.add(new RickyHazards.Quake(c, 1, 0.55, 14, 8, true));
                for (ServerPlayerEntity p : audience(world)) if (p.squaredDistanceTo(c) < 24 * 24) Cinematics.flash(p, 0.5f, 12);
                world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 2, 1, 0.2, 1, 0);
                world.spawnParticles(RickyHazards.GOLD, c.x, c.y + 0.5, c.z, 80, 3, 0.5, 3, 0);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, RickyHazards.groundState(world, BlockPos.ofFloored(c).down())), c.x, c.y + 0.3, c.z, 120, 2.5, 0.4, 2.5, 0.4);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
                world.playSound(null, c.x, c.y, c.z, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.HOSTILE, 3.0f, 0.5f);
                shake(c, 18, 1f, 18);
            }
            return;
        }
        if (t >= rise + hang + fall + 14) endAttack();
    }

    // ---- Super Saiyan: the rubble storm. Chunks of the city torn up round him, then hurled ----

    private void tickRubble(ServerWorld world) {
        int t = actionTicks, count = fighters.size() > 4 ? 12 : 9, rise = 26, every = 3;
        if (t == 1) {
            spot = getPos();
            setPoseState(POSE_SPREAD);
            rubble.clear();
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.5f);
            for (int i = 0; i < count; i++) {
                double a = i * MathHelper.TAU / count + random.nextDouble() * 0.3, r = 3 + random.nextDouble() * 3;
                Vec3d g = ground(getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                Vec3d rest = getPos().add(Math.cos(a) * 2.6, 2.2 + random.nextDouble() * 2, Math.sin(a) * 2.6);
                RickyHazards.Rubble chunk = new RickyHazards.Rubble(world, g.add(0, 0.4, 0), rest, 0.7f + random.nextFloat() * 0.6f, 10);
                rubble.add(chunk);
                hazards.add(chunk);
            }
            shake(getPos(), 10, 0.4f, 10);
        }
        stay(spot != null ? spot : getPos());
        ServerPlayerEntity near = nearestFighter();
        if (near != null) turnTowards(near.getPos(), 10f);
        if (t < rise) return;
        int k = (t - rise) / every;
        if ((t - rise) % every == 0 && k < rubble.size() && !fighters.isEmpty()) {
            RickyHazards.Rubble chunk = rubble.get(k);
            ServerPlayerEntity p = fighters.get(k % fighters.size());
            Vec3d aim = lead(p, chunk.pos.distanceTo(p.getPos()) / 1.5).add(0, 0.8, 0)
                    .add(random.nextGaussian() * 1.4, 0, random.nextGaussian() * 1.4);
            chunk.hurl(world, lob(chunk.pos, aim, 1.5, 0.025));
            setPoseState(k % 2 == 0 ? POSE_BARRAGE_A : POSE_BARRAGE_B);
        }
        if (t >= rise + rubble.size() * every + 10) {
            rubble.clear();
            endAttack();
        }
    }

    // ---- Super Saiyan: the big bang. Too close? A sphere of ki bursts out of him ----

    private void tickBigBang(ServerWorld world) {
        int t = actionTicks, charge = 24;
        double radius = 9;
        if (t == 1) {
            spot = getPos();
            setPoseState(POSE_POWER);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 2.0f, 0.6f);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.0f, 1.0f);
        }
        stay(spot != null ? spot : getPos());
        Vec3d c = getPos().add(0, 1, 0);
        if (t < charge) {
            RickyHazards.ring(world, RickyHazards.GOLD, ground(getPos()), radius, 50, t * 0.1);
            world.spawnParticles(RickyHazards.GOLD, c.x, c.y, c.z, 6, 0.5, 0.8, 0.5, 0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 4, 0.5, 0.8, 0.5, 0.2);
            return;
        }
        if (t == charge) {
            setPoseState(POSE_SPREAD);
            for (int i = 0; i < 80; i++) {
                Vec3d o = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
                world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 0, o.x, o.y, o.z, 0.9);
                world.spawnParticles(RickyHazards.GOLD, c.x + o.x * radius * 0.5, c.y + o.y * radius * 0.5, c.z + o.z * radius * 0.5, 1, 0, 0, 0, 0);
            }
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.7f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.5f, 1.2f);
            for (ServerPlayerEntity p : fighters) {
                double d = p.getBoundingBox().getCenter().distanceTo(c);
                if (d > radius) continue;
                if (crush(p, 14)) launch(p, p.getPos().subtract(c), 2.4 * (1 - d / (radius + 2)) + 0.6, 0.7);
            }
            hazards.add(new RickyHazards.Quake(ground(getPos()), 1, 1.2, radius + 3, 0, true));
            shake(c, 12, 0.7f, 12);
        }
        if (t >= charge + 14) endAttack();
    }

    // ---- Desperate: the final beam, swept all the way round. Get behind something ----

    private void tickFinalBeam(ServerWorld world) {
        int t = actionTicks, charge = 70, sweep = 76;
        if (t == 1) {
            spot = ground(getPos());
            setPoseState(POSE_CUP);
            untouchable = true;
            cupBall = RickyHazards.blockDisplay(world, net.minecraft.block.Blocks.BLACK_CONCRETE.getDefaultState(), cupPos());
            if (cupBall != null) {
                cupBall.setBrightness(new Brightness(0, 15));
                cupBall.setViewRange(10f);
            }
            for (ServerPlayerEntity p : audience(world)) {
                p.sendMessage(Text.translatable("message.fmscraft.ricky_final_beam").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
                p.playSoundToPlayer(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0f, 0.7f);
            }
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 3.0f, 0.4f);
        }
        stay(spot);
        if (t < charge) {
            float f = t / (float) charge;
            Vec3d c = cupPos();
            if (cupBall != null) RickyHazards.place(cupBall, c, RickyHazards.spinning(t * 0.6f, 0.2f + 1.3f * f), 1);
            for (int i = 0; i < 6 + (int) (10 * f); i++) {
                Vec3d o = new Vec3d(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().multiply(6 - 4 * f);
                Vec3d p = c.add(o);
                world.spawnParticles(i % 2 == 0 ? RickyHazards.DARK_RED : ParticleTypes.SQUID_INK, p.x, p.y, p.z, 0, -o.x, -o.y, -o.z, 0.15);
            }
            if (t % 4 == 0) RickyHazards.ring(world, RickyHazards.BLOOD, spot, RickyHazards.Beam.REACH * 0.5 * f + 2, 60, t * 0.05);
            if (t % 10 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0f, 0.6f + f);
            if (t == charge - 15) world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2.5f, 0.6f);
            return;
        }
        if (t == charge) {
            if (cupBall != null) cupBall.discard();
            cupBall = null;
            setPoseState(POSE_BEAM);
            beam = new RickyHazards.Beam(world, spot.add(0, 1.0, 0), Vec3d.fromPolar(0, getYaw()), sweep, 16, 30, true);
            hazards.add(beam);
            shake(spot, 40, 0.8f, sweep);
        }
        if (t > charge && t <= charge + sweep && beam != null) {
            float yaw = getYaw() + 360f / sweep;
            faceYaw(yaw);
            beam.aim(spot.add(0, 1.0, 0).add(Vec3d.fromPolar(0, yaw).multiply(0.8)), Vec3d.fromPolar(0, yaw));
        }
        if (t >= charge + sweep + 10) {
            beam = null;
            untouchable = false;
            endAttack(30);
        }
    }

    // ---- Super Saiyan: the strike. Gone, and out behind you with an elbow ----

    private void tickStrike(ServerWorld world) {
        ServerPlayerEntity target = pick(null);
        if (target == null) {
            endAttack();
            return;
        }
        focus = target.getUuid();
        vanish(world, getPos());
        startAttack(Action.TELEPORT);
        lastUsed.put(Action.STRIKE, age);
        lastAttack = Action.STRIKE;
    }

    // ---- The hazards, his aura, the world round the fight ----

    private void tickHazards(ServerWorld world) {
        for (RickyHazards.Hazard h : new ArrayList<>(hazards)) {
            if (!h.tick(world, this)) {
                h.remove();
                hazards.remove(h);
            }
        }
    }

    /** Phase 1: a faint dark red aura curling up off him. Super Saiyan: gold flaring up round him, sparks, black lightning. */
    private void tickAura(ServerWorld world) {
        int aura = aura();
        if (aura == AURA_NONE) return;
        double w = getWidth() * 0.6, h = getHeight();
        if (aura == AURA_DARK) {
            if (age % 2 == 0) world.spawnParticles(RickyHazards.DARK_RED, getX(), getY() + h * 0.45, getZ(), 1, w, h * 0.4, w, 0);
            if (age % 7 == 0) world.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + h * 0.3, getZ(), 1, w, h * 0.2, w, 0.01);
            return;
        }
        // Flames of gold licking up off him, out of a ring at his feet
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * MathHelper.TAU, r = 0.35 + random.nextDouble() * 0.25;
            world.spawnParticles(RickyHazards.GOLD, getX() + Math.cos(a) * r, getY() + random.nextDouble() * h * 0.9, getZ() + Math.sin(a) * r, 0, 0, 1, 0, 0.12);
        }
        if (age % 2 == 0) world.spawnParticles(RickyHazards.PALE_GOLD, getX(), getY() + h * 0.5, getZ(), 1, w, h * 0.4, w, 0.02);
        if (age % 4 == 0) world.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + h * 0.2, getZ(), 1, w, 0.1, w, 0.03);
        if (age % 5 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + h * 0.6, getZ(), 2, w, h * 0.3, w, 0.2);
        // Evil: black lightning crackling through it
        if (age % 11 == 0) {
            Vec3d a = getPos().add((random.nextDouble() - 0.5) * 0.8, random.nextDouble() * h, (random.nextDouble() - 0.5) * 0.8);
            Vec3d b = a.add((random.nextDouble() - 0.5) * 1.4, (random.nextDouble() - 0.3) * 1.2, (random.nextDouble() - 0.5) * 1.4);
            for (int i = 0; i <= 6; i++) {
                Vec3d p = a.lerp(b, i / 6.0).add((random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2);
                world.spawnParticles(i % 2 == 0 ? RickyHazards.SHADOW : RickyHazards.BLOOD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        if (isOnGround() || getY() - ground(getPos()).y < 1.5) {
            if (age % 3 == 0) RickyHazards.ring(world, RickyHazards.GOLD, ground(getPos()), 1.2, 12, age * 0.2);
        }
        if (age % 30 == 0) world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 1.4f, 0.6f);
    }

    /** Golden-hour dust drifting through the ruins; under the black sky, red embers rising and ash. */
    private void tickAmbience(ServerWorld world) {
        if (age % 6 == 0) {
            for (ServerPlayerEntity p : fighters) {
                if (transitioned) {
                    world.spawnParticles(p, RickyHazards.BLOOD, false, p.getX(), p.getY() + 2, p.getZ(), 3, 12, 4, 12, 0);
                    world.spawnParticles(p, ParticleTypes.ASH, false, p.getX(), p.getY() + 3, p.getZ(), 6, 10, 3, 10, 0);
                } else {
                    world.spawnParticles(p, ParticleTypes.WHITE_ASH, false, p.getX(), p.getY() + 2, p.getZ(), 3, 12, 3, 12, 0);
                }
            }
        }
    }

    /** Off the clearing, up a tower, down a hole: the city won't let you go, and he drags you back. */
    private void catchStrays(ServerWorld world) {
        // Everyone in the city, however far they've gone (up a tower is further than he looks for fighters)
        for (ServerPlayerEntity p : world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            Vec3d pos = p.getPos();
            if (SlothArena.inBounds(pos)) {
                straying.remove(p.getUuid());
                continue;
            }
            int out = straying.merge(p.getUuid(), 1, Integer::sum);
            if (pos.y >= SlothArena.MIN_Y && out < 10) continue;
            straying.remove(p.getUuid());
            Vec3d to = ground(SlothArena.returnSpot(pos));
            world.spawnParticles(RickyHazards.DARK_RED, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0);
            p.teleport(world, to.x, to.y, to.z, p.getYaw(), p.getPitch());
            p.fallDistance = 0;
            p.setVelocity(Vec3d.ZERO);
            p.velocityModified = true;
            world.spawnParticles(RickyHazards.DARK_RED, to.x, to.y + 1, to.z, 30, 0.4, 0.8, 0.4, 0);
            world.playSound(null, to.x, to.y, to.z, ModSounds.RICKY_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.8f);
            crush(p, 4);
            p.sendMessage(Text.translatable("message.fmscraft.ricky_caught").formatted(Formatting.DARK_RED), true);
        }
    }

    // ---- Dying ----

    @Override
    public void onDeath(DamageSource source) {
        if (getWorld() instanceof ServerWorld world) {
            clearAttacks(world);
            sky(world, 1);
            for (ServerPlayerEntity p : audience(world)) {
                Cinematics.unfreeze(p);
                Cinematics.flash(p, 0.9f, 30);
            }
            Vec3d c = getPos().add(0, getHeight() * 0.5, 0);
            world.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
            world.spawnParticles(transitioned ? RickyHazards.GOLD : RickyHazards.DARK_RED, c.x, c.y, c.z, 200, 1.5, 2, 1.5, 0);
            world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 0.5, 1, 0.5, 0.5);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
            world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 4.0f, 0.5f);
            setAura(AURA_NONE);
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
        for (RickyHazards.Hazard h : hazards) h.remove();
        hazards.clear();
        rubble.clear();
        beam = null;
        heldCannon = null;
        heldStone = null;
        if (heldBoulder != null) heldBoulder.discard();
        heldBoulder = null;
        if (cupBall != null) cupBall.discard();
        cupBall = null;
        discardBomb();
        for (DisplayEntity.BlockDisplayEntity chunk : lifted) chunk.discard();
        lifted.clear();
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
            if (Arenas.sinOf(getWorld()) == Sin.SLOTH && !SlothArena.inBounds(feet)) continue;
            return feet;
        }
        return null;
    }

    private void stand() {
        getNavigation().stop();
        setVelocity(0, getVelocity().y, 0);
    }

    private void faceTowards(Vec3d pos) {
        if (RickyHazards.flatDistance(pos, getPos()) < 1e-3) return;
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
