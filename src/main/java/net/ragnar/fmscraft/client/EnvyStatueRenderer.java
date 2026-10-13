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
import net.ragnar.fmscraft.entity.EnvyStatueEntity;

/** Renders the statue, then its eyes, the serpent's eyes and the copper flames again as a glowing layer. */
public class EnvyStatueRenderer extends EntityRenderer<EnvyStatueEntity> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/envy_statue.png");
    private static final Identifier EYES = Identifier.of(FmsCraft.MOD_ID, "textures/entity/envy_statue_eyes.png");
    private static final int FULL_BRIGHT = 0xF00000;

    private final EnvyStatueModel model;

    public EnvyStatueRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.model = new EnvyStatueModel(ctx.getPart(EnvyStatueModel.LAYER));
        this.shadowRadius = 1.6f;
        this.shadowOpacity = 1.0f;
    }

    @Override
    public void render(EnvyStatueEntity statue, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        matrices.push();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, statue.prevYaw, statue.getYaw());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));
        matrices.scale(-1f, -1f, 1f);
        matrices.translate(0f, -1.501f, 0f);
        model.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        model.render(matrices, consumers.getBuffer(RenderLayer.getEyes(EYES)), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        matrices.pop();
        super.render(statue, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(EnvyStatueEntity statue) {
        return TEXTURE;
    }
}
