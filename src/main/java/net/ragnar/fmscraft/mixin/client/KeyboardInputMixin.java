package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.input.KeyboardInput;
import net.ragnar.fmscraft.client.MugenControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MUGEN's Controller: while he has you on his strings, your movement keys don't do what they should (MugenControl). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void fmscraft$mugenControl(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        MugenControl.scramble((KeyboardInput) (Object) this);
    }
}
