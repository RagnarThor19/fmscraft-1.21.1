package net.ragnar.fmscraft.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.boss.envy.GummssLook;

import java.util.UUID;

/**
 * GUMMSS: his skin on a player model, holding his golden staff. His eyes glow green while he casts. Unseen (Vanish)
 * nothing of him is drawn, not even the staff. His Mirror Images use this too, with no shadow: the only way to tell.
 * <p>
 * Wearing one of you (his Disguise) he's drawn exactly as a player is: their skin (and arms, slim or not), their armour,
 * what's in their hands, their cape and their name over his head, at a player's size (vanilla draws players at 15/16).
 * Meanwhile they're drawn as him ({@link GummssGuiseRenderer}). Shut in the cups, neither of them is drawn at all.
 */
public class GummssRenderer<T extends HostileEntity & GummssLook> extends BipedEntityRenderer<T, GummssModel<T>> {
    private static final Identifier TEXTURE = Identifier.of(FmsCraft.MOD_ID, "textures/entity/gummss/gummss.png");
    private static final RenderLayer EYES = RenderLayer.getEyes(Identifier.of(FmsCraft.MOD_ID, "textures/entity/gummss/gummss_eyes.png"));
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final GummssModel<T> wide, slim;

    public GummssRenderer(EntityRendererFactory.Context ctx, float shadow) {
        super(ctx, new GummssModel<>(ctx.getPart(EntityModelLayers.PLAYER)), shadow);
        wide = model;
        slim = new GummssModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        // He wears no armour of his own: this only ever shows a disguised player's
        addFeature(new ArmorFeatureRenderer<>(this, new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addFeature(new CastingEyes<>(this));
        addFeature(new GuiseCape<>(this));
    }

    @Override
    public Identifier getTexture(T boss) {
        SkinTextures skin = worn(boss);
        return skin != null ? skin.texture() : TEXTURE;
    }

    @Override
    public void render(T boss, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (boss.isInvisible() || boss.guiseStage() == GummssLook.GUISE_HIDDEN) return;
        SkinTextures skin = worn(boss);
        if (skin == null) {
            model = wide;
            wide.guise = false;
            wide.setVisible(true);
            super.render(boss, yaw, tickDelta, matrices, buffers, light);
            return;
        }
        // Wearing one of you: everything of theirs, for as long as he's drawn
        PlayerEntity player = boss.getWorld().getPlayerByUuid(boss.guisePlayer());
        GummssModel<T> m = skin.model() == SkinTextures.Model.SLIM ? slim : wide;
        model = m;
        m.guise = true;
        m.setVisible(true);
        ItemStack[] own = new ItemStack[SLOTS.length];
        boolean leftHanded = boss instanceof MobEntity mob && mob.isLeftHanded();
        if (player != null) {
            m.hat.visible = player.isPartVisible(PlayerModelPart.HAT);
            m.jacket.visible = player.isPartVisible(PlayerModelPart.JACKET);
            m.leftPants.visible = player.isPartVisible(PlayerModelPart.LEFT_PANTS_LEG);
            m.rightPants.visible = player.isPartVisible(PlayerModelPart.RIGHT_PANTS_LEG);
            m.leftSleeve.visible = player.isPartVisible(PlayerModelPart.LEFT_SLEEVE);
            m.rightSleeve.visible = player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE);
            for (int i = 0; i < SLOTS.length; i++) {
                own[i] = boss.getEquippedStack(SLOTS[i]);
                boss.equipStack(SLOTS[i], player.getEquippedStack(SLOTS[i]));
            }
            if (boss instanceof MobEntity mob) mob.setLeftHanded(player.getMainArm() == Arm.LEFT);
        }
        BipedEntityModel.ArmPose main = armPose(boss.getMainHandStack()), off = armPose(boss.getOffHandStack());
        boolean right = boss.getMainArm() == Arm.RIGHT;
        m.guiseRight = right ? main : off;
        m.guiseLeft = right ? off : main;
        try {
            super.render(boss, yaw, tickDelta, matrices, buffers, light);
        } finally {
            if (player != null) {
                for (int i = 0; i < SLOTS.length; i++) boss.equipStack(SLOTS[i], own[i]);
                if (boss instanceof MobEntity mob) mob.setLeftHanded(leftHanded);
            }
            m.guise = false;
        }
        // Their name over his head, as over any player's
        if (MinecraftClient.isHudEnabled() && boss != dispatcher.camera.getFocusedEntity()) {
            Text name = player != null ? player.getDisplayName() : guiseName(boss.guisePlayer());
            if (name != null) renderLabelIfPresent(boss, name, matrices, buffers, light, tickDelta);
        }
    }

    @Override
    protected void scale(T boss, MatrixStack matrices, float amount) {
        // Players are drawn a little smaller than their model; so is he, wearing one of them
        if (worn(boss) != null) matrices.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    protected void setupTransforms(T boss, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale) {
        super.setupTransforms(boss, matrices, animationProgress, bodyYaw, tickDelta, scale);
        float roll = boss.flipRoll(tickDelta);
        if (roll != 0f) {
            // Rolled over about his middle, like anyone the Tome of Upside Down takes
            float middle = boss.getHeight() / 2f / scale;
            matrices.translate(0, middle, 0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(roll));
            matrices.translate(0, -middle, 0);
        }
    }

    /** The skin of whoever he's wearing right now, or null if he's himself. */
    private static SkinTextures worn(HostileEntity boss) {
        if (!(boss instanceof GummssLook look) || look.guiseStage() != GummssLook.GUISE_WORN || look.guisePlayer() == null) return null;
        PlayerEntity player = boss.getWorld().getPlayerByUuid(look.guisePlayer());
        if (player instanceof AbstractClientPlayerEntity client) return client.getSkinTextures();
        PlayerListEntry entry = entry(look.guisePlayer());
        return entry != null ? entry.getSkinTextures() : null;
    }

    private static PlayerListEntry entry(UUID id) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        return handler == null ? null : handler.getPlayerListEntry(id);
    }

    private static Text guiseName(UUID id) {
        PlayerListEntry entry = entry(id);
        return entry == null ? null : entry.getDisplayName() != null ? entry.getDisplayName() : Text.literal(entry.getProfile().getName());
    }

    /** How a player holds what's in a hand (as vanilla poses them, short of using it). */
    private static BipedEntityModel.ArmPose armPose(ItemStack stack) {
        if (stack.isEmpty()) return BipedEntityModel.ArmPose.EMPTY;
        if (stack.isOf(Items.CROSSBOW)) {
            var charged = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
            if (charged != null && !charged.isEmpty()) return BipedEntityModel.ArmPose.CROSSBOW_HOLD;
        }
        return BipedEntityModel.ArmPose.ITEM;
    }

    private static boolean casting(int pose) {
        return pose == GummssLook.POSE_CAST || pose == GummssLook.POSE_SKY || pose == GummssLook.POSE_STARE
                || pose == GummssLook.POSE_FALL || pose == GummssLook.POSE_LAND || pose == GummssLook.POSE_LIFT;
    }

    private static final class CastingEyes<T extends HostileEntity & GummssLook> extends EyesFeatureRenderer<T, GummssModel<T>> {
        CastingEyes(FeatureRendererContext<T, GummssModel<T>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            if (casting(boss.poseState()) && worn(boss) == null) {
                super.render(matrices, vertexConsumers, light, boss, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
            }
        }

        @Override
        public RenderLayer getEyesTexture() {
            return EYES;
        }
    }

    /**
     * The cape of whoever he's wearing, swinging out behind him as he moves, as vanilla swings a player's (from how
     * far he's moved this tick, where vanilla follows a trailing point; near enough to look the same).
     */
    private static final class GuiseCape<T extends HostileEntity & GummssLook> extends FeatureRenderer<T, GummssModel<T>> {
        GuiseCape(FeatureRendererContext<T, GummssModel<T>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, T boss,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            SkinTextures skin = worn(boss);
            if (skin == null || skin.capeTexture() == null) return;
            PlayerEntity player = boss.getWorld().getPlayerByUuid(boss.guisePlayer());
            if (player != null && !player.isPartVisible(PlayerModelPart.CAPE)) return;
            if (boss.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
            double dx = boss.getX() - boss.prevX, dy = boss.getY() - boss.prevY, dz = boss.getZ() - boss.prevZ;
            float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, boss.prevBodyYaw, boss.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
            double sin = MathHelper.sin(bodyYaw), cos = -MathHelper.cos(bodyYaw);
            // A trailing point lags about three ticks' movement behind
            float lift = MathHelper.clamp((float) -dy * 30f, -6f, 32f);
            float back = MathHelper.clamp((float) (-(dx * sin + dz * cos)) * 300f, 0f, 150f);
            float side = MathHelper.clamp((float) (-(dx * cos - dz * sin)) * 300f, -20f, 20f);
            lift += MathHelper.sin(MathHelper.lerp(tickDelta, boss.prevHorizontalSpeed, boss.horizontalSpeed) * 6f) * 32f * limbDistance;
            matrices.push();
            matrices.translate(0f, 0f, 0.125f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6f + back / 2f + lift));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side / 2f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - side / 2f));
            VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntitySolid(skin.capeTexture()));
            getContextModel().renderCape(matrices, vc, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
