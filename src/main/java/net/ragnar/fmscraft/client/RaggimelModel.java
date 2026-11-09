package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.LivingEntity;
import net.ragnar.fmscraft.boss.greed.RaggimelEntity;
import net.ragnar.fmscraft.boss.greed.RaggimelLook;

/** RAGGIMEL is a player (wide arms) whose arms follow what he's doing: casting, aiming, crouching to charge, slamming, calling up the Twister. */
public class RaggimelModel<T extends LivingEntity & RaggimelLook> extends PlayerEntityModel<T> {
    public RaggimelModel(ModelPart root) {
        super(root, false);
    }

    private float tickDelta;

    private static float ease(float from, float to, float t, float start, float length) {
        float u = MathHelper.clamp((t - start) / length, 0f, 1f);
        return MathHelper.lerp(u * u * (3 - 2 * u), from, to);
    }

    /**
     * His goodbye, all of it eased on its own clock: his head bowing as he grows, arms hanging; held there a while; then
     * his head slowly lifting to the sky as his right arm rises to point up at it.
     */
    private void farewell(float t, float age) {
        float breath = MathHelper.sin(age * 0.07f) * 0.02f;
        float up = RaggimelEntity.DEATH_LOOK_UP, point = RaggimelEntity.DEATH_POINT - RaggimelEntity.DEATH_LOOK_UP;
        float down = ease(0f, 0.75f, t, 0, 30);
        head.pitch = ease(down, -0.85f, t, up, point) + breath;
        head.yaw = 0;
        head.roll = ease(0f, 0.05f, t, 10, 40) * (1 - MathHelper.clamp((t - up) / point, 0, 1));
        rightArm.pitch = ease(0.05f, -2.95f, t, up + 8, point) + breath;
        rightArm.yaw = ease(0f, -0.15f, t, up + 8, point);
        rightArm.roll = ease(0.05f, 0.12f, t, up + 8, point);
        leftArm.pitch = 0.05f + breath;
        leftArm.yaw = 0;
        leftArm.roll = -0.05f;
        rightLeg.pitch = 0;
        leftLeg.pitch = 0;
    }

    @Override
    public void animateModel(T boss, float limbAngle, float limbDistance, float tickDelta) {
        super.animateModel(boss, limbAngle, limbDistance, tickDelta);
        this.tickDelta = tickDelta;
    }

    @Override
    public void setAngles(T boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == RaggimelEntity.POSE_CROUCH;
        super.setAngles(boss, limbAngle, limbDistance, age, headYaw, headPitch);
        float sway = MathHelper.cos(age * 0.6662f) * 0.15f;
        if (pose == RaggimelEntity.POSE_FAREWELL) {
            farewell(boss.farewellTime(tickDelta), age);
            pose = -1;
        }
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
            case RaggimelEntity.POSE_SHAKE -> {
                // Shaking his head hard, like a wet dog
                head.yaw = MathHelper.sin(age * 1.9f) * 0.7f;
                head.roll = MathHelper.sin(age * 1.9f + 0.6f) * 0.25f;
                head.pitch = 0.15f;
                rightArm.pitch = 0.1f;
                leftArm.pitch = 0.1f;
                rightArm.roll = 0.3f;
                leftArm.roll = -0.3f;
            }
            case RaggimelEntity.POSE_SMASH -> {
                // The staff brought down in front of him in both hands
                rightArm.pitch = -0.55f;
                leftArm.pitch = -0.55f;
                rightArm.yaw = -0.15f;
                leftArm.yaw = 0.35f;
                rightArm.roll = 0;
                leftArm.roll = 0;
                head.pitch = 0.5f;
            }
            case RaggimelEntity.POSE_RAISE -> {
                // The Twister: arms swept out and up over his head, looking up into the storm, then slowly stirring
                // the sky in circles, shaking harder as it's about to break
                float rt = boss.raiseTime(tickDelta);
                float u = MathHelper.clamp(rt / 24f, 0, 1), up = u * u * (3 - 2 * u);
                float out = MathHelper.sin(up * MathHelper.PI) * 1.1f;
                float stir = up * MathHelper.clamp((rt - 18) / 12f, 0, 1);
                float shake = MathHelper.clamp((rt - 60) / 40f, 0, 1) * 0.07f * MathHelper.sin(age * 2.7f);
                float circle = rt * 0.2f;
                rightArm.pitch = -2.95f * up + 0.14f * MathHelper.sin(circle) * stir + shake;
                leftArm.pitch = -2.95f * up + 0.14f * MathHelper.sin(circle + MathHelper.PI) * stir - shake;
                rightArm.roll = 0.12f + out + 0.2f * MathHelper.cos(circle) * stir;
                leftArm.roll = -0.12f - out - 0.2f * MathHelper.cos(circle + MathHelper.PI) * stir;
                rightArm.yaw = 0;
                leftArm.yaw = 0;
                head.pitch = MathHelper.lerp(up, head.pitch, -0.55f);
                head.yaw *= 1 - up;
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
