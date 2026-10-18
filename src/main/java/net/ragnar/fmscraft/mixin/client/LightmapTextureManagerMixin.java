package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.ragnar.fmscraft.client.VaultFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Down in a Greed Vault the light itself goes dim, the way the Darkness effect dims it, but steady instead of
 * pulsing: the brightness setting stops helping ({@link VaultFog#GLOOM}) and every light level drops a little
 * ({@link VaultFog#DIM}). Scaled by the "Darkness Effect" accessibility slider like the real thing.
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @Unique
    private float fmscraft$realFactor;
    @Unique
    private float fmscraft$factor;

    @Inject(method = "getDarknessFactor", at = @At("RETURN"), cancellable = true)
    private void fmscraft$vaultGloom(float delta, CallbackInfoReturnable<Float> cir) {
        float real = cir.getReturnValue(), s = VaultFog.strength(delta);
        fmscraft$realFactor = real;
        fmscraft$factor = s > 0 ? Math.max(real, VaultFog.GLOOM * s) : real;
        cir.setReturnValue(fmscraft$factor);
    }

    @Inject(method = "getDarkness", at = @At("RETURN"), cancellable = true)
    private void fmscraft$vaultDim(LivingEntity entity, float factor, float delta, CallbackInfoReturnable<Float> cir) {
        float s = VaultFog.strength(delta);
        if (s <= 0) return;
        // Only a real Darkness effect pulses; the vault's part is steady
        float pulse = fmscraft$factor > 0 ? cir.getReturnValue() * (fmscraft$realFactor / fmscraft$factor) : 0;
        cir.setReturnValue(Math.max(pulse, VaultFog.DIM * s));
    }
}
