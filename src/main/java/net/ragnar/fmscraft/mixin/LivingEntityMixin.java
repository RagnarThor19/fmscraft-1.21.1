package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.ragnar.fmscraft.entity.MotherWardenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The Mother Warden's sonic boom hits harder than a warden's (the boom itself is vanilla's SonicBoomTask). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float fmscraft$motherWardenBoom(float amount, DamageSource source, float original) {
        if (source.isOf(DamageTypes.SONIC_BOOM) && source.getAttacker() instanceof MotherWardenEntity) return amount * MotherWardenEntity.BOOM_DAMAGE;
        return amount;
    }
}
