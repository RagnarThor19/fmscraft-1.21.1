package net.ragnar.fmscraft.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.WardenFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.WardenEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.entity.MotherWardenEntity;

/**
 * The Mother Warden: the vanilla warden's model and glowing layers exactly, with the red textures from
 * tools/textures/wrath_textures.py. Her size comes from her scale attribute. Asleep she slumps over to one side, and
 * nods while drifting off, the same as anything put to sleep by the magic mod's sleep darts.
 */
public class MotherWardenRenderer extends MobEntityRenderer<MotherWardenEntity, WardenEntityModel<MotherWardenEntity>> {
    private static final Identifier TEXTURE = texture("mother_warden");
    private static final Identifier BIOLUMINESCENT_LAYER = texture("mother_warden_bioluminescent_layer");
    private static final Identifier HEART = texture("mother_warden_heart");
    private static final Identifier PULSATING_SPOTS_1 = texture("mother_warden_pulsating_spots_1");
    private static final Identifier PULSATING_SPOTS_2 = texture("mother_warden_pulsating_spots_2");

    private static Identifier texture(String name) {
        return Identifier.of(FmsCraft.MOD_ID, "textures/entity/mother_warden/" + name + ".png");
    }

    public MotherWardenRenderer(EntityRendererFactory.Context context) {
        super(context, new WardenEntityModel<>(context.getPart(EntityModelLayers.WARDEN)), 0.9f);
        addFeature(new WardenFeatureRenderer<>(this, BIOLUMINESCENT_LAYER, (warden, tickDelta, progress) -> 1.0f, WardenEntityModel::getHeadAndLimbs));
        addFeature(new WardenFeatureRenderer<>(this, PULSATING_SPOTS_1,
                (warden, tickDelta, progress) -> Math.max(0.0f, MathHelper.cos(progress * 0.045f) * 0.25f), WardenEntityModel::getBodyHeadAndLimbs));
        addFeature(new WardenFeatureRenderer<>(this, PULSATING_SPOTS_2,
                (warden, tickDelta, progress) -> Math.max(0.0f, MathHelper.cos(progress * 0.045f + (float) Math.PI) * 0.25f),
                WardenEntityModel::getBodyHeadAndLimbs));
        addFeature(new WardenFeatureRenderer<>(this, TEXTURE, (warden, tickDelta, progress) -> warden.getTendrilPitch(tickDelta), WardenEntityModel::getTendrils));
        addFeature(new WardenFeatureRenderer<>(this, HEART, (warden, tickDelta, progress) -> warden.getHeartPitch(tickDelta), WardenEntityModel::getBody));
    }

    @Override
    public Identifier getTexture(MotherWardenEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupTransforms(MotherWardenEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta, scale);
        if (!entity.isDozing() || entity.deathTime > 0) return;
        // As the sleep darts do it (SleepClient.slump in the magic mod): here the model's front faces -Z and up is +Y
        float t = entity.phaseAge() + tickDelta;
        float side = (entity.getId() & 1) == 0 ? 1f : -1f;
        float forward, sideways;
        if (entity.sleepPhase() == MotherWardenEntity.DROWSY) {
            // Nodding off: slow forward dips, deeper as it goes, and a little sway
            float k = Math.min(1f, t / MotherWardenEntity.DROWSY_TICKS);
            float nod = (float) Math.pow(Math.max(0f, MathHelper.sin(t * 0.28f)), 2);
            forward = nod * 9f * k;
            sideways = side * 3f * k * MathHelper.sin(t * 0.14f);
        } else {
            // Slumped over to one side, breathing slowly
            float k = Math.min(1f, t / 8f);
            forward = (7f + 1.5f * MathHelper.sin(t * 0.12f)) * k;
            sideways = side * 11f * k;
        }
        matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(forward));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sideways));
    }
}
