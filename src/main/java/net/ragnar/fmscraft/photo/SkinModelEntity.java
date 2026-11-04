package net.ragnar.fmscraft.photo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Temporary, for staging the title screen panoramas: a friend's skin frozen in one of {@link SkinModelPose#POSES}.
 * It never moves, takes no damage and never despawns; punch it to remove it.
 */
public class SkinModelEntity extends MobEntity {
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(SkinModelEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> POSE = DataTracker.registerData(SkinModelEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public SkinModelEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        setAiDisabled(true);
        setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) setEquipmentDropChance(slot, 0);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, 0);
        builder.add(POSE, 0);
    }

    public SkinModelSkin skin() {
        return SkinModelSkin.values()[Math.floorMod(dataTracker.get(SKIN), SkinModelSkin.values().length)];
    }

    public void setSkin(SkinModelSkin skin) {
        dataTracker.set(SKIN, skin.ordinal());
    }

    public SkinModelPose pose() {
        return SkinModelPose.get(dataTracker.get(POSE));
    }

    public void setPoseIndex(int index) {
        dataTracker.set(POSE, index);
    }

    @Override
    public void tick() {
        super.tick();
        // Hold exactly the facing it was placed (or /tp'd) with
        bodyYaw = prevBodyYaw = headYaw = prevHeadYaw = getYaw();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof PlayerEntity || source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
            if (!getWorld().isClient) discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Skin", dataTracker.get(SKIN));
        nbt.putInt("ModelPose", dataTracker.get(POSE));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(SKIN, nbt.getInt("Skin"));
        dataTracker.set(POSE, nbt.getInt("ModelPose"));
    }
}
