package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.boss.greed.RaggimelEntity;

/** RAGGIMEL is a player (wide arms) whose arms follow what he's doing: casting, aiming, crouching to charge, slamming. */
public class RaggimelModel extends PlayerEntityModel<RaggimelEntity> {
    public RaggimelModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(RaggimelEntity boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == RaggimelEntity.POSE_CROUCH;
        super.setAngles(boss, limbAngle, limbDistance, age, headYaw, headPitch);
        float sway = MathHelper.cos(age * 0.6662f) * 0.15f;
        switch (pose) {
            case RaggimelEntity.POSE_CAST -> {
                // Both arms up and out, like an evoker casting
                rightArm.pitch = -2.4f + sway;
                leftArm.pitch = -2.4f - sway;
                rightArm.roll = 0.5f;
                leftArm.roll = -0.5f;
                rightArm.yaw = 0;
                leftArm.yaw = 0;
            }
            case RaggimelEntity.POSE_AIM -> {
                // Staff arm pointed where he's looking
                rightArm.pitch = -MathHelper.HALF_PI + head.pitch;
                rightArm.yaw = head.yaw - 0.1f;
                rightArm.roll = 0;
                leftArm.pitch = -0.4f;
                leftArm.roll = -0.2f;
            }
            case RaggimelEntity.POSE_SLAM -> {
                rightArm.pitch = -MathHelper.PI + 0.2f;
                leftArm.pitch = -MathHelper.PI + 0.2f;
                rightArm.roll = 0.15f;
                leftArm.roll = -0.15f;
            }
            case RaggimelEntity.POSE_STUNNED -> {
                head.roll = MathHelper.sin(age * 0.3f) * 0.35f;
                head.pitch = 0.4f;
                rightArm.pitch = 0.2f;
                leftArm.pitch = 0.2f;
                rightArm.roll = 0.5f;
                leftArm.roll = -0.5f;
            }
            default -> {
            }
        }
        if (boss.isBubbled() && pose == RaggimelEntity.POSE_IDLE) {
            // Floating: legs dangle, arms drift
            rightLeg.pitch = 0.2f + sway * 0.5f;
            leftLeg.pitch = -0.1f - sway * 0.5f;
            rightArm.roll = 0.35f + sway;
            leftArm.roll = -0.35f - sway;
        }
        hat.copyTransform(head);
        rightSleeve.copyTransform(rightArm);
        leftSleeve.copyTransform(leftArm);
        rightPants.copyTransform(rightLeg);
        leftPants.copyTransform(leftLeg);
        jacket.copyTransform(body);
    }
}
