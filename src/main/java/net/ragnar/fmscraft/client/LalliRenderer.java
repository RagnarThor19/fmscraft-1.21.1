package net.ragnar.fmscraft.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.gluttony.LalliEntity;

/**
 * LalliLarge: Larus's skin on a slim player model, three blocks tall (his scale attribute), empty-handed. In phase 2
 * his eyes burn a glacier blue (lalli_eyes.png, tools/textures/lalli_textures.py). His ice blade is drawn in his right
 * hand out of ice blocks (so it follows every swing of his arm). His Ice Beam is drawn like the
 * guardian's beam in pale ice blue: a thin flickering line while it charges, thick and bright once it fires.
 */
public class LalliRenderer extends BipedEntityRenderer<LalliEntity, LalliModel> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/lalli/lalli.png");
    private static final RenderLayer EYES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/lalli/lalli_eyes.png"));
    private static final RenderLayer BEAM = RenderLayer.getEntityCutoutNoCull(Identifier.ofVanilla("textures/entity/guardian_beam.png"));

    public LalliRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new LalliModel(ctx.getPart(EntityModelLayers.PLAYER_SLIM)), 0.5f);
        addFeature(new IceEyes(this));
        addFeature(new IceBlade(this));
    }

    @Override
    public Identifier getTexture(LalliEntity boss) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(LalliEntity boss, Frustum frustum, double x, double y, double z) {
        return boss.beamStage() != LalliEntity.BEAM_OFF || super.shouldRender(boss, frustum, x, y, z);
    }

    @Override
    public void render(LalliEntity boss, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (boss.isInvisible()) return;
        super.render(boss, yaw, tickDelta, matrices, buffers, light);
        int stage = boss.beamStage();
        if (stage == LalliEntity.BEAM_OFF) return;
        float age = boss.age + tickDelta;
        Vec3d at = boss.getLerpedPos(tickDelta);
        Vec3d from = boss.handPos(tickDelta).subtract(at), to = boss.beamEnd().subtract(at);
        VertexConsumer vertices = buffers.getBuffer(BEAM);
        if (stage == LalliEntity.BEAM_CHARGE) {
            float flicker = 0.6f + 0.4f * MathHelper.sin(age * 1.7f);
            beam(matrices, vertices, from, to, 0.04f, (int) (140 * flicker), (int) (210 * flicker), 255, age);
        } else {
            beam(matrices, vertices, from, to, 0.34f, 150, 215, 255, age * 2);
            beam(matrices, vertices, from, to, 0.14f, 235, 250, 255, age * 3);
        }
    }

    /** Two crossed quads from {@code from} to {@code to}, scrolling, like the guardian's beam. */
    private static void beam(MatrixStack matrices, VertexConsumer vertices, Vec3d from, Vec3d to, float radius,
                             int r, int g, int b, float time) {
        Vec3d d = to.subtract(from);
        float length = (float) d.length();
        if (length < 0.01f) return;
        d = d.multiply(1 / length);
        float pitch = (float) Math.acos(MathHelper.clamp(d.y, -1, 1));
        float yaw = (float) Math.atan2(d.z, d.x);
        matrices.push();
        matrices.translate(from.x, from.y, from.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathHelper.HALF_PI - yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
        MatrixStack.Entry entry = matrices.peek();
        float spin = time * 0.05f * -1.5f;
        float v0 = -1.0f + (time * 0.5f % 1.0f), v1 = length * 2.5f + v0;
        for (int i = 0; i < 2; i++) {
            float a = spin + i * MathHelper.HALF_PI;
            float x1 = MathHelper.cos(a) * radius, z1 = MathHelper.sin(a) * radius;
            vertex(vertices, entry, x1, length, z1, r, g, b, 0.4999f, v1);
            vertex(vertices, entry, x1, 0, z1, r, g, b, 0.4999f, v0);
            vertex(vertices, entry, -x1, 0, -z1, r, g, b, 0.0f, v0);
            vertex(vertices, entry, -x1, length, -z1, r, g, b, 0.0f, v1);
        }
        matrices.pop();
    }

    private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, float x, float y, float z, int r, int g, int b, float u, float v) {
        vertices.vertex(entry, x, y, z)
                .color(r, g, b, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(entry, 0.0f, 1.0f, 0.0f);
    }

    /**
     * The ice blade in his right hand while he has it: a long blade of packed ice with a glassy ice edge, a blue ice
     * guard and grip, built from blocks like a block display, carried on his arm (about four blocks long at his size).
     */
    private static final class IceBlade extends net.minecraft.client.render.entity.feature.FeatureRenderer<LalliEntity, LalliModel> {
        private static final net.minecraft.block.BlockState BODY = net.minecraft.block.Blocks.PACKED_ICE.getDefaultState(),
                EDGE = net.minecraft.block.Blocks.ICE.getDefaultState(), HILT = net.minecraft.block.Blocks.BLUE_ICE.getDefaultState();

        IceBlade(FeatureRendererContext<LalliEntity, LalliModel> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, LalliEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!boss.hasBlade()) return;
            matrices.push();
            getContextModel().rightArm.rotate(matrices);
            // To his fist; the blade runs out forward from it (up when his arm is raised)
            matrices.translate(-0.03f, 0.62f, 0f);
            int glow = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            // grip, guard, blade, edge: x/y size, then z from and to (forward is -z)
            box(matrices, buffers, HILT, 0.13f, 0.13f, 0.22f, -0.05f, glow);
            box(matrices, buffers, HILT, 0.55f, 0.12f, -0.05f, -0.17f, glow);
            box(matrices, buffers, BODY, 0.26f, 0.07f, -0.17f, -2.1f, glow);
            box(matrices, buffers, EDGE, 0.12f, 0.05f, -0.2f, -2.45f, glow);
            matrices.pop();
        }

        private static void box(MatrixStack matrices, VertexConsumerProvider buffers, net.minecraft.block.BlockState state,
                                float w, float h, float from, float to, int light) {
            matrices.push();
            matrices.translate(-w / 2, -h / 2, Math.min(from, to));
            matrices.scale(w, h, Math.abs(to - from));
            net.minecraft.client.MinecraftClient.getInstance().getBlockRenderManager()
                    .renderBlockAsEntity(state, matrices, buffers, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }

    private static final class IceEyes extends EyesFeatureRenderer<LalliEntity, LalliModel> {
        IceEyes(FeatureRendererContext<LalliEntity, LalliModel> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LalliEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (boss.hasGlowingEyes()) super.render(matrices, vertexConsumers, light, boss, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return EYES;
        }
    }
}
