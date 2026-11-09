package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.boss.pride.LeonardLook;

/**
 * LEONARD (and CONEMAN): a player (Leo's skin) with his lightning bolt. His arms follow what he's doing: both raised for
 * the Discharge and the Lightning Strike, one out for the Zap and Chain Lightning, drawn back and thrown, spread wide
 * for the Almighty Pull, the clap, the dash (leaning in, arms swept back), the stomp. As CONEMAN his head is inside the
 * traffic cone, so it isn't drawn.
 */
public class LeonardModel<T extends HostileEntity & LeonardLook> extends PlayerEntityModel<T> {
    public LeonardModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(T boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == LeonardLook.POSE_LAND || pose == LeonardLook.POSE_CROUCH;
        rightArmPose = boss.getMainHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
        leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        super.setAngles(boss, limbAngle, limbDistance, age, headYaw, headPitch);
        float tremble = MathHelper.sin(age * 2.7f) * 0.04f;
        switch (pose) {
            case LeonardLook.POSE_LAND -> {
                // Landed in a crouch, the bolt held out
                rightArm.pitch = -0.4f;
                rightArm.roll = 0.9f;
                leftArm.pitch = -0.2f;
                leftArm.roll = -0.2f;
                head.pitch = 0.35f;
            }
            case LeonardLook.POSE_RAISE -> {
                rightArm.pitch = -2.95f + tremble;
                rightArm.yaw = 0;
                rightArm.roll = 0.15f;
                leftArm.pitch = -2.95f - tremble;
                leftArm.yaw = 0;
                leftArm.roll = -0.15f;
                head.pitch = -0.3f;
            }
            case LeonardLook.POSE_CHANNEL, LeonardLook.POSE_POINT -> {
                rightArm.pitch = -MathHelper.HALF_PI + head.pitch + tremble;
                rightArm.yaw = head.yaw;
                rightArm.roll = 0;
                leftArm.pitch = 0.3f;
                leftArm.roll = -0.3f;
            }
            case LeonardLook.POSE_THROW_BACK -> {
                rightArm.pitch = -2.8f;
                rightArm.yaw = 0.35f;
                rightArm.roll = 0.15f;
                leftArm.pitch = -1.2f;
                leftArm.yaw = 0.3f;
            }
            case LeonardLook.POSE_THROW -> {
                rightArm.pitch = -1.3f;
                rightArm.yaw = -0.2f;
                leftArm.pitch = 0.6f;
                leftArm.roll = -0.2f;
            }
            case LeonardLook.POSE_SIDE_THROW -> {
                rightArm.pitch = -1.4f;
                rightArm.yaw = 1.1f;
                rightArm.roll = 0;
                leftArm.pitch = -0.4f;
                leftArm.roll = -0.6f;
            }
            case LeonardLook.POSE_SPREAD, LeonardLook.POSE_HOVER -> {
                rightArm.pitch = -0.2f;
                rightArm.roll = 1.35f + tremble;
                leftArm.pitch = -0.2f;
                leftArm.roll = -1.35f - tremble;
                rightLeg.pitch = 0.1f;
                leftLeg.pitch = -0.05f;
                if (pose == LeonardLook.POSE_HOVER) head.pitch = -0.25f;
            }
            case LeonardLook.POSE_CLAP_WIND -> {
                rightArm.pitch = -1.45f;
                rightArm.yaw = -1.0f;
                leftArm.pitch = -1.45f;
                leftArm.yaw = 1.0f;
            }
            case LeonardLook.POSE_CLAP -> {
                rightArm.pitch = -1.45f;
                rightArm.yaw = 0.35f;
                leftArm.pitch = -1.45f;
                leftArm.yaw = -0.35f;
            }
            case LeonardLook.POSE_DASH -> {
                rightArm.pitch = 1.1f;
                rightArm.roll = 0.3f;
                leftArm.pitch = 1.1f;
                leftArm.roll = -0.3f;
                rightLeg.pitch = 0.4f;
                leftLeg.pitch = -0.6f;
            }
            case LeonardLook.POSE_CROUCH -> {
                rightArm.pitch = 0.5f;
                rightArm.roll = 0.5f;
                leftArm.pitch = 0.5f;
                leftArm.roll = -0.5f;
            }
            case LeonardLook.POSE_SLAM -> {
                rightArm.pitch = -0.6f;
                rightArm.roll = 0.3f;
                leftArm.pitch = -0.6f;
                leftArm.roll = -0.3f;
                head.pitch = 0.3f;
            }
            case LeonardLook.POSE_STOMP -> {
                rightLeg.pitch = -1.2f;
                rightArm.pitch = -0.4f;
                rightArm.roll = 0.9f;
                leftArm.pitch = -0.4f;
                leftArm.roll = -0.9f;
            }
            case LeonardLook.POSE_PUNCH_A -> {
                rightArm.pitch = -1.6f;
                rightArm.yaw = 0.1f;
                leftArm.pitch = 0.5f;
            }
            case LeonardLook.POSE_PUNCH_B -> {
                leftArm.pitch = -1.6f;
                leftArm.yaw = -0.1f;
                rightArm.pitch = 0.5f;
            }
            case LeonardLook.POSE_GUILTY -> {
                // Hunched, hands fidgeting together in front of him, shifting his weight, very much not guilty of anything
                float fidget = MathHelper.sin(age * 0.55f), shift = MathHelper.sin(age * 0.11f);
                rightArm.pitch = -0.75f + fidget * 0.12f;
                rightArm.yaw = -0.5f;
                rightArm.roll = 0.05f;
                leftArm.pitch = -0.7f - fidget * 0.12f;
                leftArm.yaw = 0.55f;
                leftArm.roll = -0.05f;
                head.pitch += 0.12f;
                body.roll = shift * 0.04f;
                rightLeg.pitch = Math.max(0, shift) * 0.15f;
                leftLeg.pitch = Math.max(0, -shift) * 0.15f;
            }
            case LeonardLook.POSE_CAUGHT -> {
                // Caught: arms flung up, frozen
                rightArm.pitch = -2.7f + tremble;
                rightArm.roll = 0.5f;
                leftArm.pitch = -2.7f - tremble;
                leftArm.roll = -0.5f;
                head.pitch -= 0.15f;
            }
            case LeonardLook.POSE_FLEE -> {
                // Running for his life, arms waving over his head, legs going twice as fast as they need to
                float wave = age * 1.1f;
                rightArm.pitch = -2.6f + MathHelper.sin(wave) * 0.55f;
                rightArm.roll = 0.35f + MathHelper.cos(wave) * 0.25f;
                rightArm.yaw = 0;
                leftArm.pitch = -2.6f + MathHelper.sin(wave + MathHelper.PI) * 0.55f;
                leftArm.roll = -0.35f - MathHelper.cos(wave) * 0.25f;
                leftArm.yaw = 0;
                float legs = MathHelper.cos(age * 1.3f) * 1.1f;
                rightLeg.pitch = legs;
                leftLeg.pitch = -legs;
                head.pitch = -0.1f;
            }
            default -> {
            }
        }
        boolean coned = boss.coned();
        head.visible = !coned;
        hat.visible = !coned;
        hat.copyTransform(head);
        jacket.copyTransform(body);
        rightSleeve.copyTransform(rightArm);
        leftSleeve.copyTransform(leftArm);
        rightPants.copyTransform(rightLeg);
        leftPants.copyTransform(leftLeg);
    }
}
