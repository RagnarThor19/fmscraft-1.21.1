package net.ragnar.fmscraft.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.boss.greed.GreedBubbleEntity;

public class GreedBubbleRenderer extends EntityRenderer<GreedBubbleEntity> {
    public GreedBubbleRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 0.4f;
        shadowOpacity = 0.3f;
    }

    @Override
    public void render(GreedBubbleEntity bubble, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        matrices.push();
        matrices.translate(0, GreedBubbleEntity.SIZE / 2, 0);
        BubbleCube.draw(matrices, buffers, light, GreedBubbleEntity.SIZE, bubble.age + tickDelta);
        matrices.pop();
        super.render(bubble, yaw, tickDelta, matrices, buffers, light);
    }

    @Override
    public Identifier getTexture(GreedBubbleEntity bubble) {
        return BubbleCube.TEXTURE;
    }
}
