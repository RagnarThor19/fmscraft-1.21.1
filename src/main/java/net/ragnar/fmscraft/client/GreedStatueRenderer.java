package net.ragnar.fmscraft.client;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.entity.GreedStatueEntity;

/**
 * Draws the statue in stop motion: its pose (where it stands, which way it and its head face) is only caught up
 * every {@link #FRAME} ticks and held in between, so it never visibly glides. It's just a little closer each time
 * you look. Its eyes glow faintly on a second layer.
 */
public class GreedStatueRenderer extends EntityRenderer<GreedStatueEntity> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/greed_statue.png");
    private static final Identifier EYES = Identifier.of(FmsCraft.MOD_ID, "textures/entity/greed_statue_eyes.png");
    public static final int FRAME = 16;
    private static final float MAX_HEAD_TURN = 80f;

    private final GreedStatueModel model;

    public GreedStatueRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.model = new GreedStatueModel(ctx.getPart(GreedStatueModel.LAYER));
        // No shadow: it would slide along smoothly and give the statue away
        this.shadowRadius = 0f;
    }

    @Override
    public void render(GreedStatueEntity statue, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        if (statue.age - statue.shownAge >= FRAME || statue.age < statue.shownAge) {
            statue.shownAge = statue.age;
            statue.shownX = statue.getX();
            statue.shownY = statue.getY();
            statue.shownZ = statue.getZ();
            statue.shownBodyYaw = statue.bodyYaw;
            statue.shownHeadYaw = statue.headYaw;
        }
        matrices.push();
        matrices.translate(statue.shownX - MathHelper.lerp(tickDelta, statue.prevX, statue.getX()),
                statue.shownY - MathHelper.lerp(tickDelta, statue.prevY, statue.getY()),
                statue.shownZ - MathHelper.lerp(tickDelta, statue.prevZ, statue.getZ()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - statue.shownBodyYaw));
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);
        float headTurn = MathHelper.clamp(MathHelper.wrapDegrees(statue.shownHeadYaw - statue.shownBodyYaw), -MAX_HEAD_TURN, MAX_HEAD_TURN);
        model.setAngles(statue, 0, 0, statue.age, headTurn, 0);
        model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        model.render(matrices, consumers.getBuffer(RenderLayer.getEyes(EYES)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        matrices.pop();
        super.render(statue, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(GreedStatueEntity statue) {
        return TEXTURE;
    }
}
