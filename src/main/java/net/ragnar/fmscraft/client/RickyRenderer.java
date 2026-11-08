package net.ragnar.fmscraft.client;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.sloth.RickyEntity;
import net.ragnar.fmscraft.boss.sloth.RickyLook;

/**
 * RICKY: Patrik's skin on a player model, the tome he's casting in his hand. Super Saiyan he wears the second skin (its
 * hair flickering on and off while he powers up), his golden hair and teal eyes burn with their own light
 * (ricky_saiyan_glow.png, tools/textures/ricky_textures.py), and his aura blazes round him: the shimmering energy a
 * charged creeper wears, tinted gold, on a slightly bigger copy of him. When he moves too fast to see he leaves
 * afterimages, see-through copies of himself (dark red in phase 1, gold after). Lying asleep, sitting up, flying and
 * diving tip his whole body.
 */
public class RickyRenderer extends MobEntityRenderer<RickyEntity, RickyModel<RickyEntity>> {
    private static final Identifier SKIN = Identifier.of(FmsCraft.MOD_ID, "textures/entity/ricky/ricky.png");
    private static final Identifier SAIYAN = Identifier.of(FmsCraft.MOD_ID, "textures/entity/ricky/ricky_saiyan.png");
    private static final RenderLayer GLOW = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/ricky/ricky_saiyan_glow.png"));
    private static final Identifier SWIRL = Identifier.ofVanilla("textures/entity/creeper/creeper_armor.png");
    private static final int[] GHOSTS = {2, 4, 6, 8};
    private static final float[] GHOST_ALPHA = {0.5f, 0.36f, 0.24f, 0.13f};
    private final PlayerEntityModel<RickyEntity> aura;

    public RickyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RickyModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        aura = new PlayerEntityModel<>(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(0.45f), false), 64, 64).createModel(), false);
        addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
        addFeature(new Glow(this));
    }

    @Override
    public Identifier getTexture(RickyEntity boss) {
        return boss.saiyan() ? SAIYAN : SKIN;
    }

    @Override
    public void render(RickyEntity boss, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (boss.isInvisible()) return;
        super.render(boss, yaw, tickDelta, matrices, buffers, light);
        if (boss.afterimages()) afterimages(boss, tickDelta, matrices, buffers, light);
    }

    @Override
    protected void setupTransforms(RickyEntity boss, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        super.setupTransforms(boss, matrices, animationProgress, bodyYaw, tickDelta, scale);
        switch (boss.poseState()) {
            case RickyLook.POSE_SLEEP -> {
                // Laid out flat on his back in the grass, face to the sky, his feet the way he faces
                matrices.translate(0, 0.14f, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
                matrices.translate(0, -0.95f, 0);
            }
            case RickyLook.POSE_SIT -> matrices.translate(0, -0.62f, 0);
            case RickyLook.POSE_FLY -> {
                matrices.translate(0, 0.9f, 0);
                // Leaning into it, head first
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-50f));
                matrices.translate(0, -0.9f, 0);
            }
            case RickyLook.POSE_DIVE -> {
                matrices.translate(0, 0.9f, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-160f));
                matrices.translate(0, -0.9f, 0);
            }
            default -> {
            }
        }
    }

    /** See-through copies of him where he was a moment ago. */
    private void afterimages(RickyEntity boss, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        Vec3d now = boss.getLerpedPos(tickDelta);
        var vertices = buffers.getBuffer(RenderLayer.getEntityTranslucent(getTexture(boss)));
        int tint = boss.saiyan() ? 0xFFE070 : 0xC03040;
        for (int i = 0; i < GHOSTS.length; i++) {
            Vec3d at = boss.trailPos(GHOSTS[i]);
            if (at == null || at.squaredDistanceTo(now) < 0.09) continue;
            Vec3d off = at.subtract(now);
            int alpha = (int) (GHOST_ALPHA[i] * 255);
            matrices.push();
            matrices.translate(off.x, off.y, off.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - boss.trailYaw(GHOSTS[i])));
            matrices.scale(-1f, -1f, 1f);
            matrices.translate(0f, -1.501f, 0f);
            getModel().render(matrices, vertices, light, OverlayTexture.DEFAULT_UV, (alpha << 24) | tint);
            matrices.pop();
        }
    }

    /** The golden hair and the eyes burning; and round him, the aura. */
    private final class Glow extends EyesFeatureRenderer<RickyEntity, RickyModel<RickyEntity>> {
        Glow(FeatureRendererContext<RickyEntity, RickyModel<RickyEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, RickyEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (boss.saiyan()) super.render(matrices, buffers, light, boss, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
            if (boss.aura() != RickyLook.AURA_SAIYAN) return;
            // The aura: a shimmer of gold flowing up round him, pulsing
            float t = boss.age + tickDelta;
            getContextModel().copyStateTo(aura);
            aura.setAngles(boss, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
            copyPose(getContextModel(), aura);
            float pulse = 0.75f + 0.25f * MathHelper.sin(t * 0.6f);
            int r = (int) (255 * pulse), g = (int) (200 * pulse), b = (int) (60 * pulse);
            var swirl = buffers.getBuffer(RenderLayer.getEnergySwirl(SWIRL, (t * 0.006f) % 1f, (-t * 0.02f) % 1f));
            aura.render(matrices, swirl, 0xF000F0, OverlayTexture.DEFAULT_UV, 0xFF000000 | r << 16 | g << 8 | b);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return GLOW;
        }
    }

    /** The aura model wears exactly his pose (RickyModel sets more than a plain player model would). */
    private static void copyPose(PlayerEntityModel<RickyEntity> from, PlayerEntityModel<RickyEntity> to) {
        to.head.copyTransform(from.head);
        to.hat.copyTransform(from.hat);
        to.body.copyTransform(from.body);
        to.rightArm.copyTransform(from.rightArm);
        to.leftArm.copyTransform(from.leftArm);
        to.rightLeg.copyTransform(from.rightLeg);
        to.leftLeg.copyTransform(from.leftLeg);
        to.jacket.copyTransform(from.jacket);
        to.rightSleeve.copyTransform(from.rightSleeve);
        to.leftSleeve.copyTransform(from.leftSleeve);
        to.rightPants.copyTransform(from.rightPants);
        to.leftPants.copyTransform(from.leftPants);
    }
}
