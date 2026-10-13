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
import net.ragnar.fmscraft.entity.EnvyStatueEntity;

/**
 * Invidia, after Giotto's fresco in the Scrovegni Chapel (c. 1306): huge ears, one hand clawing upward, a purse
 * clutched in the other, a serpent coming out of her mouth and turning back on her own eyes, flames at her feet.
 * Carved in stone on a plinth, which is what grinds along the maze floor. No moving parts: it's a statue.
 *
 * UV offsets here must match the box list in tools/textures/envy_textures.py, which paints the texture.
 */
public class EnvyStatueModel extends EntityModel<EnvyStatueEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(FmsCraft.MOD_ID, "envy_statue"), "main");

    private final ModelPart root;

    public EnvyStatueModel(ModelPart root) {
        this.root = root;
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-22, -6, -22, 44, 6, 44)        // plinth
                        .uv(0, 50).cuboid(-14, -26, -10, 28, 20, 20)     // robe
                        .uv(96, 50).cuboid(-12, -42, -8, 24, 16, 16)     // chest
                        .uv(176, 0).cuboid(-7, -56, -7, 14, 14, 14)      // head
                        .uv(176, 28).cuboid(-9, -54, -2, 2, 8, 5)        // ears, far too big
                        .uv(190, 28).cuboid(7, -54, -2, 2, 8, 5)
                        .uv(176, 42).cuboid(12, -40, -3, 6, 14, 6)       // arm, hanging
                        .uv(232, 0).cuboid(11, -29, -9, 7, 7, 5)         // the purse it clutches
                        .uv(232, 12).cuboid(-20, -14, -20, 6, 8, 6)      // flames at the corners
                        .uv(232, 12).cuboid(14, -14, -20, 6, 8, 6)
                        .uv(232, 12).cuboid(-20, -14, 14, 6, 8, 6)
                        .uv(232, 12).cuboid(14, -14, 14, 6, 8, 6)
                        .uv(176, 66).cuboid(-1.5f, -47, -13, 3, 3, 6)    // serpent out of the mouth...
                        .uv(194, 66).cuboid(-1.5f, -50, -13, 3, 3, 3)    // ...rising...
                        .uv(206, 66).cuboid(-2, -53, -12, 4, 3, 5),      // ...and biting back at her eyes
                ModelTransform.pivot(0, 24, 0));
        body.addChild("raised_arm", ModelPartBuilder.create()
                        .uv(200, 42).cuboid(-3, -2, -3, 6, 16, 6),
                ModelTransform.of(-15, -40, 0, -2.6f, 0f, 0.2f));
        return TexturedModelData.of(data, 256, 128);
    }

    @Override
    public void setAngles(EnvyStatueEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
