package net.ragnar.fmscraft.boss.pride;

/** What LeonardModel and LeonardRenderer need to draw LEONARD (and CONEMAN, who he becomes). */
public interface LeonardLook {
    int POSE_IDLE = 0, POSE_LAND = 1, POSE_RAISE = 2, POSE_CHANNEL = 3, POSE_THROW_BACK = 4, POSE_THROW = 5,
            POSE_SPREAD = 6, POSE_CLAP_WIND = 7, POSE_CLAP = 8, POSE_POINT = 9, POSE_DASH = 10, POSE_CROUCH = 11,
            POSE_SLAM = 12, POSE_STOMP = 13, POSE_HOVER = 14, POSE_PUNCH_A = 15, POSE_PUNCH_B = 16, POSE_SIDE_THROW = 17,
    /** Beaten: looking about, fidgeting, as guilty as anyone's ever looked; caught; and running for it, arms in the air. */
            POSE_GUILTY = 18, POSE_CAUGHT = 19, POSE_FLEE = 20;

    int poseState();

    /** Lightning glinting on his shades (phase 1). */
    boolean shadesLit();

    /** True while he's moving too fast to see: afterimages trail behind him. */
    boolean afterimages();

    /** The traffic cone is on his head: he's CONEMAN, and his head is inside it. */
    boolean coned();
}
