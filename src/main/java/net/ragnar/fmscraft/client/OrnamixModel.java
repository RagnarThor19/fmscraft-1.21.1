package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.boss.wrath.OrnamixEntity;

/**
 * ORNAMIX is a player (wide arms) with a sword in his right hand, whose arms follow what he's doing: drawing back to
 * stab, the sword raised overhead, casting, pointing a burning hand, drawing a bow, arms flung out to spin, a fist
 * pulled back for a haymaker, kneeling where he landed, roaring.
 */
public class OrnamixModel extends PlayerEntityModel<OrnamixEntity> {
    public OrnamixModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(OrnamixEntity boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == OrnamixEntity.POSE_CROUCH || pose == OrnamixEntity.POSE_KNEEL;
        rightArmPose = pose == OrnamixEntity.POSE_BOW ? BipedEntityModel.ArmPose.BOW_AND_ARROW : BipedEntityModel.ArmPose.ITEM;
        leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.setAngles(boss, limbAngle, limbDistance, age, headYaw, headPitch);
        float sway = MathHelper.cos(age * 0.6662f) * 0.15f;
        switch (pose) {
            case OrnamixEntity.POSE_DRAW -> {
                // Sword drawn back at the hip, point forward, ready to lunge
                rightArm.pitch = 0.9f;
                rightArm.yaw = 0.3f;
                rightArm.roll = 0.15f;
                leftArm.pitch = -0.7f;
                leftArm.roll = -0.2f;
            }
            case OrnamixEntity.POSE_STAB -> {
                rightArm.pitch = -MathHelper.HALF_PI - 0.1f;
                rightArm.yaw = -0.1f;
                rightArm.roll = 0;
                leftArm.pitch = 0.6f;
            }
            case OrnamixEntity.POSE_RAISE -> {
                // Sword up over his head, gathering fire
                rightArm.pitch = -MathHelper.PI + 0.3f + sway * 0.3f;
                rightArm.yaw = 0;
                rightArm.roll = 0.1f;
                leftArm.pitch = -0.7f;
                leftArm.roll = -0.3f;
            }
            case OrnamixEntity.POSE_SLAM -> {
                rightArm.pitch = -0.35f;
                rightArm.yaw = 0;
                rightArm.roll = 0;
                leftArm.pitch = -0.2f;
                head.pitch = 0.35f;
            }
            case OrnamixEntity.POSE_CAST -> {
                // Both arms up and out, like an evoker casting
                rightArm.pitch = -2.4f + sway;
                leftArm.pitch = -2.4f - sway;
                rightArm.roll = 0.5f;
                leftArm.roll = -0.5f;
                rightArm.yaw = 0;
                leftArm.yaw = 0;
            }
            case OrnamixEntity.POSE_AIM -> {
                // Burning left hand pointed where he's looking, sword held low
                leftArm.pitch = -MathHelper.HALF_PI + head.pitch;
                leftArm.yaw = head.yaw + 0.1f;
                leftArm.roll = 0;
                rightArm.pitch = 0.3f;
                rightArm.roll = 0.2f;
            }
            case OrnamixEntity.POSE_CROUCH -> {
                rightArm.pitch = 1.0f;
                rightArm.yaw = 0.2f;
                leftArm.pitch = -0.8f;
            }
            case OrnamixEntity.POSE_SPIN_READY, OrnamixEntity.POSE_SPIN -> {
                // Arms flung out to the sides, the sword held level
                rightArm.pitch = -0.2f;
                rightArm.yaw = 0;
                rightArm.roll = MathHelper.HALF_PI - 0.1f;
                leftArm.pitch = -0.2f;
                leftArm.yaw = 0;
                leftArm.roll = -MathHelper.HALF_PI + 0.1f;
            }
            case OrnamixEntity.POSE_PUNCH_READY -> {
                leftArm.pitch = 1.0f;
                leftArm.roll = -0.2f;
                rightArm.pitch = -0.3f;
            }
            case OrnamixEntity.POSE_PUNCH -> {
                leftArm.pitch = -MathHelper.HALF_PI;
                leftArm.yaw = 0.15f;
                leftArm.roll = 0;
                rightArm.pitch = 0.5f;
            }
            case OrnamixEntity.POSE_STUNNED -> {
                head.roll = MathHelper.sin(age * 0.3f) * 0.35f;
                head.pitch = 0.4f;
                rightArm.pitch = 0.2f;
                leftArm.pitch = 0.2f;
                rightArm.roll = 0.5f;
                leftArm.roll = -0.5f;
            }
            case OrnamixEntity.POSE_STUCK -> {
                // Both hands on the sword stuck in the wall, wrenching at it
                float tug = MathHelper.sin(age * 1.1f) * 0.12f;
                rightArm.pitch = -MathHelper.HALF_PI + tug;
                rightArm.yaw = 0;
                leftArm.pitch = -MathHelper.HALF_PI + tug;
                leftArm.yaw = 0.35f;
                head.pitch = 0.2f;
            }
            case OrnamixEntity.POSE_KNEEL -> {
                // Landed: crouched low, head down, the sword planted in front of him
                head.pitch = 0.6f;
                rightArm.pitch = -0.7f;
                rightArm.yaw = 0;
                leftArm.pitch = 0.1f;
                leftArm.roll = -0.3f;
            }
            case OrnamixEntity.POSE_ROAR -> {
                head.pitch = -0.6f;
                rightArm.pitch = -0.4f + sway * 0.4f;
                rightArm.roll = 0.9f;
                leftArm.pitch = -0.4f - sway * 0.4f;
                leftArm.roll = -0.9f;
            }
            default -> {
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
