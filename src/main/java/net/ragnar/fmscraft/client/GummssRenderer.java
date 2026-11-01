package net.ragnar.fmscraft.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.envy.GummssLook;

/**
 * GUMMSS: his skin on a player model, holding his golden staff. His eyes glow green while he casts. Unseen (Vanish)
 * nothing of him is drawn, not even the staff. His Mirror Images use this too, with no shadow: the only way to tell.
 */
public class GummssRenderer<T extends HostileEntity & GummssLook> extends BipedEntityRenderer<T, GummssModel<T>> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/gummss/gummss.png");
    private static final RenderLayer EYES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/gummss/gummss_eyes.png"));

    public GummssRenderer(EntityRendererFactory.Context ctx, float shadow) {
        super(ctx, new GummssModel<>(ctx.getPart(EntityModelLayers.PLAYER)), shadow);
        addFeature(new CastingEyes<>(this));
    }

    @Override
    public Identifier getTexture(T boss) {
        return TEXTURE;
    }

    @Override
    public void render(T boss, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (boss.isInvisible()) return;
        super.render(boss, yaw, tickDelta, matrices, buffers, light);
    }

    private static boolean casting(int pose) {
        return pose == GummssLook.POSE_CAST || pose == GummssLook.POSE_SKY || pose == GummssLook.POSE_STARE
                || pose == GummssLook.POSE_FALL || pose == GummssLook.POSE_LIFT || pose == GummssLook.POSE_LAND;
    }

    private static final class CastingEyes<T extends HostileEntity & GummssLook> extends EyesFeatureRenderer<T, GummssModel<T>> {
        CastingEyes(FeatureRendererContext<T, GummssModel<T>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (casting(boss.poseState())) super.render(matrices, vertexConsumers, light, boss, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return EYES;
        }
    }
}
