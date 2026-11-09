package net.ragnar.fmscraft.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.lust.MugenEntity;
import net.ragnar.fmscraft.boss.lust.MugenLook;

/**
 * MUGEN: Karl's skin on a player model, a netherite blade in each hand, the two tails of his headband streaming out
 * behind his head (black wool under the moon, red in the eclipse). His eyes glow violet in phase 1 and burn red in
 * phase 2 (mugen_eyes.png / mugen_eyes_red.png, tools/textures/mugen_textures.py). When he moves too fast to see he
 * leaves afterimages: dark see-through copies of himself where he just was, fading. He spins for the kick and the blade
 * spin, tips back for the slide and turns over to dive.
 * <p>
 * His shadow clones use this too, with no shadow: the only way to tell.
 */
public class MugenRenderer<T extends HostileEntity & MugenLook> extends BipedEntityRenderer<T, MugenModel<T>> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/mugen/mugen.png");
    private static final RenderLayer VIOLET_EYES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/mugen/mugen_eyes.png"));
    private static final RenderLayer RED_EYES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/mugen/mugen_eyes_red.png"));
    /** Ticks back each afterimage is drawn, and how solid it is. */
    private static final int[] GHOSTS = {2, 4, 6, 8};
    private static final float[] GHOST_ALPHA = {0.5f, 0.36f, 0.24f, 0.13f};

    public MugenRenderer(EntityRendererFactory.Context ctx, float shadow) {
        super(ctx, new MugenModel<>(ctx.getPart(EntityModelLayers.PLAYER)), shadow);
        addFeature(new Eyes<>(this, MugenLook.EYES_VIOLET, VIOLET_EYES));
        addFeature(new Eyes<>(this, MugenLook.EYES_RED, RED_EYES));
        addFeature(new Headband<>(this));
    }

    @Override
    public Identifier getTexture(T boss) {
        return TEXTURE;
    }

    @Override
    public void render(T boss, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (boss.isInvisible()) return;
        super.render(boss, yaw, tickDelta, matrices, buffers, light);
        if (boss.afterimages() && boss instanceof MugenEntity real) afterimages(real, tickDelta, matrices, buffers, light);
    }

    /** Dark see-through copies of him where he was a moment ago, in the pose he's in now. */
    private void afterimages(MugenEntity boss, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        Vec3d now = boss.getLerpedPos(tickDelta);
        boolean red = boss.eyes() == MugenLook.EYES_RED;
        int tint = red ? 0x5A0A14 : 0x2C1046;
        var vertices = buffers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
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

    @Override
    protected void setupTransforms(T boss, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        super.setupTransforms(boss, matrices, animationProgress, bodyYaw, tickDelta, scale);
        int pose = boss.poseState();
        float time = boss.age + tickDelta;
        switch (pose) {
            // Round and round, faster than you can follow
            case MugenLook.POSE_KICK -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 62f));
            case MugenLook.POSE_SPIN -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 48f));
            // Tipped back, feet first along the ground
            case MugenLook.POSE_SLIDE -> {
                matrices.translate(0f, 0.25f, 0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(62f));
            }
            // Sat on the ground (his hips down at it), leaning back a little more as he looks up
            case MugenLook.POSE_SIT, MugenLook.POSE_SIT_SKY -> {
                matrices.translate(0f, -0.62f, 0f);
                matrices.translate(0f, 0.75f, 0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(4f + 9f * boss.skyward(tickDelta)));
                matrices.translate(0f, -0.75f, 0f);
            }
            // Turned over, head first, round his middle
            case MugenLook.POSE_DIVE -> {
                matrices.translate(0f, 0.9f, 0f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-165f));
                matrices.translate(0f, -0.9f, 0f);
            }
            default -> {
            }
        }
    }

    private static final class Eyes<T extends HostileEntity & MugenLook> extends EyesFeatureRenderer<T, MugenModel<T>> {
        private final int which;
        private final RenderLayer layer;

        Eyes(FeatureRendererContext<T, MugenModel<T>> context, int which, RenderLayer layer) {
            super(context);
            this.which = which;
            this.layer = layer;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (boss.eyes() == which) super.render(matrices, vertexConsumers, light, boss, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        }

        @Override
        public RenderLayer getEyesTexture() {
            return layer;
        }
    }

    /**
     * The two tails of his headband, knotted at the back of his head and streaming out behind it, rippling. Built of
     * wool like a block display, so they look like the rest of the world: black under the moon, red in the eclipse.
     */
    private static final class Headband<T extends HostileEntity & MugenLook> extends FeatureRenderer<T, MugenModel<T>> {
        private static final BlockState BLACK = Blocks.BLACK_WOOL.getDefaultState(), RED = Blocks.RED_WOOL.getDefaultState();
        private static final int SEGMENTS = 4;

        Headband(FeatureRendererContext<T, MugenModel<T>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, T boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            BlockState wool = boss.eyes() == MugenLook.EYES_RED ? RED : BLACK;
            float time = boss.age + tickDelta;
            // Streams out further the faster he goes
            float speed = MathHelper.clamp(limbDistance + (boss.afterimages() ? 0.8f : 0f), 0f, 1f);
            matrices.push();
            getContextModel().head.rotate(matrices);
            // The knot, at the back of his head (model space: y down, +z behind)
            box(matrices, buffers, wool, 0f, -0.31f, 0.27f, 0.14f, 0.1f, 0.07f, light);
            for (int side = -1; side <= 1; side += 2) {
                float x = side * 0.045f, y = -0.3f, z = 0.29f;
                for (int k = 0; k < SEGMENTS; k++) {
                    float wave = MathHelper.sin(time * 0.35f + k * 0.9f + side) * (0.035f + 0.02f * k);
                    float droop = (1f - speed) * 0.09f;
                    y += droop + wave;
                    z += 0.13f - droop * 0.4f;
                    x += side * 0.012f;
                    box(matrices, buffers, wool, x, y, z, 0.055f, 0.1f - k * 0.012f, 0.15f, light);
                }
            }
            matrices.pop();
        }

        /** A small box of {@code state} centred on (x, y, z), {@code w}x{@code h}x{@code d}. */
        private static void box(MatrixStack matrices, VertexConsumerProvider buffers, BlockState state, float x, float y, float z,
                                float w, float h, float d, int light) {
            matrices.push();
            matrices.translate(x - w / 2, y - h / 2, z - d / 2);
            matrices.scale(w, h, d);
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, buffers, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
