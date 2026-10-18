package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.entity.LustStatueEntity;

/**
 * The Statue of Lust, carved in tuff: a woman in a long draped gown, veiled, her hair falling down her back, a crown
 * of roses on her brow and a girdle of them at her waist. Her right hand is pressed to her breast, her left hangs
 * gripping her skirt, a sleeve trailing from it; one bare foot shows under the hem. Her mouth is open in a scream
 * and her eyes are shut: two dark slits. 48 pixels tall (3 blocks), but no wider than a player.
 * Nothing moves. The only change is her eyes opening, the slits turning pale grey: a texture (LustStatueRenderer).
 *
 * UV offsets here must match the box list in tools/textures/lust_textures.py, which paints the texture.
 */
public class LustStatueModel extends EntityModel<LustStatueEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(FmsCraft.MOD_ID, "lust_statue"), "main");

    private final ModelPart root;

    public LustStatueModel(ModelPart root) {
        this.root = root;
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body", ModelPartBuilder.create()
                        .uv(60, 0).cuboid(-5.5f, -3, -4, 11, 3, 8)        // hem, pooling on the plinth
                        .uv(0, 15).cuboid(-5, -22, -3.5f, 10, 19, 7)      // long skirt
                        .uv(98, 0).cuboid(-4, -26, -2.5f, 8, 4, 5)        // girdle of roses
                        .uv(34, 15).cuboid(-4, -37, -2.5f, 8, 11, 5)      // bodice
                        .uv(114, 15).cuboid(-1, -40, -1, 2, 3, 2)         // neck
                        .uv(60, 15).cuboid(-3.5f, -47, 2.5f, 7, 26, 2)    // veil and hair, down her back
                        .uv(114, 22).cuboid(-3, -1, -5, 2, 1, 1),         // a bare foot under the hem
                ModelTransform.pivot(0, 24, 0));
        body.addChild("head", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-3.5f, -8, -3.5f, 7, 8, 7)
                        .uv(28, 0).cuboid(-4, -7, -4, 8, 1, 8),           // crown of roses
                ModelTransform.pivot(0, -40, 0));
        // Right hand pressed to her breast: the upper arm hangs, the forearm comes up and across
        ModelPartData upper = body.addChild("right_arm", ModelPartBuilder.create()
                        .uv(86, 15).cuboid(-1, 0, -1, 2, 8, 2),
                ModelTransform.of(-5, -36.5f, 0, 0, 0, 0.1f));
        upper.addChild("right_forearm", ModelPartBuilder.create()
                        .uv(86, 15).cuboid(-1, -8, -1, 2, 8, 2)
                        .uv(102, 22).cuboid(-1.5f, -10, -1, 3, 2, 2),     // hand
                ModelTransform.of(0, 8, 0, 0.6f, 0, 0.8f));
        // Left arm hanging, gripping a fistful of skirt, a long sleeve trailing from it
        body.addChild("left_arm", ModelPartBuilder.create()
                        .uv(78, 15).cuboid(-1, 0, -1, 2, 14, 2)
                        .uv(102, 15).cuboid(-1.5f, 14, -1.5f, 3, 3, 3)    // hand
                        .uv(94, 15).cuboid(1, 1, -1.5f, 1, 12, 3),        // sleeve
                ModelTransform.of(5, -36.5f, 0, -0.1f, 0, -0.08f));
        return TexturedModelData.of(data, 128, 64);
    }

    @Override
    public void setAngles(LustStatueEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
