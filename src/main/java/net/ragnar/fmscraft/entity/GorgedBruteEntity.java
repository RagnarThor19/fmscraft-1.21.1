package net.ragnar.fmscraft.entity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * The Gorged Brute: a piglin brute twice the size of any other, who stands on the dais at the end of the Hall of
 * Gluttony in front of the gate to the portal and will not move. He can't be hurt, pushed, burned or blown up. What he
 * wants is food: any food at all, handed to him (use it on him). He wolfs each mouthful down and needs a moment
 * ({@link #FEED_COOLDOWN} ticks) before he'll take the next, and he swells a little with every one. At
 * {@link #FOOD_TO_BURST} he bursts, and the way past him is open.
 *
 * He's drawn with the vanilla piglin brute's model and texture (GorgedBruteRenderer); his size comes from his scale
 * attribute. {@code GluttonyHalls} puts him on his dais when someone comes near and counts what he's eaten. He is never
 * saved: he's back, hungry, on every visit.
 */
public class GorgedBruteEntity extends PathAwareEntity {
    public static final float SCALE = 2.0f;
    /** How much bigger he is when he's had all he can eat. */
    public static final float FULL_SCALE = 2.6f;
    public static final int FOOD_TO_BURST = 30;
    /** He takes one mouthful a second, whoever's feeding him. */
    public static final int FEED_COOLDOWN = 20;
    /** How long he holds each mouthful up while he chews it. */
    private static final int CHEW_TICKS = 16;
    private static final TrackedData<Integer> EATEN = DataTracker.registerData(GorgedBruteEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Called (on the server) after each mouthful, with whoever fed him. */
    public static BiConsumer<GorgedBruteEntity, PlayerEntity> onFed = (brute, player) -> {
    };

    private long lastFed = Long.MIN_VALUE / 2;
    private int chewing;

    public GorgedBruteEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        setPersistent();
        calculateDimensions();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0)
                .add(EntityAttributes.GENERIC_SCALE, SCALE);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(EATEN, 0);
    }

    @Override
    protected void initGoals() {
        // He stands his ground and watches whoever comes near
        goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f, 1.0f));
        goalSelector.add(2, new LookAroundGoal(this));
    }

    // ---- Feeding ----

    public int eaten() {
        return dataTracker.get(EATEN);
    }

    public boolean full() {
        return eaten() >= FOOD_TO_BURST;
    }

    /** How much he's eaten, and he swells to match: from {@link #SCALE} hungry to {@link #FULL_SCALE} full. */
    public void setEaten(int eaten) {
        dataTracker.set(EATEN, Math.min(eaten, FOOD_TO_BURST));
        float scale = SCALE + (FULL_SCALE - SCALE) * eaten() / (float) FOOD_TO_BURST;
        getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(scale);
    }

    /** Whether he'd take a mouthful right now. */
    public boolean ready() {
        return !full() && getWorld().getTime() - lastFed >= FEED_COOLDOWN;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.contains(DataComponentTypes.FOOD)) return super.interactMob(player, hand);
        // Whatever happens, the food goes to him, not into the player's mouth
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (!feed(player, stack)) {
            if (!full()) playSound(SoundEvents.ENTITY_PIGLIN_BRUTE_ANGRY, 1.0f, getSoundPitch());
            return ActionResult.CONSUME;
        }
        return ActionResult.SUCCESS;
    }

    /** He takes one of {@code stack} from {@code player}, if he's ready for it. */
    public boolean feed(PlayerEntity player, ItemStack stack) {
        if (getWorld().isClient || !ready()) return false;
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) return false;
        lastFed = getWorld().getTime();
        ItemStack mouthful = stack.copyWithCount(1);
        // A bowl or bottle comes back, as it would from eating it yourself
        ItemStack left = food.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY);
        stack.decrementUnlessCreative(1, player);
        if (!left.isEmpty() && !player.isInCreativeMode() && !player.getInventory().insertStack(left)) player.dropItem(left, false);

        setEaten(eaten() + 1);
        equipStack(EquipmentSlot.MAINHAND, mouthful);
        chewing = CHEW_TICKS;
        swingHand(Hand.MAIN_HAND);
        playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.5f, 0.6f + random.nextFloat() * 0.1f);
        crumbs(mouthful, 8);
        if (eaten() % 10 == 0 && !full()) playSound(SoundEvents.ENTITY_PLAYER_BURP, 2.0f, 0.5f);
        onFed.accept(this, player);
        return true;
    }

    private void crumbs(ItemStack food, int count) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        Vec3d mouth = getRotationVec(1.0f).multiply(getWidth() * 0.5).add(getX(), getEyeY() - 0.3, getZ());
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, food), mouth.x, mouth.y, mouth.z, count, 0.2, 0.1, 0.2, 0.05);
    }

    /**
     * He's had all he can hold: he bursts in a cloud of crumbs, with a bang (that harms nothing), and a little of what
     * he'd hoarded spills out.
     */
    public void burst() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getBodyY(0.5), getZ(), 1, 0, 0, 0, 0);
        for (ItemStack food : new ItemStack[]{new ItemStack(Items.COOKED_PORKCHOP), new ItemStack(Items.BREAD), new ItemStack(Items.GOLDEN_CARROT)}) {
            world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, food), getX(), getBodyY(0.6), getZ(), 40, 0.6, 1.2, 0.6, 0.3);
        }
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.8f);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.HOSTILE, 3.0f, 0.4f);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PIGLIN_BRUTE_DEATH, SoundCategory.HOSTILE, 2.0f, 0.5f);
        for (ItemStack spill : new ItemStack[]{new ItemStack(Items.GOLD_INGOT, 3 + random.nextInt(4)), new ItemStack(Items.COOKED_PORKCHOP, 2 + random.nextInt(3))}) {
            ItemEntity item = new ItemEntity(world, getX(), getBodyY(0.5), getZ(), spill);
            item.setVelocity((random.nextDouble() - 0.5) * 0.4, 0.3, (random.nextDouble() - 0.5) * 0.4);
            world.spawnEntity(item);
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || chewing <= 0) return;
        if (--chewing == 0) {
            equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else if (chewing % 4 == 0) {
            crumbs(getMainHandStack(), 3);
            playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0f, 0.55f + random.nextFloat() * 0.1f);
        }
    }

    // ---- He can't be hurt or moved ----

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount); // /kill
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity) playSound(SoundEvents.ENTITY_PIGLIN_BRUTE_ANGRY, 1.0f, getSoundPitch());
        return false;
    }

    @Override
    public boolean isImmuneToExplosion(Explosion explosion) {
        return true;
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
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
    }

    // ---- A bigger brute ----

    @Override
    public float getSoundPitch() {
        return 0.6f + (random.nextFloat() - 0.5f) * 0.08f;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 160;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    /** Never saved: put back on his dais, hungry, on every visit. */
    @Override
    public boolean shouldSave() {
        return false;
    }
}
