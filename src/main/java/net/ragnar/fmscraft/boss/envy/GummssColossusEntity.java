package net.ragnar.fmscraft.boss.envy;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.ragnar.fmscraft.compat.MagicSpells;

import java.util.UUID;

/**
 * One of the four colossal GUMMSS that rise out of the void round the rim at half health (his Covet): exactly him, a
 * dozen times over, arms raised to the world they're pulling together over the middle. Nothing reaches them and they
 * do nothing on their own: he moves and poses them, and they go when he says, when he dies, or when the fight ends.
 */
public class GummssColossusEntity extends HostileEntity implements GummssLook {
    private static final TrackedData<Integer> POSE = DataTracker.registerData(GummssColossusEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID owner;

    public GummssColossusEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.noClip = true;
        setNoGravity(true);
        setInvulnerable(true);
        ItemStack staff = MagicSpells.item(MagicSpells.GOLDEN_STAFF);
        equipStack(EquipmentSlot.MAINHAND, staff.isEmpty() ? new ItemStack(Items.BLAZE_ROD) : staff);
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(POSE, POSE_COVET);
    }

    @Override
    protected void initGoals() {
    }

    @Override
    public int poseState() {
        return dataTracker.get(POSE);
    }

    void setOwner(GummssEntity boss) {
        owner = boss.getUuid();
    }

    void setLook(int pose) {
        dataTracker.set(POSE, pose);
    }

    void setSize(double scale) {
        getAttributeInstance(EntityAttributes.GENERIC_SCALE).setBaseValue(scale);
    }

    /** Where he holds them: feet at {@code at}, facing {@code yaw}, looking up by {@code pitch}. */
    void hold(Vec3d at, float yaw, float pitch) {
        setPosition(at.x, at.y, at.z);
        setVelocity(Vec3d.ZERO);
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        setPitch(pitch);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        // His alone: gone the moment he is, or the moment he's done with them
        if (owner == null || !(world.getEntity(owner) instanceof GummssEntity boss) || !boss.isAlive() || !boss.holdsColossus(getUuid())) discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    protected void pushAway(net.minecraft.entity.Entity entity) {
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        return false;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }
}
