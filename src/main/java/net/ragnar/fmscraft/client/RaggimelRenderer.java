package net.ragnar.fmscraft.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.greed.RaggimelEntity;

/**
 * RAGGIMEL: his skin on a player model, his bubble around him while he rides it, and his lasers, drawn like the
 * guardian's beam in pale blue: a thin flickering line while charging, white when it locks, thick when it fires.
 */
public class RaggimelRenderer extends BipedEntityRenderer<RaggimelEntity, RaggimelModel> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/raggimel/raggimel.png");
    private static final RenderLayer BEAM = RenderLayer.getEntityCutoutNoCull(Identifier.ofVanilla("textures/entity/guardian_beam.png"));

    public RaggimelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new RaggimelModel(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(RaggimelEntity boss) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(RaggimelEntity boss, Frustum frustum, double x, double y, double z) {
        return boss.hasBeams() || super.shouldRender(boss, frustum, x, y, z);
    }

    @Override
    public void render(RaggimelEntity boss, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        super.render(boss, yaw, tickDelta, matrices, buffers, light);
        float age = boss.age + tickDelta;
        if (boss.isBubbled()) {
            matrices.push();
            matrices.translate(0, boss.getHeight() / 2, 0);
            BubbleCube.draw(matrices, buffers, light, boss.getHeight() * 1.25f, age);
            matrices.pop();
        }
        if (!boss.hasBeams()) return;
        Vec3d at = boss.getLerpedPos(tickDelta);
        VertexConsumer vertices = buffers.getBuffer(BEAM);
        for (int slot = 0; slot < RaggimelEntity.BEAM_SLOTS; slot++) {
            int stage = boss.beamStage(slot);
            if (stage == RaggimelEntity.BEAM_OFF) continue;
            Vec3d from = boss.beamOrigin(boss.beamLow(slot), tickDelta).subtract(at);
            Vec3d to = boss.beamEnd(slot).subtract(at);
            switch (stage) {
                case RaggimelEntity.BEAM_CHARGE -> {
                    float flicker = 0.6f + 0.4f * MathHelper.sin(age * 1.7f + slot);
                    beam(matrices, vertices, from, to, 0.035f, (int) (120 * flicker), (int) (200 * flicker), 255, age);
                }
                case RaggimelEntity.BEAM_LOCK -> {
                    boolean on = ((int) age & 1) == 0;
                    beam(matrices, vertices, from, to, on ? 0.07f : 0.04f, 255, 255, 255, age);
                }
                default -> {
                    beam(matrices, vertices, from, to, 0.32f, 150, 225, 255, age * 2);
                    beam(matrices, vertices, from, to, 0.13f, 255, 255, 255, age * 3);
                }
            }
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
}
