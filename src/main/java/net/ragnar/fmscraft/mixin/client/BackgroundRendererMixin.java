package net.ragnar.fmscraft.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.client.GardenFog;
import net.ragnar.fmscraft.client.VaultFog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Down in a Greed Vault, the fog turns dark gray and closes in. Never thinner than what's already there (darkness...).
 * In Lust's Garden it only takes on a rose tint.
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;

    @Inject(method = "render", at = @At("TAIL"))
    private static void fmscraft$vaultFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
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
        float s = VaultFog.strength(tickDelta);
        if (s <= 0) return;
        float start = RenderSystem.getShaderFogStart(), end = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Math.min(start, MathHelper.lerp(s, start, VaultFog.START)));
        RenderSystem.setShaderFogEnd(Math.min(end, MathHelper.lerp(s, end, VaultFog.END)));
    }
}
