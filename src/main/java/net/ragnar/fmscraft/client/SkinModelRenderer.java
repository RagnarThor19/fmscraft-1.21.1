package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.photo.SkinModelEntity;
import net.ragnar.fmscraft.photo.SkinModelPose;

/** Temporary, for the title screen panoramas: a friend's skin on a player model, frozen in its pose. */
public class SkinModelRenderer extends LivingEntityRenderer<SkinModelEntity, PlayerEntityModel<SkinModelEntity>> {
    private final PosedModel wide;
    private final PosedModel slim;

    public SkinModelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PosedModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        wide = (PosedModel) model;
        slim = new PosedModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(SkinModelEntity entity) {
        return Identifier.of(FmsCraft.MOD_ID, "textures/entity/skin_model/" + entity.skin().id() + ".png");
    }

    @Override
    public void render(SkinModelEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        model = entity.skin().slim() ? slim : wide;
        super.render(entity, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    protected void setupTransforms(SkinModelEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
        SkinModelPose pose = entity.pose();
        matrices.translate(0, pose.lift() + pose.pivot(), 0);
        // The model faces -Z here, so tipping it forward is a negative turn about X
        if (pose.pitch() != 0) matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(pose.pitch()));
        if (pose.roll() != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.roll()));
        matrices.translate(0, -pose.pivot(), 0);
    }

    @Override
    protected boolean hasLabel(SkinModelEntity entity) {
        return entity.hasCustomName() && entity.isCustomNameVisible();
    }

    static final class PosedModel extends PlayerEntityModel<SkinModelEntity> {
        PosedModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setAngles(SkinModelEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
            SkinModelPose pose = entity.pose();
            set(head, pose.head());
            set(body, pose.body());
            set(rightArm, pose.rightArm());
            set(leftArm, pose.leftArm());
            set(rightLeg, pose.rightLeg());
            set(leftLeg, pose.leftLeg());
            hat.copyTransform(head);
            jacket.copyTransform(body);
            rightSleeve.copyTransform(rightArm);
            leftSleeve.copyTransform(leftArm);
            rightPants.copyTransform(rightLeg);
            leftPants.copyTransform(leftLeg);
        }

        private static void set(ModelPart part, float[] degrees) {
            part.resetTransform();
            part.setAngles(degrees[0] * MathHelper.RADIANS_PER_DEGREE, degrees[1] * MathHelper.RADIANS_PER_DEGREE,
                    degrees[2] * MathHelper.RADIANS_PER_DEGREE);
        }
    }
}
