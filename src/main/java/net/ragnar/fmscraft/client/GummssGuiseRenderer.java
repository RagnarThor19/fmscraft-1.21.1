package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.envy.GummssEntity;
import net.ragnar.fmscraft.boss.envy.GummssGuise;

/**
 * A player whose face GUMMSS has taken (his Disguise), drawn as him: his skin on the wide model at his size, his staff
 * in the right hand and nothing else of theirs (no armour, cape, held items or name), his free hand never quite still.
 * They move as they move. Called from PlayerEntityRendererMixin in place of vanilla's player renderer.
 */
public class GummssGuiseRenderer extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/gummss/gummss.png");
    private static GummssGuiseRenderer instance;

    /** Built with the entity renderers (on every resource reload). */
    public static void init(EntityRendererFactory.Context ctx) {
        instance = new GummssGuiseRenderer(ctx);
    }

    public static GummssGuiseRenderer get() {
        return instance;
    }

    private GummssGuiseRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        addFeature(new Staff(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(AbstractClientPlayerEntity player) {
        return TEXTURE;
    }

    @Override
    public void render(AbstractClientPlayerEntity player, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        model.setVisible(true);
        model.sneaking = player.isInSneakingPose();
        model.rightArmPose = BipedEntityModel.ArmPose.ITEM;
        model.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.render(player, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    protected boolean hasLabel(AbstractClientPlayerEntity player) {
        return false;
    }

    /** His staff: the one he's really holding, as he holds it. */
    static ItemStack staff() {
        var world = MinecraftClient.getInstance().world;
        if (world != null && world.getEntityById(GummssGuise.bossId()) instanceof GummssEntity boss && !boss.getMainHandStack().isEmpty()) {
            return boss.getMainHandStack();
        }
        return new ItemStack(Items.BLAZE_ROD);
    }

    private static final class Model extends PlayerEntityModel<AbstractClientPlayerEntity> {
        Model(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setAngles(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            super.setAngles(player, limbAngle, limbDistance, age, headYaw, headPitch);
            // As GummssModel's idle: his free hand never quite still
            float sway = MathHelper.cos(age * 0.6662f) * 0.12f;
            leftArm.pitch += -0.2f + sway * 0.4f;
            leftArm.roll -= 0.1f;
            leftSleeve.copyTransform(leftArm);
        }
    }

    private static final class Staff extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        private final HeldItemRenderer items;

        Staff(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context, HeldItemRenderer items) {
            super(context);
            this.items = items;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            // As HeldItemFeatureRenderer holds it in a right hand
            matrices.push();
            getContextModel().setArmAngle(Arm.RIGHT, matrices);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            matrices.translate(1 / 16f, 0.125f, -0.625f);
            items.renderItem(player, staff(), ModelTransformationMode.THIRD_PERSON_RIGHT_HAND, false, matrices, buffers, light);
            matrices.pop();
        }
    }
}
