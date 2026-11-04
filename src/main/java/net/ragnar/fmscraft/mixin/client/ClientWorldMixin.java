package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.client.EnvySky;
import net.ragnar.fmscraft.client.GluttonySky;
import net.ragnar.fmscraft.client.GreedSky;
import net.ragnar.fmscraft.client.LustSky;
import net.ragnar.fmscraft.client.WrathSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Over RAGGIMEL's island the sky is pale blue by day and dark blue at night, and the night dims the light (GreedSky).
 * Over ORNAMIX's colosseum it's bright red, then almost black red in Ragemode, which dims the light too (WrathSky).
 * Round GUMMSS's eye the light is always dim (EnvySky). In space round LalliLarge's Earth the sky is black and the sun
 * shines full; over his snowfield it's a grey overcast and the light is dimmer (GluttonySky). Over MUGEN's compound
 * it's a violet night, then a blood-red eclipse, and the light is dim (LustSky).
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void fmscraft$greedSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (GreedSky.active()) cir.setReturnValue(GreedSky.mix(GreedSky.DAY_SKY, GreedSky.NIGHT_SKY, tickDelta));
        else if (WrathSky.active()) cir.setReturnValue(WrathSky.mix(WrathSky.RED_SKY, WrathSky.RAGE_SKY, tickDelta));
        else if (GluttonySky.space()) cir.setReturnValue(Vec3d.ZERO);
        else if (GluttonySky.snow()) cir.setReturnValue(GluttonySky.SNOW_SKY);
        else if (LustSky.active()) cir.setReturnValue(LustSky.skyColor(tickDelta));
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true)
    private void fmscraft$greedClouds(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (GreedSky.active()) cir.setReturnValue(GreedSky.mix(cir.getReturnValue(), GreedSky.NIGHT_CLOUDS, tickDelta));
        else if (WrathSky.active()) cir.setReturnValue(WrathSky.mix(WrathSky.RED_CLOUDS, WrathSky.RAGE_CLOUDS, tickDelta));
    }

    @Inject(method = "getSkyBrightness", at = @At("RETURN"), cancellable = true)
    private void fmscraft$greedLight(float tickDelta, CallbackInfoReturnable<Float> cir) {
        float night = GreedSky.night(tickDelta);
        if (night > 0) cir.setReturnValue(MathHelper.lerp(night, cir.getReturnValue(), GreedSky.NIGHT_LIGHT));
        float rage = WrathSky.rage(tickDelta);
        if (rage > 0) cir.setReturnValue(MathHelper.lerp(rage, cir.getReturnValue(), WrathSky.RAGE_LIGHT));
        if (EnvySky.active()) cir.setReturnValue(EnvySky.LIGHT);
        if (GluttonySky.space()) cir.setReturnValue(1.0f);
        if (GluttonySky.snow()) cir.setReturnValue(GluttonySky.SNOW_LIGHT);
        if (LustSky.active()) cir.setReturnValue(LustSky.light(tickDelta));
    }
}
