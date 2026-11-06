package net.ragnar.fmscraft.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.client.EnvySky;
import net.ragnar.fmscraft.client.GluttonySky;
import net.ragnar.fmscraft.client.GreedSky;
import net.ragnar.fmscraft.client.LustSky;
import net.ragnar.fmscraft.client.PrideSky;
import net.ragnar.fmscraft.boss.pride.PrideArena;
import net.ragnar.fmscraft.client.WrathSky;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RAGGIMEL's night: no sun and no daylit dome, only vanilla's stars, turning slowly, over the dark blue the fog
 * clears the screen to (BackgroundRendererMixin). ORNAMIX's Ragemode: no sky at all, just the black red the fog clears
 * the screen to. GUMMSS's void: no sky either, only the deep green fog over the black. LalliLarge's space: the stars and
 * the sun on black, turning on Apple Pie's strums; his snowfield: a grey overcast and no clouds (GluttonySky). MUGEN's
 * compound: stars and the full moon over a violet night, or the red eclipse, and black in his Blackout (LustSky).
 * LEONARD's plaza: the noon sky with banks of cloud all round the horizon, or the storm, and the game's own clouds
 * drawn as a sea far under the plaza (PrideSky).
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow
    private VertexBuffer starsBuffer;

    /** True while drawing the clouds again at our own height (PrideSky's sea of cloud). */
    private static boolean fmscraft$cloudSea;

    @Inject(method = "renderSky", at = @At("TAIL"))
    private void fmscraft$prideBanks(Matrix4f matrix4f, Matrix4f projectionMatrix, float tickDelta, Camera camera,
                                     boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if (PrideSky.active() && PrideSky.vanillaSky(tickDelta)) PrideSky.renderBanks(matrix4f, tickDelta);
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void fmscraft$greedNight(Matrix4f matrix4f, Matrix4f projectionMatrix, float tickDelta, Camera camera,
                                     boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if (PrideSky.active() && !PrideSky.vanillaSky(tickDelta)) {
            // The storm: no sun, no blue, only the banks of grey cloud
            ci.cancel();
            fogCallback.run();
            PrideSky.renderBanks(matrix4f, tickDelta);
            return;
        }
        if (LustSky.active()) {
            ci.cancel();
            fogCallback.run();
            LustSky.render(matrix4f, projectionMatrix, tickDelta, starsBuffer, fogCallback);
            return;
        }
        if (GluttonySky.space()) {
            ci.cancel();
            fogCallback.run();
            GluttonySky.renderSpace(matrix4f, projectionMatrix, tickDelta, starsBuffer, fogCallback);
            return;
        }
        if (WrathSky.rage(tickDelta) >= 0.5f || EnvySky.active() || GluttonySky.snow()) {
            ci.cancel();
            fogCallback.run();
            return;
        }
        float night = GreedSky.night(tickDelta);
        if (night < 0.5f || starsBuffer == null) return;
        ci.cancel();
        fogCallback.run();
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(matrix4f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(160.0f + (System.currentTimeMillis() % 1_200_000L) / 1_200_000f * 360f));
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        float stars = 0.75f * night;
        RenderSystem.setShaderColor(stars, stars, stars, stars);
        BackgroundRenderer.clearFog();
        starsBuffer.bind();
        starsBuffer.draw(matrices.peek().getPositionMatrix(), projectionMatrix, GameRenderer.getPositionProgram());
        VertexBuffer.unbind();
        fogCallback.run();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    /** No clouds round LalliLarge: in space they'd cut through the Earth, and the snowfield's sky is one grey overcast. */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void fmscraft$noClouds(MatrixStack matrices, Matrix4f matrix4f, Matrix4f matrix4f2, float tickDelta, double cameraX, double cameraY,
                                   double cameraZ, CallbackInfo ci) {
        if (GluttonySky.space() || GluttonySky.snow() || LustSky.active()) ci.cancel();
        else if (PrideSky.active() && !fmscraft$cloudSea) {
            // The same clouds, but far under the plaza: drawn as if the camera were that much higher
            ci.cancel();
            fmscraft$cloudSea = true;
            try {
                ((WorldRenderer) (Object) this).renderClouds(matrices, matrix4f, matrix4f2, tickDelta, cameraX, cameraY + (192.0 - PrideArena.CLOUD_SEA), cameraZ);
            } finally {
                fmscraft$cloudSea = false;
            }
        }
    }
}
