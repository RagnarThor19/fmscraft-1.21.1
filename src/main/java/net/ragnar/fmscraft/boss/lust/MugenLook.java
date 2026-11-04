package net.ragnar.fmscraft.boss.lust;

/** What MugenModel and MugenRenderer need to draw MUGEN, and his shadow clones, which look exactly like him. */
public interface MugenLook {
    int POSE_IDLE = 0, POSE_RISE = 1, POSE_LOOK = 2, POSE_DRAW = 3, POSE_STANCE = 4, POSE_LAND = 5, POSE_SEAL = 6,
            POSE_THROW = 7, POSE_THROW_BACK = 8, POSE_KICK_WIND = 9, POSE_KICK = 10, POSE_CHAIN = 11, POSE_STAB = 12,
            POSE_SPIN = 13, POSE_PLUNGE = 14, POSE_ORB = 15, POSE_RAISE = 16, POSE_JUMP = 17, POSE_POINT = 18,
            POSE_HANG = 19, POSE_DIVE = 20, POSE_SLIDE = 21, POSE_TAUNT = 22, POSE_SLASH_A = 23, POSE_SLASH_B = 24,
            POSE_CROUCH = 25, POSE_GRAPPLE = 26, POSE_DASH = 27,
            POSE_IAIDO = 28, POSE_PUPPET = 29, POSE_HOLE = 30;
    /** His eyes: dark, the violet of phase 1, the red of phase 2. */
    int EYES_NONE = 0, EYES_VIOLET = 1, EYES_RED = 2;

    int poseState();

    int eyes();

    /** True while he's moving too fast to see: afterimages trail behind him. */
    boolean afterimages();

    /** Whether this is the real one (only he casts a shadow). */
    boolean isReal();
}
