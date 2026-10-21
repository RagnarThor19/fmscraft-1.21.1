package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.ragnar.fmscraft.entity.MotherWardenEntity;
import net.ragnar.fmscraft.structure.SealedRooms;
import net.ragnar.fmscraft.structure.gluttony.GluttonyHalls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Mother Warden's sonic boom hits harder than a warden's (the boom itself is vanilla's SonicBoomTask). And nothing
 * can go for a player sheltering in the chapel of a Hall of Gluttony: every mob's choice of target (brains and goals
 * alike) comes through canTarget, and one it can't target it forgets. And nothing teleports at random (chorus fruit,
 * endermen) into a room a challenge still has shut (SealedRooms).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float fmscraft$motherWardenBoom(float amount, DamageSource source, float original) {
        if (source.isOf(DamageTypes.SONIC_BOOM) && source.getAttacker() instanceof MotherWardenEntity) return amount * MotherWardenEntity.BOOM_DAMAGE;
        return amount;
    }

    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void fmscraft$shelteredInChapel(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (GluttonyHalls.sheltered(target)) cir.setReturnValue(false);
    }

    @Inject(method = "teleport(DDDZ)Z", at = @At("HEAD"), cancellable = true)
    private void fmscraft$notIntoSealedRooms(double x, double y, double z, boolean particleEffects, CallbackInfoReturnable<Boolean> cir) {
        if (((LivingEntity) (Object) this).getWorld() instanceof ServerWorld world && SealedRooms.sealed(world, BlockPos.ofFloored(x, y, z))) {
            cir.setReturnValue(false);
        }
    }
}
