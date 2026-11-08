package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.boss.sloth.RickyLook;

/**
 * RICKY: a player (Patrik's skin, wide arms). His arms follow what he's doing: flat on his back asleep, sitting up,
 * stretching, a tome held out, both hands raised, a palm to the ground, crouched to dash, a fist thrown, hands cupped at
 * his side for the beam and thrust forward to fire it, swept back in flight, flexed and straining as he powers up.
 * (Lying down, sitting, flying and diving tip his whole body: RickyRenderer.)
 */
public class RickyModel<T extends HostileEntity & RickyLook> extends PlayerEntityModel<T> {
    public RickyModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(T boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == RickyLook.POSE_GROUND || pose == RickyLook.POSE_CROUCH;
        rightArmPose = boss.getMainHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
        leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        boolean still = pose == RickyLook.POSE_SLEEP || pose == RickyLook.POSE_SIT || pose == RickyLook.POSE_FLY || pose == RickyLook.POSE_DIVE;
        super.setAngles(boss, still ? 0 : limbAngle, still ? 0 : limbDistance, age, headYaw, headPitch);
        float tremble = MathHelper.sin(age * 2.7f) * 0.04f;
        switch (pose) {
            case RickyLook.POSE_SLEEP -> {
                // Flat on his back, arms loose at his sides, head lolling, breathing slowly
                float breath = MathHelper.sin(age * 0.08f) * 0.03f;
                head.pitch = 0.05f;
                head.yaw = 0.35f;
                rightArm.pitch = 0;
                rightArm.yaw = 0;
                rightArm.roll = 0.18f + breath;
                leftArm.pitch = 0;
                leftArm.yaw = 0;
                leftArm.roll = -0.12f - breath;
                rightLeg.pitch = 0;
                rightLeg.roll = 0.06f;
                leftLeg.pitch = 0;
                leftLeg.roll = -0.1f;
            }
            case RickyLook.POSE_SIT -> {
                // Sitting up in the grass, legs out, one hand behind him, a yawn
                rightLeg.pitch = -1.45f;
                rightLeg.yaw = 0.15f;
                leftLeg.pitch = -1.45f;
                leftLeg.yaw = -0.15f;
                rightArm.pitch = 0.5f;
                rightArm.roll = 0.3f;
                leftArm.pitch = -2.4f + MathHelper.sin(age * 0.1f) * 0.1f;
                leftArm.yaw = 0.6f;
                head.pitch = -0.45f;
            }
            case RickyLook.POSE_STRETCH -> {
                rightArm.pitch = -3.0f;
                rightArm.yaw = -0.2f;
                rightArm.roll = -0.15f;
                leftArm.pitch = -3.0f;
                leftArm.yaw = 0.2f;
                leftArm.roll = 0.15f;
                head.pitch = -0.4f;
            }
            case RickyLook.POSE_CAST, RickyLook.POSE_POINT -> {
                rightArm.pitch = -MathHelper.HALF_PI + head.pitch * 0.6f + (pose == RickyLook.POSE_POINT ? 0.35f : 0) + tremble;
                rightArm.yaw = head.yaw * 0.6f;
                rightArm.roll = 0;
                leftArm.pitch = -0.4f;
                leftArm.roll = -0.25f;
            }
            case RickyLook.POSE_RAISE -> {
                rightArm.pitch = -2.95f + tremble;
                rightArm.yaw = 0;
                rightArm.roll = 0.2f;
                leftArm.pitch = -2.95f - tremble;
                leftArm.yaw = 0;
                leftArm.roll = -0.2f;
                head.pitch = -0.45f;
            }
            case RickyLook.POSE_GROUND -> {
                // Crouched, a palm pressed to the ground in front of him
                rightArm.pitch = -0.85f;
                rightArm.roll = 0.15f;
                leftArm.pitch = -0.7f;
                leftArm.roll = -0.25f;
                head.pitch = 0.45f;
            }
            case RickyLook.POSE_CROUCH -> {
                // Coiled to spring: fist drawn back, the other hand out in front
                rightArm.pitch = 0.75f;
                rightArm.roll = 0.35f;
                leftArm.pitch = -1.1f;
                leftArm.yaw = 0.3f;
                head.pitch = 0.1f;
            }
            case RickyLook.POSE_PUNCH -> {
                rightArm.pitch = -MathHelper.HALF_PI;
                rightArm.yaw = -0.05f;
                rightArm.roll = 0;
                leftArm.pitch = 0.8f;
                leftArm.roll = -0.2f;
                rightLeg.pitch = 0.6f;
                leftLeg.pitch = -0.7f;
            }
            case RickyLook.POSE_SMASH -> {
                rightArm.pitch = -2.7f;
                rightArm.yaw = -0.4f;
                leftArm.pitch = -2.7f;
                leftArm.yaw = 0.4f;
                head.pitch = 0.2f;
            }
            case RickyLook.POSE_CUP -> {
                // Both hands cupped together at his right hip, gathering the dark
                rightArm.pitch = 0.35f;
                rightArm.yaw = -0.2f;
                rightArm.roll = 0.25f;
                leftArm.pitch = -0.55f;
                leftArm.yaw = -1.0f;
                leftArm.roll = 0.1f;
                head.yaw = -0.3f;
                body.yaw = -0.3f;
            }
            case RickyLook.POSE_BEAM -> {
                rightArm.pitch = -MathHelper.HALF_PI + tremble;
                rightArm.yaw = 0.18f;
                leftArm.pitch = -MathHelper.HALF_PI - tremble;
                leftArm.yaw = -0.18f;
                rightLeg.pitch = 0.4f;
                leftLeg.pitch = -0.45f;
            }
            case RickyLook.POSE_FLY -> {
                rightArm.pitch = 0.6f;
                rightArm.roll = 0.35f;
                leftArm.pitch = 0.6f;
                leftArm.roll = -0.35f;
                rightLeg.pitch = 0.3f;
                leftLeg.pitch = 0.15f;
                head.pitch = -0.7f;
            }
            case RickyLook.POSE_DIVE -> {
                rightArm.pitch = -3.0f;
                rightArm.yaw = -0.15f;
                leftArm.pitch = -3.0f;
                leftArm.yaw = 0.15f;
                head.pitch = -0.5f;
            }
            case RickyLook.POSE_POWER -> {
                // Fists clenched down at his sides, every muscle straining, roaring at the sky
                rightArm.pitch = -0.25f + tremble;
                rightArm.roll = 0.55f;
                leftArm.pitch = -0.25f - tremble;
                leftArm.roll = -0.55f;
                rightLeg.roll = 0.18f;
                leftLeg.roll = -0.18f;
                head.pitch = -0.5f + tremble;
            }
            case RickyLook.POSE_THROW -> {
                rightArm.pitch = -1.25f;
                rightArm.yaw = -0.2f;
                leftArm.pitch = 0.6f;
                leftArm.roll = -0.2f;
            }
            case RickyLook.POSE_BARRAGE_A, RickyLook.POSE_JAB_A -> {
                rightArm.pitch = -MathHelper.HALF_PI;
                rightArm.yaw = 0.1f;
                leftArm.pitch = 0.4f;
            }
            case RickyLook.POSE_BARRAGE_B, RickyLook.POSE_JAB_B -> {
                leftArm.pitch = -MathHelper.HALF_PI;
                leftArm.yaw = -0.1f;
                rightArm.pitch = 0.4f;
            }
            case RickyLook.POSE_SPREAD -> {
                rightArm.pitch = -0.2f;
                rightArm.roll = 1.35f + tremble;
                leftArm.pitch = -0.2f;
                leftArm.roll = -1.35f - tremble;
                head.pitch = -0.3f;
            }
            default -> {
            }
        }
        hat.copyTransform(head);
        jacket.copyTransform(body);
        rightSleeve.copyTransform(rightArm);
        leftSleeve.copyTransform(leftArm);
        rightPants.copyTransform(rightLeg);
        leftPants.copyTransform(leftLeg);
    }
}
