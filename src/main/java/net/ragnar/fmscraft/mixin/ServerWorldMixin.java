package net.ragnar.fmscraft.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.ragnar.fmscraft.structure.wrath.WrathDens;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Mother Warden hears everything in her hall, not just what's within a warden's 16 blocks of her. */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "emitGameEvent(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/world/event/GameEvent$Emitter;)V",
            at = @At("HEAD"))
    private void fmscraft$denOfWrath(RegistryEntry<GameEvent> event, Vec3d pos, GameEvent.Emitter emitter, CallbackInfo ci) {
        WrathDens.onGameEvent((ServerWorld) (Object) this, event, pos, emitter);
    }
}
