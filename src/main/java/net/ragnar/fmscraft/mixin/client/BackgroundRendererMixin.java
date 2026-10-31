package net.ragnar.fmscraft.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.client.GardenFog;
import net.ragnar.fmscraft.client.GreedSky;
import net.ragnar.fmscraft.client.VaultFog;
import net.ragnar.fmscraft.client.WrathSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Down in a Greed Vault, the fog turns dark gray and closes in. Never thinner than what's already there (darkness...).
 * In Lust's Garden it only takes on a rose tint. Over RAGGIMEL's island it's pale blue by day and dark blue at night,
 * when it also closes in a little (GreedSky). Over ORNAMIX's colosseum it's a hot orange red, and in Ragemode a dark
 * red that closes right in round the pit (WrathSky).
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void fmscraft$vaultFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (GreedSky.active()) {
            Vec3d fog = GreedSky.mix(GreedSky.DAY_FOG, GreedSky.NIGHT_FOG, tickDelta);
            red = (float) fog.x;
            green = (float) fog.y;
            blue = (float) fog.z;
            RenderSystem.clearColor(red, green, blue, 0.0f);
            return;
        }
        if (WrathSky.active()) {
            Vec3d fog = WrathSky.mix(WrathSky.RED_FOG, WrathSky.RAGE_FOG, tickDelta);
            red = (float) fog.x;
            green = (float) fog.y;
            blue = (float) fog.z;
            RenderSystem.clearColor(red, green, blue, 0.0f);
            return;
        }
        float rose = GardenFog.strength(tickDelta), s = VaultFog.strength(tickDelta);
        if (rose <= 0 && s <= 0) return;
        if (rose > 0) {
            red = MathHelper.lerp(rose, red, GardenFog.RED);
            green = MathHelper.lerp(rose, green, GardenFog.GREEN);
            blue = MathHelper.lerp(rose, blue, GardenFog.BLUE);
        }
        if (s > 0) {
            red = MathHelper.lerp(s, red, VaultFog.RED);
            green = MathHelper.lerp(s, green, VaultFog.GREEN);
            blue = MathHelper.lerp(s, blue, VaultFog.BLUE);
        }
        RenderSystem.clearColor(red, green, blue, 0.0f);
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void fmscraft$vaultFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        float night = GreedSky.night(tickDelta);
        if (night > 0 && fogType == BackgroundRenderer.FogType.FOG_TERRAIN) {
            float start = RenderSystem.getShaderFogStart(), end = RenderSystem.getShaderFogEnd();
            RenderSystem.setShaderFogStart(Math.min(start, MathHelper.lerp(night, start, 24f)));
            RenderSystem.setShaderFogEnd(Math.min(end, MathHelper.lerp(night, end, 120f)));
        }
        float rage = WrathSky.rage(tickDelta);
        if (rage > 0 && fogType == BackgroundRenderer.FogType.FOG_TERRAIN) {
            float start = RenderSystem.getShaderFogStart(), end = RenderSystem.getShaderFogEnd();
            RenderSystem.setShaderFogStart(Math.min(start, MathHelper.lerp(rage, start, WrathSky.RAGE_FOG_START)));
            RenderSystem.setShaderFogEnd(Math.min(end, MathHelper.lerp(rage, end, WrathSky.RAGE_FOG_END)));
        }
        float s = VaultFog.strength(tickDelta);
        if (s <= 0) return;
        float start = RenderSystem.getShaderFogStart(), end = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Math.min(start, MathHelper.lerp(s, start, VaultFog.START)));
        RenderSystem.setShaderFogEnd(Math.min(end, MathHelper.lerp(s, end, VaultFog.END)));
    }
}
