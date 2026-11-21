package net.ragnar.fmscraft.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.ragnar.fmscraft.fragment.EnvyTrigger;
import net.ragnar.fmscraft.fragment.ShiveringBogged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The shivering Bogged of Envy (EnvyTrigger): armed once it's spawned (SpawnHelperMixin rolls it), and shaking the
 * way a freezing skeleton does while it isn't after a player. BoggedEntity doesn't override these, so they're hooked here.
 */
@Mixin(AbstractSkeletonEntity.class)
public abstract class AbstractSkeletonEntityMixin {
    @Inject(method = "initialize", at = @At("RETURN"))
    private void fmscraft$armShiver(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                      EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
        EnvyTrigger.onInitialize((AbstractSkeletonEntity) (Object) this);
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void fmscraft$updateCalm(CallbackInfo ci) {
        if ((Object) this instanceof BoggedEntity bogged && !bogged.getWorld().isClient && EnvyTrigger.isShivering(bogged)) {
            boolean calm = EnvyTrigger.calm(bogged);
            if (((ShiveringBogged) bogged).fmscraft$isCalm() != calm) ((ShiveringBogged) bogged).fmscraft$setCalm(calm);
        }
    }

    @Inject(method = "isShaking", at = @At("RETURN"), cancellable = true)
    private void fmscraft$shiver(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !((Object) this instanceof BoggedEntity bogged) || !EnvyTrigger.isShivering(bogged)) return;
        cir.setReturnValue(bogged.getWorld().isClient ? ((ShiveringBogged) bogged).fmscraft$isCalm() : EnvyTrigger.calm(bogged));
    }
}
