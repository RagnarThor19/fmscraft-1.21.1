package net.ragnar.fmscraft.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Stand-in boss used for every sin until the real ones are designed. Proves the framework end to end:
 * evoker fangs ring out on the beat (every 4 beats, every 2 in phase 2), and phase 2 switches the song.
 */
public class TestBossEntity extends SinBossEntity {
    private static final BossSong PHASE_ONE = new BossSong("minecraft:music_disc.pigstep", 85, 0);
    private static final BossSong PHASE_TWO = new BossSong("minecraft:music_disc.otherside", 92, 0);

    public TestBossEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 150)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 16f));
        goalSelector.add(8, new LookAroundGoal(this));
        targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    @Override
    protected BossSong songForPhase(int phase) {
        return phase == 1 ? PHASE_ONE : PHASE_TWO;
    }

    @Override
    protected void onBeat(int phase, int beat) {
        int every = phase == 1 ? 4 : 2;
        if (beat % every != 0) return;

        int count = phase == 1 ? 8 : 12;
        double radius = phase == 1 ? 3.0 : 4.5;
        for (int i = 0; i < count; i++) {
            float angle = (float) (Math.PI * 2 * i / count);
            getWorld().spawnEntity(new EvokerFangsEntity(getWorld(),
                    getX() + MathHelper.cos(angle) * radius, getY(), getZ() + MathHelper.sin(angle) * radius,
                    angle, 0, this));
        }
        getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1f, 1f);
    }

    @Override
    protected void onPhaseTwo() {
        LivingEntity target = getTarget();
        getWorld().playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.5f, 0.8f);
        if (target != null) setVelocity(target.getPos().subtract(getPos()).normalize().multiply(1.2).add(0, 0.5, 0));
    }
}
