package net.ragnar.fmscraft.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PiglinEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

/**
 * The Gorged Brute: the vanilla piglin brute exactly, model, texture and all. His size (and how much he's swollen)
 * comes from his scale attribute.
 */
public class GorgedBruteRenderer extends PiglinEntityRenderer {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/piglin/piglin_brute.png");

    public GorgedBruteRenderer(EntityRendererFactory.Context context) {
        super(context, EntityModelLayers.PIGLIN_BRUTE, EntityModelLayers.PIGLIN_BRUTE_INNER_ARMOR, EntityModelLayers.PIGLIN_BRUTE_OUTER_ARMOR, false);
    }

    @Override
    public Identifier getTexture(MobEntity entity) {
        return TEXTURE;
    }
}
