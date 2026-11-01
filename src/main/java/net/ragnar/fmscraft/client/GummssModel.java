package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.boss.envy.GummssLook;

/**
 * GUMMSS (and his Mirror Images): a player with his golden staff in his right hand and his left free for the mind's
 * work. His arms follow what he's doing: both raised to cast, palms thrust out, a hand clawing back to pull, lifting
 * someone high and dashing them down, throwing, cutting with the staff, looking up into the dark with his arms spread,
 * falling with them out, landing in a crouch, pointing the staff, holding it over his head for the Evil Eye.
 */
public class GummssModel<T extends HostileEntity & GummssLook> extends PlayerEntityModel<T> {
    public GummssModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(T boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == GummssLook.POSE_LAND;
        rightArmPose = BipedEntityModel.ArmPose.ITEM;
        leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.setAngles(boss, limbAngle, limbDistance, age, headYaw, headPitch);
        float sway = MathHelper.cos(age * 0.6662f) * 0.12f;
        float tremble = MathHelper.sin(age * 2.1f) * 0.04f;
        switch (pose) {
            case GummssLook.POSE_CAST -> {
                // Staff high overhead in both hands
                rightArm.pitch = -MathHelper.PI + 0.25f + sway * 0.3f;
                rightArm.yaw = 0;
                rightArm.roll = 0.15f;
                leftArm.pitch = -MathHelper.PI + 0.45f - sway * 0.3f;
                leftArm.yaw = 0;
                leftArm.roll = -0.3f;
                head.pitch = -0.25f;
            }
            case GummssLook.POSE_PUSH -> {
                rightArm.pitch = -MathHelper.HALF_PI;
                rightArm.yaw = -0.25f;
                leftArm.pitch = -MathHelper.HALF_PI;
                leftArm.yaw = 0.25f;
                leftArm.roll = 0;
            }
            case GummssLook.POSE_PULL -> {
                // Hand out, fingers clawing, drawing back
                leftArm.pitch = -1.35f + sway * 0.5f;
                leftArm.yaw = 0.1f;
                leftArm.roll = tremble;
                rightArm.pitch = 0.35f;
            }
            case GummssLook.POSE_LIFT -> {
                leftArm.pitch = -2.6f + tremble;
                leftArm.yaw = 0.15f;
                leftArm.roll = -0.2f + tremble;
                rightArm.pitch = 0.2f;
                head.pitch = -0.35f;
            }
            case GummssLook.POSE_SLAM -> {
                leftArm.pitch = -0.35f;
                leftArm.roll = -0.25f;
                rightArm.pitch = -0.5f;
                head.pitch = 0.35f;
            }
            case GummssLook.POSE_THROW -> {
                leftArm.pitch = -1.9f;
                leftArm.yaw = -0.2f;
                rightArm.pitch = 0.4f;
            }
            case GummssLook.POSE_SLASH_A -> {
                rightArm.pitch = -1.4f;
                rightArm.yaw = 0.9f;
                rightArm.roll = 0;
                leftArm.pitch = 0.3f;
            }
            case GummssLook.POSE_SLASH_B -> {
                rightArm.pitch = -1.7f;
                rightArm.yaw = -0.6f;
                rightArm.roll = 0.4f;
                leftArm.pitch = -0.4f;
            }
            case GummssLook.POSE_SKY -> {
                // Looking up into the dark, arms spread, staff raised
                head.pitch = -0.95f;
                head.yaw = 0;
                rightArm.pitch = -2.6f;
                rightArm.roll = 0.6f;
                leftArm.pitch = -2.4f;
                leftArm.roll = -0.8f;
            }
            case GummssLook.POSE_FALL -> {
                // Falling calmly, arms out, looking at them; this is all his doing
                rightArm.pitch = -0.15f + sway * 0.3f;
                rightArm.yaw = 0;
                rightArm.roll = 1.15f;
                leftArm.pitch = -0.15f - sway * 0.3f;
                leftArm.yaw = 0;
                leftArm.roll = -1.15f;
                rightLeg.pitch = 0.1f;
                leftLeg.pitch = -0.05f;
                rightLeg.roll = 0.05f;
                leftLeg.roll = -0.05f;
                head.pitch = 0.3f;
            }
            case GummssLook.POSE_LAND -> {
                // Landed in a crouch, one hand to the floor, the staff held out
                leftArm.pitch = -0.1f;
                leftArm.roll = -0.35f;
                rightArm.pitch = -0.9f;
                rightArm.roll = 0.8f;
                head.pitch = 0.5f;
            }
            case GummssLook.POSE_POINT -> {
                rightArm.pitch = -MathHelper.HALF_PI + head.pitch;
                rightArm.yaw = head.yaw;
                rightArm.roll = 0;
                leftArm.pitch = 0.2f;
            }
            case GummssLook.POSE_STARE -> {
                // The staff held level over his head in both hands
                rightArm.pitch = -2.85f;
                rightArm.yaw = -0.35f;
                rightArm.roll = 0;
                leftArm.pitch = -2.85f;
                leftArm.yaw = 0.35f;
                leftArm.roll = 0;
                head.pitch = -0.15f;
            }
            case GummssLook.POSE_CATCH -> {
                leftArm.pitch = -2.0f + sway;
                leftArm.yaw = -0.3f;
                leftArm.roll = -0.5f;
            }
            case GummssLook.POSE_STUNNED -> {
                head.roll = MathHelper.sin(age * 0.3f) * 0.3f;
                head.pitch = 0.4f;
                rightArm.pitch = 0.2f;
                leftArm.pitch = 0.4f;
                leftArm.roll = -0.4f;
            }
            default -> {
                // His free hand never quite still
                leftArm.pitch += -0.2f + sway * 0.4f;
                leftArm.roll -= 0.1f;
            }
        }
        hat.copyTransform(head);
        rightSleeve.copyTransform(rightArm);
        leftSleeve.copyTransform(leftArm);
        rightPants.copyTransform(rightLeg);
        leftPants.copyTransform(leftLeg);
        jacket.copyTransform(body);
    }
}
