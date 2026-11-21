package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnHelper;
import net.ragnar.fmscraft.fragment.EnvyTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Natural spawning: now and then an Overworld skeleton comes out as Envy's shivering Bogged (EnvyTrigger). */
@Mixin(SpawnHelper.class)
public abstract class SpawnHelperMixin {
    @Inject(method = "createMob", at = @At("RETURN"), cancellable = true)
    private static void fmscraft$shiveringBogged(ServerWorld world, EntityType<?> type, CallbackInfoReturnable<MobEntity> cir) {
        MobEntity swap = EnvyTrigger.rollSpawn(world, cir.getReturnValue(), world.getRandom());
        if (swap != null) cir.setReturnValue(swap);
    }
}
