package net.ragnar.fmscraft.boss.envy;

/** What GummssModel needs to pose GUMMSS, and his Mirror Images, which look exactly like him. */
public interface GummssLook {
    int POSE_IDLE = 0, POSE_CAST = 1, POSE_PUSH = 2, POSE_PULL = 3, POSE_LIFT = 4, POSE_SLAM = 5, POSE_THROW = 6,
            POSE_SLASH_A = 7, POSE_SLASH_B = 8, POSE_SKY = 9, POSE_FALL = 10, POSE_LAND = 11, POSE_POINT = 12,
            POSE_STARE = 13, POSE_CATCH = 14, POSE_STUNNED = 15;

    int poseState();
}
