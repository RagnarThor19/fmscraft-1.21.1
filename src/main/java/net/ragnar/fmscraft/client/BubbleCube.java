package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.FmsCraft;

/**
 * RAGGIMEL's bubbles are drawn as a single see-through cube, wobbling like jelly and turning slowly: blocky, the way
 * a slime's outer layer is. Texture from tools/textures/raggimel_textures.py.
 */
public final class BubbleCube {
    public static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/raggimel/bubble.png");
    private static ModelPart cube;

    private BubbleCube() {
    }

    private static ModelPart cube() {
        if (cube == null) {
            ModelData data = new ModelData();
            data.getRoot().addChild("cube", ModelPartBuilder.create().uv(0, 0).cuboid(-8, -8, -8, 16, 16, 16), ModelTransform.NONE);
            cube = TexturedModelData.of(data, 64, 32).createModel();
        }
        return cube;
    }

    /** A bubble {@code size} blocks across, centered on the current origin. */
    public static void draw(MatrixStack matrices, VertexConsumerProvider buffers, int light, float size, float age) {
        matrices.push();
        float wobble = MathHelper.sin(age * 0.25f) * 0.04f;
        matrices.scale(size * (1 + wobble), size * (1 - wobble), size * (1 + wobble));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 1.5f));
        cube().render(matrices, buffers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        matrices.pop();
    }
}
