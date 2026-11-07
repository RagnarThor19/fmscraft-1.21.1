package net.ragnar.fmscraft.mixin.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.ragnar.fmscraft.boss.envy.GummssGuise;
import net.ragnar.fmscraft.boss.envy.GummssLook;
import net.ragnar.fmscraft.client.GummssGuiseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GUMMSS's Disguise: whoever's face he's taken is drawn as him (GummssGuiseRenderer), and not at all while they're
 * shut in his cup. Everyone sees it, them too (in third person; their own hands in first person stay theirs).
 */
@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void fmscraft$drawnAsGummss(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices,
                                        VertexConsumerProvider buffers, int light, CallbackInfo ci) {
        int stage = GummssGuise.stageOf(player.getUuid(), player.getWorld().getTime());
        if (stage == GummssLook.GUISE_OFF) return;
        ci.cancel();
        GummssGuiseRenderer guise = GummssGuiseRenderer.get();
        if (stage == GummssLook.GUISE_WORN && guise != null) guise.render(player, yaw, tickDelta, matrices, buffers, light);
    }
}
