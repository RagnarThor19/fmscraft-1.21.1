package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.wrath.OrnamixEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * ORNAMIX: his skin on a player model, holding his iron sword. The sword burns (flames licking along the blade, drawn
 * as particles that follow it wherever his arm goes), he whirls round when he spins, and in Ragemode his eyes burn
 * (a glowing layer, like a spider's or an enderman's eyes).
 */
public class OrnamixRenderer extends BipedEntityRenderer<OrnamixEntity, OrnamixModel> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/ornamix/ornamix.png");
    private static final RenderLayer EYES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/ornamix/ornamix_eyes.png"));
    /** How fast he turns while spinning, degrees a tick. */
    private static final float SPIN_SPEED = 48f;

    public OrnamixRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new OrnamixModel(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        addFeature(new FireEyes(this));
        addFeature(new BurningSword(this));
    }

    @Override
    public Identifier getTexture(OrnamixEntity boss) {
        return TEXTURE;
    }

    @Override
    protected void setupTransforms(OrnamixEntity boss, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        if (boss.poseState() == OrnamixEntity.POSE_SPIN) bodyYaw += (boss.age + tickDelta) * SPIN_SPEED;
        super.setupTransforms(boss, matrices, animationProgress, bodyYaw, tickDelta, scale);
    }

    /** Ragemode's burning eyes. */
    private static final class FireEyes extends EyesFeatureRenderer<OrnamixEntity, OrnamixModel> {
        FireEyes(FeatureRendererContext<OrnamixEntity, OrnamixModel> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, OrnamixEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (boss.isRaging()) super.render(matrices, vertexConsumers, light, boss, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return EYES;
        }
    }

    /**
     * Flames along the sword's blade. Works out where the blade is exactly the way the held item is drawn (the arm, then
     * the hand, then the sword's third-person transform from item/handheld.json), and lets a few flames off it once a
     * tick. Entities are drawn relative to the camera, so the camera's position turns that into a place in the world.
     */
    private static final class BurningSword extends FeatureRenderer<OrnamixEntity, OrnamixModel> {
        /** The blade on the iron sword's texture, from just above the guard to the tip (in item space, 0-1, y up). */
        private static final float BASE = 0.38f, TIP = 0.94f;

        BurningSword(FeatureRendererContext<OrnamixEntity, OrnamixModel> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, OrnamixEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (boss.clientFlameAge == boss.age || client.isPaused() || client.world == null) return;
            boss.clientFlameAge = boss.age;
            if (!boss.getMainHandStack().isOf(Items.IRON_SWORD)) return;

            matrices.push();
            getContextModel().setArmAngle(Arm.RIGHT, matrices);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
            matrices.translate(1 / 16.0f, 0.125f, -0.625f);
            // item/handheld, thirdperson_righthand
            matrices.translate(0, 4 / 16.0f, 0.5f / 16.0f);
            matrices.multiply(new Quaternionf().rotationXYZ(0, (float) Math.toRadians(-90), (float) Math.toRadians(55)));
            matrices.scale(0.85f, 0.85f, 0.85f);
            matrices.translate(-0.5f, -0.5f, -0.5f);
            Matrix4f blade = new Matrix4f(matrices.peek().getPositionMatrix());
            matrices.pop();

            Vec3d camera = client.gameRenderer.getCamera().getPos();
            Random random = boss.getRandom();
            boolean rage = boss.isRaging();
            int flames = rage ? 3 : 2;
            for (int i = 0; i < flames; i++) {
                float u = BASE + random.nextFloat() * (TIP - BASE);
                Vector3f at = blade.transformPosition(u, u, 0.5f, new Vector3f());
                client.world.addParticle(i == 0 || !rage ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                        camera.x + at.x, camera.y + at.y, camera.z + at.z,
                        (random.nextFloat() - 0.5f) * 0.01, 0.015 + random.nextFloat() * 0.02, (random.nextFloat() - 0.5f) * 0.01);
            }
            if (boss.age % (rage ? 2 : 4) == 0) {
                Vector3f tip = blade.transformPosition(TIP, TIP, 0.5f, new Vector3f());
                client.world.addParticle(ParticleTypes.SMOKE, camera.x + tip.x, camera.y + tip.y, camera.z + tip.z, 0, 0.03, 0);
            }
        }
    }
}
