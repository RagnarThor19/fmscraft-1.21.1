package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.Mouse;
import net.ragnar.fmscraft.client.BossScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a boss cutscene you can't look around either: the camera holds still behind the picture or the black. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void fmscraft$holdCamera(double timeDelta, CallbackInfo ci) {
        if (BossScreen.locksCamera()) ci.cancel();
    }
}
