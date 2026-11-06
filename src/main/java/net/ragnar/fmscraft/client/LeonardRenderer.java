package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.pride.LeonardEntity;
import net.ragnar.fmscraft.item.ModItems;

/**
 * LEONARD: Leo's skin on a player model, his lightning bolt in his hand, half as big again as an item is held. In phase 1
 * lightning glints on his shades (leonard_eyes.png, tools/textures/leonard_textures.py), and when he moves too fast to
 * see he leaves afterimages, see-through copies of himself in yellow where he just was. As CONEMAN (five blocks tall,
 * his scale) the traffic cone sits over his head: its own cube model (models/item/traffic_cone.json), drawn on his neck,
 * wider than his head and twice as tall, so none of his head shows.
 */
public class LeonardRenderer extends MobEntityRenderer<LeonardEntity, LeonardModel<LeonardEntity>> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/leonard/leonard.png");
    private static final RenderLayer SHADES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/leonard/leonard_eyes.png"));
    private static final int[] GHOSTS = {2, 4, 6, 8};
    private static final float[] GHOST_ALPHA = {0.5f, 0.36f, 0.24f, 0.13f};
    /** How big the bolt is in his hand. */
    private static final float BOLT_SCALE = 1.5f;

    public LeonardRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new LeonardModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        addFeature(new Shades(this));
        addFeature(new Bolt(this, ctx.getHeldItemRenderer()));
        addFeature(new Cone(this));
    }

    @Override
    public Identifier getTexture(LeonardEntity boss) {
        return TEXTURE;
    }

    @Override
    public void render(LeonardEntity boss, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (boss.isInvisible()) return;
        super.render(boss, yaw, tickDelta, matrices, buffers, light);
        if (boss.afterimages()) afterimages(boss, tickDelta, matrices, buffers, light);
    }

    /** See-through copies of him in electric yellow where he was a moment ago. */
    private void afterimages(LeonardEntity boss, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        Vec3d now = boss.getLerpedPos(tickDelta);
        float s = boss.getScale();
        var vertices = buffers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
        for (int i = 0; i < GHOSTS.length; i++) {
            Vec3d at = boss.trailPos(GHOSTS[i]);
            if (at == null || at.squaredDistanceTo(now) < 0.09) continue;
            Vec3d off = at.subtract(now);
            int alpha = (int) (GHOST_ALPHA[i] * 255);
            matrices.push();
            matrices.translate(off.x, off.y, off.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - boss.trailYaw(GHOSTS[i])));
            matrices.scale(s, s, s);
            matrices.scale(-1f, -1f, 1f);
            matrices.translate(0f, -1.501f, 0f);
            getModel().render(matrices, vertices, light, OverlayTexture.DEFAULT_UV, (alpha << 24) | 0xFFE680);
            matrices.pop();
        }
    }

    private static final class Shades extends EyesFeatureRenderer<LeonardEntity, LeonardModel<LeonardEntity>> {
        Shades(FeatureRendererContext<LeonardEntity, LeonardModel<LeonardEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LeonardEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            // A flicker, like lightning on glass
            if (boss.shadesLit() && !boss.coned() && (boss.age / 2) % 7 != 0) {
                super.render(matrices, vertexConsumers, light, boss, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
            }
        }

        @Override
        public RenderLayer getEyesTexture() {
            return SHADES;
        }
    }

    /** The bolt in his right hand, bigger than an item would be. */
    private static final class Bolt extends FeatureRenderer<LeonardEntity, LeonardModel<LeonardEntity>> {
        private final HeldItemRenderer items;

        Bolt(FeatureRendererContext<LeonardEntity, LeonardModel<LeonardEntity>> context, HeldItemRenderer items) {
            super(context);
            this.items = items;
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, LeonardEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            ItemStack stack = boss.getMainHandStack();
            if (stack.isEmpty()) return;
            matrices.push();
            getContextModel().setArmAngle(Arm.RIGHT, matrices);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
            matrices.translate(1f / 16f, 0.125f, -0.625f);
            matrices.scale(BOLT_SCALE, BOLT_SCALE, BOLT_SCALE);
            // Lightning glows: full bright whatever the light
            items.renderItem(boss, stack, ModelTransformationMode.THIRD_PERSON_RIGHT_HAND, false, matrices, buffers, 0xF000F0);
            matrices.pop();
        }
    }

    /**
     * CONEMAN's traffic cone, on his neck. In the head's own space a block is one unit and y points down: the cone's
     * model is drawn flipped upright, its rim just under the neck. At one unit per 16 pixels its skirt (11 wide) and
     * lower band (10 wide, up to 8.5 high) are wider and taller than his head (8x8), so all of it is inside.
     */
    private static final class Cone extends FeatureRenderer<LeonardEntity, LeonardModel<LeonardEntity>> {
        private final ItemStack cone = new ItemStack(ModItems.TRAFFIC_CONE);

        Cone(FeatureRendererContext<LeonardEntity, LeonardModel<LeonardEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, LeonardEntity boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (!boss.coned()) return;
            matrices.push();
            getContextModel().head.rotate(matrices);
            matrices.translate(0f, 0.02f, 0f);
            matrices.scale(1f, -1f, -1f);
            matrices.translate(0f, 0.5f, 0f);
            MinecraftClient.getInstance().getItemRenderer().renderItem(cone, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
                    matrices, buffers, boss.getWorld(), boss.getId());
            matrices.pop();
        }
    }
}
