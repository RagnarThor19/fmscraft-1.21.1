package net.ragnar.fmscraft.photo;

/**
 * Temporary, for staging the title screen panoramas: the poses a skin model can hold. Angles are in degrees on the
 * player model's parts (pitch, yaw, roll). Negative arm/leg pitch swings the limb forward, -180 points an arm straight
 * up. Positive right-arm/right-leg roll swings it outward, negative for the left. The whole model can also be lifted
 * (blocks) and tipped over (pitch forward / roll sideways, degrees) around a point {@code pivot} blocks above its feet.
 */
public record SkinModelPose(String name, float[] head, float[] body, float[] rightArm, float[] leftArm,
                            float[] rightLeg, float[] leftLeg, float lift, float pitch, float roll, float pivot) {

    private static float[] r(float pitch, float yaw, float roll) {
        return new float[]{pitch, yaw, roll};
    }

    private static final float[] ZERO = r(0, 0, 0);

    private static SkinModelPose limbs(String name, float[] head, float[] rightArm, float[] leftArm, float[] rightLeg, float[] leftLeg) {
        return new SkinModelPose(name, head, ZERO, rightArm, leftArm, rightLeg, leftLeg, 0, 0, 0, 0);
    }

    private SkinModelPose whole(float lift, float pitch, float roll, float pivot) {
        return new SkinModelPose(name, head, body, rightArm, leftArm, rightLeg, leftLeg, lift, pitch, roll, pivot);
    }

    /** pose1 is POSES[0]. */
    public static final SkinModelPose[] POSES = {
            limbs("standing", ZERO, r(0, 0, 4), r(0, 0, -4), ZERO, ZERO),
            limbs("waving", r(0, 0, 8), r(-10, 0, 140), r(0, 0, -6), ZERO, ZERO),
            limbs("victory, both arms up", r(-20, 0, 0), r(0, 0, 150), r(0, 0, -150), r(0, 0, 4), r(0, 0, -4)),
            limbs("pointing ahead", r(5, 0, 0), r(-90, 0, 0), r(0, 0, -5), ZERO, ZERO),
            limbs("arms crossed", r(5, 0, 0), r(-75, -45, 0), r(-75, 45, 0), r(0, 0, 3), r(0, 0, -3)),
            limbs("running", r(-5, 0, 0), r(-70, 0, 0), r(60, 0, 0), r(50, 0, 0), r(-50, 0, 0)).whole(0, 12, 0, 0),
            limbs("thinking, hand to chin", r(12, 0, 10), r(-130, -35, 0), r(-40, 50, 0), ZERO, ZERO),
            limbs("dab", r(35, -25, 0), r(-20, 0, 120), r(-115, 65, 0), r(0, 0, 6), r(0, 0, -6)),
            limbs("zombie walk", r(10, 0, 15), r(-90, 0, 0), r(-90, 0, 0), r(25, 0, 0), r(-25, 0, 0)),
            limbs("shrug", r(-5, 0, 15), r(-20, 0, 35), r(-20, 0, -35), ZERO, ZERO),
            limbs("hands on hips", r(-8, 0, 0), r(-10, 0, 28), r(-10, 0, -28), r(0, 0, 5), r(0, 0, -5)),
            limbs("sitting on a chair", ZERO, r(-35, 0, 0), r(-35, 0, 0), r(-90, 10, 0), r(-90, -10, 0)),
            limbs("sitting on the ground", r(-5, 0, 0), r(30, 0, 15), r(30, 0, -15), r(-90, 12, 0), r(-90, -12, 0)).whole(-0.75f, 0, 0, 0),
            limbs("sitting on a ledge, legs dangling", r(15, 0, 0), r(0, 0, 15), r(0, 0, -15), r(-15, 0, 0), r(-5, 0, 0)).whole(-0.75f, 0, 0, 0),
            limbs("lying on your back", r(-10, 0, 0), r(-150, 0, 30), r(-150, 0, -30), ZERO, r(-20, 0, 0)).whole(0.2f, -90, 0, 0),
            limbs("flying like a superhero", r(-60, 0, 0), r(-180, 0, 0), r(0, 0, -10), r(5, 0, 0), ZERO).whole(0, 90, 0, 0),
            limbs("skydiving", r(-60, 0, 0), r(-30, 0, 100), r(-30, 0, -100), r(0, 0, 25), r(0, 0, -25)).whole(0, 90, 0, 0),
            limbs("holding something up high", r(-35, 0, 0), r(-180, 0, 0), r(0, 0, -5), ZERO, ZERO),
            limbs("falling, flailing", r(-30, 0, 0), r(-40, 0, 110), r(20, 0, -110), r(-30, 0, 20), r(20, 0, -20)).whole(0, -30, 25, 0.9f),
            limbs("handstand", ZERO, r(-180, 0, -8), r(-180, 0, 8), r(0, 0, 25), r(0, 0, -25)).whole(0, 180, 0, 1.0625f),
            limbs("t-pose", ZERO, r(0, 0, 90), r(0, 0, -90), ZERO, ZERO),
            limbs("sneaking", r(15, 0, 0), r(25, 0, 0), r(-15, 0, 0), r(-20, 0, 0), r(15, 0, 0)).whole(-0.1f, 20, 0, 0),
            limbs("looking up at the sky", r(-60, 0, 0), r(0, 0, 12), r(0, 0, -12), ZERO, ZERO),
            limbs("facepalm", r(25, 0, 0), r(-145, -25, 0), r(0, 0, -6), ZERO, ZERO),
            // pose25+
            limbs("leaning over a railing, looking down", r(55, 0, 0), r(-55, 0, 0), r(-55, 0, 0), r(-15, 0, 0), r(-15, 0, 0)).whole(0, 15, 0, 0),
            limbs("battle stance", r(10, 15, 0), r(-55, -10, 10), r(-30, 20, -20), r(-25, 0, 8), r(25, 0, -8)).whole(-0.05f, 8, 0, 0),
            limbs("kneeling, head bowed", r(40, 0, 0), r(-50, 0, 0), r(-10, 0, -6), r(-85, 0, 0), r(60, 0, 0)).whole(-0.4f, 0, 0, 0),
            limbs("formal bow", r(20, 0, 0), r(-70, -40, 0), r(20, 0, -10), r(-35, 0, 0), r(-35, 0, 0)).whole(0, 35, 0, 0.75f),
            limbs("hugging knees, looking down", r(30, 0, 0), r(-70, -30, 0), r(-70, 30, 0), r(-140, 8, 0), r(-140, -8, 0)).whole(-0.75f, 0, 0, 0),
            limbs("meditating (try NoGravity)", r(5, 0, 0), r(-40, 0, 25), r(-40, 0, -25), r(-90, -55, 0), r(-90, 55, 0)).whole(-0.75f, 0, 0, 0),
            limbs("superhero landing", r(25, 0, 0), r(-15, 0, -5), r(30, 0, -60), r(-75, 0, 0), r(55, 0, 0)).whole(-0.45f, 20, 0, 0),
            limbs("aiming a bow (hold one)", ZERO, r(-90, -6, 0), r(-90, 29, 0), r(-8, 0, 4), r(8, 0, -4)),
            limbs("crouching, inspecting the ground", r(55, 0, 0), r(-30, -10, 0), r(10, 0, -10), r(-30, 0, 0), r(-10, 0, 0)).whole(-0.15f, 25, 0, 0),
            limbs("lying on your stomach, kicking your legs", r(-70, 0, 0), r(-150, -15, 0), r(-150, 15, 0), r(70, 0, 0), r(40, 0, 0)).whole(0.15f, 90, 0, 0),
            limbs("dancing", r(0, 0, -12), r(0, 0, 155), r(-20, 0, -60), r(-20, 0, 30), ZERO).whole(0, 0, 8, 0),
            limbs("saluting", r(-5, 0, 0), r(-140, -50, 0), r(0, 0, -2), ZERO, ZERO),
            limbs("shading your eyes, scanning the horizon", r(-10, -25, 0), r(-160, -30, 0), r(-10, 0, -25), r(0, 0, 4), r(0, 0, -4)),
            limbs("diving head first (NoGravity)", r(-30, 0, 0), r(-180, 0, -10), r(-180, 0, 10), r(5, 0, 0), r(5, 0, 0)).whole(0, 160, 0, 0.9f),
            limbs("cheering jump (NoGravity)", r(-25, 0, 0), r(0, 0, 160), r(0, 0, -160), r(40, 0, 10), r(40, 0, -10)).whole(0.3f, 0, 0, 0),
            limbs("sprinting, leaning forward hard", r(-25, 0, 0), r(-85, 0, 5), r(75, 0, -5), r(60, 0, 0), r(-70, 0, 0)).whole(0.1f, 28, 0, 0),
            // pose41+: photo poses. Negative head yaw looks to the model's left. On an arm raised above the shoulder
            // the roll flips: positive right-arm roll then swings it in over the head.
            limbs("hand over the shoulder, head tilted", r(5, -12, -10), r(-195, 0, 12), r(-10, 0, -26), r(0, 0, 3), r(-6, -8, -7)),
            limbs("pointing one way, looking back at a friend", r(-5, -45, 0), r(-90, 40, 0), r(-30, 15, -18), r(0, 0, 4), r(-10, 0, -6)),
            limbs("scared, arms up to shield your face", r(20, 15, 0), r(-120, -28, 0), r(-110, 28, 0), r(-20, 0, 6), r(18, 0, -6)).whole(-0.05f, -10, 0, 0),
            limbs("OH NO, hands on your head", r(-20, 0, 0), r(-172, 0, 18), r(-172, 0, -18), r(15, 0, 4), r(-25, 0, -4)).whole(0, -8, 0, 0),
            limbs("falling backwards, windmilling (NoGravity)", r(25, 0, 0), r(-160, 0, 30), r(-60, 0, -70), r(-60, 0, 10), r(-20, 0, -10)).whole(0.3f, -50, 0, 0.9f),
            limbs("slipping, feet out from under you (NoGravity)", r(20, 10, 0), r(-30, 0, 80), r(-60, 0, -120), r(-75, 0, 6), r(5, 0, -4)).whole(0.35f, -25, 0, 0),
            limbs("shifting your weight", r(0, 8, -6), r(4, 0, 5), r(-6, 0, -10), r(0, 0, 2), r(-10, -12, -10)),
            limbs("shifting your weight, glancing down", r(28, 15, 4), r(6, 0, 4), r(-4, 0, -9), r(-10, 12, 10), r(0, 0, -2)),
            limbs("leaning back on a wall, arms crossed", r(8, -18, 0), r(-75, -45, 0), r(-75, 45, 0), r(-14, 0, -10), r(-6, 0, -3)).whole(0, -9, 0, 0),
            limbs("peace sign", r(0, 10, 10), r(-168, 0, -14), r(-10, 0, -28), r(0, 0, 4), r(-8, -10, -6)),
            limbs("taking a selfie", r(-15, -25, -8), r(-165, 0, -12), r(-120, -30, 0), r(0, 0, 4), r(0, 0, -4)),
            limbs("looking back over your shoulder", r(0, 65, 0), r(5, 0, 5), r(0, 0, -5), r(0, 0, 3), r(-6, 0, -3)),
            limbs("laughing, doubled over", r(-30, 0, 8), r(-35, -25, 0), r(-35, 25, 0), r(-10, 0, 4), r(-10, 0, -4)).whole(0, 30, 0, 0.75f),
            limbs("hands behind your back", r(-6, 10, 0), r(22, 25, 0), r(22, -25, 0), r(0, 0, 3), r(0, 0, -3)),
            limbs("reaching out to grab a friend's hand", r(-10, 0, 0), r(-80, 8, 0), r(15, 0, -8), r(-30, 0, 0), r(30, 0, 0)).whole(-0.05f, 14, 0, 0),
            // pose56+: boss poses, for /raggimel2 and the rest (they suit the friends too)
            limbs("powering up, fists clenched, head thrown back", r(-35, 0, 0), r(-15, 0, 28), r(-15, 0, -28), r(0, 0, 10), r(0, 0, -10)),
            limbs("casting, both hands thrust forward", r(12, 0, 0), r(-82, -14, 0), r(-82, 14, 0), r(-22, 0, 5), r(20, 0, -5)).whole(-0.03f, 6, 0, 0),
            limbs("come at me, beckoning", r(-8, 0, 8), r(0, 0, 8), r(-62, 22, 0), r(0, 0, 4), r(-10, 0, -6)),
            limbs("weapon raised overhead, about to strike", r(-18, 0, 0), r(-198, -10, 0), r(-195, 12, 0), r(-22, 0, 6), r(22, 0, -6)).whole(0, -8, 0, 0),
            limbs("mid-slash, sword swung down", r(15, 0, 0), r(-48, 32, 0), r(38, 0, -18), r(-45, 0, 4), r(32, 0, -4)).whole(-0.08f, 16, 0, 0),
            limbs("floating, arms spread, looking down on you (NoGravity)", r(22, 0, 0), r(-18, 0, 55), r(-18, 0, -55), r(10, 0, 4), r(18, 0, -4)).whole(0.5f, 0, 0, 0),
            limbs("throwing a punch", r(5, 0, 0), r(-90, 12, 0), r(-112, -42, 0), r(-28, 0, 4), r(26, 0, -4)).whole(-0.03f, 8, 0, 0),
            limbs("high kick", r(-5, 0, 0), r(-38, 0, 45), r(25, 0, -35), r(-105, 0, 0), r(0, 0, -6)).whole(0, -12, 0, 0),
            limbs("on a throne, chin on your fist", r(8, 0, 6), r(-125, -28, 0), r(-38, 0, 8), r(-90, 10, 0), r(-78, -24, 0)),
            limbs("hands behind your head, relaxed", r(-10, 0, 0), r(-165, 0, 38), r(-165, 0, -38), r(0, 0, 4), r(-8, -10, -6)),
            limbs("arms open wide, welcoming", r(-12, 0, 0), r(-30, 0, 60), r(-30, 0, -60), r(-18, 0, 3), r(14, 0, -3)),
    };

    public static SkinModelPose get(int index) {
        return POSES[Math.floorMod(index, POSES.length)];
    }
}
