package net.ragnar.fmscraft.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.MathHelper;
import net.ragnar.fmscraft.boss.lust.MugenLook;

/**
 * MUGEN (and his clones): a player (Karl's skin) with a netherite blade in each hand. His arms follow what he's doing:
 * crossed over his chest as he's dragged up out of the ground, head bowed, then lifted; reaching back over his
 * shoulders to draw; a low stance, blades trailing; a hand seal for his jutsu; throwing overhand and side-arm; a
 * crouch and a spinning kick with his leg out; the chain hand out; a stab; both blades out for the spin; diving, arms
 * swept back; hanging in the sky with his arms spread; the slide; his head on one side, staring, for the Taunt.
 */
public class MugenModel<T extends HostileEntity & MugenLook> extends PlayerEntityModel<T> {
    public MugenModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setAngles(T boss, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
        int pose = boss.poseState();
        sneaking = pose == MugenLook.POSE_STANCE || pose == MugenLook.POSE_LAND || pose == MugenLook.POSE_KICK_WIND
                || pose == MugenLook.POSE_CROUCH || pose == MugenLook.POSE_IAIDO || pose == MugenLook.POSE_SPENT;
        rightArmPose = boss.getMainHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
        leftArmPose = boss.getOffHandStack().isEmpty() ? BipedEntityModel.ArmPose.EMPTY : BipedEntityModel.ArmPose.ITEM;
        super.setAngles(boss, limbAngle, limbDistance, age, headYaw, headPitch);
        float sway = MathHelper.cos(age * 0.15f) * 0.06f;
        float tremble = MathHelper.sin(age * 2.3f) * 0.03f;
        switch (pose) {
            case MugenLook.POSE_RISE, MugenLook.POSE_LOOK -> {
                // Arms crossed over his chest; head bowed, until he looks up
                rightArm.pitch = -1.05f;
                rightArm.yaw = 0.7f;
                rightArm.roll = 0;
                leftArm.pitch = -1.0f;
                leftArm.yaw = -0.7f;
                leftArm.roll = 0;
                rightLeg.pitch = 0;
                leftLeg.pitch = 0;
                head.pitch = pose == MugenLook.POSE_RISE ? 0.55f : -0.05f + tremble;
                head.yaw = 0;
            }
            case MugenLook.POSE_DRAW -> {
                // Reaching back over both shoulders for the blades
                rightArm.pitch = -2.85f;
                rightArm.yaw = -0.25f;
                rightArm.roll = 0.35f;
                leftArm.pitch = -2.85f;
                leftArm.yaw = 0.25f;
                leftArm.roll = -0.35f;
                head.pitch = 0.15f;
            }
            case MugenLook.POSE_STANCE -> {
                // Low, the right blade trailing behind, the left held out in front
                rightArm.pitch = 0.95f;
                rightArm.yaw = 0.2f;
                rightArm.roll = 0.55f;
                leftArm.pitch = -1.25f + sway;
                leftArm.yaw = 0.35f;
                leftArm.roll = -0.1f;
                head.pitch = -0.15f;
            }
            case MugenLook.POSE_LAND -> {
                // Landed in a crouch, a blade out to each side
                rightArm.pitch = -0.25f;
                rightArm.roll = 1.0f;
                leftArm.pitch = -0.25f;
                leftArm.roll = -1.0f;
                head.pitch = 0.45f;
            }
            case MugenLook.POSE_SEAL -> {
                // A hand seal, both hands together in front of his chest
                rightArm.pitch = -1.2f + tremble;
                rightArm.yaw = 0.6f;
                rightArm.roll = 0;
                leftArm.pitch = -1.2f - tremble;
                leftArm.yaw = -0.6f;
                leftArm.roll = 0;
                head.pitch = 0.25f;
            }
            case MugenLook.POSE_THROW -> {
                rightArm.pitch = -1.65f;
                rightArm.yaw = -0.15f;
                rightArm.roll = 0;
                leftArm.pitch = 0.55f;
                leftArm.roll = -0.2f;
            }
            case MugenLook.POSE_THROW_BACK -> {
                rightArm.pitch = -2.7f;
                rightArm.yaw = 0.45f;
                rightArm.roll = 0.1f;
                leftArm.pitch = -1.1f;
                leftArm.yaw = 0.2f;
            }
            case MugenLook.POSE_KICK_WIND -> {
                rightArm.pitch = 0.4f;
                rightArm.roll = 1.0f;
                leftArm.pitch = 0.4f;
                leftArm.roll = -1.0f;
                rightLeg.pitch = -0.5f;
            }
            case MugenLook.POSE_KICK -> {
                // One leg straight out to the side, arms flung out: spinning (MugenRenderer turns him)
                rightLeg.pitch = -0.2f;
                rightLeg.roll = 1.35f;
                leftLeg.pitch = 0;
                leftLeg.roll = -0.1f;
                rightArm.pitch = 0;
                rightArm.roll = 1.3f;
                leftArm.pitch = 0;
                leftArm.roll = -1.3f;
                head.pitch = 0.1f;
            }
            case MugenLook.POSE_CHAIN, MugenLook.POSE_POINT -> {
                rightArm.pitch = -MathHelper.HALF_PI + head.pitch;
                rightArm.yaw = head.yaw;
                rightArm.roll = 0;
                leftArm.pitch = 0.45f;
                leftArm.roll = -0.15f;
            }
            case MugenLook.POSE_STAB -> {
                // A lunge: blade driven straight in, the back foot planted
                rightArm.pitch = -1.55f;
                rightArm.yaw = 0;
                rightArm.roll = 0;
                leftArm.pitch = 0.9f;
                leftArm.roll = -0.25f;
                rightLeg.pitch = -0.65f;
                leftLeg.pitch = 0.55f;
                head.pitch = 0.1f;
            }
            case MugenLook.POSE_SPIN -> {
                // Both blades held straight out to the sides: a spinning top of steel
                rightArm.pitch = 0;
                rightArm.yaw = 0;
                rightArm.roll = 1.5f;
                leftArm.pitch = 0;
                leftArm.yaw = 0;
                leftArm.roll = -1.5f;
                rightLeg.roll = 0.15f;
                leftLeg.roll = -0.15f;
            }
            case MugenLook.POSE_PLUNGE, MugenLook.POSE_DASH -> {
                // Arms swept back, blades trailing
                rightArm.pitch = 1.25f;
                rightArm.roll = 0.2f;
                leftArm.pitch = 1.25f;
                leftArm.roll = -0.2f;
                head.pitch = pose == MugenLook.POSE_PLUNGE ? 0.6f : 0.15f;
                if (pose == MugenLook.POSE_PLUNGE) {
                    rightLeg.pitch = 0.1f;
                    leftLeg.pitch = 0.1f;
                }
            }
            case MugenLook.POSE_ORB -> {
                rightArm.pitch = -1.45f;
                rightArm.yaw = 0.3f;
                rightArm.roll = 0;
                leftArm.pitch = -1.45f;
                leftArm.yaw = -0.3f;
                leftArm.roll = 0;
                head.pitch = 0.1f;
            }
            case MugenLook.POSE_RAISE -> {
                rightArm.pitch = -3.05f;
                rightArm.yaw = 0;
                rightArm.roll = 0.1f;
                leftArm.pitch = -0.2f;
                leftArm.roll = -0.6f;
                head.pitch = -0.45f;
            }
            case MugenLook.POSE_JUMP, MugenLook.POSE_CROUCH -> {
                // Knees drawn up, arms back
                rightArm.pitch = 0.8f;
                leftArm.pitch = 0.8f;
                rightArm.roll = 0.25f;
                leftArm.roll = -0.25f;
                if (pose == MugenLook.POSE_JUMP) {
                    rightLeg.pitch = -1.1f;
                    leftLeg.pitch = -0.7f;
                }
            }
            case MugenLook.POSE_GRAPPLE -> {
                rightArm.pitch = -2.2f + head.pitch * 0.5f;
                rightArm.yaw = head.yaw;
                rightArm.roll = 0;
                leftArm.pitch = 0.3f;
                leftArm.roll = -0.8f;
                rightLeg.pitch = 0.3f;
                leftLeg.pitch = -0.4f;
            }
            case MugenLook.POSE_HANG -> {
                // Hanging in the eclipse, arms spread, blades out, looking down at them
                rightArm.pitch = -0.1f + sway;
                rightArm.yaw = 0;
                rightArm.roll = 1.25f + sway;
                leftArm.pitch = -0.1f - sway;
                leftArm.yaw = 0;
                leftArm.roll = -1.25f - sway;
                rightLeg.pitch = 0.08f;
                leftLeg.pitch = -0.04f;
                rightLeg.roll = 0.05f;
                leftLeg.roll = -0.05f;
                head.pitch = 0.4f;
            }
            case MugenLook.POSE_DIVE -> {
                // Head first, blades leading (MugenRenderer turns him over)
                rightArm.pitch = -2.95f;
                rightArm.roll = 0.15f;
                leftArm.pitch = -2.95f;
                leftArm.roll = -0.15f;
                rightLeg.pitch = 0.05f;
                leftLeg.pitch = -0.05f;
                head.pitch = -0.4f;
            }
            case MugenLook.POSE_SLIDE -> {
                // Feet first along the ground, leaning back (MugenRenderer tips him)
                rightLeg.pitch = -1.45f;
                leftLeg.pitch = -0.6f;
                rightArm.pitch = 0.9f;
                rightArm.roll = 0.5f;
                leftArm.pitch = 0.9f;
                leftArm.roll = -0.5f;
                head.pitch = -0.6f;
            }
            case MugenLook.POSE_TAUNT -> {
                // Head on one side, staring, blades hanging loose
                head.roll = 0.45f + tremble;
                head.pitch = 0.2f;
                head.yaw = 0;
                rightArm.pitch = 0.15f + sway;
                rightArm.yaw = 0;
                rightArm.roll = 0.18f;
                leftArm.pitch = 0.15f - sway;
                leftArm.yaw = 0;
                leftArm.roll = -0.18f;
            }
            case MugenLook.POSE_SLASH_A -> {
                rightArm.pitch = -2.65f;
                rightArm.yaw = 0.55f;
                rightArm.roll = 0.2f;
                leftArm.pitch = -0.55f;
                leftArm.yaw = -0.3f;
            }
            case MugenLook.POSE_SLASH_B -> {
                rightArm.pitch = -0.75f;
                rightArm.yaw = -0.95f;
                rightArm.roll = 0;
                leftArm.pitch = -1.3f;
                leftArm.yaw = 0.65f;
                head.pitch = 0.15f;
            }
            case MugenLook.POSE_IAIDO -> {
                // Blades sheathed, his right hand across on the hilt at his left hip, the left steadying the sheath.
                // Perfectly still
                rightArm.pitch = -0.85f;
                rightArm.yaw = 0.85f;
                rightArm.roll = 0;
                leftArm.pitch = -0.45f;
                leftArm.yaw = 0.35f;
                leftArm.roll = 0;
                head.pitch = 0.05f;
                head.yaw = 0;
            }
            case MugenLook.POSE_PUPPET -> {
                // Both hands raised before him, fingers working the strings
                float pull = MathHelper.sin(age * 0.6f) * 0.15f;
                rightArm.pitch = -2.0f + pull;
                rightArm.yaw = -0.2f;
                rightArm.roll = 0.25f;
                leftArm.pitch = -2.0f - pull;
                leftArm.yaw = 0.2f;
                leftArm.roll = -0.25f;
                head.pitch = 0.1f;
            }
            case MugenLook.POSE_SPENT -> {
                // The anger gone out of him: knees giving, head dropping, arms hanging loose
                head.pitch = 0.6f;
                head.yaw = 0;
                rightArm.pitch = 0.1f;
                rightArm.yaw = 0;
                rightArm.roll = 0.08f;
                leftArm.pitch = 0.1f;
                leftArm.yaw = 0;
                leftArm.roll = -0.08f;
            }
            case MugenLook.POSE_SIT, MugenLook.POSE_SIT_SKY -> {
                // Sat on the ground, legs out, leaning back on his hands; slowly lifting his head to the sky
                float sky = boss.skyward(age - boss.age);
                float breath = MathHelper.sin(age * 0.07f) * 0.025f;
                head.pitch = MathHelper.lerp(sky, 0.5f, -0.95f) + breath;
                head.yaw = 0;
                rightArm.pitch = 0.6f + breath;
                rightArm.yaw = 0;
                rightArm.roll = 0.22f;
                leftArm.pitch = 0.6f + breath;
                leftArm.yaw = 0;
                leftArm.roll = -0.22f;
                rightLeg.pitch = -1.45f;
                rightLeg.yaw = 0.12f;
                rightLeg.roll = 0;
                leftLeg.pitch = -1.15f;
                leftLeg.yaw = -0.3f;
                leftLeg.roll = 0;
            }
            case MugenLook.POSE_HOLE -> {
                // Both hands straight up, holding the dark over his head, trembling with it
                rightArm.pitch = -3.0f + tremble;
                rightArm.yaw = -0.15f;
                rightArm.roll = 0.1f;
                leftArm.pitch = -3.0f - tremble;
                leftArm.yaw = 0.15f;
                leftArm.roll = -0.1f;
                head.pitch = -0.5f;
            }
            default -> {
                // Ready: both blades a little forward, never still
                rightArm.pitch = rightArm.pitch * 0.7f - 0.35f + sway;
                leftArm.pitch = leftArm.pitch * 0.7f - 0.3f - sway;
                rightArm.roll += 0.12f;
                leftArm.roll -= 0.12f;
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
