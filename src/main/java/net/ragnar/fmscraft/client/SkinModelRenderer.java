package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.item.ModItems;
import net.ragnar.fmscraft.photo.SkinModelEntity;
import net.ragnar.fmscraft.photo.SkinModelPose;
import net.ragnar.fmscraft.photo.SkinModelSkin;

/**
 * Temporary, for the title screen panoramas: a friend's skin on a player model, frozen in its pose. A boss version
 * (/raggimel2) also wears his glowing eyes, his aura (the charged creeper's shimmer in his colour, on a slightly bigger
 * copy of him, like RICKY's) and, for CONEMAN, the cone.
 */
public class SkinModelRenderer extends LivingEntityRenderer<SkinModelEntity, PlayerEntityModel<SkinModelEntity>> {
    private static final Identifier SWIRL = Identifier.ofVanilla("textures/entity/creeper/creeper_armor.png");
    private final PosedModel wide;
    private final PosedModel slim;
    private final PlayerEntityModel<SkinModelEntity> wideAura;
    private final PlayerEntityModel<SkinModelEntity> slimAura;

    public SkinModelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PosedModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        wide = (PosedModel) model;
        slim = new PosedModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        wideAura = auraModel(false);
        slimAura = auraModel(true);
        addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
        addFeature(new Glow(this));
        addFeature(new Cone(this));
    }

    private static PlayerEntityModel<SkinModelEntity> auraModel(boolean slim) {
        return new PlayerEntityModel<>(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(0.45f), slim), 64, 64).createModel(), slim);
    }

    @Override
    public Identifier getTexture(SkinModelEntity entity) {
        return Identifier.of(FmsCraft.MOD_ID, "textures/entity/" + entity.skin().texture() + ".png");
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

    /** A boss's eyes burning, whatever the light; and round him, his aura, pulsing. */
    private final class Glow extends FeatureRenderer<SkinModelEntity, PlayerEntityModel<SkinModelEntity>> {
        Glow(FeatureRendererContext<SkinModelEntity, PlayerEntityModel<SkinModelEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, SkinModelEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            SkinModelSkin skin = entity.skin();
            if (skin.eyes() != null) {
                var eyes = buffers.getBuffer(RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/" + skin.eyes() + ".png")));
                getContextModel().render(matrices, eyes, 0xF000F0, OverlayTexture.DEFAULT_UV);
            }
            if (skin.aura() < 0) return;
            PlayerEntityModel<SkinModelEntity> aura = skin.slim() ? slimAura : wideAura;
            // A fresh model starts out as a baby's (child = true); this makes it a grown-up like the one it wraps
            getContextModel().copyStateTo(aura);
            copyPose(getContextModel(), aura);
            float t = entity.age + tickDelta;
            float pulse = 0.75f + 0.25f * MathHelper.sin(t * 0.6f);
            int r = (int) ((skin.aura() >> 16 & 0xFF) * pulse), g = (int) ((skin.aura() >> 8 & 0xFF) * pulse), b = (int) ((skin.aura() & 0xFF) * pulse);
            var swirl = buffers.getBuffer(RenderLayer.getEnergySwirl(SWIRL, (t * 0.006f) % 1f, (-t * 0.02f) % 1f));
            aura.render(matrices, swirl, 0xF000F0, OverlayTexture.DEFAULT_UV, 0xFF000000 | r << 16 | g << 8 | b);
        }
    }

    /** CONEMAN's traffic cone on his neck, drawn just as {@link LeonardRenderer} does. */
    private static final class Cone extends FeatureRenderer<SkinModelEntity, PlayerEntityModel<SkinModelEntity>> {
        private final ItemStack cone = new ItemStack(ModItems.TRAFFIC_CONE);

        Cone(FeatureRendererContext<SkinModelEntity, PlayerEntityModel<SkinModelEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, SkinModelEntity entity,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!entity.skin().cone()) return;
            matrices.push();
            getContextModel().head.rotate(matrices);
            matrices.translate(0f, 0.02f, 0f);
            matrices.scale(1f, -1f, -1f);
            matrices.translate(0f, 0.5f, 0f);
            MinecraftClient.getInstance().getItemRenderer().renderItem(cone, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
                    matrices, buffers, entity.getWorld(), entity.getId());
            matrices.pop();
        }
    }

    private static void copyPose(PlayerEntityModel<SkinModelEntity> from, PlayerEntityModel<SkinModelEntity> to) {
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
