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
import net.ragnar.fmscraft.entity.GreedStatueEntity;

/**
 * The Statue of Greed: a robed figure of pale stone, once gilded all over, the gold leaf now flaking away except on
 * its hands, its purse and the thin crown round its brow. The neck is a little too long, the head hangs tilted, and
 * the arms reach almost to the floor. A blank face with two pinprick gold eyes and a smile cut too wide.
 * No walking animation: it's a statue. The head is the only part that turns.
 *
 * UV offsets here must match the box list in tools/textures/greed_textures.py, which paints the texture.
 */
public class GreedStatueModel extends EntityModel<GreedStatueEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(FmsCraft.MOD_ID, "greed_statue"), "main");
    /** How far the head hangs over to one side. */
    private static final float TILT = 0.32f;

    private final ModelPart root;
    private final ModelPart head;

    public GreedStatueModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("body").getChild("head");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData body = root.addChild("body", ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-5, -12, -3, 10, 12, 6)          // robe, down to the floor
                        .uv(32, 0).cuboid(-4, -21, -2.5f, 8, 9, 5)        // chest
                        .uv(0, 18).cuboid(-5, -22, -3, 10, 2, 6)          // hunched shoulders
                        .uv(32, 14).cuboid(-1, -26, -1, 2, 4, 2)          // neck, too long
                        .uv(44, 30).cuboid(2.5f, -13, -4, 3, 3, 2),       // the purse at its belt
                ModelTransform.pivot(0, 24, 0));
        body.addChild("head", ModelPartBuilder.create()
                        .uv(0, 26).cuboid(-3.5f, -7, -3.5f, 7, 7, 7)
                        .uv(0, 40).cuboid(-4, -6, -4, 8, 1, 8),           // tarnished crown
                ModelTransform.of(0, -26, 0, 0, 0, TILT));
        // One arm reaching for you, the other hanging, both far too long, ending in gilded hands
        ModelPartData reaching = body.addChild("right_arm", ModelPartBuilder.create()
                        .uv(40, 14).cuboid(-1, 0, -1, 2, 13, 2),
                ModelTransform.of(-6, -21, 0, -0.55f, 0, 0.06f));
        ModelPartData hanging = body.addChild("left_arm", ModelPartBuilder.create()
                        .uv(48, 14).cuboid(-1, 0, -1, 2, 13, 2),
                ModelTransform.of(6, -21, 0, -0.08f, 0, -0.1f));
        for (ModelPartData arm : new ModelPartData[]{reaching, hanging}) {
            arm.addChild("hand", ModelPartBuilder.create()
                            .uv(28, 26).cuboid(-1.5f, 0, -1.5f, 3, 4, 3)
                            .uv(40, 30).cuboid(-1.5f, 4, -1.5f, 1, 3, 1)  // long fingers
                            .uv(40, 30).cuboid(0.5f, 4, -1.5f, 1, 3, 1)
                            .uv(40, 30).cuboid(-0.5f, 4, 0.5f, 1, 3, 1),
                    ModelTransform.pivot(0, 13, 0));
        }
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public void setAngles(GreedStatueEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        head.yaw = headYaw * ((float) Math.PI / 180f);
        head.pitch = headPitch * ((float) Math.PI / 180f);
        // Now and then the tilt jerks a little further over, then back
        head.roll = TILT + (Math.floorMod(entity.shownAge * 31 + entity.getId() * 7, 23) == 0 ? 0.25f : 0f);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        root.render(matrices, vertices, light, overlay, color);
    }
}
