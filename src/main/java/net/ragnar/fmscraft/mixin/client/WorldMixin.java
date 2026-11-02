package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.ragnar.fmscraft.client.GluttonySky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Over LalliLarge's snowfield it's always snowing: on this client only (the server's weather never changes, so no snow
 * piles up), the arena's frozen biome turns it into vanilla's own snowfall.
 */
@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void fmscraft$snowfield(float delta, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof ClientWorld && GluttonySky.snow()) cir.setReturnValue(1.0f);
    }
}
