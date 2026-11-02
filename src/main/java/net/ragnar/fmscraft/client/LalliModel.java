package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.boss.gluttony.LalliEntity;

/**
 * LalliLarge: a player (slim arms, Larus's skin), empty-handed. His arms follow what he's doing: spread wide while he
 * floats, a fist drawn back and thrown, a hand held out for the beam, pointing for the Glacier, both raised for the
 * icicles, crossed in and flung wide for the push, the breath with his head thrust forward, a knee raised to stomp,
 * the ice blade raised and cut down across, held out as he spins.
 */
public class LalliModel extends PlayerEntityModel<LalliEntity> {
    public LalliModel(ModelPart root) {
        super(root, true);
    }

    @Override
    public void setAngles(LalliEntity boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == LalliEntity.POSE_LAND || pose == LalliEntity.POSE_DASH;
        rightArmPose = BipedEntityModel.ArmPose.EMPTY;
        leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.setAngles(boss, limbAngle, limbDistance, age, headYaw, headPitch);
        float sway = MathHelper.cos(age * 0.12f) * 0.08f;
        float tremble = MathHelper.sin(age * 2.3f) * 0.04f;
        switch (pose) {
            case LalliEntity.POSE_FLOAT -> {
                // Arms spread, legs hanging together, drifting
                rightArm.pitch = sway;
                rightArm.roll = 1.15f + sway;
                leftArm.pitch = -sway;
                leftArm.roll = -1.15f - sway;
                rightLeg.pitch = 0.12f + sway * 0.5f;
                leftLeg.pitch = -0.05f - sway * 0.5f;
                rightLeg.roll = 0.04f;
                leftLeg.roll = -0.04f;
                head.pitch = 0.25f;
            }
            case LalliEntity.POSE_LAND -> {
                rightArm.pitch = -0.5f;
                rightArm.roll = 0.35f;
                leftArm.pitch = -0.5f;
                leftArm.roll = -0.35f;
                head.pitch = 0.45f;
            }
            case LalliEntity.POSE_PUNCH_CHARGE -> {
                // Fist drawn right back, the other up in guard
                rightArm.pitch = 0.95f + tremble;
                rightArm.yaw = 0.35f;
                rightArm.roll = 0.25f;
                leftArm.pitch = -1.1f;
                leftArm.yaw = 0.3f;
            }
            case LalliEntity.POSE_PUNCH -> {
                rightArm.pitch = -MathHelper.HALF_PI - 0.1f;
                rightArm.yaw = -0.1f;
                rightArm.roll = 0;
                leftArm.pitch = 0.6f;
            }
            case LalliEntity.POSE_POINT -> {
                rightArm.pitch = -MathHelper.HALF_PI + head.pitch + tremble;
                rightArm.yaw = head.yaw;
                rightArm.roll = 0;
                leftArm.pitch = 0.25f;
            }
            case LalliEntity.POSE_BEAM -> {
                rightArm.pitch = -MathHelper.HALF_PI + head.pitch * 0.8f;
                rightArm.yaw = head.yaw * 0.6f;
                rightArm.roll = tremble;
                leftArm.pitch = -0.6f;
                leftArm.roll = -0.35f;
            }
            case LalliEntity.POSE_RAISE -> {
                rightArm.pitch = -MathHelper.PI + 0.2f + sway;
                rightArm.roll = 0.25f;
                leftArm.pitch = -MathHelper.PI + 0.2f - sway;
                leftArm.roll = -0.25f;
                head.pitch = -0.5f;
            }
            case LalliEntity.POSE_BREATH -> {
                // Head thrust forward, arms flung back
                head.pitch = -0.05f + tremble;
                rightArm.pitch = 0.55f;
                rightArm.roll = 0.35f;
                leftArm.pitch = 0.55f;
                leftArm.roll = -0.35f;
            }
            case LalliEntity.POSE_PUSH_CHARGE -> {
                rightArm.pitch = -1.25f + tremble;
                rightArm.yaw = 0.75f;
                leftArm.pitch = -1.25f - tremble;
                leftArm.yaw = -0.75f;
                head.pitch = 0.3f;
            }
            case LalliEntity.POSE_PUSH -> {
                rightArm.pitch = -0.6f;
                rightArm.roll = 1.45f;
                leftArm.pitch = -0.6f;
                leftArm.roll = -1.45f;
                head.pitch = -0.3f;
            }
            case LalliEntity.POSE_DASH -> {
                rightArm.pitch = 1.1f;
                leftArm.pitch = 1.1f;
                head.pitch = 0.1f;
            }
            case LalliEntity.POSE_STOMP -> {
                rightLeg.pitch = -1.05f;
                rightArm.roll = 0.55f;
                leftArm.roll = -0.55f;
            }
            case LalliEntity.POSE_CAST -> {
                rightArm.pitch = -1.0f + tremble;
                rightArm.roll = 0.2f;
                leftArm.pitch = -1.0f - tremble;
                leftArm.roll = -0.2f;
                head.pitch = 0.35f;
            }
            case LalliEntity.POSE_JUMP -> {
                rightArm.pitch = -2.8f;
                leftArm.pitch = -2.8f;
                rightLeg.pitch = 0.45f;
                leftLeg.pitch = -0.45f;
            }
            case LalliEntity.POSE_ROAR -> {
                head.pitch = -0.6f;
                rightArm.pitch = 0.3f;
                rightArm.roll = 0.9f;
                leftArm.pitch = 0.3f;
                leftArm.roll = -0.9f;
            }
            case LalliEntity.POSE_SLASH_A -> {
                // The blade raised high over his right shoulder
                rightArm.pitch = -2.7f;
                rightArm.yaw = 0.45f;
                rightArm.roll = 0.2f;
                leftArm.pitch = -0.4f;
                leftArm.roll = -0.4f;
            }
            case LalliEntity.POSE_SLASH_B -> {
                // Cut down and across
                rightArm.pitch = -0.7f;
                rightArm.yaw = -0.9f;
                rightArm.roll = 0;
                leftArm.pitch = 0.4f;
                leftArm.roll = -0.3f;
                head.pitch = 0.2f;
            }
            case LalliEntity.POSE_SPIN -> {
                // Blade out to the side, turning round and round
                rightArm.pitch = -MathHelper.HALF_PI;
                rightArm.yaw = -1.2f;
                rightArm.roll = 0;
                leftArm.pitch = 0;
                leftArm.roll = -1.2f;
            }
            default -> {
                // Heavy, slow arms
                rightArm.pitch *= 0.6f;
                leftArm.pitch *= 0.6f;
                rightArm.roll += 0.08f;
                leftArm.roll -= 0.08f;
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
